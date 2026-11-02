package io.github.hitice.pix.brcode;

import static io.github.hitice.pix.brcode.OfficialExamples.BR_CODE_AD38;
import static io.github.hitice.pix.brcode.OfficialExamples.DYNAMIC_64E4;
import static io.github.hitice.pix.brcode.OfficialExamples.DYNAMIC_RECURRENCE_FB42;
import static io.github.hitice.pix.brcode.OfficialExamples.RECURRENCE_F2DA;
import static io.github.hitice.pix.brcode.OfficialExamples.STATIC_1D3D;
import static io.github.hitice.pix.brcode.OfficialExamples.STATIC_RECURRENCE_2875;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The library reads every published example and writes the Pix ones byte for byte. */
class OfficialExamplesTest {

    private static final String RECURRENCE = "pix.example.com/rec/2353c790eefb11eaadc10242ac120002";
    private static final String LOCATION = "pix.example.com/8b3da2f39a4140d1a91abd93113bd441";

    @Test
    void parsesTheStaticExample() {
        BrCode code = BrCode.parse(STATIC_1D3D);

        assertThat(code.type()).isEqualTo(BrCode.Type.STATIC);
        assertThat(code.key()).contains(new PixKey(PixKey.Type.EVP, Payloads.EVP));
        assertThat(code.merchantName()).isEqualTo("Fulano de Tal");
        assertThat(code.merchantCity()).isEqualTo("BRASILIA");
        assertThat(code.merchantCategoryCode()).isEqualTo("0000");
        assertThat(code.amount()).isEmpty();
        assertThat(code.txid()).isEmpty();
        assertThat(code.description()).isEmpty();
        assertThat(code.initiationMethod()).isEmpty();
        assertThat(code.location()).isEmpty();
        assertThat(code.recurrenceLocation()).isEmpty();
        assertThat(code.postalCode()).isEmpty();
        assertThat(code.withdrawalFacilitator()).isEmpty();
        assertThat(code.crc()).isEqualTo("1D3D");
        assertThat(code.payload()).isEqualTo(STATIC_1D3D);
    }

    @Test
    void buildsTheStaticExample() {
        BrCode code = BrCode.staticPayment()
                .key(PixKey.evp(Payloads.EVP))
                .merchantName("Fulano de Tal")
                .merchantCity("BRASILIA")
                .build();

        assertThat(code.payload()).isEqualTo(STATIC_1D3D);
        assertThat(code).isEqualTo(BrCode.parse(STATIC_1D3D));
    }

    @Test
    void parsesTheDynamicExample() {
        BrCode code = BrCode.parse(DYNAMIC_64E4);

        assertThat(code.type()).isEqualTo(BrCode.Type.DYNAMIC);
        assertThat(code.location()).contains(LOCATION);
        assertThat(code.key()).isEmpty();
        assertThat(code.initiationMethod()).contains(BrCode.InitiationMethod.SINGLE_USE);
        assertThat(code.crc()).isEqualTo("64E4");
    }

    @Test
    void buildsTheDynamicExample() {
        BrCode code = BrCode.dynamicPayment()
                .location(LOCATION)
                .merchantName("Fulano de Tal")
                .merchantCity("BRASILIA")
                .build();

        assertThat(code.payload()).isEqualTo(DYNAMIC_64E4);
    }

    @Test
    void parsesTheRecurrenceOnlyExample() {
        BrCode code = BrCode.parse(RECURRENCE_F2DA);

        assertThat(code.type()).isEqualTo(BrCode.Type.RECURRENCE_ONLY);
        assertThat(code.key()).isEmpty();
        assertThat(code.location()).isEmpty();
        assertThat(code.recurrenceLocation()).contains(RECURRENCE);
    }

    @Test
    void buildsTheStaticCompositeExample() {
        BrCode code = BrCode.staticPayment()
                .key(Payloads.EVP)
                .merchantName("Fulano de Tal")
                .merchantCity("BRASILIA")
                .amount(new BigDecimal("100.5"))
                .recurrenceLocation(RECURRENCE)
                .build();

        assertThat(code.payload()).isEqualTo(STATIC_RECURRENCE_2875);
        BrCode parsed = BrCode.parse(STATIC_RECURRENCE_2875);
        assertThat(parsed.amount()).contains(new BigDecimal("100.50"));
        assertThat(parsed.recurrenceLocation()).contains(RECURRENCE);
    }

    @Test
    void buildsTheDynamicCompositeExample() {
        BrCode code = BrCode.dynamicPayment()
                .location(LOCATION)
                .merchantName("Fulano de Tal")
                .merchantCity("BRASILIA")
                .recurrenceLocation(RECURRENCE)
                .build();

        assertThat(code.payload()).isEqualTo(DYNAMIC_RECURRENCE_FB42);
        assertThat(BrCode.parse(DYNAMIC_RECURRENCE_FB42).type()).isEqualTo(BrCode.Type.DYNAMIC);
    }

    /**
     * The BR Code manual example mixes a card scheme, Pix and another arrangement. Its transaction
     * ID has a hyphen, which the Pix manual (section 2.6.2) does not allow in a static code, so that
     * is the only problem reported; the foreign templates and the upper case GUI are accepted.
     */
    @Test
    void brCodeManualExampleOnlyBreaksThePixTxidRule() {
        ValidationResult result = BrCode.validate(BR_CODE_AD38);

        assertThat(result.isValid()).isFalse();
        assertThat(result.brCode()).isEmpty();
        assertThat(result.problems()).singleElement().satisfies(p -> {
            assertThat(p.code()).isEqualTo(ErrorCode.INVALID_TXID);
            assertThat(BR_CODE_AD38.substring(p.position())).startsWith("RP12345678-2019");
        });
    }

    @Test
    void brCodeManualExampleIsReadWhenTheTxidFollowsThePixRule() {
        String body = BR_CODE_AD38.substring(0, BR_CODE_AD38.length() - 8)
                .replace("RP12345678-2019", "RP12345678x2019");
        BrCode code = BrCode.parse(Payloads.withCrc(body));

        assertThat(code.key()).contains(PixKey.evp(Payloads.EVP));
        assertThat(code.amount()).contains(new BigDecimal("123.45"));
        assertThat(code.postalCode()).contains("70074900");
        assertThat(code.txid()).contains("RP12345678x2019");
        assertThat(code.merchantName()).isEqualTo("NOME DO RECEBEDOR");
        assertThat(code.recurrenceLocation()).isEmpty();
    }
}
