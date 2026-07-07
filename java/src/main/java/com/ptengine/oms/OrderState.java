package com.ptengine.oms;

/**
 * Order lifecycle states for the OMS pure-domain skeleton.
 *
 * <p>This is a bounded Candidate 2 skeleton, not a complete production OMS.
 * Deferred beyond this skeleton: fill-quantity aggregation, average fill
 * price, repeated partial-fill event accounting, stale-order handling,
 * restart/retry idempotency, WebSocket/REST reconciliation, position-side
 * validation, leverage/margin validation, and kill-switch interaction. See
 * {@code docs/06_VALIDATION_POLICY.md} §5 for the full eventual OMS scope.
 */
public enum OrderState {
    NEW,
    ACCEPTED,
    PARTIALLY_FILLED,
    FILLED,
    CANCELED,
    REJECTED;

    /**
     * A terminal state cannot transition to any other state.
     */
    public boolean isTerminal() {
        return this == FILLED || this == CANCELED || this == REJECTED;
    }
}
