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
import com.datadobi.s3test.s3.S3;
import com.datadobi.s3test.s3.ServiceDefinition;

import java.io.IOException;

public class ClearBucket {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: ClearBucket S3_URI");
            System.exit(1);
        }

        var target = ServiceDefinition.fromURI(args[0]);

        if (!target.createBucket()) {
            S3.clearBucket(
                    S3.createClient(target),
                    target.bucket(),
                    target.hasQuirk(Quirk.DELETE_OBJECT_VERSION_NOT_SUPPORTED),
                    target.hasQuirk(Quirk.MULTIPART_UPLOAD_NOT_SUPPORTED)
            );
            System.out.println("Bucket deleted");
        } else {
            System.err.println("Bucket not specified in URI");
            System.exit(1);
        }
    }
}
