package com.photogai.modules.billing;

import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 支付网关：沙箱全闭环，零外部依赖。
 *
 * <p>直接回显商户订单号 + 一个占位二维码（SVG data URI），并直接把回调报文当作订单号返回，
 * 便于 {@code /api/billing/mock-pay} 走与真实回调完全相同的 {@link BillingService#onPaid} 状态机。
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PrecreateResult createOrder(String planType, String outTradeNo) {
        String payUrl = "mock://pay/" + outTradeNo;
        String qrCode = buildPlaceholderQr(outTradeNo);
        return PrecreateResult.builder()
                .outTradeNo(outTradeNo)
                .payUrl(payUrl)
                .qrCode(qrCode)
                .build();
    }

    @Override
    public String verifyAndParse(String rawBody) {
        // mock 模式：回调报文即商户订单号
        return rawBody == null ? "" : rawBody.trim();
    }

    /** 生成占位二维码（纯展示用，不具扫码含义）。 */
    private String buildPlaceholderQr(String text) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='160' height='160'>"
                + "<rect width='160' height='160' fill='#fff'/>"
                + "<text x='50%' y='50%' font-size='10' text-anchor='middle' fill='#333'>"
                + text + "</text></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes());
    }
}
