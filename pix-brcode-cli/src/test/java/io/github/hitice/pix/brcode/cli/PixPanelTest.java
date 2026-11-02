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
        assertThat(panel.keyStatus.getText()).isEqualTo("Reconhecida como e-mail: pix@example.com");

        panel.key.setText("123");
        assertThat(panel.keyStatus.getText()).startsWith("Chave inv\u00e1lida:");

        panel.key.setText("");
        assertThat(panel.keyStatus.getText()).isBlank();
    }

    @Test
    void generatesTheCodeAndTheQrFromTheKeyAlone() throws Exception {
        PixPanel panel = new PixPanel();
        assertThat(panel.save(dir.resolve("nada.png"))).isFalse();
        panel.key.setText("pix@example.com");
        panel.amount.setText("0,01");

        panel.generate.doClick();

        BrCode code = panel.current();
        assertThat(code).isNotNull();
        assertThat(BrCode.validate(panel.payload.getText()).isValid()).isTrue();
        assertThat(code.merchantName()).isEqualTo(PixForm.DEFAULT_NAME);
        assertThat(panel.qr.getIcon()).isNotNull();
        assertThat(panel.copy.isEnabled()).isTrue();
        assertThat(panel.message.getText()).contains("R$ 0,01");

        Path png = dir.resolve("pix.png");
        assertThat(panel.save(png)).isTrue();
        assertThat(Files.readAllBytes(png)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }

    @Test
    void reportsAnErrorAndClearsThePreviousCode() {
        PixPanel panel = new PixPanel();
        panel.key.setText("pix@example.com");
        panel.generate();

        panel.amount.setText("abc");
        panel.generate();

        assertThat(panel.current()).isNull();
        assertThat(panel.message.getText()).startsWith("Erro:");
        assertThat(panel.payload.getText()).isEmpty();
        assertThat(panel.qr.getIcon()).isNull();
        assertThat(panel.save.isEnabled()).isFalse();
    }

    @Test
    void checksAPastedCode() {
        PixPanel panel = new PixPanel();
        String payload = PixForm.build("pix@example.com", "5", "Maria", "Recife", "", "").payload();

        panel.check.doClick();
        assertThat(panel.checkResult.getText()).isEqualTo("Cole um c\u00f3digo para conferir.");

        panel.pasted.setText(payload);
        panel.check();
        assertThat(panel.checkResult.getText()).startsWith("C\u00f3digo Pix v\u00e1lido (est\u00e1tico)")
                .contains("Maria, Recife").contains("R$ 5,00");

        panel.pasted.setText(payload.replace("5.00", "9.00"));
        panel.check();
        assertThat(panel.checkResult.getText()).startsWith("C\u00f3digo Pix inv\u00e1lido")
                .contains("o c\u00f3digo foi alterado ou corrompido");
    }
}
