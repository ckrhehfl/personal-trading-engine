package com.ptengine.oms;

/**
 * Thrown when {@link OrderRegistry#register(String, String)} is called with
 * a {@code clientOrderId} that has already registered an order within this
 * registry instance's lifetime.
 */
public final class DuplicateClientOrderIdException extends RuntimeException {

    private final String clientOrderId;

    public DuplicateClientOrderIdException(String clientOrderId) {
        super("Duplicate clientOrderId: " + clientOrderId);
        this.clientOrderId = clientOrderId;
    }

    public String clientOrderId() {
        return clientOrderId;
    }
}
