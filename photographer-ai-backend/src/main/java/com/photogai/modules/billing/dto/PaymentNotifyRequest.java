package com.photogai.modules.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付通道回调通知（微信/支付宝）。签名校验留网关实现。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotifyRequest {
    private String rawBody;
    private String signature;
}
