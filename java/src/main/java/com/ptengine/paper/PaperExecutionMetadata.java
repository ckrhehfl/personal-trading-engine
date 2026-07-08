package com.ptengine.paper;

import com.ptengine.contract.ContractLimits;

/**
 * Caller-supplied execution identity/timestamp metadata for a {@link PaperBroker} call.
 *
 * <p>No internal id generation, no clock: both fields must come from the caller, keeping
 * {@link PaperBroker} deterministic.
 */
public record PaperExecutionMetadata(String executionId, long evaluatedAtEpochMs) {

    public PaperExecutionMetadata {
        if (executionId == null || executionId.isBlank()) {
            throw new InvalidPaperExecutionException("executionId must not be blank");
        }
        if (executionId.length() > ContractLimits.MAX_IDENTIFIER_LENGTH) {
            throw new InvalidPaperExecutionException(
                    "executionId must be at most " + ContractLimits.MAX_IDENTIFIER_LENGTH + " characters, was: "
                            + executionId.length());
        }
        if (evaluatedAtEpochMs < 0) {
            throw new InvalidPaperExecutionException(
                    "evaluatedAtEpochMs must be non-negative, was: " + evaluatedAtEpochMs);
        }
    }
}
