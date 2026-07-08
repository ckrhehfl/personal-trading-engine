package com.ptengine.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaperMarketSnapshotTest {

    private static PaperMarketSnapshot snapshot(String instrument, BigDecimal bid, BigDecimal ask, long observedAt) {
        return new PaperMarketSnapshot(instrument, bid, ask, observedAt);
    }

    @Test
    void validPositiveBidAskAccepted() {
        PaperMarketSnapshot snapshot = snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000"), 3_000L);
        assertEquals(new BigDecimal("49900"), snapshot.bestBid());
        assertEquals(new BigDecimal("50000"), snapshot.bestAsk());
    }

    @Test
    void zeroBidRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> snapshot("BTCUSDT", BigDecimal.ZERO, new BigDecimal("50000"), 3_000L));
    }

    @Test
    void zeroAskRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> snapshot("BTCUSDT", new BigDecimal("49900"), BigDecimal.ZERO, 3_000L));
    }

    @Test
    void negativeBidRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> snapshot("BTCUSDT", new BigDecimal("-1"), new BigDecimal("50000"), 3_000L));
    }

    @Test
    void negativeAskRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("-1"), 3_000L));
    }

    @Test
    void askBelowBidRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> snapshot("BTCUSDT", new BigDecimal("50000"), new BigDecimal("49900"), 3_000L));
    }

    @Test
    void zeroSpreadAccepted() {
        PaperMarketSnapshot snapshot = snapshot("BTCUSDT", new BigDecimal("50000"), new BigDecimal("50000"), 3_000L);
        assertEquals(snapshot.bestBid(), snapshot.bestAsk());
    }

    @Test
    void blankInstrumentRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> snapshot(" ", new BigDecimal("49900"), new BigDecimal("50000"), 3_000L));
    }

    @Test
    void overlengthInstrumentRejected() {
        String tooLong = "A".repeat(129);
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> snapshot(tooLong, new BigDecimal("49900"), new BigDecimal("50000"), 3_000L));
    }

    @Test
    void negativeObservedAtEpochMsRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000"), -1L));
    }

    @Test
    void nullBidThrowsNpe() {
        assertThrows(
                NullPointerException.class, () -> snapshot("BTCUSDT", null, new BigDecimal("50000"), 3_000L));
    }

    @Test
    void nullAskThrowsNpe() {
        assertThrows(
                NullPointerException.class, () -> snapshot("BTCUSDT", new BigDecimal("49900"), null, 3_000L));
    }
}
