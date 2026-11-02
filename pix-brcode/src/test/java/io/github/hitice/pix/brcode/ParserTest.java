package io.github.hitice.pix.brcode;

import static io.github.hitice.pix.brcode.OfficialExamples.DYNAMIC_64E4;
import static io.github.hitice.pix.brcode.OfficialExamples.STATIC_1D3D;
import static io.github.hitice.pix.brcode.Payloads.EVP;
import static io.github.hitice.pix.brcode.Payloads.commonFields;
import static io.github.hitice.pix.brcode.Payloads.pixAccount;
import static io.github.hitice.pix.brcode.Payloads.staticWith;
import static io.github.hitice.pix.brcode.Payloads.tlv;
import static io.github.hitice.pix.brcode.Payloads.withCrc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ParserTest {

    /** Body of the static example without the CRC field. */
    private static final String STATIC_BODY = STATIC_1D3D.substring(0, STATIC_1D3D.length() - 8);

    private static List<Problem> problems(String payload) {
        return BrCode.validate(payload).problems();
    }

    private static ErrorCode firstCode(String payload) {
        return problems(payload).get(0).code();
    }

    /** Replaces text in the static example and fixes the CRC, so only the change is wrong. */
    private static String staticExampleWith(String from, String to) {
        assertThat(STATIC_BODY).contains(from);
        return withCrc(STATIC_BODY.replace(from, to));
    }

    @Nested
    class Crc {

        @Test
        void wrongCrcIsReportedWithItsPosition() {
            String payload = STATIC_1D3D.replace("1D3D", "1D3E");
            assertThatThrownBy(() -> BrCode.parse(payload))
                    .isInstanceOfSatisfying(BrCodeException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.CRC_MISMATCH);
                        assertThat(e.position()).isEqualTo(payload.length() - 4);
                        assertThat(e.problem().message()).isEqualTo("CRC mismatch: expected 1D3D, found 1D3E");
                        assertThat(e).hasMessage("CRC mismatch: expected 1D3D, found 1D3E (at position 133)");
                    });
        }

        @Test
        void changedDataIsDetected() {
            String payload = STATIC_1D3D.replace("Fulano", "Fulana");
            assertThat(problems(payload)).extracting(Problem::code).containsExactly(ErrorCode.CRC_MISMATCH);
        }

        @Test
        void crcMustBeUpperCaseHex() {
            String payload = STATIC_1D3D.replace("1D3D", "1d3d");
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.INVALID_VALUE);
                assertThat(p.message()).contains("upper case");
            });
        }

        @Test
        void crcMustBeLast() {
            String payload = withCrc(STATIC_BODY) + "0001X";
            assertThat(firstCode(payload)).isEqualTo(ErrorCode.MISPLACED_FIELD);
        }

        @Test
        void crcIsMandatory() {
            assertThat(problems(STATIC_BODY)).extracting(Problem::code).containsExactly(ErrorCode.MISSING_FIELD);
        }

        @Test
        void crcLengthMustBe4() {
            String payload = STATIC_BODY + "63031D3";
            assertThat(firstCode(payload)).isEqualTo(ErrorCode.INVALID_LENGTH);
        }
    }

    @Nested
    class Structure {

        @Test
        void emptyPayload() {
            assertThat(problems("")).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.TRUNCATED);
                assertThat(p.position()).isZero();
            });
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 3, 20, 60, 100, 136})
        void truncatedPayload(int length) {
            String payload = STATIC_1D3D.substring(0, length);
            ValidationResult result = BrCode.validate(payload);
            assertThat(result.isValid()).isFalse();
            assertThat(result.problems().get(0).code()).isIn(ErrorCode.TRUNCATED, ErrorCode.MISSING_FIELD);
        }

        @Test
        void declaredLengthPastTheEnd() {
            String payload = "000201" + "5930Fulano";
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.TRUNCATED);
                assertThat(p.position()).isEqualTo(8);
                assertThat(p.message()).contains("declares 30 characters but only 6 remain");
            });
        }

        @Test
        void lengthOverflowShiftsEveryField() {
            // Name length 13 changed to 14: the city ID is read as part of the name.
            String payload = STATIC_1D3D.replace("5913Fulano", "5914Fulano");
            assertThat(BrCode.validate(payload).isValid()).isFalse();
        }

        @Test
        void truncationStillChecksTheCrcWhenTheTailIsPresent() {
            String payload = STATIC_1D3D.replace("5913Fulano de Tal", "5999Fulano de Tal");
            assertThat(problems(payload)).extracting(Problem::code)
                    .containsExactly(ErrorCode.TRUNCATED, ErrorCode.CRC_MISMATCH);
        }

        @Test
        void nonNumericId() {
            assertThat(problems("AB0201")).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.MALFORMED_TLV);
                assertThat(p.position()).isZero();
            });
        }

        @Test
        void nonNumericLength() {
            assertThat(problems("00X201")).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.MALFORMED_TLV);
                assertThat(p.position()).isEqualTo(2);
            });
        }

        @Test
        void zeroLength() {
            assertThat(firstCode(staticExampleWith("52040000", "5200"))).isEqualTo(ErrorCode.INVALID_LENGTH);
        }

        @Test
        void duplicateId() {
            String payload = staticExampleWith("6008BRASILIA", "6008BRASILIA6008BRASILIA");
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.DUPLICATE_ID);
                assertThat(payload.substring(p.position())).startsWith("6008BRASILIA62");
            });
        }

        @Test
        void reservedIdIsUnknown() {
            String payload = staticExampleWith("6008BRASILIA", "6008BRASILIA7003abc");
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.UNKNOWN_ID);
                assertThat(payload.substring(p.position())).startsWith("7003abc");
            });
        }

        @Test
        void payloadFormatIndicatorMustComeFirst() {
            String body = STATIC_BODY.substring(6);
            String payload = withCrc(body.replace("5204", "0002015204"));
            assertThat(firstCode(payload)).isEqualTo(ErrorCode.MISPLACED_FIELD);
            assertThat(firstCode(withCrc(body))).isEqualTo(ErrorCode.MISSING_FIELD);
            assertThat(firstCode(staticExampleWith("000201", "000202"))).isEqualTo(ErrorCode.INVALID_VALUE);
        }

        @Test
        void problemsAreReportedTogether() {
            String payload = staticExampleWith("5802BR5913Fulano de Tal6008BRASILIA",
                    "5802US5926" + "N".repeat(26) + "6016" + "C".repeat(16));
            assertThat(problems(payload)).extracting(Problem::code).containsExactly(
                    ErrorCode.INVALID_VALUE, ErrorCode.INVALID_LENGTH, ErrorCode.INVALID_LENGTH);
        }
    }

    @Nested
    class MandatoryFields {

        @ParameterizedTest
        @CsvSource({"52040000, merchant category code", "5303986, transaction currency",
            "5802BR, country code", "5913Fulano de Tal, merchant name", "6008BRASILIA, merchant city",
            "62070503***, additional data field template"})
        void missingField(String field, String name) {
            String payload = staticExampleWith(field, "");
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.MISSING_FIELD);
                assertThat(p.message()).containsIgnoringCase(name);
            });
        }

        @Test
        void txidIsMandatoryInTemplate62() {
            String payload = staticExampleWith("62070503***", "62070103***");
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.MISSING_FIELD);
                assertThat(p.message()).contains("(05)");
            });
        }

        @Test
        void missingGuiInTemplate26() {
            String payload = staticWith(tlv("01", EVP), commonFields());
            assertThat(problems(payload)).extracting(Problem::code)
                    .containsExactly(ErrorCode.NOT_PIX, ErrorCode.MISSING_FIELD);
        }

        @Test
        void noMerchantAccountAtAll() {
            String payload = withCrc(tlv("00", "01") + commonFields());
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.NOT_PIX);
                assertThat(p.message()).contains("02 to 51");
            });
        }

        @Test
        void otherArrangementOnly() {
            String payload = staticWith(tlv("00", "br.com.outro") + tlv("01", "123"), commonFields());
            assertThat(problems(payload)).extracting(Problem::code).containsExactly(ErrorCode.NOT_PIX);
        }

        @Test
        void pixTemplateNeedsKeyOrLocation() {
            String payload = staticWith(tlv("00", "br.gov.bcb.pix"), commonFields());
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.MISSING_FIELD);
                assertThat(p.message()).contains("key (01) or a location (25)");
            });
        }
    }

    @Nested
    class FieldValues {

        @Test
        void countryMustBeBrazil() {
            String payload = staticExampleWith("5802BR", "5802AR");
            assertThatThrownBy(() -> BrCode.parse(payload))
                    .isInstanceOfSatisfying(BrCodeException.class, e -> {
                        assertThat(e.code()).isEqualTo(ErrorCode.INVALID_VALUE);
                        assertThat(e.getMessage()).contains("Country code (58) must be \"BR\"");
                        assertThat(payload.substring(e.position())).startsWith("AR");
                    });
        }

        @Test
        void merchantNameOver25() {
            String payload = staticExampleWith("5913Fulano de Tal", "5926" + "N".repeat(26));
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.INVALID_LENGTH);
                assertThat(p.message()).contains("Merchant name (59) has 26 characters, the maximum is 25");
            });
        }

        @Test
        void merchantCityOver15() {
            String payload = staticExampleWith("6008BRASILIA", "6016" + "C".repeat(16));
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.INVALID_LENGTH);
                assertThat(p.message()).contains("Merchant city (60)");
            });
        }

        @Test
        void accentedNameIsRejected() {
            String payload = staticExampleWith("6008BRASILIA", "6009S\u00e3o Paulo");
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.INVALID_CHARACTER);
                assertThat(payload.substring(p.position())).startsWith("\u00e3o");
            });
        }

        @Test
        void currencyMustBeReal() {
            assertThat(firstCode(staticExampleWith("5303986", "5303840"))).isEqualTo(ErrorCode.INVALID_VALUE);
        }

        @Test
        void merchantCategoryCodeMustBeFourDigits() {
            assertThat(firstCode(staticExampleWith("52040000", "5204ABCD"))).isEqualTo(ErrorCode.INVALID_VALUE);
        }

        @Test
        void pointOfInitiationMethod() {
            assertThat(BrCode.parse(staticExampleWith("000201", "000201010211")).initiationMethod())
                    .contains(BrCode.InitiationMethod.REUSABLE);
            assertThat(firstCode(staticExampleWith("000201", "000201010213"))).isEqualTo(ErrorCode.INVALID_VALUE);
        }

        @Test
        void postalCode() {
            assertThat(BrCode.parse(staticExampleWith("62070503***", "610870074900" + "62070503***"))
                    .postalCode()).contains("70074900");
            assertThat(firstCode(staticExampleWith("62070503***", "6111" + "1".repeat(11) + "62070503***")))
                    .isEqualTo(ErrorCode.INVALID_LENGTH);
        }

        @Test
        void tipIndicator() {
            assertThat(BrCode.validate(staticExampleWith("5802BR", "550201" + "5802BR")).isValid()).isTrue();
            assertThat(firstCode(staticExampleWith("5802BR", "550209" + "5802BR"))).isEqualTo(ErrorCode.INVALID_VALUE);
        }

        @Test
        void languageTemplateIsAcceptedWhenWellFormed() {
            String language = tlv("64", tlv("00", "PT") + tlv("01", "Fulano de Tal"));
            assertThat(BrCode.validate(staticExampleWith("62070503***", "62070503***" + language)).isValid())
                    .isTrue();
            assertThat(firstCode(staticExampleWith("62070503***", "62070503***" + "6404ABCD")))
                    .isEqualTo(ErrorCode.MALFORMED_TLV);
        }
    }

    @Nested
    class Amount {

        @ParameterizedTest
        @CsvSource({"98.73, 98.73", "98, 98.00", "98., 98.00", "0.5, 0.50", "9999999999.99, 9999999999.99"})
        void readsEmvFormats(String written, String amount) {
            String payload = staticExampleWith("5802BR", tlv("54", written) + "5802BR");
            assertThat(BrCode.parse(payload).amount()).contains(new BigDecimal(amount));
        }

        @ParameterizedTest
        @ValueSource(strings = {"98,73", "3 705", "-1.00", "1.234", "0", "0.00", ".5", "1e3"})
        void rejectsInvalid(String written) {
            String payload = staticExampleWith("5802BR", tlv("54", written) + "5802BR");
            assertThat(firstCode(payload)).isEqualTo(ErrorCode.INVALID_AMOUNT);
        }

        @Test
        void rejectsMoreThan13Characters() {
            String payload = staticExampleWith("5802BR", tlv("54", "12345678901.00") + "5802BR");
            assertThat(firstCode(payload)).isEqualTo(ErrorCode.INVALID_LENGTH);
        }
    }

    @Nested
    class PixTemplate {

        @Test
        void invalidCpfKey() {
            String payload = staticWith(pixAccount("12345678900"), commonFields());
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.INVALID_KEY);
                assertThat(payload.substring(p.position())).startsWith("12345678900");
                assertThat(p.message()).contains("check digits");
            });
        }

        @ParameterizedTest
        @ValueSource(strings = {"Pix@bcb.gov.br", "123.456.789-09", "5561912345678", "abc",
            "123E4567-E12B-12D1-A456-426655440000"})
        void keysMustBeCanonical(String key) {
            assertThat(firstCode(staticWith(pixAccount(key), commonFields()))).isEqualTo(ErrorCode.INVALID_KEY);
        }

        @Test
        void allKeyTypesAreRead() {
            for (String key : List.of("52998224725", "12ABC34501DE35", "pix@bcb.gov.br", "+5561912345678", EVP)) {
                assertThat(BrCode.parse(staticWith(pixAccount(key), commonFields())).key().orElseThrow().value())
                        .isEqualTo(key);
            }
        }

        @Test
        void guiIsCaseInsensitive() {
            String account = tlv("00", "BR.GOV.BCB.PIX") + tlv("01", EVP);
            assertThat(BrCode.parse(staticWith(account, commonFields())).type()).isEqualTo(BrCode.Type.STATIC);
        }

        @Test
        void pixTemplateMayUseAnyIdFrom26To51() {
            String payload = withCrc(tlv("00", "01") + tlv("27", tlv("00", "br.com.outro") + tlv("01", "x"))
                    + tlv("51", pixAccount(EVP)) + commonFields());
            assertThat(BrCode.parse(payload).key()).contains(PixKey.evp(EVP));
        }

        @Test
        void onlyOnePixTemplate() {
            String payload = withCrc(tlv("00", "01") + tlv("26", pixAccount(EVP)) + tlv("27", pixAccount(EVP))
                    + commonFields());
            assertThat(firstCode(payload)).isEqualTo(ErrorCode.CONFLICTING_FIELDS);
        }

        @Test
        void keyAndLocationTogether() {
            String account = pixAccount(EVP) + tlv("25", "psp.example.com/qr/1");
            assertThat(firstCode(staticWith(account, commonFields()))).isEqualTo(ErrorCode.CONFLICTING_FIELDS);
        }

        @Test
        void descriptionAndFacilitatorOnlyInStaticCodes() {
            String location = tlv("00", "br.gov.bcb.pix") + tlv("25", "psp.example.com/qr/1");
            assertThat(firstCode(staticWith(location + tlv("02", "desc"), commonFields())))
                    .isEqualTo(ErrorCode.CONFLICTING_FIELDS);
            assertThat(firstCode(staticWith(location + tlv("03", "12345678"), commonFields())))
                    .isEqualTo(ErrorCode.CONFLICTING_FIELDS);
        }

        @Test
        void descriptionAndFacilitatorAreRead() {
            BrCode code = BrCode.parse(staticWith(pixAccount(EVP) + tlv("02", "P\u00e3o de queijo")
                    + tlv("03", "12345678"), commonFields()));
            assertThat(code.description()).contains("P\u00e3o de queijo");
            assertThat(code.withdrawalFacilitator()).contains("12345678");
        }

        @Test
        void invalidDescriptionAndFacilitator() {
            assertThat(firstCode(staticWith(pixAccount(EVP) + tlv("02", "a\nb"), commonFields())))
                    .isEqualTo(ErrorCode.INVALID_CHARACTER);
            assertThat(firstCode(staticWith(pixAccount(EVP) + tlv("03", "1234567A"), commonFields())))
                    .isEqualTo(ErrorCode.INVALID_VALUE);
        }

        @Test
        void unknownAndDuplicateSubfields() {
            assertThat(firstCode(staticWith(pixAccount(EVP) + tlv("04", "x"), commonFields())))
                    .isEqualTo(ErrorCode.UNKNOWN_ID);
            assertThat(firstCode(staticWith(pixAccount(EVP) + tlv("01", EVP), commonFields())))
                    .isEqualTo(ErrorCode.DUPLICATE_ID);
            assertThat(firstCode(staticWith(tlv("00", "br.gov.bcb.pix") + tlv("00", "x") + tlv("01", EVP),
                    commonFields()))).isEqualTo(ErrorCode.DUPLICATE_ID);
        }

        @Test
        void malformedTemplate() {
            assertThat(problems(staticWith("0014br.gov.bcb.pix01", commonFields())))
                    .extracting(Problem::code).containsExactly(ErrorCode.NOT_PIX, ErrorCode.TRUNCATED);
        }

        @Test
        void locationRules() {
            String https = tlv("00", "br.gov.bcb.pix") + tlv("25", "https://psp.example.com/qr/1");
            assertThat(problems(staticWith(https, commonFields()))).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.INVALID_VALUE);
                assertThat(p.message()).contains("scheme");
            });
            String spaces = tlv("00", "br.gov.bcb.pix") + tlv("25", "psp.example.com/q r");
            assertThat(firstCode(staticWith(spaces, commonFields()))).isEqualTo(ErrorCode.INVALID_VALUE);
        }
    }

    @Nested
    class Txid {

        @Test
        void staticTxidAllowsOnlyAlphanumerics() {
            String payload = staticExampleWith("62070503***", tlv("62", tlv("05", "ABC-123")));
            assertThat(problems(payload)).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo(ErrorCode.INVALID_TXID);
                assertThat(payload.substring(p.position())).startsWith("ABC-123");
            });
        }

        @Test
        void staticTxidIsRead() {
            String payload = staticExampleWith("62070503***", tlv("62", tlv("05", "Pedido42") + tlv("50", "x")));
            assertThat(BrCode.parse(payload).txid()).contains("Pedido42");
        }

        @Test
        void dynamicTxidIsAnyPrintableText() {
            String body = DYNAMIC_64E4.substring(0, DYNAMIC_64E4.length() - 8);
            String payload = withCrc(body.replace("62070503***", tlv("62", tlv("05", "abc-123.x"))));
            assertThat(BrCode.parse(payload).txid()).contains("abc-123.x");
            String accented = withCrc(body.replace("62070503***", tlv("62", tlv("05", "a\u00e7"))));
            assertThat(firstCode(accented)).isEqualTo(ErrorCode.INVALID_CHARACTER);
        }

        @Test
        void txidOver25() {
            String payload = staticExampleWith("62070503***", tlv("62", tlv("05", "A".repeat(26))));
            assertThat(firstCode(payload)).isEqualTo(ErrorCode.INVALID_LENGTH);
        }

        @Test
        void duplicateAndMalformedTemplate62() {
            assertThat(firstCode(staticExampleWith("62070503***", tlv("62", "0503***0503***"))))
                    .isEqualTo(ErrorCode.DUPLICATE_ID);
            assertThat(firstCode(staticExampleWith("62070503***", tlv("62", "05"))))
                    .isEqualTo(ErrorCode.TRUNCATED);
        }
    }

    @Nested
    class Recurrence {

        private static final String REC = "psp.example.com/rec/1";

        private String withTemplate80(String content) {
            return staticExampleWith("62070503***", "62070503***" + tlv("80", content));
        }

        @Test
        void foreignUnreservedTemplatesAreIgnored() {
            BrCode code = BrCode.parse(withTemplate80(tlv("00", "br.com.outro") + tlv("01", "x")));
            assertThat(code.recurrenceLocation()).isEmpty();
        }

        @Test
        void rules() {
            assertThat(firstCode(withTemplate80(tlv("01", "x")))).isEqualTo(ErrorCode.MISSING_FIELD);
            assertThat(firstCode(withTemplate80(tlv("00", "br.gov.bcb.pix")))).isEqualTo(ErrorCode.MISSING_FIELD);
            assertThat(firstCode(withTemplate80(tlv("00", "br.gov.bcb.pix") + tlv("25", "https://" + REC))))
                    .isEqualTo(ErrorCode.INVALID_VALUE);
            assertThat(firstCode(withTemplate80(tlv("00", "br.gov.bcb.pix") + tlv("25", REC) + tlv("26", "x"))))
                    .isEqualTo(ErrorCode.UNKNOWN_ID);
            assertThat(firstCode(withTemplate80("0014br.gov.bcb.pix25"))).isEqualTo(ErrorCode.TRUNCATED);
            String pix = tlv("00", "br.gov.bcb.pix") + tlv("25", REC);
            String twice = staticExampleWith("62070503***", "62070503***" + tlv("80", pix) + tlv("81", pix));
            assertThat(firstCode(twice)).isEqualTo(ErrorCode.CONFLICTING_FIELDS);
        }

        @Test
        void invalidRecurrenceLocationIsTheOnlyProblem() {
            String payload = withCrc(tlv("00", "01") + tlv("26", tlv("00", "br.gov.bcb.pix")) + commonFields()
                    + tlv("80", tlv("00", "br.gov.bcb.pix") + tlv("25", "https://x")));
            assertThat(problems(payload)).extracting(Problem::code).containsExactly(ErrorCode.INVALID_VALUE);
        }
    }

    @Test
    void parseThrowsTheFirstProblemAndValidateListsAll() {
        String payload = staticExampleWith("5802BR", "5802AR").replace("Fulano", "Fulana");
        ValidationResult result = BrCode.validate(payload);
        assertThat(result.problems()).extracting(Problem::code)
                .containsExactly(ErrorCode.CRC_MISMATCH, ErrorCode.INVALID_VALUE);
        assertThatThrownBy(() -> BrCode.parse(payload)).isInstanceOf(BrCodeException.class)
                .hasMessageStartingWith("CRC mismatch");
        assertThat(result.toString()).startsWith("ValidationResult[");
        assertThat(result).isEqualTo(BrCode.validate(payload)).hasSameHashCodeAs(BrCode.validate(payload));
    }

    @Test
    void validResult() {
        ValidationResult result = BrCode.validate(STATIC_1D3D);
        assertThat(result.isValid()).isTrue();
        assertThat(result.problems()).isEmpty();
        assertThat(result.brCode()).contains(BrCode.parse(STATIC_1D3D));
        assertThat(result.toString()).isEqualTo("ValidationResult[valid]");
    }

    @Test
    void problemRejectsNegativePosition() {
        assertThatThrownBy(() -> new Problem(ErrorCode.TRUNCATED, -1, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Problem(null, 0, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BrCodeException(null)).isInstanceOf(NullPointerException.class);
    }
}
