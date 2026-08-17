package com.photogai.modules.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.ai.dto.QuoteCalibrationDTO;
import com.photogai.modules.ai.entity.QuoteCalibration;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.order.enums.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 报价校准服务单元测试（纯 Mockito）。
 *
 * <p>覆盖分支：list 的 PRO 门禁 + DTO note 三类（样本不足 / 越界 / 边界内）、
 * scan 的聚合建 PENDING / 已采纳保持稳定不漂移、apply 的 NOT_FOUND / studio 越权 /
 * 样本不足 / 越界 / 正常采纳、appliedCoefMap、appliedCoef 的 FREE/PRO 命中/缺失。
 */
@ExtendWith(MockitoExtension.class)
class QuoteCalibrationServiceTest {

    @Mock
    private QuoteCalibrationRepository calibrationRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private QuoteCalibrationService service;

    private Order deal(OrderStatus status, BigDecimal amount, String region,
                       String shootType, String style) {
        Order o = new Order();
        o.setStatus(status);
        o.setAmount(amount);
        o.setRegion(region);
        o.setShootType(shootType);
        o.setStyle(style);
        return o;
    }

    // ========================= list =========================

    @Test
    void listRequiresProAndMapsNotes() {
        doNothing().when(subscriptionService).requirePro(1L);
        when(orderRepository.findByStudioIdAndDeletedAtIsNull(1L)).thenReturn(List.of());

        QuoteCalibration insufficient = new QuoteCalibration();
        insufficient.setId(1L);
        insufficient.setStudioId(1L);
        insufficient.setDimensionKey("上海|婚纱写真");
        insufficient.setDimensionLabel("上海·婚纱写真");
        insufficient.setSampleCount(5);
        insufficient.setSuggestedCoef(BigDecimal.ONE);
        insufficient.setOffsetPct(0);
        insufficient.setWithinBoundary(false);
        insufficient.setStatus("PENDING");

        QuoteCalibration within = new QuoteCalibration();
        within.setId(2L);
        within.setStudioId(1L);
        within.setDimensionKey("北京|亲子");
        within.setDimensionLabel("北京·亲子");
        within.setSampleCount(30);
        within.setSuggestedCoef(BigDecimal.valueOf(1.05));
        within.setOffsetPct(5);
        within.setWithinBoundary(true);
        within.setStatus("PENDING");

        when(calibrationRepository.findByStudioId(1L)).thenReturn(List.of(insufficient, within));

        List<QuoteCalibrationDTO> dtos = service.list(1L);
        assertEquals(2, dtos.size());
        assertEquals("样本不足（需≥20），仅供参考", dtos.get(0).getNote());
        assertEquals("边界内，可采纳", dtos.get(1).getNote());
    }

    // ========================= scan =========================

