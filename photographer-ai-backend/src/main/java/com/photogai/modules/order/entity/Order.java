package com.photogai.modules.order.entity;

import com.photogai.common.BaseEntity;
import com.photogai.modules.order.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单：核心实体。状态机见 {@link com.photogai.modules.order.statemachine.OrderStateMachine}。
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private String title;

    @Column(name = "shoot_type")
    private String shootType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.CONSULT;

    private BigDecimal amount;

    @Column(name = "deposit_amount")
    private BigDecimal depositAmount;

    @Column(length = 10)
    private String currency = "CNY";

    @Column(name = "shoot_date")
    private LocalDate shootDate;

    @Column(name = "shoot_end_date")
    private LocalDate shootEndDate;

    @Column(name = "duration_hours")
    private Integer durationHours;

    @Column(name = "photo_count")
    private Integer photoCount;

    private String region;

    private String style;

    @Column(name = "quote_suggestion", columnDefinition = "text")
    private String quoteSuggestion;

    @Column(name = "created_by")
    private Long createdBy;

    /** 分配给的成员用户 ID（团队协作，可空表示未分配）。 */
    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
