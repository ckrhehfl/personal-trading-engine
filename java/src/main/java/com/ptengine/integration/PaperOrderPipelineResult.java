package com.ptengine.integration;

import com.ptengine.oms.OrderState;
import com.ptengine.paper.PaperBroker;
import com.ptengine.paper.PaperExecutionResult;
import com.ptengine.risk.RiskDecision;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic, immutable snapshot of one {@link PaperOrderPipeline#process} outcome: the real
 * {@link RiskDecision}, the resulting OMS order identity/state, and the optional real {@link
 * PaperExecutionResult} (present only when the real {@link PaperBroker} was actually invoked).
 */
public record PaperOrderPipelineResult(
        RiskDecision riskDecision,
        String orderId,
        String clientOrderId,
        OrderState orderState,
        Optional<PaperExecutionResult> paperExecutionResult) {

    public PaperOrderPipelineResult {
        Objects.requireNonNull(riskDecision, "riskDecision");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(clientOrderId, "clientOrderId");
        Objects.requireNonNull(orderState, "orderState");
        Objects.requireNonNull(paperExecutionResult, "paperExecutionResult");
    }
}
