package com.photogai.modules.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 微信支付网关（APIv3 Native 支付）骨架。
 *
 * <p>复用 Spring 内置 {@link RestClient}，<b>不引微信官方 SDK</b>。商户号（mchid/appid/key）缺省时
 * 下单/验签直接抛 {@link ErrorCode#PAYMENT_FAILED} 并提示"待配置"，<b>优雅降级不崩</b>。
 * 真实签名与回调验签留标准实现位（商户资质到位后填充），当前为可运行的骨架。
 */
@Slf4j
@Component
public class WechatPaymentGateway implements PaymentGateway {

    private static final String NATIVE_URL = "https://api.mch.weixin.qq.com/v3/pay/transactions/native";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.payment.wechat.mchid:}")
    private String mchid;

    @Value("${app.payment.wechat.appid:}")
    private String appid;

    @Value("${app.payment.wechat.key:}")
    private String apiKey;

    public WechatPaymentGateway(RestClient paymentRestClient) {
        this.restClient = paymentRestClient;
    }

    private void ensureConfigured() {
        if (mchid == null || mchid.isBlank() || appid == null || appid.isBlank()) {
            throw new BizException(ErrorCode.PAYMENT_FAILED, "微信支付未配置（待配置商户号）");
        }
    }

    @Override
    public PrecreateResult createOrder(String planType, String outTradeNo) {
        ensureConfigured();
        // 真实实现：组装 APIv3 请求体 + 签名头，POST NATIVE_URL，解析 code_url。
        // 此处保留骨架，避免缺商户号时崩溃。
        log.info("[WechatGateway] 预创建订单（骨架）：plan={}, outTradeNo={}", planType, outTradeNo);
        throw new BizException(ErrorCode.PAYMENT_FAILED, "微信支付通道尚未启用，请使用 Mock 支付或配置商户号");
    }

    @Override
    public String verifyAndParse(String rawBody) {
        ensureConfigured();
        // 真实实现：用 apiKey 验签后解析 out_trade_no。当前骨架做最佳努力解析。
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            return root.path("out_trade_no").asText();
        } catch (Exception e) {
            throw new BizException(ErrorCode.PAYMENT_FAILED, "微信回调解析失败：" + e.getMessage());
        }
    }
}
