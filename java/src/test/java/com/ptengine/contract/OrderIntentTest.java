package com.ptengine.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderIntentTest {

    private static OrderIntent limitIntent(BigDecimal limitPrice) {
        return new OrderIntent(
                OrderIntent.SCHEMA_VERSION,
                "intent-1",
                "strategy-1",
                "BTCUSDT",
                IntentType.ENTER,
                Direction.LONG,
                OrderType.LIMIT,
                new BigDecimal("100"),
                limitPrice,
                1_000L);
    }

    private static OrderIntent marketIntent(BigDecimal limitPrice) {
        return new OrderIntent(
                OrderIntent.SCHEMA_VERSION,
                "intent-1",
                "strategy-1",
                "BTCUSDT",
                IntentType.ENTER,
                Direction.LONG,
                OrderType.MARKET,
                new BigDecimal("100"),
                limitPrice,
                1_000L);
    }

    @Test
    void validLimitIntentConstructs() {
        OrderIntent intent = limitIntent(new BigDecimal("50000"));
        assertEquals(OrderType.LIMIT, intent.orderType());
        assertEquals(new BigDecimal("50000"), intent.limitPrice());
    }

    @Test
    void validMarketIntentConstructs() {
        OrderIntent intent = marketIntent(null);
        assertEquals(OrderType.MARKET, intent.orderType());
    }

    @Test
    void limitWithoutPriceRejected() {
        assertThrows(InvalidOrderIntentException.class, () -> limitIntent(null));
    }

    @Test
    void limitWithNonPositivePriceRejected() {
        assertThrows(InvalidOrderIntentException.class, () -> limitIntent(BigDecimal.ZERO));
    }

    @Test
    void marketWithPriceRejected() {
        assertThrows(InvalidOrderIntentException.class, () -> marketIntent(new BigDecimal("50000")));
    }

    @Test
    void nonPositiveNotionalRejected() {
        assertThrows(
                InvalidOrderIntentException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "intent-1",
                        "strategy-1",
                        "BTCUSDT",
                        IntentType.ENTER,
                        Direction.LONG,
                        OrderType.MARKET,
                        BigDecimal.ZERO,
                        null,
                        1_000L));
    }

    @Test
    void blankIntentIdRejected() {
        assertThrows(
                InvalidOrderIntentException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "  ",
                        "strategy-1",
                        "BTCUSDT",
                        IntentType.ENTER,
                        Direction.LONG,
                        OrderType.MARKET,
                        new BigDecimal("100"),
                        null,
                        1_000L));
    }

    @Test
    void blankStrategyIdRejected() {
        assertThrows(
                InvalidOrderIntentException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "intent-1",
                        "",
                        "BTCUSDT",
                        IntentType.ENTER,
                        Direction.LONG,
                        OrderType.MARKET,
                        new BigDecimal("100"),
                        null,
                        1_000L));
    }

    @Test
    void blankInstrumentRejected() {
        assertThrows(
                InvalidOrderIntentException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "intent-1",
                        "strategy-1",
                        "",
                        IntentType.ENTER,
                        Direction.LONG,
                        OrderType.MARKET,
                        new BigDecimal("100"),
                        null,
                        1_000L));
    }

    @Test
    void negativeTimestampRejected() {
        assertThrows(
                InvalidOrderIntentException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "intent-1",
                        "strategy-1",
                        "BTCUSDT",
                        IntentType.ENTER,
                        Direction.LONG,
                        OrderType.MARKET,
                        new BigDecimal("100"),
                        null,
                        -1L));
    }

    @Test
    void nullIntentIdRejected() {
        assertThrows(
                InvalidOrderIntentException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        null,
                        "strategy-1",
                        "BTCUSDT",
                        IntentType.ENTER,
                        Direction.LONG,
                        OrderType.MARKET,
                        new BigDecimal("100"),
                        null,
                        1_000L));
    }

    @Test
    void negativeNotionalRejected() {
        assertThrows(
                InvalidOrderIntentException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "intent-1",
                        "strategy-1",
                        "BTCUSDT",
                        IntentType.ENTER,
                        Direction.LONG,
                        OrderType.MARKET,
                        new BigDecimal("-1"),
                        null,
                        1_000L));
    }

    @Test
    void negativeLimitPriceRejected() {
        assertThrows(InvalidOrderIntentException.class, () -> limitIntent(new BigDecimal("-1")));
    }

    @Test
    void nullIntentTypeThrowsNpe() {
        assertThrows(
                NullPointerException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "intent-1",
                        "strategy-1",
                        "BTCUSDT",
                        null,
                        Direction.LONG,
                        OrderType.MARKET,
                        new BigDecimal("100"),
                        null,
                        1_000L));
    }

    @Test
    void nullDirectionThrowsNpe() {
        assertThrows(
                NullPointerException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "intent-1",
                        "strategy-1",
                        "BTCUSDT",
                        IntentType.ENTER,
                        null,
                        OrderType.MARKET,
                        new BigDecimal("100"),
                        null,
                        1_000L));
    }

    @Test
    void nullOrderTypeThrowsNpe() {
        assertThrows(
                NullPointerException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "intent-1",
                        "strategy-1",
                        "BTCUSDT",
                        IntentType.ENTER,
                        Direction.LONG,
                        null,
                        new BigDecimal("100"),
                        null,
                        1_000L));
    }

    @Test
    void nullRequestedNotionalThrowsNpe() {
        assertThrows(
                NullPointerException.class,
                () -> new OrderIntent(
                        OrderIntent.SCHEMA_VERSION,
                        "intent-1",
                        "strategy-1",
                        "BTCUSDT",
                        IntentType.ENTER,
                        Direction.LONG,
                        OrderType.MARKET,
                        null,
                        null,
                        1_000L));
    }

    @Test
    void wrongSchemaVersionRejected() {
        assertThrows(
                InvalidOrderIntentException.class,
                () -> new OrderIntent(
                        "0.2.0",
                        "intent-1",
                        "strategy-1",
                        "BTCUSDT",
                        IntentType.ENTER,
                        Direction.LONG,
                        OrderType.MARKET,
                        new BigDecimal("100"),
                        null,
                        1_000L));
    }
}
