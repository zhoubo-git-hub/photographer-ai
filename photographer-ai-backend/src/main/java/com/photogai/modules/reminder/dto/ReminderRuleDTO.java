package com.photogai.modules.reminder.dto;

import com.photogai.modules.reminder.ReminderRule;
import com.photogai.modules.reminder.ReminderTriggerEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提醒规则视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderRuleDTO {

    private Long id;
    private Long studioId;
    private ReminderTriggerEvent event;
    private int offsetDays;
    private boolean enabled;
    private String channel;

    public static ReminderRuleDTO from(ReminderRule r) {
        if (r == null) {
            return null;
        }
        return ReminderRuleDTO.builder()
                .id(r.getId())
                .studioId(r.getStudioId())
                .event(r.getEvent())
                .offsetDays(r.getOffsetDays())
                .enabled(r.isEnabled())
                .channel(r.getChannel())
                .build();
    }
}
