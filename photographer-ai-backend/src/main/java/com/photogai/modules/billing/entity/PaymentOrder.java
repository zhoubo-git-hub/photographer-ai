package com.photogai.modules.billing.entity;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 支付单：下单 → 支付 → 激活订阅的凭证。
 *
 * <p>{@code out_trade_no} 唯一（与支付通道对账）；{@code status} 状态机 PENDING→PAID/FAILED。
 * 真实回调与 mock 支付走同一条 {@code onPaid} 路径，保证闭环一致。
 */
@Entity
@Table(name = "payment_order")
@Getter
@Setter
@NoArgsConstructor
public class PaymentOrder extends BaseEntity {

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    /** PRO | TEAM */
    @Column(name = "plan_type", nullable = false)
    private String planType;

    /** WECHAT | ALIPAY | MOCK */
    @Column(nullable = false)
    private String channel;

    /** 支付通道商户订单号（唯一）。 */
    @Column(name = "out_trade_no", nullable = false, unique = true)
    private String outTradeNo;

    @Column(nullable = false)
    private BigDecimal amount;

    /** PENDING | PAID | FAILED */
    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "subscription_id")
    private Long subscriptionId;
}
