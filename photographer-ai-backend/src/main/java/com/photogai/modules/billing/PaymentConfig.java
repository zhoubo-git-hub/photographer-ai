package com.photogai.modules.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 支付装配：提供微信支付专用 {@link RestClient}，并按 {@code app.payment.mock} 决定注入的网关。
 *
 * <p>默认 mock=true → 注入 {@link MockPaymentGateway}（沙箱全闭环）；
 * mock=false → 注入 {@link WechatPaymentGateway}（缺商户号时优雅降级）。
 */
@Configuration
public class PaymentConfig {

    @Bean
    public RestClient paymentRestClient() {
        return RestClient.builder().build();
    }

    @Bean
    public PaymentGateway paymentGateway(
            @Value("${app.payment.mock:true}") boolean mock,
            MockPaymentGateway mockGateway,
            WechatPaymentGateway wechatGateway) {
        return mock ? mockGateway : wechatGateway;
    }
}
