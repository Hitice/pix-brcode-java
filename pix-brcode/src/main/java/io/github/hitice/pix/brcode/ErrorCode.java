package io.github.hitice.pix.brcode;

/** Category of a problem found while reading a BR Code payload. */
public enum ErrorCode {
    /** The payload ends in the middle of a field, or a declared length runs past the end. */
    TRUNCATED,
    /** An ID or length is not two decimal digits. */
    MALFORMED_TLV,
    /** A declared length is outside the range the specification allows for the field. */
    INVALID_LENGTH,
    /** The same ID appears twice under the root or inside one template. */
    DUPLICATE_ID,
    /** An ID that is reserved for future use, or not defined in the Pix template. */
    UNKNOWN_ID,
    /** Field {@code 00} is not the first field or field {@code 63} is not the last one. */
    MISPLACED_FIELD,
    /** A mandatory field is absent. */
    MISSING_FIELD,
    /** A field has a value outside its allowed set or format. */
    INVALID_VALUE,
    /** A field has a character outside its allowed character set. */
    INVALID_CHARACTER,
    /** The CRC in field {@code 63} does not match the payload. */
    CRC_MISMATCH,
    /** The Pix key is not valid for any key type. */
    INVALID_KEY,
    /** The transaction amount (field {@code 54}) is malformed, zero or has more than 2 decimals. */
    INVALID_AMOUNT,
    /** The transaction ID (field {@code 62-05}) does not follow the Pix rules. */
    INVALID_TXID,
    /** Fields that cannot appear together, such as a key and a location. */
    CONFLICTING_FIELDS,
    /** No merchant account information template with the Pix GUI {@code br.gov.bcb.pix}. */
    NOT_PIX
}
