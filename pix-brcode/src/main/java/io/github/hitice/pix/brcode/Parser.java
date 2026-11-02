package io.github.hitice.pix.brcode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads a payload into a {@link BrCode}, collecting every problem it finds. */
final class Parser {

    private static final Pattern CRC_VALUE = Pattern.compile("[0-9A-F]{4}");
    private static final Pattern AMOUNT = Pattern.compile("(\\d+)(?:\\.(\\d*))?");
    private static final Pattern STATIC_TXID = Pattern.compile("[A-Za-z0-9]{1,25}");
    private static final Pattern FOUR_DIGITS = Pattern.compile("\\d{4}");
    private static final Pattern ISPB = Pattern.compile("\\d{8}");

    /** One ID, length, value data object. Positions are code point indexes in the payload. */
    private record Tlv(String id, String value, int start, int valueStart) {
        int number() {
            return Integer.parseInt(id);
        }
    }

    private final String payload;
    private final int[] cps;
    private final List<Problem> structural = new ArrayList<>();
    private final List<Problem> crc = new ArrayList<>();
    private final List<Problem> fields = new ArrayList<>();
    private boolean pixUnreservedSeen;

    private Parser(String payload) {
        this.payload = payload;
        this.cps = payload.codePoints().toArray();
    }

    static ValidationResult validate(String payload) {
        return new Parser(payload).run();
    }

    private ValidationResult run() {
        if (cps.length == 0) {
            structural.add(new Problem(ErrorCode.TRUNCATED, 0, "Payload is empty"));
            return result(null);
        }
        List<Tlv> root = tokenize(0, cps.length, "root", structural);
        if (root == null) {
            checkCrcFromTail();
            return result(null);
        }
        checkCrc(root);
        BrCode code = readFields(root);
        return result(code);
    }

    private ValidationResult result(BrCode code) {
        List<Problem> all = new ArrayList<>(structural.size() + crc.size() + fields.size());
        all.addAll(structural);
        all.addAll(crc);
        fields.sort((a, b) -> Integer.compare(a.position(), b.position()));
        all.addAll(fields);
        return new ValidationResult(all, all.isEmpty() ? code : null);
    }

    // ---------------------------------------------------------------- TLV

    /**
     * Splits {@code [from, to)} into data objects. Returns null if the structure is broken, after
     * adding the problem to {@code sink}.
     */
    private List<Tlv> tokenize(int from, int to, String context, List<Problem> sink) {
        List<Tlv> out = new ArrayList<>();
        int pos = from;
        while (pos < to) {
            if (to - pos < 4) {
                sink.add(new Problem(ErrorCode.TRUNCATED, pos, "Incomplete data object in " + context
                        + ": " + (to - pos) + " characters left, an ID and a length need 4"));
                return null;
            }
            String id = text(pos, pos + 2);
            if (!isTwoDigits(id)) {
                sink.add(new Problem(ErrorCode.MALFORMED_TLV, pos,
                        "Expected a two digit ID in " + context + ", found \"" + id + "\""));
                return null;
            }
            String len = text(pos + 2, pos + 4);
            if (!isTwoDigits(len)) {
                sink.add(new Problem(ErrorCode.MALFORMED_TLV, pos + 2,
                        "Expected a two digit length for ID " + id + " in " + context
                                + ", found \"" + len + "\""));
                return null;
            }
            int length = Integer.parseInt(len);
            if (length == 0) {
                sink.add(new Problem(ErrorCode.INVALID_LENGTH, pos + 2,
                        "ID " + id + " in " + context + " has length 00; lengths go from 01 to 99"));
                return null;
            }
            int valueStart = pos + 4;
            if (valueStart + length > to) {
                sink.add(new Problem(ErrorCode.TRUNCATED, pos + 2, "ID " + id + " in " + context
                        + " declares " + length + " characters but only " + (to - valueStart)
                        + " remain"));
                return null;
            }
            out.add(new Tlv(id, text(valueStart, valueStart + length), pos, valueStart));
            pos = valueStart + length;
        }
        return out;
    }

