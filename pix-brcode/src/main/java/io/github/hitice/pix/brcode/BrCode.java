package io.github.hitice.pix.brcode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A Pix payment QR code payload (BR Code), as defined by the Banco Central do Brasil on top of the
 * EMV QRCPS Merchant-Presented Mode.
 *
 * <p>Instances are immutable and always valid. Create one with {@link #staticPayment()} or
 * {@link #dynamicPayment()}, or read one with {@link #parse(String)}. The {@link #payload()} is
 * the "Pix Copia e Cola" text, which is also the content to encode in the QR image.
 *
 * <pre>{@code
 * BrCode code = BrCode.staticPayment()
 *         .key("+5561912345678")
 *         .merchantName("Fulano de Tal")
 *         .merchantCity("Brasilia")
 *         .amount(new BigDecimal("12.34"))
 *         .txid("PEDIDO123")
 *         .build();
 * String copiaECola = code.payload();
 * }</pre>
 *
 * <p>Two codes are equal when their payloads are equal. A parsed code keeps the exact payload it
 * was read from.
 */
public final class BrCode {

    /** Globally unique identifier of the Pix arrangement, written in lower case. */
    public static final String GUI = "br.gov.bcb.pix";
    /** Maximum characters of the merchant name (field 59). */
    public static final int MAX_MERCHANT_NAME = 25;
    /** Maximum characters of the merchant city (field 60). */
    public static final int MAX_MERCHANT_CITY = 15;
    /** Maximum characters of the postal code (field 61), as defined by EMV. */
    public static final int MAX_POSTAL_CODE = 10;
    /** Maximum characters of the transaction amount (field 54). */
    public static final int MAX_AMOUNT_LENGTH = 13;
    /** Maximum characters of the transaction ID (field 62-05). */
    public static final int MAX_TXID = 25;
    /** Maximum characters of the description (field 26-02), before the template limit applies. */
    public static final int MAX_DESCRIPTION = 72;
    /** Maximum characters of a location URL without scheme (fields 26-25 and 80-25). */
    public static final int MAX_LOCATION = 77;

    private static final int MAX_TEMPLATE = 99;
    private static final Pattern STATIC_TXID = Pattern.compile("[A-Za-z0-9]{1,25}");
    private static final Pattern CEP = Pattern.compile("\\d{5}-?\\d{3}");
    private static final Pattern FOUR_DIGITS = Pattern.compile("\\d{4}");
    private static final Pattern ISPB = Pattern.compile("\\d{8}");
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://.*");

    /** Kind of Pix code, given by the content of the Pix merchant account template. */
    public enum Type {
        /** Carries the Pix key; all payment data is in the payload. */
        STATIC,
        /** Carries a location URL that the payer's bank reads to get the charge. */
        DYNAMIC,
        /**
         * Composite code that carries only a recurrence location (Pix Automatico), with neither a
         * key nor a payment location. Parsed only; there is no builder for it.
         */
        RECURRENCE_ONLY
    }

    /** Point of initiation method (field 01). */
    public enum InitiationMethod {
        /** {@code 11}: the same code is shown for more than one payment. */
        REUSABLE("11"),
        /** {@code 12}: the code is meant for a single payment. */
        SINGLE_USE("12");

        private final String code;

        InitiationMethod(String code) {
            this.code = code;
        }

        /**
         * Returns the two digit value written in the payload.
         *
         * @return {@code "11"} or {@code "12"}
         */
        public String code() {
            return code;
        }

        static InitiationMethod fromCode(String code) {
            for (InitiationMethod m : values()) {
                if (m.code.equals(code)) {
                    return m;
                }
            }
            return null;
        }
    }

    private final String payload;
    private final Type type;
    private final InitiationMethod initiationMethod;
    private final PixKey key;
    private final String location;
    private final String description;
    private final String withdrawalFacilitator;
    private final String merchantCategoryCode;
    private final BigDecimal amount;
    private final String merchantName;
    private final String merchantCity;
    private final String postalCode;
    private final String txid;
    private final String recurrenceLocation;
    private final String crc;

    BrCode(String payload, Type type, InitiationMethod initiationMethod, PixKey key, String location,
            String description, String withdrawalFacilitator, String merchantCategoryCode,
            BigDecimal amount, String merchantName, String merchantCity, String postalCode, String txid,
            String recurrenceLocation, String crc) {
        this.payload = payload;
        this.type = type;
        this.initiationMethod = initiationMethod;
        this.key = key;
        this.location = location;
        this.description = description;
        this.withdrawalFacilitator = withdrawalFacilitator;
        this.merchantCategoryCode = merchantCategoryCode;
        this.amount = amount;
        this.merchantName = merchantName;
        this.merchantCity = merchantCity;
        this.postalCode = postalCode;
        this.txid = txid;
        this.recurrenceLocation = recurrenceLocation;
        this.crc = crc;
    }

    // ---------------------------------------------------------------- factories

    /**
     * Starts a static code: the payload carries the Pix key and, optionally, the amount,
     * transaction ID and description.
     *
     * @return a new builder
     */
    public static StaticBuilder staticPayment() {
        return new StaticBuilder();
    }

    /**
     * Starts a dynamic code: the payload carries the location (URL without scheme) where the
     * receiving PSP serves the charge. Amount and transaction ID come from that charge, so the
     * builder does not offer them. The point of initiation method defaults to
     * {@link InitiationMethod#SINGLE_USE}, as in the example of the Pix manual.
     *
     * @return a new builder
     */
    public static DynamicBuilder dynamicPayment() {
        return new DynamicBuilder();
    }

    /**
     * Reads and validates a payload.
     *
     * @param payload the "Pix Copia e Cola" text, exactly as read from the QR code
     * @return the decoded code
     * @throws BrCodeException with the first problem found, if the payload is not a valid Pix BR
     *     Code
     */
    public static BrCode parse(String payload) {
        ValidationResult result = validate(payload);
        return result.brCode().orElseThrow(() -> new BrCodeException(result.problems().get(0)));
    }

    /**
     * Validates a payload and reports every problem found, without throwing.
     *
     * @param payload the "Pix Copia e Cola" text
     * @return the problems found and, when there is none, the decoded code
     */
    public static ValidationResult validate(String payload) {
        Objects.requireNonNull(payload, "payload");
        return Parser.validate(payload);
    }

    // ---------------------------------------------------------------- accessors

    /**
     * Returns the payload, the text to encode in the QR image and to offer as "Pix Copia e Cola".
     *
     * @return the payload, including the CRC
     */
    public String payload() {
        return payload;
    }

    /**
     * Returns the kind of code.
     *
     * @return static, dynamic or recurrence only
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the point of initiation method (field 01).
     *
     * @return the method, or empty when the field is absent
     */
    public Optional<InitiationMethod> initiationMethod() {
        return Optional.ofNullable(initiationMethod);
    }

    /**
     * Returns the Pix key (field 26-01).
     *
     * @return the key of a static code, otherwise empty
     */
    public Optional<PixKey> key() {
        return Optional.ofNullable(key);
    }

    /**
     * Returns the location of the charge (field 26-25), a URL without scheme.
     *
     * @return the location of a dynamic code, otherwise empty
     */
    public Optional<String> location() {
        return Optional.ofNullable(location);
    }

    /**
     * Returns the free text shown to the payer (field 26-02, infoAdicional).
     *
     * @return the description, or empty
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the ISPB of the withdrawal service facilitator (field 26-03, fss), which marks a
     * Pix Saque code.
     *
     * @return the 8 digit ISPB, or empty
     */
    public Optional<String> withdrawalFacilitator() {
        return Optional.ofNullable(withdrawalFacilitator);
    }

    /**
     * Returns the merchant category code (field 52).
     *
     * @return four digits, {@code "0000"} when not informed
     */
    public String merchantCategoryCode() {
        return merchantCategoryCode;
    }

    /**
     * Returns the transaction amount (field 54) in reais, with scale 2. For a dynamic code the
     * payer must ignore it and use the amount of the charge.
     *
     * @return the amount, or empty when the payer types it
     */
    public Optional<BigDecimal> amount() {
        return Optional.ofNullable(amount);
    }

    /**
     * Returns the merchant name (field 59). Payer apps show the name registered in the DICT, not
     * this one.
     *
     * @return the name, at most 25 characters
     */
    public String merchantName() {
        return merchantName;
    }

    /**
     * Returns the merchant city (field 60).
     *
     * @return the city, at most 15 characters
     */
    public String merchantCity() {
        return merchantCity;
    }

    /**
     * Returns the postal code (field 61).
     *
     * @return the postal code, or empty
     */
    public Optional<String> postalCode() {
        return Optional.ofNullable(postalCode);
    }

    /**
     * Returns the transaction ID (field 62-05, Reference Label).
     *
     * @return the transaction ID, or empty when the payload has {@code ***}
     */
    public Optional<String> txid() {
        return Optional.ofNullable(txid);
    }

    /**
     * Returns the location of the recurrence parameters of a composite code (field 80-25,
     * Pix Automatico), a URL without scheme.
     *
     * @return the recurrence location, or empty
     */
    public Optional<String> recurrenceLocation() {
        return Optional.ofNullable(recurrenceLocation);
    }

    /**
     * Returns the CRC (field 63).
     *
     * @return four upper case hexadecimal digits
     */
    public String crc() {
        return crc;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BrCode other && payload.equals(other.payload);
    }

    @Override
    public int hashCode() {
        return payload.hashCode();
    }

    /**
     * Returns the payload.
     *
     * @return the same as {@link #payload()}
     */
    @Override
    public String toString() {
        return payload;
    }

    // ---------------------------------------------------------------- shared rules

    /** Returns why the location is invalid, or null. */
    static String locationProblem(String label, String value) {
        if (value.isEmpty()) {
            return label + " must not be empty";
        }
        if (SCHEME.matcher(value).matches()) {
            return label + " must not include the scheme (such as https://): \"" + value + "\"";
        }
        if (Text.length(value) > MAX_LOCATION) {
            return label + " has " + Text.length(value) + " characters, the maximum is " + MAX_LOCATION;
        }
        if (!Text.isAns(value) || value.indexOf(' ') >= 0) {
            return label + " may only contain printable ASCII characters without spaces: \""
                    + value + "\"";
        }
        return null;
    }

    private static String tlv(String id, String value) {
        int length = Text.length(value);
        if (length > MAX_TEMPLATE) {
            throw new IllegalStateException("Field " + id + " would have " + length
                    + " characters, the maximum is 99");
        }
        return id + (length < 10 ? "0" : "") + length + value;
    }

    // ---------------------------------------------------------------- builders

    /**
     * Fields shared by static and dynamic codes.
     *
     * @param <B> the concrete builder type
     */
    public abstract static sealed class Builder<B extends Builder<B>> permits StaticBuilder, DynamicBuilder {

        String merchantName;
        String merchantCity;
        String postalCode;
        String merchantCategoryCode = "0000";
        InitiationMethod initiationMethod;
        String recurrenceLocation;

        Builder() {
        }

        @SuppressWarnings("unchecked")
        private B self() {
            return (B) this;
        }

        /**
         * Sets the merchant name (field 59), mandatory. Accents are removed ("Jos&eacute;" becomes
         * "Jose") and case is kept.
         *
         * @param merchantName the name, at most 25 characters after accents are removed
         * @return this builder
         * @throws IllegalArgumentException if the name is blank, too long, or has a character
         *     outside printable ASCII after accents are removed
         */
        public B merchantName(String merchantName) {
            Objects.requireNonNull(merchantName, "merchantName");
            this.merchantName = Text.toAns("merchantName", merchantName, MAX_MERCHANT_NAME);
            return self();
        }

        /**
         * Sets the merchant city (field 60), mandatory. Accents are removed and case is kept.
         *
         * @param merchantCity the city, at most 15 characters after accents are removed
         * @return this builder
         * @throws IllegalArgumentException if the city is blank, too long, or has a character
         *     outside printable ASCII after accents are removed
         */
        public B merchantCity(String merchantCity) {
            Objects.requireNonNull(merchantCity, "merchantCity");
            this.merchantCity = Text.toAns("merchantCity", merchantCity, MAX_MERCHANT_CITY);
            return self();
        }

        /**
         * Sets the postal code (field 61, CEP), optional.
         *
         * @param postalCode 8 digits, with or without the hyphen ({@code 70074-900})
         * @return this builder
         * @throws IllegalArgumentException if the value is not a CEP
         */
        public B postalCode(String postalCode) {
            Objects.requireNonNull(postalCode, "postalCode");
            String value = postalCode.strip();
            if (!CEP.matcher(value).matches()) {
                throw new IllegalArgumentException("postalCode must be a CEP with 8 digits, got \""
                        + postalCode + "\"");
            }
            this.postalCode = value.replace("-", "");
            return self();
        }

        /**
         * Sets the merchant category code (field 52). Defaults to {@code "0000"} (not informed).
         *
         * @param merchantCategoryCode an ISO 18245 code with 4 digits
         * @return this builder
         * @throws IllegalArgumentException if the value is not 4 digits
         */
        public B merchantCategoryCode(String merchantCategoryCode) {
            Objects.requireNonNull(merchantCategoryCode, "merchantCategoryCode");
            if (!FOUR_DIGITS.matcher(merchantCategoryCode).matches()) {
                throw new IllegalArgumentException("merchantCategoryCode must have 4 digits, got \""
                        + merchantCategoryCode + "\"");
            }
            this.merchantCategoryCode = merchantCategoryCode;
            return self();
        }

        /**
         * Sets the point of initiation method (field 01).
         *
         * @param initiationMethod reusable ({@code 11}) or single use ({@code 12})
         * @return this builder
         */
        public B initiationMethod(InitiationMethod initiationMethod) {
            this.initiationMethod = Objects.requireNonNull(initiationMethod, "initiationMethod");
            return self();
        }

        /**
         * Adds the location of recurrence parameters (template 80, Pix Automatico), which turns the
         * code into a composite code.
         *
         * @param recurrenceLocation URL without scheme, at most 77 characters, for example
         *     {@code pix.example.com/rec/2353c790eefb11eaadc10242ac120002}
         * @return this builder
         * @throws IllegalArgumentException if the location has a scheme, spaces or is too long
         */
        public B recurrenceLocation(String recurrenceLocation) {
            Objects.requireNonNull(recurrenceLocation, "recurrenceLocation");
            String value = recurrenceLocation.strip();
            String problem = locationProblem("recurrenceLocation", value);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }
            this.recurrenceLocation = value;
            return self();
        }

        abstract Type type();

        abstract String accountTemplate();

        abstract BigDecimal amountValue();

        abstract String txidValue();

        abstract PixKey keyValue();

        abstract String locationValue();

        abstract String descriptionValue();

        abstract String withdrawalFacilitatorValue();

        /**
         * Builds the code and computes its payload and CRC.
         *
         * @return the code
         * @throws IllegalStateException if a mandatory field is missing, or the Pix template
         *     (key, description and facilitator together) exceeds 99 characters
         */
        public BrCode build() {
            if (merchantName == null) {
                throw new IllegalStateException("merchantName is mandatory");
            }
            if (merchantCity == null) {
                throw new IllegalStateException("merchantCity is mandatory");
            }
            String account = accountTemplate();
            if (account.length() > MAX_TEMPLATE) {
                throw new IllegalStateException("The Pix merchant account template would have "
                        + account.length() + " characters, the maximum is 99; shorten the"
                        + " description (key, description and facilitator share this space)");
            }
            BigDecimal amount = amountValue();
            String txid = txidValue();
            StringBuilder sb = new StringBuilder(160);
            sb.append(tlv("00", "01"));
            if (initiationMethod != null) {
                sb.append(tlv("01", initiationMethod.code()));
            }
            sb.append(tlv("26", account));
            sb.append(tlv("52", merchantCategoryCode));
            sb.append(tlv("53", "986"));
            if (amount != null) {
                sb.append(tlv("54", amount.toPlainString()));
            }
            sb.append(tlv("58", "BR"));
            sb.append(tlv("59", merchantName));
            sb.append(tlv("60", merchantCity));
            if (postalCode != null) {
                sb.append(tlv("61", postalCode));
            }
            sb.append(tlv("62", tlv("05", txid == null ? "***" : txid)));
            if (recurrenceLocation != null) {
                sb.append(tlv("80", tlv("00", GUI) + tlv("25", recurrenceLocation)));
            }
            sb.append("6304");
            String crc = Crc16.hex(sb.toString());
            sb.append(crc);
            return new BrCode(sb.toString(), type(), initiationMethod, keyValue(), locationValue(),
                    descriptionValue(), withdrawalFacilitatorValue(), merchantCategoryCode, amount,
                    merchantName, merchantCity, postalCode, txid, recurrenceLocation, crc);
        }
    }

    /** Builder of static codes. Mandatory: key, merchant name and merchant city. */
    public static final class StaticBuilder extends Builder<StaticBuilder> {

        private PixKey key;
        private BigDecimal amount;
        private String txid;
        private String description;
        private String withdrawalFacilitator;

        StaticBuilder() {
        }

        /**
         * Sets the Pix key (field 26-01), mandatory.
         *
         * @param key the key
         * @return this builder
         */
        public StaticBuilder key(PixKey key) {
            this.key = Objects.requireNonNull(key, "key");
            return this;
        }

        /**
         * Sets the Pix key from text, detecting its type with {@link PixKey#of(String)}.
         *
         * @param key the key as typed by a user
         * @return this builder
         * @throws IllegalArgumentException if the text is not a valid Pix key
         */
        public StaticBuilder key(String key) {
            return key(PixKey.of(key));
        }

        /**
         * Sets the transaction amount (field 54). Without it the payer types the amount.
         *
         * @param amount a positive amount in reais with at most 2 decimal places, at most
         *     {@code 9999999999.99}
         * @return this builder
         * @throws IllegalArgumentException if the amount is zero, negative, has more than 2
         *     decimal places or more than 13 characters
         */
        public StaticBuilder amount(BigDecimal amount) {
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("amount must be greater than zero, got "
                        + amount.toPlainString());
            }
            BigDecimal scaled;
            try {
                scaled = amount.setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("amount must have at most 2 decimal places, got "
                        + amount.toPlainString(), e);
            }
            if (scaled.toPlainString().length() > MAX_AMOUNT_LENGTH) {
                throw new IllegalArgumentException("amount must have at most 13 characters"
                        + " (max 9999999999.99), got " + scaled.toPlainString());
            }
            this.amount = scaled;
            return this;
        }

        /**
         * Sets the transaction ID (field 62-05) that the receiving PSP uses for reconciliation.
         * Without it the payload carries {@code ***}.
         *
         * @param txid 1 to 25 characters A-Z, a-z, 0-9; {@code ***} means none
         * @return this builder
         * @throws IllegalArgumentException if the value has other characters or is too long
         */
        public StaticBuilder txid(String txid) {
            Objects.requireNonNull(txid, "txid");
            if (txid.equals("***")) {
                this.txid = null;
                return this;
            }
            if (!STATIC_TXID.matcher(txid).matches()) {
                throw new IllegalArgumentException("txid must have 1 to 25 characters A-Z, a-z or"
                        + " 0-9, got \"" + txid + "\"");
            }
            this.txid = txid;
            return this;
        }

        /**
         * Sets the free text shown to the payer (field 26-02, infoAdicional). Accents are removed.
         * The key, the description and the facilitator share the 99 characters of the Pix
         * template, so the real limit depends on the key length; {@link #build()} checks it.
         *
         * @param description the text, at most 72 characters after accents are removed
         * @return this builder
         * @throws IllegalArgumentException if the text is blank, too long, or has a character
         *     outside printable ASCII after accents are removed
         */
        public StaticBuilder description(String description) {
            Objects.requireNonNull(description, "description");
            this.description = Text.toAns("description", description, MAX_DESCRIPTION);
            return this;
        }

        /**
         * Sets the ISPB of the withdrawal service facilitator (field 26-03, fss), which marks the
         * code as a Pix Saque code.
         *
         * @param ispb the 8 digit ISPB of the facilitator
         * @return this builder
         * @throws IllegalArgumentException if the value is not 8 digits
         */
        public StaticBuilder withdrawalFacilitator(String ispb) {
            Objects.requireNonNull(ispb, "ispb");
            if (!ISPB.matcher(ispb).matches()) {
                throw new IllegalArgumentException("withdrawalFacilitator must be an 8 digit ISPB,"
                        + " got \"" + ispb + "\"");
            }
            this.withdrawalFacilitator = ispb;
            return this;
        }

        @Override
        Type type() {
            return Type.STATIC;
        }

        @Override
        String accountTemplate() {
            if (key == null) {
                throw new IllegalStateException("key is mandatory for a static code");
            }
            return tlv("00", GUI) + tlv("01", key.value())
                    + (description == null ? "" : tlv("02", description))
                    + (withdrawalFacilitator == null ? "" : tlv("03", withdrawalFacilitator));
        }

        @Override
        BigDecimal amountValue() {
            return amount;
        }

        @Override
        String txidValue() {
            return txid;
        }

        @Override
        PixKey keyValue() {
            return key;
        }

        @Override
        String locationValue() {
            return null;
        }

        @Override
        String descriptionValue() {
            return description;
        }

        @Override
        String withdrawalFacilitatorValue() {
            return withdrawalFacilitator;
        }
    }

    /** Builder of dynamic codes. Mandatory: location, merchant name and merchant city. */
    public static final class DynamicBuilder extends Builder<DynamicBuilder> {

        private String location;

        DynamicBuilder() {
            initiationMethod = InitiationMethod.SINGLE_USE;
        }

        /**
         * Sets the location of the charge (field 26-25), mandatory: the URL served by the receiving
         * PSP, without the scheme.
         *
         * @param location for example {@code pix.example.com/qr/v2/9d36b84fc70b478fb95c12729b90ca25}
         * @return this builder
         * @throws IllegalArgumentException if the location has a scheme, spaces or more than 77
         *     characters
         */
        public DynamicBuilder location(String location) {
            Objects.requireNonNull(location, "location");
            String value = location.strip();
            String problem = locationProblem("location", value);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }
            this.location = value;
            return this;
        }

        @Override
        Type type() {
            return Type.DYNAMIC;
        }

        @Override
        String accountTemplate() {
            if (location == null) {
                throw new IllegalStateException("location is mandatory for a dynamic code");
            }
            return tlv("00", GUI) + tlv("25", location);
        }

        @Override
        BigDecimal amountValue() {
            return null;
        }

        @Override
        String txidValue() {
            return null;
        }

        @Override
        PixKey keyValue() {
            return null;
        }

        @Override
        String locationValue() {
            return location;
        }

        @Override
        String descriptionValue() {
            return null;
        }

        @Override
        String withdrawalFacilitatorValue() {
            return null;
        }
    }
}
