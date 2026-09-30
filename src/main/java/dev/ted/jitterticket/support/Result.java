package dev.ted.jitterticket.support;

/**
 * The outcome of a command: either it succeeded, or it was rejected for a domain reason.
 * A rejection is an ordinary outcome, not an error, so it is returned rather than thrown.
 * <p>
 * Lives in a neutral package so the event-sourced domain, the {@code orm} package, and the
 * decider package can all use the same type without depending on each other.
 */
public sealed interface Result<SUCCESS, FAILURE> {

    static <SUCCESS, FAILURE> Result<SUCCESS, FAILURE> success(SUCCESS value) {
        return new Success<>(value);
    }

    /** For commands that have no value to return, only "it happened". */
    static <FAILURE> Result<Void, FAILURE> succeeded() {
        return new Success<>(null);
    }

    static <SUCCESS, FAILURE> Result<SUCCESS, FAILURE> rejected(FAILURE rejection) {
        return new Failure<>(rejection);
    }

    default boolean isSuccess() {
        return this instanceof Success<SUCCESS, FAILURE>;
    }

    default boolean isRejected() {
        return this instanceof Failure<SUCCESS, FAILURE>;
    }

    /**
     * For callers that treat a rejection as a bug in themselves (sample-data setup, for example)
     * rather than as an outcome to handle.
     */
    default SUCCESS orElseThrow() {
        return switch (this) {
            case Success<SUCCESS, FAILURE>(SUCCESS value) -> value;
            case Failure<SUCCESS, FAILURE>(FAILURE rejection) ->
                    throw new IllegalStateException("Command was rejected: " + rejection);
        };
    }

    record Success<SUCCESS, FAILURE>(SUCCESS value) implements Result<SUCCESS, FAILURE> { }

    record Failure<SUCCESS, FAILURE>(FAILURE rejection) implements Result<SUCCESS, FAILURE> { }

}
