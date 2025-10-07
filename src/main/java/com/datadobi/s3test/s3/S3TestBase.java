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
package com.datadobi.s3test.s3;

import org.apache.logging.log4j.LogManager;
import org.junit.After;
import org.junit.Before;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;

public class S3TestBase {
    public static ServiceDefinition DEFAULT;

    static {
        String testUri = System.getenv("S3TEST_URI");
        if (testUri != null) {
            try {
                DEFAULT = ServiceDefinition.fromURI(testUri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected final ServiceDefinition target;
    protected S3Client s3;
    protected S3Bucket bucket;

    public S3TestBase() throws IOException {
        this(DEFAULT != null ? DEFAULT : ServiceDefinition.fromS3Profile("default"));
    }

    public S3TestBase(ServiceDefinition parameter) {
        this.target = parameter;
    }

    @Before
    public final void setUp() throws IOException {
        s3 = S3.createClient(target);

        this.bucket = new S3Bucket(s3, target.bucket());
        if (target.createBucket()) {
            bucket.create();
        }
    }

    @After
    public final void tearDown() throws IOException {
        S3.clearBucket(s3, target.bucket());
        if (target.createBucket()) {
            bucket.delete();
        }

        s3.close();
    }
}
