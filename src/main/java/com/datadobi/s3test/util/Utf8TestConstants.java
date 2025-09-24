package com.datadobi.s3test.util;

public interface Utf8TestConstants {
    /**
     * Space
     * <p>
     * Interesting because
     * - it is not interesting at all :)
     * https://www.fileformat.info/info/unicode/char/0020/index.htm
     */
    int SPACE = 32;
    byte[] SPACE_OFFICIAL_UTF8 = literal("00100000");

    /**
     * Latin small letter y with diaeresis
     * <p>
     * Interesting characteristics:
     * - it is the highest code point representable in Latin-1
     * - its UTF-8 encoding takes 2 bytes
     * <p>
     * https://www.fileformat.info/info/unicode/char/00ff/index.htm
     */
    int LATIN_Y_WITH_DIARESIS = 255;
    byte[] LATIN_Y_WITH_DIARESIS_OFFICIAL_UTF8 = literal("11000011:10111111");

    /**
     * the "NKo letter n"
     * <p>
     * Interesting characteristics:
     * - it is _in_ the Basic Multilingual Plane (BMP),
     * - but not representable in the Latin-1 encoding
     * - its UTF-8 encoding takes 2 bytes
     * <p>
     * https://www.fileformat.info/info/unicode/char/07d2/index.htm
     */
    int NKO_N = 2002;
    byte[] NKO_N_OFFICIAL_UTF8 = literal("1101_1111:10010010");

    /**
     * the devanagari letter
     * <p>
     * Interesting characteristics:
     * - it is _in_ the Basic Multilingual Plane (BMP),
     * - but not representable in the Latin-1 encoding
     * - its UTF-8 encoding takes 3 bytes
     * <p>
     * https://www.fileformat.info/info/unicode/char/0939/index.htm
     */
    int DEVANAGARI = 2361;
    byte[] DEVANAGARI_OFFICIAL_UTF8 = literal("11100000:10100100:10111001");

    /**
     * the clapping hands emoji
     * <p>
     * Interesting characteristics:
     * - it is not in the Basic Multilingual Plane (BMP)
     * - since it is not in the BMP, needs *two* 16-bit chars in UTF-16 (so also needs two Java 'char's)
     * - its UTF-8 encoding takes 4 bytes
     * <p>
     * https://www.fileformat.info/info/unicode/char/1f44f/index.htm
     */
    int CLAPPING_HANDS = 128079;
    int CLAPPING_HANDS_HIGH_SURROGATE = 55357;
    int CLAPPING_HANDS_LOW_SURROGATE = 56399;
    String CLAPPING_HANDS_STRING = new StringBuilder().appendCodePoint(CLAPPING_HANDS).toString();
    byte[] CLAPPING_HANDS_OFFICIAL_UTF8 = literal("11110000:10011111:10010001:10001111");

    /**
     * Latin small ligature ff
     * <p>
     * Interesting characteristics:
     * - it is _in_ the Basic Multilingual Plane (BMP),
     * - but above the surrogate codepoints.
     * - its UTF-8 encoding is 3 bytes
     * <p>
     * https://www.fileformat.info/info/unicode/char/fb00/index.htm
     */
    int FF_LIGATURE = 0xFB00;
    String FF_LIGATURE_STRING = new StringBuilder().appendCodePoint(FF_LIGATURE).toString();
    byte[] FF_LIGATURE_OFFICIAL_UTF8 = literal("11101111:10101100:10000000");

    /**
     * Arabic letter tcheheh initial form
     * <p>
     * Interesting characteristics:
     * - it is _in_ the Basic Multilingual Plane (BMP),
     * - but above the surrogate codepoints.
     * - its UTF-8 encoding is 3 bytes
     * <p>
     * https://www.fileformat.info/info/unicode/char/fb80/index.htm
     */
    int TCHEHEH_INITIAL_FORM = 64384;
    String TCHEHEH_INITIAL_FORM_STRING = new StringBuilder().appendCodePoint(TCHEHEH_INITIAL_FORM).toString();
    byte[] TCHEHEH_INITIAL_FORM_OFFICIAL_UTF8 = literal("11101111:10101110:10000000");

    /**
     * Unallocated code point
     */
    int UNALLOCATED_CODE_POINT = 2161;

    /**
     * Parses a String in the form of "00101111:10101010:11001100" to a byte array.
     *
     * @param s A colon-separated list of byte literals. Each literal must be 8 bytes long. Underscores are discarded, so you can insert them for readability
     * @return The byte array representing the given string.
     * @throws NumberFormatException if any literal is not 8 bits long, or is not a binary literal.
     */
    private static byte[] literal(String s) {
        String[] parts = s.split(":");
        byte[] result = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].replace("_", "");
            if (part.length() != 8) {
                throw new NumberFormatException();
            }
            result[i] = (byte) Integer.parseInt(part, 2);
        }
        return result;
    }
}
