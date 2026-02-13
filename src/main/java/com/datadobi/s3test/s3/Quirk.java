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
package com.datadobi.s3test.s3;

public enum Quirk {
    /**
     * The server does not support checksum based data integrity checks.
     */
    CHECKSUMS_NOT_SUPPORTED,
    /**
     * The server drops user specified Content-Type values when the object key ends with '/'.
     */
    CONTENT_TYPE_NOT_SET_FOR_KEYS_WITH_TRAILING_SLASH,
    /**
     * The server does not support deleting individual object versions.
     */
    DELETE_OBJECT_VERSION_NOT_SUPPORTED,
    /**
     * After copying an object, an empty ETag is returned.
     */
    ETAG_EMPTY_AFTER_COPY_OBJECT,
    /**
     * The server does not support downloading individual parts
     */
    GET_OBJECT_PART_NOT_SUPPORTED,
    /**
     * The server does not return `x-amz-mp-parts-count`
     */
    GET_OBJECT_PARTCOUNT_NOT_SUPPORTED,
    /**
     * The server returns object keys in UTF-16 lexicographical order instead of UTF-8.
     */
    KEYS_ARE_SORTED_IN_UTF16_BINARY_ORDER,
    /**
     * Server rejects U+0001.
     */
    KEYS_WITH_CODEPOINT_MIN_REJECTED,
    /**
     * Server rejects keys containing code points that are greater than U+FFFF.
     */
    KEYS_WITH_CODEPOINTS_OUTSIDE_BMP_REJECTED,
    /**
     * Server does not perform strict UTF-8 validation.
     */
    KEYS_WITH_INVALID_UTF8_NOT_REJECTED,
    /**
     * The server does not reject object keys containing null bytes.
     */
    KEYS_WITH_NULL_NOT_REJECTED,
    /**
     * The server truncates object keys containing null bytes (typically implementations that use a language with zero
     * terminated strings).
     */
    KEYS_WITH_NULL_ARE_TRUNCATED,
    /**
     * The server behaves similarly to S3ExpressOneZone directory buckets.
     */
    KEYS_WITH_SLASHES_CREATE_IMPLICIT_OBJECTS,
    /**
     * The server does not support multipart uploads.
     */
    MULTIPART_UPLOAD_NOT_SUPPORTED,
    /**
     * After completing a multipart upload, the server does not guarantee that the size or number of parts uploaded
     * by the client will be preserved.
     */
    MULTIPART_SIZES_NOT_KEPT,
    /**
     * The server does not support {@code If-Match: <etag>}.
     * <p>
     * This is a generic HTTP feature not supported by AWS S3.
     */
    PUT_OBJECT_IF_MATCH_ETAG_NOT_SUPPORTED,
    /**
     * The server does not support {@code If-None-Match: <etag>}.
     * <p>
     * This is a generic HTTP feature not supported by AWS S3.
     */
    PUT_OBJECT_IF_NONE_MATCH_ETAG_NOT_SUPPORTED,
    /**
     * The server does not support {@code If-None-Match: *}.
     */
    PUT_OBJECT_IF_NONE_MATCH_STAR_NOT_SUPPORTED,
    /**
     * The server does not retain (or return) the storage class specified by the client.
     */
    STORAGE_CLASS_NOT_KEPT,
}
