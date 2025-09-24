package com.datadobi.s3test;

import com.datadobi.s3test.s3.S3;
import com.datadobi.s3test.s3.S3TestBase;
import com.datadobi.s3test.util.Pair;
import com.datadobi.s3test.util.TLS;
import com.google.common.collect.ImmutableList;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Test;
import software.amazon.awssdk.auth.signer.AwsS3V4Signer;
import software.amazon.awssdk.auth.signer.params.AwsS3V4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

import static com.datadobi.s3test.s3.ServiceDefinition.Restriction.*;
import static com.datadobi.s3test.util.InvalidUtf8Encoder.utf8Encode;
import static com.datadobi.s3test.util.Utf8TestConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * <p>This test checks that an S3 implementation follows the S3, Unicode and UTF-8 specs.</p>
 *
 * <p>We rely on the sort order of keys, which described in https://docs.aws.amazon.com/AmazonS3/latest/dev/ListingKeysUsingAPIs.html
 * <blockquote>
 * List results are always returned in UTF-8 binary order.
 * </blockquote></p>
 * <p>This means servers need to adhere to all the specs.</p>
 */
public class ObjectKeyTests extends S3TestBase {
    public ObjectKeyTests() throws IOException {
    }

    @Test
    public void testSimpleObjectKeySigning() throws IOException {
        var key = "key";

        var content = "Content: " + UUID.randomUUID();
        var data = content.getBytes(UTF_8);

        var status = putObject(target.signingRegion(), data, key.getBytes(UTF_8));
        assertThat(status).as("Status should indicate success").matches(s -> s / 100 == 2, "HTTP 2xx status");
        var keys = bucket.listObjectKeys(S3.ListObjectsVersion.V2);
        assertThat(keys).contains(key);
    }

    @Test
    public void testKeyNamesWithHighCodePointsAreAccepted() throws IOException {
        assumeFalse(target.hasRestrictions(KEYS_WITH_CODEPOINTS_OUTSIDE_BMP_REJECTED));

        var clappingHands = utf8Encode(CLAPPING_HANDS);
        var keyPrefix = "high-codepoints-";
        var keySuffix = ".key";

        var expectedKey = new StringBuilder().append(keyPrefix).appendCodePoint(CLAPPING_HANDS).append(keySuffix).toString();

        var data = ("Content: " + UUID.randomUUID()).getBytes(UTF_8);

        bucket.putObject("a/b/c", data);

        var status = putObject(target.signingRegion(), data, keyPrefix.getBytes(UTF_8), clappingHands, keySuffix.getBytes(UTF_8));
        assertThat(status).as("Status should indicate success").matches(s -> s / 100 == 2, "HTTP 2xx status");
        var keys = bucket.listObjectKeys(S3.ListObjectsVersion.V2);
        assertThat(keys).contains(expectedKey);
    }

    @Test
    public void thatPathLikeKeysDontCreateDirectoryObjects() {
        assumeFalse(target.hasRestrictions(KEYS_WITH_SLASHES_CREATE_IMPLICIT_OBJECTS));

        bucket.putObject("a/b/c", "abcd");

        var keys = bucket.listObjectKeys(S3.ListObjectsVersion.V2);
        assertThat(keys).containsExactly("a/b/c");
    }

