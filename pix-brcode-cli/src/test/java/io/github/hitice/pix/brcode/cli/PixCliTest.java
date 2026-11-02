package io.github.hitice.pix.brcode.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            if (lines[i].equals("Pix copy and paste:")) {
                return lines[i + 1];
            }
        }
        throw new AssertionError("no payload in output:\n" + output);
    }

    @Test
    void generatesAValidCodeFromArguments() {
        int code = run("", "generate", "--key", "pix@example.com", "--name", "Maria Souza",
                "--city", "São Paulo", "--amount", "0,01", "--txid", "TESTE1",
                "--description", "Teste", "--no-qr");

        assertThat(code).isEqualTo(PixCli.OK);
        String payload = payloadLine(out());
        assertThat(BrCode.validate(payload).isValid()).isTrue();
        assertThat(payload).contains("54040.01").contains("Sao Paulo");
        assertThat(out()).contains("(EMAIL)").contains("R$ 0.01").doesNotContain("\u001b[");
    }

    @Test
    void drawsTheQrInTheTerminalByDefault() {
        int code = run("", "generate", "--key", "pix@example.com", "--name", "Maria",
                "--city", "Recife");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("▀").contains("open, the payer types it");
    }

    @Test
    void writesPngAndSvgFiles() throws Exception {
        Path png = dir.resolve("out/qr.png");
        Path svg = dir.resolve("out/qr.svg");

        int code = run("", "generate", "--key", "pix@example.com", "--name", "Maria",
                "--city", "Recife", "--png", png.toString(), "--svg", svg.toString(), "--no-qr");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(Files.readAllBytes(png)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(Files.readString(svg)).startsWith("<?xml").contains("<svg");
    }

    @Test
    void rejectsAnInvalidKey() {
        int code = run("", "generate", "--key", "123", "--name", "Maria", "--city", "Recife");

        assertThat(code).isEqualTo(PixCli.INVALID);
        assertThat(err()).startsWith("Error:");
    }

    @Test
    void rejectsANameOverTheLimit() {
        int code = run("", "generate", "--key", "pix@example.com",
                "--name", "A".repeat(26), "--city", "Recife");

        assertThat(code).isEqualTo(PixCli.INVALID);
    }

    @Test
    void reportsMissingRequiredOption() {
        int code = run("", "generate", "--key", "pix@example.com", "--name", "Maria");

        assertThat(code).isEqualTo(PixCli.USAGE);
        assertThat(err()).contains("Missing --city").contains("Usage:");
    }

    @Test
    void reportsUnknownOptionAndMissingValue() {
        assertThat(run("", "generate", "--color", "red")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "generate", "--key")).isEqualTo(PixCli.USAGE);
    }

    @Test
    void reportsUnknownCommandAndPrintsHelp() {
        assertThat(run("", "pay")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "read")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "--help")).isEqualTo(PixCli.OK);
        assertThat(out()).contains("Usage:");
    }

    @Test
    void readsAValidPayload() {
        String payload = BrCode.staticPayment().key("pix@example.com").merchantName("Maria")
                .merchantCity("Recife").amount(new BigDecimal("5.00")).txid("ABC")
                .description("Bolo").build().payload();

        int code = run("", "read", payload);

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("Valid Pix code").contains("Maria, Recife")
                .contains("R$ 5.00").contains("Identifier:  ABC").contains("Description: Bolo");
    }

    @Test
    void readsADynamicPayload() {
        String payload = BrCode.dynamicPayment().location("pix.example.com/qr/v2/abc")
                .merchantName("Maria").merchantCity("Recife").build().payload();

        int code = run("", "read", payload);

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("Location:    pix.example.com/qr/v2/abc");
    }

    @Test
    void readReportsTampering() {
        String payload = BrCode.staticPayment().key("pix@example.com").merchantName("Maria")
                .merchantCity("Recife").amount(new BigDecimal("5.00")).build().payload();

        int code = run("", "read", payload.replace("5.00", "9.00"));

        assertThat(code).isEqualTo(PixCli.INVALID);
        assertThat(err()).contains("CRC_MISMATCH");
    }

    @Test
    void interactiveModeAsksAndSavesPng() {
        String input = "not-a-key\npix@example.com\nMaria Souza\nRecife\n0,01\nPEDIDO1\nBolo\n";

        int code = run(input);

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("Recognized as EMAIL").contains("R$ 0.01")
                .contains("QR image saved to");
        assertThat(err()).contains("Not a Pix key");
        assertThat(dir.resolve("pix.png")).exists();
    }

    @Test
    void interactiveModeSkipsOptionalFields() {
        int code = run("pix@example.com\nMaria\nRecife\n\n\n\n");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("open, the payer types it");
    }

    @Test
    void interactiveModeStopsWithoutKeyOrAfterThreeBadKeys() {
        assertThat(run("\n")).isEqualTo(PixCli.INVALID);
        assertThat(run("a\nb\nc\n")).isEqualTo(PixCli.INVALID);
        assertThat(err()).contains("no valid key after 3 attempts");
    }

    @Test
    void interactiveModeRequiresNameAndCity() {
        assertThat(run("pix@example.com\n")).isEqualTo(PixCli.INVALID);
        assertThat(err()).contains("name and city are required");
    }

    @Test
    void parsesBrazilianAndDotAmounts() {
        assertThat(PixCli.parseAmount("12.34")).isEqualByComparingTo("12.34");
        assertThat(PixCli.parseAmount("12,34")).isEqualByComparingTo("12.34");
        assertThat(PixCli.parseAmount("R$ 1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(PixCli.parseAmount("  ")).isNull();
        assertThatThrownBy(() -> PixCli.parseAmount("abc"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12,34");
    }

    @Test
    void terminalQrColorsMatchEveryModule() {
        BrCodeQr qr = BrCodeQr.of(BrCode.staticPayment().key("pix@example.com")
                .merchantName("Maria").merchantCity("Recife").amount(new BigDecimal("0.01")).build());
        int quiet = TerminalQr.QUIET_ZONE;
        java.util.regex.Matcher cell = java.util.regex.Pattern
                .compile("\u001b\\[(30|97);(40|107)m▀").matcher("");

        String[] rows = TerminalQr.render(qr).split("\\R");

        for (int row = 0; row < rows.length; row++) {
            cell.reset(rows[row]);
            int x = -quiet;
            while (cell.find()) {
                int top = 2 * row - quiet;
                assertThat(cell.group(1).equals("30")).as("top x=%d y=%d", x, top)
                        .isEqualTo(dark(qr, x, top));
                assertThat(cell.group(2).equals("40")).as("bottom x=%d y=%d", x, top + 1)
                        .isEqualTo(dark(qr, x, top + 1));
                x++;
            }
            assertThat(x).isEqualTo(qr.size() + quiet);
        }
    }

    private static boolean dark(BrCodeQr qr, int x, int y) {
        return x >= 0 && y >= 0 && x < qr.size() && y < qr.size() && qr.isDark(x, y);
    }

    @Test
    void terminalQrHasQuietZoneAndHalfRows() {
        BrCodeQr qr = BrCodeQr.of(BrCode.staticPayment().key("pix@example.com")
                .merchantName("Maria").merchantCity("Recife").build());
        int side = qr.size() + 2 * TerminalQr.QUIET_ZONE;

        String[] rows = TerminalQr.render(qr).split("\\R");

        assertThat(rows).hasSize((side + 1) / 2);
        for (String row : rows) {
            assertThat(row.chars().filter(c -> c == '▀').count()).isEqualTo(side);
        }
    }
}
