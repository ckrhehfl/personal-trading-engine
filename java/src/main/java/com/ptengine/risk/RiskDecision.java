package com.ptengine.risk;

import com.ptengine.contract.ContractLimits;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Typed Java domain representation of {@code RiskDecision}, field-for-field
 * aligned with {@code schemas/v0.1/risk-decision.schema.json}.
 *
 * <p>Pure in-memory domain value, not a JSON serializer or generated model. Cross-language
 * JSON-boundary compatibility against the shared fixtures is verified by
 * {@code com.ptengine.contract.json.ContractJsonCodec} and its compatibility tests.
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

    /** Matches {@code nonEmptyIdentifier.maxLength} in the v0.1 common schema. */
    public static final int MAX_IDENTIFIER_LENGTH = ContractLimits.MAX_IDENTIFIER_LENGTH;

    /**
     * @param reasonCodes wrapped as an immutable list preserving caller order. The schema
     *     intentionally keeps {@code reasonCodes} open (any {@code nonEmptyIdentifier}-shaped
     *     string, see {@link RiskRejectReason}); each element is validated directly against that
     *     shape rather than against a closed enum. The schema's {@code uniqueItems} constraint is
     *     enforced by rejecting duplicates outright &mdash; they are never silently deduplicated.
     */
    public RiskDecision {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new InvalidRiskDecisionException(
                    "schemaVersion must be '" + SCHEMA_VERSION + "', was: " + schemaVersion);
        }
        requireValidIdentifier(decisionId, "decisionId");
        requireValidIdentifier(intentId, "intentId");
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
        Set<String> seen = new HashSet<>();
        for (String code : reasonCodes) {
            if (code == null) {
                throw new InvalidRiskDecisionException("reasonCodes must not contain a null element");
            }
            requireValidIdentifier(code, "reasonCodes element");
            if (!seen.add(code)) {
                throw new InvalidRiskDecisionException("reasonCodes must not contain a duplicate: " + code);
            }
        }
        reasonCodes = List.copyOf(reasonCodes);

        if (outcome == RiskOutcome.PASS && !reasonCodes.isEmpty()) {
            throw new InvalidRiskDecisionException("PASS must have zero reasonCodes, had: " + reasonCodes);
        }
        if (outcome == RiskOutcome.BLOCK && reasonCodes.isEmpty()) {
            throw new InvalidRiskDecisionException("BLOCK requires at least one reasonCode");
        }
    }

    /** Matches {@code common.schema.json}'s {@code nonEmptyIdentifier}: non-blank, max 128 chars. */
    private static void requireValidIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidRiskDecisionException(fieldName + " must not be blank");
        }
        if (value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new InvalidRiskDecisionException(
                    fieldName + " must be at most " + MAX_IDENTIFIER_LENGTH + " characters, was: "
                            + value.length());
        }
    }
}
