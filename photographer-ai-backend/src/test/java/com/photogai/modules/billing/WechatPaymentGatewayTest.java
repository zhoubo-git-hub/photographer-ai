package com.photogai.modules.billing;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 微信支付网关骨架行为测试（纯 new + ReflectionTestUtils，不连 PG、不启 Spring 上下文）。
 *
 * <p>覆盖 {@code ensureConfigured()} 的 4 个判空分支（mchid/appid 的 ==null 与 isBlank）、
 * 未配置时下单/验签抛 PAYMENT_FAILED（消息含「未配置」）、配置齐全时 createOrder 抛「通道尚未启用」、
 * 以及 verifyAndParse 对合法/非法回调报文的解析分支。
 */
class WechatPaymentGatewayTest {

    private WechatPaymentGateway newGateway() {
        return new WechatPaymentGateway(RestClient.builder().build());
    }

    private void assertBiz(String contains, Executable executable) {
        BizException ex = assertThrows(BizException.class, executable);
        assertTrue(ex.getMessage().contains(contains),
                "消息应含「" + contains + "」，实际: " + ex.getMessage());
        assertEquals(ErrorCode.PAYMENT_FAILED.getCode(), ex.getCode());
    }

    @Test
    void ensureConfigured_all_branches_and_order_flows() {
        // 分支1：mchid == null -> ensureConfigured 抛「未配置」
        WechatPaymentGateway g1 = newGateway();
        ReflectionTestUtils.setField(g1, "mchid", null);
        assertBiz("未配置", () -> g1.createOrder("BASIC", "OT1"));
        assertBiz("未配置", () -> g1.verifyAndParse("{}"));

        // 分支2：mchid isBlank("") -> 抛「未配置」
        WechatPaymentGateway g2 = newGateway();
        ReflectionTestUtils.setField(g2, "mchid", "");
        assertBiz("未配置", () -> g2.createOrder("BASIC", "OT1"));

        // 分支3：mchid 正常、appid == null -> 抛「未配置」
        WechatPaymentGateway g3 = newGateway();
        ReflectionTestUtils.setField(g3, "mchid", "mchid-x");
        ReflectionTestUtils.setField(g3, "appid", null);
        assertBiz("未配置", () -> g3.createOrder("BASIC", "OT1"));

        // 分支4：mchid 正常、appid isBlank("") -> 抛「未配置」
        WechatPaymentGateway g4 = newGateway();
        ReflectionTestUtils.setField(g4, "mchid", "mchid-x");
        ReflectionTestUtils.setField(g4, "appid", "");
        assertBiz("未配置", () -> g4.createOrder("BASIC", "OT1"));

        // 配置齐全：ensureConfigured 不抛；createOrder 抛「通道尚未启用」；verifyAndParse 走真实解析分支
        WechatPaymentGateway g5 = newGateway();
        ReflectionTestUtils.setField(g5, "mchid", "mchid-x");
        ReflectionTestUtils.setField(g5, "appid", "appid-x");
        ReflectionTestUtils.setField(g5, "apiKey", "apikey-x");

        BizException orderEx = assertThrows(BizException.class, () -> g5.createOrder("BASIC", "OT1"));
        assertTrue(orderEx.getMessage().contains("通道尚未启用"),
                "配置齐全应抛「通道尚未启用」，实际: " + orderEx.getMessage());
        assertEquals(ErrorCode.PAYMENT_FAILED.getCode(), orderEx.getCode());

        // verifyAndParse 合法 JSON -> 提取 out_trade_no
        assertEquals("OT1", g5.verifyAndParse("{\"out_trade_no\":\"OT1\"}"));
        // verifyAndParse 合法 JSON 但无该字段 -> 返回空串（不抛）
        assertEquals("", g5.verifyAndParse("{}"));
        // verifyAndParse 非法 JSON -> 抛「回调解析失败」
        BizException parseEx = assertThrows(BizException.class, () -> g5.verifyAndParse("not-json"));
        assertTrue(parseEx.getMessage().contains("回调解析失败"),
                "非法 JSON 应抛「回调解析失败」，实际: " + parseEx.getMessage());
        assertEquals(ErrorCode.PAYMENT_FAILED.getCode(), parseEx.getCode());
    }
}
