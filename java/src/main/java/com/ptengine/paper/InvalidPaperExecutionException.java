package com.ptengine.paper;

/**
 * Thrown for any invariant violation on the {@code com.ptengine.paper} boundary: invalid
 * {@link PaperMarketSnapshot} / {@link PaperExecutionMetadata} / {@link PaperExecutionResult}
 * field values, or a {@link PaperBroker} execution-authority violation (missing/non-PASS risk
 * decision, mismatched intent id, mismatched instrument, unsupported order type). A single
 * exception type lets callers catch one type for any paper-execution-boundary invariant
 * violation on constructed values.
 *
 * <p>Not used for null {@code intent}/{@code marketSnapshot}/{@code metadata} arguments to
 * {@link PaperBroker#execute}, which still throw {@link NullPointerException} per the existing
 * {@code RiskGateway.evaluate()} null-argument convention. Only a null {@code riskDecision} uses
 * this exception instead of {@link NullPointerException}, because a matching {@code PASS}
 * decision is mandatory execution authority, not an incidental method argument.
 */
public final class InvalidPaperExecutionException extends IllegalArgumentException {

    public InvalidPaperExecutionException(String message) {
        super(message);
    }

    public InvalidPaperExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
