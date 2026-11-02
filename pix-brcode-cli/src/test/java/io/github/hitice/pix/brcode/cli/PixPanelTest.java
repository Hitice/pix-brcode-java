package io.github.hitice.pix.brcode.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hitice.pix.brcode.BrCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives the window's panel without a display (surefire runs with java.awt.headless=true). */
class PixPanelTest {

    @TempDir
    Path dir;

    @Test
    void showsTheKeyTypeAsItIsTyped() {
        PixPanel panel = new PixPanel();

        panel.key.setText("pix@example.com");
        assertThat(panel.keyStatus.getText()).contains("Chave de e-mail: pix@example.com");
        assertThat(panel.keyStatus.getForeground()).isEqualTo(PixPanel.ACCENT);

        panel.key.setText("123");
        assertThat(panel.keyStatus.getForeground()).isEqualTo(PixPanel.ERROR);

        panel.key.setText("");
        assertThat(panel.keyStatus.getText()).isBlank();
    }

    @Test
    void generatesTheCodeAndTheQrFromTheKeyAlone() throws Exception {
        PixPanel panel = new PixPanel();
        assertThat(panel.save(dir.resolve("nada.png"))).isFalse();
        panel.key.setText("pix@example.com");
        panel.amount.setText("0,01");

        panel.generate();

        BrCode code = panel.current();
        assertThat(code).isNotNull();
        assertThat(BrCode.validate(panel.payload.getText()).isValid()).isTrue();
        assertThat(code.merchantName()).isEqualTo(PixForm.DEFAULT_NAME);
        assertThat(panel.qr.getIcon()).isNotNull();
        assertThat(panel.copy.isEnabled()).isTrue();
        assertThat(panel.message.getText()).contains("Pronto para receber R$ 0,01").contains("Dica: preencha o seu nome");

        Path png = dir.resolve("pix.png");
        assertThat(panel.save(png)).isTrue();
        assertThat(Files.readAllBytes(png)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(panel.message.getText()).isEqualTo("Imagem salva em pix.png");
    }

    @Test
    void anOpenAmountSaysThePayerTypesIt() {
        PixPanel panel = new PixPanel();
        panel.key.setText("pix@example.com");

        panel.generate();

        assertThat(panel.message.getText()).contains("Pronto. Quem paga digita o valor.");

        panel.name.setText("Maria");
        panel.generate();
        assertThat(panel.message.getText()).isEqualTo("Pronto. Quem paga digita o valor.");
    }

    @Test
    void anIncompleteKeyKeepsTheEmptyStateWithoutAnError() {
        PixPanel panel = new PixPanel();

        panel.generate();
        assertThat(panel.current()).isNull();
        assertThat(panel.message.getText()).isBlank();

        panel.key.setText("1234");
        panel.generate();
        assertThat(panel.current()).isNull();
        assertThat(panel.qr.getText()).contains("Confira a chave Pix");
    }

    @Test
    void reportsAnInvalidFieldAndClearsThePreviousCode() {
        PixPanel panel = new PixPanel();
        panel.key.setText("pix@example.com");
        panel.generate();

        panel.amount.setText("abc");
        panel.generate();

        assertThat(panel.current()).isNull();
        assertThat(panel.message.getText()).startsWith("Não deu para gerar:");
        assertThat(panel.payload.getText()).isEmpty();
        assertThat(panel.qr.getIcon()).isNull();
        assertThat(panel.save.isEnabled()).isFalse();
    }

    @Test
    void moreOptionsShowsTheOptionalFields() {
        PixPanel panel = new PixPanel();
        assertThat(panel.options.isVisible()).isFalse();

        panel.showOptions(true);
        assertThat(panel.options.isVisible()).isTrue();
        assertThat(panel.moreOptions.getText()).startsWith("Menos opções");

        panel.name.setText("Padaria Sao Joao");
        panel.key.setText("pix@example.com");
        panel.generate();
        assertThat(panel.current().merchantName()).isEqualTo("Padaria Sao Joao");

        panel.showOptions(false);
        assertThat(panel.options.isVisible()).isFalse();
    }

    @Test
    void checksAPastedCode() {
        PixPanel panel = new PixPanel();
        String payload = PixForm.build("pix@example.com", "5", "Maria", "Recife", "", "").payload();

        panel.check.doClick();
        assertThat(panel.checkResult.getText()).isEqualTo("Cole um código para conferir.");

        panel.pasted.setText(payload);
        panel.check();
        assertThat(panel.checkBadge.getText()).contains("Código válido");
        assertThat(panel.checkResult.getText()).startsWith("Código Pix válido (estático)")
                .contains("Maria, Recife").contains("R$ 5,00");

        panel.pasted.setText(payload.replace("5.00", "9.00"));
        panel.check();
        assertThat(panel.checkBadge.getText()).contains("Código com problema");
        assertThat(panel.checkResult.getText()).contains("o código foi alterado ou corrompido");
    }

    @Test
    void drawsTheAppIconAtEverySize() {
        for (int size : new int[] {16, 32, 128}) {
            assertThat(PixWindow.icon(size).getWidth()).isEqualTo(size);
        }
    }
}
