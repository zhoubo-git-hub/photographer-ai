package com.photogai.modules.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.order.ReminderService;
import com.photogai.modules.order.enums.ReminderType;
import com.photogai.modules.quota.QuotaRepository;
import com.photogai.modules.quota.entity.Quota;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.entity.Studio;
import com.photogai.modules.billing.entity.Subscription;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 订阅服务单元测试（纯 Mockito）。
 *
 * <p>覆盖分支：isPro/isTeam/getPlanType 的 PRO/TEAM/FREE、requirePro 的三态
 * （通过 / 402 续费 / 403 新购）、requireTeam 越权、hasExpiredSubscription、
 * activate（含 plan_type 缓存同步 + 升级提醒）、cancelAutoRenew（OWNER / 非 OWNER / 无订阅）、
 * syncPlanTypeCache（studio 与 quota 两种情况）、current（生效 / 最近一条 / 无）、expireOverdue 降级。
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private QuotaRepository quotaRepository;
    @Mock
    private StudioRepository studioRepository;
    @Mock
    private ReminderService reminderService;

    @InjectMocks
    private SubscriptionService service;

    private Subscription activeSub(String planType) {
        Subscription s = new Subscription();
        s.setId(1L);
        s.setStudioId(1L);
        s.setPlanType(planType);
        s.setStatus("ACTIVE");
        s.setStartedAt(LocalDateTime.now().minusDays(1));
        s.setExpiresAt(LocalDateTime.now().plusDays(10));
        s.setAutoRenew(true);
        return s;
    }

    // ========================= 判定 =========================

    @Test
    void isProTrueWhenActiveExists() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activeSub("PRO")));
        assertTrue(service.isPro(1L));
    }

    @Test
    void isProFalseWhenNoActive() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        assertFalse(service.isPro(1L));
    }

    @Test
    void isTeamTrueOnlyForTeam() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activeSub("TEAM")));
        assertTrue(service.isTeam(1L));

        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activeSub("PRO")));
        assertFalse(service.isTeam(1L));
    }

    @Test
    void getPlanTypeResolvesTier() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activeSub("TEAM")));
        assertEquals("TEAM", service.getPlanType(1L));

        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activeSub("PRO")));
        assertEquals("PRO", service.getPlanType(1L));

        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        assertEquals("FREE", service.getPlanType(1L));
    }

    // ========================= 门禁 =========================

    @Test
    void requireProPassesWhenActive() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activeSub("PRO")));
        service.requirePro(1L); // 不抛异常
    }

    @Test
    void requireProThrowsPaymentRequiredWhenExpired() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        Subscription expired = new Subscription();
        expired.setStatus("EXPIRED");
        when(subscriptionRepository.findByStudioIdOrderByStartedAtDesc(1L)).thenReturn(List.of(expired));

        BizException ex = assertThrows(BizException.class, () -> service.requirePro(1L));
        assertEquals(ErrorCode.PAYMENT_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void requireProThrowsProRequiredWhenNeverSubscribed() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findByStudioIdOrderByStartedAtDesc(1L)).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.requirePro(1L));
        assertEquals(ErrorCode.PRO_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void requireTeamThrowsWhenNotTeam() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activeSub("PRO")));
        BizException ex = assertThrows(BizException.class, () -> service.requireTeam(1L));
        assertEquals(ErrorCode.TEAM_REQUIRED.getCode(), ex.getCode());
    }

    @Test
    void hasExpiredSubscriptionTrueForExpiredRecord() {
        Subscription expired = new Subscription();
        expired.setStatus("EXPIRED");
        when(subscriptionRepository.findByStudioIdOrderByStartedAtDesc(1L)).thenReturn(List.of(expired));
        assertTrue(service.hasExpiredSubscription(1L));
    }

    @Test
    void hasExpiredSubscriptionFalseWhenPastExpiryRecord() {
        Subscription past = new Subscription();
        past.setStatus("CANCELLED");
        past.setExpiresAt(LocalDateTime.now().minusDays(5));
        when(subscriptionRepository.findByStudioIdOrderByStartedAtDesc(1L)).thenReturn(List.of(past));
        assertTrue(service.hasExpiredSubscription(1L));

        when(subscriptionRepository.findByStudioIdOrderByStartedAtDesc(1L)).thenReturn(List.of());
        assertFalse(service.hasExpiredSubscription(1L));
    }

    // ========================= 激活 / 退订 =========================

    @Test
    void activateWritesSubscriptionAndSyncsCache() {
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));
        Studio studio = new Studio();
        studio.setId(1L);
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio));
        Quota quota = new Quota();
        quota.setStudioId(1L);
        when(quotaRepository.findByStudioId(1L)).thenReturn(Optional.of(quota));
        doNothing().when(reminderService).create(anyLong(), any(), any(), any(), any());

        Subscription saved = service.activate(1L, "PRO", 3, "WECHAT");
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals("PRO", saved.getPlanType());
        assertEquals("PRO", studio.getPlanType());
        assertEquals("PRO", quota.getPlanType());
        verify(reminderService).create(anyLong(), any(), any(), any(), any());
    }

    @Test
    void cancelAutoRenewSucceedsForOwner() {
        Subscription active = activeSub("PRO");
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(active));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        service.cancelAutoRenew(1L, "OWNER");
        assertFalse(active.isAutoRenew());
    }

    @Test
    void cancelAutoRenewThrowsForNonOwner() {
        BizException ex = assertThrows(BizException.class, () -> service.cancelAutoRenew(1L, "MEMBER"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void cancelAutoRenewThrowsWhenNoActive() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        BizException ex = assertThrows(BizException.class, () -> service.cancelAutoRenew(1L, "OWNER"));
        assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND.getCode(), ex.getCode());
    }

    // ========================= 缓存 / 查询 / 到期 =========================

    @Test
    void syncPlanTypeCacheCreatesQuotaWhenAbsent() {
        Studio studio = new Studio();
        studio.setId(1L);
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio));
        when(quotaRepository.findByStudioId(1L)).thenReturn(Optional.empty());
        when(quotaRepository.save(any(Quota.class))).thenAnswer(i -> i.getArgument(0));

        service.syncPlanTypeCache(1L, "TEAM");
        assertEquals("TEAM", studio.getPlanType());
        verify(quotaRepository).save(any(Quota.class));
    }

    @Test
    void currentReturnsActiveWhenPresent() {
        Subscription active = activeSub("PRO");
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.of(active));
        assertEquals(Optional.of(active), service.current(1L));
    }

    @Test
    void currentReturnsLatestWhenNoActive() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        Subscription latest = activeSub("TEAM");
        when(subscriptionRepository.findByStudioIdOrderByStartedAtDesc(1L)).thenReturn(List.of(latest));
        assertEquals(Optional.of(latest), service.current(1L));
    }

    @Test
    void currentReturnsEmptyWhenNoSubscription() {
        when(subscriptionRepository.findActiveByStudioId(anyLong(), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findByStudioIdOrderByStartedAtDesc(1L)).thenReturn(List.of());
        assertFalse(service.current(1L).isPresent());
    }

    @Test
    void expireOverdueDowngradesToFree() {
        Subscription due = activeSub("PRO");
        when(subscriptionRepository.findDue(any(LocalDateTime.class))).thenReturn(List.of(due));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));
        Studio studio = new Studio();
        studio.setId(1L);
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio));
        Quota quota = new Quota();
        quota.setStudioId(1L);
        when(quotaRepository.findByStudioId(1L)).thenReturn(Optional.of(quota));
        doNothing().when(reminderService)
                .create(anyLong(), any(), any(), any(ReminderType.class), any());

        service.expireOverdue();
        assertEquals("EXPIRED", due.getStatus());
        assertEquals("FREE", studio.getPlanType());
    }
}
