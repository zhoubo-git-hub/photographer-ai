package com.photogai.modules.customer.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新建客户请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCreateRequest {

    @NotBlank(message = "客户名称不能为空")
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
