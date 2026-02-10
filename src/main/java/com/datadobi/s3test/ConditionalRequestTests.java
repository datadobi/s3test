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
package com.datadobi.s3test;

import com.datadobi.s3test.s3.S3TestBase;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.datadobi.s3test.s3.Quirk.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ConditionalRequestTests extends S3TestBase {
    public ConditionalRequestTests() throws IOException {
    }

    /**
     * Puts an object, then attempts overwrite with If-None-Match: * (create-only semantics).
     * Expected: First put succeeds; second put either fails with 412 Precondition Failed (object exists)
     * or 501 if unsupported; if it wrongly succeeds, GET returns new content and test fails.
     */
    @Test
    public void thatConditionalPutIfNoneMatchStarWorks() throws IOException, InterruptedException {
        bucket.putObject("object", "hello");

        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        try {
            var overwritePutResponse = bucket.putObject(
                    b -> b.key("object").ifNoneMatch("*").build(),
                    "bar"
            );

            GetObjectResponse getResponse;
            String content;
            try (var response = bucket.getObject("object")) {
                getResponse = response.response();
                content = new String(response.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!target.hasQuirk(PUT_OBJECT_IF_NONE_MATCH_STAR_NOT_SUPPORTED)) {
                assertEquals("bar", content);
                assertEquals(overwritePutResponse.eTag(), getResponse.eTag());
                fail("PutObject using 'If-None-Match: *' should fail if object already exists");
            }
        } catch (S3Exception e) {
            if (target.hasQuirk(PUT_OBJECT_IF_NONE_MATCH_STAR_NOT_SUPPORTED)) {
                assertEquals(501, e.statusCode());
            } else {
                assertEquals(412, e.statusCode());
            }
        }
    }

    /**
     * Puts an object, then attempts overwrite with If-None-Match: "<etag>" (create-only if etag differs).
     * Expected: Overwrite fails with 412 (or 501 if unsupported); object content remains unchanged.
     */
    @Test
    public void thatConditionalPutIfNoneMatchEtagWorks() throws IOException, InterruptedException {
        var initialPutResponse = bucket.putObject(
                b -> b.key("object"),
                "hello"
        );

        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        try {
            var overwritePutResponse = bucket.putObject(
                    b -> b.key("object").ifNoneMatch(initialPutResponse.eTag()),
                    "bar"
            );

            GetObjectResponse getResponse;
            String content;
            try (var response = bucket.getObject("object")) {
                getResponse = response.response();
                content = new String(response.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (target.hasQuirk(PUT_OBJECT_IF_NONE_MATCH_ETAG_NOT_SUPPORTED)) {
                assertEquals("bar", content);
                assertEquals(overwritePutResponse.eTag(), getResponse.eTag());
                fail("PutObject using 'If-None-Match: \"<etag>\"' should fail if object already exists");
            }
        } catch (S3Exception e) {
            if (target.hasQuirk(PUT_OBJECT_IF_NONE_MATCH_ETAG_NOT_SUPPORTED)) {
                assertEquals(501, e.statusCode());
            } else {
                assertEquals(412, e.statusCode());
            }
        }
    }

    /**
     * Puts an object, then overwrites with If-Match: "<etag>" (update-only if etag matches).
     * Expected: Overwrite succeeds; GET returns new content and new ETag; or 412/501 if precondition fails.
     */
    @Test
    public void thatConditionalPutIfMatchEtagWorks() throws IOException, InterruptedException {
        var initialPutResponse = bucket.putObject("object", "hello");

        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        try {
            var etag = initialPutResponse.eTag();
            var overwritePutResponse = bucket.putObject(
                    b -> b.key("object").ifMatch(etag),
                    "bar"
            );

            GetObjectResponse getResponse;
            String content;
            try (var response = bucket.getObject("object")) {
                getResponse = response.response();
                content = new String(response.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!target.hasQuirk(PUT_OBJECT_IF_MATCH_ETAG_NOT_SUPPORTED)) {
                assertEquals("bar", content);
                assertEquals(overwritePutResponse.eTag(), getResponse.eTag());
                fail("PutObject using 'If-Match: \"<etag>\"' should fail if object etag does not match");
            }
        } catch (S3Exception e) {
            if (!target.hasQuirk(PUT_OBJECT_IF_MATCH_ETAG_NOT_SUPPORTED)) {
                assertEquals(501, e.statusCode());
            } else {
                assertEquals(412, e.statusCode());
            }
        }
    }
}
