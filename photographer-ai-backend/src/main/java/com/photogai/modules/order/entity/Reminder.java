package com.photogai.modules.order.entity;

import com.photogai.common.BaseEntity;
import com.photogai.modules.order.enums.ReminderStatus;
import com.photogai.modules.order.enums.ReminderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 到期自动提醒。P0 仅站内；P1 扩微信/短信。
 */
@Entity
@Table(name = "reminder")
@Getter
@Setter
@NoArgsConstructor
public class Reminder extends BaseEntity {

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    /** 复购等无关联订单的提醒归属客户（阶段2 新增，可空）。 */
    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReminderType type;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReminderStatus status = ReminderStatus.PENDING;
}
