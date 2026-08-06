package com.photogai.modules.billing;

/**
 * 支付网关抽象：与 {@code LlmClient} 同模式（接口 + Mock + 真实骨架）。
 *
 * <p>真实回调与 mock 支付走同一条 {@link BillingService#onPaid} 路径，保证闭环一致。
 */
public interface PaymentGateway {

    /**
     * 下单预创建。返回商户订单号（与 BillingService 生成的一致）及支付入口。
     *
     * @param planType   PRO | TEAM
     * @param outTradeNo 由 BillingService 生成的唯一商户订单号
     */
    PrecreateResult createOrder(String planType, String outTradeNo);

    /**
     * 解析支付通道回调报文，提取商户订单号。
     *
     * @param rawBody   回调原始报文
     * @return outTradeNo
     */
    String verifyAndParse(String rawBody);
}
