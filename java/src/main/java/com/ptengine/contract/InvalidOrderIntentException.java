package com.ptengine.contract;

/** Thrown when constructing an {@link OrderIntent} that violates a v0.1 field invariant. */
public final class InvalidOrderIntentException extends IllegalArgumentException {

    public InvalidOrderIntentException(String message) {
        super(message);
    }
}
