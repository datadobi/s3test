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

import com.datadobi.s3test.s3.S3;
import com.datadobi.s3test.s3.S3TestBase;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class ListBucketsTests extends S3TestBase {
    public ListBucketsTests() throws IOException {
    }

    /**
     * Calls ListBuckets and checks the response Date header.
     * Expected: Response includes a "Date" header; parsed value is within ~30 seconds of request time (valid RFC 1123).
     */
    @Test
    public void listBucketsResponsesShouldReturnValidDateHeader() {
        var timeOfRequest = Instant.now();

        var listBucketsResponse = S3.listBuckets(s3);
        var headers = listBucketsResponse.sdkHttpResponse().headers();

        Assertions.assertThat(headers)
                .as("Response headers should contain Date header")
                .containsKey("Date");

        var date = headers.get("Date").get(0);
        var serverTime = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(date));

        // coarse time validation, we don't want to test that the clocks of our test runners are perfectly in sync with the clocks of the server
        // rather, we want to know if the timezone information is correct etc
        Assertions.assertThat(serverTime)
                .as("Server time should be within 30 seconds of request time (expected range: %s to %s, received: %s)",
                        timeOfRequest.minus(30, ChronoUnit.SECONDS),
                        timeOfRequest.plus(30, ChronoUnit.SECONDS),
                        serverTime)
                .isBetween(timeOfRequest.minus(30, ChronoUnit.SECONDS), timeOfRequest.plus(30, ChronoUnit.SECONDS));
    }
}
