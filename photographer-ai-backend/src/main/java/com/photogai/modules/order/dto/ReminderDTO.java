package com.photogai.modules.order.dto;

import com.photogai.modules.order.enums.ReminderStatus;
import com.photogai.modules.order.enums.ReminderType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提醒视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderDTO {

    private Long id;
    private Long orderId;
    private Long customerId;
    private ReminderType type;
    private LocalDateTime dueAt;
    private ReminderStatus status;
    /** 关联订单标题（便于展示）。 */
    private String orderTitle;
    /** 关联客户名称（复购等无订单提醒展示用）。 */
    private String customerName;

    public static ReminderDTO from(com.photogai.modules.order.entity.Reminder r, String orderTitle) {
        if (r == null) {
            return null;
        }
        return ReminderDTO.builder()
                .id(r.getId())
                .orderId(r.getOrderId())
                .customerId(r.getCustomerId())
                .type(r.getType())
                .dueAt(r.getDueAt())
                .status(r.getStatus())
                .orderTitle(orderTitle)
                .build();
    }
}
