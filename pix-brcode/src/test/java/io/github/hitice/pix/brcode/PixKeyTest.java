package io.github.hitice.pix.brcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PixKeyTest {

    @Nested
    class Cpf {

        @ParameterizedTest
        @ValueSource(strings = {"12345678909", "52998224725", "123.456.789-09", " 529.982.247-25 "})
        void acceptsValidCpf(String input) {
            PixKey key = PixKey.cpf(input);
            assertThat(key.type()).isEqualTo(PixKey.Type.CPF);
            assertThat(key.value()).matches("\\d{11}");
        }

        @Test
        void manualFormatExampleHasWrongCheckDigits() {
            // The Pix manual (2.5.1) shows 12345678900 only to illustrate the format.
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.cpf("12345678900"))
                    .withMessageContaining("check digits should be 09");
        }

        @ParameterizedTest
        @ValueSource(strings = {"11111111111", "00000000000"})
        void rejectsRepeatedDigits(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.cpf(input))
                    .withMessageContaining("all digits are equal");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1234567890", "123456789012", "1234567890A", "", "52998224724"})
        void rejectsMalformedOrWrongDigits(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.cpf(input))
                    .withMessageContaining("CPF");
        }
    }

    @Nested
    class Cnpj {

        /** Test vectors of the official Serpro routine for the alphanumeric CNPJ. */
        @ParameterizedTest
        @CsvSource({
            "12ABC34501DE35, true",
            "1345C3A5000106, true",
            "R55231B3000700, false",
            "90.021.382/0001-22, true",
            "90.024.778/000123, true",
            "90.025.108/000101, false",
            "90.025.255/0001, false",
            "90.024.420/0001A2, false"
        })
        void serproVectors(String input, boolean valid) {
            if (valid) {
                assertThat(PixKey.cnpj(input).type()).isEqualTo(PixKey.Type.CNPJ);
            } else {
                assertThatIllegalArgumentException().isThrownBy(() -> PixKey.cnpj(input));
            }
        }

        @Test
        void receitaFederalExample() {
            // FAQ "CNPJ alfanumerico", question 14: 12.ABC.345/01DE-35.
            assertThat(PixKey.cnpj("12.ABC.345/01DE-35").value()).isEqualTo("12ABC34501DE35");
        }

        @Test
        void pixManualExamplesAreValid() {
            assertThat(PixKey.of("00038166000105").type()).isEqualTo(PixKey.Type.CNPJ);
            assertThat(PixKey.of("12ABC34501DE35").type()).isEqualTo(PixKey.Type.CNPJ);
        }

        @Test
        void factoryConvertsToUpperCaseButCanonicalFormDoesNot() {
            assertThat(PixKey.cnpj("1345c3a5000106").value()).isEqualTo("1345C3A5000106");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PixKey(PixKey.Type.CNPJ, "1345c3A5000106"));
        }

        @Test
        void rejectsAllZeros() {
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.cnpj("00000000000000"))
                    .withMessageContaining("zero");
        }

        @Test
        void reportsExpectedCheckDigits() {
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.cnpj("12ABC34501DE36"))
                    .withMessageContaining("check digits should be 35");
        }
    }

    @Nested
    class Email {

        @Test
        void normalizesToLowerCase() {
            assertThat(PixKey.email("  Fulano_da_Silva.Recebedor@Example.COM ").value())
                    .isEqualTo("fulano_da_silva.recebedor@example.com");
        }

        @ParameterizedTest
        @ValueSource(strings = {"plainaddress", "a@", "@example.com", "a b@example.com",
            "a%b@example.com", "a@-example.com", "jos\u00e9@example.com"})
        void rejectsInvalid(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.email(input));
        }

        @Test
        void maximumLengthIs77() {
            String local = "a".repeat(77 - "@example.com".length());
            assertThat(PixKey.email(local + "@example.com").value()).hasSize(77);
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.email("b" + local + "@example.com"))
                    .withMessageContaining("maximum is 77");
        }

        @Test
        void canonicalFormMustBeLowerCase() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new PixKey(PixKey.Type.EMAIL, "Pix@bcb.gov.br"))
                    .withMessageContaining("lower case");
        }
    }

    @Nested
    class Phone {

        @ParameterizedTest
        @ValueSource(strings = {"+5561912345678", "+55 (61) 91234-5678", "61912345678",
            "(61) 91234-5678", "5561912345678", "+55.61.91234.5678"})
        void acceptsBrazilianMobile(String input) {
            assertThat(PixKey.phone(input).value()).isEqualTo("+5561912345678");
        }

        @ParameterizedTest
        @ValueSource(strings = {"+556112345678", "+5561812345678", "+14155552671", "+5501912345678",
            "6112345678", "+55619123456789", "phone"})
        void rejectsOthers(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.phone(input))
                    .withMessageContaining("phone");
        }
    }

    @Nested
    class Evp {

        @Test
        void acceptsUuidInAnyCase() {
            assertThat(PixKey.evp("123E4567-E12B-12D1-A456-426655440000").value()).isEqualTo(Payloads.EVP);
            UUID uuid = UUID.randomUUID();
            assertThat(PixKey.evp(uuid).value()).isEqualTo(uuid.toString());
        }

        @ParameterizedTest
        @ValueSource(strings = {"123e4567e12b12d1a456426655440000", "123e4567-e12b-12d1-a456-42665544000",
            "g23e4567-e12b-12d1-a456-426655440000"})
        void rejectsNonUuid(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.evp(input))
                    .withMessageContaining("random key");
        }
    }

    @Nested
    class Detection {

        @ParameterizedTest
        @CsvSource({
            "529.982.247-25, CPF, 52998224725",
            "12.ABC.345/01DE-35, CNPJ, 12ABC34501DE35",
            "00038166000105, CNPJ, 00038166000105",
            "Pix@BCB.gov.br, EMAIL, pix@bcb.gov.br",
            "+55 61 91234-5678, PHONE, +5561912345678",
            "123E4567-E12B-12D1-A456-426655440000, EVP, 123e4567-e12b-12d1-a456-426655440000"
        })
        void detectsType(String input, PixKey.Type type, String value) {
            assertThat(PixKey.of(input)).isEqualTo(new PixKey(type, value));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "hello", "123", "1234567890123"})
        void rejectsUnknown(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.of(input))
                    .withMessageContaining("Not a Pix key");
        }

        @Test
        void elevenDigitsAreReadAsCpf() {
            // A national phone number without +55 is ambiguous; it is checked as a CPF.
            assertThatIllegalArgumentException().isThrownBy(() -> PixKey.of("61912345678"))
                    .withMessageContaining("CPF");
        }
    }

    @Test
    void canonicalParsingDoesNotNormalize() {
        String[] reason = new String[1];
        assertThat(PixKey.parseCanonical("PIX@bcb.gov.br", reason)).isNull();
        assertThat(reason[0]).contains("lower case");
        assertThat(PixKey.parseCanonical("123.456.789-09", reason)).isNull();
        assertThat(reason[0]).contains("Not a Pix key");
        assertThat(PixKey.parseCanonical("+5561912345678", reason)).isEqualTo(PixKey.phone("+5561912345678"));
    }

    @Test
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> PixKey.of(null));
        assertThatNullPointerException().isThrownBy(() -> PixKey.cpf(null));
        assertThatNullPointerException().isThrownBy(() -> PixKey.cnpj(null));
        assertThatNullPointerException().isThrownBy(() -> PixKey.email(null));
        assertThatNullPointerException().isThrownBy(() -> PixKey.phone(null));
        assertThatNullPointerException().isThrownBy(() -> PixKey.evp((String) null));
        assertThatNullPointerException().isThrownBy(() -> PixKey.evp((UUID) null));
        assertThatNullPointerException().isThrownBy(() -> new PixKey(null, "x"));
        assertThatNullPointerException().isThrownBy(() -> new PixKey(PixKey.Type.CPF, null));
    }
}
