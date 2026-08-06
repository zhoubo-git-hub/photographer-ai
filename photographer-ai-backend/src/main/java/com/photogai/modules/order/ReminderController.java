package com.photogai.modules.order;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.order.dto.ReminderDTO;
import com.photogai.modules.order.enums.ReminderStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提醒接口（P0 仅站内）。列表与标记。
 */
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping
    public Result<List<ReminderDTO>> list(
            @RequestParam(required = false) ReminderStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean dueOnly) {
        Long studioId = CurrentUser.getStudioId();
        if (dueOnly) {
            return Result.ok(reminderService.listDueOnly(studioId));
        }
        return Result.ok(reminderService.listByStudioAndStatus(studioId, status));
    }

    @PutMapping("/{id}")
    public Result<ReminderDTO> updateStatus(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestParam ReminderStatus status) {
        return Result.ok(reminderService.updateStatus(CurrentUser.getStudioId(), id, status));
    }
}
