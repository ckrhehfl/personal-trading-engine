package com.ptengine.contract;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Typed Java domain representation of {@code OrderIntent}, field-for-field
 * aligned with {@code schemas/v0.1/order-intent.schema.json}.
 *
 * <p>This is a pure in-memory domain value, not a JSON parser or generated model.
 * Cross-language JSON-boundary compatibility against the shared fixtures is verified by
 * {@code com.ptengine.contract.json.ContractJsonCodec} and its compatibility tests.
 *
 * <p>{@code requestedNotional} and {@code limitPrice} use {@link BigDecimal}
 * rather than {@code float}/{@code double} to avoid binary-float drift,
 * matching the schema's decimal-as-string encoding intent.
 */
public record OrderIntent(
        String schemaVersion,
        String intentId,
        String strategyId,
        String instrument,
        IntentType intentType,
        Direction direction,
        OrderType orderType,
        BigDecimal requestedNotional,
        BigDecimal limitPrice,
        long createdAtEpochMs) {

    /** Matches the {@code const} constraint on {@code schemaVersion} in the v0.1 common schema. */
    public static final String SCHEMA_VERSION = "0.1.0";

    /** Matches {@code nonEmptyIdentifier.maxLength} in the v0.1 common schema. */
    public static final int MAX_IDENTIFIER_LENGTH = ContractLimits.MAX_IDENTIFIER_LENGTH;

    public OrderIntent {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new InvalidOrderIntentException(
                    "schemaVersion must be '" + SCHEMA_VERSION + "', was: " + schemaVersion);
        }
        requireNonBlank(intentId, "intentId");
        requireNonBlank(strategyId, "strategyId");
        requireNonBlank(instrument, "instrument");
        Objects.requireNonNull(intentType, "intentType");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(orderType, "orderType");
        Objects.requireNonNull(requestedNotional, "requestedNotional");
        if (requestedNotional.signum() <= 0) {
            throw new InvalidOrderIntentException("requestedNotional must be positive, was: " + requestedNotional);
        }
        if (createdAtEpochMs < 0) {
            throw new InvalidOrderIntentException("createdAtEpochMs must be non-negative, was: " + createdAtEpochMs);
        }
        if (orderType == OrderType.LIMIT) {
            if (limitPrice == null || limitPrice.signum() <= 0) {
                throw new InvalidOrderIntentException("orderType LIMIT requires a positive limitPrice");
            }
        } else if (limitPrice != null) {
            throw new InvalidOrderIntentException("orderType MARKET forbids limitPrice");
        }
    }

    /** Matches {@code common.schema.json}'s {@code nonEmptyIdentifier}: non-blank, max 128 chars. */
    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderIntentException(fieldName + " must not be blank");
        }
        if (value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new InvalidOrderIntentException(
                    fieldName + " must be at most " + MAX_IDENTIFIER_LENGTH + " characters, was: "
                            + value.length());
        }
    }
}
