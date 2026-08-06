package com.photogai.modules.repurchase.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 复购任务视图：客户 / 周期 / 触发日 / 状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepurchaseTaskDTO {

    private Long reminderId;
    private Long customerId;
    private String customerName;
    private String shootType;
    private String lastShootDate;
    private Integer repurchaseCycleDays;
    private String dueAt;
    private String status;
}
