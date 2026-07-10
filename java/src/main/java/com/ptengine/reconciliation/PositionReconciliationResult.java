package com.ptengine.reconciliation;

import com.ptengine.contract.ContractLimits;
import java.util.List;

/**
 * Immutable, deterministic outcome of one {@link PositionReconciler#reconcile} call: the
 * caller-supplied identity/timestamp, the {@link Status}, the stable-ordered {@link Reason} list,
 * and the two compared {@link PositionSnapshot} values.
 */
public record PositionReconciliationResult(
        String reconciliationId,
        Status status,
        List<Reason> reasons,
        PositionSnapshot expected,
        PositionSnapshot observed,
        long reconciledAtEpochMs) {

    public enum Status {
        MATCH,
        MISMATCH
    }

    public enum Reason {
        INSTRUMENT_MISMATCH,
        DIRECTION_MISMATCH,
        POSITION_NOTIONAL_MISMATCH,
        AVERAGE_ENTRY_PRICE_MISMATCH
    }

    /**
     * @param reasons wrapped as an immutable list preserving caller order &mdash; the ordering
     *     itself is {@link PositionReconciler}'s responsibility, not re-derived here.
     */
    public PositionReconciliationResult {
        requireValidIdentifier(reconciliationId, "reconciliationId");
        if (status == null) {
            throw new InvalidPositionReconciliationResultException("status must not be null");
        }
        if (reasons == null) {
            throw new InvalidPositionReconciliationResultException("reasons must not be null");
        }
        for (Reason reason : reasons) {
            if (reason == null) {
                throw new InvalidPositionReconciliationResultException("reasons must not contain a null element");
            }
        }
        reasons = List.copyOf(reasons);
        if (expected == null) {
            throw new InvalidPositionReconciliationResultException("expected must not be null");
        }
        if (observed == null) {
            throw new InvalidPositionReconciliationResultException("observed must not be null");
        }
        if (reconciledAtEpochMs < 0) {
            throw new InvalidPositionReconciliationResultException(
                    "reconciledAtEpochMs must be non-negative, was: " + reconciledAtEpochMs);
        }
        if (status == Status.MATCH && !reasons.isEmpty()) {
            throw new InvalidPositionReconciliationResultException("MATCH must have zero reasons, had: " + reasons);
        }
        if (status == Status.MISMATCH && reasons.isEmpty()) {
            throw new InvalidPositionReconciliationResultException("MISMATCH requires at least one reason");
        }
    }

    /** Matches {@code common.schema.json}'s {@code nonEmptyIdentifier}: non-blank, max 128 chars. */
    private static void requireValidIdentifier(String value, String fieldName) {
        if (value == null) {
            throw new InvalidPositionReconciliationResultException(fieldName + " must not be null");
        }
        if (value.isBlank()) {
            throw new InvalidPositionReconciliationResultException(fieldName + " must not be blank");
        }
        if (value.length() > ContractLimits.MAX_IDENTIFIER_LENGTH) {
            throw new InvalidPositionReconciliationResultException(
                    fieldName + " must be at most " + ContractLimits.MAX_IDENTIFIER_LENGTH + " characters, was: "
                            + value.length());
        }
    }

    /**
     * Thrown when constructing a {@link PositionReconciliationResult} that violates a field
     * invariant.
     */
    public static final class InvalidPositionReconciliationResultException extends IllegalArgumentException {
        public InvalidPositionReconciliationResultException(String message) {
            super(message);
        }
    }
}
