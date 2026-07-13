package com.ptengine.bingx.market;

import java.math.BigDecimal;

/**
 * Immutable, deterministic single public trade observation for the {@code BTC-USDT} BingX
 * USDT-M perpetual swap instrument, as read from the public {@code
 * GET /openApi/swap/v2/quote/trades} response.
 *
 * <p>This type carries no ordering claim: {@link #tradedAtEpochMs} is the wire value as reported
 * by BingX for this element, but no first/last/latest/chronological meaning is asserted by this
 * record or by {@link BingxPublicMarketDataClient}.
 */
public record BingxPerpetualTrade(
        String symbol,
        long tradedAtEpochMs,
        boolean buyerMaker,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal quoteQuantity) {

    private static final String REQUIRED_SYMBOL = "BTC-USDT";

    public BingxPerpetualTrade {
        if (symbol == null) {
            throw new BingxPublicMarketDataException("symbol must not be null");
        }
        if (!REQUIRED_SYMBOL.equals(symbol)) {
            throw new BingxPublicMarketDataException(
                    "symbol must be exactly \"" + REQUIRED_SYMBOL + "\", was: " + symbol);
        }
        if (tradedAtEpochMs < 0) {
            throw new BingxPublicMarketDataException("tradedAtEpochMs must be non-negative, was: " + tradedAtEpochMs);
        }
        if (price == null) {
            throw new BingxPublicMarketDataException("price must not be null");
        }
        if (quantity == null) {
            throw new BingxPublicMarketDataException("quantity must not be null");
        }
        if (quoteQuantity == null) {
            throw new BingxPublicMarketDataException("quoteQuantity must not be null");
        }
        if (price.signum() <= 0) {
            throw new BingxPublicMarketDataException("price must be positive, was: " + price);
        }
        if (quantity.signum() <= 0) {
            throw new BingxPublicMarketDataException("quantity must be positive, was: " + quantity);
        }
        if (quoteQuantity.signum() <= 0) {
            throw new BingxPublicMarketDataException("quoteQuantity must be positive, was: " + quoteQuantity);
        }
    }
}
