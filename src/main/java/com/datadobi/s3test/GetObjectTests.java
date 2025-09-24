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
            assertEquals(fullContent, new String(bytes, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testGetKeyThatDoesNotExist() throws IOException {
        try (var objectInputStream = bucket.getObject("foo")) {
            fail("Should return HTTP 404");
        } catch (S3Exception e) {
            assertEquals(404, e.statusCode());
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
            assertEquals("World!", new String(bytes, StandardCharsets.UTF_8));

            var response = objectInputStream.response();
            assertEquals(contentRange.toString(), response.contentRange());
            assertEquals(Long.valueOf(lastPos - startPos + 1), response.contentLength());
        }
    }

    @Test
    public void testGetPartialUnsatisfiable() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("foo", fullContent);

        try {
            bucket.getObject("foo", null, new Range("bytes", ImmutableList.of(new RangeSpec(200L, null))));
            fail("Should return HTTP 416");
        } catch (S3Exception e) {
            assertEquals(416, e.statusCode());
        }
    }
}
