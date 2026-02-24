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
import com.datadobi.s3test.s3.SkipForQuirks;
import org.junit.Test;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.datadobi.s3test.s3.Quirk.*;
import static org.junit.Assert.*;

public class MultiPartUploadTests extends S3TestBase {
    private static final int MB = 1024 * 1024;

    public MultiPartUploadTests() throws IOException {
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void abortMultipartUploadWithNoParts() {
        String key = "foo";

        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload(key);
        String uploadId = mpu.uploadId();

        verifyMultipartUploadPresentInListing(key, uploadId);

        bucket.abortMultipartUpload(key, uploadId);

        verifyMultipartUploadNotPresentInListing(key, uploadId);
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void abortMultipartUploadTwice() {
        String key = "foo";

        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload(key);
        String uploadId = mpu.uploadId();

        verifyMultipartUploadPresentInListing(key, uploadId);

        bucket.abortMultipartUpload(key, uploadId);

        verifyMultipartUploadNotPresentInListing(key, uploadId);

        bucket.abortMultipartUpload(key, uploadId);

        verifyMultipartUploadNotPresentInListing(key, uploadId);
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void abortMultipartUploadWithPart() {
        String key = "foo";

        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload(key);
        String uploadId = mpu.uploadId();

        verifyMultipartUploadPresentInListing(key, uploadId);

        bucket.uploadPart(key, uploadId, 1, "hello");

        bucket.abortMultipartUpload(key, uploadId);

        verifyMultipartUploadNotPresentInListing(key, uploadId);
    }

    private void verifyMultipartUploadPresentInListing(String key, String uploadId) {
        ListMultipartUploadsResponse uploads = bucket.listMultipartUploads();
        MultipartUpload upload;
        if (uploads.hasUploads()) {
            upload = uploads.uploads()
                    .stream()
                    .filter(u -> u.key().equals(key) && u.uploadId().equals(uploadId))
                    .findFirst()
                    .orElse(null);
        } else {
            upload = null;
        }

        assertNotNull("Multipart upload should exist", upload);
    }

    private void verifyMultipartUploadNotPresentInListing(String key, String uploadId) {
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
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void abortMultipartUploadWithIncorrectKey() {
        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload("foo");
        try {
            bucket.abortMultipartUpload("bar", mpu.uploadId());
        } catch (S3Exception e) {
            assertEquals("Expected HTTP 404 Not Found", 404, e.statusCode());
        }
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void abortMultipartUploadWithIncorrectUploadId() {
        bucket.createMultipartUpload("foo");

        try {
            bucket.abortMultipartUpload("foo", UUID.randomUUID().toString());
        } catch (S3Exception e) {
            assertEquals("Expected HTTP 404 Not Found", 404, e.statusCode());
        }
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void abortMultipartUploadWithIncorrectKeyAndUploadId() {
        bucket.createMultipartUpload("foo");

        try {
            bucket.abortMultipartUpload("bar", UUID.randomUUID().toString());
        } catch (S3Exception e) {
            assertEquals("Expected HTTP 404 Not Found", 404, e.statusCode());
        }
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void completeMultipartUploadWithNoParts() {
        String key = "foo";

        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload(key);
        String uploadId = mpu.uploadId();

        try {
            CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder().build();
            bucket.completeMultipartUpload(r -> r.key(key).uploadId(uploadId).multipartUpload(completedUpload));
        } catch (S3Exception e) {
            assertEquals("Expected HTTP 400 Bad Request for CompleteMultipartUpload with no parts", 400, e.statusCode());
        }
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void completeMultipartUploadWithSinglePart() {
        String key = "foo";

        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload(key);
        String uploadId = mpu.uploadId();

        verifyMultipartUploadPresentInListing(key, uploadId);

        UploadPartResponse uploadPart = bucket.uploadPart(key, uploadId, 1, "hello");
        CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder().parts(CompletedPart.builder()
                .partNumber(1)
                .eTag(uploadPart.eTag())
                .build()).build();

        bucket.completeMultipartUpload(r -> r.key(key).uploadId(uploadId).multipartUpload(completedUpload));

        verifyMultipartUploadNotPresentInListing(key, uploadId);
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void completeMultipartUploadTwice() {
        String key = "foo";

        CreateMultipartUploadResponse mpu = bucket.createMultipartUpload(key);
        String uploadId = mpu.uploadId();

        verifyMultipartUploadPresentInListing(key, uploadId);

        UploadPartResponse uploadPart = bucket.uploadPart(key, uploadId, 1, "hello");
        CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder().parts(CompletedPart.builder()
                .partNumber(1)
                .eTag(uploadPart.eTag())
                .build()).build();

        bucket.completeMultipartUpload(r -> r.key(key).uploadId(uploadId).multipartUpload(completedUpload));

        verifyMultipartUploadNotPresentInListing(key, uploadId);

        bucket.completeMultipartUpload(r -> r.key(key).uploadId(uploadId).multipartUpload(completedUpload));

        verifyMultipartUploadNotPresentInListing(key, uploadId);
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void completeMultipartUploadWithMultipleParts() throws Exception {
        var key = "multiparted";

        // Start multipart upload
        String uploadId = bucket.createMultipartUpload(key).uploadId();

        // Upload parts
        long[] partitionSizes = {5 * MB, 7 * MB, 11 * MB, 1 * MB};
        List<CompletedPart> completedParts = uploadParts(key, uploadId, partitionSizes);

        // Complete the multipart upload with all uploaded parts.
        bucket.completeMultipartUpload(r -> r.key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build()));

        // Verify result
        verifyMultipartUpload(key, partitionSizes, completedParts);
    }

    @Test
    @SkipForQuirks({MULTIPART_UPLOAD_NOT_SUPPORTED})
    public void completeMultipartUploadWithOmittedParts() throws Exception {
        var key = "multiparted";

        // Start multipart upload
        String uploadId = bucket.createMultipartUpload(key).uploadId();

        // Upload parts
        long[] partitionSizes = {5 * MB, 10 * MB, 1 * MB};
        List<CompletedPart> allUploadedParts = uploadParts(key, uploadId, partitionSizes);

        // Complete the multipart upload with only two of the three uploaded parts.
        List<CompletedPart> completedParts = List.of(allUploadedParts.get(0), allUploadedParts.get(2));
        bucket.completeMultipartUpload(r -> r.key(key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build()));

        // Verify result
        verifyMultipartUpload(key, partitionSizes, completedParts);
    }

    private List<CompletedPart> uploadParts(String key, String uploadId, long[] partitionSizes) {
        // Upload the file parts.
        List<CompletedPart> partETags = new ArrayList<>();

        for (var partNumber = 1; partNumber <= partitionSizes.length; partNumber++) {
            var partitionSize = partitionSizes[partNumber - 1];

            var content = new byte[(int) partitionSize];

            // Upload the part and add the response's ETag to our list.
            var uploadResult = bucket.uploadPart(key, uploadId, partNumber, content);
            partETags.add(CompletedPart.builder()
                    .partNumber(partNumber)
                    .eTag(uploadResult.eTag())
                    .build());
        }
        return partETags;
    }

    private void verifyMultipartUpload(String key, long[] partitionSizes, List<CompletedPart> completedParts) throws IOException {
        long uploadedTotalSize = 0L;
        for (CompletedPart completedPart : completedParts) {
            int originalPartNumber = completedPart.partNumber();
            uploadedTotalSize += partitionSizes[originalPartNumber - 1];
        }

        long reportedTotalSize = 0;

        var objectMetadata = bucket.headObject(r -> r.key(key).partNumber(1));

        assertNotNull("Object metadata should not be null after HEAD request", objectMetadata);

        Integer reportedPartCount = null;

        if (!target.hasQuirk(GET_OBJECT_PART_NOT_SUPPORTED)) {
            reportedPartCount = objectMetadata.partsCount();
            if (reportedPartCount != null) {
                if (!target.hasQuirk(MULTIPART_SIZES_NOT_KEPT)) {
                    assertEquals(
                            "Part count should match unless MULTIPART_SIZES_NOT_KEPT quirk",
                            Integer.valueOf(completedParts.size()),
                            reportedPartCount
                    );
                }
                assertFalse("Part count should be supported", target.hasQuirk(GET_OBJECT_PARTCOUNT_NOT_SUPPORTED));
            } else {
                assertTrue("Part count should not be supported for this target", target.hasQuirk(GET_OBJECT_PARTCOUNT_NOT_SUPPORTED));
            }
        }

        if (reportedPartCount != null) {
            // Download the file parts.
            for (var partNumber = 1; partNumber <= reportedPartCount; partNumber++) {
                int originalPartNumber = completedParts.get(partNumber - 1).partNumber();
                var partitionSize = partitionSizes[originalPartNumber - 1];

                var finalPartNumber = partNumber;
                try (var object = bucket.getObject(r -> r.key(key).partNumber(finalPartNumber))) {
                    long reportedPartSize = object.response().contentLength();

                    reportedTotalSize += reportedPartSize;

                    if (!target.hasQuirk(MULTIPART_SIZES_NOT_KEPT)) {
                        assertEquals(
                                "Part size should be preserved",
                                reportedPartSize,
                                partitionSize
                        );
                    }
                }
            }
        } else {
            // Download in single request.
            try (var object = bucket.getObject(key)) {
                long receivedSize = object.response().contentLength();

                reportedTotalSize += receivedSize;
            }
        }

        assertEquals("Total received size should match uploaded size", reportedTotalSize, uploadedTotalSize);
    }
}
