package io.github.hitice.pix.brcode;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** Character set rules of the EMV QRCPS formats used by the BR Code. */
final class Text {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private Text() {
    }

    /**
     * Whether the character belongs to the EMV Alphanumeric Special (ans) set, the printable
     * ASCII range from space to tilde.
     */
    static boolean isAns(int codePoint) {
        return codePoint >= 0x20 && codePoint <= 0x7E;
    }

    static boolean isAns(String value) {
        return value.codePoints().allMatch(Text::isAns);
    }

    /** Index of the first character outside the ans set, or -1. */
    static int firstNonAns(String value) {
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            if (!isAns(cp)) {
                return value.codePointCount(0, i);
            }
            i += Character.charCount(cp);
        }
        return -1;
    }

    /**
     * Converts free text to the ans set: trims, decomposes accented letters (NFD) and removes the
     * combining marks, so "S&atilde;o Paulo" becomes "Sao Paulo". Case is kept.
     *
     * @throws IllegalArgumentException if the result is empty, longer than {@code maxLength}
     *     characters, or still has characters outside the ans set
     */
    static String toAns(String field, String value, int maxLength) {
        String stripped = COMBINING_MARKS.matcher(Normalizer.normalize(value.strip(), Normalizer.Form.NFD))
                .replaceAll("");
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        int bad = firstNonAns(stripped);
        if (bad >= 0) {
            int cp = stripped.codePointAt(stripped.offsetByCodePoints(0, bad));
            throw new IllegalArgumentException(field + " has a character that the BR Code does not"
                    + " allow at index " + bad + ": '" + new String(Character.toChars(cp)) + "' (U+"
                    + String.format("%04X", cp) + "); only printable ASCII is allowed after accents"
                    + " are removed");
        }
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(field + " has " + stripped.length()
                    + " characters, the maximum is " + maxLength + ": \"" + stripped + "\"");
        }
        return stripped;
    }

    static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
