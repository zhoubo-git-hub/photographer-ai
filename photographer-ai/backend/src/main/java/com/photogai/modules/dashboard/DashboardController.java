package com.photogai.modules.dashboard;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.dashboard.dto.FunnelDTO;
import com.photogai.modules.dashboard.dto.MemberPerfDTO;
import com.photogai.modules.dashboard.dto.OverviewDTO;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经营看板接口（纯聚合，零埋点）。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** C1 概览。 */
    @GetMapping("/overview")
    public Result<OverviewDTO> overview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(dashboardService.overview(
                CurrentUser.getStudioId(), parse(from), parse(to)));
    }

    /** C2 漏斗。 */
    @GetMapping("/funnel")
    public Result<FunnelDTO> funnel(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(dashboardService.funnel(
                CurrentUser.getStudioId(), parse(from), parse(to)));
    }

    /** C3 成员业绩（团队版）。 */
    @GetMapping("/members")
    public Result<List<MemberPerfDTO>> members() {
        return Result.ok(dashboardService.members(CurrentUser.getStudioId()));
    }

    private LocalDateTime parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
