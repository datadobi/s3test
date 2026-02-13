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
package com.datadobi.s3test.s3;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public class S3TestBase {
    public static final Config DEFAULT_CONFIG;
    public static ServiceDefinition DEFAULT_SERVICE;
    public static WireLogger WIRE_LOGGER;

    private static final boolean CAPTURE_SETUP = Boolean.parseBoolean(Objects.requireNonNullElse(System.getenv("S3TEST_WIRELOG_SETUP"), "false"));
    private static final boolean CAPTURE_TEARDOWN = Boolean.parseBoolean(Objects.requireNonNullElse(System.getenv("S3TEST_WIRELOG_TEARDOWN"), "false"));

    static {
        String config = System.getenv("S3TEST_CONFIG");
        if (config != null) {
            DEFAULT_CONFIG = Config.loadFromToml(Path.of(config));
        } else {
            DEFAULT_CONFIG = Config.AWS_CONFIG;
        }

        String testUri = System.getenv("S3TEST_URI");
        if (testUri != null) {
            try {
                DEFAULT_SERVICE = ServiceDefinition.fromURI(testUri).toBuilder().quirks(DEFAULT_CONFIG.quirks()).build();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String wireLogPath = System.getenv("S3TEST_WIRELOG");
        WIRE_LOGGER = new WireLogger(wireLogPath == null ? null : Path.of(wireLogPath));

    }

    private Description currentTest;

    /** When cleanup fails, next test uses this bucket instead of target.bucket(); cleared after use. */
    private static volatile String cleanupFailedNextBucket = null;

    @Rule(order = 0)
    public TestWatcher testName = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            S3TestBase.this.currentTest = description;
        }
    };

    @Rule(order = 1)
    public SkipForQuirksRule skipForQuirksRule = new SkipForQuirksRule();

    protected final ServiceDefinition target;
    protected S3Client s3;
    protected S3Bucket bucket;

    public S3TestBase() throws IOException {
        this(DEFAULT_SERVICE != null ? DEFAULT_SERVICE : ServiceDefinition.fromS3Profile("default"));
    }

    public S3TestBase(ServiceDefinition parameter) {
        this.target = parameter;
    }

    @Before
    public final void setUp() throws IOException {
        if (CAPTURE_SETUP) {
            WIRE_LOGGER.start(currentTest);
        }

        s3 = S3.createClient(target);

        String bucketName = cleanupFailedNextBucket != null ? cleanupFailedNextBucket : target.bucket();
        this.bucket = new S3Bucket(s3, bucketName);
        if (target.createBucket()) {
            bucket.create();
        }
        // If we used a fallback bucket, keep using new buckets for subsequent tests (original may still exist).
        if (cleanupFailedNextBucket != null) {
            cleanupFailedNextBucket = "s3test-" + UUID.randomUUID();
        } else {
            cleanupFailedNextBucket = null;
        }

        if (!CAPTURE_SETUP) {
            WIRE_LOGGER.start(currentTest);
        }
    }

    @After
    public final void tearDown() {
        if (!CAPTURE_TEARDOWN) {
            WIRE_LOGGER.stop();
        }

        try {
            S3.clearBucket(s3, bucket.name());
            if (target.createBucket()) {
                bucket.delete();
            }
        } catch (Throwable t) {
            // Cleanup failed (e.g. bucket not empty): use a new bucket for next test and do not
            // fail this test — only the test method's result counts.
            cleanupFailedNextBucket = "s3test-" + UUID.randomUUID();
        }

        s3.close();

        if (CAPTURE_TEARDOWN) {
            WIRE_LOGGER.stop();
        }
    }
}
