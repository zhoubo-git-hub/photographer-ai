package com.photogai.modules.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.common.PageData;
import com.photogai.exception.BizException;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.dto.ConflictDTO;
import com.photogai.modules.order.dto.OrderCreateRequest;
import com.photogai.modules.order.dto.OrderDTO;
import com.photogai.modules.order.dto.OrderUpdateRequest;
import com.photogai.modules.order.dto.StatusChangeRequest;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.entity.StatusHistory;
import com.photogai.modules.order.enums.OrderStatus;
import com.photogai.modules.order.enums.ReminderType;
import com.photogai.modules.order.statemachine.OrderStateMachine;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.reminder.ReminderRuleService;
import com.photogai.modules.reminder.ReminderTriggerEvent;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.auth.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * 订单服务主流程单元测试（纯 Mockito，覆盖主流程分支；多租户隔离见 OrderServiceMultiTenantTest）。
 *
 * <p>覆盖分支：list 的 status 空/非空、get、create 的档期冲突/无冲突、update 的字段覆盖与改期冲突、
 * delete、assign 的回退与指派成员、changeStatus 的合法流转触发提醒 / 非法流转 / DELIVER 回填客户画像。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

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
    @Mock
    private ReminderRuleService reminderRuleService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrderService service;

    private Order owned(Long id, Long studioId, Long customerId, OrderStatus status) {
        Order o = new Order();
        o.setId(id);
        o.setStudioId(studioId);
        o.setCustomerId(customerId);
        o.setTitle("t");
        o.setStatus(status);
        o.setDeletedAt(null);
        return o;
    }

    private Customer customer(Long id, Long studioId) {
        Customer c = new Customer();
        c.setId(id);
        c.setStudioId(studioId);
        c.setName("c" + id);
        return c;
    }

    // ========================= list =========================

    @Test
    void listWithoutStatusUsesFindAll() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        when(orderRepository.findByStudioIdAndDeletedAtIsNull(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(o)));
        when(customerRepository.findByStudioIdAndDeletedAtIsNull(1L)).thenReturn(List.of(customer(2L, 1L)));

        PageData<OrderDTO> pd = service.list(1L, null, 0, 10);
        assertEquals(1, pd.getContent().size());
        assertEquals("c2", pd.getContent().get(0).getCustomerName());
    }

    @Test
    void listWithStatusFiltersByStatus() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        when(orderRepository.findByStudioIdAndStatusAndDeletedAtIsNull(eq(1L), eq(OrderStatus.CONSULT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(o)));
        when(customerRepository.findByStudioIdAndDeletedAtIsNull(1L)).thenReturn(List.of(customer(2L, 1L)));

        PageData<OrderDTO> pd = service.list(1L, OrderStatus.CONSULT, 0, 10);
        assertEquals(1, pd.getContent().size());
    }

    // ========================= get =========================

    @Test
    void getReturnsOrderWithHistory() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
        when(statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.of(customer(2L, 1L)));

        OrderDTO dto = service.get(1L, 1L);
        assertEquals(1L, dto.getId());
        assertEquals("c2", dto.getCustomerName());
    }

    // ========================= create =========================

    @Test
    void createWithoutShootDateSkipsConflict() {
        OrderCreateRequest req = OrderCreateRequest.builder().customerId(2L).title("t").build();
        doNothing().when(quotaService).ensureWithinLimit(1L);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(quotaService).recountOrders(1L);
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.of(customer(2L, 1L)));

        OrderDTO dto = service.create(1L, 1L, req);
        assertEquals("t", dto.getTitle());
        verify(scheduleConflictService, org.mockito.Mockito.never()).checkConflict(anyLong(), any(), any(), any());
    }

    @Test
    void createWithShootDateNoConflictProceeds() {
        OrderCreateRequest req = OrderCreateRequest.builder()
                .customerId(2L).title("t").shootDate(LocalDate.now()).build();
        doNothing().when(quotaService).ensureWithinLimit(1L);
        when(scheduleConflictService.checkConflict(eq(1L), any(), any(), isNull())).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(quotaService).recountOrders(1L);
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.of(customer(2L, 1L)));

        OrderDTO dto = service.create(1L, 1L, req);
        assertEquals("t", dto.getTitle());
    }

    @Test
    void createThrowsOnScheduleConflict() {
        OrderCreateRequest req = OrderCreateRequest.builder()
                .customerId(2L).title("t").shootDate(LocalDate.now()).build();
        doNothing().when(quotaService).ensureWithinLimit(1L);
        when(scheduleConflictService.checkConflict(eq(1L), any(), any(), isNull()))
                .thenReturn(List.of(new ConflictDTO()));

        BizException ex = assertThrows(BizException.class, () -> service.create(1L, 1L, req));
        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
    }

    // ========================= update =========================

    @Test
    void updateAppliesProvidedFields() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
        OrderUpdateRequest req = OrderUpdateRequest.builder().title("new").amount(BigDecimal.TEN).build();
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.of(customer(2L, 1L)));

        OrderDTO dto = service.update(1L, 1L, req);
        assertEquals("new", dto.getTitle());
        assertEquals(BigDecimal.TEN, dto.getAmount());
    }

    @Test
    void updateThrowsOnDateChangeConflict() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        o.setShootDate(LocalDate.of(2026, 1, 1));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
        OrderUpdateRequest req = OrderUpdateRequest.builder().shootDate(LocalDate.of(2026, 6, 1)).build();
        when(scheduleConflictService.checkConflict(eq(1L), any(), any(), eq(1L)))
                .thenReturn(List.of(new ConflictDTO()));

        BizException ex = assertThrows(BizException.class, () -> service.update(1L, 1L, req));
        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
    }

    @Test
    void updateThrowsWhenNotFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());
        BizException ex = assertThrows(BizException.class,
                () -> service.update(1L, 1L, OrderUpdateRequest.builder().build()));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ========================= delete =========================

    @Test
    void deleteSoftDeletesAndRecounts() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(quotaService).recountOrders(1L);

        service.delete(1L, 1L);
        verify(orderRepository).save(any(Order.class));
        verify(quotaService).recountOrders(1L);
    }

    // ========================= assign =========================

    @Test
    void assignWithNullMemberFallsBackToUnassigned() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.of(customer(2L, 1L)));

        OrderDTO dto = service.assign(1L, 1L, null, 1L, "OWNER");
        assertNull(dto.getAssignedTo());
    }

    @Test
    void assignWithMemberSetsAssignedTo() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
        com.photogai.modules.auth.entity.User member = new com.photogai.modules.auth.entity.User();
        member.setId(9L);
        when(userRepository.findByStudioIdAndId(1L, 9L)).thenReturn(Optional.of(member));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.of(customer(2L, 1L)));

        OrderDTO dto = service.assign(1L, 1L, 9L, 1L, "OWNER");
        assertEquals(9L, dto.getAssignedTo());
    }

    // ========================= changeStatus =========================

    @Test
    void changeStatusValidTriggersReminder() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
        when(orderStateMachine.canTransition(OrderStatus.CONSULT, OrderStatus.DEPOSIT)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(reminderRuleService.findOffset(1L, ReminderTriggerEvent.DEPOSIT)).thenReturn(3);
        doNothing().when(reminderService)
                .create(anyLong(), any(), anyLong(), any(ReminderType.class), any());
        when(statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(statusHistoryRepository.save(any(StatusHistory.class))).thenAnswer(i -> i.getArgument(0));
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 2L))
                .thenReturn(Optional.of(customer(2L, 1L)));

        OrderDTO dto = service.changeStatus(1L, 1L,
                StatusChangeRequest.builder().toStatus(OrderStatus.DEPOSIT).build(), 1L);
        assertEquals(OrderStatus.DEPOSIT, dto.getStatus());
        verify(reminderService).create(anyLong(), any(), anyLong(), any(ReminderType.class), any());
    }

    @Test
    void changeStatusIllegalTransitionThrows() {
        Order o = owned(1L, 1L, 2L, OrderStatus.CONSULT);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
        when(orderStateMachine.canTransition(OrderStatus.CONSULT, OrderStatus.SHOOT)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.changeStatus(1L, 1L,
                StatusChangeRequest.builder().toStatus(OrderStatus.SHOOT).build(), 1L));
        assertEquals(ErrorCode.VALIDATION.getCode(), ex.getCode());
    }

    @Test
    void changeStatusToDeliverBackfillsCustomerProfile() {
        Order o = owned(1L, 1L, 2L, OrderStatus.EDIT);
        LocalDate shoot = LocalDate.of(2026, 5, 1);
        o.setShootDate(shoot);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(o));
        when(orderStateMachine.canTransition(OrderStatus.EDIT, OrderStatus.DELIVER)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(reminderRuleService.findOffset(1L, ReminderTriggerEvent.DELIVER)).thenReturn(3);
        doNothing().when(reminderService)
                .create(anyLong(), any(), anyLong(), any(ReminderType.class), any());

        Customer cust = customer(2L, 1L);
        cust.setLastShootDate(null);
        cust.setRepurchaseCycleDays(null);
        when(customerRepository.findByStudioIdAndIdAndDeletedAtIsNull(1L, 2L)).thenReturn(Optional.of(cust));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));
        when(statusHistoryRepository.findByOrderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        when(statusHistoryRepository.save(any(StatusHistory.class))).thenAnswer(i -> i.getArgument(0));

        service.changeStatus(1L, 1L,
                StatusChangeRequest.builder().toStatus(OrderStatus.DELIVER).build(), 1L);

        assertEquals(shoot, cust.getLastShootDate());
        assertEquals(Integer.valueOf(365), cust.getRepurchaseCycleDays());
    }
}
