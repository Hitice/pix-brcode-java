package io.github.hitice.pix.brcode;

import java.io.Serializable;
import java.util.Objects;

/**
 * One problem found in a BR Code payload.
 *
 * @param code the problem category
 * @param position zero based character index in the payload where the problem was found; for a
 *     missing field it is the start of the template that should contain it ({@code 0} for the root)
 * @param message a description in English
 */
public record Problem(ErrorCode code, int position, String message) implements Serializable {

    /**
     * Creates a problem.
     *
     * @param code the problem category
     * @param position the character index, not negative
     * @param message the description
     */
    public Problem {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative: " + position);
        }
    }

    /**
     * Returns the message followed by the position, for example
     * {@code "CRC mismatch: expected 1D3D, found 1D3E (at position 133)"}.
     *
     * @return the description with its position
     */
    @Override
    public String toString() {
        return message + " (at position " + position + ")";
    }
}
