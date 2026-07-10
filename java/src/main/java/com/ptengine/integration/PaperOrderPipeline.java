package com.ptengine.integration;

import com.ptengine.contract.OrderIntent;
import com.ptengine.oms.Order;
import com.ptengine.oms.OrderRegistry;
import com.ptengine.paper.PaperBroker;
import com.ptengine.paper.PaperExecutionMetadata;
import com.ptengine.paper.PaperExecutionResult;
import com.ptengine.paper.PaperExecutionStatus;
import com.ptengine.paper.PaperMarketSnapshot;
import com.ptengine.risk.RiskDecision;
import com.ptengine.risk.RiskDecisionMetadata;
import com.ptengine.risk.RiskGateway;
import com.ptengine.risk.RiskOutcome;
import com.ptengine.risk.RiskRule;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic in-process composition of a real {@link RiskGateway}, {@link OrderRegistry}, and
 * {@link PaperBroker}: one canonical path from an {@link OrderIntent} to a paper-trading outcome.
 *
 * <p>{@link #process} accepts no {@link RiskDecision} and no PASS/authority shortcut of any kind
 * &mdash; the only way this class obtains a decision is by calling the real {@link
 * RiskGateway#evaluate}. On BLOCK, the order is registered and rejected without the
 * instrument-match check or {@link PaperBroker} ever running. On PASS, the instrument-match check
 * runs before registration, so a known-invalid execution input cannot leave a newly accepted order
 * behind in the registry; no rollback semantics are needed or invented.
 *
 * <p>This proves composition only: no persistence, restart recovery, concurrency, position/PnL
 * accounting, reconciliation, or production risk-rule values. {@link RiskRule}, {@link
 * OrderRegistry}, and {@link PaperBroker} remain independently usable public component APIs; this
 * class does not claim direct invocation of those components by some other caller is impossible.
 */
public final class PaperOrderPipeline {

    private final RiskGateway riskGateway;
    private final OrderRegistry orderRegistry;
    private final PaperBroker paperBroker;

    public PaperOrderPipeline(List<RiskRule> rules, OrderRegistry orderRegistry) {
        this.riskGateway = new RiskGateway(rules);
        this.orderRegistry = Objects.requireNonNull(orderRegistry, "orderRegistry");
        this.paperBroker = new PaperBroker();
    }

    public PaperOrderPipelineResult process(
            OrderIntent intent,
            RiskDecisionMetadata riskDecisionMetadata,
            String orderId,
            String clientOrderId,
            PaperMarketSnapshot marketSnapshot,
            PaperExecutionMetadata executionMetadata) {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(riskDecisionMetadata, "riskDecisionMetadata");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(clientOrderId, "clientOrderId");
        Objects.requireNonNull(marketSnapshot, "marketSnapshot");
        Objects.requireNonNull(executionMetadata, "executionMetadata");

        RiskDecision riskDecision = riskGateway.evaluate(intent, riskDecisionMetadata);

        if (riskDecision.outcome() == RiskOutcome.BLOCK) {
            Order order = orderRegistry.register(orderId, clientOrderId);
            order.reject();
            return new PaperOrderPipelineResult(
                    riskDecision, orderId, clientOrderId, order.state(), Optional.empty());
        }

        if (!marketSnapshot.instrument().equals(intent.instrument())) {
            throw new PaperOrderPipelineException(
                    "marketSnapshot.instrument() must equal intent.instrument(): snapshot="
                            + marketSnapshot.instrument() + " intent=" + intent.instrument());
        }

        Order order = orderRegistry.register(orderId, clientOrderId);
        order.accept();

        PaperExecutionResult executionResult =
                paperBroker.execute(intent, riskDecision, marketSnapshot, executionMetadata);
        if (executionResult.status() == PaperExecutionStatus.FILLED) {
            order.fill();
        }

        return new PaperOrderPipelineResult(
                riskDecision, orderId, clientOrderId, order.state(), Optional.of(executionResult));
    }

    /**
     * Thrown when a real PASS decision's paired {@link PaperMarketSnapshot} targets a different
     * instrument than the {@link OrderIntent}, before any {@link OrderRegistry} mutation occurs.
     */
    public static final class PaperOrderPipelineException extends RuntimeException {
        public PaperOrderPipelineException(String message) {
            super(message);
        }
    }
}
