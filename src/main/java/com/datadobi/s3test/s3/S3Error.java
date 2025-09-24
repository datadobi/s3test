package com.datadobi.s3test.s3;

import com.google.common.collect.ImmutableMap;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.annotation.Nullable;

/**
 * An enumeration representing AWS error codes taken from https://docs.aws.amazon.com/AmazonS3/latest/API/ErrorResponses.html#ErrorCodeList
 */
public enum S3Error {
    /**
     * Access Denied
     */
    AccessDenied("AccessDenied", 403),
    /**
     * There is a problem with your AWS account that prevents the operation from completing successfully.
     * Please contact AWS Support for further assistance, see Contact Us.
     */
    AccountProblem("AccountProblem", 403),
    /**
     * All access to this Amazon S3 resource has been disabled. Please contact AWS Support for further assistance, see Contact Us.
     */
    AllAccessDisabled("AllAccessDisabled", 403),
    /**
     * The email address you provided is associated with more than one account.
     */
    AmbiguousGrantByEmailAddress("AmbiguousGrantByEmailAddress", 400),
    /**
     * The authorization header you provided is invalid.
     */
    AuthorizationHeaderMalformed("AuthorizationHeaderMalformed", 400),
    /**
     * The Content-MD5 you specified did not match what we received.
     */
    BadDigest("BadDigest", 400),
    /**
     * The requested bucket name is not available. The bucket namespace is shared by all users of the system. Please select a different name and try again.
     */
    BucketAlreadyExists("BucketAlreadyExists", 409),
    /**
     * The bucket you tried to create already exists, and you own it. Amazon S3 returns this error in all AWS Regions except us-east-1 (N. Virginia).
     * For legacy compatibility, if you re-create an existing bucket that you already own in us-east-1, Amazon S3 returns 200 OK and resets the bucket access control lists (ACLs).
     */
    BucketAlreadyOwnedByYou("BucketAlreadyOwnedByYou", 409),
    /**
     * The bucket you tried to delete is not empty.
     */
    BucketNotEmpty("BucketNotEmpty", 409),
    /**
     * This request does not support credentials.
     */
    CredentialsNotSupported("CredentialsNotSupported", 400),
    /**
     * Cross-location logging not allowed. Buckets in one geographic location cannot log information to a bucket in another location.
     */
    CrossLocationLoggingProhibited("CrossLocationLoggingProhibited", 403),
    /**
     * Your proposed upload is smaller than the minimum allowed object size.
     */
    EntityTooSmall("EntityTooSmall", 400),
    /**
     * Your proposed upload exceeds the maximum allowed object size.
     */
    EntityTooLarge("EntityTooLarge", 400),
    /**
     * The provided token has expired.
     */
    ExpiredToken("ExpiredToken", 400),
    /**
     * Indicates that you are attempting to access a bucket from a different region than where the bucket exists. To avoid this error, use the --region option.
     * For example: aws s3 cp awsexample.txt s3://testbucket/ --region ap-east-1.
     */
    IllegalLocationConstraintException("IllegalLocationConstraintException", 400),
    /**
     * Indicates that the versioning configuration specified in the request is invalid.
     */
    IllegalVersioningConfigurationException("IllegalVersioningConfigurationException", 400),
    /**
     * You did not provide the number of bytes specified by the Content-Length HTTP header.
     */
    IncompleteBody("IncompleteBody", 400),
    /**
     * POST requires exactly one file upload per request.
     */
    IncorrectNumberOfFilesInPostRequest("IncorrectNumberOfFilesInPostRequest", 400),
    /**
     * Inline data exceeds the maximum allowed size.
     */
    InlineDataTooLarge("InlineDataTooLarge", 400),
    /**
     * We encountered an internal error. Please try again.
     */
    InternalError("InternalError", 500),
    /**
     * The AWS access key ID you provided does not exist in our records.
     */
    InvalidAccessKeyId("InvalidAccessKeyId", 403),
    /**
     * You must specify the Anonymous role.
     */
    InvalidAddressingHeader("InvalidAddressingHeader"),
    /**
     * Invalid Argument
     */
    InvalidArgument("InvalidArgument", 400),
    /**
     * The specified bucket is not valid.
     */
    InvalidBucketName("InvalidBucketName", 400),
    /**
     * The request is not valid with the current state of the bucket.
     */
    InvalidBucketState("InvalidBucketState", 409),
    /**
     * The Content-MD5 you specified is not valid.
     */
    InvalidDigest("InvalidDigest", 400),
    /**
     * The encryption request you specified is not valid. The valid value is AES256.
     */
    InvalidEncryptionAlgorithmError("InvalidEncryptionAlgorithmError", 400),
    /**
     * The specified location constraint is not valid. For more information about Regions, see How to Select a Region for Your Buckets.
     */
    InvalidLocationConstraint("InvalidLocationConstraint", 400),
    /**
     * The operation is not valid for the current state of the object.
     */
    InvalidObjectState("InvalidObjectState", 403),
    /**
     * One or more of the specified parts could not be found. The part might not have been uploaded, or the specified entity tag might not have matched the
     * part's entity tag.
     */
    InvalidPart("InvalidPart", 400),
    /**
     * The list of parts was not in ascending order. Parts list must be specified in order by part number.
     */
    InvalidPartOrder("InvalidPartOrder", 400),
    /**
     * All access to this object has been disabled. Please contact AWS Support for further assistance, see Contact Us.
     */
    InvalidPayer("InvalidPayer", 403),
    /**
     * The content of the form does not meet the conditions specified in the policy document.
     */
    InvalidPolicyDocument("InvalidPolicyDocument", 400),
    /**
     * The requested range cannot be satisfied.
     */
    InvalidRange("InvalidRange", 416),
    /**
     * Please use AWS4-HMAC-SHA256.
     * SOAP requests must be made over an HTTPS connection.
     * Amazon S3 Transfer Acceleration is not supported for buckets with non-DNS compliant names.
     * Amazon S3 Transfer Acceleration is not supported for buckets with periods (.) in their names.
     * Amazon S3 Transfer Accelerate endpoint only supports virtual style requests.
     * Amazon S3 Transfer Accelerate is not configured on this bucket.
     * Amazon S3 Transfer Accelerate is disabled on this bucket.
     * Amazon S3 Transfer Acceleration is not supported on this bucket. Contact AWS Support for more information.
     * Amazon S3 Transfer Acceleration cannot be enabled on this bucket. Contact AWS Support for more information.
     */
    InvalidRequest("InvalidRequest", 400),
    /**
     * The provided security credentials are not valid.
     */
    InvalidSecurity("InvalidSecurity", 403),
    /**
     * The SOAP request body is invalid.
     */
    InvalidSOAPRequest("InvalidSOAPRequest", 400),
    /**
     * The storage class you specified is not valid.
     */
    InvalidStorageClass("InvalidStorageClass", 400),
    /**
     * The target bucket for logging does not exist, is not owned by you, or does not have the appropriate grants for the log-delivery group.
     */
    InvalidTargetBucketForLogging("InvalidTargetBucketForLogging", 400),
    /**
     * The provided token is malformed or otherwise invalid.
     */
    InvalidToken("InvalidToken", 400),
    /**
     * Couldn't parse the specified URI.
     */
    InvalidURI("InvalidURI", 400),
    /**
     * Your key is too long.
     */
    KeyTooLongError("KeyTooLongError", 400),
    /**
     * The XML you provided was not well-formed or did not validate against our published schema.
     */
    MalformedACLError("MalformedACLError", 400),
    /**
     * The body of your POST request is not well-formed multipart/form-data.
     */
    MalformedPOSTRequest("MalformedPOSTRequest", 400),
    /**
     * This happens when the user sends malformed XML (XML that doesn't conform to the published XSD) for the configuration. The error message is, The XML you
     * provided was not well - formed or did not validate against our published schema.
     */
    MalformedXML("MalformedXML", 400),
    /**
     * Your request was too big.
     */
    MaxMessageLengthExceeded("MaxMessageLengthExceeded", 400),
    /**
     * Your POST request fields preceding the upload file were too large.
     */
    MaxPostPreDataLengthExceededError("MaxPostPreDataLengthExceededError", 400),
    /**
     * Your metadata headers exceed the maximum allowed metadata size.
     */
    MetadataTooLarge("MetadataTooLarge", 400),
    /**
     * The specified method is not allowed against this resource.
     */
    MethodNotAllowed("MethodNotAllowed", 405),
    /**
     * A SOAP attachment was expected, but none were found.
     */
    MissingAttachment("MissingAttachment"),
    /**
     * You must provide the Content-Length HTTP header.
     */
    MissingContentLength("MissingContentLength", 411),
    /**
     * This happens when the user sends an empty XML document as a request.
     */
    MissingRequestBodyError("MissingRequestBodyError", 400),
    /**
     * The SOAP 1.1 request is missing a security element.
     */
    MissingSecurityElement("MissingSecurityElement", 400),
    /**
     * Your request is missing a required header.
     */
    MissingSecurityHeader("MissingSecurityHeader", 400),
    /**
     * There is no such thing as a logging status subresource for a key.
     */
    NoLoggingStatusForKey("NoLoggingStatusForKey", 400),
    /**
     * The specified bucket does not exist.
     */
    NoSuchBucket("NoSuchBucket", 404),
    /**
     * The specified bucket does not have a bucket policy.
     */
    NoSuchBucketPolicy("NoSuchBucketPolicy", 404),
    /**
     * The specified key does not exist.
     */
    NoSuchKey("NoSuchKey", 404),
    /**
     * The lifecycle configuration does not exist.
     */
    NoSuchLifecycleConfiguration("NoSuchLifecycleConfiguration", 404),
    /**
     * The specified multipart upload does not exist. The upload ID might be invalid, or the multipart upload might have been aborted or completed.
     */
    NoSuchUpload("NoSuchUpload", 404),
    /**
     * Indicates that the version ID specified in the request does not match an existing version.
     */
    NoSuchVersion("NoSuchVersion", 404),
    /**
     * A header you provided implies functionality that is not implemented.
     */
    NotImplemented("NotImplemented", 501),
    /**
     * Your account is not signed up for the Amazon S3 service. You must sign up before you can use Amazon S3. You can sign up at the following URL:
     * https://aws.amazon.com/s3
     */
    NotSignedUp("NotSignedUp", 403),
    /**
     * A conflicting conditional operation is currently in progress against this resource. Try again.
     */
    OperationAborted("OperationAborted", 409),
    /**
     * The bucket you are attempting to access must be addressed using the specified endpoint. Send all future requests to this endpoint.
     */
    PermanentRedirect("PermanentRedirect", 301),
    /**
     * At least one of the preconditions you specified did not hold.
     */
    PreconditionFailed("PreconditionFailed", 412),
    /**
     * Temporary redirect.
     */
    Redirect("Redirect", 307),
    /**
     * Object restore is already in progress.
     */
    RestoreAlreadyInProgress("RestoreAlreadyInProgress", 409),
    /**
     * Bucket POST must be of the enclosure-type multipart/form-data.
     */
    RequestIsNotMultiPartContent("RequestIsNotMultiPartContent", 400),
    /**
     * Your socket connection to the server was not read from or written to within the timeout period.
     */
    RequestTimeout("RequestTimeout", 400),
    /**
     * The difference between the request time and the server's time is too large.
     */
    RequestTimeTooSkewed("RequestTimeTooSkewed", 403),
    /**
     * Requesting the torrent file of a bucket is not permitted.
     */
    RequestTorrentOfBucketError("RequestTorrentOfBucketError", 400),
    /**
     * The server-side encryption configuration was not found.
     */
    ServerSideEncryptionConfigurationNotFoundError("ServerSideEncryptionConfigurationNotFoundError", 400),
    /**
     * Reduce your request rate.
     */
    ServiceUnavailable("ServiceUnavailable", 503),
    /**
     * The request signature we calculated does not match the signature you provided. Check your AWS secret access key and signing method. For more information,
     * see REST Authentication and SOAP Authentication for details.
     */
    SignatureDoesNotMatch("SignatureDoesNotMatch", 403),
    /**
     * Reduce your request rate.
     */
    SlowDown("SlowDown", 503),
    /**
     * You are being redirected to the bucket while DNS updates.
     */
    TemporaryRedirect("TemporaryRedirect", 307),
    /**
     * The provided token must be refreshed.
     */
    TokenRefreshRequired("TokenRefreshRequired", 400),
    /**
     * You have attempted to create more buckets than allowed.
     */
    TooManyBuckets("TooManyBuckets", 400),
    /**
     * This request does not support content.
     */
    UnexpectedContent("UnexpectedContent", 400),
    /**
     * The email address you provided does not match any account on record.
     */
    UnresolvableGrantByEmailAddress("UnresolvableGrantByEmailAddress", 400),
    /**
     * The bucket POST must contain the specified field name. If it is specified, check the order of the fields.
     */
    UserKeyMustBeSpecified("UserKeyMustBeSpecified", 400),

