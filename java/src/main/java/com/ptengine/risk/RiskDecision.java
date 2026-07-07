package com.ptengine.risk;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Typed Java domain representation of {@code RiskDecision}, field-for-field
 * aligned with {@code schemas/v0.1/risk-decision.schema.json}.
 *
 * <p>Pure in-memory domain value, not a JSON serializer or generated model.
 * Makes no cross-language compatibility claim; that verification is
 * deferred to a later compatibility-baseline task.
 */
public record RiskDecision(
        String schemaVersion,
        String decisionId,
        String intentId,
        RiskOutcome outcome,
        List<String> reasonCodes,
        long evaluatedAtEpochMs) {

    /** Matches the {@code const} constraint on {@code schemaVersion} in the v0.1 common schema. */
    public static final String SCHEMA_VERSION = "0.1.0";

    /**
     * @param reasonCodes deduplicated (order-preserving) and wrapped as an immutable list; the
     *     schema's {@code uniqueItems} constraint on {@code reasonCodes} is enforced here rather
     *     than left to the caller.
     */
    public RiskDecision {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new InvalidRiskDecisionException(
                    "schemaVersion must be '" + SCHEMA_VERSION + "', was: " + schemaVersion);
        }
        requireNonBlank(decisionId, "decisionId");
        requireNonBlank(intentId, "intentId");
        if (outcome == null) {
            throw new InvalidRiskDecisionException("outcome must not be null");
        }
        if (evaluatedAtEpochMs < 0) {
            throw new InvalidRiskDecisionException(
                    "evaluatedAtEpochMs must be non-negative, was: " + evaluatedAtEpochMs);
        }
        if (reasonCodes == null) {
            throw new InvalidRiskDecisionException("reasonCodes must not be null");
        }
        for (String code : reasonCodes) {
            if (code == null) {
                throw new InvalidRiskDecisionException("reasonCodes must not contain a null element");
            }
            try {
                RiskRejectReason.valueOf(code);
            } catch (IllegalArgumentException e) {
                throw new InvalidRiskDecisionException("Unknown reasonCode: " + code);
            }
        }
        reasonCodes = List.copyOf(new LinkedHashSet<>(reasonCodes));

        if (outcome == RiskOutcome.PASS && !reasonCodes.isEmpty()) {
            throw new InvalidRiskDecisionException("PASS must have zero reasonCodes, had: " + reasonCodes);
        }
        if (outcome == RiskOutcome.BLOCK && reasonCodes.isEmpty()) {
            throw new InvalidRiskDecisionException("BLOCK requires at least one reasonCode");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidRiskDecisionException(fieldName + " must not be blank");
        }
    }
}
