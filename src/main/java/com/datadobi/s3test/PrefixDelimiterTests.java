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

import com.datadobi.s3test.s3.S3TestBase;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * <p>This test checks that an S3 implementation follows the S3, list objects specs.</p>
 */

public class PrefixDelimiterTests extends S3TestBase {
    public PrefixDelimiterTests() throws IOException {
    }

    @Test
    public void testSimple() {
        bucket.putObject("a", "a");

        for (var i = 0; i < 10; i++) {
            bucket.putObject("a/b/" + i, Integer.toString(i));
        }
        for (var i = 0; i < 10; i++) {
            bucket.putObject("a/c/" + i, Integer.toString(i));
        }

        bucket.putObject("a/d", "d");

        var response = list(
                r -> r
                        .maxKeys(10)
                        .startAfter(null)
                        .prefix("a/")
                        .delimiter("/")
                        .encodingType(EncodingType.URL)
                        .build(),
                List.of("a/d"),
                List.of("a/b/", "a/c/")
        );
        assertFalse("Result should not be truncated", response.isTruncated());
    }

    @Test
    public void testTruncatedPrefix() {
        bucket.putObject("a", "a");

        for (var i = 0; i < 10; i++) {
            bucket.putObject("a/b/" + i, Integer.toString(i));
        }
        for (var i = 0; i < 10; i++) {
            bucket.putObject("a/c/" + i, Integer.toString(i));
        }

        bucket.putObject("a/d", "d");

        var result = list(
                r -> r.maxKeys(10)
                        .startAfter(null)
                        .prefix("a/b/")
                        .delimiter("/")
                        .encodingType(EncodingType.URL)
                        .build(),
                List.of("a/b/0", "a/b/1", "a/b/2", "a/b/3", "a/b/4", "a/b/5", "a/b/6", "a/b/7", "a/b/8", "a/b/9"),
                List.of()
        );
        assertFalse("Result should not be truncated", result.isTruncated());
    }

    @Test
    public void testPrefixOnly() {
        bucket.putObject("a", "a");

        for (var i = 0; i < 10; i++) {
            bucket.putObject("a/b/" + i, Integer.toString(i));
        }
        for (var i = 0; i < 10; i++) {
            bucket.putObject("a/c/" + i, Integer.toString(i));
        }

        bucket.putObject("a/d", "d");

        Consumer<ListObjectsV2Request.Builder> request = r -> r
                .maxKeys(10)
                .startAfter(null)
                .prefix("a/")
                .encodingType(EncodingType.URL);

        var result = list(
                request,
                List.of("a/b/0", "a/b/1", "a/b/2", "a/b/3", "a/b/4", "a/b/5", "a/b/6", "a/b/7", "a/b/8", "a/b/9"),
                List.of()
        );

        var ct1 = result.nextContinuationToken();
        result = list(
                request.andThen(r -> r.continuationToken(ct1)),
                List.of("a/c/0", "a/c/1", "a/c/2", "a/c/3", "a/c/4", "a/c/5", "a/c/6", "a/c/7", "a/c/8", "a/c/9"),
                List.of()
        );
        var ct2 = result.nextContinuationToken();
        result = list(
                request.andThen(r -> r.continuationToken(ct2)),
                List.of("a/d"),
                List.of()
        );
    }

    @Test
    public void testMorePrefixesThanMaxKeys() {
        for (var i = 0; i < 15; i++) {
            bucket.putObject("a/" + i, Integer.toString(i));
            bucket.putObject("a/" + i + "/b", Integer.toString(i));
        }

        for (var i = 0; i < 15; i++) {
            bucket.putObject("a/" + (char) ('b' + i) + "/" + i, "");
        }

        Consumer<ListObjectsV2Request.Builder> request = r -> r
                .maxKeys(10)
                .startAfter(null)
                .prefix("a/")
                .delimiter("/")
                .encodingType(EncodingType.URL);

        var result = list(
                request.andThen(r -> r.continuationToken(null)),
                List.of("a/0", "a/1", "a/10", "a/11", "a/12"),
                List.of("a/0/", "a/1/", "a/10/", "a/11/", "a/12/")
        );
        var ct1 = result.nextContinuationToken();
        result = list(
                request.andThen(r -> r.continuationToken(ct1)),
                List.of("a/13", "a/14", "a/2", "a/3", "a/4"),
                List.of("a/13/", "a/14/", "a/2/", "a/3/", "a/4/")
        );
        var ct2 = result.nextContinuationToken();
        result = list(
                request.andThen(r -> r.continuationToken(ct2)),
                List.of("a/5", "a/6", "a/7", "a/8", "a/9"),
                List.of("a/5/", "a/6/", "a/7/", "a/8/", "a/9/")
        );
        var ct3 = result.nextContinuationToken();
        result = list(
                request.andThen(r -> r.continuationToken(ct3)),
                List.of(),
                List.of("a/b/", "a/c/", "a/d/", "a/e/", "a/f/", "a/g/", "a/h/", "a/i/", "a/j/", "a/k/")
        );
        var ct4 = result.nextContinuationToken();
        result = list(
                request.andThen(r -> r.continuationToken(ct4)),
                List.of(),
                List.of("a/l/", "a/m/", "a/n/", "a/o/", "a/p/")
        );
    }

