package com.photogai.modules.customer.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新客户请求（全量可选字段，含阶段2 画像字段）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerUpdateRequest {

    private String name;
    private String wechatId;
    private String phone;
    private String tags;
    private String note;

    // 阶段2 画像字段（可选）
    private LocalDate lastShootDate;
    private Integer repurchaseCycleDays;
    private LocalDate birthday;
    private LocalDate anniversary;
    private Boolean repurchaseEnabled;
    private String sourceChannel;
}
