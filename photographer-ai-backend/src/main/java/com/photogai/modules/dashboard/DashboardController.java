package com.photogai.modules.dashboard;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.dashboard.dto.FunnelDTO;
import com.photogai.modules.dashboard.dto.MemberPerfDTO;
import com.photogai.modules.dashboard.dto.OverviewDTO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经营看板接口：概览 / 转化漏斗 / 成员业绩。
 *
 * <p>纯聚合 {@code orders}/{@code status_history}/{@code customer}，零埋点；
 * 成员维度接口 {@code requireTeam}（仅团队版）。
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** C1 概览（收入/订单/客单价/复购/趋势）。 */
    @GetMapping("/overview")
    public Result<OverviewDTO> overview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(dashboardService.overview(
                CurrentUser.getStudioId(), parse(from), parse(to)));
    }

    /** C2 转化漏斗（咨询→定金→拍摄→修图→交付）。 */
    @GetMapping("/funnel")
    public Result<FunnelDTO> funnel(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(dashboardService.funnel(
                CurrentUser.getStudioId(), parse(from), parse(to)));
    }

    /** C3 成员业绩拆分（团队版；按 assigned_to）。 */
    @GetMapping("/members")
    public Result<List<MemberPerfDTO>> members() {
        return Result.ok(dashboardService.members(CurrentUser.getStudioId()));
    }

    /** 解析时间窗参数：优先 ISO 日期时间，回退 ISO 日期（当天 00:00）。 */
    private LocalDateTime parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
    }
}
