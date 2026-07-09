package com.ptengine.paper;

import java.math.BigDecimal;

/**
 * Explicit, deterministic single-instrument top-of-book snapshot supplied by the caller.
 *
 * <p>No order-book depth, size/liquidity, trade stream, volatility, or stale-data policy. No
 * internal clock: {@code observedAtEpochMs} is caller-supplied.
 */
public record PaperMarketSnapshot(String instrument, BigDecimal bestBid, BigDecimal bestAsk, long observedAtEpochMs) {

    public PaperMarketSnapshot {
        PaperValidation.requireValidIdentifier(instrument, "instrument");
        if (bestBid == null) {
            throw new InvalidPaperExecutionException("bestBid must not be null");
        }
        if (bestAsk == null) {
            throw new InvalidPaperExecutionException("bestAsk must not be null");
        }
        if (bestBid.signum() <= 0) {
            throw new InvalidPaperExecutionException("bestBid must be positive, was: " + bestBid);
        }
        if (bestAsk.signum() <= 0) {
            throw new InvalidPaperExecutionException("bestAsk must be positive, was: " + bestAsk);
        }
        if (bestAsk.compareTo(bestBid) < 0) {
            throw new InvalidPaperExecutionException(
                    "bestAsk must be >= bestBid, bestAsk=" + bestAsk + " bestBid=" + bestBid);
        }
        if (observedAtEpochMs < 0) {
            throw new InvalidPaperExecutionException(
                    "observedAtEpochMs must be non-negative, was: " + observedAtEpochMs);
        }
    }
}
