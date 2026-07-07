package com.ptengine.oms;

/**
 * Thrown when a transition would move an {@link Order} between two states
 * that {@link OrderTransitions} does not recognize as legal. The order's
 * state is left unchanged.
 */
public final class IllegalOrderTransitionException extends RuntimeException {

    private final String orderId;
    private final OrderState from;
    private final OrderState to;

    public IllegalOrderTransitionException(String orderId, OrderState from, OrderState to) {
        super("Illegal order transition for orderId=" + orderId + ": " + from + " -> " + to);
        this.orderId = orderId;
        this.from = from;
        this.to = to;
    }

    public String orderId() {
        return orderId;
    }

    public OrderState from() {
        return from;
    }

    public OrderState to() {
        return to;
    }
}
