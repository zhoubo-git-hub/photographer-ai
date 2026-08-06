package com.photogai.modules.repurchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.ReminderRepository;
import com.photogai.modules.order.ReminderService;
import com.photogai.modules.order.enums.ReminderStatus;
import com.photogai.modules.order.enums.ReminderType;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.studio.StudioRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 复购引擎服务单元测试（对应 PRD US-P2-09 / 架构 §2.3）。
 *
 * <p>验证 RepurchaseService.scanStudio 的委托与幂等：
 * 1. 候选客户（lastShootDate+cycle ≤ today 且 enabled）存在且无 PENDING 提醒 → 生成 1 条；
 * 2. 已存在 PENDING 提醒 → 幂等跳过（不再生成）；
 * 3. 无候选 → 生成 0 条。
 *
 * <p>注：findRepurchaseCandidates 的 JPQL 过滤语义（lastShootDate+cycle≤today 等）由前端转录用例
 * repurchase.test.ts 覆盖，并建议在 CI 用 Testcontainers/PostgreSQL 做集成测试。
 */
@ExtendWith(MockitoExtension.class)
class RepurchaseServiceTest {

    @Mock
    private StudioRepository studioRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ReminderRepository reminderRepository;
    @Mock
    private ReminderService reminderService;
    @Mock
    private QuotaService quotaService;

    @InjectMocks
    private RepurchaseService service;

    private Customer candidate(Long id, String lastShoot, Integer cycle) {
        Customer c = new Customer();
        c.setId(id);
        c.setStudioId(1L);
        c.setName("客户" + id);
        c.setLastShootDate(LocalDate.parse(lastShoot));
        c.setRepurchaseCycleDays(cycle);
        c.setRepurchaseEnabled(true);
        return c;
    }

    @Test
    void scanStudioCreatesReminderWhenNonePending() {
        Customer c = candidate(1L, "2025-06-20", 365);
        when(customerRepository.findRepurchaseCandidates(1L, LocalDate.parse("2026-06-20")))
                .thenReturn(List.of(c));
        when(reminderRepository.existsByStudioIdAndCustomerIdAndTypeAndStatus(
                1L, 1L, ReminderType.REPURCHASE, ReminderStatus.PENDING)).thenReturn(false);

        int created = service.scanStudio(1L, LocalDate.parse("2026-06-20"));

        assertEquals(1, created);
        verify(reminderService, times(1))
                .create(eq(1L), eq(null), eq(1L), eq(ReminderType.REPURCHASE), any());
    }

    @Test
    void scanStudioIsIdempotentWhenPendingExists() {
        Customer c = candidate(1L, "2025-06-20", 365);
        when(customerRepository.findRepurchaseCandidates(1L, LocalDate.parse("2026-06-20")))
                .thenReturn(List.of(c));
        when(reminderRepository.existsByStudioIdAndCustomerIdAndTypeAndStatus(
                1L, 1L, ReminderType.REPURCHASE, ReminderStatus.PENDING)).thenReturn(true);

        int created = service.scanStudio(1L, LocalDate.parse("2026-06-20"));

        assertEquals(0, created);
        verify(reminderService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void scanStudioCreatesNothingWhenNoCandidates() {
        when(customerRepository.findRepurchaseCandidates(1L, LocalDate.parse("2026-06-20")))
                .thenReturn(List.of());
        assertEquals(0, service.scanStudio(1L, LocalDate.parse("2026-06-20")));
    }
}
