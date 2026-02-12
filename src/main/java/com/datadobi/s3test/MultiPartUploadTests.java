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
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.datadobi.s3test.s3.Quirk.*;
import static org.junit.Assert.*;

public class MultiPartUploadTests extends S3TestBase {
    private static final int MB = 1024 * 1024;

    public MultiPartUploadTests() throws IOException {
    }

    @Test
    public void abortMultipartUploadWithNoParts() {
        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload("foo");

        bucket.abortMultipartUpload("foo", mpu.uploadId());

        verifyMultipartUploadDoesNotExist("foo", mpu.uploadId());
    }

    @Test
    public void abortMultipartUploadTwice() {
        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload("foo");

        bucket.abortMultipartUpload("foo", mpu.uploadId());

        verifyMultipartUploadDoesNotExist("foo", mpu.uploadId());

        bucket.abortMultipartUpload("foo", mpu.uploadId());

        verifyMultipartUploadDoesNotExist("foo", mpu.uploadId());
    }

    @Test
    public void abortMultipartUploadWithPart() {
        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload("foo");

        bucket.uploadPart("foo", mpu.uploadId(), 1, "hello");

        bucket.abortMultipartUpload("foo", mpu.uploadId());

        verifyMultipartUploadDoesNotExist("foo", mpu.uploadId());
    }

    private void verifyMultipartUploadDoesNotExist(String key, String uploadId) {
        ListMultipartUploadsResponse uploads = bucket.listMultipartUploads();
        if (uploads.hasUploads()) {
            MultipartUpload upload = uploads.uploads()
                    .stream()
                    .filter(u -> u.key().equals(key) && u.uploadId().equals(uploadId))
                    .findFirst()
                    .orElse(null);

            assertNull("Multipart upload should not exist", upload);
        }
    }

    @Test
    public void abortMultipartUploadWithIncorrectKey() {
        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload("foo");
        try {
            bucket.abortMultipartUpload("bar", mpu.uploadId());
        } catch (S3Exception e) {
            assertEquals("Expected HTTP 404 Not Found", 404, e.statusCode());
        }
    }

    @Test
    public void abortMultipartUploadWithIncorrectUploadId() {
        bucket.createMultipartUpload("foo");

        try {
            bucket.abortMultipartUpload("foo", UUID.randomUUID().toString());
        } catch (S3Exception e) {
            assertEquals("Expected HTTP 404 Not Found", 404, e.statusCode());
        }
    }

    @Test
    public void abortMultipartUploadWithIncorrectKeyAndUploadId() {
        bucket.createMultipartUpload("foo");

        try {
            bucket.abortMultipartUpload("bar", UUID.randomUUID().toString());
        } catch (S3Exception e) {
            assertEquals("Expected HTTP 404 Not Found", 404, e.statusCode());
        }
    }

    @Test
    public void thatMultipartRetrievesOriginalParts() throws Exception {
        // generate multipart data
        // see: https://docs.aws.amazon.com/AmazonS3/latest/dev/llJavaUploadFile.html

        var key = "multiparted";
        // parts are minimum 5 MB
        long[] partitionSizes = {5 * MB, 10 * MB, 5 * MB, 7 * MB, 12 * MB, 13 * MB, 9 * MB, 5 * MB, 5 * MB, 5 * MB, 3 * MB};
        var partitionCount = partitionSizes.length;
        var uploadedTotalSize = Arrays.stream(partitionSizes).sum();

        List<CompletedPart> partETags = new ArrayList<>();

        // Initiate the multipart upload.
        var initResponse = bucket.createMultipartUpload(key);

        // Upload the file parts.
        for (var partNumber = 1; partNumber <= partitionCount; partNumber++) {
            var partitionSize = partitionSizes[partNumber - 1];

            var content = new byte[(int) partitionSize];

            // Upload the part and add the response's ETag to our list.
            var uploadResult = bucket.uploadPart(key, initResponse.uploadId(), partNumber, content);
            partETags.add(CompletedPart.builder()
                    .partNumber(partNumber)
                    .eTag(uploadResult.eTag())
                    .build());
        }

        // Complete the multipart upload.
        bucket.completeMultipartUpload(r -> r.key(key)
                .uploadId(initResponse.uploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(partETags).build()));

        //
        // retrieve multipart data
        //

        long receivedTotalSize = 0;

        var objectMetadata = bucket.headObject(r -> r.key(key).partNumber(1));

        assertNotNull("Object metadata should not be null after HEAD request", objectMetadata);

        Integer receivePartitionCount = null;

        if (!target.hasQuirk(GET_OBJECT_PART_NOT_SUPPORTED)) {
            receivePartitionCount = objectMetadata.partsCount();
            if (receivePartitionCount != null) {
                if (!target.hasQuirk(MULTIPART_SIZES_NOT_KEPT)) {
                    assertEquals(
                            "Part count should match unless MULTIPART_SIZES_NOT_KEPT quirk",
                            Integer.valueOf(partitionCount),
                            receivePartitionCount
                    );
                }
                assertFalse("Part count should be supported", target.hasQuirk(GET_OBJECT_PARTCOUNT_NOT_SUPPORTED));
            } else {
                assertTrue("Part count should not be supported for this target", target.hasQuirk(GET_OBJECT_PARTCOUNT_NOT_SUPPORTED));
            }
        }

        if (receivePartitionCount != null) {
            // Download the file parts.
            for (var partNumber = 1; partNumber <= receivePartitionCount; partNumber++) {
                var partitionSize = partitionSizes[partNumber - 1];

                var finalPartNumber = partNumber;
                try (var object = bucket.getObject(r -> r.key(key).partNumber(finalPartNumber))) {
                    long receivedSize = object.response().contentLength();

                    receivedTotalSize += receivedSize;

                    if (!target.hasQuirk(MULTIPART_SIZES_NOT_KEPT)) {
                        assertEquals(
                                "Part size should be preserved",
                                receivedSize,
                                partitionSize
                        );
                    }
                }
            }
        } else {
            // Download in single request.
            try (var object = bucket.getObject(key)) {
                long receivedSize = object.response().contentLength();

                receivedTotalSize += receivedSize;
            }
        }

        assertEquals("Total received size should match uploaded size", receivedTotalSize, uploadedTotalSize);
    }
}
