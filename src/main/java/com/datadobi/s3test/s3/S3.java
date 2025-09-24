package com.datadobi.s3test.s3;

import com.datadobi.s3test.util.Pair;
import com.datadobi.s3test.util.SupplierThatThrows;
import com.google.common.net.InetAddresses;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.awscore.util.AwsHostNameUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.LegacyMd5Plugin;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.utils.AttributeMap;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class S3 {
    private static final Logger LOG = LoggerFactory.getLogger(S3.class);

    public static final int MAX_CONNECTIONS = S3ClientOption.MAX_CONNECTIONS.getValue(1024);
    public static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(S3ClientOption.CONNECT_TIMEOUT_SECONDS.getValue(60));
    public static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(S3ClientOption.SOCKET_TIMEOUT_SECONDS.getValue(120));

    public static final int NUM_RETRIES = S3ClientOption.NUM_RETRIES.getValue(2);
    @Nullable
    public static final Duration API_CALL_TIMEOUT = Optional.ofNullable(S3ClientOption.API_CALL_TIMEOUT_SECONDS.getValue())
            .map(Duration::ofSeconds)
            .orElse(null);
    @Nullable
    public static final Duration API_CALL_ATTEMPT_TIMEOUT = Optional.ofNullable(S3ClientOption.API_CALL_ATTEMPT_TIMEOUT_SECONDS.getValue())
            .map(Duration::ofSeconds)
            .orElse(null);

    public static S3Client createClient(ServiceDefinition target) {
        S3ClientBuilder clientBuilder = S3Client.builder();

        // Rolls back the SDK v2.30 checksum changes to avoid compatibility issues
        // See https://github.com/aws/aws-sdk-java-v2/discussions/5802#discussioncomment-12281124
        clientBuilder.addPlugin(LegacyMd5Plugin.create());

        ClientOverrideConfiguration.Builder clientConfiguration = ClientOverrideConfiguration.builder();

        var defaultRetryPolicy = RetryPolicy.builder(RetryMode.STANDARD)
                .numRetries(NUM_RETRIES)
                .build();
        clientConfiguration.retryPolicy(defaultRetryPolicy);

        clientConfiguration.apiCallTimeout(API_CALL_TIMEOUT);
        clientConfiguration.apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT);

        // Disable chunked encoding for empty request bodies
        // When creating directory placeholders on certain servers, the request
        // fails otherwise.
        clientConfiguration.addExecutionInterceptor(new NoChunkedForEmptyPutInterceptor());
        clientBuilder.overrideConfiguration(clientConfiguration.build());

        String accessKey = target.accessKeyId();
        String secretKey = target.secretAccessKey();

        if (accessKey != null && !accessKey.isBlank() && !secretKey.isBlank()) {
            clientBuilder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            clientBuilder.credentialsProvider(AnonymousCredentialsProvider.create());
        }

        Region region;
        if (target.signingRegion() != null) {
            region = target.signingRegion();
        } else {
            // If the region name was not specified, try to derive it from the endpoint
            region = AwsHostNameUtils.parseSigningRegion(target.host(), null).orElse(null);
        }

        if (region == null) {
            region = Region.US_EAST_1;
        }
        clientBuilder.region(region);

        clientBuilder.requestChecksumCalculation(target.requestChecksumCalculation());
        clientBuilder.responseChecksumValidation(target.responseChecksumValidation());

        clientBuilder.endpointOverride(target.endpoint());

        boolean forcePathStyle = target.addressingStyle() == AddressingStyle.PATH || InetAddresses.isInetAddress(target.host());
        clientBuilder.forcePathStyle(forcePathStyle);

        clientBuilder.httpClientBuilder(createHttpClientBuilder());

        return clientBuilder.build();
    }

    public static SdkHttpClient.Builder createHttpClientBuilder() {
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
        httpClientBuilder.maxConnections(MAX_CONNECTIONS);
        httpClientBuilder.connectionTimeout(CONNECTION_TIMEOUT);
        httpClientBuilder.socketTimeout(SOCKET_TIMEOUT);

        AttributeMap.Builder httpOptionsBuilder = AttributeMap.builder();
        httpOptionsBuilder.put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, Boolean.TRUE);
        return serviceDefaults -> httpClientBuilder.buildWithDefaults(
                serviceDefaults.merge(httpOptionsBuilder.build())
        );
    }

    public static RetryPolicy createNoPutRetryPolicy() {
        RetryCondition defaultRetryCondition = RetryCondition.defaultRetryCondition();

        RetryPolicy.Builder builder = RetryPolicy.builder();
        // Prevent the SDK from adding additional retry conditions behind our back
        builder.additionalRetryConditionsAllowed(false);
        builder.numRetries(NUM_RETRIES);
        return builder.retryCondition(
                (context) -> {
                    if (context.originalRequest() instanceof PutObjectRequest || context.originalRequest() instanceof UploadPartRequest) {
                        return false;
                    }
                    return defaultRetryCondition.shouldRetry(context);
                }).build();
    }

    public static void createBucket(S3Client s3, String bucket) {
        if (!bucketExists(s3, bucket)) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    public static boolean createBucketAndWait(S3Client s3Client, String bucketName) throws IOException {
        try {
            return attempt(5, "create bucket", () -> {
                if (bucketExists(s3Client, bucketName)) {
                    //the previous attempt did succeed, apparently
                    return true;
                }

                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                boolean success = waitUntilBucketExists(s3Client, bucketName, Duration.ofMinutes(3));
                if (!success) Assert.fail(String.format("Could not create bucket %s", bucketName));
                return true;
            });
        } catch (InterruptedException e) {
            return false;
        }
    }

    public static boolean waitUntilBucketExists(S3Client s3, String bucketName, Duration eventualConsistencyDelay) throws IOException {
        long delay = eventualConsistencyDelay.toMillis();
        long deadline = System.currentTimeMillis() + delay;

        while (true) {
            if (bucketExists(s3, bucketName)) {
                return true;
            }

            if (System.currentTimeMillis() > deadline) {
                break;
            }

            LOG.error("Bucket {} does not (yet) exist, retrying ...", bucketName);
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                throw (IOException) new InterruptedIOException(e.getMessage()).initCause(e);
            }
        }

        return false;
    }

    public static boolean bucketExists(S3Client s3, String bucketName) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return true;
        } catch (AwsServiceException e) {
            if (e.statusCode() != 404) {
                throw e;
            } else {
                return false;
            }
        }
    }

    public static void deleteBucket(S3Client s3, String bucketName) {
        int attempt = 0;
        while (true) {
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            } catch (AwsServiceException e) {
                if (e.statusCode() == 404) {
                    // no longer exists, so nothing to do
                    return;
                }
            }

            try {
                clearBucket(s3, bucketName);
                s3.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
                break;
            } catch (AwsServiceException e) {
                if (S3Error.findError(e) == S3Error.NoSuchBucket) {
                    // already gone
                    break;
                }

                if (attempt < 10) {
                    try {
                        long sleepTime = 100 + attempt * attempt * 100;
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException interruptedException) {
                        // Ignored
                    }
                    attempt++;
                } else {
                    throw e;
                }
            }
        }
    }

    public static void clearBucket(S3Client s3, String bucketName) {
        clearBucket(s3, bucketName, false, false);
    }

    public static void clearBucket(S3Client s3, String bucketName, boolean canNotDeleteVersions, boolean isMultipartSupported) {
        GetBucketVersioningResponse configuration = s3.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucketName).build());
        boolean versionsEnabled = configuration.status() == BucketVersioningStatus.ENABLED;

        if (versionsEnabled && !canNotDeleteVersions) {
            List<Pair<String, String>> objectVersions = new ArrayList<>();
            List<Pair<String, String>> deleteMarkers = new ArrayList<>();

            ListObjectVersionsRequest.Builder req = ListObjectVersionsRequest.builder()
                    .bucket(bucketName)
                    .encodingType(EncodingType.URL);
            ListObjectVersionsResponse res;
            do {
                res = s3.listObjectVersions(req.build());
                for (ObjectVersion summary : res.versions()) {
                    objectVersions.add(Pair.create(summary.key(), summary.versionId()));
                }
                for (DeleteMarkerEntry deleteMarker : res.deleteMarkers()) {
                    deleteMarkers.add(Pair.create(deleteMarker.key(), deleteMarker.versionId()));
                }
                req.keyMarker(res.nextKeyMarker());
                req.versionIdMarker(res.nextVersionIdMarker());
            } while (res.isTruncated());

            for (Pair<String, String> key : objectVersions) {
                try {
                    s3.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key.first())
                            .versionId(key.second())
                            .build());
                } catch (S3Exception e) {
                    if (e.statusCode() == 403) {
                        // ok, probably under retention.
                        continue;
                    }
                    throw e;
                }
            }

            for (Pair<String, String> key : deleteMarkers) {
                s3.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key.first())
                        .versionId(key.second())
                        .build());
            }

        } else {
            List<String> keys = new ArrayList<>();

            ListObjectsV2Request.Builder req = ListObjectsV2Request.builder();
            ListObjectsV2Response res;
            req.bucket(bucketName);
            req.encodingType(EncodingType.URL);
            do {
                res = s3.listObjectsV2(req.build());
                for (S3Object summary : res.contents()) {
                    keys.add(summary.key());
                }
                req.continuationToken(res.nextContinuationToken());
            } while (res.isTruncated() == Boolean.TRUE);

            for (String key : keys) {
                try {
                    s3.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build());
                } catch (S3Exception e) {
                    if (e.statusCode() == 403) {
                        // ok, probably under retention.
                        continue;
                    }
                    throw e;
                }
            }
        }

        if (isMultipartSupported) {
            ListMultipartUploadsResponse uploads = s3.listMultipartUploads(
                    ListMultipartUploadsRequest.builder().bucket(bucketName).encodingType(EncodingType.URL).build());
            for (MultipartUpload upload : uploads.uploads()) {
                s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(upload.key())
                        .uploadId(upload.uploadId())
                        .build());
            }
        }
    }

    public static <T> T attempt(int attempts, String message, SupplierThatThrows<T, IOException> toRetry) throws IOException, InterruptedException {
        int attemptsLeft = attempts;

        List<Exception> exceptionsFromPreviousAttempts = new ArrayList<>();
        while (attemptsLeft > 0) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            try {
                return toRetry.get();
            } catch (Exception e) {
                exceptionsFromPreviousAttempts.add(e);
                attemptsLeft--;

                LOG.warn("Unable to {} (attempt {}/{}): {}", message, attempts - attemptsLeft, attempts, e.getMessage());
                Thread.sleep(100 + 100 * (attempts - attemptsLeft));
            }
        }

        IOException combiningException = new IOException(String.format("Unable to %s after %d attempts", message, attempts));
        exceptionsFromPreviousAttempts.forEach(combiningException::addSuppressed);
        throw combiningException;
    }

    public enum ListObjectsVersion {
        V1, V2
    }

    public static List<String> listObjectKeys(S3Client s3, String bucketName, ListObjectsVersion version, Integer maxKeys) {
        return listObjectKeys(s3, bucketName, version, maxKeys, null);
    }

    public static List<String> listObjectKeys(S3Client s3, String bucketName, ListObjectsVersion version, Integer maxKeys, @Nullable String startAfter) {
        switch (version) {
            case V1:
                return listObjectKeysV1(s3, bucketName, maxKeys, startAfter);
            case V2:
                return listObjectKeysV2(s3, bucketName, maxKeys, startAfter);
            default:
                throw new IllegalArgumentException(version.toString());
        }
    }

    public static ListBucketsResponse listBuckets(S3Client s3) {
        return s3.listBuckets();
    }

    public static List<String> listBucketsPaging(S3Client s3, int limit) {
        return s3.listBucketsPaginator(ListBucketsRequest.builder()
                        .overrideConfiguration(
                                AwsRequestOverrideConfiguration.builder()
                                        .putRawQueryParameter("limit", Integer.toString(limit))
                                        .build()
                        )
                        .build())
                .buckets()
                .stream()
                .map(Bucket::name)
                .toList();
    }

    public static List<String> listObjectKeysV1(S3Client s3, String bucketName, Integer maxKeys) {
        return listObjectKeysV1(s3, bucketName, maxKeys, null);
    }

    public static List<String> listObjectKeysV1(S3Client s3, String bucketName, Integer maxKeys, @Nullable String marker) {
        //use the standard SDK to list the objects
        List<String> keys = new ArrayList<>();

        ListObjectsRequest.Builder request = ListObjectsRequest.builder();
        request.bucket(bucketName);
        request.maxKeys(maxKeys);
        request.marker(marker);
        request.encodingType(EncodingType.URL);

        ListObjectsResponse result;
        do {
            result = s3.listObjects(request.build());
            result.contents().forEach(s -> keys.add(s.key()));

            String nextMarker = result.nextMarker();
            if (nextMarker == null && !result.contents().isEmpty()) {
                nextMarker = result.contents().get(result.contents().size() - 1).key();
            }
            request.marker(nextMarker);
        } while (result.isTruncated());

        return keys;
    }

    public static ListObjectsResponse listObjectsV1(S3Client s3, String bucketName, Integer maxKeys, @Nullable String marker) {
        ListObjectsRequest.Builder request = ListObjectsRequest.builder();
        request.bucket(bucketName);
        request.maxKeys(maxKeys);
        request.marker(marker);
        request.encodingType(EncodingType.URL);

        return s3.listObjects(request.build());
    }

    public static List<String> listObjectKeysV2(S3Client s3, String bucketName, Integer maxKeys) {
        return listObjectKeysV2(s3, bucketName, maxKeys, null);

    }

    public static List<String> listObjectKeysV2(S3Client s3, String bucketName, Integer maxKeys, @Nullable String startAfter) {
        //use the standard SDK to list the objects
        List<String> keys = new ArrayList<>();

        ListObjectsV2Request.Builder request = ListObjectsV2Request.builder();
        request.bucket(bucketName);
        request.maxKeys(maxKeys);
        request.startAfter(startAfter);
        request.encodingType(EncodingType.URL);

        ListObjectsV2Response result;
        do {
            result = s3.listObjectsV2(request.build());
            result.contents().forEach(s -> keys.add(s.key()));
            request.continuationToken(result.nextContinuationToken());
        } while (result.isTruncated() == Boolean.TRUE);

        return keys;
    }

    public static ListObjectsV2Response listObjectsV2(S3Client s3, String bucketName, Integer maxKeys, @Nullable String startAfter) {
        ListObjectsV2Request.Builder request = ListObjectsV2Request.builder();
        request.bucket(bucketName);
        request.maxKeys(maxKeys);
        request.startAfter(startAfter);
        request.encodingType(EncodingType.URL);

        return s3.listObjectsV2(request.build());
    }

    public static PutObjectResponse putObject(S3Client s3Client, String bucketName, String key, String data) {
        return putObject(s3Client, bucketName, key, data, (r) -> {
        });
    }

    public static PutObjectResponse putObject(S3Client s3Client, String bucketName, String key, String data,
                                              Consumer<PutObjectRequest.Builder> modifyPutObjectRequest) {
        var putObjectRequestBuilder = PutObjectRequest.builder();

        putObjectRequestBuilder
                .bucket(bucketName)
                .key(key);

        modifyPutObjectRequest.accept(putObjectRequestBuilder);

        return s3Client.putObject(putObjectRequestBuilder.build(), RequestBody.fromString(data));
    }

    public static void deleteObject(S3Client s3Client, String bucketName, String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

}
