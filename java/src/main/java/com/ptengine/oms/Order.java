package com.ptengine.oms;

import java.util.Objects;

/**
 * Pure-domain order lifecycle skeleton. No I/O, no framework dependency, no
 * network/exchange concept.
 *
 * <p>Identity ({@code orderId}, {@code clientOrderId}) is immutable. Current
 * {@link OrderState} is the only mutable field, and it can only change
 * through the explicit transition methods below, which all route through
 * {@link OrderTransitions#isLegal(OrderState, OrderState)}. There is no
 * public state setter and no other mutation path. An illegal transition
 * throws {@link IllegalOrderTransitionException} and leaves the state
 * unchanged.
 */
public final class Order {

    private final String orderId;
    private final String clientOrderId;
    private OrderState state;

    private Order(String orderId, String clientOrderId) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.clientOrderId = Objects.requireNonNull(clientOrderId, "clientOrderId");
        this.state = OrderState.NEW;
    }

    public static Order newOrder(String orderId, String clientOrderId) {
        return new Order(orderId, clientOrderId);
    }

    public String orderId() {
        return orderId;
    }

    public String clientOrderId() {
        return clientOrderId;
    }

    public OrderState state() {
        return state;
    }

    /** NEW -&gt; ACCEPTED */
    public void accept() {
        transitionTo(OrderState.ACCEPTED);
    }

    /** NEW -&gt; REJECTED */
    public void reject() {
        transitionTo(OrderState.REJECTED);
    }

    /** ACCEPTED -&gt; PARTIALLY_FILLED */
    public void partiallyFill() {
        transitionTo(OrderState.PARTIALLY_FILLED);
    }

    /** ACCEPTED -&gt; FILLED, or PARTIALLY_FILLED -&gt; FILLED */
    public void fill() {
        transitionTo(OrderState.FILLED);
    }

    /** ACCEPTED -&gt; CANCELED, or PARTIALLY_FILLED -&gt; CANCELED */
    public void cancel() {
        transitionTo(OrderState.CANCELED);
    }

    private void transitionTo(OrderState target) {
        if (!OrderTransitions.isLegal(state, target)) {
            throw new IllegalOrderTransitionException(orderId, state, target);
        }
        this.state = target;
    }
}
