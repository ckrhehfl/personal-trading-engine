package com.ptengine.oms;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized legal-transition table for {@link OrderState}.
 *
 * <p>Legal paths covered by this skeleton (see
 * {@code docs/06_VALIDATION_POLICY.md} §5):
 *
 * <ul>
 *   <li>NEW -&gt; ACCEPTED -&gt; PARTIALLY_FILLED -&gt; FILLED</li>
 *   <li>NEW -&gt; ACCEPTED -&gt; CANCELED</li>
 *   <li>NEW -&gt; REJECTED</li>
 *   <li>NEW -&gt; ACCEPTED -&gt; PARTIALLY_FILLED -&gt; CANCELED</li>
 *   <li>NEW -&gt; ACCEPTED -&gt; FILLED (full fill without an intermediate
 *       partial fill)</li>
 * </ul>
 *
 * <p>Repeated partial-fill transitions (PARTIALLY_FILLED -&gt;
 * PARTIALLY_FILLED) are intentionally not modeled here; fill-quantity
 * aggregation across multiple partial fills is deferred beyond this
 * skeleton.
 */
final class OrderTransitions {

    private static final Map<OrderState, Set<OrderState>> LEGAL = buildLegalTransitions();

    private static Map<OrderState, Set<OrderState>> buildLegalTransitions() {
        Map<OrderState, Set<OrderState>> legal = new EnumMap<>(OrderState.class);
        legal.put(OrderState.NEW, EnumSet.of(OrderState.ACCEPTED, OrderState.REJECTED));
        legal.put(
                OrderState.ACCEPTED,
                EnumSet.of(OrderState.PARTIALLY_FILLED, OrderState.FILLED, OrderState.CANCELED));
        legal.put(
                OrderState.PARTIALLY_FILLED, EnumSet.of(OrderState.FILLED, OrderState.CANCELED));
        legal.put(OrderState.FILLED, EnumSet.noneOf(OrderState.class));
        legal.put(OrderState.CANCELED, EnumSet.noneOf(OrderState.class));
        legal.put(OrderState.REJECTED, EnumSet.noneOf(OrderState.class));
        return Map.copyOf(legal);
    }

    static boolean isLegal(OrderState from, OrderState to) {
        return LEGAL.get(from).contains(to);
    }

    private OrderTransitions() {
    }
}
