package com.ptengine.paper;

import com.ptengine.contract.Direction;
import com.ptengine.contract.IntentType;
import com.ptengine.contract.OrderIntent;
import com.ptengine.contract.OrderType;
import com.ptengine.risk.RiskDecision;
import com.ptengine.risk.RiskOutcome;
import java.math.BigDecimal;

/**
 * Pure-domain, deterministic paper execution boundary.
 *
 * <p>Given the same valid {@link OrderIntent}, matching {@code PASS} {@link RiskDecision},
 * explicit {@link PaperMarketSnapshot}, and explicit {@link PaperExecutionMetadata}, {@link
 * #execute} returns an equal {@link PaperExecutionResult} every time. No hidden clock, random
 * source, network, exchange, or OMS mutation.
 *
 * <p>A raw {@link OrderIntent} alone is not sufficient authority to execute: a matching {@code
 * PASS} {@link RiskDecision} is mandatory. This class does not call {@code RiskGateway}, does not
 * know about current position state, and does not mutate {@code Order}/{@code OrderRegistry}.
 * Runtime wiring of Risk → OMS → PaperBroker is a later Candidate's responsibility.
 */
public final class PaperBroker {

    public PaperExecutionResult execute(
            OrderIntent intent,
            RiskDecision riskDecision,
            PaperMarketSnapshot marketSnapshot,
            PaperExecutionMetadata metadata) {
        if (intent == null) {
            throw new InvalidPaperExecutionException("intent must not be null");
        }
        if (riskDecision == null) {
            throw new InvalidPaperExecutionException("riskDecision must not be null");
        }
        if (marketSnapshot == null) {
            throw new InvalidPaperExecutionException("marketSnapshot must not be null");
        }
        if (metadata == null) {
            throw new InvalidPaperExecutionException("metadata must not be null");
        }

        if (riskDecision.outcome() != RiskOutcome.PASS) {
            throw new InvalidPaperExecutionException(
                    "riskDecision outcome must be PASS to execute, was: " + riskDecision.outcome());
        }
        if (!riskDecision.intentId().equals(intent.intentId())) {
            throw new InvalidPaperExecutionException(
                    "riskDecision.intentId() must equal intent.intentId(): riskDecision="
                            + riskDecision.intentId() + " intent=" + intent.intentId());
        }
        if (!marketSnapshot.instrument().equals(intent.instrument())) {
            throw new InvalidPaperExecutionException(
                    "marketSnapshot.instrument() must equal intent.instrument(): snapshot="
                            + marketSnapshot.instrument() + " intent=" + intent.instrument());
        }

        PaperExecutionSide side = sideFor(intent.intentType(), intent.direction());
        BigDecimal executionPrice;
        PaperExecutionStatus status;

        switch (intent.orderType()) {
            case MARKET -> {
                executionPrice = bestPriceFor(side, marketSnapshot);
                status = PaperExecutionStatus.FILLED;
            }
            case LIMIT -> {
                boolean crosses = side == PaperExecutionSide.BUY
                        ? marketSnapshot.bestAsk().compareTo(intent.limitPrice()) <= 0
                        : marketSnapshot.bestBid().compareTo(intent.limitPrice()) >= 0;
                if (crosses) {
                    executionPrice = bestPriceFor(side, marketSnapshot);
                    status = PaperExecutionStatus.FILLED;
                } else {
                    executionPrice = null;
                    status = PaperExecutionStatus.NO_FILL;
                }
            }
            default -> throw new InvalidPaperExecutionException("unsupported orderType: " + intent.orderType());
        }

        return new PaperExecutionResult(
                metadata.executionId(),
                intent.intentId(),
                riskDecision.decisionId(),
                intent.instrument(),
                side,
                intent.requestedNotional(),
                status,
                executionPrice,
                metadata.evaluatedAtEpochMs());
    }

    /**
     * Economic execution direction only: ENTER+LONG/EXIT+SHORT are BUY, EXIT+LONG/ENTER+SHORT are
     * SELL. Does not decide hedge vs one-way position mode.
     */
    private static PaperExecutionSide sideFor(IntentType intentType, Direction direction) {
        boolean enter = intentType == IntentType.ENTER;
        boolean isLong = direction == Direction.LONG;
        if (enter) {
            return isLong ? PaperExecutionSide.BUY : PaperExecutionSide.SELL;
        }
        return isLong ? PaperExecutionSide.SELL : PaperExecutionSide.BUY;
    }

    private static BigDecimal bestPriceFor(PaperExecutionSide side, PaperMarketSnapshot marketSnapshot) {
        return side == PaperExecutionSide.BUY ? marketSnapshot.bestAsk() : marketSnapshot.bestBid();
    }
}
