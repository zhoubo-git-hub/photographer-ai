package com.photogai.modules.order.statemachine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.photogai.modules.order.enums.OrderStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 订单状态机单元测试（对应 PRD P0-2 / 架构状态流转）。
 *
 * <p>流转链：CONSULT → DEPOSIT → SHOOT → EDIT → DELIVER → REPURCHASE，
 * 仅相邻状态可正向/回退流转，自环与跨状态均拒绝。
 */
class OrderStateMachineTest {

    private final OrderStateMachine sm = new OrderStateMachine();

    @Test
    void adjacentForwardTransitionsAllowed() {
        assertTrue(sm.canTransition(OrderStatus.CONSULT, OrderStatus.DEPOSIT));
        assertTrue(sm.canTransition(OrderStatus.DEPOSIT, OrderStatus.SHOOT));
        assertTrue(sm.canTransition(OrderStatus.SHOOT, OrderStatus.EDIT));
        assertTrue(sm.canTransition(OrderStatus.EDIT, OrderStatus.DELIVER));
        assertTrue(sm.canTransition(OrderStatus.DELIVER, OrderStatus.REPURCHASE));
    }

    @Test
    void adjacentBackwardTransitionsAllowed() {
        assertTrue(sm.canTransition(OrderStatus.DEPOSIT, OrderStatus.CONSULT));
        assertTrue(sm.canTransition(OrderStatus.SHOOT, OrderStatus.DEPOSIT));
        assertTrue(sm.canTransition(OrderStatus.EDIT, OrderStatus.SHOOT));
        assertTrue(sm.canTransition(OrderStatus.DELIVER, OrderStatus.EDIT));
        assertTrue(sm.canTransition(OrderStatus.REPURCHASE, OrderStatus.DELIVER));
    }

    @Test
    void nonAdjacentTransitionsRejected() {
        assertFalse(sm.canTransition(OrderStatus.CONSULT, OrderStatus.SHOOT));
        assertFalse(sm.canTransition(OrderStatus.CONSULT, OrderStatus.EDIT));
        assertFalse(sm.canTransition(OrderStatus.CONSULT, OrderStatus.DELIVER));
        assertFalse(sm.canTransition(OrderStatus.CONSULT, OrderStatus.REPURCHASE));
        assertFalse(sm.canTransition(OrderStatus.DEPOSIT, OrderStatus.EDIT));
        assertFalse(sm.canTransition(OrderStatus.DEPOSIT, OrderStatus.DELIVER));
        assertFalse(sm.canTransition(OrderStatus.EDIT, OrderStatus.REPURCHASE));
        assertFalse(sm.canTransition(OrderStatus.DELIVER, OrderStatus.CONSULT));
        assertFalse(sm.canTransition(OrderStatus.REPURCHASE, OrderStatus.EDIT));
    }

    @Test
    void selfTransitionRejected() {
        for (OrderStatus s : OrderStatus.values()) {
            assertFalse(sm.canTransition(s, s), "自环流转应被拒绝: " + s);
        }
    }

    @Test
    void nullArgumentsRejected() {
        assertFalse(sm.canTransition(null, OrderStatus.DEPOSIT));
        assertFalse(sm.canTransition(OrderStatus.CONSULT, null));
        assertFalse(sm.canTransition(null, null));
    }

    @Test
    void nextReturnsExactlyAdjacentStatusesInOrder() {
        assertEquals(List.of(OrderStatus.DEPOSIT), sm.next(OrderStatus.CONSULT));
        assertEquals(List.of(OrderStatus.CONSULT, OrderStatus.SHOOT), sm.next(OrderStatus.DEPOSIT));
        assertEquals(List.of(OrderStatus.DEPOSIT, OrderStatus.EDIT), sm.next(OrderStatus.SHOOT));
        assertEquals(List.of(OrderStatus.SHOOT, OrderStatus.DELIVER), sm.next(OrderStatus.EDIT));
        assertEquals(List.of(OrderStatus.EDIT, OrderStatus.REPURCHASE), sm.next(OrderStatus.DELIVER));
        assertEquals(List.of(OrderStatus.DELIVER), sm.next(OrderStatus.REPURCHASE));
    }
}
