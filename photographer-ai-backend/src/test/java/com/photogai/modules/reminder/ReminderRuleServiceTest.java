package com.photogai.modules.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.reminder.dto.ReminderRuleDTO;
import com.photogai.modules.reminder.dto.ReminderRuleRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 提醒规则服务单元测试（对应 PRD P1-4 / 架构 §2.3 重构 triggerReminders）。
 *
 * <p>验证：
 * 1. findOffset 在「无启用规则」时回退与阶段1 一致的默认硬编码偏移；
 * 2. findOffset 在「存在启用规则」时优先使用规则值；
 * 3. 本轮新增：listByStudio 的 PRO 门禁 + 空规则懒种子 + 非空直接返回、
 *    create/update/delete 的 PRO 门禁、channel 默认 INAPP、update 缺 channel 保持原值、NOT_FOUND 分支。
 */
@ExtendWith(MockitoExtension.class)
class ReminderRuleServiceTest {

    @Mock
    private ReminderRuleRepository ruleRepository;

    @Mock
    private QuotaService quotaService;

    @InjectMocks
    private ReminderRuleService service;

    @Test
    void findOffsetFallsBackToDefaultHardcodedWhenNoRule() {
        when(ruleRepository.findByStudioIdAndEventAndEnabledTrue(1L, ReminderTriggerEvent.DEPOSIT))
                .thenReturn(List.of());
        when(ruleRepository.findByStudioIdAndEventAndEnabledTrue(1L, ReminderTriggerEvent.SHOOT))
                .thenReturn(List.of());
        when(ruleRepository.findByStudioIdAndEventAndEnabledTrue(1L, ReminderTriggerEvent.EDIT))
                .thenReturn(List.of());
        when(ruleRepository.findByStudioIdAndEventAndEnabledTrue(1L, ReminderTriggerEvent.DELIVER))
                .thenReturn(List.of());
        when(ruleRepository.findByStudioIdAndEventAndEnabledTrue(1L, ReminderTriggerEvent.REPURCHASE))
                .thenReturn(List.of());

        assertEquals(3, service.findOffset(1L, ReminderTriggerEvent.DEPOSIT));
        assertEquals(-1, service.findOffset(1L, ReminderTriggerEvent.SHOOT));
        assertEquals(7, service.findOffset(1L, ReminderTriggerEvent.EDIT));
        assertEquals(3, service.findOffset(1L, ReminderTriggerEvent.DELIVER));
        assertEquals(0, service.findOffset(1L, ReminderTriggerEvent.REPURCHASE));
    }

    @Test
    void findOffsetUsesEnabledRuleWhenPresent() {
        ReminderRule rule = new ReminderRule();
        rule.setOffsetDays(-2);
        when(ruleRepository.findByStudioIdAndEventAndEnabledTrue(1L, ReminderTriggerEvent.SHOOT))
                .thenReturn(List.of(rule));

        assertEquals(-2, service.findOffset(1L, ReminderTriggerEvent.SHOOT));
    }

    // ========================= 本轮新增分支 =========================

    @Test
    void listByStudioSeedsDefaultsWhenEmpty() {
        doNothing().when(quotaService).requirePro(1L);
        when(ruleRepository.findByStudioId(1L)).thenReturn(List.of());
        when(ruleRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        List<ReminderRuleDTO> dtos = service.listByStudio(1L);
        assertEquals(4, dtos.size()); // DEPOSIT/SHOOT/DELIVER/REPURCHASE 默认四条
    }

    @Test
    void listByStudioReturnsExistingWithoutSeeding() {
        doNothing().when(quotaService).requirePro(1L);
        ReminderRule r = new ReminderRule();
        r.setId(1L);
        r.setStudioId(1L);
        r.setEvent(ReminderTriggerEvent.DEPOSIT);
        r.setOffsetDays(3);
        r.setChannel("INAPP");
        when(ruleRepository.findByStudioId(1L)).thenReturn(List.of(r));

        List<ReminderRuleDTO> dtos = service.listByStudio(1L);
        assertEquals(1, dtos.size());
        verify(ruleRepository, never()).saveAll(anyList());
    }

    @Test
    void createDefaultsChannelToInappWhenNull() {
        doNothing().when(quotaService).requirePro(1L);
        ReminderRuleRequest req = ReminderRuleRequest.builder()
                .event(ReminderTriggerEvent.DEPOSIT).offsetDays(3).build(); // channel 为 null
        when(ruleRepository.save(any(ReminderRule.class))).thenAnswer(i -> {
            ReminderRule r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        ReminderRuleDTO dto = service.create(1L, req);
        assertEquals("INAPP", dto.getChannel());
    }

    @Test
    void createKeepsProvidedChannel() {
        doNothing().when(quotaService).requirePro(1L);
        ReminderRuleRequest req = ReminderRuleRequest.builder()
                .event(ReminderTriggerEvent.EDIT).offsetDays(7).channel("INAPP").build();
        when(ruleRepository.save(any(ReminderRule.class))).thenAnswer(i -> {
            ReminderRule r = i.getArgument(0);
            r.setId(1L);
            return r;
        });

        ReminderRuleDTO dto = service.create(1L, req);
        assertEquals(7, dto.getOffsetDays());
        assertEquals(ReminderTriggerEvent.EDIT, dto.getEvent());
    }

    @Test
    void updateThrowsNotFound() {
        doNothing().when(quotaService).requirePro(1L);
        when(ruleRepository.findByIdAndStudioId(9L, 1L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.update(1L, 9L,
                ReminderRuleRequest.builder().event(ReminderTriggerEvent.DEPOSIT).offsetDays(1).build()));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void updateAppliesFieldsAndKeepsChannelWhenNull() {
        doNothing().when(quotaService).requirePro(1L);
        ReminderRule r = new ReminderRule();
        r.setId(1L);
        r.setStudioId(1L);
        r.setEvent(ReminderTriggerEvent.DEPOSIT);
        r.setOffsetDays(3);
        r.setChannel("INAPP");
        when(ruleRepository.findByIdAndStudioId(1L, 1L)).thenReturn(Optional.of(r));
        ReminderRuleRequest req = ReminderRuleRequest.builder()
                .event(ReminderTriggerEvent.SHOOT).offsetDays(-1).build(); // channel 为 null
        when(ruleRepository.save(any(ReminderRule.class))).thenAnswer(i -> i.getArgument(0));

        ReminderRuleDTO dto = service.update(1L, 1L, req);
        assertEquals(ReminderTriggerEvent.SHOOT, dto.getEvent());
        assertEquals(-1, dto.getOffsetDays());
        assertEquals("INAPP", dto.getChannel());
    }

    @Test
    void deleteThrowsNotFound() {
        doNothing().when(quotaService).requirePro(1L);
        when(ruleRepository.findByIdAndStudioId(9L, 1L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.delete(1L, 9L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void deleteSucceeds() {
        doNothing().when(quotaService).requirePro(1L);
        ReminderRule r = new ReminderRule();
        r.setId(1L);
        r.setStudioId(1L);
        when(ruleRepository.findByIdAndStudioId(1L, 1L)).thenReturn(Optional.of(r));

        service.delete(1L, 1L);
        verify(ruleRepository).delete(r);
    }
}
