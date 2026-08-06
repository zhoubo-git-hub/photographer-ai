package com.photogai.modules.team;

import com.photogai.common.ErrorCode;
import com.photogai.config.JwtUtil;
import com.photogai.exception.BizException;
import com.photogai.modules.auth.UserRepository;
import com.photogai.modules.auth.UserService;
import com.photogai.modules.auth.dto.AuthResponse;
import com.photogai.modules.auth.dto.UserDTO;
import com.photogai.modules.auth.entity.User;
import com.photogai.modules.billing.SubscriptionService;
import com.photogai.modules.order.OrderRepository;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.dto.StudioDTO;
import com.photogai.modules.studio.entity.Studio;
import com.photogai.modules.team.dto.AcceptInvitationRequest;
import com.photogai.modules.team.dto.TeamInviteRequest;
import com.photogai.modules.team.dto.TeamMemberDTO;
import com.photogai.modules.team.entity.TeamInvitation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 团队服务：邀请 / 接受 / 改角色 / 移除 / 列表，成员上限校验，多租户隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    /** 团队版成员硬上限（含 OWNER）。 */
    public static final int TEAM_MEMBER_LIMIT = 5;

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final TeamInvitationRepository invitationRepository;
    private final StudioRepository studioRepository;
    private final OrderRepository orderRepository;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    /** B1 邀请成员（带操作者角色，供矩阵校验）。 */
    @Transactional
    public TeamMemberDTO invite(Long studioId, Long inviterId, String inviterRole,
                                TeamInviteRequest req) {
        subscriptionService.requireTeam(studioId);
        RoleGuard.assertManageTeam(inviterRole);

        if (!List.of("ADMIN", "MEMBER", "READONLY").contains(req.getRole())) {
            throw new BizException(ErrorCode.VALIDATION, "邀请角色仅支持 ADMIN / MEMBER / READONLY");
        }
        if (userRepository.countByStudioId(studioId) >= TEAM_MEMBER_LIMIT) {
            throw new BizException(ErrorCode.MEMBER_LIMIT_EXCEEDED, "团队人数已达上限（" + TEAM_MEMBER_LIMIT + "）");
        }

        TeamInvitation inv = new TeamInvitation();
        inv.setStudioId(studioId);
        inv.setInviterId(inviterId);
        inv.setEmail(req.getEmail());
        inv.setPhone(req.getPhone());
        inv.setRole(req.getRole());
        inv.setToken(UUID.randomUUID().toString().replace("-", ""));
        inv.setExpiresAt(LocalDateTime.now().plusDays(7));
        inv.setStatus("PENDING");
        TeamInvitation saved = invitationRepository.save(inv);

        return TeamMemberDTO.builder()
                .invitationId(saved.getId())
                .id(saved.getId())
                .username(req.getEmail() != null ? req.getEmail() : req.getPhone())
                .role(saved.getRole())
                .orderCount(0)
                .token(saved.getToken())
                .build();
    }

    /** B2 成员列表（同 Studio 全部用户）。 */
    @Transactional(readOnly = true)
    public List<TeamMemberDTO> members(Long studioId) {
        subscriptionService.requireTeam(studioId);
        return userRepository.findByStudioId(studioId).stream()
                .map(u -> TeamMemberDTO.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .role(u.getRole())
                        .orderCount((int) orderRepository
                                .countByStudioIdAndAssignedToAndDeletedAtIsNull(studioId, u.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    /** B3 修改成员角色。 */
    @Transactional
    public TeamMemberDTO updateRole(Long studioId, String operatorRole, Long memberId, String newRole) {
        subscriptionService.requireTeam(studioId);
        RoleGuard.assertManageTeam(operatorRole);

        // 所有权转移不通过此接口：ADMIN 近似 OWNER 但 PRD Q3 明确不可转让/提权至 OWNER。
        if ("OWNER".equals(newRole)) {
            throw new BizException(ErrorCode.FORBIDDEN, "不可通过此接口转让所有者权限");
        }
        if (!List.of("ADMIN", "MEMBER", "READONLY").contains(newRole)) {
            throw new BizException(ErrorCode.VALIDATION, "角色非法");
        }
        User member = requireMember(studioId, memberId);
        Studio studio = studioRepository.findById(studioId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "工作室不存在"));
        if (memberId.equals(studio.getOwnerUserId()) && !"OWNER".equals(newRole)) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能修改所有者的角色");
        }
        member.setRole(newRole);
        User saved = userRepository.save(member);
        return toDto(studioId, saved);
    }

    /** B4 移除成员（其名下订单回退未分配，不级联删）。 */
    @Transactional
    public void removeMember(Long studioId, String operatorRole, Long memberId) {
        subscriptionService.requireTeam(studioId);
        RoleGuard.assertManageTeam(operatorRole);

        User member = requireMember(studioId, memberId);
        Studio studio = studioRepository.findById(studioId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "工作室不存在"));
        if (memberId.equals(studio.getOwnerUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能移除工作室所有者");
        }

        // 名下订单回退未分配
        orderRepository.findByStudioIdAndAssignedToAndDeletedAtIsNull(studioId, memberId)
                .forEach(o -> {
                    o.setAssignedTo(null);
                    orderRepository.save(o);
                });

        userRepository.delete(member);
    }

    /** B5 接受邀请：凭 token 建用户并登录。 */
    @Transactional
    public AuthResponse accept(AcceptInvitationRequest req) {
        TeamInvitation inv = invitationRepository.findByToken(req.getToken())
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_INVITATION, "邀请无效或已过期"));
        if (!"PENDING".equals(inv.getStatus())) {
            throw new BizException(ErrorCode.INVALID_INVITATION, "邀请已失效");
        }
        if (inv.getExpiresAt() != null && inv.getExpiresAt().isBefore(LocalDateTime.now())) {
            inv.setStatus("EXPIRED");
            invitationRepository.save(inv);
            throw new BizException(ErrorCode.INVALID_INVITATION, "邀请已过期");
        }
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            throw new BizException(ErrorCode.VALIDATION, "用户名已存在");
        }

        User user = new User();
        user.setStudioId(inv.getStudioId());
        user.setUsername(req.getUsername());
        user.setPasswordHash(userService.encodePassword(req.getPassword()));
        user.setEmail(inv.getEmail());
        user.setRole(inv.getRole());
        User saved = userRepository.save(user);

        inv.setStatus("ACCEPTED");
        inv.setAcceptedUserId(saved.getId());
        invitationRepository.save(inv);

        Studio studio = studioRepository.findById(inv.getStudioId())
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM, "工作室数据异常"));
        String token = jwtUtil.generateToken(
                saved.getId(), studio.getId(), saved.getUsername(), saved.getRole());

        return AuthResponse.builder()
                .token(token)
                .user(UserDTO.from(saved))
                .studio(StudioDTO.from(studio))
                .build();
    }

    private User requireMember(Long studioId, Long memberId) {
        return userRepository.findByStudioIdAndId(studioId, memberId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "成员不存在"));
    }

    private TeamMemberDTO toDto(Long studioId, User u) {
        return TeamMemberDTO.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .role(u.getRole())
                .orderCount((int) orderRepository
                        .countByStudioIdAndAssignedToAndDeletedAtIsNull(studioId, u.getId()))
                .build();
    }
}
