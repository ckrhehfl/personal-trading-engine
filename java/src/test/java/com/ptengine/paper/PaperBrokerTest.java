package com.ptengine.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ptengine.contract.Direction;
import com.ptengine.contract.IntentType;
import com.ptengine.contract.OrderIntent;
import com.ptengine.contract.OrderType;
import com.ptengine.risk.RiskDecision;
import com.ptengine.risk.RiskOutcome;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaperBrokerTest {

    private final PaperBroker broker = new PaperBroker();

    private static OrderIntent intent(
            String intentId, String instrument, IntentType intentType, Direction direction, OrderType orderType,
            BigDecimal limitPrice) {
        return new OrderIntent(
                OrderIntent.SCHEMA_VERSION,
                intentId,
                "strategy-1",
                instrument,
                intentType,
                direction,
                orderType,
                new BigDecimal("100"),
                limitPrice,
                1_000L);
    }

    private static OrderIntent marketIntent(IntentType intentType, Direction direction) {
        return intent("intent-1", "BTCUSDT", intentType, direction, OrderType.MARKET, null);
    }

    private static RiskDecision passDecision(String intentId) {
        return new RiskDecision(RiskDecision.SCHEMA_VERSION, "decision-1", intentId, RiskOutcome.PASS, List.of(), 2_000L);
    }

    private static RiskDecision blockDecision(String intentId) {
        return new RiskDecision(
                RiskDecision.SCHEMA_VERSION,
                "decision-1",
                intentId,
                RiskOutcome.BLOCK,
                List.of("RISK_ENGINE_ERROR"),
                2_000L);
    }

    private static PaperMarketSnapshot snapshot(String instrument, BigDecimal bid, BigDecimal ask) {
        return new PaperMarketSnapshot(instrument, bid, ask, 3_000L);
    }

    private static PaperExecutionMetadata metadata() {
        return new PaperExecutionMetadata("exec-1", 4_000L);
    }

    // --- A. Risk authority ---

    @Test
    void matchingPassAccepted() {
        OrderIntent intent = marketIntent(IntentType.ENTER, Direction.LONG);
        PaperExecutionResult result = broker.execute(
                intent, passDecision("intent-1"), snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")),
                metadata());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
    }

    @Test
    void blockRejected() {
        OrderIntent intent = marketIntent(IntentType.ENTER, Direction.LONG);
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> broker.execute(
                        intent, blockDecision("intent-1"),
                        snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata()));
    }

    @Test
    void mismatchedIntentIdRejected() {
        OrderIntent intent = marketIntent(IntentType.ENTER, Direction.LONG);
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> broker.execute(
                        intent, passDecision("other-intent"),
                        snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata()));
    }

    @Test
    void nullRiskDecisionRejected() {
        OrderIntent intent = marketIntent(IntentType.ENTER, Direction.LONG);
        assertThrows(
                NullPointerException.class,
                () -> broker.execute(
                        intent, null, snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")),
                        metadata()));
    }

    @Test
    void nullIntentRejected() {
        assertThrows(
                NullPointerException.class,
                () -> broker.execute(
                        null, passDecision("intent-1"),
                        snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata()));
    }

    @Test
    void nullMarketSnapshotRejected() {
        OrderIntent intent = marketIntent(IntentType.ENTER, Direction.LONG);
        assertThrows(
                NullPointerException.class,
                () -> broker.execute(intent, passDecision("intent-1"), null, metadata()));
    }

    @Test
    void nullExecutionMetadataRejected() {
        OrderIntent intent = marketIntent(IntentType.ENTER, Direction.LONG);
        assertThrows(
                NullPointerException.class,
                () -> broker.execute(
                        intent, passDecision("intent-1"),
                        snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), null));
    }

    // --- snapshot/intent instrument mismatch ---

    @Test
    void snapshotIntentInstrumentMismatchRejected() {
        OrderIntent intent = marketIntent(IntentType.ENTER, Direction.LONG);
        assertThrows(
                InvalidPaperExecutionException.class,
                () -> broker.execute(
                        intent, passDecision("intent-1"),
                        snapshot("ETHUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata()));
    }

    // --- D. MARKET side mapping / price ---

    @Test
    void enterLongMarketBuysAtAsk() {
        PaperExecutionResult result = broker.execute(
                marketIntent(IntentType.ENTER, Direction.LONG), passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata());
        assertEquals(PaperExecutionSide.BUY, result.side());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("50000"), result.executionPrice());
    }

    @Test
    void exitLongMarketSellsAtBid() {
        PaperExecutionResult result = broker.execute(
                marketIntent(IntentType.EXIT, Direction.LONG), passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata());
        assertEquals(PaperExecutionSide.SELL, result.side());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("49900"), result.executionPrice());
    }

    @Test
    void enterShortMarketSellsAtBid() {
        PaperExecutionResult result = broker.execute(
                marketIntent(IntentType.ENTER, Direction.SHORT), passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata());
        assertEquals(PaperExecutionSide.SELL, result.side());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("49900"), result.executionPrice());
    }

    @Test
    void exitShortMarketBuysAtAsk() {
        PaperExecutionResult result = broker.execute(
                marketIntent(IntentType.EXIT, Direction.SHORT), passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata());
        assertEquals(PaperExecutionSide.BUY, result.side());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("50000"), result.executionPrice());
    }

    // --- E. LIMIT behavior ---
    // BUY-side cases use ENTER+LONG; SELL-side cases use EXIT+LONG (side mapping already proven above).

    @Test
    void crossingBuyLimitFillsAtAsk() {
        OrderIntent intent = intent(
                "intent-1", "BTCUSDT", IntentType.ENTER, Direction.LONG, OrderType.LIMIT, new BigDecimal("50100"));
        PaperExecutionResult result = broker.execute(
                intent, passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("50000"), result.executionPrice());
    }

    @Test
    void nonCrossingBuyLimitNoFill() {
        OrderIntent intent = intent(
                "intent-1", "BTCUSDT", IntentType.ENTER, Direction.LONG, OrderType.LIMIT, new BigDecimal("50100"));
        PaperExecutionResult result = broker.execute(
                intent, passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("50100"), new BigDecimal("50200")), metadata());
        assertEquals(PaperExecutionStatus.NO_FILL, result.status());
        assertNull(result.executionPrice());
    }

    @Test
    void crossingSellLimitFillsAtBid() {
        OrderIntent intent = intent(
                "intent-1", "BTCUSDT", IntentType.EXIT, Direction.LONG, OrderType.LIMIT, new BigDecimal("49900"));
        PaperExecutionResult result = broker.execute(
                intent, passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("50000"), new BigDecimal("50100")), metadata());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("50000"), result.executionPrice());
    }

    @Test
    void nonCrossingSellLimitNoFill() {
        OrderIntent intent = intent(
                "intent-1", "BTCUSDT", IntentType.EXIT, Direction.LONG, OrderType.LIMIT, new BigDecimal("49900"));
        PaperExecutionResult result = broker.execute(
                intent, passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("49800"), new BigDecimal("49900")), metadata());
        assertEquals(PaperExecutionStatus.NO_FILL, result.status());
        assertNull(result.executionPrice());
    }

    @Test
    void exactBuyEqualityFills() {
        OrderIntent intent = intent(
                "intent-1", "BTCUSDT", IntentType.ENTER, Direction.LONG, OrderType.LIMIT, new BigDecimal("50000"));
        PaperExecutionResult result = broker.execute(
                intent, passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("50000"), result.executionPrice());
    }

    @Test
    void exactSellEqualityFills() {
        OrderIntent intent = intent(
                "intent-1", "BTCUSDT", IntentType.EXIT, Direction.LONG, OrderType.LIMIT, new BigDecimal("50000"));
        PaperExecutionResult result = broker.execute(
                intent, passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("50000"), new BigDecimal("50100")), metadata());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("50000"), result.executionPrice());
    }

    @Test
    void crossingBuyLimitUsesBetterAskNotLimitPrice() {
        OrderIntent intent = intent(
                "intent-1", "BTCUSDT", IntentType.ENTER, Direction.LONG, OrderType.LIMIT, new BigDecimal("50000"));
        PaperExecutionResult result = broker.execute(
                intent, passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("48900"), new BigDecimal("49000")), metadata());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("49000"), result.executionPrice());
    }

    @Test
    void crossingSellLimitUsesBetterBidNotLimitPrice() {
        OrderIntent intent = intent(
                "intent-1", "BTCUSDT", IntentType.EXIT, Direction.LONG, OrderType.LIMIT, new BigDecimal("50000"));
        PaperExecutionResult result = broker.execute(
                intent, passDecision("intent-1"),
                snapshot("BTCUSDT", new BigDecimal("51000"), new BigDecimal("51100")), metadata());
        assertEquals(PaperExecutionStatus.FILLED, result.status());
        assertEquals(new BigDecimal("51000"), result.executionPrice());
    }

    // --- F. Result invariants (source fields preserved through PaperBroker) ---

    @Test
    void filledResultPreservesAllSourceFieldsExactly() {
        OrderIntent intent = marketIntent(IntentType.ENTER, Direction.LONG);
        RiskDecision decision = passDecision("intent-1");
        PaperExecutionMetadata metadata = metadata();
        PaperExecutionResult result = broker.execute(
                intent, decision, snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), metadata);

        assertEquals(metadata.executionId(), result.executionId());
        assertEquals(intent.intentId(), result.intentId());
        assertEquals(decision.decisionId(), result.riskDecisionId());
        assertEquals(intent.instrument(), result.instrument());
        assertEquals(intent.requestedNotional(), result.requestedNotional());
        assertEquals(metadata.evaluatedAtEpochMs(), result.evaluatedAtEpochMs());
    }

    // --- G. Determinism ---

    @Test
    void repeatedExecutionWithIdenticalInputsReturnsEqualResult() {
        OrderIntent intent = marketIntent(IntentType.ENTER, Direction.LONG);
        RiskDecision decision = passDecision("intent-1");
        PaperMarketSnapshot snapshot = snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000"));
        PaperExecutionMetadata metadata = metadata();

        PaperExecutionResult first = broker.execute(intent, decision, snapshot, metadata);
        PaperExecutionResult second = broker.execute(intent, decision, snapshot, metadata);

        assertEquals(first, second);
    }
}
