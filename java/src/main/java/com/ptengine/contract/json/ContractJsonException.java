package com.ptengine.contract.json;

/**
 * Thrown when {@link ContractJsonCodec} cannot parse input into a schema-valid {@code OrderIntent}
 * / {@code RiskDecision}, or cannot serialize a domain value into the canonical v0.1 JSON
 * representation. The single exception type for the whole codec boundary: callers catch this one
 * type for any JSON-boundary rejection rather than also having to catch a bare
 * {@code IllegalArgumentException}, a domain exception, or a raw Jackson exception.
 */
public final class ContractJsonException extends RuntimeException {

    public ContractJsonException(String message) {
        super(message);
    }

    public ContractJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
