package io.github.hitice.pix.brcode.cli;

import io.github.hitice.pix.brcode.BrCode;
import io.github.hitice.pix.brcode.ErrorCode;
import io.github.hitice.pix.brcode.PixKey;
import io.github.hitice.pix.brcode.Problem;
import io.github.hitice.pix.brcode.ValidationResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns what a person typed into a Pix code, and a pasted code into lines a person can read.
 * Shared by the terminal and the window, so both accept and explain exactly the same things.
 */
final class PixForm {

    static final String OPEN_AMOUNT = "aberto, quem paga digita";
    /**
     * Name and city in a static code are informative: the payer's bank looks the key up in the
     * DICT and shows the registered owner before confirming. So both fall back to these values.
     */
    static final String DEFAULT_NAME = "Recebedor Pix";
    static final String DEFAULT_CITY = "Brasil";
    static final String PAYER_NOTE =
            "Na hora de pagar, o banco de quem paga mostra o nome cadastrado da chave.";

    private PixForm() {
    }

    /**
     * Builds a static code. Blank optional fields are left out; blank name and city use the
     * defaults.
     *
     * @throws IllegalArgumentException, IllegalStateException or BrCodeException with a message
     *     for the person, when a field is invalid
     */
    static BrCode build(String key, String amount, String name, String city, String txid,
            String description) {
        BrCode.StaticBuilder builder = BrCode.staticPayment()
                .key(key.strip())
                .merchantName(blank(name) ? DEFAULT_NAME : name.strip())
                .merchantCity(blank(city) ? DEFAULT_CITY : city.strip());
        BigDecimal value = parseAmount(amount == null ? "" : amount);
        if (value != null) {
            builder.amount(value);
        }
        if (!blank(txid)) {
            builder.txid(txid.strip());
        }
        if (!blank(description)) {
            builder.description(description.strip());
        }
        return builder.build();
    }

    /** Fields of a valid code, one per line, in Portuguese. */
    static List<String> describe(BrCode code) {
        List<String> lines = new ArrayList<>();
        code.key().ifPresent(k -> lines.add("Chave:          " + k.value() + " (" + label(k.type()) + ")"));
        code.location().ifPresent(l -> lines.add("Endere\u00e7o:       " + l));
        lines.add("Recebedor:      " + code.merchantName() + ", " + code.merchantCity());
        lines.add("Valor:          " + code.amount().map(PixForm::money).orElse(OPEN_AMOUNT));
        code.txid().ifPresent(t -> lines.add("Identificador:  " + t));
        code.description().ifPresent(d -> lines.add("Descri\u00e7\u00e3o:      " + d));
        lines.add("CRC:            " + code.crc());
        return lines;
    }

    /** One line per problem, explained in Portuguese, or an empty list for a valid code. */
    static List<String> problems(String payload) {
        ValidationResult result = BrCode.validate(payload.strip());
        List<String> lines = new ArrayList<>();
        for (Problem problem : result.problems()) {
            lines.add(problem.code() + ": " + explain(problem.code())
                    + " (" + problem.message() + ", posi\u00e7\u00e3o " + problem.position() + ")");
        }
        return lines;
    }

    /** Recognizes a key as it is typed: its type, or why it is not a key yet. */
    static String keyStatus(String key) {
        if (blank(key)) {
            return "";
        }
        try {
            PixKey parsed = PixKey.of(key.strip());
            return "Reconhecida como " + label(parsed.type()) + ": " + parsed.value();
        } catch (IllegalArgumentException e) {
            return "Chave inv\u00e1lida: " + e.getMessage();
        }
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

    static String money(BigDecimal amount) {
        return "R$ " + amount.toPlainString().replace('.', ',');
    }

    static String label(PixKey.Type type) {
        return switch (type) {
            case CPF -> "CPF";
            case CNPJ -> "CNPJ";
            case EMAIL -> "e-mail";
            case PHONE -> "telefone";
            case EVP -> "chave aleat\u00f3ria";
        };
    }

    static String label(BrCode.Type type) {
        return switch (type) {
            case STATIC -> "est\u00e1tico";
            case DYNAMIC -> "din\u00e2mico";
            case RECURRENCE_ONLY -> "s\u00f3 recorr\u00eancia";
        };
    }

    static String explain(ErrorCode code) {
        return switch (code) {
            case TRUNCATED -> "o c\u00f3digo termina antes do fim de um campo";
            case MALFORMED_TLV -> "um campo n\u00e3o segue o formato ID, tamanho e valor";
            case INVALID_LENGTH -> "um campo passa do tamanho permitido";
            case DUPLICATE_ID -> "o mesmo campo aparece duas vezes";
            case UNKNOWN_ID -> "campo que a especifica\u00e7\u00e3o n\u00e3o define";
            case MISPLACED_FIELD -> "campo fora da posi\u00e7\u00e3o obrigat\u00f3ria";
            case MISSING_FIELD -> "falta um campo obrigat\u00f3rio";
            case INVALID_VALUE -> "valor fora do permitido para o campo";
            case INVALID_CHARACTER -> "caractere que o campo n\u00e3o aceita";
            case CRC_MISMATCH -> "o c\u00f3digo foi alterado ou corrompido";
            case INVALID_KEY -> "chave Pix inv\u00e1lida";
            case INVALID_AMOUNT -> "valor em reais inv\u00e1lido";
            case INVALID_TXID -> "identificador da transa\u00e7\u00e3o inv\u00e1lido";
            case CONFLICTING_FIELDS -> "campos que n\u00e3o podem aparecer juntos";
            case NOT_PIX -> "n\u00e3o \u00e9 um c\u00f3digo Pix";
        };
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