    private static boolean isTwoDigits(String s) {
        return s.length() == 2 && isDigit(s.charAt(0)) && isDigit(s.charAt(1));
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private String text(int from, int to) {
        return new String(cps, from, to - from);
    }

    // ---------------------------------------------------------------- CRC

    private void checkCrc(List<Tlv> root) {
        Tlv last = root.get(root.size() - 1);
        for (int i = 0; i < root.size() - 1; i++) {
            if (root.get(i).id().equals("63")) {
                crc.add(new Problem(ErrorCode.MISPLACED_FIELD, root.get(i).start(),
                        "The CRC (63) must be the last field"));
                return;
            }
        }
        if (!last.id().equals("63")) {
            crc.add(new Problem(ErrorCode.MISSING_FIELD, cps.length,
                    "Missing CRC: the payload must end with 6304 and 4 hexadecimal digits"));
            return;
        }
        if (last.value().length() != 4) {
            crc.add(new Problem(ErrorCode.INVALID_LENGTH, last.start() + 2,
                    "The CRC (63) must have length 04, found " + last.value().length()));
            return;
        }
        compareCrc(last.valueStart(), last.value());
    }

    /** Used when the structure is broken: still report a CRC mismatch if the tail looks like one. */
    private void checkCrcFromTail() {
        int n = cps.length;
        if (n >= 8 && text(n - 8, n - 4).equals("6304")) {
            compareCrc(n - 4, text(n - 4, n));
        }
    }

    private void compareCrc(int valueStart, String found) {
        if (!CRC_VALUE.matcher(found).matches()) {
            crc.add(new Problem(ErrorCode.INVALID_VALUE, valueStart,
                    "The CRC must be 4 upper case hexadecimal digits, found \"" + found + "\""));
            return;
        }
        String covered = text(0, valueStart);
        String expected = Crc16.hex(covered);
        if (!expected.equals(found)) {
            crc.add(new Problem(ErrorCode.CRC_MISMATCH, valueStart,
                    "CRC mismatch: expected " + expected + ", found " + found));
        }
    }

    // ---------------------------------------------------------------- fields

    private BrCode readFields(List<Tlv> root) {
        Map<String, Tlv> byId = new HashMap<>();
        for (Tlv t : root) {
            if (byId.putIfAbsent(t.id(), t) != null) {
                fields.add(new Problem(ErrorCode.DUPLICATE_ID, t.start(),
                        "ID " + t.id() + " appears more than once under the root"));
            }
        }

        Tlv first = root.get(0);
        if (!first.id().equals("00")) {
            Tlv pfi = byId.get("00");
            if (pfi != null) {
                fields.add(new Problem(ErrorCode.MISPLACED_FIELD, pfi.start(),
                        "The payload format indicator (00) must be the first field"));
            } else {
                fields.add(new Problem(ErrorCode.MISSING_FIELD, 0,
                        "Missing payload format indicator (00)"));
            }
        }
        Tlv pfi = byId.get("00");
        if (pfi != null && !pfi.value().equals("01")) {
            invalid(pfi, "Payload format indicator (00) must be \"01\", found \"" + pfi.value() + "\"");
        }

        BrCode.InitiationMethod initiation = null;
        Tlv poi = byId.get("01");
        if (poi != null) {
            initiation = BrCode.InitiationMethod.fromCode(poi.value());
            if (initiation == null) {
                invalid(poi, "Point of initiation method (01) must be \"11\" or \"12\", found \""
                        + poi.value() + "\"");
            }
        }

        PixTemplate pix = null;
        boolean anyAccount = false;
        String recurrence = null;
        for (Tlv t : root) {
            int n = t.number();
            if (n >= 2 && n <= 51) {
                anyAccount = true;
            }
            if (n >= 26 && n <= 51) {
                PixTemplate candidate = readAccountTemplate(t);
                if (candidate != null) {
                    if (pix != null) {
                        fields.add(new Problem(ErrorCode.CONFLICTING_FIELDS, t.start(),
                                "More than one merchant account template carries the Pix GUI"));
                    } else {
                        pix = candidate;
                    }
                }
            } else if (n >= 65 && n <= 79) {
                fields.add(new Problem(ErrorCode.UNKNOWN_ID, t.start(),
                        "ID " + t.id() + " is reserved for future use by EMVCo"));
            } else if (n >= 80) {
                String url = readUnreservedTemplate(t);
                if (url != null) {
                    if (recurrence != null) {
                        fields.add(new Problem(ErrorCode.CONFLICTING_FIELDS, t.start(),
                                "More than one unreserved template carries the Pix GUI"));
                    } else {
                        recurrence = url;
                    }
                }
            } else if (n == 64) {
                tokenize(t.valueStart(), t.valueStart() + Text.length(t.value()),
                        "template 64", fields);
            }
        }
        if (pix == null) {
            fields.add(new Problem(ErrorCode.NOT_PIX, 0, anyAccount
                    ? "No merchant account template (26 to 51) with the Pix GUI br.gov.bcb.pix"
                    : "Missing merchant account information: no field from 02 to 51"));
        }

        String mcc = required(byId, "52", "merchant category code");
        if (mcc != null && !FOUR_DIGITS.matcher(mcc).matches()) {
            invalid(byId.get("52"), "Merchant category code (52) must be 4 digits, found \"" + mcc + "\"");
        }
        String currency = required(byId, "53", "transaction currency");
        if (currency != null && !currency.equals("986")) {
            invalid(byId.get("53"), "Transaction currency (53) must be \"986\" (BRL), found \""
                    + currency + "\"");
        }
        BigDecimal amount = readAmount(byId.get("54"));
        Tlv tip = byId.get("55");
        if (tip != null && !(tip.value().equals("01") || tip.value().equals("02")
                || tip.value().equals("03"))) {
            invalid(tip, "Tip or convenience indicator (55) must be 01, 02 or 03, found \""
                    + tip.value() + "\"");
        }
        String country = required(byId, "58", "country code");
        if (country != null && !country.equals("BR")) {
            invalid(byId.get("58"), "Country code (58) must be \"BR\", found \"" + country + "\"");
        }
        String name = required(byId, "59", "merchant name");
        checkAns(byId.get("59"), "Merchant name (59)", BrCode.MAX_MERCHANT_NAME);
        String city = required(byId, "60", "merchant city");
        checkAns(byId.get("60"), "Merchant city (60)", BrCode.MAX_MERCHANT_CITY);
        Tlv postal = byId.get("61");
        checkAns(postal, "Postal code (61)", BrCode.MAX_POSTAL_CODE);

        BrCode.Type type = null;
        if (pix != null) {
            if (pix.key != null || pix.keyRejected) {
                type = BrCode.Type.STATIC;
            } else if (pix.location != null || pix.locationRejected) {
                type = BrCode.Type.DYNAMIC;
            } else if (recurrence != null) {
                type = BrCode.Type.RECURRENCE_ONLY;
            } else if (!pixUnreservedSeen) {
                fields.add(new Problem(ErrorCode.MISSING_FIELD, pix.template.start(),
                        "The Pix template must contain a key (01) or a location (25)"));
            }
        }
        String txid = readTxid(byId.get("62"), type);

        if (!fields.isEmpty()) {
            return null;
        }
        return new BrCode(payload, type, initiation, pix.key, pix.location, pix.description,
                pix.withdrawalFacilitator, mcc, amount, name, city,
                postal == null ? null : postal.value(), txid, recurrence,
                payload.substring(payload.length() - 4));
    }

    private void invalid(Tlv t, String message) {
        fields.add(new Problem(ErrorCode.INVALID_VALUE, t.valueStart(), message));
    }

    private String required(Map<String, Tlv> byId, String id, String name) {
        Tlv t = byId.get(id);
        if (t == null) {
            fields.add(new Problem(ErrorCode.MISSING_FIELD, 0, "Missing " + name + " (" + id + ")"));
            return null;
        }
        return t.value();
    }

    private void checkAns(Tlv t, String label, int max) {
        if (t == null) {
            return;
        }
        int length = Text.length(t.value());
        if (length > max) {
            fields.add(new Problem(ErrorCode.INVALID_LENGTH, t.start() + 2, label + " has "
                    + length + " characters, the maximum is " + max));
        }
        int bad = Text.firstNonAns(t.value());
        if (bad >= 0) {
            fields.add(new Problem(ErrorCode.INVALID_CHARACTER, t.valueStart() + bad, label
                    + " may only contain printable ASCII characters"));
        }
    }

    private BigDecimal readAmount(Tlv t) {
        if (t == null) {
            return null;
        }
        String value = t.value();
        if (value.length() > BrCode.MAX_AMOUNT_LENGTH) {
            fields.add(new Problem(ErrorCode.INVALID_LENGTH, t.start() + 2,
                    "Transaction amount (54) has more than 13 characters"));
            return null;
        }
        var m = AMOUNT.matcher(value);
        if (!m.matches()) {
            fields.add(new Problem(ErrorCode.INVALID_AMOUNT, t.valueStart(), "Transaction amount (54)"
                    + " must contain only digits and an optional dot, found \"" + value + "\""));
            return null;
        }
        String decimals = m.group(2) == null ? "" : m.group(2);
        if (decimals.length() > 2) {
            fields.add(new Problem(ErrorCode.INVALID_AMOUNT, t.valueStart(),
                    "Transaction amount (54) has more than 2 decimal places: \"" + value + "\""));
            return null;
        }
        BigDecimal amount = new BigDecimal(m.group(1) + (decimals.isEmpty() ? "" : "." + decimals))
                .setScale(2);
        if (amount.signum() == 0) {
            fields.add(new Problem(ErrorCode.INVALID_AMOUNT, t.valueStart(),
                    "Transaction amount (54) must be greater than zero"));
            return null;
        }
        return amount;
    }

    private String readTxid(Tlv t, BrCode.Type type) {
        if (t == null) {
            fields.add(new Problem(ErrorCode.MISSING_FIELD, 0,
                    "Missing additional data field template (62) with the transaction ID (05)"));
            return null;
        }
        List<Tlv> sub = tokenize(t.valueStart(), t.valueStart() + Text.length(t.value()),
                "template 62", fields);
        if (sub == null) {
            return null;
        }
        Tlv ref = unique(sub, "05", "template 62");
        if (ref == null) {
            fields.add(new Problem(ErrorCode.MISSING_FIELD, t.start(),
                    "Template 62 must contain the transaction ID (05); use *** when there is none"));
            return null;
        }
        String value = ref.value();
        if (value.equals("***")) {
            return null;
        }
        int length = Text.length(value);
        if (length > BrCode.MAX_TXID) {
            fields.add(new Problem(ErrorCode.INVALID_LENGTH, ref.start() + 2,
                    "Transaction ID (62-05) has " + length + " characters, the maximum is 25"));
            return null;
        }
        if (type == BrCode.Type.STATIC) {
            if (!STATIC_TXID.matcher(value).matches()) {
                fields.add(new Problem(ErrorCode.INVALID_TXID, ref.valueStart(), "Transaction ID"
                        + " (62-05) of a static code may only contain A-Z, a-z and 0-9, or be ***;"
                        + " found \"" + value + "\""));
                return null;
            }
        } else {
            int bad = Text.firstNonAns(value);
            if (bad >= 0) {
                fields.add(new Problem(ErrorCode.INVALID_CHARACTER, ref.valueStart() + bad,
                        "Transaction ID (62-05) may only contain printable ASCII characters"));
                return null;
            }
        }
        return value;
    }

    /** Returns the data object with the ID, reporting duplicates. */
    private Tlv unique(List<Tlv> list, String id, String context) {
        Tlv found = null;
        for (Tlv t : list) {
            if (t.id().equals(id)) {
                if (found != null) {
                    fields.add(new Problem(ErrorCode.DUPLICATE_ID, t.start(),
                            "ID " + id + " appears more than once in " + context));
                } else {
                    found = t;
                }
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- Pix templates

    private static final class PixTemplate {
        Tlv template;
        PixKey key;
        boolean keyRejected;
        String location;
        boolean locationRejected;
        String description;
        String withdrawalFacilitator;
    }

    private static boolean isPixGui(String gui) {
        return gui.toLowerCase(Locale.ROOT).equals(BrCode.GUI);
    }

    /** Reads a template 26 to 51; returns null when it does not carry the Pix GUI. */
    private PixTemplate readAccountTemplate(Tlv t) {
        String context = "template " + t.id();
        List<Tlv> sub = tokenize(t.valueStart(), t.valueStart() + Text.length(t.value()), context, fields);
        if (sub == null) {
            return null;
        }
        Tlv gui = unique(sub, "00", context);
        if (gui == null) {
            fields.add(new Problem(ErrorCode.MISSING_FIELD, t.start(),
                    "Merchant account template " + t.id() + " has no GUI (00)"));
            return null;
        }
        if (!isPixGui(gui.value())) {
            return null;
        }
        PixTemplate pix = new PixTemplate();
        pix.template = t;
        Tlv key = null;
        Tlv url = null;
        Tlv description = null;
        Tlv fss = null;
        for (Tlv s : sub) {
            switch (s.id()) {
                case "00" -> { }
                case "01" -> key = firstOrDuplicate(key, s, context);
                case "02" -> description = firstOrDuplicate(description, s, context);
                case "03" -> fss = firstOrDuplicate(fss, s, context);
                case "25" -> url = firstOrDuplicate(url, s, context);
                default -> fields.add(new Problem(ErrorCode.UNKNOWN_ID, s.start(),
                        "ID " + s.id() + " is not defined in the Pix merchant account template"));
            }
        }
        if (key != null && url != null) {
            fields.add(new Problem(ErrorCode.CONFLICTING_FIELDS, url.start(),
                    "The Pix template has both a key (01) and a location (25)"));
        }
        // The 99 character template already caps the key at 77 and the description at 72.
        if (key != null) {
            String[] reason = new String[1];
            pix.key = PixKey.parseCanonical(key.value(), reason);
            if (pix.key == null) {
                fields.add(new Problem(ErrorCode.INVALID_KEY, key.valueStart(), reason[0]));
                pix.keyRejected = true;
            }
        }
        if (url != null) {
            String reason = BrCode.locationProblem("Location (26-25)", url.value());
            if (reason != null) {
                fields.add(new Problem(ErrorCode.INVALID_VALUE, url.valueStart(), reason));
                pix.locationRejected = true;
            } else {
                pix.location = url.value();
            }
        }
        if (description != null) {
            if (key == null) {
                fields.add(new Problem(ErrorCode.CONFLICTING_FIELDS, description.start(),
                        "The description (26-02) is only defined for static codes"));
            } else if (description.value().codePoints().anyMatch(Character::isISOControl)) {
                fields.add(new Problem(ErrorCode.INVALID_CHARACTER, description.valueStart(),
                        "Description (26-02) has a control character"));
            } else {
                pix.description = description.value();
            }
        }
        if (fss != null) {
            if (key == null) {
                fields.add(new Problem(ErrorCode.CONFLICTING_FIELDS, fss.start(),
                        "The withdrawal facilitator (26-03) is only defined for static codes"));
            } else if (!ISPB.matcher(fss.value()).matches()) {
                fields.add(new Problem(ErrorCode.INVALID_VALUE, fss.valueStart(),
                        "Withdrawal facilitator (26-03) must be an 8 digit ISPB, found \""
                                + fss.value() + "\""));
            } else {
                pix.withdrawalFacilitator = fss.value();
            }
        }
        return pix;
    }

    private Tlv firstOrDuplicate(Tlv current, Tlv next, String context) {
        if (current != null) {
            fields.add(new Problem(ErrorCode.DUPLICATE_ID, next.start(),
                    "ID " + next.id() + " appears more than once in " + context));
            return current;
        }
        return next;
    }

    /** Reads a template 80 to 99; returns the recurrence location when it carries the Pix GUI. */
    private String readUnreservedTemplate(Tlv t) {
        String context = "template " + t.id();
        List<Tlv> sub = tokenize(t.valueStart(), t.valueStart() + Text.length(t.value()), context, fields);
        if (sub == null) {
            return null;
        }
        Tlv gui = unique(sub, "00", context);
        if (gui == null) {
            fields.add(new Problem(ErrorCode.MISSING_FIELD, t.start(),
                    "Unreserved template " + t.id() + " has no GUI (00)"));
            return null;
        }
        if (!isPixGui(gui.value())) {
            return null;
        }
        pixUnreservedSeen = true;
        Tlv url = null;
        for (Tlv s : sub) {
            switch (s.id()) {
                case "00" -> { }
                case "25" -> url = firstOrDuplicate(url, s, context);
                default -> fields.add(new Problem(ErrorCode.UNKNOWN_ID, s.start(),
                        "ID " + s.id() + " is not defined in the Pix unreserved template"));
            }
        }
        if (url == null) {
            fields.add(new Problem(ErrorCode.MISSING_FIELD, t.start(),
                    "The Pix unreserved template " + t.id() + " has no recurrence location (25)"));
            return null;
        }
        String reason = BrCode.locationProblem("Recurrence location (" + t.id() + "-25)", url.value());
        if (reason != null) {
            fields.add(new Problem(ErrorCode.INVALID_VALUE, url.valueStart(), reason));
            return null;
        }
        return url.value();
    }
}
