package com.photogai.modules.order.dto;

import com.photogai.modules.order.enums.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新建订单请求。状态默认 CONSULT；档期字段用于冲突校验。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

    @NotNull(message = "客户 ID 不能为空")
    private Long customerId;

    @NotBlank(message = "订单标题不能为空")
    private String title;

    private String shootType;

    private OrderStatus status;

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
