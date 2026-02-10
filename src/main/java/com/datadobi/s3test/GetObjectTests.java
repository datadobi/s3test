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

import com.datadobi.s3test.http.ContentRange;
import com.datadobi.s3test.http.Range;
import com.datadobi.s3test.http.RangeSpec;
import com.datadobi.s3test.s3.S3TestBase;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GetObjectTests extends S3TestBase {
    public GetObjectTests() throws IOException {
    }

    /**
     * Puts an object and retrieves it with GET.
     * Expected: GetObject returns the full body; content matches original string "Hello, World!".
     */
    @Test
    public void testGetObject() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        try (var objectInputStream = bucket.getObject("foo")) {
            var bytes = objectInputStream.readAllBytes();
            assertEquals(fullContent, new String(bytes, StandardCharsets.UTF_8));
        }
    }

    /**
     * Puts an empty object and retrieves it with GET.
     * Expected: GetObject returns 0 bytes; response contentLength is 0.
     */
    @Test
    public void testGetEmptyObject() throws IOException {
        var emptyContent = "";
        bucket.putObject("empty", emptyContent);

        try (var objectInputStream = bucket.getObject("empty")) {
            var bytes = objectInputStream.readAllBytes();
            assertEquals(0, bytes.length);
            assertEquals(Long.valueOf(0L), objectInputStream.response().contentLength());
        }
    }

    /**
     * Calls GetObject for a key that does not exist.
     * Expected: S3Exception with HTTP status 404 (Not Found).
     */
    @Test
    public void testGetKeyThatDoesNotExist() throws IOException {
        try (var objectInputStream = bucket.getObject("foo")) {
            fail("Should return HTTP 404");
        } catch (S3Exception e) {
            assertEquals(404, e.statusCode());
        }
    }

    /**
     * Gets a byte range (bytes 7 to end) of an object using Range header.
     * Expected: Response body is "World!"; Content-Range and Content-Length match the range.
     */
    @Test
    public void testGetPartial() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        var startPos = 7L;
        long lastPos = fullContent.length() - 1;

        var range = new Range("bytes", ImmutableList.of(new RangeSpec(startPos, null)));
        var contentRange = new ContentRange("bytes", startPos, lastPos, lastPos + 1);

        try (var objectInputStream = bucket.getObject("foo", null, range)) {
            var bytes = objectInputStream.readAllBytes();
            assertEquals("World!", new String(bytes, StandardCharsets.UTF_8));

            var response = objectInputStream.response();
            assertEquals(contentRange.toString(), response.contentRange());
            assertEquals(Long.valueOf(lastPos - startPos + 1), response.contentLength());
        }
    }

    /**
     * Requests a byte range that starts beyond the object size (e.g. bytes 200-).
     * Expected: S3Exception with HTTP status 416 (Range Not Satisfiable).
     */
    @Test
    public void testGetPartialUnsatisfiable() {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        try {
            bucket.getObject("foo", null, new Range("bytes", ImmutableList.of(new RangeSpec(200L, null))));
            fail("Should return HTTP 416");
        } catch (S3Exception e) {
            assertEquals(416, e.statusCode());
        }
    }

    /**
     * Gets a byte range with both start and end (bytes 0-4).
     * Expected: Response body is "Hello"; Content-Range and Content-Length are correct.
     */
    @Test
    public void testGetPartialWithEndPosition() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        var startPos = 0L;
        var endPos = 4L;

        var range = new Range("bytes", ImmutableList.of(new RangeSpec(startPos, endPos)));
        var contentRange = new ContentRange("bytes", startPos, endPos, (long) fullContent.length());

        try (var objectInputStream = bucket.getObject("foo", null, range)) {
            var bytes = objectInputStream.readAllBytes();
            assertEquals("Hello", new String(bytes, StandardCharsets.UTF_8));

            var response = objectInputStream.response();
            assertEquals(contentRange.toString(), response.contentRange());
            assertEquals(Long.valueOf(endPos - startPos + 1), response.contentLength());
        }
    }

    /**
     * Gets a middle byte range (bytes 7-11) of "Hello, World!".
     * Expected: Response body is "World"; Content-Range and Content-Length match.
     */
    @Test
    public void testGetPartialMiddleRange() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        var startPos = 7L;
        var endPos = 11L;

        var range = new Range("bytes", ImmutableList.of(new RangeSpec(startPos, endPos)));
        var contentRange = new ContentRange("bytes", startPos, endPos, (long) fullContent.length());

        try (var objectInputStream = bucket.getObject("foo", null, range)) {
            var bytes = objectInputStream.readAllBytes();
            assertEquals("World", new String(bytes, StandardCharsets.UTF_8));

            var response = objectInputStream.response();
            assertEquals(contentRange.toString(), response.contentRange());
            assertEquals(Long.valueOf(endPos - startPos + 1), response.contentLength());
        }
    }

    /**
     * Gets the last N bytes using suffix-length range (e.g. bytes=-6 for last 6 bytes).
     * Expected: Response body is "World!" (last 6 bytes of "Hello, World!").
     */
    @Test
    public void testGetPartialLastBytes() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        // Get last 6 bytes using suffix-length range
        var range = new Range("bytes", ImmutableList.of(new RangeSpec(null, 6L)));

        try (var objectInputStream = bucket.getObject("foo", null, range)) {
            var bytes = objectInputStream.readAllBytes();
            assertEquals("World!", new String(bytes, StandardCharsets.UTF_8));
        }
    }

    /**
     * Requests a range with end position beyond object size (e.g. bytes 10-100).
     * Expected: Server returns from start to end of object only; body is "ld!" (bytes 10-12).
     */
    @Test
    public void testGetPartialBeyondEnd() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        var startPos = 10L;
        var endPos = 100L; // Beyond actual content

        var range = new Range("bytes", ImmutableList.of(new RangeSpec(startPos, endPos)));

        try (var objectInputStream = bucket.getObject("foo", null, range)) {
            var bytes = objectInputStream.readAllBytes();
            // Should return from startPos to end of file
            assertEquals("ld!", new String(bytes, StandardCharsets.UTF_8));
        }
    }
}
