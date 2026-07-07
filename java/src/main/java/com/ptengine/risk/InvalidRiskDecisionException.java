package com.ptengine.risk;

/** Thrown when constructing a {@link RiskDecision} that violates a v0.1 field invariant. */
public final class InvalidRiskDecisionException extends IllegalArgumentException {

    public InvalidRiskDecisionException(String message) {
        super(message);
    }
}
