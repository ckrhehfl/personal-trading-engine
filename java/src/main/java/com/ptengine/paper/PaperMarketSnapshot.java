package com.ptengine.paper;

import com.ptengine.contract.ContractLimits;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Explicit, deterministic single-instrument top-of-book snapshot supplied by the caller.
 *
 * <p>No order-book depth, size/liquidity, trade stream, volatility, or stale-data policy. No
 * internal clock: {@code observedAtEpochMs} is caller-supplied.
 */
public record PaperMarketSnapshot(String instrument, BigDecimal bestBid, BigDecimal bestAsk, long observedAtEpochMs) {

    public PaperMarketSnapshot {
        requireValidIdentifier(instrument, "instrument");
        Objects.requireNonNull(bestBid, "bestBid");
        Objects.requireNonNull(bestAsk, "bestAsk");
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

    private static void requireValidIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidPaperExecutionException(fieldName + " must not be blank");
        }
        if (value.length() > ContractLimits.MAX_IDENTIFIER_LENGTH) {
            throw new InvalidPaperExecutionException(
                    fieldName + " must be at most " + ContractLimits.MAX_IDENTIFIER_LENGTH + " characters, was: "
                            + value.length());
        }
    }
}
