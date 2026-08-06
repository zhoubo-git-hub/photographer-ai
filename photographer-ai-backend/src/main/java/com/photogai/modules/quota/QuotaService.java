package com.photogai.modules.quota;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.quota.dto.QuotaDTO;
import com.photogai.modules.quota.entity.Quota;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.entity.Studio;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 额度服务：免费版订单数 ≤10、AI 报价 5 次/月控制；阶段2 新增专业版门禁 {@link #requirePro}。
 *
 * <p>{@code quota_month} 变更时自动重置当月 AI 报价计数。
 *
 * <p>阶段3 起：PRO 真源改为"有效订阅（{@code subscription} 表有效期）"，
 * {@link #requirePro} 委托 {@link SubscriptionService#requirePro}，不破坏阶段2 五项门禁调用点。
 * {@code quota.plan_type} 仅作为展示缓存，由订阅生命周期经 {@link #syncPlanType} 同步。
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    /** 免费版在管订单上限。 */
    public static final int FREE_ORDER_LIMIT = 10;
    /** 免费版每月 AI 报价上限。 */
    public static final int FREE_AI_QUOTE_LIMIT = 5;
    /** 专业版视为无限。 */
    public static final int UNLIMITED = 999;

    private final QuotaRepository quotaRepository;
    private final OrderRepository orderRepository;
    private final StudioRepository studioRepository;
    private final SubscriptionService subscriptionService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 专业版门禁：以"有效订阅"为唯一真源（PRO 或 TEAM 均通过）。
     * 从未订阅抛 {@link ErrorCode#PRO_REQUIRED}(403)；订阅过期抛 {@link ErrorCode#PAYMENT_REQUIRED}(402)。
     */
    @Transactional(readOnly = true)
    public void requirePro(Long studioId) {
        subscriptionService.requirePro(studioId);
    }

    /** 同步展示缓存 plan_type（订阅激活/到期时由 SubscriptionService 调用）。 */
    @Transactional
    public void syncPlanType(Long studioId, String planType) {
        Quota quota = getOrInit(studioId);
        quota.setPlanType(planType);
        quotaRepository.save(quota);
    }

    /** 确保当前 studio 在管订单未触顶（免费版 ≤10）。 */
    @Transactional
    public void ensureWithinLimit(Long studioId) {
        Quota quota = getOrInit(studioId);
        long activeOrders = orderRepository.countByStudioIdAndDeletedAtIsNull(studioId);
        quota.setOrderCount((int) activeOrders);
        quotaRepository.save(quota);

        if (!subscriptionService.isPro(studioId) && activeOrders >= FREE_ORDER_LIMIT) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "免费版在管订单已达 " + FREE_ORDER_LIMIT + " 单上限，请升级专业版解锁不限单");
        }
    }

    /** 免费版校验 AI 报价月额度（5 次/月）。专业版不限制。 */
    @Transactional
    public void checkAiQuoteLimit(Long studioId) {
        Quota quota = getOrInit(studioId);
        refreshMonth(quota);
        quotaRepository.save(quota);

        if (!subscriptionService.isPro(studioId)
                && quota.getAiQuoteUsedMonth() >= FREE_AI_QUOTE_LIMIT) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "免费版本月 AI 报价已用满 " + FREE_AI_QUOTE_LIMIT + " 次，请升级专业版解锁无限次");
        }
    }

    /** 成功后累加当月 AI 报价使用次数。 */
    @Transactional
    public void incrementAiQuoteUsed(Long studioId) {
        Quota quota = getOrInit(studioId);
        refreshMonth(quota);
        quota.setAiQuoteUsedMonth(quota.getAiQuoteUsedMonth() + 1);
        quotaRepository.save(quota);
    }

    /** 重新统计在管订单数（软删/建单后调用）。 */
    @Transactional
    public void recountOrders(Long studioId) {
        Quota quota = getOrInit(studioId);
        long activeOrders = orderRepository.countByStudioIdAndDeletedAtIsNull(studioId);
        quota.setOrderCount((int) activeOrders);
        quotaRepository.save(quota);
    }

    /** 当前剩余 AI 报价次数。 */
    @Transactional
    public int getRemainingAiQuota(Long studioId) {
        Quota quota = getOrInit(studioId);
        refreshMonth(quota);
        quotaRepository.save(quota);
        if (subscriptionService.isPro(studioId)) {
            return UNLIMITED;
        }
        return Math.max(0, FREE_AI_QUOTE_LIMIT - quota.getAiQuoteUsedMonth());
    }

    /** 查询额度视图。 */
    @Transactional
    public QuotaDTO getQuota(Long studioId) {
        Quota quota = getOrInit(studioId);
        refreshMonth(quota);
        quotaRepository.save(quota);

        boolean free = !subscriptionService.isPro(studioId);
        int orderLimit = free ? FREE_ORDER_LIMIT : UNLIMITED;
        int aiLimit = free ? FREE_AI_QUOTE_LIMIT : UNLIMITED;
        int remainingAi = free
                ? Math.max(0, FREE_AI_QUOTE_LIMIT - quota.getAiQuoteUsedMonth())
                : UNLIMITED;
        int remainingOrder = free
                ? Math.max(0, FREE_ORDER_LIMIT - quota.getOrderCount())
                : UNLIMITED;

        return QuotaDTO.builder()
                .planType(quota.getPlanType())
                .orderCount(quota.getOrderCount())
                .orderLimit(orderLimit)
                .aiQuoteUsedMonth(quota.getAiQuoteUsedMonth())
                .aiQuoteLimit(aiLimit)
                .quotaMonth(quota.getQuotaMonth())
                .remainingOrderQuota(remainingOrder)
                .remainingAiQuota(remainingAi)
                .build();
    }

    /** 若跨月则重置当月 AI 报价计数，并刷新 quota_month。 */
    private void refreshMonth(Quota quota) {
        String current = YearMonth.from(LocalDate.now()).format(MONTH_FMT);
        if (!current.equals(quota.getQuotaMonth())) {
            quota.setQuotaMonth(current);
            quota.setAiQuoteUsedMonth(0);
        }
    }

    private Quota getOrInit(Long studioId) {
        return quotaRepository.findByStudioId(studioId)
                .orElseGet(() -> {
                    Quota q = new Quota();
                    q.setStudioId(studioId);
                    q.setPlanType("FREE");
                    q.setOrderCount(0);
                    q.setAiQuoteUsedMonth(0);
                    q.setQuotaMonth(YearMonth.from(LocalDate.now()).format(MONTH_FMT));
                    return quotaRepository.save(q);
                });
    }
}
