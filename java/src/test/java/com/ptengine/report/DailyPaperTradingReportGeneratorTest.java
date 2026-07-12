package com.ptengine.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ptengine.contract.Direction;
import com.ptengine.integration.PaperOrderPipelineResult;
import com.ptengine.oms.OrderState;
import com.ptengine.paper.PaperExecutionResult;
import com.ptengine.paper.PaperExecutionSide;
import com.ptengine.paper.PaperExecutionStatus;
import com.ptengine.reconciliation.PositionReconciliationResult;
import com.ptengine.reconciliation.PositionSnapshot;
import com.ptengine.risk.RiskDecision;
import com.ptengine.risk.RiskOutcome;
import com.ptengine.risk.RiskRejectReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link DailyPaperTradingReportGenerator} and {@link DailyPaperTradingReport} using only
 * real value objects and the real generator: no mocks, no reflection, no sleeps, no randomness,
 * no clock.
 */
class DailyPaperTradingReportGeneratorTest {

    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 7, 12);

    private static RiskDecision passDecision(String decisionId) {
        return new RiskDecision(RiskDecision.SCHEMA_VERSION, decisionId, "intent-x", RiskOutcome.PASS, List.of(), 1_000L);
    }

    private static RiskDecision blockDecision(String decisionId) {
        return new RiskDecision(
                RiskDecision.SCHEMA_VERSION,
                decisionId,
                "intent-x",
                RiskOutcome.BLOCK,
                List.of(RiskRejectReason.RISK_STATE_DEGRADED.name()),
                1_000L);
    }

    private static PaperExecutionResult filledExecution(String executionId) {
        return new PaperExecutionResult(
                executionId, "intent-1", "decision-1", "BTCUSDT", PaperExecutionSide.BUY, new BigDecimal("100"),
                PaperExecutionStatus.FILLED, new BigDecimal("50000"), 2_000L);
    }

    private static PaperExecutionResult noFillExecution(String executionId) {
        return new PaperExecutionResult(
                executionId, "intent-2", "decision-2", "BTCUSDT", PaperExecutionSide.BUY, new BigDecimal("100"),
                PaperExecutionStatus.NO_FILL, null, 2_000L);
    }

    private static PositionSnapshot snapshot() {
        return new PositionSnapshot("BTCUSDT", Direction.LONG, new BigDecimal("100"), new BigDecimal("50000"), 4_000L);
    }

    private static List<PaperOrderPipelineResult> mixedPipelineResults() {
        PaperOrderPipelineResult passFilled = new PaperOrderPipelineResult(
                passDecision("decision-1"), "order-1", "client-1", OrderState.FILLED,
                Optional.of(filledExecution("exec-1")));
        PaperOrderPipelineResult passAcceptedNoFill = new PaperOrderPipelineResult(
                passDecision("decision-2"), "order-2", "client-2", OrderState.ACCEPTED,
                Optional.of(noFillExecution("exec-2")));
        PaperOrderPipelineResult blockRejected = new PaperOrderPipelineResult(
                blockDecision("decision-3"), "order-3", "client-3", OrderState.REJECTED, Optional.empty());
        return List.of(passFilled, passAcceptedNoFill, blockRejected);
    }

    private static List<PositionReconciliationResult> mixedReconciliationResults() {
        PositionSnapshot snapshot = snapshot();
        PositionReconciliationResult match = new PositionReconciliationResult(
                "recon-1", PositionReconciliationResult.Status.MATCH, List.of(), snapshot, snapshot, 5_000L);
        PositionReconciliationResult mismatchOne = new PositionReconciliationResult(
                "recon-2", PositionReconciliationResult.Status.MISMATCH,
                List.of(PositionReconciliationResult.Reason.DIRECTION_MISMATCH,
                        PositionReconciliationResult.Reason.POSITION_NOTIONAL_MISMATCH),
                snapshot, snapshot, 5_000L);
        PositionReconciliationResult mismatchTwo = new PositionReconciliationResult(
                "recon-3", PositionReconciliationResult.Status.MISMATCH,
                List.of(PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH), snapshot, snapshot, 5_000L);
        return List.of(match, mismatchOne, mismatchTwo);
    }

    // --- A. Empty day produces a valid zero-count report ---

    @Test
    void emptyDayProducesValidZeroCountReport() {
        DailyPaperTradingReport report = new DailyPaperTradingReportGenerator()
                .generate("report-empty", TRADING_DATE, 0L, List.of(), List.of());

        assertEquals("report-empty", report.reportId());
        assertEquals(TRADING_DATE, report.tradingDate());
        assertEquals(0L, report.generatedAtEpochMs());
        assertEquals(0, report.pipelineResultCount());
        assertEquals(0, report.riskPassCount());
        assertEquals(0, report.riskBlockCount());
        assertEquals(0, report.omsAcceptedCount());
        assertEquals(0, report.omsFilledCount());
        assertEquals(0, report.omsRejectedCount());
        assertEquals(0, report.paperExecutionCount());
        assertEquals(0, report.paperFilledCount());
        assertEquals(0, report.paperNoFillCount());
        assertEquals(0, report.reconciliationResultCount());
        assertEquals(0, report.reconciliationMatchCount());
        assertEquals(0, report.reconciliationMismatchCount());
        assertTrue(report.mismatchReasons().isEmpty());

        String expectedText = "reportId=report-empty\n"
                + "tradingDate=2026-07-12\n"
                + "generatedAtEpochMs=0\n"
                + "pipelineResultCount=0\n"
                + "riskPassCount=0\n"
                + "riskBlockCount=0\n"
                + "omsAcceptedCount=0\n"
                + "omsFilledCount=0\n"
                + "omsRejectedCount=0\n"
                + "paperExecutionCount=0\n"
                + "paperFilledCount=0\n"
                + "paperNoFillCount=0\n"
                + "reconciliationResultCount=0\n"
                + "reconciliationMatchCount=0\n"
                + "reconciliationMismatchCount=0\n"
                + "mismatchReasons=";
        assertEquals(expectedText, report.toPlainText());
    }

    // --- B. Mixed pipeline results are counted correctly ---

    @Test
    void mixedPipelineResultsAreCountedCorrectly() {
        DailyPaperTradingReport report = new DailyPaperTradingReportGenerator()
                .generate("report-mixed", TRADING_DATE, 1_234L, mixedPipelineResults(), List.of());

        assertEquals(3, report.pipelineResultCount());
        assertEquals(2, report.riskPassCount());
        assertEquals(1, report.riskBlockCount());
        assertEquals(1, report.omsAcceptedCount());
        assertEquals(1, report.omsFilledCount());
        assertEquals(1, report.omsRejectedCount());
        assertEquals(2, report.paperExecutionCount());
        assertEquals(1, report.paperFilledCount());
        assertEquals(1, report.paperNoFillCount());
    }

    // --- C. Reconciliation results are counted correctly, mismatchReasons flattened in order ---

    @Test
    void reconciliationResultsAreCountedCorrectlyWithStableMismatchOrder() {
        DailyPaperTradingReport report = new DailyPaperTradingReportGenerator()
                .generate("report-recon", TRADING_DATE, 1_234L, List.of(), mixedReconciliationResults());

        assertEquals(3, report.reconciliationResultCount());
        assertEquals(1, report.reconciliationMatchCount());
        assertEquals(2, report.reconciliationMismatchCount());
        assertEquals(
                List.of(
                        PositionReconciliationResult.Reason.DIRECTION_MISMATCH,
                        PositionReconciliationResult.Reason.POSITION_NOTIONAL_MISMATCH,
                        PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH),
                report.mismatchReasons());
    }

    // --- D. Plain text renderer is deterministic and complete ---

    @Test
    void plainTextRendererIsDeterministicAndComplete() {
        DailyPaperTradingReportGenerator generator = new DailyPaperTradingReportGenerator();
        DailyPaperTradingReport reportOne = generator.generate(
                "report-d", TRADING_DATE, 9_999L, mixedPipelineResults(), mixedReconciliationResults());
        DailyPaperTradingReport reportTwo = generator.generate(
                "report-d", TRADING_DATE, 9_999L, mixedPipelineResults(), mixedReconciliationResults());

        String expectedText = "reportId=report-d\n"
                + "tradingDate=2026-07-12\n"
                + "generatedAtEpochMs=9999\n"
                + "pipelineResultCount=3\n"
                + "riskPassCount=2\n"
                + "riskBlockCount=1\n"
                + "omsAcceptedCount=1\n"
                + "omsFilledCount=1\n"
                + "omsRejectedCount=1\n"
                + "paperExecutionCount=2\n"
                + "paperFilledCount=1\n"
                + "paperNoFillCount=1\n"
                + "reconciliationResultCount=3\n"
                + "reconciliationMatchCount=1\n"
                + "reconciliationMismatchCount=2\n"
                + "mismatchReasons=DIRECTION_MISMATCH,POSITION_NOTIONAL_MISMATCH,INSTRUMENT_MISMATCH";

        assertEquals(expectedText, reportOne.toPlainText());
        assertEquals(reportOne, reportTwo);
        assertEquals(reportOne.toPlainText(), reportTwo.toPlainText());
    }

    // --- E. Generator rejects invalid inputs ---

    @Test
    void generatorRejectsNullReportId() {
        assertThrows(NullPointerException.class,
                () -> new DailyPaperTradingReportGenerator().generate(null, TRADING_DATE, 0L, List.of(), List.of()));
    }

    @Test
    void generatorRejectsNullTradingDate() {
        assertThrows(NullPointerException.class,
                () -> new DailyPaperTradingReportGenerator().generate("report-1", null, 0L, List.of(), List.of()));
    }

    @Test
    void generatorRejectsNullPipelineResultsList() {
        assertThrows(NullPointerException.class,
                () -> new DailyPaperTradingReportGenerator().generate("report-1", TRADING_DATE, 0L, null, List.of()));
    }

    @Test
    void generatorRejectsNullReconciliationResultsList() {
        assertThrows(NullPointerException.class,
                () -> new DailyPaperTradingReportGenerator().generate("report-1", TRADING_DATE, 0L, List.of(), null));
    }

    @Test
    void generatorRejectsNullPipelineResultElement() {
        List<PaperOrderPipelineResult> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new DailyPaperTradingReportGenerator()
                .generate("report-1", TRADING_DATE, 0L, withNull, List.of()));
    }

    @Test
    void generatorRejectsNullReconciliationResultElement() {
        List<PositionReconciliationResult> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> new DailyPaperTradingReportGenerator()
                .generate("report-1", TRADING_DATE, 0L, List.of(), withNull));
    }

    // --- F. DailyPaperTradingReport validation rejects invalid direct construction ---

    @Test
    void rejectsBlankReportId() {
        assertThrows(DailyPaperTradingReport.InvalidDailyPaperTradingReportException.class,
                () -> new DailyPaperTradingReport("   ", TRADING_DATE, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
    }

    @Test
    void rejectsOverlengthReportId() {
        String tooLong = "a".repeat(DailyPaperTradingReport.MAX_IDENTIFIER_LENGTH + 1);
        assertThrows(DailyPaperTradingReport.InvalidDailyPaperTradingReportException.class,
                () -> new DailyPaperTradingReport(tooLong, TRADING_DATE, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
    }

    @Test
    void rejectsNegativeGeneratedAtEpochMs() {
        assertThrows(DailyPaperTradingReport.InvalidDailyPaperTradingReportException.class,
                () -> new DailyPaperTradingReport("report-1", TRADING_DATE, -1L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
    }

    @Test
    void rejectsNegativeCount() {
        assertThrows(DailyPaperTradingReport.InvalidDailyPaperTradingReportException.class,
                () -> new DailyPaperTradingReport("report-1", TRADING_DATE, 0L, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
    }

    @Test
    void rejectsRiskCountsExceedingPipelineResultCount() {
        assertThrows(DailyPaperTradingReport.InvalidDailyPaperTradingReportException.class,
                () -> new DailyPaperTradingReport("report-1", TRADING_DATE, 0L, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
    }

    @Test
    void rejectsPaperFillCountsExceedingPaperExecutionCount() {
        assertThrows(DailyPaperTradingReport.InvalidDailyPaperTradingReportException.class,
                () -> new DailyPaperTradingReport("report-1", TRADING_DATE, 0L, 0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, List.of()));
    }

    @Test
    void rejectsReconciliationCountsExceedingReconciliationResultCount() {
        assertThrows(DailyPaperTradingReport.InvalidDailyPaperTradingReportException.class,
                () -> new DailyPaperTradingReport("report-1", TRADING_DATE, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, List.of()));
    }

    @Test
    void rejectsNullMismatchReasons() {
        assertThrows(DailyPaperTradingReport.InvalidDailyPaperTradingReportException.class,
                () -> new DailyPaperTradingReport("report-1", TRADING_DATE, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null));
    }

    @Test
    void rejectsNullMismatchReasonsElement() {
        List<PositionReconciliationResult.Reason> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(DailyPaperTradingReport.InvalidDailyPaperTradingReportException.class,
                () -> new DailyPaperTradingReport("report-1", TRADING_DATE, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, withNull));
    }

    // --- G. mismatchReasons is immutable and defensively copied ---

    @Test
    void mismatchReasonsIsDefensivelyCopiedAndImmutable() {
        List<PositionReconciliationResult.Reason> mutable =
                new ArrayList<>(List.of(PositionReconciliationResult.Reason.DIRECTION_MISMATCH));

        DailyPaperTradingReport report =
                new DailyPaperTradingReport("report-1", TRADING_DATE, 0L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, mutable);

        mutable.add(PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH);
        assertEquals(List.of(PositionReconciliationResult.Reason.DIRECTION_MISMATCH), report.mismatchReasons());

        assertThrows(UnsupportedOperationException.class,
                () -> report.mismatchReasons().add(PositionReconciliationResult.Reason.INSTRUMENT_MISMATCH));
    }

    // --- H. Determinism across fresh generator instances ---

    @Test
    void determinismAcrossFreshGeneratorInstances() {
        List<PaperOrderPipelineResult> pipelineResults = mixedPipelineResults();
        List<PositionReconciliationResult> reconciliationResults = mixedReconciliationResults();

        DailyPaperTradingReport firstReport = new DailyPaperTradingReportGenerator()
                .generate("report-h", TRADING_DATE, 7_777L, pipelineResults, reconciliationResults);
        DailyPaperTradingReport secondReport = new DailyPaperTradingReportGenerator()
                .generate("report-h", TRADING_DATE, 7_777L, pipelineResults, reconciliationResults);

        assertEquals(firstReport, secondReport);
        assertEquals(firstReport.toPlainText(), secondReport.toPlainText());
    }
}
