package com.ptengine.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ptengine.contract.Direction;
import com.ptengine.contract.IntentType;
import com.ptengine.contract.OrderIntent;
import com.ptengine.contract.OrderType;
import com.ptengine.oms.OrderRegistry;
import com.ptengine.oms.OrderState;
import com.ptengine.paper.PaperExecutionMetadata;
import com.ptengine.paper.PaperExecutionResult;
import com.ptengine.paper.PaperExecutionStatus;
import com.ptengine.paper.PaperMarketSnapshot;
import com.ptengine.reconciliation.PaperExecutionPositionReconciliationCoordinator;
import com.ptengine.reconciliation.PositionReconciliationResult;
import com.ptengine.reconciliation.PositionSnapshot;
import com.ptengine.risk.RiskDecisionMetadata;
import com.ptengine.risk.RiskOutcome;
import com.ptengine.risk.RiskRejectReason;
import com.ptengine.risk.RiskRule;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Integration-only proof that a real {@link PaperOrderPipeline} outcome can be reconciled end to
 * end by the real production {@link PaperExecutionPositionReconciliationCoordinator} (Candidate
 * 17), using only real components.
 *
 * <p>Adds no production code of its own: this is a test-only composition of already-merged
 * Candidate 8 ({@link PaperOrderPipeline}) and Candidate 17 ({@link
 * PaperExecutionPositionReconciliationCoordinator}, which itself composes the already-merged
 * Candidate 9/10 projector and reconciler).
 */
class PaperOrderPositionReconciliationIntegrationTest {

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

    private static OrderIntent marketIntent(String intentId, String instrument, Direction direction) {
        return intent(intentId, instrument, IntentType.ENTER, direction, OrderType.MARKET, null);
    }

    private static OrderIntent nonCrossingLimitIntent(String intentId, String instrument) {
        return intent(intentId, instrument, IntentType.ENTER, Direction.LONG, OrderType.LIMIT, new BigDecimal("50100"));
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
        return orderIntent -> Optional.empty();
    }

    private static RiskRule alwaysReject(RiskRejectReason reason) {
        return orderIntent -> Optional.of(reason);
    }

    // --- A. PASS MARKET LONG/BUY-side fill projects and reconciles MATCH ---