    /**
     * Undocumented error code by AWS.
     * <p>
     * The bucket does not have object lock configuration.
     */
    ObjectLockConfigurationNotFoundError("ObjectLockConfigurationNotFoundError", 404),
    // returned by the google apis
    ObjectLockConfigurationNotFound("ObjectLockConfigurationNotFound", 404),
    // returned by ?
    NoSuchObjectLockConfiguration("NoSuchObjectLockConfiguration", 404, 400),
    ;

    private final String errorCode;
    private final int[] statusCodes;

    S3Error(String errorCode, int... statusCodes) {
        this.errorCode = errorCode;
        this.statusCodes = statusCodes;
    }

    private static final ImmutableMap<String, S3Error> ERRORS_BY_ERROR_CODE;

    static {
        ImmutableMap.Builder<String, S3Error> builder = ImmutableMap.builder();
        for (S3Error value : values()) {
            builder.put(value.errorCode.toLowerCase(), value);
        }
        ERRORS_BY_ERROR_CODE = builder.build();
    }

    @Nullable
    public static S3Error findError(AwsServiceException exception) {
        if (!(exception instanceof S3Exception)) {
            return null;
        }
        int statusCode = exception.statusCode();
        String errorCode = exception.awsErrorDetails().errorCode();
        S3Error error = errorCode == null ? null : ERRORS_BY_ERROR_CODE.get(errorCode.toLowerCase());
        if (error != null) {
            if (error.statusCodes.length == 0) {
                return error;
            }
            for (int i = 0; i < error.statusCodes.length; i++) {
                if (error.statusCodes[i] == statusCode) {
                    return error;
                }
            }
        }
        return null;
    }

    @Nullable
    public static S3Error findError(String code) {
        return ERRORS_BY_ERROR_CODE.get(code.toLowerCase());
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatusCode() {
        return statusCodes.length > 0 ? statusCodes[0] : -1;
    }
}

