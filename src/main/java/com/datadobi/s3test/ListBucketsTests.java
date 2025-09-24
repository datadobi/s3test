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

    @Test
    public void listBucketsResponsesShouldReturnValidDateHeader() {
        var timeOfRequest = Instant.now();

        var listBucketsResponse = S3.listBuckets(s3);
        var headers = listBucketsResponse.sdkHttpResponse().headers();

        Assertions.assertThat(headers).containsKey("Date");

        var date = headers.get("Date").get(0);
        var serverTime = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(date));

        // coarse time validation, we don't want to test that the clocks of our test runners are perfectly in sync with the clocks of the server
        // rather, we want to know if the timezone information is correct etc
        Assertions.assertThat(serverTime).isBetween(timeOfRequest.minus(30, ChronoUnit.SECONDS), timeOfRequest.plus(30, ChronoUnit.SECONDS));
    }
}
