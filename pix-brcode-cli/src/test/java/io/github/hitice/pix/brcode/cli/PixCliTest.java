package io.github.hitice.pix.brcode.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.PixKey;
import io.github.hitice.pix.brcode.qrcode.BrCodeQr;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        assertThat(out()).contains("(e-mail)").contains("R$ 0,01").doesNotContain("\u001b[");
    }

    @Test
    void acceptsTheEnglishNamesToo() {
        int code = run("", "generate", "--key", "pix@example.com", "--name", "Maria",
                "--city", "Recife", "--amount", "5", "--description", "Bolo", "--no-qr");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(payloadLine(out())).contains("54045.00");
    }

    @Test
    void drawsTheQrInTheTerminalByDefault() {
        int code = run("", "gerar", "--chave", "pix@example.com", "--nome", "Maria",
                "--cidade", "Recife");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("▀").contains("aberto, quem paga digita");
    }

    @Test
    void writesPngAndSvgFiles() throws Exception {
        Path png = dir.resolve("out/qr.png");
        Path svg = dir.resolve("out/qr.svg");

        int code = run("", "gerar", "--chave", "pix@example.com", "--nome", "Maria",
                "--cidade", "Recife", "--png", png.toString(), "--svg", svg.toString(), "--sem-qr");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(Files.readAllBytes(png)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(Files.readString(svg)).startsWith("<?xml").contains("<svg");
        assertThat(out()).contains("QR salvo em");
    }

    @Test
    void rejectsAnInvalidKey() {
        int code = run("", "gerar", "--chave", "123", "--nome", "Maria", "--cidade", "Recife");

        assertThat(code).isEqualTo(PixCli.INVALID);
        assertThat(err()).startsWith("Erro:");
    }

    @Test
    void rejectsANameOverTheLimit() {
        int code = run("", "gerar", "--chave", "pix@example.com",
                "--nome", "A".repeat(26), "--cidade", "Recife");

        assertThat(code).isEqualTo(PixCli.INVALID);
    }

    @Test
    void reportsMissingRequiredOption() {
        int code = run("", "gerar", "--chave", "pix@example.com", "--nome", "Maria");

        assertThat(code).isEqualTo(PixCli.USAGE);
        assertThat(err()).contains("Falta --cidade").contains("Uso:");
    }

    @Test
    void reportsUnknownOptionAndMissingValue() {
        assertThat(run("", "gerar", "--cor", "azul")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "gerar", "--chave")).isEqualTo(PixCli.USAGE);
    }

    @Test
    void reportsUnknownCommandAndPrintsHelp() {
        assertThat(run("", "pagar")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "ler")).isEqualTo(PixCli.USAGE);
        assertThat(run("", "ajuda")).isEqualTo(PixCli.OK);
        assertThat(out()).contains("Uso:");
    }

    @Test
    void readsAValidPayload() {
        String payload = BrCode.staticPayment().key("pix@example.com").merchantName("Maria")
                .merchantCity("Recife").amount(new BigDecimal("5.00")).txid("ABC")
                .description("Bolo").build().payload();

        int code = run("", "ler", payload);

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("Codigo Pix valido (estatico)").contains("Maria, Recife")
                .contains("R$ 5,00").contains("Identificador:  ABC").contains("Descricao:      Bolo");
    }

    @Test
    void readsADynamicPayloadWithTheEnglishCommand() {
        String payload = BrCode.dynamicPayment().location("pix.example.com/qr/v2/abc")
                .merchantName("Maria").merchantCity("Recife").build().payload();

        int code = run("", "read", payload);

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("(dinamico)").contains("Endereco:       pix.example.com/qr/v2/abc");
    }

    @Test
    void readReportsTampering() {
        String payload = BrCode.staticPayment().key("pix@example.com").merchantName("Maria")
                .merchantCity("Recife").amount(new BigDecimal("5.00")).build().payload();

        int code = run("", "ler", payload.replace("5.00", "9.00"));

        assertThat(code).isEqualTo(PixCli.INVALID);
        assertThat(err()).contains("Codigo Pix invalido")
                .contains("CRC_MISMATCH: o codigo foi alterado ou corrompido").contains("posicao");
    }

    @Test
    void explainsEveryProblemCategoryInPortuguese() {
        for (io.github.hitice.pix.brcode.ErrorCode code : io.github.hitice.pix.brcode.ErrorCode.values()) {
            assertThat(PixCli.explain(code)).as(code.name()).isNotBlank();
        }
    }

    @Test
    void interactiveModeAsksAndSavesPng() {
        String input = "nao-e-chave\npix@example.com\nMaria Souza\nRecife\n0,01\nPEDIDO1\nBolo\n";

        int code = run(input);

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("Reconhecida como e-mail").contains("R$ 0,01")
                .contains("QR salvo em");
        assertThat(err()).contains("Chave invalida");
        assertThat(dir.resolve("pix.png")).exists();
    }

    @Test
    void interactiveModeSkipsOptionalFields() {
        int code = run("pix@example.com\nMaria\nRecife\n\n\n\n");

        assertThat(code).isEqualTo(PixCli.OK);
        assertThat(out()).contains("aberto, quem paga digita");
    }

    @Test
    void interactiveModeStopsWithoutKeyOrAfterThreeBadKeys() {
        assertThat(run("\n")).isEqualTo(PixCli.INVALID);
        assertThat(run("a\nb\nc\n")).isEqualTo(PixCli.INVALID);
        assertThat(err()).contains("nenhuma chave valida em 3 tentativas");
    }

    @Test
    void interactiveModeRequiresNameAndCity() {
        assertThat(run("pix@example.com\n")).isEqualTo(PixCli.INVALID);
        assertThat(err()).contains("nome e cidade sao obrigatorios");
    }

    @Test
    void labelsEveryKeyAndCodeTypeInPortuguese() {
        assertThat(PixCli.label(PixKey.Type.CPF)).isEqualTo("CPF");
        assertThat(PixCli.label(PixKey.Type.CNPJ)).isEqualTo("CNPJ");
        assertThat(PixCli.label(PixKey.Type.PHONE)).isEqualTo("telefone");
        assertThat(PixCli.label(PixKey.Type.EVP)).isEqualTo("chave aleatoria");
        assertThat(PixCli.label(BrCode.Type.RECURRENCE_ONLY)).isEqualTo("so recorrencia");
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
        Matcher cell = Pattern.compile("\u001b\\[(30|97);(40|107)m▀").matcher("");

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
