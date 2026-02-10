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
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.datadobi.s3test.s3.Quirk.*;
import static org.junit.Assert.*;

public class MultiPartUploadTests extends S3TestBase {
    private static final int MB = 1024 * 1024;

    public MultiPartUploadTests() throws IOException {
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
            var finalPartNumber = partNumber;
            var uploadResult = bucket.uploadPart(r -> r.key(key)
                    .uploadId(initResponse.uploadId())
                    .partNumber(finalPartNumber)
                    .contentLength((long) content.length), content);
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

        assertNotNull(objectMetadata);

        Integer receivePartitionCount = null;

        if (!target.hasQuirk(GET_OBJECT_PART_NOT_SUPPORTED)) {
            receivePartitionCount = objectMetadata.partsCount();
            if (receivePartitionCount != null) {
                assertTrue(
                        target.hasQuirk(MULTIPART_SIZES_NOT_KEPT) ||
                                receivePartitionCount == partitionCount
                );
                assertFalse(target.hasQuirk(GET_OBJECT_PARTCOUNT_NOT_SUPPORTED));
            } else {
                assertTrue(target.hasQuirk(GET_OBJECT_PARTCOUNT_NOT_SUPPORTED));
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

                    if (target.hasQuirk(MULTIPART_SIZES_NOT_KEPT)) {
                        assertNotEquals(receivedSize, partitionSize);
                    } else {
                        assertEquals(receivedSize, partitionSize);
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

        assertEquals(receivedTotalSize, uploadedTotalSize);
    }
}
