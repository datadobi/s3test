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
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.DeletedObject;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class DeleteObjectsTests extends S3TestBase {
    public DeleteObjectsTests() throws IOException {
    }

    /**
     * Deletes multiple objects by key in a single DeleteObjects request.
     * Expected: Both objects are removed; response hasDeleted() is true and deleted list contains "a" and "b".
     */
    @Test
    public void testDeleteObjectsByKey() {
        bucket.putObject("a", "Hello");
        bucket.putObject("b", "World");
        var deleteResponse = bucket.deleteObjects("a", "b");
        assertTrue("Delete response should have deleted objects", deleteResponse.hasDeleted());
        var actualKeys = deleteResponse.deleted().stream().map(DeletedObject::key).sorted().toList();
        assertEquals(
                "Deleted keys should match requested keys",
                List.of("a", "b"),
                actualKeys
        );
    }

    /**
     * Deletes one object using DeleteObjects with a matching ETag condition.
     * Expected: Object is deleted; response hasDeleted() is true and deleted list contains "a".
     */
    @Test
    public void testDeleteObjectsWithMatchingETag() {
        bucket.putObject("a", "Hello");
        var headResponse = bucket.headObject("a");
        var deleteResponse = bucket.deleteObjects(oid -> oid.key("a").eTag(headResponse.eTag()));
        assertTrue("Delete response should have deleted objects with matching ETag", deleteResponse.hasDeleted());
        var actualKeys = deleteResponse.deleted().stream().map(DeletedObject::key).sorted().toList();
        assertEquals(
                "Deleted keys should match requested key with matching etag",
                List.of("a"),
                actualKeys
        );
    }

    /**
     * Attempts to delete an object with a non-matching ETag (conditional delete).
     * Expected: Object is not deleted; response hasDeleted() is false and deleted list is empty.
     */
    @Test
    public void testDeleteObjectsWithDifferentETag() {
        bucket.putObject("a", "Hello");
        var deleteResponse = bucket.deleteObjects(oid -> {
            var etag = "\"foo\"";
            oid.key("a").eTag(etag);
        });
        assertFalse("Delete response should not have deleted objects with mismatched ETag", deleteResponse.hasDeleted());
        var actualKeys = deleteResponse.deleted().stream().map(DeletedObject::key).sorted().toList();
        assertEquals(
                "No objects should be deleted when etag does not match",
                List.of(),
                actualKeys
        );
    }

    /**
     * Deletes objects whose keys contain ".." (e.g. "a..b", "c..d") via DeleteObjects.
     * Expected: Both objects are deleted; response lists "a..b" and "c..d" in deleted.
     */
    @Test
    public void testDeleteObjectsContainingDotDot() {
        bucket.putObject("a..b", "Hello");
        bucket.putObject("c..d", "World");
        var deleteResponse = bucket.deleteObjects("a..b", "c..d");
        var actualKeys = deleteResponse.deleted().stream().map(DeletedObject::key).sorted().toList();
        assertTrue("Delete response should have deleted objects with '..' in keys", deleteResponse.hasDeleted());
        assertEquals(
                "Deleted keys should match requested keys with dot dot",
                List.of("a..b", "c..d"),
                actualKeys
        );
    }
}
