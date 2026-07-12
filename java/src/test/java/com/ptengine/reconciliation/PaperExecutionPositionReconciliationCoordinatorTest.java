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

class PaperExecutionPositionReconciliationCoordinatorTest {

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

    private static PaperExecutionResult filledBuy() {
        return execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);
    }

    private static PaperExecutionResult filledSell() {
        return execution(PaperExecutionSide.SELL, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);
    }

    private static PaperExecutionResult noFill() {
        return execution(PaperExecutionSide.BUY, PaperExecutionStatus.NO_FILL, "1000", null, 4_000L);
    }

    private static PositionSnapshot snapshot(
            String instrument, Direction direction, String notional, String price, long capturedAtEpochMs) {
        return new PositionSnapshot(
                instrument, direction, new BigDecimal(notional), new BigDecimal(price), capturedAtEpochMs);
    }

    // --- A. FILLED BUY reconciles equivalent LONG expected snapshot as MATCH ---

    @Test
    void filledBuyReconcilesEquivalentLongExpectedAsMatch() {
        PositionSnapshot expected = snapshot("BTCUSDT", Direction.LONG, "1000", "50000", 4_000L);

        Optional<PositionReconciliationResult> result = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(filledBuy(), expected, "recon-1", 9_000L);

        assertTrue(result.isPresent());
        assertEquals(PositionReconciliationResult.Status.MATCH, result.get().status());
        assertTrue(result.get().reasons().isEmpty());
        assertEquals("recon-1", result.get().reconciliationId());
        assertEquals(9_000L, result.get().reconciledAtEpochMs());
    }

    // --- B. FILLED SELL reconciles equivalent SHORT expected snapshot as MATCH ---

    @Test
    void filledSellReconcilesEquivalentShortExpectedAsMatch() {
        PositionSnapshot expected = snapshot("BTCUSDT", Direction.SHORT, "1000", "50000", 4_000L);

        Optional<PositionReconciliationResult> result = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(filledSell(), expected, "recon-1", 9_000L);

        assertTrue(result.isPresent());
        assertEquals(PositionReconciliationResult.Status.MATCH, result.get().status());
        assertTrue(result.get().reasons().isEmpty());
    }

    // --- C. FILLED with wrong expected direction produces exact [DIRECTION_MISMATCH] ---

    @Test
    void filledWithWrongExpectedDirectionProducesExactDirectionMismatch() {
        PositionSnapshot wrongExpected = snapshot("BTCUSDT", Direction.SHORT, "1000", "50000", 4_000L);

        Optional<PositionReconciliationResult> result = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(filledBuy(), wrongExpected, "recon-1", 9_000L);

        assertTrue(result.isPresent());
        assertEquals(PositionReconciliationResult.Status.MISMATCH, result.get().status());
        assertEquals(
                List.of(PositionReconciliationResult.Reason.DIRECTION_MISMATCH),
                result.get().reasons());
    }

    // --- D. Multiple field mismatches retain the existing exact stable reason order ---

    @Test
    void multipleFieldMismatchesRetainStableReasonOrder() {
        PositionSnapshot expected = snapshot("ETHUSDT", Direction.SHORT, "2000", "51000", 4_000L);

        Optional<PositionReconciliationResult> result = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(filledBuy(), expected, "recon-1", 9_000L);

        assertTrue(result.isPresent());
        assertEquals(PositionReconciliationResult.Status.MISMATCH, result.get().status());
        assertEquals(
                List.of(
                        PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH,
                        PositionReconciliationResult.Reason.DIRECTION_MISMATCH,
                        PositionReconciliationResult.Reason.POSITION_NOTIONAL_MISMATCH,
                        PositionReconciliationResult.Reason.AVERAGE_ENTRY_PRICE_MISMATCH),
                result.get().reasons());
    }

    // --- E. BigDecimal scale-only differences still reconcile as MATCH ---

    @Test
    void bigDecimalScaleOnlyDifferencesReconcileAsMatch() {
        PaperExecutionResult execution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "1000.00", "50000.0", 4_000L);
        PositionSnapshot expected = snapshot("BTCUSDT", Direction.LONG, "1000.0", "50000.00", 4_000L);

        Optional<PositionReconciliationResult> result = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(execution, expected, "recon-1", 9_000L);

        assertTrue(result.isPresent());
        assertEquals(PositionReconciliationResult.Status.MATCH, result.get().status());
        assertTrue(result.get().reasons().isEmpty());
    }

    // --- F. NO_FILL returns Optional.empty and does not fabricate a snapshot/result ---

    @Test
    void noFillReturnsEmptyOptional() {
        PositionSnapshot expected = snapshot("BTCUSDT", Direction.LONG, "1000", "50000", 4_000L);

        Optional<PositionReconciliationResult> result = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(noFill(), expected, "recon-1", 9_000L);

        assertFalse(result.isPresent());
    }

    // --- G. NO_FILL short-circuits reconciliation-only inputs ---

    @Test
    void noFillShortCircuitsReconciliationOnlyInputs() {
        Optional<PositionReconciliationResult> result = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(noFill(), null, null, -1L);

        assertFalse(result.isPresent());
    }

    // --- H. Null executionResult is rejected ---

    @Test
    void nullExecutionResultRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new PaperExecutionPositionReconciliationCoordinator()
                        .reconcileIfFilled(null, snapshot("BTCUSDT", Direction.LONG, "1000", "50000", 4_000L), "recon-1", 9_000L));
    }

    // --- I. FILLED with null expected is rejected through the existing validation path ---

    @Test
    void filledWithNullExpectedRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new PaperExecutionPositionReconciliationCoordinator()
                        .reconcileIfFilled(filledBuy(), null, "recon-1", 9_000L));
    }

    // --- J. FILLED with invalid reconciliationId is rejected through the existing contract ---

    @Test
    void filledWithInvalidReconciliationIdRejected() {
        PositionSnapshot expected = snapshot("BTCUSDT", Direction.LONG, "1000", "50000", 4_000L);

        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PaperExecutionPositionReconciliationCoordinator()
                        .reconcileIfFilled(filledBuy(), expected, "  ", 9_000L));
    }

    // --- K. FILLED with negative reconciledAtEpochMs is rejected through the existing contract ---

    @Test
    void filledWithNegativeReconciledAtEpochMsRejected() {
        PositionSnapshot expected = snapshot("BTCUSDT", Direction.LONG, "1000", "50000", 4_000L);

        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PaperExecutionPositionReconciliationCoordinator()
                        .reconcileIfFilled(filledBuy(), expected, "recon-1", -1L));
    }

    // --- L. Determinism across fresh coordinator instances ---

    @Test
    void determinismAcrossFreshCoordinatorInstances() {
        PaperExecutionResult firstExecution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);
        PaperExecutionResult secondExecution =
                execution(PaperExecutionSide.BUY, PaperExecutionStatus.FILLED, "1000", "50000", 4_000L);
        assertEquals(firstExecution, secondExecution);

        PositionSnapshot firstExpected = snapshot("BTCUSDT", Direction.LONG, "1000", "50000", 4_000L);
        PositionSnapshot secondExpected = snapshot("BTCUSDT", Direction.LONG, "1000", "50000", 4_000L);
        assertEquals(firstExpected, secondExpected);

        Optional<PositionReconciliationResult> firstResult = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(firstExecution, firstExpected, "recon-1", 9_000L);
        Optional<PositionReconciliationResult> secondResult = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(secondExecution, secondExpected, "recon-1", 9_000L);

        assertEquals(firstResult, secondResult);
    }
}
