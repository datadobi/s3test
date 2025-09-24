package com.datadobi.s3test.util;

public class InvalidUtf8Encoder {
    public static byte[] utf8Encode(int codePoint) {
        return utf8Encode(codePoint, -1);
    }

    public static byte[] utf8Encode(int codePoint, int byteCount) {
        //WARNING: this is an INVALID implementation!
        //that is *the intention*
        //we want to test what happens if random clients outside of our control uploaded exotic key names to the S3 server
        //this is an invalid implementation because valid implementations :
        // * should reject invalid codePoints (i.e. all the surrogates and all "Unallocated code points" as defined in the spec)
        // * should encode the codepoint in the smallest possible amount of bytes, according to the UTF-8 spec

        if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT || byteCount > 4) {
            throw new IllegalArgumentException("We only go so far ...");
        }

        if (byteCount <= 0) {
            if (codePoint < 0x80) {
                byteCount = 1;
            } else if (codePoint < 0x0800) {
                byteCount = 2;
            } else if (codePoint < 0x10000) {
                byteCount = 3;
            } else {
                byteCount = 4;
            }
        }

        switch (byteCount) {
            case 1:
                return new byte[]{(byte) codePoint};
            case 2:
                //two byte utf-8 encoding (max 11 bits): 110x_xxxx, 10xx_xxxx
                return new byte[]{(byte) (0b1100_0000 | codePoint >> 6 & 0b0001_1111), (byte) (0b1000_0000 | codePoint & 0b0011_1111)};
            case 3:
                //three byte utf-8 encoding (max 16 bits): 1110_xxxx, 10xx_xxxx, 10xx_xxxx
                return new byte[]{(byte) (0b1110_0000 | codePoint >> 12 & 0b0000_1111), (byte) (0b1000_0000 | codePoint >> 6 & 0b0011_1111), (byte) (0b1000_0000 | codePoint & 0b0011_1111)};
            case 4:
                //four byte utf-8 encoding (max 21 bits): 1111_0xxx, 10xx_xxxx, 10xx_xxxx, 10xx_xxxx
                return new byte[]{(byte) (0b1111_0000 | codePoint >> 18 & 0b0000_0111), (byte) (0b1000_0000 | codePoint >> 12 & 0b0011_1111), (byte) (0b1000_0000 | codePoint >> 6 & 0b0011_1111), (byte) (0b1000_0000 | codePoint & 0b0011_1111)};
            default:
                throw new IllegalArgumentException("Cannot encode to more than four bytes!");
        }
    }
}
