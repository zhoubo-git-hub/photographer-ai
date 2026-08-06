package com.photogai.modules.contract.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同生成请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractGenerateRequest {

    @NotNull(message = "订单不能为空")
    private Long orderId;

    @NotNull(message = "模板不能为空")
    private Long templateId;
}
