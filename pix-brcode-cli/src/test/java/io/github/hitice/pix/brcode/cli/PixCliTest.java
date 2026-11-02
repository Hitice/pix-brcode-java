package io.github.hitice.pix.brcode.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.qrcode.BrCodeQr;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PixCliTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

    private int run(String input, String... args) {
        PixCli cli = new PixCli(new BufferedReader(new StringReader(input)),
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8), dir);
        return cli.run(args);
    }

    private String out() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    private static String payloadLine(String output) {
        String[] lines = output.split("\\R");
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].equals("Pix copia e cola:")) {
                return lines[i + 1];
            }
        }
        throw new AssertionError("no payload in output:\n" + output);
    }

    @Test
    void generatesAValidCodeFromPortugueseOptions() {
        int code = run("", "gerar", "--chave", "pix@example.com", "--nome", "Maria Souza",
                "--cidade", "São Paulo", "--valor", "0,01", "--txid", "TESTE1",
                "--descricao", "Teste", "--sem-qr");

        assertThat(code).isEqualTo(PixCli.OK);
        String payload = payloadLine(out());
        assertThat(BrCode.validate(payload).isValid()).isTrue();
        assertThat(payload).contains("54040.01").contains("Sao Paulo");
        assertThat(out()).contains("(e-mail)").contains("R$ 0,01").doesNotContain(TerminalQr.DARK);
    }

    @Test
    void acceptsTheEnglishNamesToo() {
        int code = run("", "generate", "--key", "pix@example.com", "--name", "Maria",
                "--city", "Recife", "--amount", "5", "--description", "Bolo", "--no-qr");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(payloadLine(out())).contains("54045.00");
    }

    @Test
    void onlyTheKeyIsRequiredNameAndCityHaveDefaults() {
        assertThat(run("", "gerar", "--valor", "1")).isEqualTo(PixCli.USAGE);
        assertThat(err()).contains("Falta --chave").contains("Uso:");

        int code = run("", "gerar", "--chave", "pix@example.com", "--sem-qr");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(payloadLine(out()))
                .contains("5913" + PixForm.DEFAULT_NAME).contains("6006" + PixForm.DEFAULT_CITY);
    }

    @Test
    void drawsTheQrInTheTerminalByDefault() {
        int code = run("", "gerar", "--chave", "pix@example.com");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains(TerminalQr.DARK).contains(TerminalQr.LIGHT)
                .contains("aberto, quem paga digita").contains("nome cadastrado da chave");
    }

    @Test
    void writesPngAndSvgFiles() throws Exception {
        Path png = dir.resolve("out/qr.png");
        Path svg = dir.resolve("out/qr.svg");

        int code = run("", "gerar", "--chave", "pix@example.com", "--png", png.toString(),
                "--svg", svg.toString(), "--sem-qr");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(Files.readAllBytes(png)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(Files.readString(svg)).startsWith("<?xml").contains("<svg");
        assertThat(out()).contains("QR salvo em");
    }

    @Test
    void rejectsAnInvalidKeyAndANameOverTheLimit() {
        assertThat(run("", "gerar", "--chave", "123")).isEqualTo(PixCli.INVALID);
        assertThat(err()).startsWith("Erro:");
        assertThat(run("", "gerar", "--chave", "pix@example.com", "--nome", "A".repeat(26)))
                .isEqualTo(PixCli.INVALID);
    }

    @Test
    void reportsUnknownOptionMissingValueAndUnknownCommand() {
        assertThat(run("", "gerar", "--cor", "azul")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "gerar", "--chave")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "pagar")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "ler")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "janela")).isEqualTo(PixCli.USAGE);
        assertThat(err()).contains("sem tela dispon\u00edvel");
        assertThat(run("", "ajuda")).isEqualTo(PixCli.OK);
        assertThat(out()).contains("Uso:");
    }

    @Test
    void readsValidStaticAndDynamicPayloads() {
        String payload = BrCode.staticPayment().key("pix@example.com").merchantName("Maria")
                .merchantCity("Recife").amount(new BigDecimal("5.00")).txid("ABC")
                .description("Bolo").build().payload();
        assertThat(run("", "ler", payload)).isEqualTo(PixCli.OK);
        assertThat(out()).contains("C\u00f3digo Pix v\u00e1lido (est\u00e1tico)").contains("Maria, Recife")
                .contains("R$ 5,00").contains("Identificador:  ABC").contains("Descri\u00e7\u00e3o:      Bolo");

        String dynamic = BrCode.dynamicPayment().location("pix.example.com/qr/v2/abc")
                .merchantName("Maria").merchantCity("Recife").build().payload();
        assertThat(run("", "read", dynamic)).isEqualTo(PixCli.OK);
        assertThat(out()).contains("(din\u00e2mico)").contains("Endere\u00e7o:       pix.example.com/qr/v2/abc");
    }

    @Test
    void readReportsTampering() {
        String payload = BrCode.staticPayment().key("pix@example.com").merchantName("Maria")
                .merchantCity("Recife").amount(new BigDecimal("5.00")).build().payload();

        int code = run("", "ler", payload.replace("5.00", "9.00"));

        assertThat(code).isEqualTo(PixCli.INVALID);
        assertThat(err()).contains("C\u00f3digo Pix inv\u00e1lido")
                .contains("CRC_MISMATCH: o c\u00f3digo foi alterado ou corrompido").contains("posi\u00e7\u00e3o");
    }

    @Test
    void interactiveModeAsksKeyAndAmountFirstAndSavesPng() {
        String input = "n\u00e3o-e-chave\npix@example.com\n0,01\nMaria Souza\nRecife\nPEDIDO1\nBolo\n";

        int code = run(input);

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("Reconhecida como e-mail").contains("R$ 0,01")
                .contains("Maria Souza, Recife").contains("QR salvo em");
        assertThat(err()).contains("Chave inv\u00e1lida");
        assertThat(dir.resolve("pix.png")).exists();
    }

    @Test
    void interactiveModeNeedsOnlyTheKey() {
        assertThat(run("pix@example.com\n\n\n\n\n\n")).isEqualTo(PixCli.OK);
        assertThat(out()).contains("aberto, quem paga digita")
                .contains("Recebedor: " + PixForm.DEFAULT_NAME + ", " + PixForm.DEFAULT_CITY);
    }

    @Test
    void interactiveModeStopsWithoutKeyAfterThreeBadKeysOrOnABadAmount() {
        assertThat(run("\n")).isEqualTo(PixCli.INVALID);
        assertThat(run("a\nb\nc\n")).isEqualTo(PixCli.INVALID);
        assertThat(err()).contains("nenhuma chave v\u00e1lida em 3 tentativas");
        assertThat(run("pix@example.com\nabc\n\n\n\n\n")).isEqualTo(PixCli.INVALID);
        assertThat(err()).contains("o valor deve ser como 12,34");
        assertThat(run("terminal")).isEqualTo(PixCli.INVALID);
    }

    @Test
    void opensTheWindowOnlyWithoutATerminalOrWhenAsked() {
        assertThat(PixCli.wantsWindow(new String[0], true, false)).isTrue();
        assertThat(PixCli.wantsWindow(new String[0], false, false)).isFalse();
        assertThat(PixCli.wantsWindow(new String[] {"janela"}, false, false)).isTrue();
        assertThat(PixCli.wantsWindow(new String[] {"window"}, false, false)).isTrue();
        assertThat(PixCli.wantsWindow(new String[] {"ler", "x"}, true, false)).isFalse();
        assertThat(PixCli.wantsWindow(new String[0], true, true)).isFalse();
    }

    @Test
    void terminalQrPaintsEveryModuleWithItsColor() {
        BrCodeQr qr = BrCodeQr.of(BrCode.staticPayment().key("pix@example.com")
                .merchantName("Maria").merchantCity("Recife").amount(new BigDecimal("0.01")).build());
        int quiet = TerminalQr.QUIET_ZONE;

        String[] rows = TerminalQr.render(qr).split("\\R");

        assertThat(rows).hasSize(qr.size() + 2 * quiet);
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            assertThat(line).endsWith(TerminalQr.RESET);
            line = line.substring(0, line.length() - TerminalQr.RESET.length());
            int pos = 0;
            int x = -quiet;
            while (pos < line.length()) {
                boolean dark = line.startsWith(TerminalQr.DARK, pos);
                assertThat(dark || line.startsWith(TerminalQr.LIGHT, pos)).as("cell at %d", pos).isTrue();
                assertThat(dark).as("x=%d y=%d", x, row - quiet).isEqualTo(dark(qr, x, row - quiet));
                pos += dark ? TerminalQr.DARK.length() : TerminalQr.LIGHT.length();
                x++;
            }
            assertThat(x).isEqualTo(qr.size() + quiet);
        }
    }

    private static boolean dark(BrCodeQr qr, int x, int y) {
        return x >= 0 && y >= 0 && x < qr.size() && y < qr.size() && qr.isDark(x, y);
    }
}
