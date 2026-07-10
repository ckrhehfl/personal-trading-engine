package com.ptengine.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ptengine.contract.ContractLimits;
import com.ptengine.contract.Direction;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionReconcilerTest {

    private static PositionSnapshot snapshot(
            String instrument, Direction direction, String notional, String price, long capturedAtEpochMs) {
        return new PositionSnapshot(
                instrument, direction, new BigDecimal(notional), new BigDecimal(price), capturedAtEpochMs);
    }

    private static PositionSnapshot btcLong() {
        return snapshot("BTCUSDT", Direction.LONG, "1000", "50000", 1_000L);
    }

    // --- A. Matching snapshots produce MATCH ---

    @Test
    void matchingSnapshotsProduceMatchWithZeroReasons() {
        PositionSnapshot expected = btcLong();
        PositionSnapshot observed = snapshot("BTCUSDT", Direction.LONG, "1000", "50000", 5_000L);

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MATCH, result.status());
        assertTrue(result.reasons().isEmpty());
        assertEquals(expected, result.expected());
        assertEquals(observed, result.observed());
        assertEquals("recon-1", result.reconciliationId());
        assertEquals(9_000L, result.reconciledAtEpochMs());
    }

    // --- B. BigDecimal scale-only differences are treated as equal ---

    @Test
    void notionalScaleOnlyDifferenceMatches() {
        PositionSnapshot expected = snapshot("BTCUSDT", Direction.LONG, "100.0", "50000", 1_000L);
        PositionSnapshot observed = snapshot("BTCUSDT", Direction.LONG, "100.00", "50000", 1_000L);

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MATCH, result.status());
    }

    @Test
    void priceScaleOnlyDifferenceMatches() {
        PositionSnapshot expected = snapshot("BTCUSDT", Direction.LONG, "1000", "50000.0", 1_000L);
        PositionSnapshot observed = snapshot("BTCUSDT", Direction.LONG, "1000", "50000.00", 1_000L);

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MATCH, result.status());
    }

    // --- C-F. Single-field mismatches ---

    @Test
    void instrumentMismatchProducesOnlyInstrumentMismatch() {
        PositionSnapshot expected = btcLong();
        PositionSnapshot observed = snapshot("ETHUSDT", Direction.LONG, "1000", "50000", 1_000L);

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MISMATCH, result.status());
        assertEquals(List.of(PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH), result.reasons());
    }

    @Test
    void directionMismatchProducesOnlyDirectionMismatch() {
        PositionSnapshot expected = btcLong();
        PositionSnapshot observed = snapshot("BTCUSDT", Direction.SHORT, "1000", "50000", 1_000L);

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MISMATCH, result.status());
        assertEquals(List.of(PositionReconciliationResult.Reason.DIRECTION_MISMATCH), result.reasons());
    }

    @Test
    void positionNotionalMismatchProducesOnlyPositionNotionalMismatch() {
        PositionSnapshot expected = btcLong();
        PositionSnapshot observed = snapshot("BTCUSDT", Direction.LONG, "2000", "50000", 1_000L);

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MISMATCH, result.status());
        assertEquals(List.of(PositionReconciliationResult.Reason.POSITION_NOTIONAL_MISMATCH), result.reasons());
    }

    @Test
    void averageEntryPriceMismatchProducesOnlyAverageEntryPriceMismatch() {
        PositionSnapshot expected = btcLong();
        PositionSnapshot observed = snapshot("BTCUSDT", Direction.LONG, "1000", "51000", 1_000L);

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MISMATCH, result.status());
        assertEquals(List.of(PositionReconciliationResult.Reason.AVERAGE_ENTRY_PRICE_MISMATCH), result.reasons());
    }

    // --- G. Multiple mismatches produce stable ordered reasons ---

    @Test
    void multipleMismatchesProduceStableOrderedReasons() {
        PositionSnapshot expected = btcLong();
        PositionSnapshot observed = snapshot("ETHUSDT", Direction.SHORT, "2000", "51000", 1_000L);

        PositionReconciliationResult result =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);

        assertEquals(PositionReconciliationResult.Status.MISMATCH, result.status());
        assertEquals(
                List.of(
                        PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH,
                        PositionReconciliationResult.Reason.DIRECTION_MISMATCH,
                        PositionReconciliationResult.Reason.POSITION_NOTIONAL_MISMATCH,
                        PositionReconciliationResult.Reason.AVERAGE_ENTRY_PRICE_MISMATCH),
                result.reasons());
    }

    // --- H. PositionSnapshot validation ---

    @Test
    void nullInstrumentRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot(null, Direction.LONG, new BigDecimal("1000"), new BigDecimal("50000"), 1_000L));
    }

    @Test
    void blankInstrumentRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot(
                        "   ", Direction.LONG, new BigDecimal("1000"), new BigDecimal("50000"), 1_000L));
    }

    @Test
    void overlengthInstrumentRejected() {
        String tooLong = "B".repeat(ContractLimits.MAX_IDENTIFIER_LENGTH + 1);
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot(
                        tooLong, Direction.LONG, new BigDecimal("1000"), new BigDecimal("50000"), 1_000L));
    }

    @Test
    void instrumentAtMaxLengthAccepted() {
        String maxLength = "B".repeat(ContractLimits.MAX_IDENTIFIER_LENGTH);
        PositionSnapshot snapshot = new PositionSnapshot(
                maxLength, Direction.LONG, new BigDecimal("1000"), new BigDecimal("50000"), 1_000L);
        assertEquals(maxLength, snapshot.instrument());
    }

    @Test
    void nullDirectionRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot(
                        "BTCUSDT", null, new BigDecimal("1000"), new BigDecimal("50000"), 1_000L));
    }

    @Test
    void nullPositionNotionalRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot("BTCUSDT", Direction.LONG, null, new BigDecimal("50000"), 1_000L));
    }

    @Test
    void zeroPositionNotionalRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot(
                        "BTCUSDT", Direction.LONG, BigDecimal.ZERO, new BigDecimal("50000"), 1_000L));
    }

    @Test
    void negativePositionNotionalRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot(
                        "BTCUSDT", Direction.LONG, new BigDecimal("-1"), new BigDecimal("50000"), 1_000L));
    }

    @Test
    void nullAverageEntryPriceRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot("BTCUSDT", Direction.LONG, new BigDecimal("1000"), null, 1_000L));
    }

    @Test
    void zeroAverageEntryPriceRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot(
                        "BTCUSDT", Direction.LONG, new BigDecimal("1000"), BigDecimal.ZERO, 1_000L));
    }

    @Test
    void negativeAverageEntryPriceRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot(
                        "BTCUSDT", Direction.LONG, new BigDecimal("1000"), new BigDecimal("-1"), 1_000L));
    }

    @Test
    void negativeCapturedAtEpochMsRejected() {
        assertThrows(
                PositionSnapshot.InvalidPositionSnapshotException.class,
                () -> new PositionSnapshot(
                        "BTCUSDT", Direction.LONG, new BigDecimal("1000"), new BigDecimal("50000"), -1L));
    }

    // --- I. PositionReconciliationResult validation ---

    private static PositionSnapshot expectedSnap() {
        return btcLong();
    }

    private static PositionSnapshot observedSnap() {
        return btcLong();
    }

    @Test
    void nullReconciliationIdRejected() {
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        null, PositionReconciliationResult.Status.MATCH, List.of(), expectedSnap(), observedSnap(), 1_000L));
    }

    @Test
    void blankReconciliationIdRejected() {
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        "  ", PositionReconciliationResult.Status.MATCH, List.of(), expectedSnap(), observedSnap(), 1_000L));
    }

    @Test
    void overlengthReconciliationIdRejected() {
        String tooLong = "r".repeat(ContractLimits.MAX_IDENTIFIER_LENGTH + 1);
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        tooLong,
                        PositionReconciliationResult.Status.MATCH,
                        List.of(),
                        expectedSnap(),
                        observedSnap(),
                        1_000L));
    }

    @Test
    void nullStatusRejected() {
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        "recon-1", null, List.of(), expectedSnap(), observedSnap(), 1_000L));
    }

    @Test
    void nullReasonsListRejected() {
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        "recon-1",
                        PositionReconciliationResult.Status.MATCH,
                        null,
                        expectedSnap(),
                        observedSnap(),
                        1_000L));
    }

    @Test
    void nullReasonElementRejected() {
        List<PositionReconciliationResult.Reason> withNull = new java.util.ArrayList<>();
        withNull.add(PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH);
        withNull.add(null);
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        "recon-1",
                        PositionReconciliationResult.Status.MISMATCH,
                        withNull,
                        expectedSnap(),
                        observedSnap(),
                        1_000L));
    }

    @Test
    void matchWithNonEmptyReasonsRejected() {
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        "recon-1",
                        PositionReconciliationResult.Status.MATCH,
                        List.of(PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH),
                        expectedSnap(),
                        observedSnap(),
                        1_000L));
    }

    @Test
    void mismatchWithEmptyReasonsRejected() {
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        "recon-1",
                        PositionReconciliationResult.Status.MISMATCH,
                        List.of(),
                        expectedSnap(),
                        observedSnap(),
                        1_000L));
    }

    @Test
    void nullExpectedRejected() {
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        "recon-1", PositionReconciliationResult.Status.MATCH, List.of(), null, observedSnap(), 1_000L));
    }

    @Test
    void nullObservedRejected() {
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        "recon-1", PositionReconciliationResult.Status.MATCH, List.of(), expectedSnap(), null, 1_000L));
    }

    @Test
    void negativeReconciledAtEpochMsRejected() {
        assertThrows(
                PositionReconciliationResult.InvalidPositionReconciliationResultException.class,
                () -> new PositionReconciliationResult(
                        "recon-1",
                        PositionReconciliationResult.Status.MATCH,
                        List.of(),
                        expectedSnap(),
                        observedSnap(),
                        -1L));
    }

    @Test
    void reasonsAreImmutable() {
        List<PositionReconciliationResult.Reason> inputReasons =
                new java.util.ArrayList<>(
                        List.of(PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH));
        PositionReconciliationResult result = new PositionReconciliationResult(
                "recon-1",
                PositionReconciliationResult.Status.MISMATCH,
                inputReasons,
                expectedSnap(),
                observedSnap(),
                1_000L);

        inputReasons.clear();

        assertEquals(
                List.of(PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH),
                result.reasons());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.reasons().add(PositionReconciliationResult.Reason.DIRECTION_MISMATCH));
    }

    // --- J. Reconciler rejects null expected/observed ---

    @Test
    void reconcileRejectsNullExpected() {
        assertThrows(
                NullPointerException.class,
                () -> new PositionReconciler().reconcile(null, btcLong(), "recon-1", 1_000L));
    }

    @Test
    void reconcileRejectsNullObserved() {
        assertThrows(
                NullPointerException.class,
                () -> new PositionReconciler().reconcile(btcLong(), null, "recon-1", 1_000L));
    }

    // --- K. Determinism across fresh instances ---

    @Test
    void determinismAcrossFreshInstances() {
        PositionSnapshot expected = btcLong();
        PositionSnapshot observed = snapshot("ETHUSDT", Direction.SHORT, "2000", "51000", 2_000L);

        PositionReconciliationResult first =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);
        PositionReconciliationResult second =
                new PositionReconciler().reconcile(expected, observed, "recon-1", 9_000L);

        assertEquals(first, second);
    }
}
