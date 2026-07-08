package com.ptengine.paper;

/**
 * Thrown for any invariant violation on the {@code com.ptengine.paper} boundary: invalid
 * {@link PaperMarketSnapshot} / {@link PaperExecutionMetadata} / {@link PaperExecutionResult}
 * field values, or a {@link PaperBroker} execution-authority violation (missing/non-PASS risk
 * decision, mismatched intent id, mismatched instrument). A single exception type lets callers
 * catch one type for any paper-execution-boundary invariant violation.
 */
public final class InvalidPaperExecutionException extends IllegalArgumentException {

    public InvalidPaperExecutionException(String message) {
        super(message);
    }

    public InvalidPaperExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
