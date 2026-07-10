package com.ptengine.reconciliation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, pure-domain comparison of two {@link PositionSnapshot} values.
 *
 * <p>No clock, no ID generation, no tolerance/staleness threshold, no network/file/environment
 * access. Does not mutate OMS, {@code PaperOrderPipeline}, {@code RiskGateway}, or {@code
 * PaperBroker} state; {@code capturedAtEpochMs} on either snapshot is never compared, because no
 * staleness threshold or reconciliation window has been approved.
 */
public final class PositionReconciler {

    public PositionReconciliationResult reconcile(
            PositionSnapshot expected, PositionSnapshot observed, String reconciliationId, long reconciledAtEpochMs) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(observed, "observed");

        List<PositionReconciliationResult.Reason> reasons = new ArrayList<>();
        if (!expected.instrument().equals(observed.instrument())) {
            reasons.add(PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH);
        }
        if (expected.direction() != observed.direction()) {
            reasons.add(PositionReconciliationResult.Reason.DIRECTION_MISMATCH);
        }
        if (expected.positionNotional().compareTo(observed.positionNotional()) != 0) {
            reasons.add(PositionReconciliationResult.Reason.POSITION_NOTIONAL_MISMATCH);
        }
        if (expected.averageEntryPrice().compareTo(observed.averageEntryPrice()) != 0) {
            reasons.add(PositionReconciliationResult.Reason.AVERAGE_ENTRY_PRICE_MISMATCH);
        }

        PositionReconciliationResult.Status status = reasons.isEmpty()
                ? PositionReconciliationResult.Status.MATCH
                : PositionReconciliationResult.Status.MISMATCH;

        return new PositionReconciliationResult(reconciliationId, status, reasons, expected, observed, reconciledAtEpochMs);
    }
}
