package com.photogai.modules.ai;

import com.photogai.modules.ai.dto.QuoteRequest;
import com.photogai.modules.ai.dto.QuoteResponse;
import com.photogai.modules.quota.QuotaService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 报价服务。
 *
 * <p>流程：额度校验 → 基于规则系数拼装 Prompt → 调 LLM（JSON 输出）→ 累加计数。
 * 当密钥缺失 / 调用失败 / 解析失败时，<b>降级为纯规则计算</b>并打日志，不崩溃。
 */
@Slf4j
@Service
public class AiQuoteService {

    /** 各拍摄类型基础价（元）。 */
    private static final Map<String, Integer> BASE_PRICE = Map.ofEntries(
            Map.entry("婚纱写真", 2999),
            Map.entry("亲子", 1299),
            Map.entry("毕业", 899),
            Map.entry("商务", 1999),
            Map.entry("写真", 1499),
            Map.entry("儿童", 1199),
            Map.entry("孕婴", 1699),
            Map.entry("婚礼跟拍", 3599));

    /** 一线城市。 */
    private static final java.util.Set<String> TIER1 = java.util.Set.of("北京", "上海", "广州", "深圳");
    /** 新一线。 */
    private static final java.util.Set<String> TIER2 = java.util.Set.of(
            "成都", "杭州", "重庆", "武汉", "西安", "苏州", "南京", "天津", "长沙", "郑州", "青岛", "东莞", "宁波", "佛山");

    private final LlmClient llmClient;
    private final QuotaService quotaService;
    private final QuoteCalibrationService calibrationService;

    public AiQuoteService(LlmClient llmClient, QuotaService quotaService,
                          QuoteCalibrationService calibrationService) {
        this.llmClient = llmClient;
        this.quotaService = quotaService;
        this.calibrationService = calibrationService;
    }

    public QuoteResponse quote(QuoteRequest req, Long studioId) {
        quotaService.checkAiQuoteLimit(studioId);

        QuoteResponse rule = computeRule(req, studioId);
        QuoteResponse result;
        try {
            String prompt = buildPrompt(req, rule);
            result = llmClient.complete(prompt);
        } catch (Exception e) {
            log.warn("LLM 报价不可用，降级为规则计算：{}", e.getMessage());
            result = rule;
        }

        // 配额记账独立保护：即便失败也不影响已降级的结果返回，避免把降级请求变成 500 系统错误
        try {
            quotaService.incrementAiQuoteUsed(studioId);
            result.setRemainingQuota(quotaService.getRemainingAiQuota(studioId));
        } catch (Exception e) {
            log.warn("AI 报价配额记账失败（不影响报价返回）：{}", e.getMessage());
        }
        return result;
    }

    /** 基于规则系数生成报价（同时用于 Prompt 参考与降级兜底）。 */
    QuoteResponse computeRule(QuoteRequest req, Long studioId) {
        int base = BASE_PRICE.getOrDefault(
                req.getShootType() == null ? "" : req.getShootType(), 1299);

        double durationCoef = 1.0
                + Math.max(0, (req.getDurationHours() == null ? 3 : req.getDurationHours()) - 3) * 0.10;
        double photoCoef = photoCoef(req.getPhotoCount() == null ? 0 : req.getPhotoCount());
        double regionCoef = regionCoef(req.getRegion());
        double styleCoef = styleCoef(req.getStyle());

        // 已采纳的校准系数作为本维度的乘子叠加（FREE 降级为 1.0，即原规则）
        double calibrationCoef = calibrationService
                .appliedCoef(studioId, req.getRegion(), req.getShootType(), req.getStyle())
                .doubleValue();
        double price = base * durationCoef * photoCoef * regionCoef * styleCoef * calibrationCoef;
        BigDecimal low = BigDecimal.valueOf(Math.round(price * 0.9))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal high = BigDecimal.valueOf(Math.round(price * 1.15))
                .setScale(0, RoundingMode.HALF_UP);

        String basis = String.format(
                "基础价¥%d × 时长系数%.2f × 张数系数%.2f × 地区系数%.2f × 风格系数%.2f",
                base, durationCoef, photoCoef, regionCoef, styleCoef);

        String script = buildScript(req, low, high);

        return QuoteResponse.builder()
                .priceLow(low)
                .priceHigh(high)
                .basis(basis)
                .script(script)
                .build();
    }

    private double photoCoef(int count) {
        if (count <= 0) {
            return 1.0;
        }
        if (count <= 50) {
            return 1.0;
        }
        if (count <= 100) {
            return 1.10;
        }
        if (count <= 200) {
            return 1.25;
        }
        return 1.40;
    }

    private double regionCoef(String region) {
        if (region == null) {
            return 1.0;
        }
        if (TIER1.contains(region)) {
            return 1.20;
        }
        if (TIER2.contains(region)) {
            return 1.10;
        }
        return 1.0;
    }

    private double styleCoef(String style) {
        if (style == null) {
            return 1.0;
        }
        return switch (style) {
            case "轻奢" -> 1.15;
            case "高级感" -> 1.30;
            case "复古" -> 1.10;
            case "简约" -> 1.00;
            case "韩式" -> 1.10;
            case "自然" -> 1.00;
            default -> 1.0;
        };
    }

    private String buildScript(QuoteRequest req, BigDecimal low, BigDecimal high) {
        String name = (req.getCustomerName() == null || req.getCustomerName().isBlank())
                ? "您好" : (req.getCustomerName() + "您好");
        String type = req.getShootType() == null ? "拍摄" : req.getShootType();
        int hours = req.getDurationHours() == null ? 0 : req.getDurationHours();
        int photos = req.getPhotoCount() == null ? 0 : req.getPhotoCount();
        String style = req.getStyle() == null ? "" : ("/" + req.getStyle() + "风");
        String region = req.getRegion() == null ? "" : (req.getRegion() + "地区");
        return String.format(
                "%s，您的%s套餐（%d小时/%d张%s%s）建议报价 ¥%s–¥%s，"
                        + "包含前期沟通、当天拍摄、精修与成片交付，具体可按需求微调。",
                name, type, hours, photos, style, region,
                low.toPlainString(), high.toPlainString());
    }

    /** 拼装给 LLM 的用户级 Prompt，附带规则参考价，要求输出 JSON。 */
    private String buildPrompt(QuoteRequest req, QuoteResponse rule) {
        return String.format(
                "请为以下摄影订单给出报价建议，并严格以 JSON 返回：\n"
                        + "拍摄类型：%s\n时长：%s 小时\n张数：%s 张\n地区：%s\n风格：%s\n"
                        + "规则参考价（仅作基准，可微调）：¥%s–¥%s\n"
                        + "要求：priceLow、priceHigh 为数字（元），basis 简述依据，"
                        + "script 为给客户的中文报价话术（%s）。",
                req.getShootType(),
                req.getDurationHours(),
                req.getPhotoCount(),
                req.getRegion(),
                req.getStyle(),
                rule.getPriceLow(), rule.getPriceHigh(),
                (req.getCustomerName() == null || req.getCustomerName().isBlank())
                        ? "用礼貌称呼" : ("称呼" + req.getCustomerName()));
    }
}
