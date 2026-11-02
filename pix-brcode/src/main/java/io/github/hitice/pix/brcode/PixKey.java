package io.github.hitice.pix.brcode;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A Pix key (chave Pix) in the canonical format stored by the DICT.
 *
 * <p>Instances are immutable and always valid: the canonical constructor rejects any value that
 * is not already in the exact DICT format for its type. Use the static factories to accept user
 * input with formatting characters ({@code 123.456.789-09}, {@code (11) 98765-4321}, upper case
 * e-mail) and normalize it.
 *
 * <table class="striped">
 *   <caption>Canonical formats</caption>
 *   <thead><tr><th>Type</th><th>Canonical value</th></tr></thead>
 *   <tbody>
 *     <tr><td>{@link Type#CPF}</td><td>11 digits with valid check digits</td></tr>
 *     <tr><td>{@link Type#CNPJ}</td><td>12 characters {@code [0-9A-Z]} followed by 2 check digits
 *         (numeric and alphanumeric CNPJ)</td></tr>
 *     <tr><td>{@link Type#EMAIL}</td><td>lower case, at most 77 characters</td></tr>
 *     <tr><td>{@link Type#PHONE}</td><td>E.164 Brazilian mobile number: {@code +55}, area code,
 *         9 digit number starting with 9</td></tr>
 *     <tr><td>{@link Type#EVP}</td><td>random key, lower case UUID with hyphens</td></tr>
 *   </tbody>
 * </table>
 *
 * @param type the key type
 * @param value the canonical key value
 */
public record PixKey(Type type, String value) {

    /** Maximum length of a Pix key inside the merchant account information template. */
    public static final int MAX_LENGTH = 77;

    private static final Pattern CPF = Pattern.compile("\\d{11}");
    private static final Pattern CNPJ = Pattern.compile("[0-9A-Z]{12}\\d{2}");
    // Regular expression published in the DICT API specification (bacen/pix-dict-api).
    private static final Pattern EMAIL = Pattern.compile(
            "[a-z0-9.!#$&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                    + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");
    private static final Pattern PHONE = Pattern.compile("\\+55[1-9]{2}9\\d{8}");
    private static final Pattern EVP =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern EVP_ANY_CASE = Pattern.compile(EVP.pattern(), Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCUMENT_PUNCTUATION = Pattern.compile("[\\s./-]");
    private static final Pattern PHONE_PUNCTUATION = Pattern.compile("[\\s().-]");

    /** Pix key types defined by the DICT. */
    public enum Type {
        /** Individual taxpayer number (Cadastro de Pessoas Fisicas). */
        CPF,
        /** Company taxpayer number (Cadastro Nacional da Pessoa Juridica), numeric or alphanumeric. */
        CNPJ,
        /** E-mail address. */
        EMAIL,
        /** Mobile phone number. */
        PHONE,
        /** Random key (Endereco Virtual de Pagamento). */
        EVP
    }

    /**
     * Creates a key from a value that is already in canonical form.
     *
     * @param type the key type
     * @param value the canonical value
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if the value is not a canonical key of the given type
     */
    public PixKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        String problem = canonicalProblem(type, value);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
    }

    /**
     * Detects the key type and validates the key.
     *
     * <p>Detection rules, applied to the trimmed input in this order: a value containing
     * {@code @} is an e-mail; a value starting with {@code +} is a phone; a UUID is a random key;
     * after removing {@code . / -} and spaces, 11 digits are a CPF and 14 characters are a CNPJ.
     * A national phone number without {@code +55} is read as a CPF, so use {@link #phone(String)}
     * for that format.
     *
     * @param input the key as typed by a user
     * @return the validated key in canonical form
     * @throws IllegalArgumentException if the input is not a valid key of any type
     */
    public static PixKey of(String input) {
        Objects.requireNonNull(input, "input");
        String trimmed = input.strip();
        if (trimmed.indexOf('@') >= 0) {
            return email(trimmed);
        }
        if (trimmed.startsWith("+")) {
            return phone(trimmed);
        }
        if (EVP_ANY_CASE.matcher(trimmed).matches()) {
            return evp(trimmed);
        }
        String document = DOCUMENT_PUNCTUATION.matcher(trimmed).replaceAll("");
        if (document.length() == 11 && CPF.matcher(document).matches()) {
            return cpf(document);
        }
        if (document.length() == 14) {
            return cnpj(document);
        }
        throw new IllegalArgumentException("Not a Pix key: expected a CPF, CNPJ, e-mail, phone"
                + " starting with +55 or random key (UUID), got \"" + input + "\"");
    }

    /**
     * Creates a CPF key. Dots, hyphens and spaces are removed.
     *
     * @param cpf the CPF, formatted or not
     * @return the key
     * @throws IllegalArgumentException if the CPF is malformed or its check digits are wrong
     */
    public static PixKey cpf(String cpf) {
        Objects.requireNonNull(cpf, "cpf");
        return new PixKey(Type.CPF, DOCUMENT_PUNCTUATION.matcher(cpf).replaceAll(""));
    }

    /**
     * Creates a CNPJ key, numeric or alphanumeric. Dots, slashes, hyphens and spaces are removed
     * and letters are converted to upper case.
     *
     * @param cnpj the CNPJ, formatted or not
     * @return the key
     * @throws IllegalArgumentException if the CNPJ is malformed or its check digits are wrong
     */
    public static PixKey cnpj(String cnpj) {
        Objects.requireNonNull(cnpj, "cnpj");
        String value = DOCUMENT_PUNCTUATION.matcher(cnpj).replaceAll("").toUpperCase(Locale.ROOT);
        return new PixKey(Type.CNPJ, value);
    }

    /**
     * Creates an e-mail key. Surrounding spaces are removed and the address is converted to lower
     * case, as the DICT stores it.
     *
     * @param email the e-mail address
     * @return the key
     * @throws IllegalArgumentException if the address does not match the DICT e-mail format
     */
    public static PixKey email(String email) {
        Objects.requireNonNull(email, "email");
        return new PixKey(Type.EMAIL, email.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * Creates a phone key in E.164 format.
     *
     * <p>Spaces, parentheses, dots and hyphens are removed. A number without the leading
     * {@code +} is accepted with 11 digits (area code and number, {@code +55} is added) or
     * 13 digits starting with {@code 55}.
     *
     * @param phone the mobile phone number
     * @return the key
     * @throws IllegalArgumentException if the number is not a Brazilian mobile number
     */
    public static PixKey phone(String phone) {
        Objects.requireNonNull(phone, "phone");
        String digits = PHONE_PUNCTUATION.matcher(phone).replaceAll("");
        if (!digits.startsWith("+")) {
            if (digits.length() == 11) {
                digits = "+55" + digits;
            } else if (digits.length() == 13 && digits.startsWith("55")) {
                digits = "+" + digits;
            }
        }
        return new PixKey(Type.PHONE, digits);
    }

    /**
     * Creates a random key (EVP). The value is converted to lower case.
     *
     * @param evp the random key in UUID format with hyphens
     * @return the key
     * @throws IllegalArgumentException if the value is not a UUID
     */
    public static PixKey evp(String evp) {
        Objects.requireNonNull(evp, "evp");
        return new PixKey(Type.EVP, evp.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * Creates a random key (EVP) from a UUID.
     *
     * @param evp the random key
     * @return the key
     */
    public static PixKey evp(UUID evp) {
        Objects.requireNonNull(evp, "evp");
        return new PixKey(Type.EVP, evp.toString());
    }

    /**
     * Reads a key exactly as it appears in a payload, without normalization.
     *
     * @return the key, or null with the reason in {@code reason[0]}
     */
    static PixKey parseCanonical(String value, String[] reason) {
        Type type = detectCanonical(value);
        if (type == null) {
            reason[0] = "Not a Pix key in canonical DICT format: \"" + value + "\"";
            return null;
        }
        String problem = canonicalProblem(type, value);
        if (problem != null) {
            reason[0] = problem;
            return null;
        }
        return new PixKey(type, value);
    }

    private static Type detectCanonical(String value) {
        if (value.indexOf('@') >= 0) {
            return Type.EMAIL;
        }
        if (value.startsWith("+")) {
            return Type.PHONE;
        }
        if (EVP_ANY_CASE.matcher(value).matches()) {
            return Type.EVP;
        }
        if (value.length() == 11 && CPF.matcher(value).matches()) {
            return Type.CPF;
        }
        if (value.length() == 14 && value.chars().allMatch(Character::isLetterOrDigit)) {
            return Type.CNPJ;
        }
        return null;
    }

    private static String canonicalProblem(Type type, String value) {
        return switch (type) {
            case CPF -> cpfProblem(value);
            case CNPJ -> cnpjProblem(value);
            case EMAIL -> emailProblem(value);
            case PHONE -> PHONE.matcher(value).matches() ? null
                    : "Invalid phone key \"" + value + "\": expected +55, a 2 digit area code and a"
                            + " 9 digit mobile number starting with 9, for example +5561912345678";
            case EVP -> EVP.matcher(value).matches() ? null
                    : "Invalid random key \"" + value + "\": expected a lower case UUID such as"
                            + " 123e4567-e12b-12d1-a456-426655440000";
        };
    }

    private static String cpfProblem(String value) {
        if (!CPF.matcher(value).matches()) {
            return "Invalid CPF \"" + value + "\": expected 11 digits";
        }
        if (value.chars().distinct().count() == 1) {
            return "Invalid CPF \"" + value + "\": all digits are equal";
        }
        int first = cpfDigit(value, 9);
        int second = cpfDigit(value, 10);
        if (value.charAt(9) - '0' != first || value.charAt(10) - '0' != second) {
            return "Invalid CPF \"" + value + "\": check digits should be " + first + second;
        }
        return null;
    }

    private static int cpfDigit(String value, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (value.charAt(i) - '0') * (length + 1 - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static String cnpjProblem(String value) {
        if (!CNPJ.matcher(value).matches()) {
            return "Invalid CNPJ \"" + value + "\": expected 12 characters 0-9 or A-Z followed by"
                    + " 2 check digits";
        }
        if (value.chars().allMatch(c -> c == '0')) {
            return "Invalid CNPJ \"" + value + "\": all digits are zero";
        }
        int first = cnpjDigit(value, 12);
        int second = cnpjDigit(value, 13);
        if (value.charAt(12) - '0' != first || value.charAt(13) - '0' != second) {
            return "Invalid CNPJ \"" + value + "\": check digits should be " + first + second;
        }
        return null;
    }

    /**
     * Modulo 11 check digit of the first {@code length} characters, each character valued as its
     * ASCII code minus 48 (Receita Federal, alphanumeric CNPJ; identical to the numeric rule for
     * digits).
     */
    private static int cnpjDigit(String value, int length) {
        int sum = 0;
        int weight = 2;
        for (int i = length - 1; i >= 0; i--) {
            sum += (value.charAt(i) - '0') * weight;
            weight = weight == 9 ? 2 : weight + 1;
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static String emailProblem(String value) {
        if (value.length() > MAX_LENGTH) {
            return "Invalid e-mail key: " + value.length() + " characters, the maximum is " + MAX_LENGTH;
        }
        if (!value.equals(value.toLowerCase(Locale.ROOT))) {
            return "Invalid e-mail key \"" + value + "\": the DICT stores e-mail keys in lower case";
        }
        if (!EMAIL.matcher(value).matches()) {
            return "Invalid e-mail key \"" + value + "\": does not match the DICT e-mail format";
        }
        return null;
    }
}
