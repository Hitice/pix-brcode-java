package io.github.hitice.pix.brcode.cli;

import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.BrCodeException;
import io.github.hitice.pix.brcode.ErrorCode;
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

/**
 * Command line tool: generates a static Pix code from a key, or reads and checks a payload.
 * Messages are in Portuguese, since Pix is a Brazilian payment system; commands and options
 * also accept their English names.
 *
 * <pre>
 * java -jar pix.jar                       asks for the fields
 * java -jar pix.jar gerar --chave ... --nome ... --cidade ... [--valor 0,01] [--png qr.png]
 * java -jar pix.jar ler "000201..."
 * </pre>
 */
public final class PixCli {

    static final int OK = 0;
    static final int INVALID = 1;
    static final int USAGE = 2;

    private static final int KEY_ATTEMPTS = 3;
    private static final int PNG_MODULE_SIZE = 8;
    private static final String OPEN_AMOUNT = "aberto, quem paga digita";

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
            case "gerar", "generate" -> generateFromArgs(args);
            case "ler", "read" -> args.length == 2 ? read(args[1]) : usageError("ler recebe um codigo");
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
                return usageError("Opcao desconhecida ou sem valor: " + name);
            }
        }
        for (String[] required : new String[][] {{"key", "--chave"}, {"name", "--nome"}, {"city", "--cidade"}}) {
            if (!options.containsKey(required[0])) {
                return usageError("Falta " + required[1]);
            }
        }
        return generate(options);
    }

    private int interactive() {
        out.println("Gerador de codigo Pix. Aperte Enter para pular os campos opcionais.");
        Map<String, String> options = new HashMap<>();
        String key = askKey();
        if (key == null) {
            return INVALID;
        }
        options.put("key", key);
        String name = ask("Nome de quem recebe, como aparece no banco (ate 25 caracteres): ");
        String city = ask("Cidade (ate 15 caracteres): ");
        if (name.isEmpty() || city.isEmpty()) {
            err.println("Erro: nome e cidade sao obrigatorios.");
            return INVALID;
        }
        options.put("name", name);
        options.put("city", city);
        putIfPresent(options, "amount", ask("Valor, por exemplo 0,01 (vazio deixa quem paga digitar): "));
        putIfPresent(options, "txid", ask("Identificador, letras e numeros ate 25 (opcional): "));
        putIfPresent(options, "description", ask("Descricao (opcional): "));
        options.put("png", workDir.resolve("pix.png").toString());
        return generate(options);
    }

    private String askKey() {
        for (int attempt = 0; attempt < KEY_ATTEMPTS; attempt++) {
            String key = ask("Chave Pix (CPF, CNPJ, e-mail, telefone +55... ou chave aleatoria): ");
            if (key.isEmpty()) {
                err.println("Erro: a chave e obrigatoria.");
                return null;
            }
            try {
                PixKey parsed = PixKey.of(key);
                out.println("  Reconhecida como " + label(parsed.type()) + ": " + parsed.value());
                return key;
            } catch (IllegalArgumentException e) {
                err.println("  Chave invalida: " + e.getMessage());
            }
        }
        err.println("Erro: nenhuma chave valida em " + KEY_ATTEMPTS + " tentativas.");
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

    static String label(PixKey.Type type) {
        return switch (type) {
            case CPF -> "CPF";
            case CNPJ -> "CNPJ";
            case EMAIL -> "e-mail";
            case PHONE -> "telefone";
            case EVP -> "chave aleatoria";
        };
    }

    static String explain(ErrorCode code) {
        return switch (code) {
            case TRUNCATED -> "o codigo termina antes do fim de um campo";
            case MALFORMED_TLV -> "um campo nao segue o formato ID, tamanho e valor";
            case INVALID_LENGTH -> "um campo passa do tamanho permitido";
            case DUPLICATE_ID -> "o mesmo campo aparece duas vezes";
            case UNKNOWN_ID -> "campo que a especificacao nao define";
            case MISPLACED_FIELD -> "campo fora da posicao obrigatoria";
            case MISSING_FIELD -> "falta um campo obrigatorio";
            case INVALID_VALUE -> "valor fora do permitido para o campo";
            case INVALID_CHARACTER -> "caractere que o campo nao aceita";
            case CRC_MISMATCH -> "o codigo foi alterado ou corrompido";
            case INVALID_KEY -> "chave Pix invalida";
            case INVALID_AMOUNT -> "valor em reais invalido";
            case INVALID_TXID -> "identificador da transacao invalido";
            case CONFLICTING_FIELDS -> "campos que nao podem aparecer juntos";
            case NOT_PIX -> "nao e um codigo Pix";
        };
    }

    static String label(BrCode.Type type) {
        return switch (type) {
            case STATIC -> "estatico";
            case DYNAMIC -> "dinamico";
            case RECURRENCE_ONLY -> "so recorrencia";
        };
    }

    private static String money(BigDecimal amount) {
        return "R$ " + amount.toPlainString().replace('.', ',');
    }

    private int generate(Map<String, String> options) {
        BrCode code;
        try {
            BrCode.StaticBuilder builder = BrCode.staticPayment()
                    .key(options.get("key"))
                    .merchantName(options.get("name"))
                    .merchantCity(options.get("city"));
            BigDecimal amount = parseAmount(options.getOrDefault("amount", ""));
            if (amount != null) {
                builder.amount(amount);
            }
            if (options.containsKey("txid")) {
                builder.txid(options.get("txid"));
            }
            if (options.containsKey("description")) {
                builder.description(options.get("description"));
            }
            code = builder.build();
        } catch (IllegalArgumentException | IllegalStateException | BrCodeException e) {
            err.println("Erro: " + e.getMessage());
            return INVALID;
        }

        PixKey key = code.key().orElseThrow();
        out.println();
        out.println("Chave:     " + key.value() + " (" + label(key.type()) + ")");
        out.println("Recebedor: " + code.merchantName() + ", " + code.merchantCity());
        out.println("Valor:     " + code.amount().map(PixCli::money).orElse(OPEN_AMOUNT));
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
            err.println("Erro: nao foi possivel gravar a imagem: " + e.getMessage());
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
            err.println("Codigo Pix invalido:");
            for (Problem problem : result.problems()) {
                err.println("  " + problem.code() + ": " + explain(problem.code())
                        + " (" + problem.message() + ", posicao " + problem.position() + ")");
            }
            return INVALID;
        }
        BrCode code = BrCode.parse(payload);
        out.println("Codigo Pix valido (" + label(code.type()) + ")");
        code.key().ifPresent(k -> out.println("Chave:          " + k.value() + " (" + label(k.type()) + ")"));
        code.location().ifPresent(l -> out.println("Endereco:       " + l));
        out.println("Recebedor:      " + code.merchantName() + ", " + code.merchantCity());
        out.println("Valor:          " + code.amount().map(PixCli::money).orElse(OPEN_AMOUNT));
        code.txid().ifPresent(t -> out.println("Identificador:  " + t));
        code.description().ifPresent(d -> out.println("Descricao:      " + d));
        out.println("CRC:            " + code.crc());
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
                    "o valor deve ser como 12,34 ou 12.34, veio \"" + text + "\"", e);
        }
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
                      Pergunta a chave, o nome, a cidade e o valor, mostra o copia e cola,
                      desenha o QR no terminal e salva pix.png.
                  java -jar pix.jar gerar --chave CHAVE --nome NOME --cidade CIDADE
                      [--valor 0,01] [--txid ID] [--descricao TEXTO]
                      [--png qr.png] [--svg qr.svg] [--sem-qr]
                  java -jar pix.jar ler CODIGO
                      Confere um copia e cola e mostra os campos.
                Os comandos e opcoes tambem aceitam os nomes em ingles
                (generate, read, --key, --name, --city, --amount, --description, --no-qr).""");
    }
}
