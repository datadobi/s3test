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

    @Test
    public void testGetObject() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        try (var objectInputStream = bucket.getObject("foo")) {
            var bytes = objectInputStream.readAllBytes();
            var actualContent = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(
                    String.format("Content mismatch (expected: %s, received: %s)", fullContent, actualContent),
                    fullContent,
                    actualContent
            );
        }
    }

    @Test
    public void testGetEmptyObject() throws IOException {
        var emptyContent = "";
        bucket.putObject("empty", emptyContent);

        try (var objectInputStream = bucket.getObject("empty")) {
            var bytes = objectInputStream.readAllBytes();
            assertEquals(
                    String.format("Byte array length mismatch (expected: %s, received: %s)", 0, bytes.length),
                    0,
                    bytes.length
            );
            assertEquals(
                    String.format("Content length mismatch (expected: %s, received: %s)", 0L, objectInputStream.response().contentLength()),
                    Long.valueOf(0L),
                    objectInputStream.response().contentLength()
            );
        }
    }

    @Test
    public void testGetKeyThatDoesNotExist() throws IOException {
        try (var objectInputStream = bucket.getObject("foo")) {
            fail("Should return HTTP 404");
        } catch (S3Exception e) {
            assertEquals(
                    String.format("Status code mismatch (expected: %s, received: %s)", 404, e.statusCode()),
                    404,
                    e.statusCode()
            );
        }
    }

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
            var actualContent = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(
                    String.format("Partial content mismatch (expected: %s, received: %s)", "World!", actualContent),
                    "World!",
                    actualContent
            );

            var response = objectInputStream.response();
            assertEquals(
                    String.format("Content range mismatch (expected: %s, received: %s)", contentRange.toString(), response.contentRange()),
                    contentRange.toString(),
                    response.contentRange()
            );
            assertEquals(
                    String.format("Content length mismatch (expected: %s, received: %s)", lastPos - startPos + 1, response.contentLength()),
                    Long.valueOf(lastPos - startPos + 1),
                    response.contentLength()
            );
        }
    }

    @Test
    public void testGetPartialUnsatisfiable() {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        try {
            bucket.getObject("foo", null, new Range("bytes", ImmutableList.of(new RangeSpec(200L, null))));
            fail("Should return HTTP 416");
        } catch (S3Exception e) {
            assertEquals(
                    String.format("Status code mismatch (expected: %s, received: %s)", 416, e.statusCode()),
                    416,
                    e.statusCode()
            );
        }
    }

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
            var actualContent = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(
                    String.format("Partial content mismatch (expected: %s, received: %s)", "Hello", actualContent),
                    "Hello",
                    actualContent
            );

            var response = objectInputStream.response();
            assertEquals(
                    String.format("Content range mismatch (expected: %s, received: %s)", contentRange.toString(), response.contentRange()),
                    contentRange.toString(),
                    response.contentRange()
            );
            assertEquals(
                    String.format("Content length mismatch (expected: %s, received: %s)", endPos - startPos + 1, response.contentLength()),
                    Long.valueOf(endPos - startPos + 1),
                    response.contentLength()
            );
        }
    }

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
            var actualContent = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(
                    String.format("Partial content mismatch (expected: %s, received: %s)", "World", actualContent),
                    "World",
                    actualContent
            );

            var response = objectInputStream.response();
            assertEquals(
                    String.format("Content range mismatch (expected: %s, received: %s)", contentRange.toString(), response.contentRange()),
                    contentRange.toString(),
                    response.contentRange()
            );
            assertEquals(
                    String.format("Content length mismatch (expected: %s, received: %s)", endPos - startPos + 1, response.contentLength()),
                    Long.valueOf(endPos - startPos + 1),
                    response.contentLength()
            );
        }
    }

    @Test
    public void testGetPartialLastBytes() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        // Get last 6 bytes using suffix-length range
        var range = new Range("bytes", ImmutableList.of(new RangeSpec(null, 6L)));

        try (var objectInputStream = bucket.getObject("foo", null, range)) {
            var bytes = objectInputStream.readAllBytes();
            var actualContent = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(
                    String.format("Partial content mismatch (expected: %s, received: %s)", "World!", actualContent),
                    "World!",
                    actualContent
            );
        }
    }

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
            var actualContent = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(
                    String.format("Partial content mismatch (expected: %s, received: %s)", "ld!", actualContent),
                    "ld!",
                    actualContent
            );
        }
    }
}
