package io.github.hitice.pix.brcode;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * CRC-16/CCITT-FALSE, the checksum of the EMV QRCPS (ID {@code 63}): polynomial {@code 0x1021},
 * initial value {@code 0xFFFF}, no reflection, no final XOR. The check value of the ASCII string
 * {@code 123456789} is {@code 0x29B1}.
 */
public final class Crc16 {

    private static final int[] TABLE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            int crc = i << 8;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
            }
            TABLE[i] = crc & 0xFFFF;
        }
    }

    private Crc16() {
    }

    /**
     * Computes the checksum of the given bytes.
     *
     * @param data the bytes
     * @return the checksum, from {@code 0} to {@code 0xFFFF}
     */
    public static int checksum(byte[] data) {
        Objects.requireNonNull(data, "data");
        int crc = 0xFFFF;
        for (byte b : data) {
            crc = ((crc << 8) ^ TABLE[((crc >>> 8) ^ b) & 0xFF]) & 0xFFFF;
        }
        return crc;
    }

    /**
     * Computes the checksum of the UTF-8 encoding of the text and formats it as four upper case
     * hexadecimal digits, as the BR Code expects in field {@code 63}. For a payload, pass every
     * character up to and including {@code 6304}.
     *
     * @param data the text
     * @return four upper case hexadecimal digits, for example {@code 29B1}
     */
    public static String hex(String data) {
        Objects.requireNonNull(data, "data");
        return String.format("%04X", checksum(data.getBytes(StandardCharsets.UTF_8)));
    }
}
