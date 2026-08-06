package com.photogai.modules.ai;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.ai.dto.QuoteCalibrationDTO;
import com.photogai.modules.ai.entity.QuoteCalibration;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 自学习报价校准服务（受限版）。
 *
 * <p>扫描历史成交单（{@code DELIVER/REPURCHASE} 且金额非空），按维度（地区|类型|风格）聚合，
 * 计算建议系数偏移：{@code suggestedCoef = 实际均价 / 规则基准价}，偏移截断到 ±{@link #MAX_OFFSET_PCT}%。
 * 仅当 {@code |offset| <= 15% 且 样本 >= MIN_SAMPLE} 视为边界内、可人工采纳。
 *
 * <p><b>不自动覆盖</b>线上系数：建议始终为 PENDING，采纳（{@link #apply}）才置 APPLIED 并写回；
 * FREE 用户按原规则（{@link #appliedCoef} 对非 PRO 返回 1.0）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteCalibrationService {

    /** 单次偏移安全边界 ±15%。 */
    public static final int MAX_OFFSET_PCT = 15;
    /** 生效所需最小样本数。 */
    public static final int MIN_SAMPLE = 20;

    private final QuoteCalibrationRepository calibrationRepository;
    private final OrderRepository orderRepository;
    private final SubscriptionService subscriptionService;

    /** 计入"历史成交"的状态（金额真实可信）。 */
    private static final List<OrderStatus> DEAL_STATUSES = List.of(
            OrderStatus.DELIVER, OrderStatus.REPURCHASE);

    // ===== 规则基准系数（与 AiQuoteService.computeRule 同口径：基础价 × 地区 × 风格，时长/张数默认 1.0） =====
    private static final Map<String, Integer> BASE_PRICE = Map.ofEntries(
            Map.entry("婚纱写真", 2999),
            Map.entry("亲子", 1299),
            Map.entry("毕业", 899),
            Map.entry("商务", 1999),
            Map.entry("写真", 1499),
            Map.entry("儿童", 1199),
            Map.entry("孕婴", 1699),
            Map.entry("婚礼跟拍", 3599));
    private static final Set<String> TIER1 = Set.of("北京", "上海", "广州", "深圳");
    private static final Set<String> TIER2 = Set.of(
            "成都", "杭州", "重庆", "武汉", "西安", "苏州", "南京", "天津",
            "长沙", "郑州", "青岛", "东莞", "宁波", "佛山");

    /** D1 列表；首次触发懒扫描生成建议。 */
    @Transactional
    public List<QuoteCalibrationDTO> list(Long studioId) {
        subscriptionService.requirePro(studioId);
        scan(studioId);
        return calibrationRepository.findByStudioId(studioId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** 懒扫描：基于历史成交聚合各维度建议系数，upsert 为 PENDING（已采纳者保持稳定不漂移）。 */
    @Transactional
    public void scan(Long studioId) {
        List<Order> deals = orderRepository.findByStudioIdAndDeletedAtIsNull(studioId).stream()
                .filter(o -> DEAL_STATUSES.contains(o.getStatus())
                        && o.getAmount() != null
                        && o.getRegion() != null && !o.getRegion().isBlank()
                        && o.getShootType() != null && !o.getShootType().isBlank())
                .collect(Collectors.toList());

        Map<String, List<Order>> byDim = new LinkedHashMap<>();
        for (Order o : deals) {
            String key = dimensionKey(o.getRegion(), o.getShootType(), o.getStyle());
            byDim.computeIfAbsent(key, k -> new ArrayList<>()).add(o);
        }

        for (Map.Entry<String, List<Order>> e : byDim.entrySet()) {
            List<Order> group = e.getValue();
            int sample = group.size();

            double ruleAvg = group.stream()
                    .mapToDouble(o -> ruleBaseline(o.getRegion(), o.getShootType(), o.getStyle()))
                    .average().orElse(0.0);
            double actualAvg = group.stream()
                    .mapToDouble(o -> o.getAmount().doubleValue())
                    .average().orElse(0.0);
            if (ruleAvg <= 0) {
                continue;
            }

            double suggested = actualAvg / ruleAvg;
            int offsetPct = (int) Math.round((suggested - 1.0) * 100.0);
            offsetPct = clampOffset(offsetPct);
            boolean within = Math.abs(offsetPct) <= MAX_OFFSET_PCT && sample >= MIN_SAMPLE;

            BigDecimal sugCoef = BigDecimal.valueOf(1.0 + offsetPct / 100.0)
                    .setScale(4, RoundingMode.HALF_UP);

            Optional<QuoteCalibration> existing =
                    calibrationRepository.findByStudioIdAndDimensionKey(studioId, e.getKey());
            // 已采纳的建议保持稳定，不随重新扫描漂移
            if (existing.isPresent() && "APPLIED".equals(existing.get().getStatus())) {
                continue;
            }

            QuoteCalibration entity = existing.orElseGet(() -> {
                QuoteCalibration q = new QuoteCalibration();
                q.setStudioId(studioId);
                q.setDimensionKey(e.getKey());
                q.setDimensionLabel(dimensionLabel(e.getKey()));
                q.setCurrentCoef(BigDecimal.ONE);
                return q;
            });
            entity.setSampleCount(sample);
            entity.setSuggestedCoef(sugCoef);
            entity.setOffsetPct(offsetPct);
            entity.setWithinBoundary(within);
            entity.setStatus("PENDING");
            calibrationRepository.save(entity);
        }
    }

    /** D2 采纳：写回建议系数（仅边界内且样本充足；越界/样本不足拒绝并抛 409）。 */
    @Transactional
    public QuoteCalibrationDTO apply(Long studioId, Long id) {
        subscriptionService.requirePro(studioId);
        QuoteCalibration entity = calibrationRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "校准建议不存在"));
        if (!studioId.equals(entity.getStudioId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作该工作室的校准建议");
        }
        if (!entity.isWithinBoundary()) {
            if (entity.getSampleCount() < MIN_SAMPLE) {
                throw new BizException(ErrorCode.CALIBRATION_SAMPLE_INSUFFICIENT,
                        "样本不足（需≥" + MIN_SAMPLE + "），建议仅供参考，暂不生效");
            }
            throw new BizException(ErrorCode.CALIBRATION_OUT_OF_BOUND,
                    "校准偏移越界（单次≤±" + MAX_OFFSET_PCT + "%），禁止采纳");
        }
        entity.setStatus("APPLIED");
        entity.setAppliedAt(LocalDateTime.now());
        calibrationRepository.save(entity);
        log.info("采纳报价校准：studio={}, dim={}, coef={}",
                studioId, entity.getDimensionKey(), entity.getSuggestedCoef());
        return toDto(entity);
    }

    /** 已采纳系数查询（供 AiQuoteService 读取作为乘子）。 */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> appliedCoefMap(Long studioId) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (QuoteCalibration q : calibrationRepository.findByStudioIdAndStatus(studioId, "APPLIED")) {
            map.put(q.getDimensionKey(), q.getSuggestedCoef());
        }
        return map;
    }

    /** 读取某维度的已采纳系数（非 PRO 或缺失返回 1.0，即保持原规则）。 */
    public BigDecimal appliedCoef(Long studioId, String region, String shootType, String style) {
        if (!subscriptionService.isPro(studioId)) {
            return BigDecimal.ONE;
        }
        BigDecimal c = appliedCoefMap(studioId).get(dimensionKey(region, shootType, style));
        return c != null ? c : BigDecimal.ONE;
    }

    private String dimensionKey(String region, String shootType, String style) {
        if (style != null && !style.isBlank()) {
            return region + "|" + shootType + "|" + style;
        }
        return region + "|" + shootType;
    }

    private String dimensionLabel(String key) {
        return key.replace("|", "·");
    }

    private int clampOffset(int offsetPct) {
        return Math.max(-MAX_OFFSET_PCT, Math.min(MAX_OFFSET_PCT, offsetPct));
    }

    /** 规则基准价（与 AiQuoteService 同口径：基础价 × 地区系数 × 风格系数，时长/张数默认 1.0）。 */
    private double ruleBaseline(String region, String shootType, String style) {
        int base = BASE_PRICE.getOrDefault(shootType, 1299);
        return base * regionCoef(region) * styleCoef(style);
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

    private QuoteCalibrationDTO toDto(QuoteCalibration q) {
        String note;
        if (q.getSampleCount() < MIN_SAMPLE) {
            note = "样本不足（需≥" + MIN_SAMPLE + "），仅供参考";
        } else if (!q.isWithinBoundary()) {
            note = "已达安全边界（单次≤±" + MAX_OFFSET_PCT + "%）";
        } else {
            note = "边界内，可采纳";
        }
        return QuoteCalibrationDTO.builder()
                .id(q.getId())
                .dimensionKey(q.getDimensionKey())
                .dimensionLabel(q.getDimensionLabel())
                .sampleCount(q.getSampleCount())
                .currentCoef(q.getCurrentCoef())
                .suggestedCoef(q.getSuggestedCoef())
                .offsetPct(q.getOffsetPct())
                .withinBoundary(q.isWithinBoundary())
                .status(q.getStatus())
                .note(note)
                .build();
    }
}
