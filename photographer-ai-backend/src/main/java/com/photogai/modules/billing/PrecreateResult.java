package com.photogai.modules.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付下单预创建结果：支付单号 + 跳转/二维码。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrecreateResult {
    private String outTradeNo;
    private String payUrl;
    private String qrCode;
}
