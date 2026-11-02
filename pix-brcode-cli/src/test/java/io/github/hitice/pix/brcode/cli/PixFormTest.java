package io.github.hitice.pix.brcode.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.ErrorCode;
import io.github.hitice.pix.brcode.PixKey;
import org.junit.jupiter.api.Test;

class PixFormTest {

    @Test
    void buildsWithDefaultsWhenOptionalFieldsAreBlank() {
        BrCode code = PixForm.build(" pix@example.com ", "", " ", null, "", null);

        assertThat(code.merchantName()).isEqualTo(PixForm.DEFAULT_NAME);
        assertThat(code.merchantCity()).isEqualTo(PixForm.DEFAULT_CITY);
        assertThat(code.amount()).isEmpty();
        assertThat(code.txid()).isEmpty();
        assertThat(code.description()).isEmpty();
    }

    @Test
    void buildsWithEveryField() {
        BrCode code = PixForm.build("pix@example.com", "R$ 1.234,56", "Maria", "Recife", "PED1", "Bolo");

        assertThat(code.amount()).hasValueSatisfying(a -> assertThat(a).isEqualByComparingTo("1234.56"));
        assertThat(PixForm.describe(code)).contains("Recebedor:      Maria, Recife",
                "Valor:          R$ 1234,56", "Identificador:  PED1", "Descri\u00e7\u00e3o:      Bolo");
    }

    @Test
    void recognizesKeysAsTheyAreTyped() {
        assertThat(PixForm.keyStatus("")).isEmpty();
        assertThat(PixForm.keyStatus("pix@example.com")).isEqualTo("Reconhecida como e-mail: pix@example.com");
        assertThat(PixForm.keyStatus("123")).startsWith("Chave inv\u00e1lida:");
    }

    @Test
    void reportsNoProblemsForAValidCodeAndExplainsTheOthers() {
        String payload = PixForm.build("pix@example.com", "1", "", "", "", "").payload();

        assertThat(PixForm.problems(payload)).isEmpty();
        assertThat(PixForm.problems(payload.replace("1.00", "2.00")))
                .anySatisfy(line -> assertThat(line).startsWith("CRC_MISMATCH: o c\u00f3digo foi alterado"));
    }

    @Test
    void parsesBrazilianAndDotAmounts() {
        assertThat(PixForm.parseAmount("12.34")).isEqualByComparingTo("12.34");
        assertThat(PixForm.parseAmount("12,34")).isEqualByComparingTo("12.34");
        assertThat(PixForm.parseAmount("R$ 1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(PixForm.parseAmount("  ")).isNull();
        assertThatThrownBy(() -> PixForm.parseAmount("abc"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12,34");
    }

    @Test
    void labelsAndExplanationsCoverEveryValue() {
        assertThat(PixForm.label(PixKey.Type.CPF)).isEqualTo("CPF");
        assertThat(PixForm.label(PixKey.Type.CNPJ)).isEqualTo("CNPJ");
        assertThat(PixForm.label(PixKey.Type.EMAIL)).isEqualTo("e-mail");
        assertThat(PixForm.label(PixKey.Type.PHONE)).isEqualTo("telefone");
        assertThat(PixForm.label(PixKey.Type.EVP)).isEqualTo("chave aleat\u00f3ria");
        assertThat(PixForm.label(BrCode.Type.STATIC)).isEqualTo("est\u00e1tico");
        assertThat(PixForm.label(BrCode.Type.DYNAMIC)).isEqualTo("din\u00e2mico");
        assertThat(PixForm.label(BrCode.Type.RECURRENCE_ONLY)).isEqualTo("s\u00f3 recorr\u00eancia");
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(PixForm.explain(code)).as(code.name()).isNotBlank();
        }
    }
}
