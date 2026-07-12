package com.ptengine.report;

import com.ptengine.contract.ContractLimits;
import com.ptengine.reconciliation.PositionReconciliationResult;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable, deterministic daily summary of already-produced {@code PaperOrderPipelineResult} and
 * {@link PositionReconciliationResult} values.
 *
 * <p>Contains only derived counts and a stable-ordered mismatch-reason flattening. No clock, no
 * ID generation, no network/file/environment access, no persistence, no AccountSnapshot, no
 * fill-to-position accounting, no position/margin mode, no leverage/exposure/loss-limit values,
 * and no live-trading authority.
 */
public record DailyPaperTradingReport(
        String reportId,
        LocalDate tradingDate,
        long generatedAtEpochMs,
        int pipelineResultCount,
        int riskPassCount,
        int riskBlockCount,
        int omsAcceptedCount,
        int omsFilledCount,
        int omsRejectedCount,
        int paperExecutionCount,
        int paperFilledCount,
        int paperNoFillCount,
        int reconciliationResultCount,
        int reconciliationMatchCount,
        int reconciliationMismatchCount,
        List<PositionReconciliationResult.Reason> mismatchReasons) {

    /** Matches {@code common.schema.json}'s {@code nonEmptyIdentifier}: non-blank, max 128 chars. */
    public static final int MAX_IDENTIFIER_LENGTH = ContractLimits.MAX_IDENTIFIER_LENGTH;

    /**
     * @param mismatchReasons wrapped as an immutable list preserving caller order &mdash; the
     *     ordering itself is {@link DailyPaperTradingReportGenerator}'s responsibility, not
     *     re-derived here.
     */
    public DailyPaperTradingReport {
        requireValidIdentifier(reportId, "reportId");
        if (tradingDate == null) {
            throw new InvalidDailyPaperTradingReportException("tradingDate must not be null");
        }
        if (generatedAtEpochMs < 0) {
            throw new InvalidDailyPaperTradingReportException(
                    "generatedAtEpochMs must be non-negative, was: " + generatedAtEpochMs);
        }
        requireNonNegative(pipelineResultCount, "pipelineResultCount");
        requireNonNegative(riskPassCount, "riskPassCount");
        requireNonNegative(riskBlockCount, "riskBlockCount");
        requireNonNegative(omsAcceptedCount, "omsAcceptedCount");
        requireNonNegative(omsFilledCount, "omsFilledCount");
        requireNonNegative(omsRejectedCount, "omsRejectedCount");
        requireNonNegative(paperExecutionCount, "paperExecutionCount");
        requireNonNegative(paperFilledCount, "paperFilledCount");
        requireNonNegative(paperNoFillCount, "paperNoFillCount");
        requireNonNegative(reconciliationResultCount, "reconciliationResultCount");
        requireNonNegative(reconciliationMatchCount, "reconciliationMatchCount");
        requireNonNegative(reconciliationMismatchCount, "reconciliationMismatchCount");

        requireExactSum(
                "riskPassCount + riskBlockCount", sum(riskPassCount, riskBlockCount),
                "pipelineResultCount", pipelineResultCount);
        requireExactSum(
                "omsAcceptedCount + omsFilledCount + omsRejectedCount",
                sum(omsAcceptedCount, omsFilledCount, omsRejectedCount),
                "pipelineResultCount", pipelineResultCount);
        requireExactMatch("riskPassCount", riskPassCount, "paperExecutionCount", paperExecutionCount);
        requireExactMatch("riskBlockCount", riskBlockCount, "omsRejectedCount", omsRejectedCount);
        requireExactMatch("paperFilledCount", paperFilledCount, "omsFilledCount", omsFilledCount);
        requireExactMatch("paperNoFillCount", paperNoFillCount, "omsAcceptedCount", omsAcceptedCount);
        requireExactSum(
                "paperFilledCount + paperNoFillCount", sum(paperFilledCount, paperNoFillCount),
                "paperExecutionCount", paperExecutionCount);
        requireExactSum(
                "reconciliationMatchCount + reconciliationMismatchCount",
                sum(reconciliationMatchCount, reconciliationMismatchCount),
                "reconciliationResultCount", reconciliationResultCount);

        if (mismatchReasons == null) {
            throw new InvalidDailyPaperTradingReportException("mismatchReasons must not be null");
        }
        for (PositionReconciliationResult.Reason reason : mismatchReasons) {
            if (reason == null) {
                throw new InvalidDailyPaperTradingReportException("mismatchReasons must not contain a null element");
            }
        }
        if (reconciliationMismatchCount == 0) {
            if (!mismatchReasons.isEmpty()) {
                throw new InvalidDailyPaperTradingReportException(
                        "reconciliationMismatchCount is 0 but mismatchReasons is non-empty, had: "
                                + mismatchReasons.size() + " reason(s)");
            }
        } else if (mismatchReasons.size() < reconciliationMismatchCount) {
            throw new InvalidDailyPaperTradingReportException(
                    "mismatchReasons.size() (" + mismatchReasons.size()
                            + ") must be >= reconciliationMismatchCount (" + reconciliationMismatchCount + ")");
        }
        mismatchReasons = List.copyOf(mismatchReasons);
    }

    /**
     * Deterministic plain-text rendering of this report's own fields only: no clock, no
     * env/file/network access, no secrets, explicit {@code \n} line endings, no system-locale
     * formatting, no default {@code Object.toString()}/identity output.
     */
    public String toPlainText() {
        StringBuilder text = new StringBuilder();
        text.append("reportId=").append(reportId).append('\n');
        text.append("tradingDate=").append(tradingDate).append('\n');
        text.append("generatedAtEpochMs=").append(generatedAtEpochMs).append('\n');
        text.append("pipelineResultCount=").append(pipelineResultCount).append('\n');
        text.append("riskPassCount=").append(riskPassCount).append('\n');
        text.append("riskBlockCount=").append(riskBlockCount).append('\n');
        text.append("omsAcceptedCount=").append(omsAcceptedCount).append('\n');
        text.append("omsFilledCount=").append(omsFilledCount).append('\n');
        text.append("omsRejectedCount=").append(omsRejectedCount).append('\n');
        text.append("paperExecutionCount=").append(paperExecutionCount).append('\n');
        text.append("paperFilledCount=").append(paperFilledCount).append('\n');
        text.append("paperNoFillCount=").append(paperNoFillCount).append('\n');
        text.append("reconciliationResultCount=").append(reconciliationResultCount).append('\n');
        text.append("reconciliationMatchCount=").append(reconciliationMatchCount).append('\n');
        text.append("reconciliationMismatchCount=").append(reconciliationMismatchCount).append('\n');
        text.append("mismatchReasons=");
        for (int i = 0; i < mismatchReasons.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(mismatchReasons.get(i).name());
        }
        return text.toString();
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new InvalidDailyPaperTradingReportException(fieldName + " must be non-negative, was: " + value);
        }
    }

    /** Promotes to {@code long} before adding, so a huge pair of counts cannot wrap around as int. */
    private static long sum(int a, int b) {
        return (long) a + b;
    }

    /** Promotes to {@code long} before adding, so a huge triple of counts cannot wrap around as int. */
    private static long sum(int a, int b, int c) {
        return (long) a + b + c;
    }

    private static void requireExactSum(String sumLabel, long actualSum, String targetLabel, int target) {
        if (actualSum != target) {
            throw new InvalidDailyPaperTradingReportException(
                    sumLabel + " must equal " + targetLabel + ", was: " + actualSum + " != " + target);
        }
    }

    private static void requireExactMatch(String aLabel, int a, String bLabel, int b) {
        if (a != b) {
            throw new InvalidDailyPaperTradingReportException(aLabel + " must equal " + bLabel + ", was: " + a
                    + " != " + b);
        }
    }

    /** Matches {@code common.schema.json}'s {@code nonEmptyIdentifier}: non-blank, max 128 chars. */
    private static void requireValidIdentifier(String value, String fieldName) {
        if (value == null) {
            throw new InvalidDailyPaperTradingReportException(fieldName + " must not be null");
        }
        if (value.isBlank()) {
            throw new InvalidDailyPaperTradingReportException(fieldName + " must not be blank");
        }
        if (value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new InvalidDailyPaperTradingReportException(
                    fieldName + " must be at most " + MAX_IDENTIFIER_LENGTH + " characters, was: "
                            + value.length());
        }
    }

    /** Thrown when constructing a {@link DailyPaperTradingReport} that violates a field invariant. */
    public static final class InvalidDailyPaperTradingReportException extends IllegalArgumentException {
        public InvalidDailyPaperTradingReportException(String message) {
            super(message);
        }
    }
}
