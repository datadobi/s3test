/*
 *
 *  Copyright 2025 Datadobi
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software

 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.datadobi.s3test.s3;

import com.datadobi.s3test.http.Range;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class S3Bucket {
    private final S3Client client;
    private final String bucket;

    public S3Bucket(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public String name() {
        return bucket;
    }

    public void create() {
        client.createBucket(r -> r.bucket(bucket));
    }

    public void delete() {
        client.deleteBucket(r -> r.bucket(bucket));
    }

    public PutObjectResponse putObject(String key, String content) {
        return putObject(key, content.getBytes(StandardCharsets.UTF_8));
    }

    public PutObjectResponse putObject(String key, byte[] content) {
        return putObject(r -> r.key(key), content);
    }

    public PutObjectResponse putObject(Consumer<PutObjectRequest.Builder> putObjectRequest, String content) {
        return putObject(putObjectRequest, content.getBytes(StandardCharsets.UTF_8));
    }

    public PutObjectResponse putObject(Consumer<PutObjectRequest.Builder> putObjectRequest, byte[] content) {
        return putObject(putObjectRequest, RequestBody.fromBytes(content));
    }

    public PutObjectResponse putObject(Consumer<PutObjectRequest.Builder> putObjectRequest, RequestBody body) {
        return client.putObject(
                r -> {
                    putObjectRequest.accept(r);
                    r.bucket(bucket);
                },
                body
        );
    }

    public List<String> listObjectKeys(S3.ListObjectsVersion version) {
        return listObjectKeys(version, null, null);
    }

    public List<String> listObjectKeys(S3.ListObjectsVersion version, @Nullable Integer maxKeys) {
        return listObjectKeys(version, maxKeys, null);
    }

    public List<String> listObjectKeys(S3.ListObjectsVersion version, @Nullable Integer maxKeys, @Nullable String startAfter) {
        return S3.listObjectKeys(client, bucket, version, maxKeys, startAfter);
    }

    public ListObjectsResponse listObjectsV1(@Nullable Integer maxKeys, @Nullable String marker) {
        return listObjectsV1(r -> {
            r.maxKeys(maxKeys);
            r.marker(marker);
            r.encodingType(EncodingType.URL);
        });
    }

    public ListObjectsResponse listObjectsV1(Consumer<ListObjectsRequest.Builder> listObjectsV1Request) {
        return client.listObjects(r -> {
            listObjectsV1Request.accept(r);
            r.bucket(bucket);
        });
    }

    public ListObjectsV2Response listObjectsV2(@Nullable Integer maxKeys, @Nullable String startAfter) {
        return listObjectsV2(r -> {
            r.maxKeys(maxKeys);
            r.startAfter(startAfter);
            r.encodingType(EncodingType.URL);
        });
    }

    public ListObjectsV2Response listObjectsV2(Consumer<ListObjectsV2Request.Builder> listObjectsV2Request) {
        return client.listObjectsV2(r -> {
            listObjectsV2Request.accept(r);
            r.bucket(bucket);
        });
    }

    public HeadObjectResponse headObject(String key) {
        return headObject(b -> b.key(key));
    }

    public HeadObjectResponse headObject(Consumer<HeadObjectRequest.Builder> headObjectRequest) {
        return client.headObject(b -> {
            headObjectRequest.accept(b);
            b.bucket(bucket);
        });
    }

    public HeadObjectResponse headObjectWithETag(String key, String expectedETag, Duration timeout) throws TimeoutException {
        Instant start = Instant.now();
        do {
            HeadObjectResponse response = headObject(key);
            if (response.eTag().equals(expectedETag)) {
                return response;
            }
        } while (Duration.between(start, Instant.now()).compareTo(timeout) < 0);

        throw new TimeoutException();
    }

    public ResponseInputStream<GetObjectResponse> getObject(String key) {
        return getObject(key, null, null);
    }

    public ResponseInputStream<GetObjectResponse> getObject(String key, @Nullable String versionId) {
        return getObject(key, versionId, null);
    }

    public ResponseInputStream<GetObjectResponse> getObject(String key, @Nullable String versionId, @Nullable Range range) {
        return getObject(r -> {
            r.key(key).versionId(versionId);
            if (range != null) {
                r.range(range.toString());
            }
        });
    }

    public ResponseInputStream<GetObjectResponse> getObject(Consumer<GetObjectRequest.Builder> getObjectRequest) {
        return client.getObject(r -> {
            getObjectRequest.accept(r);
            r.bucket(bucket);
        });
    }

    public byte[] getObjectContent(String key) throws IOException {
        try (var result = getObject(key)) {
            return result.readAllBytes();
        }
    }

    public DeleteObjectResponse deleteObject(String key) {
        return deleteObject(r -> r.key(key));
    }

    public DeleteObjectResponse deleteObject(Consumer<DeleteObjectRequest.Builder> deleteObjectRequest) {
        return client.deleteObject(r -> {
            deleteObjectRequest.accept(r);
            r.bucket(bucket);
        });
    }

    public DeleteObjectsResponse deleteObjects(String... keys) {
        return client.deleteObjects(r -> {
            r.bucket(bucket);
            r.delete(d -> {
                d.objects(Arrays.stream(keys).map(k -> ObjectIdentifier.builder().key(k).build()).toList());
            });
        });
    }

    public DeleteObjectsResponse deleteObjects(Consumer<ObjectIdentifier.Builder> objectId) {
        return client.deleteObjects(r -> {
            r.bucket(bucket);
            r.delete(d -> d.objects(objectId));
        });
    }

    public CopyObjectResponse copyObject(String key, String targetBucket, String targetKey, Map<String, String> userMetadata) {
        return client.copyObject(r -> r
                .sourceBucket(bucket)
                .sourceKey(key)
                .destinationBucket(targetBucket)
                .destinationKey(targetKey)
                .metadata(userMetadata)
                .metadataDirective(MetadataDirective.REPLACE)
        );
    }

    public CreateMultipartUploadResponse createMultipartUpload(String key) {
        return client.createMultipartUpload(r -> r.bucket(bucket).key(key));
    }

    public UploadPartResponse uploadPart(Consumer<UploadPartRequest.Builder> uploadRequest, byte[] content) {
        return client.uploadPart(r -> {
                    uploadRequest.accept(r);
                    r.bucket(bucket);
                },
                RequestBody.fromBytes(content)
        );
    }

    public CompleteMultipartUploadResponse completeMultipartUpload(Consumer<CompleteMultipartUploadRequest.Builder> complete) {
        return client.completeMultipartUpload(r -> {
            complete.accept(r);
            r.bucket(bucket);
        });
    }
}
