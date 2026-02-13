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

import com.datadobi.s3test.s3.Quirk;
import com.datadobi.s3test.s3.S3TestBase;
import com.datadobi.s3test.s3.SkipForQuirks;
import com.datadobi.s3test.util.DummyInputStream;
import org.junit.Ignore;
import org.junit.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm;
import software.amazon.awssdk.services.s3.model.ChecksumMode;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ChecksumTests extends S3TestBase {
    public ChecksumTests() throws IOException {
    }

    @Test
    @SkipForQuirks({Quirk.CHECKSUMS_NOT_SUPPORTED})
    public void testCRC32() {
        putWithChecksum(ChecksumAlgorithm.CRC32);
    }

    @Test
    @SkipForQuirks({Quirk.CHECKSUMS_NOT_SUPPORTED})
    public void testCRC32_C() {
        putWithChecksum(ChecksumAlgorithm.CRC32_C);
    }

    @Test
    @SkipForQuirks({Quirk.CHECKSUMS_NOT_SUPPORTED})
    public void testSHA1() {
        putWithChecksum(ChecksumAlgorithm.SHA1);
    }

    @Test
    @SkipForQuirks({Quirk.CHECKSUMS_NOT_SUPPORTED})
    public void testSHA256() {
        putWithChecksum(ChecksumAlgorithm.SHA256);
    }

    @Test
    @SkipForQuirks({Quirk.CHECKSUMS_NOT_SUPPORTED})
    @Ignore("Requires C runtime")
    public void testCRC64_NVME() {
        putWithChecksum(ChecksumAlgorithm.CRC64_NVME);
    }

    private void putWithChecksum(ChecksumAlgorithm checksumAlgorithm) {
        String key = "foo";
        String content = "bar";

        var putResponse = bucket.putObject(
                r -> r.key(key).checksumAlgorithm(checksumAlgorithm),
                content
        );
        var putChecksum = switch (checksumAlgorithm) {
            case CRC32 -> putResponse.checksumCRC32();
            case CRC32_C -> putResponse.checksumCRC32C();
            case SHA1 -> putResponse.checksumSHA1();
            case SHA256 -> putResponse.checksumSHA256();
            case CRC64_NVME -> putResponse.checksumCRC64NVME();
            case UNKNOWN_TO_SDK_VERSION -> throw new IllegalArgumentException(checksumAlgorithm.toString());
        };

        var headResponse = bucket.headObject(r -> r.key(key).checksumMode(ChecksumMode.ENABLED));
        assertEquals("Content length mismatch", Long.valueOf(key.length()), headResponse.contentLength());
        assertEquals("ETag mismatch", putResponse.eTag(), headResponse.eTag());
        var headChecksum = switch (checksumAlgorithm) {
            case CRC32 -> headResponse.checksumCRC32();
            case CRC32_C -> headResponse.checksumCRC32C();
            case SHA1 -> headResponse.checksumSHA1();
            case SHA256 -> headResponse.checksumSHA256();
            case CRC64_NVME -> headResponse.checksumCRC64NVME();
            case UNKNOWN_TO_SDK_VERSION -> throw new IllegalArgumentException(checksumAlgorithm.toString());
        };
        assertEquals("Checksum mismatch", putChecksum, headChecksum);
    }

    @Test
    @SkipForQuirks({Quirk.CHECKSUMS_NOT_SUPPORTED})
    public void indefiniteLengthWithChecksum() {
        String key = "foo";
        bucket.putObject(
                r -> r.key(key).checksumAlgorithm(ChecksumAlgorithm.CRC32),
                RequestBody.fromContentProvider(
                        ContentStreamProvider.fromInputStream(new DummyInputStream(1024*1024)),
                        "application/octet-stream"
                )
        );
    }
}
