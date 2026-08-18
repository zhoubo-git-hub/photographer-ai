package com.photogai.modules.billing;

import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mock 支付网关行为测试（纯 new，不连 PG、不启 Spring 上下文）。
 *
 * <p>覆盖 {@code verifyAndParse} 三元的两分支（rawBody==null -> ""；否则 trim），
 * 以及 {@code createOrder} 构造的 PrecreateResult（outTradeNo / payUrl / qrCode 的 SVG Base64）。
 */
class MockPaymentGatewayTest {

    private static final String QR_PREFIX = "data:image/svg+xml;base64,";

    @Test
    void verifyAndParse_covers_both_branches() {
        MockPaymentGateway g = new MockPaymentGateway();
        assertEquals("", g.verifyAndParse(null));        // rawBody == null -> ""
        assertEquals("OT1", g.verifyAndParse("  OT1  ")); // 否则 trim -> "OT1"
    }

    @Test
    void createOrder_builds_qr_and_urls() {
        MockPaymentGateway g = new MockPaymentGateway();
        PrecreateResult r = g.createOrder("BASIC", "OT1");

        assertEquals("OT1", r.getOutTradeNo());
        assertEquals("mock://pay/OT1", r.getPayUrl());
        assertTrue(r.getQrCode().startsWith(QR_PREFIX), "qrCode 应以 data URI 前缀开头");

        byte[] decoded = Base64.getDecoder().decode(r.getQrCode().substring(QR_PREFIX.length()));
        String svg = new String(decoded);
        assertTrue(svg.contains("OT1"), "二维码 SVG 应含订单号 OT1，实际: " + svg);
    }
}
