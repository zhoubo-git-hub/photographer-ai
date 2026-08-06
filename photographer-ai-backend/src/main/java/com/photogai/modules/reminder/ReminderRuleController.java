package com.photogai.modules.reminder;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.reminder.dto.ReminderRuleDTO;
import com.photogai.modules.reminder.dto.ReminderRuleRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提醒规则接口（PRO）。列表/增/改/删。
 */
@RestController
@RequestMapping("/api/reminder-rules")
@RequiredArgsConstructor
public class ReminderRuleController {

    private final ReminderRuleService reminderRuleService;

    @GetMapping
    public Result<List<ReminderRuleDTO>> list() {
        return Result.ok(reminderRuleService.listByStudio(CurrentUser.getStudioId()));
    }

    @PostMapping
    public Result<ReminderRuleDTO> create(@Valid @RequestBody ReminderRuleRequest req) {
        return Result.ok(reminderRuleService.create(CurrentUser.getStudioId(), req));
    }

    @PutMapping("/{id}")
    public Result<ReminderRuleDTO> update(
            @PathVariable Long id, @Valid @RequestBody ReminderRuleRequest req) {
        return Result.ok(reminderRuleService.update(CurrentUser.getStudioId(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reminderRuleService.delete(CurrentUser.getStudioId(), id);
        return Result.ok();
    }
}
