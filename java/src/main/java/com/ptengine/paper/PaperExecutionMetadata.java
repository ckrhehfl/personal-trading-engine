package com.ptengine.paper;

/**
 * Caller-supplied execution identity/timestamp metadata for a {@link PaperBroker} call.
 *
 * <p>No internal id generation, no clock: both fields must come from the caller, keeping
 * {@link PaperBroker} deterministic.
 */
public record PaperExecutionMetadata(String executionId, long evaluatedAtEpochMs) {

    public PaperExecutionMetadata {
        PaperValidation.requireValidIdentifier(executionId, "executionId");
        if (evaluatedAtEpochMs < 0) {
            throw new InvalidPaperExecutionException(
                    "evaluatedAtEpochMs must be non-negative, was: " + evaluatedAtEpochMs);
        }
    }
}
