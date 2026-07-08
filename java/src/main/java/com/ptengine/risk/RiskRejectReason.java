package com.ptengine.risk;

/**
 * Initial closed Java rejection-reason enum for {@link RiskDecision#reasonCodes()}.
 * {@code schemas/v0.1/risk-decision.schema.json} intentionally left this closed
 * enum deferred to the Java Risk Gateway task; this is that task.
 *
 * <p>Deliberately minimal. Each value's {@link #name()} is used verbatim as the
 * schema-aligned {@code reasonCodes} string.
 */
public enum RiskRejectReason {

    /** No {@link RiskRule} was configured on the {@link RiskGateway}; fail closed rather than PASS. */
    RISK_CONFIGURATION_MISSING,

    /** A {@link RiskRule} threw, or returned a result the gateway contract forbids; fail closed. */
    RISK_ENGINE_ERROR,

    /**
     * Reserved for a future runtime task: {@code docs/05_RISK_POLICY.md} names degraded risk state
     * (stale WebSocket, API error spike, risk engine internal error) as a fail-closed condition.
     * This pure-domain skeleton has no network/runtime state to observe, so no rule in this
     * candidate produces this reason yet; it is declared now so the closed enum does not need a
     * breaking addition when degraded-state detection is implemented.
     */
    RISK_STATE_DEGRADED
}
