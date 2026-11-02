package io.github.hitice.pix.brcode.cli;

import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.BrCodeException;
import io.github.hitice.pix.brcode.PixKey;
import io.github.hitice.pix.brcode.qrcode.BrCodeQr;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a static Pix code from a key, or reads and checks a payload. Opened with a double
 * click (no terminal), it shows a window; in a terminal it asks questions or takes commands.
 * Messages are in Portuguese, since Pix is a Brazilian payment system; commands and options also
 * accept their English names.
 *
 * <pre>
 * java -jar pix.jar                       window, or questions when run in a terminal
 * java -jar pix.jar janela                window
 * java -jar pix.jar gerar --chave ... [--valor 0,01] [--png qr.png]
 * java -jar pix.jar ler "000201..."
 * </pre>
 */
public final class PixCli {

    static final int OK = 0;
    static final int INVALID = 1;
    static final int USAGE = 2;

    private static final int KEY_ATTEMPTS = 3;
    private static final int PNG_MODULE_SIZE = 8;

    /** Option name, in Portuguese or English, to the internal name used below. */
    private static final Map<String, String> VALUE_OPTIONS = Map.ofEntries(
            Map.entry("--chave", "key"), Map.entry("--key", "key"),
            Map.entry("--nome", "name"), Map.entry("--name", "name"),
            Map.entry("--cidade", "city"), Map.entry("--city", "city"),
            Map.entry("--valor", "amount"), Map.entry("--amount", "amount"),
            Map.entry("--txid", "txid"),
            Map.entry("--descricao", "description"), Map.entry("--description", "description"),
            Map.entry("--png", "png"), Map.entry("--svg", "svg"));
    private static final Map<String, String> FLAG_OPTIONS = Map.of(
            "--sem-qr", "noQr", "--no-qr", "noQr");

    private final BufferedReader in;
    private final PrintStream out;
    private final PrintStream err;
    private final Path workDir;

    PixCli(BufferedReader in, PrintStream out, PrintStream err, Path workDir) {
        this.in = in;
        this.out = out;
        this.err = err;
        this.workDir = workDir;
    }

    /**
     * Entry point.
     *
     * @param args command and options; none opens the window, or asks questions in a terminal
     */
    public static void main(String[] args) {
        boolean noTerminal = System.console() == null;
        if (wantsWindow(args, noTerminal, GraphicsEnvironment.isHeadless())) {
            PixWindow.open();
            return;
        }
        PixCli cli = new PixCli(
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                System.out, System.err, Path.of("").toAbsolutePath());
        System.exit(cli.run(args));
    }

    /** A double click starts the jar with no arguments and no terminal: that gets the window. */
    static boolean wantsWindow(String[] args, boolean noTerminal, boolean headless) {
        if (headless) {
            return false;
        }
        if (args.length == 1 && (args[0].equals("janela") || args[0].equals("window"))) {
            return true;
        }
        return args.length == 0 && noTerminal;
    }

    int run(String[] args) {
        if (args.length == 0) {
            return interactive();
        }
        return switch (args[0]) {
            case "terminal" -> interactive();
            case "gerar", "generate" -> generateFromArgs(args);
            case "ler", "read" -> args.length == 2 ? read(args[1]) : usageError("ler recebe um c\u00f3digo");
            case "janela", "window" -> usageError("sem tela dispon\u00edvel para abrir a janela");
            case "ajuda", "help", "-h", "--help" -> {
                usage(out);
                yield OK;
            }
            default -> usageError("Comando desconhecido: " + args[0]);
        };
    }

