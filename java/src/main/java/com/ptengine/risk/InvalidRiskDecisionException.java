package com.ptengine.risk;

/**
 * Thrown when constructing a value on the typed {@link RiskDecision} boundary that violates a
 * v0.1 field invariant. Covers both {@link RiskDecision} itself and its caller-supplied
 * {@link RiskDecisionMetadata}, so callers can catch this one type for any risk-decision-boundary
 * invariant violation rather than also having to catch a bare {@link IllegalArgumentException} or
 * {@link NullPointerException}.
 */
public final class InvalidRiskDecisionException extends IllegalArgumentException {

    public InvalidRiskDecisionException(String message) {
        super(message);
    }

    public InvalidRiskDecisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
