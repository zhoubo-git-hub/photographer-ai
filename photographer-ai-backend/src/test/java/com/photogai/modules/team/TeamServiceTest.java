package com.photogai.modules.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.config.JwtUtil;
import com.photogai.exception.BizException;
import com.photogai.modules.auth.UserRepository;
import com.photogai.modules.auth.UserService;
import com.photogai.modules.auth.entity.User;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.entity.Studio;
import com.photogai.modules.team.dto.TeamInviteRequest;
import com.photogai.modules.team.dto.TeamMemberDTO;
import com.photogai.modules.team.entity.TeamInvitation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 团队服务单元测试（Mockito，不连 PG）。
 *
 * <p>覆盖邀请/成员列表/改角色/移除主路径 + 角色非法/成员上限/越权异常路径。
 */
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamInvitationRepository invitationRepository;
    @Mock
    private StudioRepository studioRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserService userService;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private TeamService teamService;

    /** B1 邀请：正常创建待接受邀请。 */
    @Test
    void inviteCreatesInvitation() {
        TeamInviteRequest req = TeamInviteRequest.builder()
                .email("new@x.com").role("MEMBER").build();
        when(userRepository.countByStudioId(1L)).thenReturn(1L);
        when(invitationRepository.save(any(TeamInvitation.class))).thenAnswer(i -> {
            TeamInvitation inv = i.getArgument(0);
            inv.setId(10L);
            return inv;
        });

        TeamMemberDTO dto = teamService.invite(1L, 1L, "OWNER", req);
        assertEquals(10L, dto.getId());
        assertEquals("MEMBER", dto.getRole());
    }

    /** B1 邀请：成员已达上限抛 MEMBER_LIMIT_EXCEEDED(403)。 */
    @Test
    void inviteThrowsWhenMemberLimitExceeded() {
        TeamInviteRequest req = TeamInviteRequest.builder()
                .email("x@x.com").role("MEMBER").build();
        when(userRepository.countByStudioId(1L)).thenReturn(5L);

        BizException ex = assertThrows(BizException.class,
                () -> teamService.invite(1L, 1L, "OWNER", req));
        assertEquals(ErrorCode.MEMBER_LIMIT_EXCEEDED.getCode(), ex.getCode());
    }

    /** B1 邀请：非法角色抛 VALIDATION(400)。 */
    @Test
    void inviteThrowsValidationForInvalidRole() {
        TeamInviteRequest req = TeamInviteRequest.builder()
                .email("x@x.com").role("OWNER").build();

        BizException ex = assertThrows(BizException.class,
                () -> teamService.invite(1L, 1L, "OWNER", req));
        assertEquals(ErrorCode.VALIDATION.getCode(), ex.getCode());
    }

    /** B2 成员列表：按 studio 聚合成员及订单数。 */
    @Test
    void membersReturnsList() {
        User u1 = new User();
        u1.setId(2L);
        u1.setStudioId(1L);
        u1.setUsername("alice");
        u1.setRole("MEMBER");
        User u2 = new User();
        u2.setId(3L);
        u2.setStudioId(1L);
        u2.setUsername("bob");
        u2.setRole("ADMIN");
        when(userRepository.findByStudioId(1L)).thenReturn(List.of(u1, u2));
        when(orderRepository.countByStudioIdAndAssignedToAndDeletedAtIsNull(anyLong(), anyLong()))
                .thenReturn(0L);

        List<TeamMemberDTO> list = teamService.members(1L);
        assertEquals(2, list.size());
    }

    /** B3 改角色：正常更新成员角色。 */
    @Test
    void updateRoleReturnsUpdatedMember() {
        User member = new User();
        member.setId(5L);
        member.setStudioId(1L);
        member.setUsername("alice");
        member.setRole("MEMBER");
        Studio studio = new Studio();
        studio.setId(1L);
        studio.setOwnerUserId(1L);

        when(userRepository.findByStudioIdAndId(1L, 5L)).thenReturn(Optional.of(member));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio));
        when(userRepository.save(any(User.class))).thenReturn(member);

        TeamMemberDTO dto = teamService.updateRole(1L, "OWNER", 5L, "ADMIN");
        assertEquals("ADMIN", dto.getRole());
    }

    /** B3 改角色：不可通过此接口提权为 OWNER，抛 FORBIDDEN(403)。 */
    @Test
    void updateRoleThrowsForbiddenWhenPromotingToOwner() {
        BizException ex = assertThrows(BizException.class,
                () -> teamService.updateRole(1L, "OWNER", 5L, "OWNER"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /** B4 移除成员：正常移除并回退名下订单。 */
    @Test
    void removeMemberSucceeds() {
        User member = new User();
        member.setId(5L);
        member.setStudioId(1L);
        member.setUsername("alice");
        member.setRole("MEMBER");
        Studio studio = new Studio();
        studio.setId(1L);
        studio.setOwnerUserId(1L);

        when(userRepository.findByStudioIdAndId(1L, 5L)).thenReturn(Optional.of(member));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio));
        when(orderRepository.findByStudioIdAndAssignedToAndDeletedAtIsNull(1L, 5L))
                .thenReturn(List.of());

        teamService.removeMember(1L, "OWNER", 5L);
    }
}
