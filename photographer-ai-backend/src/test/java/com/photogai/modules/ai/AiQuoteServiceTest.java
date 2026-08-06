package com.photogai.modules.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.photogai.modules.ai.dto.QuoteRequest;
import com.photogai.modules.ai.dto.QuoteResponse;
import com.photogai.modules.quota.QuotaService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AI 报价服务单元测试（对应 PRD P0-4 / 架构 AI 报价降级）。
 *
 * <p>验证两点：
 * 1. 规则计算（computeRule）系数为确定性、价位合理（priceLow < priceHigh 且 >= 0）；
 * 2. LLM 不可用（apiKey 缺失 / 调用异常）时自动降级为规则计算，不抛异常。
 */
@ExtendWith(MockitoExtension.class)
class AiQuoteServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private QuotaService quotaService;

    @Mock
    private QuoteCalibrationService calibrationService;

    @InjectMocks
    private AiQuoteService service;

    @Test
    void computeRuleProducesDeterministicReasonablePrice() {
        // 婚纱写真 / 4h / 80张 / 上海(一线) / 轻奢
        // base=2999; duration=1.1; photo=1.10; region=1.20; style=1.15
        QuoteRequest req = QuoteRequest.builder()
                .shootType("婚纱写真").durationHours(4).photoCount(80)
                .region("上海").style("轻奢").customerName("王小姐").build();

        // computeRule 会读取校准系数，FREE 用户/未采纳场景恒定返回 1.0（即原规则价）
        when(calibrationService.appliedCoef(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ONE);

        QuoteResponse r = service.computeRule(req, 1L);

        assertEquals(BigDecimal.valueOf(4507), r.getPriceLow());
        assertEquals(BigDecimal.valueOf(5759), r.getPriceHigh());
        assertTrue(r.getPriceLow().compareTo(r.getPriceHigh()) < 0);
        assertTrue(r.getPriceLow().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(r.getBasis().contains("基础价¥2999"));
        assertTrue(r.getScript().contains("王小姐"));
    }

    @Test
    void degradationReturnsRuleQuoteWhenLlmThrows() {
        // 模拟 LLM 客户端不可用（apiKey 缺失 / 调用失败）
        when(llmClient.complete(anyString()))
                .thenThrow(new IllegalStateException("LLM api-key 未配置，降级为规则计算"));

        QuoteRequest req = QuoteRequest.builder()
                .shootType("亲子").durationHours(2).photoCount(30)
                .region("成都").style("简约").build();

        // 走 computeRule 前需 stub 校准系数（否则 calibrationService.appliedCoef 默认返回 null → NPE）
        when(calibrationService.appliedCoef(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ONE);

        QuoteResponse result = service.quote(req, 1L);

        assertNotNull(result);
        // 降级为规则计算，价位合理且不抛异常
        assertTrue(result.getPriceLow().compareTo(result.getPriceHigh()) < 0);
        assertTrue(result.getPriceLow().compareTo(BigDecimal.ZERO) >= 0);
    }

    @Test
    void llmSuccessPassesThrough() {
        QuoteResponse llmResp = QuoteResponse.builder()
                .priceLow(BigDecimal.valueOf(3000))
                .priceHigh(BigDecimal.valueOf(3600))
                .basis("LLM 依据").script("LLM 话术").build();
        when(llmClient.complete(anyString())).thenReturn(llmResp);

        QuoteRequest req = QuoteRequest.builder().shootType("毕业").build();
        // 走 computeRule 前需 stub 校准系数（quote 内部先调 computeRule 再调 LLM）
        when(calibrationService.appliedCoef(anyLong(), any(), any(), any())).thenReturn(BigDecimal.ONE);
        QuoteResponse result = service.quote(req, 1L);

        assertEquals(BigDecimal.valueOf(3000), result.getPriceLow());
        assertEquals(BigDecimal.valueOf(3600), result.getPriceHigh());
    }

    @Test
    void quoteChecksQuotaBeforeCallingLlm() {
        // 免费版当月 AI 报价已用满 5 次 → 抛业务异常（403 语义）
        org.mockito.Mockito.doThrow(
                new com.photogai.exception.BizException(
                        com.photogai.common.ErrorCode.FORBIDDEN, "已达上限"))
                .when(quotaService).checkAiQuoteLimit(anyLong());

        QuoteRequest req = QuoteRequest.builder().shootType("毕业").build();
        assertThrows(com.photogai.exception.BizException.class, () -> service.quote(req, 1L));
    }
}
