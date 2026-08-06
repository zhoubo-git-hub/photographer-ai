package com.photogai.modules.quota.entity;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工作室额度：在管订单数 + 当月 AI 报价已用次数。
 *
 * <p>{@code quota_month} 用于按月重置 AI 报价计数。
 */
@Entity
@Table(name = "quota", uniqueConstraints = {
        @UniqueConstraint(name = "uk_quota_studio", columnNames = "studio_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Quota extends BaseEntity {

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    @Column(name = "plan_type", nullable = false)
    private String planType = "FREE";

    @Column(name = "order_count", nullable = false)
    private Integer orderCount = 0;

    @Column(name = "ai_quote_used_month", nullable = false)
    private Integer aiQuoteUsedMonth = 0;

    /** 'YYYY-MM' */
    @Column(name = "quota_month", nullable = false)
    private String quotaMonth;
}
