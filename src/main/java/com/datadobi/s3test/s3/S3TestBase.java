package com.datadobi.s3test.s3;

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
