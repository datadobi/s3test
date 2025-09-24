package com.datadobi.s3test;

import com.datadobi.s3test.s3.S3TestBase;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.DeletedObject;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.Assert.*;

public class DeleteObjectsTests extends S3TestBase {
    public DeleteObjectsTests() throws IOException {
    }

    @Test
    public void testDeleteObjectsByKey() throws IOException {
        bucket.putObject("a", "Hello");
        bucket.putObject("b", "World");
        var deleteResponse = bucket.deleteObjects("a", "b");
        assertTrue(deleteResponse.hasDeleted());
        assertEquals(
                List.of("a", "b"),
                deleteResponse.deleted().stream().map(DeletedObject::key).sorted().toList()
        );
    }

    @Test
    public void testDeleteObjectsWithMatchingETag() throws IOException {
        bucket.putObject("a", "Hello");
        var headResponse = bucket.headObject("a");
        var deleteResponse = bucket.deleteObjects(oid -> oid.key("a").eTag(headResponse.eTag()));
        assertTrue(deleteResponse.hasDeleted());
        assertEquals(
                List.of("a"),
                deleteResponse.deleted().stream().map(DeletedObject::key).sorted().toList()
        );
    }

    @Test
    public void testDeleteObjectsWithDifferentETag() throws IOException {
        bucket.putObject("a", "Hello");
        var deleteResponse = bucket.deleteObjects(oid -> {
            var etag = "\"foo\"";
            oid.key("a").eTag(etag);
        });
        assertFalse(deleteResponse.hasDeleted());
        assertEquals(
                List.of(),
                deleteResponse.deleted().stream().map(DeletedObject::key).sorted().toList()
        );
    }

    @Test
    public void testDeleteObjectsContainingDotDot() throws IOException {
        bucket.putObject("a..b", "Hello");
        bucket.putObject("c..d", "World");
        var deleteResponse = bucket.deleteObjects("a..b", "c..d");
        assertTrue(deleteResponse.hasDeleted());
        assertEquals(
                List.of("a..b", "c..d"),
                deleteResponse.deleted().stream().map(DeletedObject::key).sorted().toList()
        );
    }
}
