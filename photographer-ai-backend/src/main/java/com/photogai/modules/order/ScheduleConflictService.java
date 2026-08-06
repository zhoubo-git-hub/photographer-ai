package com.photogai.modules.order;

import com.photogai.modules.order.dto.ConflictDTO;
import com.photogai.modules.order.entity.Order;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 档期冲突检测：按 {@code studio_id + shoot_date/shoot_end_date} 时间段重叠判定。
 *
 * <p>采用<b>硬阻断</b>策略——重叠时上层返回 409，前端红色提示，阻止保存。
 */
@Service
@RequiredArgsConstructor
public class ScheduleConflictService {

    private final OrderRepository orderRepository;

    /**
     * 检测候选时间段是否与同 studio 已有拍摄重叠。
     *
     * @param studioId       工作室 ID（多租户隔离）
     * @param shootDate      拍摄开始日
     * @param shootEndDate   拍摄结束日（可空，等同 shootDate）
     * @param excludeOrderId 自查时排除的订单（新建传 null）
     * @return 重叠的已有订单列表；空表示无冲突
     */
    public List<ConflictDTO> checkConflict(Long studioId, LocalDate shootDate,
                                           LocalDate shootEndDate, Long excludeOrderId) {
        if (shootDate == null) {
            return List.of();
        }
        List<Order> conflicts = orderRepository.findConflicts(
                studioId, shootDate, shootEndDate, excludeOrderId);
        return conflicts.stream()
                .map(o -> ConflictDTO.builder()
                        .orderId(o.getId())
                        .title(o.getTitle())
                        .shootDate(o.getShootDate())
                        .shootEndDate(o.getShootEndDate())
                        .build())
                .collect(Collectors.toList());
    }
}
