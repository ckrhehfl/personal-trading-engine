package com.ptengine.paper;

import com.ptengine.contract.ContractLimits;
import java.math.BigDecimal;
import java.util.Objects;

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
        requireValidIdentifier(executionId, "executionId");
        requireValidIdentifier(intentId, "intentId");
        requireValidIdentifier(riskDecisionId, "riskDecisionId");
        requireValidIdentifier(instrument, "instrument");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(requestedNotional, "requestedNotional");
        if (requestedNotional.signum() <= 0) {
            throw new InvalidPaperExecutionException("requestedNotional must be positive, was: " + requestedNotional);
        }
        Objects.requireNonNull(status, "status");
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

    private static void requireValidIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidPaperExecutionException(fieldName + " must not be blank");
        }
        if (value.length() > ContractLimits.MAX_IDENTIFIER_LENGTH) {
            throw new InvalidPaperExecutionException(
                    fieldName + " must be at most " + ContractLimits.MAX_IDENTIFIER_LENGTH + " characters, was: "
                            + value.length());
        }
    }
}
