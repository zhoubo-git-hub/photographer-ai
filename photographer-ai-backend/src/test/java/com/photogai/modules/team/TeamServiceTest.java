package com.photogai.modules.team;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.photogai.modules.auth.dto.AuthResponse;
import com.photogai.modules.order.entity.Order;
import com.photogai.modules.team.dto.AcceptInvitationRequest;
import com.photogai.modules.team.dto.TeamInviteRequest;
import com.photogai.modules.team.dto.TeamMemberDTO;
import com.photogai.modules.team.entity.TeamInvitation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    // ===================================================================
    // 本轮新增：invite phone 分支、updateRole / removeMember 剩余分支、accept 全路径（B5）
    // ===================================================================

    private User member(Long id, String username, String role) {
        User u = new User();
        u.setId(id);
        u.setStudioId(1L);
        u.setUsername(username);
        u.setRole(role);
        return u;
    }

    private Studio studio(Long id, Long ownerUserId) {
        Studio s = new Studio();
        s.setId(id);
        s.setName("studio" + id);
        s.setOwnerUserId(ownerUserId);
        return s;
    }

    private TeamInvitation invitation(String token, String status, LocalDateTime expiresAt) {
        TeamInvitation inv = new TeamInvitation();
        inv.setId(100L);
        inv.setStudioId(1L);
        inv.setInviterId(1L);
        inv.setEmail("new@x.com");
        inv.setRole("MEMBER");
        inv.setToken(token);
        inv.setStatus(status);
        inv.setExpiresAt(expiresAt);
        return inv;
    }

    // ---------- B1 invite ----------

    /** 仅提供 phone → 覆盖 {@code req.getEmail() != null} 的 false 分支，username 取 phone。 */
    @Test
    void inviteUsesPhoneAsUsernameWhenEmailAbsent() {
        TeamInviteRequest req = TeamInviteRequest.builder()
                .phone("13900001111").role("READONLY").build();
        when(userRepository.countByStudioId(1L)).thenReturn(2L);
        when(invitationRepository.save(any(TeamInvitation.class))).thenAnswer(i -> {
            TeamInvitation inv = i.getArgument(0);
            inv.setId(11L);
            return inv;
        });

        TeamMemberDTO dto = teamService.invite(1L, 1L, "ADMIN", req);

        assertEquals("13900001111", dto.getUsername());
        assertEquals("READONLY", dto.getRole());
        assertEquals(11L, dto.getInvitationId());
        assertEquals(0, dto.getOrderCount());
        assertNotNull(dto.getToken());
    }

    /** 非 OWNER/ADMIN 发起邀请 → RoleGuard 抛 TEAM_REQUIRED。 */
    @Test
    void inviteThrowsTeamRequiredForNonManagerRole() {
        TeamInviteRequest req = TeamInviteRequest.builder()
                .email("x@x.com").role("MEMBER").build();

        BizException ex = assertThrows(BizException.class,
                () -> teamService.invite(1L, 1L, "MEMBER", req));
        assertEquals(ErrorCode.TEAM_REQUIRED.getCode(), ex.getCode());
        verify(invitationRepository, never()).save(any(TeamInvitation.class));
    }

    // ---------- B3 updateRole ----------

    /** 非法角色（不在 ADMIN/MEMBER/READONLY 内且非 OWNER）→ VALIDATION("角色非法")。 */
    @Test
    void updateRoleThrowsValidationForUnknownRole() {
        BizException ex = assertThrows(BizException.class,
                () -> teamService.updateRole(1L, "OWNER", 5L, "GUEST"));
        assertEquals(ErrorCode.VALIDATION.getCode(), ex.getCode());
        assertEquals("角色非法", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    /** 目标成员是工作室所有者 → FORBIDDEN("不能修改所有者的角色")。 */
    @Test
    void updateRoleThrowsForbiddenWhenTargetIsOwner() {
        when(userRepository.findByStudioIdAndId(1L, 1L)).thenReturn(Optional.of(member(1L, "boss", "OWNER")));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio(1L, 1L)));

        BizException ex = assertThrows(BizException.class,
                () -> teamService.updateRole(1L, "OWNER", 1L, "MEMBER"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("不能修改所有者的角色", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    /** 工作室记录缺失 → orElseThrow → NOT_FOUND("工作室不存在")。 */
    @Test
    void updateRoleThrowsNotFoundWhenStudioMissing() {
        when(userRepository.findByStudioIdAndId(1L, 5L)).thenReturn(Optional.of(member(5L, "alice", "MEMBER")));
        when(studioRepository.findById(1L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> teamService.updateRole(1L, "OWNER", 5L, "ADMIN"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("工作室不存在", ex.getMessage());
    }

    /** 成员不存在 → requireMember 抛 NOT_FOUND("成员不存在")。 */
    @Test
    void updateRoleThrowsNotFoundWhenMemberMissing() {
        when(userRepository.findByStudioIdAndId(1L, 404L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> teamService.updateRole(1L, "OWNER", 404L, "ADMIN"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("成员不存在", ex.getMessage());
    }

    // ---------- B4 removeMember ----------

    /** 目标是工作室所有者 → FORBIDDEN("不能移除工作室所有者")。 */
    @Test
    void removeMemberThrowsForbiddenWhenTargetIsOwner() {
        when(userRepository.findByStudioIdAndId(1L, 1L)).thenReturn(Optional.of(member(1L, "boss", "OWNER")));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio(1L, 1L)));

        BizException ex = assertThrows(BizException.class,
                () -> teamService.removeMember(1L, "OWNER", 1L));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("不能移除工作室所有者", ex.getMessage());
        verify(userRepository, never()).delete(any(User.class));
    }

    /** 工作室记录缺失 → NOT_FOUND。 */
    @Test
    void removeMemberThrowsNotFoundWhenStudioMissing() {
        when(userRepository.findByStudioIdAndId(1L, 5L)).thenReturn(Optional.of(member(5L, "alice", "MEMBER")));
        when(studioRepository.findById(1L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> teamService.removeMember(1L, "OWNER", 5L));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    /** 名下有订单 → forEach 把 assignedTo 回退为 null 并逐条 save，再删除用户。 */
    @Test
    void removeMemberReassignsOwnedOrdersToUnassigned() {
        User target = member(5L, "alice", "MEMBER");
        when(userRepository.findByStudioIdAndId(1L, 5L)).thenReturn(Optional.of(target));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio(1L, 1L)));
        Order o = new Order();
        o.setId(21L);
        o.setStudioId(1L);
        o.setCustomerId(9L);
        o.setTitle("婚纱");
        o.setAssignedTo(5L);
        when(orderRepository.findByStudioIdAndAssignedToAndDeletedAtIsNull(1L, 5L))
                .thenReturn(List.of(o));

        assertDoesNotThrow(() -> teamService.removeMember(1L, "OWNER", 5L));

        assertNull(o.getAssignedTo());
        verify(orderRepository).save(o);
        verify(userRepository).delete(target);
    }

    // ---------- B5 accept（此前完全未覆盖） ----------

    /** accept 成功路径：建用户 + 邀请置 ACCEPTED + 发 token。 */
    @Test
    void acceptCreatesUserAndReturnsAuthResponse() {
        TeamInvitation inv = invitation("tok", "PENDING", LocalDateTime.now().plusDays(3));
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(inv));
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userService.encodePassword("pw")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(7L);
            return u;
        });
        when(invitationRepository.save(any(TeamInvitation.class))).thenAnswer(i -> i.getArgument(0));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio(1L, 1L)));
        when(jwtUtil.generateToken(7L, 1L, "newuser", "MEMBER")).thenReturn("jwt-tok");

        AuthResponse resp = teamService.accept(
                AcceptInvitationRequest.builder().token("tok").username("newuser").password("pw").build());

        assertNotNull(resp);
        assertEquals("jwt-tok", resp.getToken());
        assertEquals(7L, resp.getUser().getId());
        assertEquals("newuser", resp.getUser().getUsername());
        assertEquals("MEMBER", resp.getUser().getRole());
        assertEquals(1L, resp.getStudio().getId());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertEquals("hash", created.getPasswordHash());
        assertEquals("new@x.com", created.getEmail());
        assertEquals(1L, created.getStudioId());

        assertEquals("ACCEPTED", inv.getStatus());
        assertEquals(7L, inv.getAcceptedUserId());
    }

    /** token 不存在 → INVALID_INVITATION("邀请无效或已过期")。 */
    @Test
    void acceptThrowsWhenTokenNotFound() {
        when(invitationRepository.findByToken("missing")).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> teamService.accept(
                AcceptInvitationRequest.builder().token("missing").username("u").password("p").build()));
        assertEquals(ErrorCode.INVALID_INVITATION.getCode(), ex.getCode());
        assertEquals("邀请无效或已过期", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    /** 邀请状态非 PENDING → INVALID_INVITATION("邀请已失效")。 */
    @Test
    void acceptThrowsWhenInvitationNotPending() {
        when(invitationRepository.findByToken("tok"))
                .thenReturn(Optional.of(invitation("tok", "ACCEPTED", LocalDateTime.now().plusDays(1))));

        BizException ex = assertThrows(BizException.class, () -> teamService.accept(
                AcceptInvitationRequest.builder().token("tok").username("u").password("p").build()));
        assertEquals(ErrorCode.INVALID_INVITATION.getCode(), ex.getCode());
        assertEquals("邀请已失效", ex.getMessage());
        verify(invitationRepository, never()).save(any(TeamInvitation.class));
    }

    /** 邀请已过期 → 先落库 EXPIRED 再抛 INVALID_INVITATION("邀请已过期")。 */
    @Test
    void acceptMarksExpiredAndThrows() {
        TeamInvitation inv = invitation("tok", "PENDING", LocalDateTime.now().minusMinutes(1));
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(inv));
        when(invitationRepository.save(any(TeamInvitation.class))).thenAnswer(i -> i.getArgument(0));

        BizException ex = assertThrows(BizException.class, () -> teamService.accept(
                AcceptInvitationRequest.builder().token("tok").username("u").password("p").build()));
        assertEquals(ErrorCode.INVALID_INVITATION.getCode(), ex.getCode());
        assertEquals("邀请已过期", ex.getMessage());
        assertEquals("EXPIRED", inv.getStatus());
        verify(invitationRepository).save(inv);
        verify(userRepository, never()).save(any(User.class));
    }

    /** 用户名已被占用 → VALIDATION("用户名已存在")。 */
    @Test
    void acceptThrowsWhenUsernameTaken() {
        when(invitationRepository.findByToken("tok"))
                .thenReturn(Optional.of(invitation("tok", "PENDING", LocalDateTime.now().plusDays(1))));
        when(userRepository.findByUsername("taken")).thenReturn(Optional.of(member(9L, "taken", "MEMBER")));

        BizException ex = assertThrows(BizException.class, () -> teamService.accept(
                AcceptInvitationRequest.builder().token("tok").username("taken").password("p").build()));
        assertEquals(ErrorCode.VALIDATION.getCode(), ex.getCode());
        assertEquals("用户名已存在", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    /** 邀请 expiresAt 为 null → 覆盖 {@code inv.getExpiresAt() != null} 的 false 分支，视为未过期。 */
    @Test
    void acceptTreatsNullExpiryAsValid() {
        TeamInvitation inv = invitation("tok", "PENDING", null);
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(inv));
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userService.encodePassword("pw")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(8L);
            return u;
        });
        when(invitationRepository.save(any(TeamInvitation.class))).thenAnswer(i -> i.getArgument(0));
        when(studioRepository.findById(1L)).thenReturn(Optional.of(studio(1L, 1L)));
        when(jwtUtil.generateToken(8L, 1L, "newuser", "MEMBER")).thenReturn("jwt-2");

        AuthResponse resp = teamService.accept(
                AcceptInvitationRequest.builder().token("tok").username("newuser").password("pw").build());

        assertEquals("jwt-2", resp.getToken());
        assertEquals("ACCEPTED", inv.getStatus());
    }

    /** accept 时工作室数据缺失 → SYSTEM("工作室数据异常")。 */
    @Test
    void acceptThrowsSystemWhenStudioMissing() {
        TeamInvitation inv = invitation("tok", "PENDING", LocalDateTime.now().plusDays(1));
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(inv));
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userService.encodePassword("pw")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(9L);
            return u;
        });
        when(invitationRepository.save(any(TeamInvitation.class))).thenAnswer(i -> i.getArgument(0));
        when(studioRepository.findById(1L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> teamService.accept(
                AcceptInvitationRequest.builder().token("tok").username("newuser").password("pw").build()));
        assertEquals(ErrorCode.SYSTEM.getCode(), ex.getCode());
        assertEquals("工作室数据异常", ex.getMessage());
    }

    /** B2 成员列表为空 → stream 空集合分支。 */
    @Test
    void membersReturnsEmptyListWhenNoUsers() {
        when(userRepository.findByStudioId(1L)).thenReturn(List.of());

        List<TeamMemberDTO> list = teamService.members(1L);
        assertEquals(0, list.size());
    }
}
