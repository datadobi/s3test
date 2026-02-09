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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@AutoValue
public abstract class ServiceDefinition {
    public static ServiceDefinition fromS3Profile(String profile) throws IOException {
        return fromS3Profile(profile, null);
    }

    public static ServiceDefinition fromS3Profile(String profile, @Nullable String bucket) throws IOException {
        var builder = builder();

        Path userHome = Path.of(System.getProperty("user.home"));
        Path awsDir = userHome.resolve(".aws");
        Map<String, Map<String, String>> config = readConfigFile(awsDir.resolve("config"));
        Map<String, Map<String, String>> credentials = readConfigFile(awsDir.resolve("credentials"));

        Map<String, String> conf = config.getOrDefault("profile " + profile, Map.of());
        Map<String, String> creds = credentials.getOrDefault(profile, Map.of());

        String accessKeyId = creds.get("aws_access_key_id");
        String secretAccessKey = creds.get("aws_secret_access_key");
        if (accessKeyId == null && secretAccessKey == null) {
            accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
            secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        }
        builder.accessKeyId(accessKeyId);
        builder.secretAccessKey(secretAccessKey);

        String url = conf.get("endpoint_url");
        if (url == null) {
            url = "https://s3.amazonaws.com";
        }

        URI endpoint = URI.create(url);

        builder.host(endpoint.getHost());

        int port = endpoint.getPort();
        if (port != -1) {
            builder.port(port);
        }

        builder.useEncryption(endpoint.getScheme().equalsIgnoreCase("https"));

        if (bucket != null && !bucket.isEmpty()) {
            builder.withExistingBucket(bucket);
        } else {
            builder.withUniqueBucket();
        }

        String regionString = conf.get("region");
        if (regionString != null) {
            builder.signingRegion(Region.of(regionString));
        }

        String addressingStyleString = conf.get("addressing_style");
        if (addressingStyleString != null) {
            switch (addressingStyleString) {
                case "path" -> builder.addressingStyle(AddressingStyle.PATH);
                case "virtual" -> builder.addressingStyle(AddressingStyle.AUTO);
            }
        }

        String requestChecksumCalculation = conf.get("request_checksum_calculation");
        if (requestChecksumCalculation != null) {
            builder.responseChecksumValidation(ResponseChecksumValidation.fromValue(requestChecksumCalculation));
        }

        String responseChecksumValidation = conf.get("response_checksum_validation");
        if (responseChecksumValidation != null) {
            builder.responseChecksumValidation(ResponseChecksumValidation.fromValue(responseChecksumValidation));
        }

        String payloadSigningEnabled = conf.get("payload_signing_enabled");
        if (payloadSigningEnabled != null) {
            builder.payloadSigningEnabled(Boolean.parseBoolean(payloadSigningEnabled));
        }

        return builder.build();
    }

    private static Map<String, Map<String, String>> readConfigFile(Path path) throws IOException {
        if (!Files.exists(path)) {
            return Map.of();
        }

        Pattern header = Pattern.compile("\\[(.+)]");
        Pattern keyValue = Pattern.compile("(.+)=(.+)");

        List<String> config = Files.readAllLines(path);
        Map<String, Map<String, String>> sections = new HashMap<>();
        Map<String, String> section = null;

        for (int i = 0; i < config.size(); i++) {
            String line = config.get(i).trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            Matcher headerMatcher = header.matcher(line);
            if (headerMatcher.matches()) {
                section = new HashMap<>();
                sections.put(headerMatcher.group(1), section);
                continue;
            }

            Matcher kvMatcher = keyValue.matcher(line);
            if (kvMatcher.matches()) {
                String key = kvMatcher.group(1).trim();
                String value = kvMatcher.group(2).trim();
                section.put(key, value);
            }
            ;
        }
        return sections;
    }

    public static ServiceDefinition fromURI(String uriString) throws IOException {
        URI uri = URI.create(uriString);

        String bucketName = uri.getPath();
        if (bucketName != null) {
            while (bucketName.startsWith("/")) {
                bucketName = bucketName.substring(1);
            }
        }

        if (bucketName != null && bucketName.isBlank()) {
            bucketName = null;
        }

        if (uri.getScheme().equals("s3profile")) {
            return fromS3Profile(uri.getHost(), bucketName);
        }

        var builder = builder();

        builder.useEncryption(uri.getScheme().equalsIgnoreCase("https"));

        builder.host(uri.getHost());

        int port = uri.getPort();
        if (port != -1) {
            builder.port(port);
        }

        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            String[] userPass = userInfo.split(":", 2);
            builder.accessKeyId(userPass[0]);
            builder.secretAccessKey(userPass[1]);
        } else {
            builder.accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"));
            builder.secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"));
        }

        if (bucketName != null) {
            builder.withExistingBucket(bucketName);
        }

