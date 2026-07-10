package com.ptengine.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ptengine.contract.Direction;
import com.ptengine.contract.IntentType;
import com.ptengine.contract.OrderIntent;
import com.ptengine.contract.OrderType;
import com.ptengine.oms.DuplicateClientOrderIdException;
import com.ptengine.oms.OrderRegistry;
import com.ptengine.oms.OrderState;
import com.ptengine.paper.PaperExecutionMetadata;
import com.ptengine.paper.PaperExecutionStatus;
import com.ptengine.paper.PaperMarketSnapshot;
import com.ptengine.risk.RiskDecisionMetadata;
import com.ptengine.risk.RiskOutcome;
import com.ptengine.risk.RiskRejectReason;
import com.ptengine.risk.RiskRule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaperOrderPipelineTest {

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

    private static OrderIntent marketIntent(String intentId, String instrument) {
        return intent(intentId, instrument, IntentType.ENTER, Direction.LONG, OrderType.MARKET, null);
    }

    private static OrderIntent limitIntent(String intentId, String instrument, BigDecimal limitPrice) {
        return intent(intentId, instrument, IntentType.ENTER, Direction.LONG, OrderType.LIMIT, limitPrice);
    }

    private static PaperMarketSnapshot snapshot(String instrument, BigDecimal bid, BigDecimal ask) {
        return new PaperMarketSnapshot(instrument, bid, ask, 3_000L);
    }

    private static RiskDecisionMetadata riskMetadata(String decisionId) {
        return new RiskDecisionMetadata(decisionId, 2_000L);
    }

    private static PaperExecutionMetadata execMetadata(String executionId) {
        return new PaperExecutionMetadata(executionId, 4_000L);
    }

    private static RiskRule alwaysPass() {
        return orderIntent -> java.util.Optional.empty();
    }

    private static RiskRule alwaysReject(RiskRejectReason reason) {
        return orderIntent -> java.util.Optional.of(reason);
    }

    private static RiskRule alwaysThrow() {
        return orderIntent -> {
            throw new RuntimeException("simulated rule failure");
        };
    }

    // --- A. PASS + MARKET fill ---

    @Test
    void passMarketFillReachesFilled() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT");

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.PASS, result.riskDecision().outcome());
        assertEquals("decision-1", result.riskDecision().decisionId());
        assertEquals(OrderState.FILLED, result.orderState());
        assertTrue(result.paperExecutionResult().isPresent());
        assertEquals(PaperExecutionStatus.FILLED, result.paperExecutionResult().get().status());
        assertEquals("exec-1", result.paperExecutionResult().get().executionId());
    }

    // --- B. PASS + non-crossing LIMIT NO_FILL ---

    @Test
    void passNonCrossingLimitLeavesOrderAccepted() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());
        OrderIntent intent = limitIntent("intent-1", "BTCUSDT", new BigDecimal("50100"));

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("50100"), new BigDecimal("50200")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.PASS, result.riskDecision().outcome());
        assertEquals(OrderState.ACCEPTED, result.orderState());
        assertTrue(result.paperExecutionResult().isPresent());
        assertEquals(PaperExecutionStatus.NO_FILL, result.paperExecutionResult().get().status());
    }

    // --- C. Explicit rule rejection ---

    @Test
    void explicitRuleRejectionEndsRejectedWithoutExecution() {
        OrderRegistry registry = new OrderRegistry();
        PaperOrderPipeline pipeline = new PaperOrderPipeline(
                List.of(alwaysReject(RiskRejectReason.RISK_STATE_DEGRADED)), registry);
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT");

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.BLOCK, result.riskDecision().outcome());
        assertEquals(List.of("RISK_STATE_DEGRADED"), result.riskDecision().reasonCodes());
        assertEquals(OrderState.REJECTED, result.orderState());
        assertTrue(result.paperExecutionResult().isEmpty());
        assertEquals(OrderState.REJECTED, registry.findByClientOrderId("client-1").orElseThrow().state());
    }

    // --- D. Zero rules fail closed ---

    @Test
    void zeroRulesFailClosed() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(), new OrderRegistry());
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT");

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.BLOCK, result.riskDecision().outcome());
        assertEquals(List.of("RISK_CONFIGURATION_MISSING"), result.riskDecision().reasonCodes());
        assertEquals(OrderState.REJECTED, result.orderState());
        assertTrue(result.paperExecutionResult().isEmpty());
    }

    // --- E. Risk-rule exception fail closed ---

    @Test
    void ruleExceptionFailsClosed() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(alwaysThrow()), new OrderRegistry());
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT");

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.BLOCK, result.riskDecision().outcome());
        assertEquals(List.of("RISK_ENGINE_ERROR"), result.riskDecision().reasonCodes());
        assertEquals(OrderState.REJECTED, result.orderState());
        assertTrue(result.paperExecutionResult().isEmpty());
    }

    // --- F. BLOCK skips PaperBroker, proven without mocking ---

    @Test
    void blockShortCircuitsBeforePaperBrokerEvenWithMismatchedSnapshot() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(
                List.of(alwaysReject(RiskRejectReason.RISK_STATE_DEGRADED)), new OrderRegistry());
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT");

        // Mismatched instrument: if PaperBroker (or the PASS-only instrument guard) had run,
        // PaperBroker's real instrument-match check would throw InvalidPaperExecutionException.
        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("ETHUSDT", new BigDecimal("49900"), new BigDecimal("50000")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.BLOCK, result.riskDecision().outcome());
        assertEquals(OrderState.REJECTED, result.orderState());
        assertTrue(result.paperExecutionResult().isEmpty());
    }

    // --- G. PASS instrument mismatch fails before OMS registration ---

    @Test
    void passInstrumentMismatchFailsBeforeRegistration() {
        OrderRegistry registry = new OrderRegistry();
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(alwaysPass()), registry);
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT");

        assertThrows(
                PaperOrderPipeline.PaperOrderPipelineException.class,
                () -> pipeline.process(
                        intent, riskMetadata("decision-1"), "order-1", "client-1",
                        snapshot("ETHUSDT", new BigDecimal("49900"), new BigDecimal("50000")),
                        execMetadata("exec-1")));

        assertTrue(registry.findByClientOrderId("client-1").isEmpty());
    }

    // --- H. Duplicate clientOrderId remains enforced ---

    @Test
    void duplicateClientOrderIdRemainsEnforced() {
        OrderRegistry registry = new OrderRegistry();
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(alwaysPass()), registry);
        PaperMarketSnapshot snap = snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000"));

        pipeline.process(
                marketIntent("intent-1", "BTCUSDT"), riskMetadata("decision-1"), "order-1", "client-1", snap,
                execMetadata("exec-1"));

        assertThrows(
                DuplicateClientOrderIdException.class,
                () -> pipeline.process(
                        marketIntent("intent-2", "BTCUSDT"), riskMetadata("decision-2"), "order-2", "client-1",
                        snap, execMetadata("exec-2")));

        assertEquals("order-1", registry.findByClientOrderId("client-1").orElseThrow().orderId());
    }

    // --- I. Determinism across fresh instances ---

    @Test
    void determinismAcrossFreshInstances() {
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT");
        RiskDecisionMetadata riskMeta = riskMetadata("decision-1");
        PaperMarketSnapshot snap = snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000"));
        PaperExecutionMetadata execMeta = execMetadata("exec-1");

        PaperOrderPipeline firstPipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());
        PaperOrderPipeline secondPipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());

        PaperOrderPipelineResult first = firstPipeline.process(intent, riskMeta, "order-1", "client-1", snap, execMeta);
        PaperOrderPipelineResult second =
                secondPipeline.process(intent, riskMeta, "order-1", "client-1", snap, execMeta);

        assertEquals(first, second);
    }
}
