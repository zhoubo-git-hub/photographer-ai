package com.photogai.modules.dashboard.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收入趋势点（按月/日分组）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenuePointDTO {
    private String period;
    private BigDecimal revenue;
}