    @Test
    public void testSurrogatePairsAreRejected() throws IOException {
        assumeFalse(target.hasRestrictions(KEYS_WITH_INVALID_UTF8_NOT_REJECTED));

        //surrogates in Unicode are a compatibility mechanism intended for UTF-16 encoding.
        //surrogates are always expected to come in pairs (a high surrogate followed by a low surrogate) and one pair maps to one codepoint outside of the BMP
        //according to the specification, codepoints corresponding to surrogates should never be encoded in UTF-8. Instead, the codepoint outside the BMP should
        //be encoded in the byte-stream.

        //in this test we simulate a bad UTF-8 encoder, which _does_ encode the codepoints of the surrogate pair in the byte-stream
        //then we ask the server to generate an object with this key, and see what happens.
        //we expect the server to reject the bad key

        //double check that we're correct: Java's char type is a 16-bit type, corresponding to the UTF-16 encoding
        //i.e. you can create a String in Java by appending the codepoints of the surrogate pair.
        //the codePoints stream yields real Unicode code-points (i.e. 21-bit codepoints, packed in a 32-bit integer). So the constructed string
        //
        var stringFromSurrogates = new StringBuilder().appendCodePoint(CLAPPING_HANDS_HIGH_SURROGATE)
                .appendCodePoint(CLAPPING_HANDS_LOW_SURROGATE)
                .toString();
        var stringFromSupplementaryCp = new StringBuilder().appendCodePoint(CLAPPING_HANDS).toString();
        assertThat(stringFromSurrogates).isEqualTo(stringFromSupplementaryCp);

        //create an invalid utf-8 byte sequence, encoding the surrogate pair
        var highSurrogate = utf8Encode(CLAPPING_HANDS_HIGH_SURROGATE);
        var lowSurrogate = utf8Encode(CLAPPING_HANDS_LOW_SURROGATE);

        var keyPrefix = "surrogate-pairs-".getBytes(UTF_8);
        var keySuffix = ".key".getBytes(UTF_8);

        var data = ("Content: " + UUID.randomUUID()).getBytes(UTF_8);

        var status = putObject(target.signingRegion(), data, keyPrefix, highSurrogate, lowSurrogate, keySuffix);
        assertThat(status).as("Put object with invalid UTF-8 bytes should be rejected").matches(s -> s / 100 > 3, "HTTP status of 300 or above");
    }


    @Test
    public void testCodePointMinIsAccepted() throws IOException {
        assumeFalse(target.hasRestrictions(KEYS_WITH_CODEPOINT_MIN_REJECTED));

        var key = "min-codepoint-\u0001.key";
        var keyBytes = key.getBytes(UTF_8);
        var data = ("Content: " + UUID.randomUUID()).getBytes(UTF_8);
        var status = putObject(target.signingRegion(), data, keyBytes);
        assertThat(status).as("Put object with key containing min codepoint should be accepted").matches(s -> s / 100 == 2, "HTTP 2xx status");
    }

    @Test
    public void testNullIsRejected() throws IOException {
        assumeFalse(target.hasRestrictions(KEYS_WITH_NULL_NOT_REJECTED));

        var nullEncoding = new byte[]{0};

        var keyPrefix = "with-null-byte-".getBytes(UTF_8);
        var keySuffix = ".key".getBytes(UTF_8);

        var data = ("Content: " + UUID.randomUUID()).getBytes(UTF_8);

        var status = putObject(target.signingRegion(), data, keyPrefix, nullEncoding, keySuffix);
        assertThat(status).as("Put object with invalid UTF-8 bytes should be rejected").matches(s -> s / 100 > 3, "HTTP status of 300 or above");
    }

    @Test
    public void testOverlongNullIsRejected() throws IOException {
        assumeFalse(target.hasRestrictions(KEYS_WITH_INVALID_UTF8_NOT_REJECTED));

        var nullEncoding = new byte[]{(byte) 0xC0, (byte) 0x80};

        var keyPrefix = "keyPrefix".getBytes(UTF_8);
        var keySuffix = ".key".getBytes(UTF_8);

        var data = ("Content: " + UUID.randomUUID()).getBytes(UTF_8);

        var status = putObject(target.signingRegion(), data, keyPrefix, nullEncoding, keySuffix);
        assertThat(status).as("Put object with invalid UTF-8 bytes should be rejected").matches(s -> s / 100 > 3, "HTTP status of 300 or above");
    }

    @Test
    public void testOverlongEncodingsAreRejected() throws IOException {
        assumeFalse(target.hasRestrictions(KEYS_WITH_INVALID_UTF8_NOT_REJECTED));

        // https://en.wikipedia.org/wiki/UTF-8#Overlong_encodings
        // in theory, any codepoint can be encoded in 4 bytes in UTF-8, even if it _should_ be encoded in less bytes
        // this is called overlong encodings, and should be rejected

        // we are interested in testing that the server actually rejects these because an overlong encoding of a codepoint does not sort
        // the same as the 'tight' encoding of it.

        //create an invalid utf-8 byte sequence, with an overlong encoding of 'a'
        var a = utf8Encode('a', 4);

        var keyPrefix = "overlong-".getBytes(UTF_8);
        var keySuffix = ".key".getBytes(UTF_8);

        var data = ("Content: " + UUID.randomUUID()).getBytes(UTF_8);

        var status = putObject(target.signingRegion(), data, keyPrefix, a, keySuffix);
        assertThat(status).as("Put object with invalid UTF-8 bytes should be rejected").matches(s -> s / 100 > 3, "HTTP status of 300 or above");
    }

