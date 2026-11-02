package io.github.hitice.pix.brcode.cli;

import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.BrCodeException;
import io.github.hitice.pix.brcode.PixKey;
import io.github.hitice.pix.brcode.Problem;
import io.github.hitice.pix.brcode.ValidationResult;
import io.github.hitice.pix.brcode.qrcode.BrCodeQr;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Command line tool: generates a static Pix code from a key, or reads and checks a payload.
 *
 * <pre>
 * java -jar pix.jar                       asks for the fields
 * java -jar pix.jar generate --key ... --name ... --city ... [--amount 0.01] [--png qr.png]
 * java -jar pix.jar read "000201..."
 * </pre>
 */
public final class PixCli {

    static final int OK = 0;
    static final int INVALID = 1;
    static final int USAGE = 2;

    private static final int KEY_ATTEMPTS = 3;
    private static final int PNG_MODULE_SIZE = 8;
    private static final Set<String> VALUE_OPTIONS = Set.of(
            "--key", "--name", "--city", "--amount", "--txid", "--description", "--png", "--svg");
    private static final Set<String> FLAG_OPTIONS = Set.of("--no-qr");

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
     * @param args command and options; none starts the interactive mode
     */
    public static void main(String[] args) {
        PixCli cli = new PixCli(
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                System.out, System.err, Path.of("").toAbsolutePath());
        System.exit(cli.run(args));
    }

    int run(String[] args) {
        if (args.length == 0) {
            return interactive();
        }
        return switch (args[0]) {
            case "generate" -> generateFromArgs(args);
            case "read" -> args.length == 2 ? read(args[1]) : usageError("read takes one payload");
            case "help", "-h", "--help" -> {
                usage(out);
                yield OK;
            }
            default -> usageError("Unknown command: " + args[0]);
        };
    }

