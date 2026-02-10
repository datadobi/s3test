# Datadobi S3 Test Suite

This repository contains a suite of S3 client tests in the form of JUnit tests.
These tests are used by [Datadobi](https://www.datadobi.com) to test edge case behaviour of S3 server implementations.

## Specifying Target S3 Servers

The test suite is expected to be run against an empty bucket on an S3 server.
The target S3 bucket can be specified using an `http`, `https`, or `s3profile` URI.

`http` and `https` URIs are expected to follow the pattern `http[s]://[<access_key_id>:<secret_access_key>@]<endpoint>[:<port>][/<bucket_name>]`.

`s3profile` URIs are of the form `s3profile://<profile_name>[/<bucket_name>]`.
The profile name refers to a profile that is read from the AWS CLI configuration files location in the `.aws` directory in your home directory.

The target URI can be passed to the tests either via the command line (see below) or by setting it as the value of the `S3TEST_URI` environment variable.

If the `bucket_name` is omitted from the target URI, each test will create and destroy a new bucket for each test case.
This slows down test execution, but improves test isolation.

## Running Tests

### Test Runner

This project contains a simple wrapper test runner in the `com.datadobi.s3test.RunTests` class.
This can be launched either from an IDE or by using `gradlew run --console=plain --args="<test_args>"`.

Individual test cases can be explictly included or excluded using the `-i`/`--include` and `-e`/`--exclude` command line arguments.

Additional configuration can be loaded using the `-c`/`--config` command line argument followed by a path to a configuration file (see below).

### Running from an IDE

Since each test is a JUnit test case, tests can be easily executed from your IDE of choice.
Instructions on how to run JUnit tests from IDEs is out of scope for this README.

## Configuration File

The configuration file is a TOML file that specifies server quirks to accommodate non-standard S3 implementations.

### Syntax

```toml
quirks = [
    "QUIRK_NAME_1",
    "QUIRK_NAME_2"
]
```

### Available Quirks

- `CONTENT_TYPE_NOT_SET_FOR_KEYS_WITH_TRAILING_SLASH` - The server drops user specified Content-Type values when the object key ends with '/'
- `ETAG_EMPTY_AFTER_COPY_OBJECT` - After copying an object, an empty ETag is returned
- `GET_OBJECT_PARTCOUNT_NOT_SUPPORTED` - The server does not return `x-amz-mp-parts-count`
- `GET_OBJECT_PART_NOT_SUPPORTED` - The server does not support downloading individual parts
- `KEYS_ARE_SORTED_IN_UTF16_BINARY_ORDER` - The server returns object keys in UTF-16 lexicographical order instead of UTF-8
- `KEYS_WITH_CODEPOINT_MIN_REJECTED` - Server rejects U+0001
- `KEYS_WITH_CODEPOINTS_OUTSIDE_BMP_REJECTED` - Server rejects keys containing code points that are greater than U+FFFF
- `KEYS_WITH_INVALID_UTF8_NOT_REJECTED` - Server does not perform strict UTF-8 validation
- `KEYS_WITH_NULL_ARE_TRUNCATED` - The server truncates object keys containing null bytes (typically implementations that use a language with zero terminated strings)
- `KEYS_WITH_NULL_NOT_REJECTED` - The server does not reject object keys containing null bytes
- `KEYS_WITH_SLASHES_CREATE_IMPLICIT_OBJECTS` - The server behaves similarly to S3ExpressOneZone directory buckets
- `MULTIPART_SIZES_NOT_KEPT` - After completing a multipart upload, the server does not guarantee that the size or number of parts uploaded by the client will be preserved
- `PUT_OBJECT_IF_MATCH_ETAG_NOT_SUPPORTED` - The server does not support `If-Match: <etag>` (generic HTTP feature not supported by AWS S3)
- `PUT_OBJECT_IF_NONE_MATCH_ETAG_NOT_SUPPORTED` - The server does not support `If-None-Match: <etag>` (generic HTTP feature not supported by AWS S3)
- `PUT_OBJECT_IF_NONE_MATCH_STAR_NOT_SUPPORTED` - The server does not support `If-None-Match: *`
- `STORAGE_CLASS_NOT_KEPT` - The server does not retain (or return) the storage class specified by the client

## Logging HTTP Requests

When tests are run using the `RunTests` harness, the command line flag `-l`/`--log` can be used to specify a target path for HTTP request logs.

Alternatively, the environment variable `S3TEST_WIRELOG` can be set to a path to enable HTTP request logging.

If both the command line flag and environment variable are set, the command line flag takes precedence.
