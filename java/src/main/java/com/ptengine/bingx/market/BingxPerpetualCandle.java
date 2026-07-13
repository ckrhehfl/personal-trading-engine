package com.ptengine.bingx.market;

import java.math.BigDecimal;

/**
 * Immutable, deterministic single public 15-minute candle observation for the {@code BTC-USDT}
 * BingX USDT-M perpetual swap instrument, as read from the public {@code
 * GET /openApi/swap/v3/quote/klines} response.
 *
 * <p>{@code symbol} and {@code interval} are not echoed on the wire; this record injects the
 * locked constants for both rather than trusting a wire-provided value, matching how {@link
 * BingxPerpetualTrade#symbol} is already injected in Candidate 18.
 *
 * <p>This type carries no ordering or closed-candle claim: {@link #openTimeEpochMs} is the wire
 * {@code time} value as reported by BingX for this element, but no first/last/latest/chronological
 * meaning, and no claim that this candle is closed rather than still forming, is asserted by this
 * record or by {@link BingxPublicMarketDataClient}.
 */
public record BingxPerpetualCandle(
        String symbol,
        String interval,
        long openTimeEpochMs,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume) {

    private static final String REQUIRED_SYMBOL = "BTC-USDT";
    private static final String REQUIRED_INTERVAL = "15m";

    public BingxPerpetualCandle {
        if (symbol == null) {
            throw new BingxPublicMarketDataException("symbol must not be null");
        }
        if (!REQUIRED_SYMBOL.equals(symbol)) {
            throw new BingxPublicMarketDataException(
                    "symbol must be exactly \"" + REQUIRED_SYMBOL + "\", was: " + symbol);
        }
        if (interval == null) {
            throw new BingxPublicMarketDataException("interval must not be null");
        }
        if (!REQUIRED_INTERVAL.equals(interval)) {
            throw new BingxPublicMarketDataException(
                    "interval must be exactly \"" + REQUIRED_INTERVAL + "\", was: " + interval);
        }
        if (openTimeEpochMs < 0) {
            throw new BingxPublicMarketDataException(
                    "openTimeEpochMs must be non-negative, was: " + openTimeEpochMs);
        }
        if (open == null) {
            throw new BingxPublicMarketDataException("open must not be null");
        }
        if (high == null) {
            throw new BingxPublicMarketDataException("high must not be null");
        }
        if (low == null) {
            throw new BingxPublicMarketDataException("low must not be null");
        }
        if (close == null) {
            throw new BingxPublicMarketDataException("close must not be null");
        }
        if (volume == null) {
            throw new BingxPublicMarketDataException("volume must not be null");
        }
        if (open.signum() <= 0) {
            throw new BingxPublicMarketDataException("open must be positive, was: " + open);
        }
        if (high.signum() <= 0) {
            throw new BingxPublicMarketDataException("high must be positive, was: " + high);
        }
        if (low.signum() <= 0) {
            throw new BingxPublicMarketDataException("low must be positive, was: " + low);
        }
        if (close.signum() <= 0) {
            throw new BingxPublicMarketDataException("close must be positive, was: " + close);
        }
        if (volume.signum() < 0) {
            throw new BingxPublicMarketDataException("volume must not be negative, was: " + volume);
        }
    }
}
