/*
 *
 *  Copyright Datadobi
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
package com.datadobi.s3test;

import com.datadobi.s3test.s3.S3;
import com.datadobi.s3test.s3.S3TestBase;
import com.datadobi.s3test.s3.SkipForQuirks;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.EncodingType;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.datadobi.s3test.s3.Quirk.*;
import static com.datadobi.s3test.s3.S3.ListObjectsVersion.V1;
import static com.datadobi.s3test.s3.S3.ListObjectsVersion.V2;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ListObjectsTests extends S3TestBase {
    public ListObjectsTests() throws IOException {
    }

    /**
     * Uploads 143 objects, then lists all keys (implementation uses V2) with page size 7.
     * Expected: All 143 keys are returned (paginated); set of keys matches uploaded keys.
     */
    @Test
    public void serialListObjectsV1GetsAllKeys() {
        Set<String> generatedKeys = new HashSet<>();
        for (var i = 0; i < 143; i++) {
            var key = Integer.toString(i);
            bucket.putObject(key, key);
            generatedKeys.add(key);
        }

        var keys = bucket.listObjectKeys(V2, 7);
        var actualKeys = new HashSet<>(keys);
        assertEquals(
                "ListObjectsV1 should return all generated keys",
                generatedKeys,
                actualKeys
        );
    }

    /**
     * Uploads 143 objects, then lists all keys using ListObjects V2 with page size 7.
     * Expected: All 143 keys are returned (paginated); set of keys matches uploaded keys.
     */
    @Test
    public void serialListObjectsV2GetsAllKeys() {
        Set<String> generatedKeys = new HashSet<>();
        for (var i = 0; i < 143; i++) {
            var key = Integer.toString(i);
            bucket.putObject(key, key);
            generatedKeys.add(key);
        }

        var keys = bucket.listObjectKeys(V2, 7);
        var actualKeys = new HashSet<>(keys);
        assertEquals(
                "ListObjectsV2 should return all generated keys",
                generatedKeys,
                actualKeys
        );
    }

    /**
     * Lists with V2, maxKeys=1, startAfter="180" when keys "180","190","200" exist.
     * Expected: One key returned ("190"); isTruncated is true (more keys exist).
     */
    @Test
    public void listUntilPenultimateV2ShouldIndicateTruncatedWithExistingStartAfterKey() {
        listUntilPenultimateShouldIndicateTruncated(V2, "180");
    }

    /**
     * Same as above but startAfter="185" (non-existing key); first key after "185" is "190".
     * Expected: One key "190" returned; isTruncated is true.
     */
    @Test
    public void listUntilPenultimateV2ShouldIndicateTruncatedWithNonExistingStartAfterKey() {
        listUntilPenultimateShouldIndicateTruncated(V2, "185");
    }

    /**
     * Lists with V1 (marker), maxKeys=1, startAfter="180"; keys "180","190","200" exist.
     * Expected: One key "190"; isTruncated true.
     */
    @Test
    public void listUntilPenultimateV1ShouldIndicateTruncatedWithExistingStartAfterKey() {
        listUntilPenultimateShouldIndicateTruncated(V1, "180");
    }

    /**
     * Same with V1 and startAfter="185" (non-existing).
     * Expected: One key "190"; isTruncated true.
     */
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

    /**
     * Puts objects with keys containing BMP and non-BMP (surrogate) codepoints; lists and checks sort order.
     * Expected: Keys are returned in UTF-8 binary order (or UTF-16 order if quirk); startAfter filtering works accordingly.
     */
    @Test
    @SkipForQuirks({KEYS_WITH_CODEPOINTS_OUTSIDE_BMP_REJECTED})
    public void thatServerSortsInUtf8Binary() {

        bucket.putObject(A, A);
        bucket.putObject(BEFORE_SURROGATES, BEFORE_SURROGATES);
        bucket.putObject(AFTER_SURROGATES, AFTER_SURROGATES);
        bucket.putObject(SURROGATE_PAIR1, SURROGATE_PAIR1);
        bucket.putObject(SURROGATE_PAIR2, SURROGATE_PAIR2);

        if (target.hasQuirk(KEYS_ARE_SORTED_IN_UTF16_BINARY_ORDER)) {
            assertEquals("Keys should be sorted in UTF-16 binary order",
                    List.of(A, BEFORE_SURROGATES, SURROGATE_PAIR1, SURROGATE_PAIR2, AFTER_SURROGATES),
                    bucket.listObjectKeys(V2, null, null));
            assertEquals("Keys after BEFORE_SURROGATES should be in UTF-16 order",
                    List.of(SURROGATE_PAIR1, SURROGATE_PAIR2, AFTER_SURROGATES),
                    bucket.listObjectKeys(V2, null, BEFORE_SURROGATES));
            assertEquals("Keys after SURROGATE_PAIR1 should be in UTF-16 order",
                    List.of(SURROGATE_PAIR2, AFTER_SURROGATES), bucket.listObjectKeys(V2, null, SURROGATE_PAIR1));
            assertEquals("Keys after CP_MAX should be in UTF-16 order",
                    List.of(AFTER_SURROGATES), bucket.listObjectKeys(V2, null, CP_MAX));
        } else {
            assertEquals("Keys should be sorted in UTF-8 binary order",
                    List.of(A, BEFORE_SURROGATES, AFTER_SURROGATES, SURROGATE_PAIR1, SURROGATE_PAIR2),
                    bucket.listObjectKeys(V2, null, null));
            assertEquals("Keys after BEFORE_SURROGATES should be in UTF-8 order",
                    List.of(AFTER_SURROGATES, SURROGATE_PAIR1, SURROGATE_PAIR2),
                    bucket.listObjectKeys(V2, null, BEFORE_SURROGATES));
            assertEquals("Keys after SURROGATE_PAIR1 should be in UTF-8 order",
                    List.of(SURROGATE_PAIR2), bucket.listObjectKeys(V2, null, SURROGATE_PAIR1));
            assertEquals("No keys should be after CP_MAX in UTF-8 order",
                    List.of(), bucket.listObjectKeys(V2, null, CP_MAX));
        }
    }

    /**
     * Puts an object, copies it in-place with metadata replace; checks LIST and HEAD ETags.
     * Expected: Copy preserves content so ETag unchanged; LIST V1/V2 return same ETag (or "" if quirk ETAG_EMPTY_AFTER_COPY_OBJECT).
     */
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
        assertEquals("ETag should remain same after metadata-only copy", putResponse.eTag(), copyResponse.copyObjectResult().eTag());

        var headObjectResponse = bucket.headObject("key");
        // HEAD of the object should return the same ETag
        assertEquals("HEAD should return same ETag as copy response", copyResponse.copyObjectResult().eTag(), headObjectResponse.eTag());

        var listV1Response = bucket.listObjectsV1(null, null);
        var listV2Response = bucket.listObjectsV2(null, null);

        // Listing should return the same etag
        if (target.hasQuirk(ETAG_EMPTY_AFTER_COPY_OBJECT)) {
            assertEquals("ETag should be empty string after copy when quirk is present", "\"\"", listV1Response.contents().getFirst().eTag());
            assertEquals("ETag should be empty string after copy when quirk is present", "\"\"", listV2Response.contents().getFirst().eTag());
        } else {
            assertEquals("List V1 should return same ETag as copy response", copyResponse.copyObjectResult().eTag(), listV1Response.contents().getFirst().eTag());
            assertEquals("List V2 should return same ETag as copy response", copyResponse.copyObjectResult().eTag(), listV2Response.contents().getFirst().eTag());
        }
    }

    /**
     * Lists objects on an empty bucket (V1 and V2).
     * Expected: Both return empty list of keys.
     */
    @Test
    public void testListEmpty() {
        assertEquals("V1 should return empty list for empty bucket", List.of(), bucket.listObjectKeys(V1));
        assertEquals("V2 should return empty list for empty bucket", List.of(), bucket.listObjectKeys(V2));
    }

    /**
     * Puts three objects ("a","l","z") and lists with V1 and V2.
     * Expected: Both return keys in order ["a","l","z"].
     */
    @Test
    public void testList() {
        var keys = Arrays.asList("a", "l", "z");
        for (var key : keys) {
            bucket.putObject(key, key);
        }

        assertEquals("V1 should return all keys", keys, bucket.listObjectKeys(V1));
        assertEquals("V2 should return all keys", keys, bucket.listObjectKeys(V2));
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

        assertEquals("Listed keys should match expected keys in UTF-8 binary order", expectedKeys, actualKeys);
    }

    /**
     * Puts objects with keys "a","c","z" and lists with ListObjects V1.
     * Expected: Keys returned in UTF-8 binary order: ["a","c","z"].
     */
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

    /**
     * Lists with V1 when a key contains comma ("z,a"); uses URL encoding.
     * Expected: Keys returned in UTF-8 order including "z,a".
     */
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

    /**
     * Same keys as above; validates V1 listing without relying on URL decode behavior.
     * Expected: Keys ["a","c","z,a"] in order.
     */
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

    /**
     * Puts objects "a","c","z" and lists with ListObjects V2.
     * Expected: Keys ["a","c","z"] in UTF-8 binary order.
     */
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

    /**
     * Lists with V2 when key "z,a" exists; URL encoding.
     * Expected: "z,a" included in listed keys in correct order.
     */
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

    /**
     * Same as above for V2 without URL encode; key "z,a" present.
     * Expected: Keys ["a","c","z,a"].
     */
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

    /**
     * Puts "A/B", then lists V1 with marker="A/C" (after "A/B" in key space).
     * Expected: Not truncated; contents empty (no keys after marker in range).
     */
    @Test
    public void testListObjectsAfterKeySpaceWithSeparator() {
        bucket.putObject("A/B", "content");

        var result = bucket.listObjectsV1(
                r -> r.maxKeys(10).marker("A/C").encodingType(EncodingType.URL)
        );

        assertFalse("Result should not be truncated", result.isTruncated());
        assertTrue("Contents should be empty when marker is after all keys", result.contents().isEmpty());
    }

    /**
     * Puts "Z/A"; lists V1 with prefix="Z" and marker="A/C" (marker before prefix range).
     * Expected: Not truncated; one content "Z/A" returned (marker does not restrict prefix match).
     */
    @Test
    public void testListObjectsWithMarkerBeforePrefix() {
        bucket.putObject("Z/A", "content");

        var result = bucket.listObjectsV1(
                r -> r.maxKeys(10).marker("A/C").prefix("Z").encodingType(EncodingType.URL)
        );

        assertFalse("Result should not be truncated", result.isTruncated());
        assertEquals("Should return one object matching prefix", 1, result.contents().size());
    }

    /**
     * Puts "A/A"; lists V1 with prefix="A/" and marker="Z/" (marker after prefix).
     * Expected: No contents (marker is past prefix range); not truncated.
     */
    @Test
    public void testListObjectsWithMarkerAfterPrefix() {
        bucket.putObject("A/A", "content");

        var result = bucket.listObjectsV1(
                r -> r.maxKeys(10).marker("Z/").prefix("A/").encodingType(EncodingType.URL)
        );

        assertFalse("Result should not be truncated", result.isTruncated());
        assertEquals("Should return no objects when marker is after prefix", 0, result.contents().size());
    }
}
