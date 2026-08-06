package com.photogai.modules.team;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.team.dto.AcceptInvitationRequest;
import com.photogai.modules.team.dto.TeamInviteRequest;
import com.photogai.modules.team.dto.TeamMemberDTO;
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
 * 团队接口：邀请 / 成员列表 / 改角色 / 移除 / 接受邀请。
 *
 * <p>B6 订单分配见 {@code OrderController#assign}（requireTeam + 非只读）。
 */
@RestController
@RequestMapping("/api/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    /** B1 邀请成员。 */
    @PostMapping("/invite")
    public Result<TeamMemberDTO> invite(@RequestBody TeamInviteRequest req) {
        return Result.ok(teamService.invite(
                CurrentUser.getStudioId(), CurrentUser.getUserId(), CurrentUser.getRole(), req));
    }

    /** B2 成员列表。 */
    @GetMapping("/members")
    public Result<List<TeamMemberDTO>> members() {
        return Result.ok(teamService.members(CurrentUser.getStudioId()));
    }

    /** B3 修改成员角色。 */
    @PutMapping("/members/{id}")
    public Result<TeamMemberDTO> updateRole(@PathVariable Long id,
                                            @RequestBody java.util.Map<String, String> body) {
        String newRole = body == null ? null : body.get("role");
        return Result.ok(teamService.updateRole(
                CurrentUser.getStudioId(), CurrentUser.getRole(), id, newRole));
    }

    /** B4 移除成员。 */
    @DeleteMapping("/members/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        teamService.removeMember(CurrentUser.getStudioId(), CurrentUser.getRole(), id);
        return Result.ok();
    }

    /** B5 接受邀请（匿名可访问，凭 token 建用户并登录）。 */
    @PostMapping("/accept")
    public Result<com.photogai.modules.auth.dto.AuthResponse> accept(
            @RequestBody AcceptInvitationRequest req) {
        return Result.ok(teamService.accept(req));
    }
}