    @Test
    public void testThatServerDoesNotNormalizeCodePoints() throws IOException {
        // Unicode normalization : https://www.unicode.org/reports/tr15/#Norm_Forms
        // Unicode defines a normalization process, where certain combinations of code points are said to be equivalent to other code points.
        // for instance, an Å ['ANGSTROM SIGN'] is the codepoint U+212B
        // however, the codepoint A (U+0041) followed by the codepoint 'COMBINING RING ABOVE' (U+030A) is said to be equivalent
        // also the codepoint 'LATIN CAPITAL LETTER A WITH RING ABOVE' (U+00C5) is said to be equivalent
        // storage systems typically don't care about normalization, this is more important for searching and indexing,
        // but we want to make sure that the S3 server doesn't perform any conversion between the normalized forms.

        // some more info in the java tutorial : https://docs.oracle.com/javase/tutorial/i18n/text/normalizerapi.html


        List<List<String>> equivalentStringTuples = ImmutableList.of(
                ImmutableList.of("\u212B", "\u0041\u030A", "\u00C5"),
                ImmutableList.of("\u00F6", "o\u0308")
        );


        //sanity check of our test inputs
        //  the given strings should _not_ be equal to begin with
        //  but they should decompose to the same NFD decomposition
        for (var equivalentStrings : equivalentStringTuples) {
            assertThat(new HashSet<>(equivalentStrings)).hasSize(equivalentStrings.size());
            var normalized = equivalentStrings.stream().map(s -> Normalizer.normalize(s, Normalizer.Form.NFD)).collect(Collectors.toSet());
            assertThat(normalized).hasSize(1); //should be normalized to the same form
        }

        for (var equivalentStrings : equivalentStringTuples) {
            var testData = "Content: " + UUID.randomUUID();
            var status = putObject(target.signingRegion(), testData.getBytes(UTF_8), equivalentStrings.get(0).getBytes(UTF_8));
            assertThat(status).as("putting the object should succeed").matches(s -> s / 100 == 2, "HTTP 2xx status");

            //check that we can retrieve
            var roundTrip = new String(getObject(equivalentStrings.get(0)), UTF_8);
            assertThat(roundTrip).isEqualTo(testData);


            //trying the other keys should fail
            for (var equivalentString : equivalentStrings.subList(1, equivalentStrings.size())) {
                assertThatExceptionOfType(Throwable.class).isThrownBy(() -> getObject(equivalentString));
            }
        }
    }

    @Test
    public void thatServerSortsNullInUtf8Order() throws IOException {
        assumeTrue(target.hasRestrictions(KEYS_WITH_NULL_NOT_REJECTED));
        assumeFalse(target.hasRestrictions(KEYS_WITH_NULL_ARE_TRUNCATED));

        var nullEncoding = "\0".getBytes(UTF_8);
        var capitalAEncoding = "A".getBytes(UTF_8);

        var keyPrefix = "with-null-byte-".getBytes(UTF_8);
        var keySuffix = ".key".getBytes(UTF_8);

        var data = ("Content: " + UUID.randomUUID()).getBytes(UTF_8);

        var status = putObject(target.signingRegion(), data, keyPrefix, nullEncoding, keySuffix);
        assertThat(status).as("Put object with null key is accepted").matches(s -> s / 100 == 2, "HTTP status of 2** or below");

        status = putObject(target.signingRegion(), data, keyPrefix, capitalAEncoding, keySuffix);
        assertThat(status).as("Put object with non-null key is accepted").matches(s -> s / 100 == 2, "HTTP status of 2** or below");

        var keys = bucket.listObjectKeys(S3.ListObjectsVersion.V2);
        assertThat(keys).containsExactly("with-null-byte-\0.key", "with-null-byte-A.key");
    }

