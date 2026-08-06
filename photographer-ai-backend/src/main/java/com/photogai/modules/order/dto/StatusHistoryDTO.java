package com.photogai.modules.order.dto;

import com.photogai.modules.order.entity.StatusHistory;
import com.photogai.modules.order.enums.OrderStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态流转留痕视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryDTO {

    private Long id;
    private Long orderId;
    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private Long operatorId;
    private LocalDateTime createdAt;

    public static StatusHistoryDTO from(StatusHistory h) {
        if (h == null) {
            return null;
        }
        return StatusHistoryDTO.builder()
                .id(h.getId())
                .orderId(h.getOrderId())
                .fromStatus(h.getFromStatus())
                .toStatus(h.getToStatus())
                .operatorId(h.getOperatorId())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
