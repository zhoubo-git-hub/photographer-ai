package com.photogai.modules.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.photogai.modules.quota.QuotaService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 提醒规则服务单元测试（对应 PRD P1-4 / 架构 §2.3 重构 triggerReminders）。
 *
 * <p>验证：
 * 1. findOffset 在「无启用规则」时回退与阶段1 一致的默认硬编码偏移
 *    （DEPOSIT 3 / SHOOT -1 / EDIT 7 / DELIVER 3 / REPURCHASE 0）；
 * 2. findOffset 在「存在启用规则」时优先使用规则值。
 *
 * <p>注：本测试引用 ReminderTriggerEvent.EDIT，依赖后端补全该枚举常量
 * （见测试报告「已知问题」——当前 ReminderTriggerEvent 枚举缺失 EDIT 导致模块无法编译）。
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
}
