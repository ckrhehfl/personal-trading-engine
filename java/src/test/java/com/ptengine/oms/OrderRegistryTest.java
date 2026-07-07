package com.ptengine.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrderRegistryTest {

    @Test
    void firstRegistrationSucceeds() {
        OrderRegistry registry = new OrderRegistry();
        Order order = registry.register("order-1", "client-1");
        assertEquals("order-1", order.orderId());
        assertEquals("client-1", order.clientOrderId());
        assertEquals(OrderState.NEW, order.state());
        assertTrue(registry.findByClientOrderId("client-1").isPresent());
    }

    @Test
    void duplicateClientOrderIdIsRejected() {
        OrderRegistry registry = new OrderRegistry();
        registry.register("order-1", "client-1");

        DuplicateClientOrderIdException ex =
                assertThrows(
                        DuplicateClientOrderIdException.class,
                        () -> registry.register("order-2", "client-1"));
        assertEquals("client-1", ex.clientOrderId());
    }

    @Test
    void duplicateRegistrationDoesNotCreateSecondOrder() {
        OrderRegistry registry = new OrderRegistry();
        Order first = registry.register("order-1", "client-1");

        assertThrows(
                DuplicateClientOrderIdException.class,
                () -> registry.register("order-2", "client-1"));

        Order stored = registry.findByClientOrderId("client-1").orElseThrow();
        assertSame(first, stored);
        assertEquals("order-1", stored.orderId());
    }

    @Test
    void distinctClientOrderIdsRegisterIndependently() {
        OrderRegistry registry = new OrderRegistry();
        Order first = registry.register("order-1", "client-1");
        Order second = registry.register("order-2", "client-2");

        assertSame(first, registry.findByClientOrderId("client-1").orElseThrow());
        assertSame(second, registry.findByClientOrderId("client-2").orElseThrow());
        assertEquals("order-1", first.orderId());
        assertEquals("order-2", second.orderId());
    }
}
