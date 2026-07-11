package com.ptengine.reconciliation;

import com.ptengine.contract.ContractLimits;
import com.ptengine.contract.Direction;
import java.math.BigDecimal;

/**
 * Immutable snapshot of one open logical position, compared field-for-field by {@link
 * PositionReconciler}.
 *
 * <p>Models exactly one open logical position, not a portfolio/account ledger: no flat/no-position
 * representation, no quantity/base size, no leverage, margin, position mode, exchange symbol
 * mapping, account balance, PnL, fees/funding, liquidation, reduce-only, or runtime/exchange
 * source.
 */
public record PositionSnapshot(
        String instrument,
        Direction direction,
        BigDecimal positionNotional,
        BigDecimal averageEntryPrice,
        long capturedAtEpochMs) {

    public PositionSnapshot {
        requireValidIdentifier(instrument, "instrument");
        if (direction == null) {
            throw new InvalidPositionSnapshotException("direction must not be null");
        }
        if (positionNotional == null) {
            throw new InvalidPositionSnapshotException("positionNotional must not be null");
        }
        if (positionNotional.signum() <= 0) {
            throw new InvalidPositionSnapshotException(
                    "positionNotional must be positive, was: " + positionNotional);
        }
        if (averageEntryPrice == null) {
            throw new InvalidPositionSnapshotException("averageEntryPrice must not be null");
        }
        if (averageEntryPrice.signum() <= 0) {
            throw new InvalidPositionSnapshotException(
                    "averageEntryPrice must be positive, was: " + averageEntryPrice);
        }
        if (capturedAtEpochMs < 0) {
            throw new InvalidPositionSnapshotException(
                    "capturedAtEpochMs must be non-negative, was: " + capturedAtEpochMs);
        }
    }

    /** Matches {@code common.schema.json}'s {@code nonEmptyIdentifier}: non-blank, max 128 chars. */
    private static void requireValidIdentifier(String value, String fieldName) {
        if (value == null) {
            throw new InvalidPositionSnapshotException(fieldName + " must not be null");
        }
        if (value.isBlank()) {
            throw new InvalidPositionSnapshotException(fieldName + " must not be blank");
        }
        if (value.length() > ContractLimits.MAX_IDENTIFIER_LENGTH) {
            throw new InvalidPositionSnapshotException(
                    fieldName + " must be at most " + ContractLimits.MAX_IDENTIFIER_LENGTH + " characters, was: "
                            + value.length());
        }
    }

    /** Thrown when constructing a {@link PositionSnapshot} that violates a field invariant. */
    public static final class InvalidPositionSnapshotException extends IllegalArgumentException {
        public InvalidPositionSnapshotException(String message) {
            super(message);
        }
    }
}
