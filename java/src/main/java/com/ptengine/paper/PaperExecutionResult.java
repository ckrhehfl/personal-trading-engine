package com.ptengine.paper;

import java.math.BigDecimal;

/**
 * Deterministic, immutable result of one {@link PaperBroker} execution attempt.
 *
 * <p>Preserves caller-supplied and source identifiers unchanged: no BTC quantity, contract size,
 * exchange precision/rounding, or fee is derived or added here.
 */
public record PaperExecutionResult(
        String executionId,
        String intentId,
        String riskDecisionId,
        String instrument,
        PaperExecutionSide side,
        BigDecimal requestedNotional,
        PaperExecutionStatus status,
        BigDecimal executionPrice,
        long evaluatedAtEpochMs) {

    public PaperExecutionResult {
        PaperValidation.requireValidIdentifier(executionId, "executionId");
        PaperValidation.requireValidIdentifier(intentId, "intentId");
        PaperValidation.requireValidIdentifier(riskDecisionId, "riskDecisionId");
        PaperValidation.requireValidIdentifier(instrument, "instrument");
        if (side == null) {
            throw new InvalidPaperExecutionException("side must not be null");
        }
        if (requestedNotional == null) {
            throw new InvalidPaperExecutionException("requestedNotional must not be null");
        }
        if (requestedNotional.signum() <= 0) {
            throw new InvalidPaperExecutionException("requestedNotional must be positive, was: " + requestedNotional);
        }
        if (status == null) {
            throw new InvalidPaperExecutionException("status must not be null");
        }
        if (status == PaperExecutionStatus.FILLED) {
            if (executionPrice == null) {
                throw new InvalidPaperExecutionException("FILLED requires a non-null executionPrice");
            }
            if (executionPrice.signum() <= 0) {
                throw new InvalidPaperExecutionException(
                        "FILLED executionPrice must be positive, was: " + executionPrice);
            }
        } else if (executionPrice != null) {
            throw new InvalidPaperExecutionException("NO_FILL must have a null executionPrice, was: " + executionPrice);
        }
        if (evaluatedAtEpochMs < 0) {
            throw new InvalidPaperExecutionException(
                    "evaluatedAtEpochMs must be non-negative, was: " + evaluatedAtEpochMs);
        }
    }
}
