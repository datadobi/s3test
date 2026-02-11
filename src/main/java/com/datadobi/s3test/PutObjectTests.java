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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static com.datadobi.s3test.s3.Quirk.CONTENT_TYPE_NOT_SET_FOR_KEYS_WITH_TRAILING_SLASH;
import static org.junit.Assert.*;

public class PutObjectTests extends S3TestBase {
    public PutObjectTests() throws IOException {
    }

    @Test
    public void testPutObject() {
        var putResponse = bucket.putObject("foo", "bar");
        var headResponse = bucket.headObject("foo");
        var actualContentLength = headResponse.contentLength();
        assertEquals(
                String.format("Content length mismatch (expected: %s, received: %s)", Long.valueOf(3), actualContentLength),
                Long.valueOf(3),
                actualContentLength
        );
        var actualETag = headResponse.eTag();
        assertEquals(
                String.format("ETag mismatch (expected: %s, received: %s)", putResponse.eTag(), actualETag),
                putResponse.eTag(),
                actualETag
        );
    }

    @Test
    public void testPutEmptyObject() {
        var putResponse = bucket.putObject("foo", "");
        var headResponse = bucket.headObject("foo");
        var actualContentLength = headResponse.contentLength();
        assertEquals(
                String.format("Content length mismatch for empty object (expected: %s, received: %s)", Long.valueOf(0), actualContentLength),
                Long.valueOf(0),
                actualContentLength
        );
        var actualETag = headResponse.eTag();
        assertEquals(
                String.format("ETag mismatch (expected: %s, received: %s)", putResponse.eTag(), actualETag),
                putResponse.eTag(),
                actualETag
        );
    }

    @Test
    public void thatPutObjectCanUpdate() throws Exception {
        var key = "key";

        var content1 = "a";
        var putObjectResult1 = bucket.putObject(key, content1);
        var eTag1 = putObjectResult1.eTag();

        try (var object = bucket.getObject(key)) {
            var actualETag = object.response().eTag();
            assertEquals(
                    String.format("ETag mismatch after first put (expected: %s, received: %s)", eTag1, actualETag),
                    eTag1,
                    actualETag
            );

            var content = new String(object.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(
                    String.format("Content mismatch after first put (expected: %s, received: %s)", content1, content),
                    content1,
                    content
            );
        }

        var content2 = "bb";
        var putObjectResult2 = bucket.putObject(key, content2);

        assertNotNull("PutObject result should not be null", putObjectResult2);
        var eTag2 = putObjectResult2.eTag();

        assertNotEquals(
                String.format("ETags should differ after updating object (first: %s, second: %s)", eTag1, eTag2),
                eTag1,
                eTag2
        );

        try (var object = bucket.getObject(key)) {
            var actualETag = object.response().eTag();
            assertEquals(
                    String.format("ETag mismatch after second put (expected: %s, received: %s)", eTag2, actualETag),
                    eTag2,
                    actualETag
            );

            var content = new String(object.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(
                    String.format("Content mismatch after second put (expected: %s, received: %s)", content2, content),
                    content2,
                    content
            );
        }

    }

    @Test
    public void thatServerAcceptsContentEncodingGzip() throws Exception {
        var xml = "<root></root>";
        var bos = new ByteArrayOutputStream();
        var gzipOutputStream = new GZIPOutputStream(bos);
        gzipOutputStream.write(xml.getBytes(StandardCharsets.UTF_8));
        gzipOutputStream.close();
        var data = bos.toByteArray();

        bucket.putObject(
                r -> r.key("content-encoding-gzip")
                        .contentType("text/xml")
                        .contentEncoding("gzip")
                        .build(),
                data
        );

        try (var object = bucket.getObject("content-encoding-gzip")) {
            var bytes = object.readAllBytes();
            var actualEncoding = object.response().contentEncoding();
            assertEquals(
                    String.format("Content encoding mismatch (expected: %s, received: %s)", "gzip", actualEncoding),
                    "gzip",
                    actualEncoding
            );
            assertArrayEquals("Gzipped data should match", data, bytes);
        }
    }

    @Test
    public void thatServerAcceptsArbitraryContentEncoding() throws Exception {
        var xml = "<root></root>";

        bucket.putObject(
                b -> b
                        .key("content-encoding-unknown")
                        .contentType("text/xml")
                        .contentEncoding("dd-plain-no-encoding")
                        .build(),
                xml
        );

        try (var object = bucket.getObject("content-encoding-unknown")) {
            var bytes = object.readAllBytes();
            var actualEncoding = object.response().contentEncoding();
            assertEquals(
                    String.format("Content encoding mismatch (expected: %s, received: %s)", "dd-plain-no-encoding", actualEncoding),
                    "dd-plain-no-encoding",
                    actualEncoding
            );
            var actualContent = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(
                    String.format("Content mismatch (expected: %s, received: %s)", xml, actualContent),
                    xml,
                    actualContent
            );
        }
    }

    @Test
    @SkipForQuirks({CONTENT_TYPE_NOT_SET_FOR_KEYS_WITH_TRAILING_SLASH})
    public void canSetContentTypeOnEmptyObjectWithKeyContainingTrailingSlash() throws IOException {

        var data = new byte[0];

        bucket.putObject(
                r -> r
                        .key("content-type/")
                        .contentType("text/empty"),
                data
        );

        try (var object = bucket.getObject("content-type/")) {
            var actualContentType = object.response().contentType();
            assertEquals(
                    String.format("Content type mismatch (expected: %s, received: %s)", "text/empty", actualContentType),
                    "text/empty",
                    actualContentType
            );
        }
    }

    @Test
    public void thatUpdateMetadataUsingPutObjectAffectsLastModifiedTime() throws Exception {
        var key = "key";
        var content = "aaaaaaaaaa";

        // generate initial data
        var put1 = bucket.putObject(key, content);
        var info1 = bucket.headObjectWithETag(key, put1.eTag(), target.eventualConsistencyDelay());

        // wait a bit
        Thread.sleep(Duration.ofSeconds(2));

        // update metadata
        Map<String, String> userMetaData = new HashMap<>();
        userMetaData.put("key1", "var1");
        userMetaData.put("key2", "var2");

        var put2 = bucket.putObject(r -> r.key(key).metadata(userMetaData), content);
        var info2 = bucket.headObjectWithETag(key, put2.eTag(), target.eventualConsistencyDelay());

        // Last modified should have changed
        assertNotEquals("PutObject with different user metadata should change LastModified", info1.lastModified(), info2.lastModified());
    }

    @Test
    public void thatUpdateDataUsingPutObjectAffectsLastModifiedTime() throws Exception {
        var key = "key";

        // generate initial data
        var put1 = bucket.putObject(key, "aaaaaaaaaaa");
        var info1 = bucket.headObjectWithETag(key, put1.eTag(), target.eventualConsistencyDelay());

        // wait a bit
        Thread.sleep(Duration.ofSeconds(2));

        // update object content
        var put2 = bucket.putObject(key, "bbbbbbbbbbbbbbbbb");
        var info2 = bucket.headObjectWithETag(key, put2.eTag(), target.eventualConsistencyDelay());

        assertNotEquals("PutObject with different content should change ETag", info1.eTag(), info2.eTag());
        assertNotEquals("PutObject with different content should change LastModified", info1.lastModified(), info2.lastModified());
    }
}
