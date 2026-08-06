package com.photogai.modules.ai;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.ai.dto.CommRequest;
import com.photogai.modules.ai.dto.CommResponse;
import com.photogai.modules.ai.enums.CommScenario;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.quota.QuotaService;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 沟通助手服务：拼装 Prompt → 调 {@link LlmClient}（复用阶段1 客户端与降级模式）→ 规则模板兜底。
 *
 * <p>PRO 门禁：以 {@link QuotaService#requirePro} 为真源，免费版直接 403。
 * LLM 调用异常（密钥缺失/网络/解析）一律降级为规则模板话术，标记 {@code fallback=true}，不报错、不空。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCommService {

    private static final String SYSTEM_PROMPT = "你是专业摄影师的客户沟通助手。"
            + "请基于给定的场景与客户/订单信息，生成一段自然、亲切、专业的中文微信沟通话术"
            + "（1-3 句，直接输出话术正文，不要使用 markdown、不要加引号包裹）。";

    private final QuotaService quotaService;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final LlmClient llmClient;

    public CommResponse generate(CommRequest req, Long studioId) {
        quotaService.requirePro(studioId); // PRO 门禁：免费版抛 PRO_REQUIRED(403)

        CommScenario scenario = req.getScenario();
        Order order = null;
        Customer customer = null;

        if (req.getOrderId() != null) {
            order = orderRepository.findById(req.getOrderId())
                    .filter(o -> Objects.equals(o.getStudioId(), studioId))
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "订单不存在"));
            customer = customerRepository
                    .findByStudioIdAndIdAndDeletedAtIsNull(studioId, order.getCustomerId())
                    .orElse(null);
        } else if (req.getCustomerId() != null) {
            customer = customerRepository
                    .findByStudioIdAndIdAndDeletedAtIsNull(studioId, req.getCustomerId())
                    .orElse(null);
        }

        if (scenario != CommScenario.REPURCHASE && order == null) {
            throw new BizException(ErrorCode.VALIDATION, "请指定订单");
        }
        if (scenario == CommScenario.REPURCHASE && customer == null) {
            throw new BizException(ErrorCode.VALIDATION, "请指定客户");
        }

        String name = (customer != null && customer.getName() != null) ? customer.getName() : "客户";

        try {
            String userPrompt = buildPrompt(scenario, order, customer, name);
            String text = llmClient.chat(SYSTEM_PROMPT, userPrompt);
            return CommResponse.builder().text(text).scenario(scenario).fallback(false).build();
        } catch (Exception e) {
            log.warn("LLM 沟通助手不可用，降级为规则模板：{}", e.getMessage());
            return CommResponse.builder()
                    .text(buildFallback(scenario, order, customer, name))
                    .scenario(scenario)
                    .fallback(true)
                    .build();
        }
    }

    private String buildPrompt(CommScenario scenario, Order order, Customer customer, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("场景：").append(scenario).append("\n");
        sb.append("客户称呼：").append(name).append("\n");
        if (order != null) {
            sb.append("拍摄类型：").append(nullToEmpty(order.getShootType())).append("\n");
            sb.append("拍摄日：").append(order.getShootDate() == null ? "" : order.getShootDate()).append("\n");
            sb.append("套餐金额：").append(fmt(order.getAmount())).append("\n");
            sb.append("已付定金：").append(fmt(order.getDepositAmount())).append("\n");
            sb.append("尾款：").append(fmt(balance(order))).append("\n");
        }
        if (customer != null) {
            sb.append("客户来源：").append(nullToEmpty(customer.getSourceChannel())).append("\n");
        }
        sb.append("请生成贴合该场景的微信沟通话术。");
        return sb.toString();
    }

    private String buildFallback(CommScenario scenario, Order order, Customer customer, String name) {
        String type = order != null && order.getShootType() != null ? order.getShootType() : "拍摄";
        String shootDate = order != null && order.getShootDate() != null
                ? order.getShootDate().toString() : "约定档期";
        switch (scenario) {
            case URGE_DEPOSIT:
                return String.format("%s您好，您的%s拍摄定金还差一步到位，方便时微信转我就行，"
                        + "我好帮您锁定档期~", name, type);
            case URGE_FINAL:
                return String.format("%s您好，您%s尾款 %s 还差最后一步，方便时微信转我就行~",
                        name, type, fmt(balance(order)));
            case PRE_SHOOT:
                return String.format("%s您好，您%s拍摄安排在 %s，当天建议早睡、素颜护肤，"
                        + "具体流程我稍后发您~", name, type, shootDate);
            case DELIVER_REVIEW:
                return String.format("%s您好，您的%s成片已交付，方便时给个好评/晒图就更好啦，"
                        + "后续有任何修图需求随时找我~", name, type);
            case FAQ:
                return String.format("%s您好，关于%s拍摄您有任何疑问都可以问我，"
                        + "我会第一时间帮您解答~", name, type);
            case REPURCHASE:
                return String.format("%s您好，去年为您拍摄的%s一周年啦，"
                        + "预约续拍享老客专属礼遇，有空聊聊？~", name, type);
            default:
                return String.format("%s您好，感谢您的信任，期待为您服务~", name);
        }
    }

    private BigDecimal balance(Order order) {
        if (order == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = order.getAmount() == null ? BigDecimal.ZERO : order.getAmount();
        BigDecimal deposit = order.getDepositAmount() == null ? BigDecimal.ZERO : order.getDepositAmount();
        return amount.subtract(deposit);
    }

    private String fmt(BigDecimal v) {
        if (v == null) {
            return "¥0";
        }
        return "¥" + v.toPlainString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
