package com.photogai.modules.reminder.dto;

import com.photogai.modules.reminder.ReminderTriggerEvent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提醒规则增改请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderRuleRequest {

    @NotNull(message = "触发事件不能为空")
    private ReminderTriggerEvent event;

    private int offsetDays;

    private boolean enabled = true;

    private String channel;
}
