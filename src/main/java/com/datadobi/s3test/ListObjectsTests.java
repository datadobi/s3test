package com.datadobi.s3test;

import com.datadobi.s3test.s3.S3;
import com.datadobi.s3test.s3.S3TestBase;
import com.google.common.collect.ImmutableMap;
import org.junit.Assume;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.EncodingType;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.datadobi.s3test.s3.S3.ListObjectsVersion.V1;
import static com.datadobi.s3test.s3.S3.ListObjectsVersion.V2;
import static com.datadobi.s3test.s3.ServiceDefinition.Restriction.*;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ListObjectsTests extends S3TestBase {
    public ListObjectsTests() throws IOException {
    }

    @Test
    public void serialListObjectsV1GetsAllKeys() {
        Set<String> generatedKeys = new HashSet<>();
        for (var i = 0; i < 143; i++) {
            var key = Integer.toString(i);
            bucket.putObject(key, key);
            generatedKeys.add(key);
        }

        var keys = bucket.listObjectKeys(V2, 7);
        assertEquals(generatedKeys, new HashSet<>(keys));
    }

    @Test
    public void serialListObjectsV2GetsAllKeys() {
        Set<String> generatedKeys = new HashSet<>();
        for (var i = 0; i < 143; i++) {
            var key = Integer.toString(i);
            bucket.putObject(key, key);
            generatedKeys.add(key);
        }

        var keys = bucket.listObjectKeys(V2, 7);
        assertEquals(generatedKeys, new HashSet<>(keys));
    }

    @Test
    public void listUntilPenultimateV2ShouldIndicateTruncatedWithExistingStartAfterKey() {
        listUntilPenultimateShouldIndicateTruncated(V2, "180");
    }

    @Test
    public void listUntilPenultimateV2ShouldIndicateTruncatedWithNonExistingStartAfterKey() {
        listUntilPenultimateShouldIndicateTruncated(V2, "185");
    }

    @Test
    public void listUntilPenultimateV1ShouldIndicateTruncatedWithExistingStartAfterKey() {
        listUntilPenultimateShouldIndicateTruncated(V1, "180");
    }

    @Test
    public void listUntilPenultimateV1ShouldIndicateTruncatedWithNonExistingStartAfterKey() {
        listUntilPenultimateShouldIndicateTruncated(V1, "185");
    }

    private void listUntilPenultimateShouldIndicateTruncated(S3.ListObjectsVersion version, String startAfter) {
        bucket.putObject("180", "doesn't matter");
        bucket.putObject("190", "doesn't matter");
        bucket.putObject("200", "doesn't matter");

        List<String> keys;
        boolean truncated;
        if (version == V2) {
            var result = bucket.listObjectsV2(1, startAfter);
            truncated = result.isTruncated();
            keys = result
                    .contents()
                    .stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
        } else {
            var result = bucket.listObjectsV1(1, startAfter);
            truncated = result.isTruncated();
            keys = result
                    .contents()
                    .stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
        }

        assertThat("we set maxKeys to 1, so we expect one key in the response", keys.size(), is(1));
        assertThat("the first key lexicographically after '185' is '190'", keys, is(Collections.singletonList("190")));
        assertThat("there is one other key, '200', so the result should indicate that it's truncated", truncated, is(true));
    }

    public static final String A = "a";
    public static final String SURROGATE_PAIR1 = "\uD83D\uDC4F";
    public static final String SURROGATE_PAIR2 = "\uD83D\uDC50";
    public static final String BEFORE_SURROGATES = "\uD7FB";
    public static final String AFTER_SURROGATES = "\uFB80";
    public static final String CP_MAX = "\uDBFF\uDFFF";

    @Test
    public void thatServerSortsInUtf8Binary() {
        Assume.assumeFalse(target.hasRestrictions(KEYS_WITH_CODEPOINTS_OUTSIDE_BMP_REJECTED));

        bucket.putObject(A, A);
        bucket.putObject(BEFORE_SURROGATES, BEFORE_SURROGATES);
        bucket.putObject(AFTER_SURROGATES, AFTER_SURROGATES);
        bucket.putObject(SURROGATE_PAIR1, SURROGATE_PAIR1);
        bucket.putObject(SURROGATE_PAIR2, SURROGATE_PAIR2);

        if (target.hasRestrictions(KEYS_ARE_SORTED_IN_UTF16_BINARY_ORDER)) {
            assertEquals(List.of(A, BEFORE_SURROGATES, SURROGATE_PAIR1, SURROGATE_PAIR2, AFTER_SURROGATES),
                    bucket.listObjectKeys(V2, null, null));
            assertEquals(List.of(SURROGATE_PAIR1, SURROGATE_PAIR2, AFTER_SURROGATES),
                    bucket.listObjectKeys(V2, null, BEFORE_SURROGATES));
            assertEquals(List.of(SURROGATE_PAIR2, AFTER_SURROGATES), bucket.listObjectKeys(V2, null, SURROGATE_PAIR1));
            assertEquals(List.of(AFTER_SURROGATES), bucket.listObjectKeys(V2, null, CP_MAX));
        } else {
            assertEquals(List.of(A, BEFORE_SURROGATES, AFTER_SURROGATES, SURROGATE_PAIR1, SURROGATE_PAIR2),
                    bucket.listObjectKeys(V2, null, null));
            assertEquals(List.of(AFTER_SURROGATES, SURROGATE_PAIR1, SURROGATE_PAIR2),
                    bucket.listObjectKeys(V2, null, BEFORE_SURROGATES));
            assertEquals(List.of(SURROGATE_PAIR2), bucket.listObjectKeys(V2, null, SURROGATE_PAIR1));
            assertEquals(List.of(), bucket.listObjectKeys(V2, null, CP_MAX));
        }
    }

    @Test
    public void listReturnsSameEtagAsCopyObject() {
        var putResponse = bucket.putObject("key", "body");

        var copyResponse = s3.copyObject(
                CopyObjectRequest.builder()
                        .sourceBucket(target.bucket())
                        .sourceKey("key")
                        .destinationBucket(target.bucket())
                        .destinationKey("key")
                        .metadataDirective(MetadataDirective.REPLACE)
                        .metadata(Map.of("metakey", "metavalue"))
                        .build()
        );
        // ETag shouldn't change since the object content did not change
        assertEquals(putResponse.eTag(), copyResponse.copyObjectResult().eTag());

        var headObjectResponse = bucket.headObject("key");
        // HEAD of the object should return the same ETag
        assertEquals(copyResponse.copyObjectResult().eTag(), headObjectResponse.eTag());

        var listV1Response = bucket.listObjectsV1(null, null);
        var listV2Response = bucket.listObjectsV2(null, null);

        // Listing should return the same etag
        if (target.hasRestrictions(ETAG_EMPTY_AFTER_COPY_OBJECT)) {
            assertEquals("\"\"", listV1Response.contents().get(0).eTag());
            assertEquals("\"\"", listV2Response.contents().get(0).eTag());
        } else {
            assertEquals(copyResponse.copyObjectResult().eTag(), listV1Response.contents().get(0).eTag());
            assertEquals(copyResponse.copyObjectResult().eTag(), listV2Response.contents().get(0).eTag());
        }
    }

    @Test
    public void testListEmpty() throws IOException {
        assertEquals(List.of(), bucket.listObjectKeys(V1));
        assertEquals(List.of(), bucket.listObjectKeys(V2));
    }

    @Test
    public void testList() throws IOException {
        var keys = Arrays.asList("a", "l", "z");
        for (var key : keys) {
            bucket.putObject(key, key);
        }

        assertEquals(keys, bucket.listObjectKeys(V1));
        assertEquals(keys, bucket.listObjectKeys(V2));
    }

    private void validateListObjects(S3.ListObjectsVersion listObjectsVersion, Map<String, String> content) {
        for (var entry : content.entrySet()) {
            bucket.putObject(entry.getKey(), entry.getValue());
        }

        List<String> expectedKeys = new ArrayList<>(content.keySet());
        expectedKeys.sort(Comparator.comparing(
                key -> key.getBytes(StandardCharsets.UTF_8),
                Arrays::compareUnsigned
        ));

        var actualKeys = bucket.listObjectKeys(listObjectsVersion, 10);

        assertEquals(expectedKeys, actualKeys);
    }

    @Test
    public void testListObjectsV1() {
        validateListObjects(
                V1, ImmutableMap.of(
                        "a", "dataA",
                        "c", "dataC",
                        "z", "dataZ"
                )
        );
    }

    @Test
    public void testListObjectsV1EvilKeyWithUrlEncode() {
        validateListObjects(
                V1, ImmutableMap.of(
                        "a", "dataA",
                        "c", "dataC",
                        "z,a", "dataZ"
                )
        );
    }

    @Test
    public void testListObjectsV1EvilKeyNoUrlDecode() {
        validateListObjects(
                V1, ImmutableMap.of(
                        "a", "dataA",
                        "c", "dataC",
                        "z,a", "dataZ"
                )
        );
    }

    @Test
    public void testListObjectsV2() {
        validateListObjects(
                V2,
                ImmutableMap.of(
                        "a", "dataA",
                        "c", "dataC",
                        "z", "dataZ"
                )
        );
    }

    @Test
    public void testListObjectsV2EvilKeyWithUrlEncode() {
        validateListObjects(
                V2,
                ImmutableMap.of(
                        "a", "dataA",
                        "c", "dataC",
                        "z,a", "dataZ"
                )
        );
    }

    @Test
    public void testListObjectsV2EvilKeyNoUrlEncode() {
        validateListObjects(
                V2,
                ImmutableMap.of(
                        "a", "dataA",
                        "c", "dataC",
                        "z,a", "dataZ"
                )
        );
    }

    @Test
    public void testListObjectsAfterKeySpaceWithSeparator() {
        bucket.putObject("A/B", "content");

        var result = bucket.listObjectsV1(
                r -> r.maxKeys(10).marker("A/C").encodingType(EncodingType.URL)
        );

        assertFalse(result.isTruncated());

        if (target.hasRestrictions(LIST_OBJECTS_CAN_RETURN_INVALID_KEY)) {
            assertFalse(result.contents().isEmpty());
        } else {
            assertTrue(result.contents().isEmpty());
        }
    }

    @Test
    public void testListObjectsWithMarkerBeforePrefix() {
        bucket.putObject("Z/A", "content");

        var result = bucket.listObjectsV1(
                r -> r.maxKeys(10).marker("A/C").prefix("Z").encodingType(EncodingType.URL)
        );

        assertFalse(result.isTruncated());
        assertEquals(1, result.contents().size());
    }

    @Test
    public void testListObjectsWithMarkerAfterPrefix() {
        bucket.putObject("A/A", "content");

        var result = bucket.listObjectsV1(
                r -> r.maxKeys(10).marker("Z/").prefix("A/").encodingType(EncodingType.URL)
        );

        assertFalse(result.isTruncated());
        assertEquals(0, result.contents().size());
    }
}
