package com.photogai.modules.quota.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 额度视图对象。包含订单额度与 AI 报价额度的限制与剩余。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotaDTO {

    private String planType;
    private Integer orderCount;
    /** 在管订单上限：FREE=10，PRO=999（无限）。 */
    private Integer orderLimit;
    private Integer aiQuoteUsedMonth;
    /** AI 报价月上限：FREE=5，PRO=999（无限）。 */
    private Integer aiQuoteLimit;
    private String quotaMonth;
    /** 剩余可建订单数。 */
    private Integer remainingOrderQuota;
    /** 剩余 AI 报价次数。 */
    private Integer remainingAiQuota;
}
