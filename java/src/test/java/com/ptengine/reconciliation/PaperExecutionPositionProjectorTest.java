package com.ptengine.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ptengine.contract.Direction;
import com.ptengine.paper.PaperExecutionResult;
import com.ptengine.paper.PaperExecutionSide;
import com.ptengine.paper.PaperExecutionStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaperExecutionPositionProjectorTest {

    private static PaperExecutionResult execution(
            PaperExecutionSide side, PaperExecutionStatus status, String notional, String price, long evaluatedAtEpochMs) {
        return new PaperExecutionResult(
                "exec-1",
                "intent-1",
                "decision-1",
                "BTCUSDT",
                side,
                new BigDecimal(notional),
                status,
                price == null ? null : new BigDecimal(price),
                evaluatedAtEpochMs);
    }

    // --- A. BUY FILLED projects LONG PositionSnapshot ---

    @Test
    void buyFilledProjectsLongPositionSnapshot() {
        PaperExecutionResult execution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);

        Optional<PositionSnapshot> projected = new PaperExecutionPositionProjector().project(execution);

        assertTrue(projected.isPresent());
        PositionSnapshot snapshot = projected.get();
        assertEquals("BTCUSDT", snapshot.instrument());
        assertEquals(Direction.LONG, snapshot.direction());
        assertEquals(0, new BigDecimal("1000").compareTo(snapshot.positionNotional()));
        assertEquals(0, new BigDecimal("50000").compareTo(snapshot.averageEntryPrice()));
        assertEquals(4_000L, snapshot.capturedAtEpochMs());
    }

    // --- B. SELL FILLED projects SHORT PositionSnapshot ---

    @Test
    void sellFilledProjectsShortPositionSnapshot() {
        PaperExecutionResult execution =
                execution(PaperExecutionSide.SELL, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);

        Optional<PositionSnapshot> projected = new PaperExecutionPositionProjector().project(execution);

        assertTrue(projected.isPresent());
        PositionSnapshot snapshot = projected.get();
        assertEquals("BTCUSDT", snapshot.instrument());
        assertEquals(Direction.SHORT, snapshot.direction());
        assertEquals(0, new BigDecimal("1000").compareTo(snapshot.positionNotional()));
        assertEquals(0, new BigDecimal("50000").compareTo(snapshot.averageEntryPrice()));
        assertEquals(4_000L, snapshot.capturedAtEpochMs());
    }

    // --- C. NO_FILL returns Optional.empty ---

    @Test
    void noFillReturnsEmptyOptional() {
        PaperExecutionResult execution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.NO_FILL, "1000", null, 4_000L);

        Optional<PositionSnapshot> projected = new PaperExecutionPositionProjector().project(execution);

        assertFalse(projected.isPresent());
        assertEquals(null, execution.executionPrice());
    }

    // --- D. null PaperExecutionResult is rejected ---

    @Test
    void nullExecutionResultRejected() {
        assertThrows(NullPointerException.class, () -> new PaperExecutionPositionProjector().project(null));
    }

    // --- E. BigDecimal scale preserved enough to remain reconciliation-compatible ---

    @Test
    void bigDecimalScalePreservedForReconciliationCompatibility() {
        PaperExecutionResult execution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "100.00", "50000.0", 4_000L);

        Optional<PositionSnapshot> projected = new PaperExecutionPositionProjector().project(execution);

        assertTrue(projected.isPresent());
        PositionSnapshot snapshot = projected.get();
        assertEquals(0, new BigDecimal("100.0").compareTo(snapshot.positionNotional()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(snapshot.averageEntryPrice()));
    }

    // --- F. Projected snapshot reconciles as MATCH against an equivalent expected snapshot ---

    @Test
    void projectedSnapshotReconcilesAsMatch() {
        PaperExecutionResult execution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);
        PositionSnapshot projected = new PaperExecutionPositionProjector().project(execution).orElseThrow();
        PositionSnapshot expected =
                new PositionSnapshot("BTCUSDT", Direction.LONG, new BigDecimal("1000"), new BigDecimal("50000"), 4_000L);

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(expected, projected, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MATCH, result.status());
        assertTrue(result.reasons().isEmpty());
    }

    // --- G. BUY vs SELL projections reconcile as direction mismatch ---

    @Test
    void buyVersusSellProjectionsReconcileAsDirectionMismatch() {
        PaperExecutionResult buyExecution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);
        PaperExecutionResult sellExecution =
                execution(PaperExecutionSide.SELL, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);

        PaperExecutionPositionProjector projector = new PaperExecutionPositionProjector();
        PositionSnapshot buySnapshot = projector.project(buyExecution).orElseThrow();
        PositionSnapshot sellSnapshot = projector.project(sellExecution).orElseThrow();

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(buySnapshot, sellSnapshot, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MISMATCH, result.status());
        assertEquals(List.of(PositionReconciliationResult.Reason.DIRECTION_MISMATCH), result.reasons());
    }

    // --- H. Determinism across fresh projector instances ---

    @Test
    void determinismAcrossFreshProjectorInstances() {
        PaperExecutionResult firstExecution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);
        PaperExecutionResult secondExecution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);
        assertEquals(firstExecution, secondExecution);

        Optional<PositionSnapshot> first = new PaperExecutionPositionProjector().project(firstExecution);
        Optional<PositionSnapshot> second = new PaperExecutionPositionProjector().project(secondExecution);

        assertEquals(first, second);
    }
}
