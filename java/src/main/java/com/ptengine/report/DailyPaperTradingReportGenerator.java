package com.ptengine.report;

import com.ptengine.integration.PaperOrderPipelineResult;
import com.ptengine.oms.OrderState;
import com.ptengine.paper.PaperExecutionStatus;
import com.ptengine.reconciliation.PositionReconciliationResult;
import com.ptengine.risk.RiskOutcome;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, pure-domain generator of a {@link DailyPaperTradingReport} from caller-supplied
 * {@link PaperOrderPipelineResult} and {@link PositionReconciliationResult} snapshots.
 *
 * <p>No clock, no ID generation, no runtime loop, no scheduling, no persistence, no network/file
 * access, no Telegram/alert sink, no exchange adapter, no account state. Consumes only
 * already-produced result snapshots; adds no runtime authority.
 */
public final class DailyPaperTradingReportGenerator {

    public DailyPaperTradingReport generate(
            String reportId,
            LocalDate tradingDate,
            long generatedAtEpochMs,
            List<PaperOrderPipelineResult> pipelineResults,
            List<PositionReconciliationResult> reconciliationResults) {
        Objects.requireNonNull(reportId, "reportId");
        Objects.requireNonNull(tradingDate, "tradingDate");
        Objects.requireNonNull(pipelineResults, "pipelineResults");
        Objects.requireNonNull(reconciliationResults, "reconciliationResults");

        int riskPassCount = 0;
        int riskBlockCount = 0;
        int omsAcceptedCount = 0;
        int omsFilledCount = 0;
        int omsRejectedCount = 0;
        int paperExecutionCount = 0;
        int paperFilledCount = 0;
        int paperNoFillCount = 0;

        for (PaperOrderPipelineResult result : pipelineResults) {
            Objects.requireNonNull(result, "pipelineResults element");

            RiskOutcome outcome = result.riskDecision().outcome();
            if (outcome == RiskOutcome.PASS) {
                riskPassCount++;
            } else if (outcome == RiskOutcome.BLOCK) {
                riskBlockCount++;
            }

            OrderState orderState = result.orderState();
            if (orderState == OrderState.ACCEPTED) {
                omsAcceptedCount++;
            } else if (orderState == OrderState.FILLED) {
                omsFilledCount++;
            } else if (orderState == OrderState.REJECTED) {
                omsRejectedCount++;
            }

            if (result.paperExecutionResult().isPresent()) {
                paperExecutionCount++;
                PaperExecutionStatus status = result.paperExecutionResult().get().status();
                if (status == PaperExecutionStatus.FILLED) {
                    paperFilledCount++;
                } else if (status == PaperExecutionStatus.NO_FILL) {
                    paperNoFillCount++;
                }
            }
        }

        int reconciliationMatchCount = 0;
        int reconciliationMismatchCount = 0;
        List<PositionReconciliationResult.Reason> mismatchReasons = new ArrayList<>();

        for (PositionReconciliationResult result : reconciliationResults) {
            Objects.requireNonNull(result, "reconciliationResults element");

            if (result.status() == PositionReconciliationResult.Status.MATCH) {
                reconciliationMatchCount++;
            } else if (result.status() == PositionReconciliationResult.Status.MISMATCH) {
                reconciliationMismatchCount++;
                mismatchReasons.addAll(result.reasons());
            }
        }

        return new DailyPaperTradingReport(
                reportId,
                tradingDate,
                generatedAtEpochMs,
                pipelineResults.size(),
                riskPassCount,
                riskBlockCount,
                omsAcceptedCount,
                omsFilledCount,
                omsRejectedCount,
                paperExecutionCount,
                paperFilledCount,
                paperNoFillCount,
                reconciliationResults.size(),
                reconciliationMatchCount,
                reconciliationMismatchCount,
                mismatchReasons);
    }
}