        return builder.build();
    }

    public AwsCredentialsProvider getCredentials() {
        String accessKey = accessKeyId();
        String secretKey = secretAccessKey();

        if (accessKey != null && !accessKey.isBlank() && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        } else {
            return AnonymousCredentialsProvider.create();
        }
    }

    public URI endpoint() {
        StringBuilder uri = new StringBuilder();
        uri.append("http");
        if (useEncryption()) {
            uri.append("s");
        }
        uri.append("://");
        uri.append(host());
        if (port() != null) {
            uri.append(":").append(port());
        }
        return URI.create(uri.toString());
    }

    public enum Capability {
        PUT_OBJECT_IF_NONE_MATCH_ETAG_SUPPORTED,

    }

    public enum Restriction {
        // OBJECT SPECIFIC
        PARTCOUNT_NOT_SUPPORTED,
        MULTIPART_SIZES_NOT_KEPT,
        KEYS_WITH_NULL_NOT_REJECTED,
        KEYS_WITH_NULL_ARE_TRUNCATED,
        KEYS_WITH_INVALID_UTF8_NOT_REJECTED,
        KEYS_WITH_CODEPOINT_MIN_REJECTED,
        COPY_OBJECT_BROKEN,
        KEYS_ARE_SORTED_IN_UTF16_BINARY_ORDER,
        USER_METADATA_CHANGE_DOES_NOT_BUMP_LAST_MODIFIED_TIME,
        LIST_OBJECTS_CAN_RETURN_INVALID_KEY,
        MULTIPART_DOWNLOAD_BROKEN,
        KEYS_WITH_CODEPOINTS_OUTSIDE_BMP_REJECTED,
        KEYS_WITH_SLASHES_CREATE_IMPLICIT_OBJECTS,
        CANNOT_DELETE_OBJECT_VERSIONS,
        CONTENT_TYPE_NOT_SET_FOR_KEYS_WITH_TRAILING_SLASH,
        STORAGE_CLASS_NOT_KEPT,
        ETAG_EMPTY_AFTER_COPY_OBJECT,
        RETENTION_CHANGE_BUMPS_LAST_MODIFIED_TIME,
        ENCODING_TYPE_URL_NOT_HONORED,
        PUT_OBJECT_IF_NONE_MATCH_STAR_NOT_SUPPORTED,
        KEY_WITH_TRAILING_SLASH_MAPS_TO_DIRECTORY,
    }

    @Nullable
    public Integer port() {
        return portSupplier() == null ? null : portSupplier().get();
    }

    @Nullable
    public abstract Supplier<Integer> portSupplier();

    public abstract String host();

    @Nullable
    public abstract String accessKeyId();

    @Nullable
    public abstract String secretAccessKey();

    public abstract ImmutableSet<Capability> capabilities();

    public abstract ImmutableSet<Restriction> restrictions();

    public boolean hasCapabilities(Capability... capability) {
        return Arrays.stream(capability).allMatch(c -> capabilities().contains(c));
    }

    public boolean hasRestrictions(Restriction... restriction) {
        return Arrays.stream(restriction).allMatch(r -> restrictions().contains(r));
    }

    public abstract Duration eventualConsistencyDelay();

    public abstract Region signingRegion();

    public abstract @Nullable AddressingStyle addressingStyle();

    public abstract boolean useListV1();

    public abstract boolean useEncryption();

    public abstract RequestChecksumCalculation requestChecksumCalculation();

    public abstract ResponseChecksumValidation responseChecksumValidation();

    public abstract boolean payloadSigningEnabled();

    public abstract String bucket();

    public abstract boolean createBucket();

    public abstract Builder toBuilder();

    public static Builder builder() {
        Builder b = new AutoValue_ServiceDefinition.Builder();
        return b.capabilities()
                .restrictions()
                .useEncryption(true)
                .useListV1(true)
                .signingRegion(Region.US_EAST_1)
                .eventualConsistencyDelay(Duration.ofSeconds(30))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .payloadSigningEnabled(false)
                .withUniqueBucket();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder host(String host);

        public Builder port(@Nullable Integer port) {
            return portSupplier(port == null ? null : () -> port);
        }

        public abstract Builder portSupplier(@Nullable Supplier<Integer> port);

        @Nullable
        abstract Supplier<Integer> portSupplier();

        public abstract Builder bucket(String bucket);

        public abstract Builder accessKeyId(String user);

        public abstract Builder secretAccessKey(String password);

        public abstract Builder capabilities(Set<Capability> capabilities);

        public abstract Builder capabilities(Capability... capabilities);

        public abstract Builder restrictions(Set<Restriction> restrictions);

        public abstract Builder restrictions(Restriction... restrictions);

        public abstract Builder signingRegion(Region region);

        public abstract Region signingRegion();

        @Nullable
        public abstract AddressingStyle addressingStyle();

        public abstract Builder addressingStyle(@Nullable AddressingStyle addressingStyle);

        public abstract Builder useListV1(boolean useListV1);

        public abstract boolean useListV1();

        public abstract Builder eventualConsistencyDelay(Duration delay);

        public abstract Duration eventualConsistencyDelay();

        public abstract Builder useEncryption(boolean useEncryption);

        public abstract Builder requestChecksumCalculation(RequestChecksumCalculation value);

        public abstract Builder responseChecksumValidation(ResponseChecksumValidation value);

        public Builder withUniqueBucket() {
            bucket("s3test-" + UUID.randomUUID());
            createBucket(true);
            return this;
        }

        public Builder withExistingBucket(String bucket) {
            bucket(bucket);
            createBucket(false);
            return this;
        }

        public abstract Builder createBucket(boolean createBucket);

        public abstract Builder payloadSigningEnabled(boolean value);

        public abstract ServiceDefinition build();

    }
}