    private String urlEncode(byte[]... chunks) {
        var b = new StringBuilder();
        for (var chunk : chunks) {
            for (var b1 : chunk) {
                //for ASCII chars, use the regular percent-encoding
                if (/*b1 < 128 &&*/ b1 > 32) {
                    //^^ bytes are signed in Java, so always < 128...
                    b.append(URLEncoder.encode("" + (char) b1, StandardCharsets.US_ASCII));
                } else {
                    //for all other characters, explicitly control the percent-escaping to encode the binary code point
                    b.append(String.format("%%%02X", b1));
                }
            }
        }
        return b.toString();
    }

    /**
     * You need to specify the bytes, so that Java doesn't try to do UTF-8 validation and fixing client side. We really want to send these bytes
     * to the server.
     */
    private int putObject(Region signingRegion, byte[] testData, byte[]... keyBytes) throws IOException {
        var key = urlEncode(keyBytes);
        //don't use the standard SDK, we want to force invalid key names without client-side validation of the keys
        var full = target.endpoint().resolve(bucket.name() + "/" + key);
        var authHeaders = awsSignPutRequest(testData, full, signingRegion.id());

        var sslConnectionSocketFactory = new SSLConnectionSocketFactory(
                TLS.createLenientSSLSocketFactory(TLS.createLenientTrustManager()),
                new NoopHostnameVerifier()
        );

        try (var httpClient = HttpClientBuilder.create()
                //don't normalize URI's, we carefully craft them!
                .setDefaultRequestConfig(
                        RequestConfig.copy(RequestConfig.DEFAULT).setNormalizeUri(false).build())
                .setSSLSocketFactory(sslConnectionSocketFactory)
                .setSSLHostnameVerifier(new NoopHostnameVerifier())
                .build()) {
            var httpPut = new HttpPut(full);
            httpPut.setEntity(new ByteArrayEntity(testData));
            for (var header : authHeaders.entrySet()) {
                httpPut.setHeader(header.getKey(), header.getValue());
            }

            var attempt = 0;

            while (true) {
                var resp = httpClient.execute(httpPut, response -> {
                    byte[] responseContent;
                    var e = response.getEntity();
                    if (e != null) {
                        var out = new ByteArrayOutputStream();
                        e.writeTo(out);
                        responseContent = out.toByteArray();
                    } else {
                        responseContent = null;
                    }
                    return Pair.create(response.getStatusLine().getStatusCode(), responseContent);
                });

                // handle the case where the bucket does not exist yet
                if (resp.first() == 404 && attempt < 10) {
                    System.out.println("  Retry[" + attempt + "] putObject: " + resp.first());
                    try {
                        Thread.sleep(100 + attempt * 100);
                    } catch (InterruptedException interruptedException) {
                        // abort
                        return resp.first();
                    }
                    attempt++;
                } else {
                    return resp.first();
                }
            }
        }
    }

    @Nonnull
    private Map<String, String> awsSignPutRequest(byte[] testData, URI full, String signingRegion) {
        var credentials = target.getCredentials().resolveCredentials();

        //Use the AWS sdk to generate the Authorization header.
        var r = SdkHttpFullRequest.builder();

        r.method(SdkHttpMethod.PUT);
        r.contentStreamProvider(() -> new ByteArrayInputStream(testData));
        r.protocol(full.getScheme());
        r.uri(full);
        r.appendHeader("x-amz-content-sha256", hash(testData));
        r.appendHeader("Content-Type", "text/plain");
        r.appendHeader("Content-Length", Integer.toString(testData.length));

        var s = AwsS3V4Signer.create();
        var signedReq = s.sign(
                r.build(),
                AwsS3V4SignerParams.builder()
                        .signingName("s3")
                        .signingRegion(Region.of(signingRegion))
                        .awsCredentials(credentials)
                        .doubleUrlEncode(false)
                        .build()
        );

        var headers = signedReq.headers().entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().get(0)
        ));
        headers.remove("Content-Length");
        headers.remove("Host");
        return headers;
    }

    private void deleteObject(String key) {
        bucket.deleteObject(key);
    }

    private byte[] getObject(String key) throws IOException {
        try (var result = bucket.getObject(key)) {
            return result.readAllBytes();
        }
    }

    private String hash(byte[] data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
