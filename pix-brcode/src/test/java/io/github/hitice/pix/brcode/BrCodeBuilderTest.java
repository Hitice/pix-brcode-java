package io.github.hitice.pix.brcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BrCodeBuilderTest {

    private static BrCode.StaticBuilder base() {
        return BrCode.staticPayment()
                .key(PixKey.phone("+5561912345678"))
                .merchantName("Fulano de Tal")
                .merchantCity("Brasilia");
    }

    @Nested
    class Amount {

        @ParameterizedTest
        @CsvSource({"12.34, 12.34", "12.3, 12.30", "10, 10.00", "0.01, 0.01", "12.3400, 12.34",
            "1E+3, 1000.00", "9999999999.99, 9999999999.99"})
        void writesTwoDecimals(String input, String written) {
            BrCode code = base().amount(new BigDecimal(input)).build();
            assertThat(code.payload()).contains("54" + String.format("%02d", written.length()) + written);
            assertThat(code.amount()).contains(new BigDecimal(written));
            assertThat(code.amount().orElseThrow().scale()).isEqualTo(2);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-1.00", "-0.01"})
        void rejectsZeroAndNegative(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> base().amount(new BigDecimal(input)))
                    .withMessageContaining("greater than zero");
        }

        @ParameterizedTest
        @ValueSource(strings = {"12.345", "0.001", "1.999"})
        void rejectsMoreThanTwoDecimals(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> base().amount(new BigDecimal(input)))
                    .withMessageContaining("2 decimal places");
        }

        @Test
        void rejectsMoreThan13Characters() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> base().amount(new BigDecimal("10000000000.00")))
                    .withMessageContaining("13 characters");
        }

        @Test
        void isOptional() {
            assertThat(base().build().payload()).doesNotContain("5802BR54").contains("53039865802BR");
        }
    }

    @Nested
    class MerchantText {

        @Test
        void removesDiacritics() {
            BrCode code = base().merchantName("Jos\u00e9 da Concei\u00e7\u00e3o").merchantCity("S\u00e3o Paulo").build();
            assertThat(code.merchantName()).isEqualTo("Jose da Conceicao");
            assertThat(code.merchantCity()).isEqualTo("Sao Paulo");
            assertThat(code.payload()).contains("5917Jose da Conceicao6009Sao Paulo").matches("\\p{ASCII}*");
        }

        @Test
        void removesDecomposedDiacriticsAndKeepsCase() {
            // "Sa" + combining tilde + "o", already in NFD form.
            assertThat(base().merchantCity("Sa\u0303o Paulo").build().merchantCity()).isEqualTo("Sao Paulo");
            assertThat(base().merchantCity("GOI\u00c2NIA").build().merchantCity()).isEqualTo("GOIANIA");
        }

        @Test
        void countsCharactersAfterNormalization() {
            String name = "\u00c1".repeat(25);
            assertThat(base().merchantName(name).build().merchantName()).isEqualTo("A".repeat(25));
        }

        @Test
        void nameIsLimitedTo25() {
            assertThat(base().merchantName("A".repeat(25)).build().merchantName()).hasSize(25);
            assertThatIllegalArgumentException().isThrownBy(() -> base().merchantName("A".repeat(26)))
                    .withMessageContaining("maximum is 25");
        }

        @Test
        void cityIsLimitedTo15() {
            assertThat(base().merchantCity("B".repeat(15)).build().merchantCity()).hasSize(15);
            assertThatIllegalArgumentException().isThrownBy(() -> base().merchantCity("B".repeat(16)))
                    .withMessageContaining("maximum is 15");
        }

        @ParameterizedTest
        @ValueSource(strings = {"Stra\u00dfe", "\u00c6sir", "Caf\u00e9 \u2615", "Tab\there"})
        void rejectsCharactersOutsideAscii(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> base().merchantName(input))
                    .withMessageContaining("printable ASCII");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\u0301"})
        void rejectsBlank(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> base().merchantCity(input))
                    .withMessageContaining("blank");
        }

        @Test
        void trimsSurroundingSpaces() {
            assertThat(base().merchantName("  Fulano  ").build().merchantName()).isEqualTo("Fulano");
        }
    }

    @Nested
    class Txid {

        @Test
        void writesTxid() {
            BrCode code = base().txid("PEDIDO123abc").build();
            assertThat(code.payload()).contains("62160512PEDIDO123abc");
            assertThat(code.txid()).contains("PEDIDO123abc");
        }

        @Test
        void defaultsToStars() {
            assertThat(base().build().payload()).contains("62070503***");
            assertThat(base().txid("ABC").txid("***").build().txid()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "PEDIDO-123", "pedido 123", "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "pedid\u00e3o"})
        void rejectsInvalid(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> base().txid(input))
                    .withMessageContaining("txid");
        }

        @Test
        void acceptsMaximumLength() {
            assertThat(base().txid("A".repeat(25)).build().txid()).contains("A".repeat(25));
        }
    }

    @Nested
    class Description {

        @Test
        void writesDescriptionInsideThePixTemplate() {
            BrCode code = base().description("Pedido 123").build();
            assertThat(code.payload()).contains("0114+55619123456780210Pedido 123");
            assertThat(code.description()).contains("Pedido 123");
        }

        @Test
        void sharesTheTemplateWithTheKey() {
            // GUI 18 + key (4 + 14) + description header 4 = 40, leaving 59 characters.
            assertThat(base().description("d".repeat(59)).build().payload()).contains("2699");
            assertThatIllegalStateException().isThrownBy(() -> base().description("d".repeat(60)).build())
                    .withMessageContaining("maximum is 99");
        }

        @Test
        void manualExampleKeyOfNineCharactersLeaves64() {
            // Section 2.6.1 of the Pix manual: a 9 character key leaves 64 characters of text.
            String nine = "a@b.co.br";
            assertThat(nine).hasSize(9);
            BrCode.StaticBuilder builder = BrCode.staticPayment().key(PixKey.email(nine))
                    .merchantName("X").merchantCity("Y");
            assertThat(builder.description("t".repeat(64)).build().description()).contains("t".repeat(64));
            assertThatIllegalStateException().isThrownBy(() -> builder.description("t".repeat(65)).build());
            // With the 12 characters of fss, 52 remain.
            builder.withdrawalFacilitator("12345678");
            assertThat(builder.description("t".repeat(52)).build().withdrawalFacilitator()).contains("12345678");
            assertThatIllegalStateException().isThrownBy(() -> builder.description("t".repeat(53)).build());
        }

        @Test
        void isLimitedTo72AndNormalized() {
            assertThatIllegalArgumentException().isThrownBy(() -> base().description("d".repeat(73)))
                    .withMessageContaining("maximum is 72");
            assertThat(base().description("Pe\u00e7a \u00fanica").build().description()).contains("Peca unica");
        }
    }

    @Nested
    class OtherFields {

        @ParameterizedTest
        @CsvSource({"70074900, 70074900", "70074-900, 70074900", " 01310-100 , 01310100"})
        void postalCode(String input, String written) {
            BrCode code = base().postalCode(input).build();
            assertThat(code.postalCode()).contains(written);
            assertThat(code.payload()).contains("6108" + written);
        }

        @ParameterizedTest
        @ValueSource(strings = {"7007490", "700749000", "70074_900", "ABCDE-FGH"})
        void rejectsInvalidPostalCode(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> base().postalCode(input));
        }

        @Test
        void merchantCategoryCode() {
            assertThat(base().merchantCategoryCode("5812").build().payload()).contains("52045812");
            assertThatIllegalArgumentException().isThrownBy(() -> base().merchantCategoryCode("581"));
            assertThatIllegalArgumentException().isThrownBy(() -> base().merchantCategoryCode("58A2"));
        }

        @Test
        void initiationMethod() {
            assertThat(base().build().initiationMethod()).isEmpty();
            assertThat(base().initiationMethod(BrCode.InitiationMethod.SINGLE_USE).build().payload())
                    .startsWith("000201010212");
            assertThat(base().initiationMethod(BrCode.InitiationMethod.REUSABLE).build().payload())
                    .startsWith("000201010211");
            assertThat(BrCode.InitiationMethod.REUSABLE.code()).isEqualTo("11");
        }

        @Test
        void withdrawalFacilitator() {
            BrCode code = base().withdrawalFacilitator("00000000").build();
            assertThat(code.payload()).contains("030800000000");
            assertThatIllegalArgumentException().isThrownBy(() -> base().withdrawalFacilitator("1234567"));
        }

        @Test
        void keyFromText() {
            assertThat(base().key("pix@BCB.gov.br").build().key().orElseThrow().value()).isEqualTo("pix@bcb.gov.br");
        }

        @Test
        void recurrenceLocation() {
            String url = "psp.example.com/rec/abc";
            assertThat(base().recurrenceLocation(" " + url + " ").build().recurrenceLocation()).contains(url);
            assertThatIllegalArgumentException().isThrownBy(() -> base().recurrenceLocation("https://" + url));
        }
    }

    @Nested
    class Dynamic {

        private BrCode.DynamicBuilder dynamic() {
            return BrCode.dynamicPayment().merchantName("Loja").merchantCity("Recife");
        }

        @Test
        void defaultsToSingleUse() {
            BrCode code = dynamic().location("psp.example.com/qr/v2/abc").build();
            assertThat(code.type()).isEqualTo(BrCode.Type.DYNAMIC);
            assertThat(code.initiationMethod()).contains(BrCode.InitiationMethod.SINGLE_USE);
            assertThat(code.amount()).isEmpty();
            assertThat(code.txid()).isEmpty();
            assertThat(code.key()).isEmpty();
            assertThat(code.description()).isEmpty();
            assertThat(code.withdrawalFacilitator()).isEmpty();
            assertThat(code.payload()).contains("62070503***");
        }

        @ParameterizedTest
        @ValueSource(strings = {"https://psp.example.com/qr", "http://psp.example.com/qr",
            "psp.example.com/a b", "", "psp.example.com/\u00e7"})
        void rejectsInvalidLocation(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> dynamic().location(input));
        }

        @Test
        void locationIsLimitedTo77() {
            String base = "psp.example.com/";
            String max = base + "x".repeat(77 - base.length());
            assertThat(dynamic().location(max).build().location()).contains(max);
            assertThatIllegalArgumentException().isThrownBy(() -> dynamic().location(max + "x"))
                    .withMessageContaining("maximum is 77");
        }

        @Test
        void requiresLocation() {
            assertThatIllegalStateException().isThrownBy(() -> dynamic().build())
                    .withMessageContaining("location");
        }
    }

    @Test
    void requiresMandatoryFields() {
        assertThatIllegalStateException()
                .isThrownBy(() -> BrCode.staticPayment().merchantName("A").merchantCity("B").build())
                .withMessageContaining("key");
        assertThatIllegalStateException()
                .isThrownBy(() -> BrCode.staticPayment().key(Payloads.EVP).merchantCity("B").build())
                .withMessageContaining("merchantName");
        assertThatIllegalStateException()
                .isThrownBy(() -> BrCode.staticPayment().key(Payloads.EVP).merchantName("A").build())
                .withMessageContaining("merchantCity");
    }

    @Test
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> base().key((PixKey) null));
        assertThatNullPointerException().isThrownBy(() -> base().merchantName(null));
        assertThatNullPointerException().isThrownBy(() -> base().merchantCity(null));
        assertThatNullPointerException().isThrownBy(() -> base().amount(null));
        assertThatNullPointerException().isThrownBy(() -> base().txid(null));
        assertThatNullPointerException().isThrownBy(() -> base().description(null));
        assertThatNullPointerException().isThrownBy(() -> base().postalCode(null));
        assertThatNullPointerException().isThrownBy(() -> base().merchantCategoryCode(null));
        assertThatNullPointerException().isThrownBy(() -> base().initiationMethod(null));
        assertThatNullPointerException().isThrownBy(() -> base().withdrawalFacilitator(null));
        assertThatNullPointerException().isThrownBy(() -> base().recurrenceLocation(null));
        assertThatNullPointerException().isThrownBy(() -> BrCode.dynamicPayment().location(null));
        assertThatNullPointerException().isThrownBy(() -> BrCode.parse(null));
        assertThatNullPointerException().isThrownBy(() -> BrCode.validate(null));
    }

    @Test
    void readmeQuickStartPayload() {
        BrCode code = BrCode.staticPayment()
                .key(PixKey.email("pix@example.com"))
                .merchantName("Padaria Sao Joao")
                .merchantCity("S\u00e3o Paulo")
                .amount(new BigDecimal("12.34"))
                .txid("PEDIDO123")
                .description("Pedido 123")
                .build();
        assertThat(code.payload()).isEqualTo("00020126510014br.gov.bcb.pix0115pix@example.com0210Pedido 123"
                + "520400005303986540512.345802BR5916Padaria Sao Joao6009Sao Paulo62130509PEDIDO1236304BD94");

        String edited = code.payload().replace("5916Padaria Sao Joao", "5926Padaria Sao Joao Ltda XYZ1");
        assertThat(BrCode.validate(edited).problems()).extracting(Problem::toString).containsExactly(
                "CRC mismatch: expected A5F3, found BD94 (at position 155)",
                "Merchant name (59) has 26 characters, the maximum is 25 (at position 93)");
    }

    @Test
    void equalityFollowsThePayload() {
        BrCode a = base().amount(new BigDecimal("1.00")).build();
        BrCode b = base().amount(new BigDecimal("1")).build();
        BrCode c = base().amount(new BigDecimal("2")).build();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c).isNotEqualTo("x");
        assertThat(a.toString()).isEqualTo(a.payload());
    }
}
