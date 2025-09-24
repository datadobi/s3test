package com.datadobi.s3test;

import com.datadobi.s3test.s3.S3TestBase;
import org.junit.Test;

import java.io.IOException;

public class DeleteObjectTests extends S3TestBase {
    public DeleteObjectTests() throws IOException {
    }

    @Test
    public void testDeleteObject() throws IOException {
        bucket.putObject("foo", "Hello, World!");
        bucket.deleteObject("foo");
    }

    @Test
    public void testDeleteObjectContainingDotDot() throws IOException {
        var fullContent = "Hello, World!";
        bucket.putObject("f..o", fullContent);
        bucket.headObject("f..o");

        bucket.deleteObject("f..o");
    }
}
