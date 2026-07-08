package com.ptengine.contract;

/**
 * Single Java-side mirror of {@code nonEmptyIdentifier.maxLength} in
 * {@code schemas/v0.1/common.schema.json}. The JSON Schema remains the canonical source of
 * truth; every Java layer that needs this bound ({@link OrderIntent}, {@code RiskDecision},
 * {@code ContractJsonCodec}) reuses this one constant instead of each independently declaring
 * the literal, which would otherwise let the layers silently drift apart if the schema value
 * ever changes.
 */
public final class ContractLimits {

    public static final int MAX_IDENTIFIER_LENGTH = 128;

    private ContractLimits() {}
}