    @Test
    void passMarketLongFillProjectsAndReconcilesMatch() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT", Direction.LONG);

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.PASS, result.riskDecision().outcome());
        assertEquals(OrderState.FILLED, result.orderState());
        assertTrue(result.paperExecutionResult().isPresent());
        PaperExecutionResult executionResult = result.paperExecutionResult().get();
        assertEquals(PaperExecutionStatus.FILLED, executionResult.status());

        PositionSnapshot expected = new PositionSnapshot(
                "BTCUSDT", Direction.LONG, new BigDecimal("100"), new BigDecimal("50000"), 4_000L);
        Optional<PositionReconciliationResult> reconciliation = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(executionResult, expected, "recon-1", 9_000L);

        assertTrue(reconciliation.isPresent());
        PositionSnapshot observed = reconciliation.get().observed();
        assertEquals("BTCUSDT", observed.instrument());
        assertEquals(Direction.LONG, observed.direction());
        assertEquals(0, new BigDecimal("100").compareTo(observed.positionNotional()));
        assertEquals(0, new BigDecimal("50000").compareTo(observed.averageEntryPrice()));
        assertEquals(4_000L, observed.capturedAtEpochMs());

        assertEquals(PositionReconciliationResult.Status.MATCH, reconciliation.get().status());
        assertTrue(reconciliation.get().reasons().isEmpty());
    }

    // --- B. PASS MARKET SHORT/SELL-side fill projects and reconciles MATCH ---

    @Test
    void passMarketShortFillProjectsAndReconcilesMatch() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT", Direction.SHORT);

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.PASS, result.riskDecision().outcome());
        assertEquals(OrderState.FILLED, result.orderState());
        assertTrue(result.paperExecutionResult().isPresent());
        PaperExecutionResult executionResult = result.paperExecutionResult().get();
        assertEquals(PaperExecutionStatus.FILLED, executionResult.status());

        PositionSnapshot expected = new PositionSnapshot(
                "BTCUSDT", Direction.SHORT, new BigDecimal("100"), new BigDecimal("49900"), 4_000L);
        Optional<PositionReconciliationResult> reconciliation = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(executionResult, expected, "recon-1", 9_000L);

        assertTrue(reconciliation.isPresent());
        PositionSnapshot observed = reconciliation.get().observed();
        assertEquals("BTCUSDT", observed.instrument());
        assertEquals(Direction.SHORT, observed.direction());
        assertEquals(0, new BigDecimal("100").compareTo(observed.positionNotional()));
        assertEquals(0, new BigDecimal("49900").compareTo(observed.averageEntryPrice()));
        assertEquals(4_000L, observed.capturedAtEpochMs());

        assertEquals(PositionReconciliationResult.Status.MATCH, reconciliation.get().status());
        assertTrue(reconciliation.get().reasons().isEmpty());
    }

    // --- C. PASS LIMIT non-crossing NO_FILL produces no projection/reconciliation result ---

    @Test
    void passNonCrossingLimitProducesNoProjection() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());
        OrderIntent intent = nonCrossingLimitIntent("intent-1", "BTCUSDT");

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("50100"), new BigDecimal("50200")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.PASS, result.riskDecision().outcome());
        assertEquals(OrderState.ACCEPTED, result.orderState());
        assertTrue(result.paperExecutionResult().isPresent());
        PaperExecutionResult executionResult = result.paperExecutionResult().get();
        assertEquals(PaperExecutionStatus.NO_FILL, executionResult.status());

        PositionSnapshot expected = new PositionSnapshot(
                "BTCUSDT", Direction.LONG, new BigDecimal("100"), new BigDecimal("50100"), 4_000L);
        Optional<PositionReconciliationResult> reconciliation = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(executionResult, expected, "recon-1", 9_000L);
        assertFalse(reconciliation.isPresent());
    }

    // --- D. Risk BLOCK does not create paper execution, projection, or reconciliation ---

    @Test
    void riskBlockProducesNoExecutionOrProjection() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(
                List.of(alwaysReject(RiskRejectReason.RISK_STATE_DEGRADED)), new OrderRegistry());
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT", Direction.LONG);

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), execMetadata("exec-1"));

        assertEquals(RiskOutcome.BLOCK, result.riskDecision().outcome());
        assertEquals(OrderState.REJECTED, result.orderState());
        assertTrue(result.paperExecutionResult().isEmpty());

        PositionSnapshot expected = new PositionSnapshot(
                "BTCUSDT", Direction.LONG, new BigDecimal("100"), new BigDecimal("50000"), 4_000L);
        Optional<PositionReconciliationResult> reconciliation = result.paperExecutionResult()
                .flatMap(executionResult -> new PaperExecutionPositionReconciliationCoordinator()
                        .reconcileIfFilled(executionResult, expected, "recon-1", 9_000L));
        assertTrue(reconciliation.isEmpty());
    }

    // --- E. FILLED but wrong expected direction reconciles as DIRECTION_MISMATCH ---

    @Test
    void filledWithWrongExpectedDirectionReconcilesAsDirectionMismatch() {
        PaperOrderPipeline pipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT", Direction.LONG);

        PaperOrderPipelineResult result = pipeline.process(
                intent, riskMetadata("decision-1"), "order-1", "client-1",
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000")), execMetadata("exec-1"));

        PaperExecutionResult executionResult = result.paperExecutionResult().orElseThrow();
        PositionSnapshot wrongExpected = new PositionSnapshot(
                "BTCUSDT", Direction.SHORT, new BigDecimal("100"), new BigDecimal("50000"), 4_000L);

        PositionReconciliationResult reconciliation = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(executionResult, wrongExpected, "recon-1", 9_000L)
                .orElseThrow();

        assertEquals(PositionReconciliationResult.Status.MISMATCH, reconciliation.status());
        assertEquals(List.of(PositionReconciliationResult.Reason.DIRECTION_MISMATCH), reconciliation.reasons());
    }

    // --- F. Determinism across fresh component/coordinator instances ---

    @Test
    void determinismAcrossFreshComponentInstances() {
        OrderIntent intent = marketIntent("intent-1", "BTCUSDT", Direction.LONG);
        RiskDecisionMetadata riskMeta = riskMetadata("decision-1");
        PaperMarketSnapshot marketSnapshot =
                snapshot("BTCUSDT", new BigDecimal("49900"), new BigDecimal("50000"));
        PaperExecutionMetadata execMeta = execMetadata("exec-1");
        PositionSnapshot expected = new PositionSnapshot(
                "BTCUSDT", Direction.LONG, new BigDecimal("100"), new BigDecimal("50000"), 4_000L);

        PaperOrderPipeline firstPipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());
        PaperOrderPipeline secondPipeline = new PaperOrderPipeline(List.of(alwaysPass()), new OrderRegistry());

        PaperOrderPipelineResult firstResult =
                firstPipeline.process(intent, riskMeta, "order-1", "client-1", marketSnapshot, execMeta);
        PaperOrderPipelineResult secondResult =
                secondPipeline.process(intent, riskMeta, "order-1", "client-1", marketSnapshot, execMeta);
        assertEquals(firstResult, secondResult);

        PositionReconciliationResult firstReconciliation = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(firstResult.paperExecutionResult().orElseThrow(), expected, "recon-1", 9_000L)
                .orElseThrow();
        PositionReconciliationResult secondReconciliation = new PaperExecutionPositionReconciliationCoordinator()
                .reconcileIfFilled(secondResult.paperExecutionResult().orElseThrow(), expected, "recon-1", 9_000L)
                .orElseThrow();
        assertEquals(firstReconciliation, secondReconciliation);
    }
}
