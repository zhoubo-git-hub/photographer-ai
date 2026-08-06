package com.photogai.modules.billing;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.order.ReminderService;
import com.photogai.modules.order.enums.ReminderType;
import com.photogai.modules.quota.QuotaRepository;
import com.photogai.modules.quota.entity.Quota;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.entity.Studio;
import com.photogai.modules.team.RoleGuard;
import com.photogai.modules.billing.entity.Subscription;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订阅服务：PRO/TEAM 门禁与有效期判定的<b>统一入口</b>。
 *
 * <p>判定真源为 {@code subscription} 表的有效记录（status=ACTIVE 且 expires_at>now）。
 * {@link com.photogai.modules.quota.QuotaService#requirePro} 委托本服务，阶段2 五项门禁调用点不变。
 *
 * <p>多租户隔离：所有读写按 {@code studio_id}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** 套餐定价（元/月），与 application.yml 的 app.payment.price.* 保持一致。 */
    public static final int PRO_PRICE = 39;
    public static final int TEAM_PRICE = 99;

    /** 单月折算天数（subscribe 传入 months 时按 30d/月换算）。 */
    private static final long DAYS_PER_MONTH = 30;

    private final SubscriptionRepository subscriptionRepository;
    private final QuotaRepository quotaRepository;
    private final StudioRepository studioRepository;
    private final ReminderService reminderService;

    /** 当前是否解锁专业能力（PRO 或 TEAM 有效订阅均通过）。 */
    @Transactional(readOnly = true)
    public boolean isPro(Long studioId) {
        return subscriptionRepository.findActiveByStudioId(studioId, LocalDateTime.now()).isPresent();
    }

    /** 是否为团队版有效订阅（仅 TEAM）。 */
    @Transactional(readOnly = true)
    public boolean isTeam(Long studioId) {
        return subscriptionRepository.findActiveByStudioId(studioId, LocalDateTime.now())
                .map(s -> "TEAM".equals(s.getPlanType()))
                .orElse(false);
    }

    /** 当前套餐类型：TEAM > PRO > FREE。 */
    @Transactional(readOnly = true)
    public String getPlanType(Long studioId) {
        if (isTeam(studioId)) {
            return "TEAM";
        }
        if (isPro(studioId)) {
            return "PRO";
        }
        return "FREE";
    }

    /**
     * 专业版门禁（PRO/TEAM 通过）。
     * 从未订阅抛 {@link ErrorCode#PRO_REQUIRED}(403)；订阅过期抛 {@link ErrorCode#PAYMENT_REQUIRED}(402)。
     */
    @Transactional(readOnly = true)
    public void requirePro(Long studioId) {
        if (isPro(studioId)) {
            return;
        }
        if (hasExpiredSubscription(studioId)) {
            throw new BizException(ErrorCode.PAYMENT_REQUIRED, "订阅已到期，请续费以继续使用");
        }
        throw new BizException(ErrorCode.PRO_REQUIRED, "该功能为专业版专属，请升级专业版");
    }

    /** 团队版门禁（仅 TEAM 有效订阅）。 */
    @Transactional(readOnly = true)
    public void requireTeam(Long studioId) {
        if (!isTeam(studioId)) {
            throw new BizException(ErrorCode.TEAM_REQUIRED, "该功能需团队版");
        }
    }

    /** 是否存在"曾订阅但已过期"的记录（用于区分 402 续费 vs 403 新购）。 */
    @Transactional(readOnly = true)
    public boolean hasExpiredSubscription(Long studioId) {
        return subscriptionRepository.findByStudioIdOrderByStartedAtDesc(studioId).stream()
                .anyMatch(s -> "EXPIRED".equals(s.getStatus())
                        || (s.getExpiresAt() != null && s.getExpiresAt().isBefore(LocalDateTime.now())));
    }

    /**
     * 激活订阅：写 subscription + 同步 studio/quota 的 plan_type 缓存 + 升级通知。
     *
     * @param months 订阅月数（按 30d/月折算）
     */
    @Transactional
    public Subscription activate(Long studioId, String planType, int months, String channel) {
        LocalDateTime now = LocalDateTime.now();
        Subscription sub = new Subscription();
        sub.setStudioId(studioId);
        sub.setPlanType(planType);
        sub.setStatus("ACTIVE");
        sub.setStartedAt(now);
        sub.setExpiresAt(now.plus((long) months * DAYS_PER_MONTH, ChronoUnit.DAYS));
        sub.setAutoRenew(true);
        sub.setChannel(channel);
        Subscription saved = subscriptionRepository.save(sub);

        syncPlanTypeCache(studioId, planType);
        reminderService.create(studioId, null, null, ReminderType.SUBSCRIPTION_UPGRADED, now);
        log.info("订阅已激活：studio={}, plan={}, 到期={}", studioId, planType, saved.getExpiresAt());
        return saved;
    }

    /** 退订：关闭自动续费（订阅仍有效至自然到期，由 expireOverdue 降级）。仅 OWNER 可操作（PRD Q3）。 */
    @Transactional
    public void cancelAutoRenew(Long studioId, String operatorRole) {
        RoleGuard.assertOwnerOnly(operatorRole);
        Subscription sub = subscriptionRepository.findActiveByStudioId(studioId, LocalDateTime.now())
                .orElseThrow(() -> new BizException(ErrorCode.SUBSCRIPTION_NOT_FOUND, "无有效订阅"));
        sub.setAutoRenew(false);
        subscriptionRepository.save(sub);
    }

    /** 同步 studio.plan_type 与 quota.plan_type 展示缓存（不回写订阅真源）。 */
    @Transactional
    public void syncPlanTypeCache(Long studioId, String planType) {
        studioRepository.findById(studioId).ifPresent(studio -> {
            studio.setPlanType(planType);
            studioRepository.save(studio);
        });
        Quota quota = quotaRepository.findByStudioId(studioId)
                .orElseGet(() -> {
                    Quota q = new Quota();
                    q.setStudioId(studioId);
                    q.setOrderCount(0);
                    q.setAiQuoteUsedMonth(0);
                    q.setQuotaMonth(java.time.YearMonth.now().toString());
                    return q;
                });
        quota.setPlanType(planType);
        quotaRepository.save(quota);
    }

    /** 查询当前订阅视图（优先有效订阅，否则最近一条）。 */
    @Transactional(readOnly = true)
    public Optional<Subscription> current(Long studioId) {
        Optional<Subscription> active = subscriptionRepository.findActiveByStudioId(studioId, LocalDateTime.now());
        if (active.isPresent()) {
            return active;
        }
        var all = subscriptionRepository.findByStudioIdOrderByStartedAtDesc(studioId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * 每日扫描到期订阅，降级为 FREE 并通知。
     * cron 见 application.yml 的 app.subscription.expire-cron（默认 04:00）。
     */
    @Scheduled(cron = "${app.subscription.expire-cron}")
    @Transactional
    public void expireOverdue() {
        LocalDateTime now = LocalDateTime.now();
        for (Subscription sub : subscriptionRepository.findDue(now)) {
            sub.setStatus("EXPIRED");
            subscriptionRepository.save(sub);
            syncPlanTypeCache(sub.getStudioId(), "FREE");
            reminderService.create(sub.getStudioId(), null, null,
                    ReminderType.SUBSCRIPTION_EXPIRED, now);
            log.info("订阅已到期降级 FREE：studio={}, sub={}", sub.getStudioId(), sub.getId());
        }
    }
}
