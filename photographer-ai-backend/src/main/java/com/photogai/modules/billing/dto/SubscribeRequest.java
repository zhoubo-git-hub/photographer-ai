package com.photogai.modules.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订阅下单请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeRequest {

    /** PRO | TEAM */
    @NotBlank(message = "套餐类型不能为空")
    private String planType;

    /** WECHAT | MOCK | ALIPAY（mock 模式下以实际网关为准）。 */
    private String channel;
}
