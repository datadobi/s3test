package com.datadobi.s3test;

import com.datadobi.s3test.s3.S3TestBase;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.datadobi.s3test.s3.ServiceDefinition.Capability.PUT_OBJECT_IF_NONE_MATCH_ETAG_SUPPORTED;
import static com.datadobi.s3test.s3.ServiceDefinition.Restriction.PUT_OBJECT_IF_NONE_MATCH_STAR_NOT_SUPPORTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ConditionalRequests extends S3TestBase {
    public ConditionalRequests() throws IOException {
    }

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
            if (!target.hasRestrictions(PUT_OBJECT_IF_NONE_MATCH_STAR_NOT_SUPPORTED)) {
                assertEquals("bar", content);
                assertEquals(overwritePutResponse.eTag(), getResponse.eTag());
                fail("PutObject using 'If-None-Match: *' should fail if object already exists");
            }
        } catch (S3Exception e) {
            if (target.hasRestrictions(PUT_OBJECT_IF_NONE_MATCH_STAR_NOT_SUPPORTED)) {
                assertEquals(501, e.statusCode());
            } else {
                assertEquals(412, e.statusCode());
            }
        }
    }

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
            if (target.hasCapabilities(PUT_OBJECT_IF_NONE_MATCH_ETAG_SUPPORTED)) {
                assertEquals("bar", content);
                assertEquals(overwritePutResponse.eTag(), getResponse.eTag());
                fail("PutObject using 'If-None-Match: \"<etag>\"' should fail if object already exists");
            }
        } catch (S3Exception e) {
            if (target.hasCapabilities(PUT_OBJECT_IF_NONE_MATCH_ETAG_SUPPORTED)) {
                assertEquals(412, e.statusCode());
            } else {
                assertEquals(501, e.statusCode());
            }
        }
    }

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
            if (target.hasCapabilities(PUT_OBJECT_IF_NONE_MATCH_ETAG_SUPPORTED)) {
                assertEquals("bar", content);
                assertEquals(overwritePutResponse.eTag(), getResponse.eTag());
                fail("PutObject using 'If-Match: \"<etag>\"' should fail if object etag does not match");
            }
        } catch (S3Exception e) {
            if (target.hasCapabilities(PUT_OBJECT_IF_NONE_MATCH_ETAG_SUPPORTED)) {
                assertEquals(412, e.statusCode());
            } else {
                assertEquals(501, e.statusCode());
            }
        }
    }
}
