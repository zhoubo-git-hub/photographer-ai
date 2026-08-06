package com.photogai.modules.order.entity;

import com.photogai.common.BaseEntity;
import com.photogai.modules.order.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 状态流转留痕。每次状态变更写一条记录，含操作人与时间戳。
 */
@Entity
@Table(name = "status_history")
@Getter
@Setter
@NoArgsConstructor
public class StatusHistory extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private OrderStatus toStatus;

    @Column(name = "operator_id")
    private Long operatorId;
}
