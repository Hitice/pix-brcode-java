package io.github.hitice.pix.brcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Crc16Test {

    @Test
    void checkValueOfCcittFalse() {
        assertThat(Crc16.hex("123456789")).isEqualTo("29B1");
        assertThat(Crc16.checksum("123456789".getBytes(StandardCharsets.US_ASCII))).isEqualTo(0x29B1);
    }

    @Test
    void emptyInputIsTheInitialValue() {
        assertThat(Crc16.checksum(new byte[0])).isEqualTo(0xFFFF);
        assertThat(Crc16.hex("")).isEqualTo("FFFF");
    }

    @Test
    void hexIsUpperCaseAndZeroPadded() {
        // Brute force a short input whose CRC has a leading zero nibble.
        String found = null;
        for (int i = 0; i < 100_000 && found == null; i++) {
            String candidate = Integer.toString(i);
            if (Crc16.checksum(candidate.getBytes(StandardCharsets.US_ASCII)) < 0x1000) {
                found = candidate;
            }
        }
        assertThat(found).isNotNull();
        assertThat(Crc16.hex(found)).hasSize(4).startsWith("0").matches("[0-9A-F]{4}");
    }

    @Test
    void textIsEncodedAsUtf8() {
        assertThat(Crc16.hex("\u00e3"))
                .isEqualTo(String.format("%04X", Crc16.checksum(new byte[] {(byte) 0xC3, (byte) 0xA3})));
    }

    @Test
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> Crc16.checksum(null));
        assertThatNullPointerException().isThrownBy(() -> Crc16.hex(null));
    }

    /** Every published example: the CRC of the text up to and including 6304. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("io.github.hitice.pix.brcode.OfficialExamples#all")
    void officialExamplesHaveTheirPublishedCrc(String name, String payload, String crc) {
        assertThat(payload).endsWith("6304" + crc);
        assertThat(Crc16.hex(payload.substring(0, payload.length() - 4))).isEqualTo(crc);
    }
}
