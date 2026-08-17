package com.photogai.modules.schedule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.ScheduleConflictService;
import com.photogai.modules.order.dto.ConflictDTO;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import com.photogai.modules.schedule.dto.ScheduleDTO;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 档期日历服务单元测试（Mockito，不连 PG）。
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ScheduleConflictService scheduleConflictService;

    @InjectMocks
    private ScheduleService scheduleService;

    @Test
    void monthMarksNoConflictWhenNoOverlap() {
        Order order = new Order();
        order.setId(1L);
        order.setTitle("婚纱订单");
        order.setShootDate(LocalDate.of(2024, 5, 1));
        order.setShootEndDate(LocalDate.of(2024, 5, 1));
        order.setStatus(OrderStatus.SHOOT);
        when(orderRepository.findByStudioAndMonth(anyLong(), any(), any())).thenReturn(List.of(order));
        when(scheduleConflictService.checkConflict(anyLong(), any(), any(), any()))
                .thenReturn(List.of());

        List<ScheduleDTO> result = scheduleService.month(1L, 2024, 5);
        assertEquals(1, result.size());
        assertEquals("婚纱订单", result.get(0).getTitle());
        assertEquals(false, result.get(0).isConflict());
    }

    @Test
    void monthMarksConflictWhenOverlapExists() {
        Order order = new Order();
        order.setId(1L);
        order.setTitle("婚纱订单");
        order.setShootDate(LocalDate.of(2024, 5, 1));
        order.setStatus(OrderStatus.SHOOT);
        when(orderRepository.findByStudioAndMonth(anyLong(), any(), any())).thenReturn(List.of(order));
        when(scheduleConflictService.checkConflict(anyLong(), any(), any(), any()))
                .thenReturn(List.of(ConflictDTO.builder().orderId(9L).build()));

        List<ScheduleDTO> result = scheduleService.month(1L, 2024, 5);
        assertEquals(true, result.get(0).isConflict());
    }
}