    private int generateFromArgs(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String name = args[i];
            if (FLAG_OPTIONS.contains(name)) {
                options.put(name, "");
            } else if (VALUE_OPTIONS.contains(name) && i + 1 < args.length) {
                options.put(name, args[++i]);
            } else {
                return usageError("Unknown option or missing value: " + name);
            }
        }
        for (String required : new String[] {"--key", "--name", "--city"}) {
            if (!options.containsKey(required)) {
                return usageError("Missing " + required);
            }
        }
        return generate(options);
    }

    private int interactive() {
        out.println("Pix code generator. Press Enter to skip optional fields.");
        Map<String, String> options = new HashMap<>();
        String key = askKey();
        if (key == null) {
            return INVALID;
        }
        options.put("--key", key);
        String name = ask("Receiver name, as your bank shows it (up to 25 characters): ");
        String city = ask("City (up to 15 characters): ");
        if (name.isEmpty() || city.isEmpty()) {
            err.println("Error: name and city are required.");
            return INVALID;
        }
        options.put("--name", name);
        options.put("--city", city);
        putIfPresent(options, "--amount",
                ask("Amount, for example 0.01 (empty lets the payer type it): "));
        putIfPresent(options, "--txid", ask("Identifier, letters and digits up to 25 (optional): "));
        putIfPresent(options, "--description", ask("Description (optional): "));
        options.put("--png", workDir.resolve("pix.png").toString());
        return generate(options);
    }

    private String askKey() {
        for (int attempt = 0; attempt < KEY_ATTEMPTS; attempt++) {
            String key = ask("Pix key (CPF, CNPJ, e-mail, phone +55..., or random key): ");
            if (key.isEmpty()) {
                err.println("Error: the key is required.");
                return null;
            }
            try {
                PixKey parsed = PixKey.of(key);
                out.println("  Recognized as " + parsed.type() + ": " + parsed.value());
                return key;
            } catch (IllegalArgumentException e) {
                err.println("  " + e.getMessage());
            }
        }
        err.println("Error: no valid key after " + KEY_ATTEMPTS + " attempts.");
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

    private static void putIfPresent(Map<String, String> options, String name, String value) {
        if (!value.isEmpty()) {
            options.put(name, value);
        }
    }

    private int generate(Map<String, String> options) {
        BrCode code;
        try {
            BrCode.StaticBuilder builder = BrCode.staticPayment()
                    .key(options.get("--key"))
                    .merchantName(options.get("--name"))
                    .merchantCity(options.get("--city"));
            BigDecimal amount = parseAmount(options.getOrDefault("--amount", ""));
            if (amount != null) {
                builder.amount(amount);
            }
            if (options.containsKey("--txid")) {
                builder.txid(options.get("--txid"));
            }
            if (options.containsKey("--description")) {
                builder.description(options.get("--description"));
            }
            code = builder.build();
        } catch (IllegalArgumentException | IllegalStateException | BrCodeException e) {
            err.println("Error: " + e.getMessage());
            return INVALID;
        }

        PixKey key = code.key().orElseThrow();
        out.println();
        out.println("Key:      " + key.value() + " (" + key.type() + ")");
        out.println("Receiver: " + code.merchantName() + ", " + code.merchantCity());
        out.println("Amount:   " + code.amount().map(a -> "R$ " + a.toPlainString())
                .orElse("open, the payer types it"));
        out.println();
        out.println("Pix copy and paste:");
        out.println(code.payload());

        BrCodeQr qr = BrCodeQr.of(code);
        if (!options.containsKey("--no-qr")) {
            out.println();
            out.print(TerminalQr.render(qr));
        }
        try {
            if (options.containsKey("--png")) {
                Path png = Path.of(options.get("--png")).toAbsolutePath();
                createParent(png);
                qr.writePng(png, PNG_MODULE_SIZE);
                out.println("QR image saved to " + png);
            }
            if (options.containsKey("--svg")) {
                Path svg = Path.of(options.get("--svg")).toAbsolutePath();
                createParent(svg);
                qr.writeSvg(svg, PNG_MODULE_SIZE);
                out.println("QR image saved to " + svg);
            }
        } catch (IOException e) {
            err.println("Error: could not write the image: " + e.getMessage());
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
        ValidationResult result = BrCode.validate(payload);
        if (!result.isValid()) {
            err.println("Invalid Pix code:");
            for (Problem problem : result.problems()) {
                err.println("  " + problem.code() + ": " + problem);
            }
            return INVALID;
        }
        BrCode code = BrCode.parse(payload);
        out.println("Valid Pix code (" + code.type() + ")");
        code.key().ifPresent(k -> out.println("Key:         " + k.value() + " (" + k.type() + ")"));
        code.location().ifPresent(l -> out.println("Location:    " + l));
        out.println("Receiver:    " + code.merchantName() + ", " + code.merchantCity());
        out.println("Amount:      " + code.amount().map(a -> "R$ " + a.toPlainString())
                .orElse("open, the payer types it"));
        code.txid().ifPresent(t -> out.println("Identifier:  " + t));
        code.description().ifPresent(d -> out.println("Description: " + d));
        out.println("CRC:         " + code.crc());
        return OK;
    }

    /**
     * Accepts "12.34", "12,34", "1.234,56" and "R$ 0,01". Returns null for an empty string.
     */
    static BigDecimal parseAmount(String text) {
        String value = text.replace("R$", "").replace(" ", "").strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.contains(",")) {
            value = value.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Amount must look like 12.34 or 12,34, got \"" + text + "\"", e);
        }
    }

    private int usageError(String message) {
        err.println("Error: " + message);
        usage(err);
        return USAGE;
    }

    private static void usage(PrintStream stream) {
        stream.println("""
                Usage:
                  java -jar pix.jar
                      Asks for the key, name, city and amount, then prints the code,
                      draws the QR in the terminal and saves pix.png.
                  java -jar pix.jar generate --key KEY --name NAME --city CITY
                      [--amount 0.01] [--txid ID] [--description TEXT]
                      [--png qr.png] [--svg qr.svg] [--no-qr]
                  java -jar pix.jar read PAYLOAD
                      Checks a copy-and-paste code and shows its fields.""");
    }
}
