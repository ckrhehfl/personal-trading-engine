package com.ptengine.oms;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory duplicate-client-order-id boundary for this skeleton.
 *
 * <p>A {@code clientOrderId} can register at most one order for the
 * lifetime of a single {@code OrderRegistry} instance. This is a
 * pure-domain, single-process, in-memory invariant only: it is backed by a
 * plain, non-thread-safe {@link HashMap} and makes no claim about
 * idempotency across process restarts, multiple JVMs, network retries,
 * exchange-side retries, or concurrent callers -- those are deferred beyond
 * this skeleton (see {@code docs/06_VALIDATION_POLICY.md} §5).
 */
public final class OrderRegistry {

    private final Map<String, Order> ordersByClientOrderId = new HashMap<>();

    /**
     * Registers a new order under {@code clientOrderId}.
     *
     * @throws DuplicateClientOrderIdException if {@code clientOrderId} has
     *     already registered an order in this registry
     */
    public Order register(String orderId, String clientOrderId) {
        Objects.requireNonNull(clientOrderId, "clientOrderId");
        if (ordersByClientOrderId.containsKey(clientOrderId)) {
            throw new DuplicateClientOrderIdException(clientOrderId);
        }
        Order order = Order.newOrder(orderId, clientOrderId);
        ordersByClientOrderId.put(clientOrderId, order);
        return order;
    }

    public Optional<Order> findByClientOrderId(String clientOrderId) {
        return Optional.ofNullable(ordersByClientOrderId.get(clientOrderId));
    }
}
