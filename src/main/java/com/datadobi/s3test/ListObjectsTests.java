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
                String.format("ListObjectsV1 should return all generated keys (expected: %s, received: %s)", generatedKeys, actualKeys),
                generatedKeys,
                actualKeys
        );
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
        var actualKeys = new HashSet<>(keys);
        assertEquals(
                String.format("ListObjectsV2 should return all generated keys (expected: %s, received: %s)", generatedKeys, actualKeys),
                generatedKeys,
                actualKeys
        );
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
    @SkipForQuirks({KEYS_WITH_CODEPOINTS_OUTSIDE_BMP_REJECTED})
    public void thatServerSortsInUtf8Binary() {

        bucket.putObject(A, A);
        bucket.putObject(BEFORE_SURROGATES, BEFORE_SURROGATES);
        bucket.putObject(AFTER_SURROGATES, AFTER_SURROGATES);
        bucket.putObject(SURROGATE_PAIR1, SURROGATE_PAIR1);
        bucket.putObject(SURROGATE_PAIR2, SURROGATE_PAIR2);

        if (target.hasQuirk(KEYS_ARE_SORTED_IN_UTF16_BINARY_ORDER)) {
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
        assertEquals(
                String.format("ETag should not change after COPY (expected: %s, received: %s)", putResponse.eTag(), copyResponse.copyObjectResult().eTag()),
                putResponse.eTag(),
                copyResponse.copyObjectResult().eTag()
        );

        var headObjectResponse = bucket.headObject("key");
        // HEAD of the object should return the same ETag
        assertEquals(
                String.format("HEAD should return same ETag as COPY (expected: %s, received: %s)", copyResponse.copyObjectResult().eTag(), headObjectResponse.eTag()),
                copyResponse.copyObjectResult().eTag(),
                headObjectResponse.eTag()
        );

        var listV1Response = bucket.listObjectsV1(null, null);
        var listV2Response = bucket.listObjectsV2(null, null);

        // Listing should return the same etag
        if (target.hasQuirk(ETAG_EMPTY_AFTER_COPY_OBJECT)) {
            var listV1ETag = listV1Response.contents().getFirst().eTag();
            assertEquals(
                    String.format("ListV1 should return empty ETag after COPY (expected: %s, received: %s)", "\"\"", listV1ETag),
                    "\"\"",
                    listV1ETag
            );
            var listV2ETag = listV2Response.contents().getFirst().eTag();
            assertEquals(
                    String.format("ListV2 should return empty ETag after COPY (expected: %s, received: %s)", "\"\"", listV2ETag),
                    "\"\"",
                    listV2ETag
            );
        } else {
            var listV1ETag = listV1Response.contents().getFirst().eTag();
            assertEquals(
                    String.format("ListV1 should return same ETag as COPY (expected: %s, received: %s)", copyResponse.copyObjectResult().eTag(), listV1ETag),
                    copyResponse.copyObjectResult().eTag(),
                    listV1ETag
            );
            var listV2ETag = listV2Response.contents().getFirst().eTag();
            assertEquals(
                    String.format("ListV2 should return same ETag as COPY (expected: %s, received: %s)", copyResponse.copyObjectResult().eTag(), listV2ETag),
                    copyResponse.copyObjectResult().eTag(),
                    listV2ETag
            );
        }
    }

    @Test
    public void testListEmpty() {
        var listV1Keys = bucket.listObjectKeys(V1);
        assertEquals(
                String.format("ListObjectsV1 should return empty list for empty bucket (expected: %s, received: %s)", List.of(), listV1Keys),
                List.of(),
                listV1Keys
        );
        var listV2Keys = bucket.listObjectKeys(V2);
        assertEquals(
                String.format("ListObjectsV2 should return empty list for empty bucket (expected: %s, received: %s)", List.of(), listV2Keys),
                List.of(),
                listV2Keys
        );
    }

    @Test
    public void testList() {
        var keys = Arrays.asList("a", "l", "z");
        for (var key : keys) {
            bucket.putObject(key, key);
        }

        var listV1Keys = bucket.listObjectKeys(V1);
        assertEquals(
                String.format("ListObjectsV1 should return all keys (expected: %s, received: %s)", keys, listV1Keys),
                keys,
                listV1Keys
        );
        var listV2Keys = bucket.listObjectKeys(V2);
        assertEquals(
                String.format("ListObjectsV2 should return all keys (expected: %s, received: %s)", keys, listV2Keys),
                keys,
                listV2Keys
        );
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

        assertEquals(
                String.format("Keys should be sorted in UTF-8 binary order (expected: %s, received: %s)", expectedKeys, actualKeys),
                expectedKeys,
                actualKeys
        );
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

        assertFalse(
                String.format("Result should not be truncated when listing after key space (received: %s)", result.isTruncated()),
                result.isTruncated()
        );
        assertTrue(
                String.format("Contents should be empty when listing after key space (received: %s)", result.contents()),
                result.contents().isEmpty()
        );
    }

    @Test
    public void testListObjectsWithMarkerBeforePrefix() {
        bucket.putObject("Z/A", "content");

        var result = bucket.listObjectsV1(
                r -> r.maxKeys(10).marker("A/C").prefix("Z").encodingType(EncodingType.URL)
        );

        assertFalse(
                String.format("Result should not be truncated when marker is before prefix (received: %s)", result.isTruncated()),
                result.isTruncated()
        );
        var actualSize = result.contents().size();
        assertEquals(
                String.format("Should return 1 object when marker is before prefix (expected: %s, received: %s)", 1, actualSize),
                1,
                actualSize
        );
    }

    @Test
    public void testListObjectsWithMarkerAfterPrefix() {
        bucket.putObject("A/A", "content");

        var result = bucket.listObjectsV1(
                r -> r.maxKeys(10).marker("Z/").prefix("A/").encodingType(EncodingType.URL)
        );

        assertFalse(
                String.format("Result should not be truncated when marker is after prefix (received: %s)", result.isTruncated()),
                result.isTruncated()
        );
        var actualSize = result.contents().size();
        assertEquals(
                String.format("Should return 0 objects when marker is after prefix (expected: %s, received: %s)", 0, actualSize),
                0,
                actualSize
        );
    }
}
