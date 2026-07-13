package com.ptengine.bingx.market;

/**
 * Single fail-closed exception boundary for {@link BingxPublicMarketDataClient} and {@link
 * BingxPerpetualTrade}: URI validation, transport failure, interruption, non-200 HTTP status,
 * oversized response, malformed JSON, business-code/envelope/batch-size rejection, and per-trade
 * field/domain validation all surface as this single type.
 *
 * <p>Messages never include credentials, a full response body, account data, or unbounded
 * external input.
 */
public final class BingxPublicMarketDataException extends RuntimeException {

    public BingxPublicMarketDataException(String message) {
        super(message);
    }

    public BingxPublicMarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
