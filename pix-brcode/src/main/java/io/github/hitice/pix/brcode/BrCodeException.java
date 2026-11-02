package io.github.hitice.pix.brcode;

import java.util.Objects;

/**
 * Thrown by {@link BrCode#parse(String)} when a payload is not a valid Pix BR Code. It carries the
 * first {@link Problem} found; use {@link BrCode#validate(String)} to get all of them.
 */
public final class BrCodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The problem found. */
    private final Problem problem;

    /**
     * Creates the exception for a problem.
     *
     * @param problem the problem found
     */
    public BrCodeException(Problem problem) {
        super(Objects.requireNonNull(problem, "problem").toString());
        this.problem = problem;
    }

    /**
     * Returns the problem that caused this exception.
     *
     * @return the problem
     */
    public Problem problem() {
        return problem;
    }

    /**
     * Returns the problem category.
     *
     * @return the category
     */
    public ErrorCode code() {
        return problem.code();
    }

    /**
     * Returns the zero based character index in the payload where the problem was found.
     *
     * @return the position
     */
    public int position() {
        return problem.position();
    }
}
