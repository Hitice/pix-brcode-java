package io.github.hitice.pix.brcode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of {@link BrCode#validate(String)}: every problem found in a payload, and the decoded
 * {@link BrCode} when there is none.
 */
public final class ValidationResult {

    private final List<Problem> problems;
    private final BrCode brCode;

    ValidationResult(List<Problem> problems, BrCode brCode) {
        this.problems = List.copyOf(problems);
        this.brCode = brCode;
    }

    /**
     * Whether the payload is a valid Pix BR Code.
     *
     * @return true when no problem was found
     */
    public boolean isValid() {
        return problems.isEmpty();
    }

    /**
     * Returns the problems found, structural ones first, then the CRC, then field rules; each group
     * in payload order.
     *
     * @return an immutable list, empty when the payload is valid
     */
    public List<Problem> problems() {
        return problems;
    }

    /**
     * Returns the decoded code.
     *
     * @return the code when the payload is valid, otherwise empty
     */
    public Optional<BrCode> brCode() {
        return Optional.ofNullable(brCode);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ValidationResult other
                && problems.equals(other.problems)
                && Objects.equals(brCode, other.brCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(problems, brCode);
    }

    @Override
    public String toString() {
        return isValid() ? "ValidationResult[valid]" : "ValidationResult" + problems;
    }
}
