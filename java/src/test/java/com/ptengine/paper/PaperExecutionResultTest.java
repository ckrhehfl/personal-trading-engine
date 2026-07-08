package com.ptengine.paper;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaperExecutionResultTest {

    private static PaperExecutionResult result(PaperExecutionStatus status, BigDecimal executionPrice) {
        return new PaperExecutionResult(
                "exec-1",
                "intent-1",
                "decision-1",
                "BTCUSDT",
                PaperExecutionSide.BUY,
                new BigDecimal("100"),
                status,
                executionPrice,
                4_000L);
    }

    @Test
    void filledWithPositivePriceAccepted() {
        result(PaperExecutionStatus.FILLED, new BigDecimal("50000"));
    }

    @Test
    void filledWithNonPositivePriceRejected() {
        assertThrows(
                InvalidPaperExecutionException.class, () -> result(PaperExecutionStatus.FILLED, BigDecimal.ZERO));
    }

    @Test
    void noFillWithNullPriceAccepted() {
        result(PaperExecutionStatus.NO_FILL, null);
    }

    @Test
    void filledWithNullPriceRejected() {
        assertThrows(InvalidPaperExecutionException.class, () -> result(PaperExecutionStatus.FILLED, null));
    }

    @Test
    void noFillWithNonNullPriceRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> result(PaperExecutionStatus.NO_FILL, new BigDecimal("50000")));
    }

    @Test
    void nonPositiveRequestedNotionalRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> new PaperExecutionResult(
                        "exec-1",
                        "intent-1",
                        "decision-1",
                        "BTCUSDT",
                        PaperExecutionSide.BUY,
                        BigDecimal.ZERO,
                        PaperExecutionStatus.NO_FILL,
                        null,
                        4_000L));
    }

    @Test
    void negativeEvaluatedAtEpochMsRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> new PaperExecutionResult(
                        "exec-1",
                        "intent-1",
                        "decision-1",
                        "BTCUSDT",
                        PaperExecutionSide.BUY,
                        new BigDecimal("100"),
                        PaperExecutionStatus.NO_FILL,
                        null,
                        -1L));
    }

    @Test
    void blankIdentifierRejected() {
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> new PaperExecutionResult(
                        " ",
                        "intent-1",
                        "decision-1",
                        "BTCUSDT",
                        PaperExecutionSide.BUY,
                        new BigDecimal("100"),
                        PaperExecutionStatus.NO_FILL,
                        null,
                        4_000L));
    }

    @Test
    void overlengthIdentifierRejected() {
        String tooLong = "X".repeat(129);
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> new PaperExecutionResult(
                        tooLong,
                        "intent-1",
                        "decision-1",
                        "BTCUSDT",
                        PaperExecutionSide.BUY,
                        new BigDecimal("100"),
                        PaperExecutionStatus.NO_FILL,
                        null,
                        4_000L));
    }
}
