package com.photogai.modules.billing.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订阅下单响应（含支付入口）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeResponse {
    private String outTradeNo;
    private String payUrl;
    private String qrCode;
    private BigDecimal amount;
}
