package com.photogai.modules.schedule;

import com.photogai.modules.order.ScheduleConflictService;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.dto.ConflictDTO;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.schedule.dto.ScheduleDTO;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 档期日历服务：返回某月拍摄排期，并标记重叠冲突（红色高亮）。
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final OrderRepository orderRepository;
    private final ScheduleConflictService scheduleConflictService;

    @Transactional(readOnly = true)
    public List<ScheduleDTO> month(Long studioId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        List<Order> orders = orderRepository.findByStudioAndMonth(studioId, start, end);
        return orders.stream().map(o -> {
            List<ConflictDTO> conflicts = scheduleConflictService.checkConflict(
                    studioId, o.getShootDate(), o.getShootEndDate(), o.getId());
            return ScheduleDTO.builder()
                    .orderId(o.getId())
                    .title(o.getTitle())
                    .shootDate(o.getShootDate())
                    .shootEndDate(o.getShootEndDate())
                    .status(o.getStatus())
                    .conflict(!conflicts.isEmpty())
                    .build();
        }).collect(Collectors.toList());
    }
}
