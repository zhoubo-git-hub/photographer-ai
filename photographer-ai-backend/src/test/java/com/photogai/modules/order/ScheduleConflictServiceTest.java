package com.photogai.modules.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.photogai.modules.order.dto.ConflictDTO;
import com.photogai.modules.order.entity.Order;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 档期冲突服务单元测试（对应 PRD P0-3 / 架构硬阻断）。
 *
 * <p>本测试验证 {@link ScheduleConflictService} 把 studioId 正确透传给仓储，
 * 并把命中的订单映射为 {@link ConflictDTO}。底层 SQL 的"时间段重叠"语义由
 * 前端可独立运行的 Vitest（scheduleConflict.test.ts）实证，并建议补充
 * {@code @DataJpaTest}（需 H2）作为回归测试（见测试报告）。
 */
@ExtendWith(MockitoExtension.class)
class ScheduleConflictServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ScheduleConflictService service;

    @Test
    void returnsConflictsWhenOverlapExists() {
        LocalDate d = LocalDate.of(2026, 6, 28);
        Order o = new Order();
        o.setId(7L);
        o.setTitle("张同学-毕业");
        o.setShootDate(d);
        o.setShootEndDate(d);

        when(orderRepository.findConflicts(eq(1L), eq(d), eq(d), isNull()))
                .thenReturn(List.of(o));

        List<ConflictDTO> result = service.checkConflict(1L, d, d, null);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getOrderId());
        assertEquals("张同学-毕业", result.get(0).getTitle());
        // 关键：studioId 必须透传，禁止跨租户
        verify(orderRepository, times(1)).findConflicts(eq(1L), eq(d), eq(d), isNull());
    }

    @Test
    void returnsEmptyWhenNoOverlap() {
        LocalDate d = LocalDate.of(2026, 6, 29);
        when(orderRepository.findConflicts(eq(1L), eq(d), eq(d), isNull()))
                .thenReturn(List.of());

        assertTrue(service.checkConflict(1L, d, d, null).isEmpty());
    }

    @Test
    void nullShootDateShortCircuitsWithoutQuery() {
        assertTrue(service.checkConflict(1L, null, null, null).isEmpty());
        verifyNoInteractions(orderRepository);
    }
}
