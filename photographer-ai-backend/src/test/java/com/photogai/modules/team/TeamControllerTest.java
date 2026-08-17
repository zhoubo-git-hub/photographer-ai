package com.photogai.modules.team;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photogai.common.JwtUser;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.exception.GlobalExceptionHandler;
import com.photogai.modules.auth.dto.AuthResponse;
import com.photogai.modules.auth.dto.UserDTO;
import com.photogai.modules.studio.dto.StudioDTO;
import com.photogai.modules.team.dto.AcceptInvitationRequest;
import com.photogai.modules.team.dto.TeamInviteRequest;
import com.photogai.modules.team.dto.TeamMemberDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 团队控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    @Mock
    private TeamService teamService;

    @InjectMocks
    private TeamController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new JwtUser(1L, 1L, "tester", "STUDIO"), "", AuthorityUtils.NO_AUTHORITIES));
    }

    private AuthResponse sampleAuthResponse() {
        return AuthResponse.builder()
                .token("jwt-token")
                .user(UserDTO.builder().id(1L).username("tester").build())
                .studio(StudioDTO.builder().id(1L).name("我的工作室").build())
                .build();
    }

    @Test
    void inviteReturnsMember() throws Exception {
        TeamInviteRequest req = TeamInviteRequest.builder().email("a@b.com").role("MEMBER").build();
        TeamMemberDTO dto = TeamMemberDTO.builder().id(2L).username("a@b.com").role("MEMBER").build();
        when(teamService.invite(anyLong(), anyLong(), anyString(), any(TeamInviteRequest.class)))
                .thenReturn(dto);

        mockMvc.perform(post("/api/team/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(2));
    }

    @Test
    void invitePropagatesMemberLimitExceeded() throws Exception {
        TeamInviteRequest req = TeamInviteRequest.builder().email("a@b.com").role("MEMBER").build();
        when(teamService.invite(anyLong(), anyLong(), anyString(), any(TeamInviteRequest.class)))
                .thenThrow(new BizException(ErrorCode.MEMBER_LIMIT_EXCEEDED, "团队人数已达上限（5）"));

        mockMvc.perform(post("/api/team/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("团队人数已达上限（5）"));
    }

    @Test
    void membersReturnsList() throws Exception {
        TeamMemberDTO dto = TeamMemberDTO.builder().id(2L).username("成员A").role("MEMBER").build();
        when(teamService.members(anyLong())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/team/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].username").value("成员A"));
    }

    @Test
    void membersPropagatesTeamRequired() throws Exception {
        when(teamService.members(anyLong()))
                .thenThrow(new BizException(ErrorCode.TEAM_REQUIRED, "该功能需团队版"));

        mockMvc.perform(get("/api/team/members"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("该功能需团队版"));
    }

    @Test
    void updateRoleReturnsMember() throws Exception {
        TeamMemberDTO dto = TeamMemberDTO.builder().id(2L).username("成员A").role("ADMIN").build();
        when(teamService.updateRole(anyLong(), anyString(), anyLong(), anyString())).thenReturn(dto);

        mockMvc.perform(put("/api/team/members/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void removeSucceeds() throws Exception {
        mockMvc.perform(delete("/api/team/members/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void removePropagatesNotFound() throws Exception {
        doThrow(new BizException(ErrorCode.NOT_FOUND, "成员不存在"))
                .when(teamService).removeMember(anyLong(), anyString(), anyLong());

        mockMvc.perform(delete("/api/team/members/2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("成员不存在"));
    }

    @Test
    void acceptReturnsAuthResponse() throws Exception {
        AcceptInvitationRequest req = AcceptInvitationRequest.builder()
                .token("tok").username("newuser").password("secret123").build();
        when(teamService.accept(any(AcceptInvitationRequest.class))).thenReturn(sampleAuthResponse());

        mockMvc.perform(post("/api/team/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void acceptPropagatesInvalidInvitation() throws Exception {
        AcceptInvitationRequest req = AcceptInvitationRequest.builder()
                .token("bad").username("newuser").password("secret123").build();
        when(teamService.accept(any(AcceptInvitationRequest.class)))
                .thenThrow(new BizException(ErrorCode.INVALID_INVITATION, "邀请无效或已过期"));

        mockMvc.perform(post("/api/team/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邀请无效或已过期"));
    }
}