    @Test
    public void testNullPrefix() {
        bucket.putObject("a", "a");

        // Intentionally create 1000 objects to see if "d" is returned or not
        for (var i = 0; i < 500; i++) {
            bucket.putObject("b/" + i, Integer.toString(i));
        }
        for (var i = 0; i < 500; i++) {
            bucket.putObject("c/" + i, Integer.toString(i));
        }

        bucket.putObject("d", "d");

        list(
                r -> r
                        .maxKeys(10)
                        .startAfter(null)
                        .prefix(null)
                        .delimiter("/")
                        .encodingType(EncodingType.URL)
                        .build(),
                List.of("a", "d"),
                List.of("b/", "c/")
        );
    }

    @Test
    public void testSlashPrefix() {
        bucket.putObject("a", "a");

        for (var i = 0; i < 10; i++) {
            bucket.putObject("a/b/" + i, Integer.toString(i));
        }
        for (var i = 0; i < 10; i++) {
            bucket.putObject("a/c/" + i, Integer.toString(i));
        }

        bucket.putObject("a/d", "d");

        list(
                r -> r.maxKeys(10)
                        .startAfter(null)
                        .prefix("/")
                        .delimiter("/")
                        .encodingType(EncodingType.URL)
                        .build(),
                List.of(),
                List.of()
        );
    }

    @Test
    public void testPrefixMatchingObjectKey() {
        bucket.putObject("a", "a");

        // Intentionally create an object with a key matching the prefix to see if it's returned or not
        bucket.putObject("b/", "");
        for (var i = 0; i < 200; i++) {
            bucket.putObject("b/" + i, Integer.toString(i));
        }
        for (var i = 0; i < 10; i++) {
            bucket.putObject("c/" + i, Integer.toString(i));
        }

        bucket.putObject("d", "d");

        list(
                r -> r
                        .maxKeys(10)
                        .startAfter(null)
                        .prefix("b/")
                        .delimiter("/")
                        .encodingType(EncodingType.URL)
                        .build(),
                List.of("b/", "b/0", "b/1", "b/10", "b/100", "b/101", "b/102", "b/103", "b/104", "b/105"),
                List.of()
        );
    }

    @Test
    public void testSingleObjectIsReportedAsCommonPrefix() {
        // Intentionally create an object with a key matching the prefix to see if it's returned or not
        bucket.putObject("b/", "");

        list(
                r -> r
                        .maxKeys(10)
                        .startAfter(null)
                        .prefix("")
                        .delimiter("/")
                        .encodingType(EncodingType.URL)
                        .build(),
                List.of(),
                List.of("b/")
        );
    }

    private ListObjectsV2Response list(Consumer<ListObjectsV2Request.Builder> request, List<String> expectedKeys, List<String> expectedPrefixes) {
        var result = bucket.listObjectsV2(request);

        assertNotNull("List result should not be null", result);

        var actualKeys = result.contents().stream().map(S3Object::key).collect(Collectors.toList());
        assertEquals(
                "Returned keys should match expected keys",
                expectedKeys,
                actualKeys
        );

        var actualPrefixes = result.commonPrefixes().stream().map(CommonPrefix::prefix).collect(Collectors.toList());
        assertEquals(
                "Common prefixes should match expected prefixes",
                expectedPrefixes,
                actualPrefixes
        );

        return result;
    }
}
