package com.ptengine.risk;

/**
 * Caller-supplied {@link RiskDecision} identity/timestamp metadata, passed
 * explicitly into {@link RiskGateway#evaluate(com.ptengine.contract.OrderIntent, RiskDecisionMetadata)}.
 *
 * <p>This keeps decision-id generation and timestamping outside the pure
 * evaluation core: no static clock, no static/global ID generator, no
 * hidden nondeterminism. Tests supply fixed values directly; a future
 * runtime task is responsible for wiring a real clock/id-supplier at the
 * call site, not for changing this contract.
 */
public record RiskDecisionMetadata(String decisionId, long evaluatedAtEpochMs) {

    public RiskDecisionMetadata {
        if (decisionId == null || decisionId.isBlank()) {
            throw new IllegalArgumentException("decisionId must not be blank");
        }
        if (evaluatedAtEpochMs < 0) {
            throw new IllegalArgumentException("evaluatedAtEpochMs must be non-negative, was: " + evaluatedAtEpochMs);
        }
    }
}
