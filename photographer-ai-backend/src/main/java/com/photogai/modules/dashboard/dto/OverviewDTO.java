package com.photogai.modules.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 经营看板概览（纯聚合，零埋点）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverviewDTO {
    /** 收入（DELIVER/REPURCHASE 成交单金额合计）。 */
    private BigDecimal revenue;
    /** 成交订单数。 */
    private int orderCount;
    /** 客单价 AOV。 */
    private BigDecimal aov;
    /** 复购率（≥2 单客户 / 总客户）。 */
    private double repurchaseRate;

    /** 漏斗关键层到达数（consult/deposit/shoot/deliver）。 */
    private Conversion conversion;

    /** 收入趋势（按月分组）。 */
    private List<RevenuePointDTO> revenuePoints;

    /** 漏斗关键层计数。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Conversion {
        private int consult;
        private int deposit;
        private int shoot;
        private int deliver;
    }
}
