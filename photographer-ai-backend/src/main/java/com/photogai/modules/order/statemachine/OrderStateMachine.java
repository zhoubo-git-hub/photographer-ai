package com.photogai.modules.order.statemachine;

import com.photogai.modules.order.enums.OrderStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 订单状态机：仅允许相邻状态之间的正向/回退流转。
 *
 * <p>流转链：CONSULT → DEPOSIT → SHOOT → EDIT → DELIVER → REPURCHASE。
 * 非法跳变（如 CONSULT 直接到 SHOOT）一律拒绝。
 */
@Component
public class OrderStateMachine {

    /** 每个状态允许的相邻状态集合（含正向与回退）。 */
    private final Map<OrderStatus, List<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);

    public OrderStateMachine() {
        register(OrderStatus.CONSULT, OrderStatus.DEPOSIT);
        register(OrderStatus.DEPOSIT, OrderStatus.SHOOT);
        register(OrderStatus.SHOOT, OrderStatus.EDIT);
        register(OrderStatus.EDIT, OrderStatus.DELIVER);
        register(OrderStatus.DELIVER, OrderStatus.REPURCHASE);
    }

    private void register(OrderStatus from, OrderStatus to) {
        transitions.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        transitions.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
    }

    /** 是否允许从 {@code from} 流转到 {@code to}（相邻即可，支持回退）。 */
    public boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return false;
        }
        List<OrderStatus> allowed = transitions.get(from);
        return allowed != null && allowed.contains(to);
    }

    /** 返回某状态可流转到的相邻状态列表。 */
    public List<OrderStatus> next(OrderStatus status) {
        return new ArrayList<>(transitions.getOrDefault(status, List.of()));
    }
}
