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
import software.amazon.awssdk.services.s3.model.DeletedObject;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class DeleteObjectsTests extends S3TestBase {
    public DeleteObjectsTests() throws IOException {
    }

    @Test
    public void testDeleteObjectsByKey() {
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
    public void testDeleteObjectsWithMatchingETag() {
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
    public void testDeleteObjectsWithDifferentETag() {
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
    public void testDeleteObjectsContainingDotDot() {
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