    private int generateFromArgs(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String name = args[i];
            if (FLAG_OPTIONS.containsKey(name)) {
                options.put(FLAG_OPTIONS.get(name), "");
            } else if (VALUE_OPTIONS.containsKey(name) && i + 1 < args.length) {
                options.put(VALUE_OPTIONS.get(name), args[++i]);
            } else {
                return usageError("Op\u00e7\u00e3o desconhecida ou sem valor: " + name);
            }
        }
        if (!options.containsKey("key")) {
            return usageError("Falta --chave");
        }
        return generate(options);
    }

    private int interactive() {
        out.println("Gerador de c\u00f3digo Pix. S\u00f3 a chave \u00e9 obrigat\u00f3ria; Enter pula os outros campos.");
        Map<String, String> options = new HashMap<>();
        String key = askKey();
        if (key == null) {
            return INVALID;
        }
        options.put("key", key);
        options.put("amount", ask("Valor, por exemplo 0,01 (vazio deixa quem paga digitar): "));
        options.put("name", ask("Nome no QR, at\u00e9 25 caracteres (Enter usa \"" + PixForm.DEFAULT_NAME + "\"): "));
        options.put("city", ask("Cidade no QR, at\u00e9 15 caracteres (Enter usa \"" + PixForm.DEFAULT_CITY + "\"): "));
        options.put("txid", ask("Identificador, letras e n\u00fameros at\u00e9 25 (opcional): "));
        options.put("description", ask("Descri\u00e7\u00e3o (opcional): "));
        options.put("png", workDir.resolve("pix.png").toString());
        return generate(options);
    }

    private String askKey() {
        for (int attempt = 0; attempt < KEY_ATTEMPTS; attempt++) {
            String key = ask("Chave Pix (CPF, CNPJ, e-mail, telefone +55... ou chave aleat\u00f3ria): ");
            if (key.isEmpty()) {
                err.println("Erro: a chave \u00e9 obrigat\u00f3ria.");
                return null;
            }
            String status = PixForm.keyStatus(key);
            if (status.startsWith("Chave inv\u00e1lida")) {
                err.println("  " + status);
            } else {
                out.println("  " + status);
                return key;
            }
        }
        err.println("Erro: nenhuma chave v\u00e1lida em " + KEY_ATTEMPTS + " tentativas.");
        return null;
    }

    private String ask(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            String line = in.readLine();
            return line == null ? "" : line.strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int generate(Map<String, String> options) {
        BrCode code;
        try {
            code = PixForm.build(options.get("key"), options.get("amount"), options.get("name"),
                    options.get("city"), options.get("txid"), options.get("description"));
        } catch (IllegalArgumentException | IllegalStateException | BrCodeException e) {
            err.println("Erro: " + e.getMessage());
            return INVALID;
        }

        PixKey key = code.key().orElseThrow();
        out.println();
        out.println("Chave:     " + key.value() + " (" + PixForm.label(key.type()) + ")");
        out.println("Recebedor: " + code.merchantName() + ", " + code.merchantCity());
        out.println("Valor:     " + code.amount().map(PixForm::money).orElse(PixForm.OPEN_AMOUNT));
        out.println(PixForm.PAYER_NOTE);
        out.println();
        out.println("Pix copia e cola:");
        out.println(code.payload());

        BrCodeQr qr = BrCodeQr.of(code);
        if (!options.containsKey("noQr")) {
            out.println();
            out.print(TerminalQr.render(qr));
        }
        try {
            if (options.containsKey("png")) {
                Path png = Path.of(options.get("png")).toAbsolutePath();
                createParent(png);
                qr.writePng(png, PNG_MODULE_SIZE);
                out.println("QR salvo em " + png);
            }
            if (options.containsKey("svg")) {
                Path svg = Path.of(options.get("svg")).toAbsolutePath();
                createParent(svg);
                qr.writeSvg(svg, PNG_MODULE_SIZE);
                out.println("QR salvo em " + svg);
            }
        } catch (IOException e) {
            err.println("Erro: n\u00e3o foi poss\u00edvel gravar a imagem: " + e.getMessage());
            return INVALID;
        }
        return OK;
    }

    private static void createParent(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private int read(String payload) {
        List<String> problems = PixForm.problems(payload);
        if (!problems.isEmpty()) {
            err.println("C\u00f3digo Pix inv\u00e1lido:");
            problems.forEach(p -> err.println("  " + p));
            return INVALID;
        }
        BrCode code = BrCode.parse(payload.strip());
        out.println("C\u00f3digo Pix v\u00e1lido (" + PixForm.label(code.type()) + ")");
        PixForm.describe(code).forEach(out::println);
        return OK;
    }

    private int usageError(String message) {
        err.println("Erro: " + message);
        usage(err);
        return USAGE;
    }

    private static void usage(PrintStream stream) {
        stream.println("""
                Uso:
                  java -jar pix.jar
                      Abre a janela. Num terminal, pergunta a chave e o valor (o resto \u00e9
                      opcional), mostra o copia e cola, desenha o QR e salva pix.png.
                  java -jar pix.jar janela | terminal
                      For\u00e7a a janela ou as perguntas no terminal.
                  java -jar pix.jar gerar --chave CHAVE [--valor 0,01]
                      [--nome NOME] [--cidade CIDADE] [--txid ID] [--descricao TEXTO]
                      [--png qr.png] [--svg qr.svg] [--sem-qr]
                  java -jar pix.jar ler CODIGO
                      Confere um copia e cola e mostra os campos.
                Os comandos e op\u00e7\u00f5es tamb\u00e9m aceitam os nomes em ingl\u00eas
                (generate, read, window, --key, --name, --city, --amount, --description, --no-qr).""");
    }
}
