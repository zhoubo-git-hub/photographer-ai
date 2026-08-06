package com.photogai.modules.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新订单请求（不含状态流转与改客户，状态走 {@code /status} 端点）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderUpdateRequest {

    private String title;
    private String shootType;
    private BigDecimal amount;
    private BigDecimal depositAmount;
    private String currency;
    private LocalDate shootDate;
    private LocalDate shootEndDate;
    private Integer durationHours;
    private Integer photoCount;
    private String region;
    private String style;
    private String quoteSuggestion;
}
