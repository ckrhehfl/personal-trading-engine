package com.ptengine.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderLifecycleTest {

    private static Order newOrder() {
        return Order.newOrder("order-1", "client-1");
    }

    // ------------------------------------------------------------------
    // Required legal lifecycle paths (docs/06_VALIDATION_POLICY.md §5)
    // ------------------------------------------------------------------

    @Test
    void newToAcceptedToPartiallyFilledToFilled() {
        Order order = newOrder();
        order.accept();
        order.partiallyFill();
        order.fill();
        assertEquals(OrderState.FILLED, order.state());
    }

    @Test
    void newToAcceptedToCanceled() {
        Order order = newOrder();
        order.accept();
        order.cancel();
        assertEquals(OrderState.CANCELED, order.state());
    }

    @Test
    void newToRejected() {
        Order order = newOrder();
        order.reject();
        assertEquals(OrderState.REJECTED, order.state());
    }

    @Test
    void newToAcceptedToPartiallyFilledToCanceled() {
        Order order = newOrder();
        order.accept();
        order.partiallyFill();
        order.cancel();
        assertEquals(OrderState.CANCELED, order.state());
    }

    @Test
    void newToAcceptedToFilledWithoutIntermediatePartialFill() {
        Order order = newOrder();
        order.accept();
        order.fill();
        assertEquals(OrderState.FILLED, order.state());
    }

    // ------------------------------------------------------------------
    // Representative illegal transitions
    // ------------------------------------------------------------------

    @Test
    void newToFilledIsIllegal() {
        Order order = newOrder();
        IllegalOrderTransitionException ex =
                assertThrows(IllegalOrderTransitionException.class, order::fill);
        assertEquals(OrderState.NEW, ex.from());
        assertEquals(OrderState.FILLED, ex.to());
        assertEquals(OrderState.NEW, order.state());
    }

    @Test
    void newToCanceledIsIllegal() {
        Order order = newOrder();
        IllegalOrderTransitionException ex =
                assertThrows(IllegalOrderTransitionException.class, order::cancel);
        assertEquals(OrderState.NEW, ex.from());
        assertEquals(OrderState.CANCELED, ex.to());
        assertEquals(OrderState.NEW, order.state());
    }

    @Test
    void acceptedToRejectedIsIllegal() {
        Order order = newOrder();
        order.accept();
        IllegalOrderTransitionException ex =
                assertThrows(IllegalOrderTransitionException.class, order::reject);
        assertEquals(OrderState.ACCEPTED, ex.from());
        assertEquals(OrderState.REJECTED, ex.to());
        assertEquals(OrderState.ACCEPTED, order.state());
    }

    @Test
    void partiallyFilledToRejectedIsIllegal() {
        Order order = newOrder();
        order.accept();
        order.partiallyFill();
        IllegalOrderTransitionException ex =
                assertThrows(IllegalOrderTransitionException.class, order::reject);
        assertEquals(OrderState.PARTIALLY_FILLED, ex.from());
        assertEquals(OrderState.REJECTED, ex.to());
        assertEquals(OrderState.PARTIALLY_FILLED, order.state());
    }

    // ------------------------------------------------------------------
    // Terminal-state protection: no transition leaves a terminal state
    // ------------------------------------------------------------------

    @Test
    void noTransitionOutOfFilled() {
        Order order = newOrder();
        order.accept();
        order.fill();
        assertAllTransitionsIllegalFrom(order, OrderState.FILLED);
    }

    @Test
    void noTransitionOutOfCanceled() {
        Order order = newOrder();
        order.accept();
        order.cancel();
        assertAllTransitionsIllegalFrom(order, OrderState.CANCELED);
    }

    @Test
    void noTransitionOutOfRejected() {
        Order order = newOrder();
        order.reject();
        assertAllTransitionsIllegalFrom(order, OrderState.REJECTED);
    }

    private static void assertAllTransitionsIllegalFrom(Order order, OrderState terminalState) {
        assertEquals(true, terminalState.isTerminal());
        Set<Runnable> attempts =
                Set.of(order::accept, order::reject, order::partiallyFill, order::fill, order::cancel);
        for (Runnable attempt : attempts) {
            assertThrows(IllegalOrderTransitionException.class, attempt::run);
            assertEquals(terminalState, order.state());
        }
    }
}
