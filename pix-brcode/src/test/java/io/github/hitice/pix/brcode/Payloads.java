package io.github.hitice.pix.brcode;

/** Helpers to write payloads by hand in tests. */
final class Payloads {

    private Payloads() {
    }

    /** Encodes one data object. */
    static String tlv(String id, String value) {
        int length = value.codePointCount(0, value.length());
        return id + String.format("%02d", length) + value;
    }

    /** Appends a correct CRC field to the body. */
    static String withCrc(String body) {
        String covered = body + "6304";
        return covered + Crc16.hex(covered);
    }

    /** A static payload with the given root fields between 26 and 63, with a correct CRC. */
    static String staticWith(String merchantAccount, String rest) {
        return withCrc(tlv("00", "01") + tlv("26", merchantAccount) + rest);
    }

    static String pixAccount(String key) {
        return tlv("00", "br.gov.bcb.pix") + tlv("01", key);
    }

    static final String EVP = "123e4567-e12b-12d1-a456-426655440000";

    /** Fields 52 to 62 of the static example of the Pix manual. */
    static String commonFields() {
        return tlv("52", "0000") + tlv("53", "986") + tlv("58", "BR") + tlv("59", "Fulano de Tal")
                + tlv("60", "BRASILIA") + tlv("62", tlv("05", "***"));
    }
}