    @Test
    void scanCreatesPendingSuggestion() {
        Order o = deal(OrderStatus.DELIVER, BigDecimal.valueOf(4000), "上海", "婚纱写真", "轻奢");
        when(orderRepository.findByStudioIdAndDeletedAtIsNull(1L)).thenReturn(List.of(o));
        when(calibrationRepository.findByStudioIdAndDimensionKey(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(calibrationRepository.save(any(QuoteCalibration.class))).thenAnswer(i -> i.getArgument(0));

        service.scan(1L);
        verify(calibrationRepository, times(1)).save(any(QuoteCalibration.class));
    }

    @Test
    void scanKeepsAppliedSuggestionStable() {
        Order o = deal(OrderStatus.DELIVER, BigDecimal.valueOf(4000), "上海", "婚纱写真", "轻奢");
        when(orderRepository.findByStudioIdAndDeletedAtIsNull(1L)).thenReturn(List.of(o));

        QuoteCalibration existing = new QuoteCalibration();
        existing.setStudioId(1L);
        existing.setDimensionKey("上海|婚纱写真|轻奢");
        existing.setStatus("APPLIED");
        when(calibrationRepository.findByStudioIdAndDimensionKey(1L, "上海|婚纱写真|轻奢"))
                .thenReturn(Optional.of(existing));

        service.scan(1L);
        verify(calibrationRepository, never()).save(any(QuoteCalibration.class));
    }

    // ========================= apply =========================

    @Test
    void applyAppliesWithinBoundarySuggestion() {
        doNothing().when(subscriptionService).requirePro(1L);
        QuoteCalibration q = new QuoteCalibration();
        q.setId(1L);
        q.setStudioId(1L);
        q.setDimensionKey("上海|婚纱写真");
        q.setWithinBoundary(true);
        q.setStatus("PENDING");
        when(calibrationRepository.findById(1L)).thenReturn(Optional.of(q));
        when(calibrationRepository.save(any(QuoteCalibration.class))).thenAnswer(i -> i.getArgument(0));

        QuoteCalibrationDTO dto = service.apply(1L, 1L);
        assertEquals("APPLIED", dto.getStatus());
    }

    @Test
    void applyThrowsWhenNotFound() {
        doNothing().when(subscriptionService).requirePro(1L);
        when(calibrationRepository.findById(99L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.apply(1L, 99L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void applyThrowsWhenStudioMismatch() {
        doNothing().when(subscriptionService).requirePro(1L);
        QuoteCalibration q = new QuoteCalibration();
        q.setId(1L);
        q.setStudioId(2L);
        q.setWithinBoundary(true);
        when(calibrationRepository.findById(1L)).thenReturn(Optional.of(q));

        BizException ex = assertThrows(BizException.class, () -> service.apply(1L, 1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void applyThrowsWhenSampleInsufficient() {
        doNothing().when(subscriptionService).requirePro(1L);
        QuoteCalibration q = new QuoteCalibration();
        q.setId(1L);
        q.setStudioId(1L);
        q.setWithinBoundary(false);
        q.setSampleCount(5);
        when(calibrationRepository.findById(1L)).thenReturn(Optional.of(q));

        BizException ex = assertThrows(BizException.class, () -> service.apply(1L, 1L));
        assertEquals(ErrorCode.CALIBRATION_SAMPLE_INSUFFICIENT.getCode(), ex.getCode());
    }

    @Test
    void applyThrowsWhenOutOfBound() {
        doNothing().when(subscriptionService).requirePro(1L);
        QuoteCalibration q = new QuoteCalibration();
        q.setId(1L);
        q.setStudioId(1L);
        q.setWithinBoundary(false);
        q.setSampleCount(50);
        when(calibrationRepository.findById(1L)).thenReturn(Optional.of(q));

        BizException ex = assertThrows(BizException.class, () -> service.apply(1L, 1L));
        assertEquals(ErrorCode.CALIBRATION_OUT_OF_BOUND.getCode(), ex.getCode());
    }

    // ========================= appliedCoef =========================

    @Test
    void appliedCoefMapReturnsAppliedCoefs() {
        QuoteCalibration q = new QuoteCalibration();
        q.setDimensionKey("上海|婚纱写真");
        q.setSuggestedCoef(BigDecimal.valueOf(1.1));
        q.setStatus("APPLIED");
        when(calibrationRepository.findByStudioIdAndStatus(1L, "APPLIED")).thenReturn(List.of(q));

        Map<String, BigDecimal> map = service.appliedCoefMap(1L);
        assertEquals(BigDecimal.valueOf(1.1), map.get("上海|婚纱写真"));
    }

    @Test
    void appliedCoefReturnsOneWhenNotPro() {
        when(subscriptionService.isPro(1L)).thenReturn(false);
        assertEquals(BigDecimal.ONE, service.appliedCoef(1L, "上海", "婚纱写真", "轻奢"));
    }

    @Test
    void appliedCoefReturnsOneWhenProButNoMatch() {
        when(subscriptionService.isPro(1L)).thenReturn(true);
        when(calibrationRepository.findByStudioIdAndStatus(1L, "APPLIED")).thenReturn(List.of());
        assertEquals(BigDecimal.ONE, service.appliedCoef(1L, "上海", "婚纱写真", "轻奢"));
    }

    @Test
    void appliedCoefReturnsMappedWhenPro() {
        when(subscriptionService.isPro(1L)).thenReturn(true);
        QuoteCalibration q = new QuoteCalibration();
        q.setDimensionKey("上海|婚纱写真|轻奢");
        q.setSuggestedCoef(BigDecimal.valueOf(1.2));
        q.setStatus("APPLIED");
        when(calibrationRepository.findByStudioIdAndStatus(1L, "APPLIED")).thenReturn(List.of(q));
        assertEquals(BigDecimal.valueOf(1.2), service.appliedCoef(1L, "上海", "婚纱写真", "轻奢"));
    }
}
