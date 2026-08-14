package com.photogai.modules.quota;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.quota.entity.Quota;
import com.photogai.modules.studio.StudioRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 免费额度服务单元测试（对应 PRD P0-7 / 架构免费额度）。
 *
 * <p>验证：免费版在管订单 ≤10（第 11 单被拦截）、AI 报价 5 次/月（第 6 次被拦截），
 * 专业版（PRO）无限。源码 {@code QuotaService.getOrInit} 已采用 {@code new Quota()}+setter
 * 构造（无 {@code @Builder} 依赖），与本项目 {@code Quota} 实体一致，可直接编译运行。
 */
@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock
    private QuotaRepository quotaRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StudioRepository studioRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private QuotaService service;

    private Quota freeQuota(int aiUsed) {
        Quota q = new Quota();
        q.setStudioId(1L);
        q.setPlanType("FREE");
        q.setOrderCount(0);
        q.setAiQuoteUsedMonth(aiUsed);
        q.setQuotaMonth(YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        return q;
    }

    @Test
    void freeOrderLimitBlocksEleventhOrder() {
        Quota q = freeQuota(0);
        q.setOrderCount(10);
        when(quotaRepository.findByStudioId(1L)).thenReturn(Optional.of(q));
        when(orderRepository.countByStudioIdAndDeletedAtIsNull(1L)).thenReturn(10L);
        when(subscriptionService.isPro(1L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.ensureWithinLimit(1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void freeOrderLimitAllowsTenthOrder() {
        Quota q = freeQuota(0);
        q.setOrderCount(9);
        when(quotaRepository.findByStudioId(1L)).thenReturn(Optional.of(q));
        when(orderRepository.countByStudioIdAndDeletedAtIsNull(1L)).thenReturn(9L);
        when(subscriptionService.isPro(1L)).thenReturn(false);

        assertDoesNotThrow(() -> service.ensureWithinLimit(1L));
    }

    @Test
    void freeAiQuoteLimitBlocksSixth() {
        Quota q = freeQuota(5);
        when(quotaRepository.findByStudioId(1L)).thenReturn(Optional.of(q));
        when(subscriptionService.isPro(1L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> service.checkAiQuoteLimit(1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void freeAiQuoteLimitAllowsFifth() {
        Quota q = freeQuota(4);
        when(quotaRepository.findByStudioId(1L)).thenReturn(Optional.of(q));
        when(subscriptionService.isPro(1L)).thenReturn(false);
        assertDoesNotThrow(() -> service.checkAiQuoteLimit(1L));
    }

    @Test
    void proPlanNeverThrows() {
        Quota q = freeQuota(999);
        q.setPlanType("PRO");
        q.setOrderCount(999);
        when(quotaRepository.findByStudioId(1L)).thenReturn(Optional.of(q));
        when(orderRepository.countByStudioIdAndDeletedAtIsNull(1L)).thenReturn(999L);
        when(subscriptionService.isPro(1L)).thenReturn(true);

        assertDoesNotThrow(() -> service.ensureWithinLimit(1L));
        assertDoesNotThrow(() -> service.checkAiQuoteLimit(1L));
        assertEquals(QuotaService.UNLIMITED, service.getRemainingAiQuota(1L));
    }

    @Test
    void incrementAiQuoteUsedIncrementsWithinSameMonth() {
        Quota q = freeQuota(0);
        when(quotaRepository.findByStudioId(1L)).thenReturn(Optional.of(q));

        service.incrementAiQuoteUsed(1L);
        assertEquals(1, q.getAiQuoteUsedMonth());
    }
}
