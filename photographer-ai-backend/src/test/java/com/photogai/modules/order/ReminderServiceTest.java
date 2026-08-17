package com.photogai.modules.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.dto.ReminderDTO;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.entity.Reminder;
import com.photogai.modules.order.enums.ReminderStatus;
import com.photogai.modules.order.enums.ReminderType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 提醒服务单元测试（纯 Mockito）。
 *
 * <p>覆盖分支：4 参 create 委托 5 参、5 参保存 customerId、listByStudioAndStatus 的 status 空/非空、
 * listDueOnly 的到期过滤（过去/未来/无日期）、updateStatus 成功/不存在、
 * toDto 的订单标题与客户名解析与双双为空兜底。
 */
@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private com.photogai.modules.customer.CustomerRepository customerRepository;

    @InjectMocks
    private ReminderService service;

    // ========================= 创建 =========================

    @Test
    void createFourArgDelegatesToFiveArgWithNullCustomer() {
        LocalDateTime dueAt = LocalDateTime.now().plusDays(1);
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(i -> i.getArgument(0));

        service.create(1L, 2L, ReminderType.DEPOSIT_DUE, dueAt);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepository).save(captor.capture());
        Reminder saved = captor.getValue();
        assertEquals(1L, saved.getStudioId());
        assertEquals(2L, saved.getOrderId());
        assertNull(saved.getCustomerId());
        assertEquals(ReminderType.DEPOSIT_DUE, saved.getType());
        assertEquals(ReminderStatus.PENDING, saved.getStatus());
    }

    @Test
    void createFiveArgSavesCustomerId() {
        LocalDateTime dueAt = LocalDateTime.now().plusDays(1);
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(i -> i.getArgument(0));

        service.create(1L, 2L, 3L, ReminderType.REPURCHASE, dueAt);

        ArgumentCaptor<Reminder> captor = ArgumentCaptor.forClass(Reminder.class);
        verify(reminderRepository).save(captor.capture());
        assertEquals(3L, captor.getValue().getCustomerId());
    }

    // ========================= 列表 =========================

    @Test
    void listByStudioAndStatusDefaultsToPendingWhenNull() {
        Reminder r = new Reminder();
        r.setId(1L);
        r.setStatus(ReminderStatus.PENDING);
        when(reminderRepository.findByStudioIdAndStatus(1L, ReminderStatus.PENDING)).thenReturn(List.of(r));

        List<ReminderDTO> dtos = service.listByStudioAndStatus(1L, null);
        assertEquals(1, dtos.size());
        verify(reminderRepository, times(1)).findByStudioIdAndStatus(1L, ReminderStatus.PENDING);
    }

    @Test
    void listByStudioAndStatusUsesGivenStatus() {
        Reminder r = new Reminder();
        r.setId(1L);
        r.setStatus(ReminderStatus.DONE);
        when(reminderRepository.findByStudioIdAndStatus(1L, ReminderStatus.DONE)).thenReturn(List.of(r));

        List<ReminderDTO> dtos = service.listByStudioAndStatus(1L, ReminderStatus.DONE);
        assertEquals(1, dtos.size());
        assertEquals(ReminderStatus.DONE, dtos.get(0).getStatus());
    }

    @Test
    void listDueOnlyFiltersFutureReminders() {
        Reminder due = new Reminder();
        due.setId(1L);
        due.setDueAt(LocalDateTime.now().minusDays(1));
        Reminder future = new Reminder();
        future.setId(2L);
        future.setDueAt(LocalDateTime.now().plusDays(1));
        Reminder noDate = new Reminder();
        noDate.setId(3L);
        noDate.setDueAt(null);
        when(reminderRepository.findByStudioIdAndStatus(1L, ReminderStatus.PENDING))
                .thenReturn(List.of(due, future, noDate));

        List<ReminderDTO> dtos = service.listDueOnly(1L);
        assertEquals(2, dtos.size()); // 过去 + 无日期，未来被过滤
    }

    // ========================= 状态更新 =========================

    @Test
    void updateStatusSucceeds() {
        Reminder r = new Reminder();
        r.setId(1L);
        r.setStudioId(1L);
        r.setStatus(ReminderStatus.PENDING);
        when(reminderRepository.findByIdAndStudioId(1L, 1L)).thenReturn(Optional.of(r));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(i -> i.getArgument(0));

        ReminderDTO dto = service.updateStatus(1L, 1L, ReminderStatus.DONE);
        assertEquals(ReminderStatus.DONE, dto.getStatus());
    }

    @Test
    void updateStatusThrowsWhenNotFound() {
        when(reminderRepository.findByIdAndStudioId(anyLong(), anyLong())).thenReturn(Optional.empty());
        BizException ex = assertThrows(BizException.class,
                () -> service.updateStatus(1L, 99L, ReminderStatus.DONE));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ========================= toDto 解析 =========================

    @Test
    void toDtoResolvesOrderTitleAndCustomerName() {
        Reminder r = new Reminder();
        r.setId(1L);
        r.setOrderId(2L);
        r.setCustomerId(3L);
        when(orderRepository.findById(2L)).thenReturn(Optional.of(orderWithTitle("王小姐-婚纱")));
        Customer c = new Customer();
        c.setId(3L);
        c.setName("王小姐");
        when(customerRepository.findById(3L)).thenReturn(Optional.of(c));

        // 借助 updateStatus 触发私有 toDto 映射分支
        when(reminderRepository.findByIdAndStudioId(1L, 1L)).thenReturn(Optional.of(r));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(i -> i.getArgument(0));
        ReminderDTO resolved = service.updateStatus(1L, 1L, ReminderStatus.PENDING);
        assertEquals("王小姐-婚纱", resolved.getOrderTitle());
        assertEquals("王小姐", resolved.getCustomerName());
    }

    @Test
    void toDtoLeavesTitleAndNameNullWhenNoOrderOrCustomer() {
        Reminder r = new Reminder();
        r.setId(1L);
        r.setOrderId(null);
        r.setCustomerId(null);
        when(reminderRepository.findByIdAndStudioId(1L, 1L)).thenReturn(Optional.of(r));
        when(reminderRepository.save(any(Reminder.class))).thenAnswer(i -> i.getArgument(0));

        ReminderDTO dto = service.updateStatus(1L, 1L, ReminderStatus.PENDING);
        assertNull(dto.getOrderTitle());
        assertNull(dto.getCustomerName());
    }

    private Order orderWithTitle(String title) {
        Order o = new Order();
        o.setId(2L);
        o.setTitle(title);
        return o;
    }
}
