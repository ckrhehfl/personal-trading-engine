package com.ptengine.paper;

import com.ptengine.contract.ContractLimits;

/**
 * Shared identifier validation for the {@code com.ptengine.paper} package: non-blank, at most
 * {@link ContractLimits#MAX_IDENTIFIER_LENGTH} characters.
 */
final class PaperValidation {

    private PaperValidation() {}

    static void requireValidIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidPaperExecutionException(fieldName + " must not be blank");
        }
        if (value.length() > ContractLimits.MAX_IDENTIFIER_LENGTH) {
            throw new InvalidPaperExecutionException(
                    fieldName + " must be at most " + ContractLimits.MAX_IDENTIFIER_LENGTH + " characters, was: "
                            + value.length());
        }
    }
}
