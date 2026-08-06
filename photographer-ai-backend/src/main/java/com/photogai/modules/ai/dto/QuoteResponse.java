package com.photogai.modules.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 报价响应。价格区间 + 依据 + 给客户的话术 + 剩余免费次数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteResponse {

    private java.math.BigDecimal priceLow;
    private java.math.BigDecimal priceHigh;
    private String basis;
    private String script;
    /** 免费版剩余 AI 报价次数；专业版为 999（无限）。 */
    private Integer remainingQuota;
}
