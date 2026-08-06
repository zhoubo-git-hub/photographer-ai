package com.photogai.modules.reminder;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.reminder.dto.ReminderRuleDTO;
import com.photogai.modules.reminder.dto.ReminderRuleRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提醒规则服务：CRUD（PRO 门禁）+ 懒种子默认规则 + 偏移计算。
 *
 * <p>阶段1 硬编码的偏移（定金+3 / 拍摄前-1 / 修图+7 / 交付+3）作为默认兜底，
 * FREE 或 PRO 无启用规则时回退到该默认值，保证阶段1 行为不回归。
 */
@Service
@RequiredArgsConstructor
public class ReminderRuleService {

    private final ReminderRuleRepository ruleRepository;
    private final QuotaService quotaService;

    /** 默认偏移天数（负=提前）。与阶段1 硬编码完全一致。 */
    private static final Map<ReminderTriggerEvent, Integer> DEFAULT_OFFSETS = Map.of(
            ReminderTriggerEvent.DEPOSIT, 3,
            ReminderTriggerEvent.SHOOT, -1,
            ReminderTriggerEvent.EDIT, 7,
            ReminderTriggerEvent.DELIVER, 3,
            ReminderTriggerEvent.REPURCHASE, 0);

    @Transactional
    public List<ReminderRuleDTO> listByStudio(Long studioId) {
        quotaService.requirePro(studioId); // E2 PRO 门禁
        List<ReminderRule> rules = ruleRepository.findByStudioId(studioId);
        if (rules.isEmpty()) {
            rules = seedDefaults(studioId);
        }
        return rules.stream().map(ReminderRuleDTO::from).collect(Collectors.toList());
    }

    /** PRO 首次访问且无规则时种入默认规则（与阶段1 行为一致）。 */
    private List<ReminderRule> seedDefaults(Long studioId) {
        return ruleRepository.saveAll(List.of(
                build(studioId, ReminderTriggerEvent.DEPOSIT, 3),
                build(studioId, ReminderTriggerEvent.SHOOT, -1),
                build(studioId, ReminderTriggerEvent.DELIVER, 3),
                build(studioId, ReminderTriggerEvent.REPURCHASE, 0)));
    }

    /**
     * 计算某事件的偏移天数：PRO 存在启用规则则用规则值，否则回退默认硬编码。
     * 该方法不抛异常，供 {@code OrderService} 在每次状态流转时调用。
     */
    @Transactional(readOnly = true)
    public int findOffset(Long studioId, ReminderTriggerEvent event) {
        return ruleRepository.findByStudioIdAndEventAndEnabledTrue(studioId, event).stream()
                .findFirst()
                .map(ReminderRule::getOffsetDays)
                .orElse(DEFAULT_OFFSETS.getOrDefault(event, 0));
    }

    @Transactional
    public ReminderRuleDTO create(Long studioId, ReminderRuleRequest req) {
        quotaService.requirePro(studioId);
        ReminderRule rule = new ReminderRule();
        rule.setStudioId(studioId);
        rule.setEvent(req.getEvent());
        rule.setOffsetDays(req.getOffsetDays());
        rule.setEnabled(req.isEnabled());
        rule.setChannel(req.getChannel() == null ? "INAPP" : req.getChannel());
        return ReminderRuleDTO.from(ruleRepository.save(rule));
    }

    @Transactional
    public ReminderRuleDTO update(Long studioId, Long id, ReminderRuleRequest req) {
        quotaService.requirePro(studioId);
        ReminderRule rule = ruleRepository.findByIdAndStudioId(id, studioId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "提醒规则不存在"));
        rule.setEvent(req.getEvent());
        rule.setOffsetDays(req.getOffsetDays());
        rule.setEnabled(req.isEnabled());
        if (req.getChannel() != null) {
            rule.setChannel(req.getChannel());
        }
        return ReminderRuleDTO.from(ruleRepository.save(rule));
    }

    @Transactional
    public void delete(Long studioId, Long id) {
        quotaService.requirePro(studioId);
        ReminderRule rule = ruleRepository.findByIdAndStudioId(id, studioId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "提醒规则不存在"));
        ruleRepository.delete(rule);
    }

    private ReminderRule build(Long studioId, ReminderTriggerEvent event, int offsetDays) {
        ReminderRule rule = new ReminderRule();
        rule.setStudioId(studioId);
        rule.setEvent(event);
        rule.setOffsetDays(offsetDays);
        rule.setEnabled(true);
        rule.setChannel("INAPP");
        return rule;
    }
}
