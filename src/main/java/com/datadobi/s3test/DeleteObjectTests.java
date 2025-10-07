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

import java.io.IOException;

public class DeleteObjectTests extends S3TestBase {
    public DeleteObjectTests() throws IOException {
    }

    @Test
    public void testDeleteObject() {
        bucket.putObject("foo", "Hello, World!");
        bucket.deleteObject("foo");
    }

    @Test
    public void testDeleteObjectContainingDotDot() {
        var fullContent = "Hello, World!";
        bucket.putObject("f..o", fullContent);
        bucket.headObject("f..o");

        bucket.deleteObject("f..o");
    }
}
