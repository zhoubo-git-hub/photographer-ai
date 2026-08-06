package com.photogai.modules.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.dto.OrderCreateRequest;
import com.photogai.modules.order.dto.OrderUpdateRequest;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.order.ReminderService;
import com.photogai.modules.order.StatusHistoryRepository;
import com.photogai.modules.order.statemachine.OrderStateMachine;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 订单服务多租户隔离单元测试（对应架构"多租户隔离"约束）。
 *
 * <p>验证：所有查询/写入都按传入的 studioId 过滤，跨 studio 的订单不可见
 * （requireOwned 依据 studioId 过滤，不匹配则视为 NOT_FOUND）。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceMultiTenantTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private StatusHistoryRepository statusHistoryRepository;

    @Mock
    private ScheduleConflictService scheduleConflictService;

    @Mock
    private OrderStateMachine orderStateMachine;

    @Mock
    private QuotaService quotaService;

    @Mock
    private ReminderService reminderService;

    @InjectMocks
    private OrderService orderService;

    private static final Long STUDIO_ID = 7L;

    private Order ownedOrder(Long id, Long studioId, Long customerId) {
        Order o = new Order();
        o.setId(id);
        o.setStudioId(studioId);
        o.setCustomerId(customerId);
        o.setTitle("t");
        o.setStatus(OrderStatus.CONSULT);
        o.setDeletedAt(null);
        return o;
    }

    @Test
    void createSetsStudioIdAndFiltersConflictCheckByStudio() {
        OrderCreateRequest req = OrderCreateRequest.builder()
                .customerId(2L).title("王小姐-婚纱").shootDate(LocalDate.of(2026, 6, 28)).build();

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.create(STUDIO_ID, 1L, req);

        // 冲突检测必须按当前 studio 过滤，禁止跨租户
        verify(scheduleConflictService, times(1))
                .checkConflict(eq(STUDIO_ID), any(LocalDate.class), any(), isNull());
        verify(quotaService, times(1)).ensureWithinLimit(eq(STUDIO_ID));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertEquals(STUDIO_ID, captor.getValue().getStudioId());
    }

    @Test
    void getFiltersByStudioAndResolvesCustomerWithinStudio() {
        when(orderRepository.findById(3L)).thenReturn(Optional.of(ownedOrder(3L, STUDIO_ID, 2L)));

        assertNotNull(orderService.get(STUDIO_ID, 3L));
        // 客户名解析也按 studio 过滤
        verify(customerRepository, times(1)).findByStudioIdAndIdAndDeletedAtIsNull(eq(STUDIO_ID), eq(2L));
    }

    @Test
    void crossStudioOrderIsInvisible() {
        // 订单属于 studio 99，但当前请求是 studio 7 → 视为不存在
        when(orderRepository.findById(5L)).thenReturn(Optional.of(ownedOrder(5L, 99L, 2L)));

        BizException ex = assertThrows(BizException.class, () -> orderService.get(STUDIO_ID, 5L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void updateResolvesCustomerWithinStudio() {
        when(orderRepository.findById(4L)).thenReturn(Optional.of(ownedOrder(4L, STUDIO_ID, 2L)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderUpdateRequest req = OrderUpdateRequest.builder().title("新标题").build();
        orderService.update(STUDIO_ID, 4L, req);

        verify(customerRepository, times(1)).findByStudioIdAndIdAndDeletedAtIsNull(eq(STUDIO_ID), eq(2L));
    }

    @Test
    void deleteFiltersByStudio() {
        when(orderRepository.findById(6L)).thenReturn(Optional.of(ownedOrder(6L, STUDIO_ID, 2L)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.delete(STUDIO_ID, 6L);

        verify(orderRepository, times(1)).findById(6L);
        verify(quotaService, times(1)).recountOrders(eq(STUDIO_ID));
    }
}
