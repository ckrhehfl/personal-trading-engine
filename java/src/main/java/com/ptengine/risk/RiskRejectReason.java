package com.ptengine.risk;

/**
 * Closed enum of the rejection reasons the present {@link RiskGateway} rules actually emit.
 *
 * <p>This is <strong>not</strong> a wire-format whitelist for {@link RiskDecision#reasonCodes()}.
 * {@code schemas/v0.1/risk-decision.schema.json} intentionally keeps {@code reasonCodes} open
 * (any {@code nonEmptyIdentifier}-shaped string), and {@link RiskDecision} accepts any
 * schema-valid reason string accordingly &mdash; including reasons no current {@link RiskRule}
 * emits, such as a future kill-switch reason. This enum only documents/names the closed set of
 * reasons this codebase's rules currently produce; it is a producer-side convenience, not a
 * consumer-side (wire) validation boundary.
 *
 * <p>Each value's {@link #name()} is used verbatim as the schema-aligned {@code reasonCodes}
 * string when a {@link RiskRule} in this codebase emits it.
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
