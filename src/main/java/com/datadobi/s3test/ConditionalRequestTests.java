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
import com.datadobi.s3test.s3.SkipForQuirks;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.datadobi.s3test.s3.Quirk.*;
import static org.junit.Assert.*;

public class ConditionalRequestTests extends S3TestBase {
    public ConditionalRequestTests() throws IOException {
    }

    @Test
    @SkipForQuirks({PUT_OBJECT_IF_NONE_MATCH_STAR_NOT_SUPPORTED})
    public void thatConditionalPutIfNoneMatchStarWorks() throws InterruptedException {
        // Write an object
        bucket.putObject("object", "hello");

        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        try {
            // Overwrite the object with `If-None-Match: *`.
            // This should fail since the object already exists.
            bucket.putObject(
                    b -> b.key("object").ifNoneMatch("*").build(),
                    "bar"
            );

            fail("PutObject using 'If-None-Match: *' should fail if object already exists");
        } catch (S3Exception e) {
            // Should have gotten 412 Precondition Failed
            var actualStatusCode = e.statusCode();
            assertEquals(String.format("HTTP error should be precondition failed (expected: %s, received: %s)", 412, actualStatusCode), 412, actualStatusCode);
        }
    }

    @Test
    @SkipForQuirks({PUT_OBJECT_IF_NONE_MATCH_ETAG_NOT_SUPPORTED})
    public void thatConditionalPutIfNoneMatchEtagWorks() throws IOException, InterruptedException {
        // Write an object
        var initialPutResponse = bucket.putObject("object", "hello");

        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        // Overwrite the object with `If-None-Match: <etag>` using a random etag value.
        // This should succeed since the etag does not match.
        var etag = initialPutResponse.eTag();
        var overwritePutResponse = bucket.putObject(
                b -> b.key("object").ifNoneMatch(etag),
                "bar"
        );

        // The etag should have changed compared to the initial one since the object content changed
        var actualEtag = overwritePutResponse.eTag();
        var expectedEtag = initialPutResponse.eTag();
        assertNotEquals(String.format("Object etag should have changed (expected different from: %s, received: %s)", expectedEtag, actualEtag), actualEtag, expectedEtag);

        // Read back the object
        GetObjectResponse getResponse;
        String content;
        try (var response = bucket.getObject("object")) {
            getResponse = response.response();
            content = new String(response.readAllBytes(), StandardCharsets.UTF_8);
        }

        // The contents and etag should match those from the overwrite request
        assertEquals(String.format("Object content should match overwrite request (expected: %s, received: %s)", "bar", content), "bar", content);
        var expectedResponseEtag = overwritePutResponse.eTag();
        var actualResponseEtag = getResponse.eTag();
        assertEquals(String.format("Object etag should match overwrite response (expected: %s, received: %s)", expectedResponseEtag, actualResponseEtag), expectedResponseEtag, actualResponseEtag);

        try {
            // Overwrite the object with `If-None-Match: <etag>` using the current etag value.
            // This should fail since the etag does not match.
            bucket.putObject(
                    b -> b.key("object").ifNoneMatch(overwritePutResponse.eTag()),
                    "baz"
            );

            fail("PutObject using 'If-None-Match: \"<etag>\"' should fail if object etag matches current etag");
        } catch (S3Exception e) {
            // Should have gotten 412 Precondition Failed
            var actualStatusCode = e.statusCode();
            assertEquals(String.format("HTTP error should be precondition failed (expected: %s, received: %s)", 412, actualStatusCode), 412, actualStatusCode);
        }
    }

    @Test
    @SkipForQuirks({PUT_OBJECT_IF_MATCH_ETAG_NOT_SUPPORTED})
    public void thatConditionalPutIfMatchEtagWorks() throws IOException, InterruptedException {
        // Write an object
        var initialPutResponse = bucket.putObject("object", "hello");

        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        // Overwrite the object with `If-Match: <etag>` using the current etag value.
        // This should succeed since the etag matches.
        var etag = initialPutResponse.eTag();
        var overwritePutResponse = bucket.putObject(
                b -> b.key("object").ifMatch(etag),
                "bar"
        );

        // The etag should have changed compared to the initial one since the object content changed
        var actualEtag = overwritePutResponse.eTag();
        var expectedEtag = initialPutResponse.eTag();
        assertNotEquals(String.format("Object etag should have changed (expected different from: %s, received: %s)", expectedEtag, actualEtag), expectedEtag, actualEtag);

        // Read back the object
        GetObjectResponse getResponse;
        String content;
        try (var response = bucket.getObject("object")) {
            getResponse = response.response();
            content = new String(response.readAllBytes(), StandardCharsets.UTF_8);
        }

        // The contents and etag should match those from the overwrite request
        assertEquals(String.format("Object content should match overwrite request (expected: %s, received: %s)", "bar", content), "bar", content);
        var expectedResponseEtag = overwritePutResponse.eTag();
        var actualResponseEtag = getResponse.eTag();
        assertEquals(String.format("Object etag should match overwrite response (expected: %s, received: %s)", expectedResponseEtag, actualResponseEtag), expectedResponseEtag, actualResponseEtag);

        try {
            // Overwrite the object with `If-Match: <etag>` using a stale etag value.
            // This should fail since the etag does not match.
            bucket.putObject(
                    b -> b.key("object").ifMatch(initialPutResponse.eTag()),
                    "baz"
            );

            fail("PutObject using 'If-Match: \"<etag>\"' should fail if object etag does not match current etag");
        } catch (S3Exception e) {
            // Should have gotten 412 Precondition Failed
            var actualStatusCode = e.statusCode();
            assertEquals(String.format("HTTP error should be precondition failed (expected: %s, received: %s)", 412, actualStatusCode), 412, actualStatusCode);
        }
    }
}
