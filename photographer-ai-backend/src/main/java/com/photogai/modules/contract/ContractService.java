package com.photogai.modules.contract;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.contract.dto.ContractGenerateRequest;
import com.photogai.modules.contract.dto.ContractGenerateResponse;
import com.photogai.modules.contract.dto.ContractTemplateDTO;
import com.photogai.modules.customer.CustomerRepository;
import com.photogai.modules.customer.entity.Customer;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.entity.Studio;
import static java.util.Map.entry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合同服务：模板列表（全员可见）+ 字段替换引擎（纯字符串占位符，不依赖模板引擎）+ 生成（PRO）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    /** 占位符正则：{{key}}。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final ContractTemplateRepository templateRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final StudioRepository studioRepository;
    private final QuotaService quotaService;

    /** 列出内置 + 本工作室模板（E6 全员可见，无需 PRO）。 */
    @Transactional(readOnly = true)
    public List<ContractTemplateDTO> listTemplates(Long studioId) {
        return templateRepository.findByStudioIdIsNullOrStudioId(studioId).stream()
                .map(ContractTemplateDTO::from)
                .toList();
    }

    /** 套模板生成合同（E7 PRO 门禁）。 */
    @Transactional(readOnly = true)
    public ContractGenerateResponse generate(Long studioId, ContractGenerateRequest req) {
        quotaService.requirePro(studioId); // E7 PRO 门禁

        ContractTemplate template = templateRepository.findById(req.getTemplateId())
                .filter(t -> t.getStudioId() == null || Objects.equals(t.getStudioId(), studioId))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "合同模板不存在"));

        Order order = orderRepository.findById(req.getOrderId())
                .filter(o -> Objects.equals(o.getStudioId(), studioId) && o.getDeletedAt() == null)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "订单不存在"));

        Customer customer = customerRepository
                .findByStudioIdAndIdAndDeletedAtIsNull(studioId, order.getCustomerId())
                .orElse(null);
        Studio studio = studioRepository.findById(studioId).orElse(null);

        Map<String, String> values = buildValues(studio, customer, order);
        String content = render(template.getContent(), values);

        String customerName = customer != null ? customer.getName() : "客户";
        String title = template.getName() + " - " + customerName
                + (order.getShootType() != null ? "-" + order.getShootType() : "");

        return ContractGenerateResponse.builder().title(title).content(content).build();
    }

    /** 组装替换键值（缺失字段留空，供引擎决定是否保留占位符）。 */
    private Map<String, String> buildValues(Studio studio, Customer customer, Order order) {
        BigDecimal amount = order.getAmount() == null ? BigDecimal.ZERO : order.getAmount();
        BigDecimal deposit = order.getDepositAmount() == null ? BigDecimal.ZERO : order.getDepositAmount();
        BigDecimal balance = amount.subtract(deposit);
        String depositRatio = (order.getAmount() != null && order.getAmount().signum() > 0
                && order.getDepositAmount() != null)
                ? String.valueOf(deposit.multiply(BigDecimal.valueOf(100))
                        .divide(order.getAmount(), 0, RoundingMode.HALF_UP).intValue())
                : "";

        return Map.ofEntries(
                entry("studioName", studio != null ? nullToEmpty(studio.getName()) : ""),
                entry("customerName", customer != null ? nullToEmpty(customer.getName()) : "客户"),
                entry("wechatId", customer != null ? nullToEmpty(customer.getWechatId()) : ""),
                entry("phone", customer != null ? nullToEmpty(customer.getPhone()) : ""),
                entry("shootType", nullToEmpty(order.getShootType())),
                entry("shootDate", order.getShootDate() != null ? order.getShootDate().toString() : ""),
                entry("durationHours", order.getDurationHours() != null ? order.getDurationHours().toString() : ""),
                entry("photoCount", order.getPhotoCount() != null ? order.getPhotoCount().toString() : ""),
                entry("region", nullToEmpty(order.getRegion())),
                entry("style", nullToEmpty(order.getStyle())),
                entry("amount", order.getAmount() == null ? "" : order.getAmount().toPlainString()),
                entry("depositAmount", order.getDepositAmount() == null ? "" : order.getDepositAmount().toPlainString()),
                entry("balance", balance.toPlainString()),
                entry("depositRatio", depositRatio),
                entry("note", ""), // 订单暂无备注字段，保留占位符由引擎保留并告警
                entry("retouchCount", "") // 暂无精修张数字段，保留占位符由引擎保留并告警
        );
    }

    /** 占位符替换：命中键值则替换；未命中保留原占位符并告警。 */
    private String render(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            if (value != null && !value.isEmpty()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                log.warn("合同模板占位符未匹配，保留原文：{}", key);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
