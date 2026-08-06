package com.photogai.modules.schedule;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.schedule.dto.ScheduleDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档期日历接口。
 */
@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/month")
    public Result<List<ScheduleDTO>> month(
            @RequestParam int year,
            @RequestParam int month) {
        return Result.ok(scheduleService.month(CurrentUser.getStudioId(), year, month));
    }
}
