package com.photogai.modules.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photogai.common.JwtUser;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.exception.GlobalExceptionHandler;
import com.photogai.modules.dashboard.dto.FunnelDTO;
import com.photogai.modules.dashboard.dto.MemberPerfDTO;
import com.photogai.modules.dashboard.dto.OverviewDTO;
import java.math.BigDecimal;
import java.util.List;
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
 * 经营看板控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController controller;

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

    @Test
    void overviewReturnsAggregation() throws Exception {
        OverviewDTO dto = OverviewDTO.builder()
                .revenue(BigDecimal.TEN).orderCount(1).aov(BigDecimal.TEN).repurchaseRate(0.5)
                .conversion(OverviewDTO.Conversion.builder()
                        .consult(1).deposit(1).shoot(1).deliver(1).build())
                .revenuePoints(List.of()).build();
        when(dashboardService.overview(anyLong(), any(), any())).thenReturn(dto);

        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderCount").value(1));
    }

    @Test
    void overviewPropagatesProRequired() throws Exception {
        when(dashboardService.overview(anyLong(), any(), any()))
                .thenThrow(new BizException(ErrorCode.PRO_REQUIRED, "该功能为专业版专属，请升级专业版"));

        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("该功能为专业版专属，请升级专业版"));
    }

    @Test
    void funnelReturnsStages() throws Exception {
        FunnelDTO dto = FunnelDTO.builder()
                .stages(List.of(FunnelDTO.Stage.builder()
                        .status("CONSULT").count(2).rate(1.0).build()))
                .build();
        when(dashboardService.funnel(anyLong(), any(), any())).thenReturn(dto);

        mockMvc.perform(get("/api/dashboard/funnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.stages[0].status").value("CONSULT"));
    }

    @Test
    void membersReturnsPerformance() throws Exception {
        MemberPerfDTO dto = MemberPerfDTO.builder()
                .memberId(2L).name("成员A").orderCount(1).revenue(BigDecimal.TEN).aov(BigDecimal.TEN).build();
        when(dashboardService.members(anyLong())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/dashboard/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("成员A"));
    }

    @Test
    void membersPropagatesTeamRequired() throws Exception {
        when(dashboardService.members(anyLong()))
                .thenThrow(new BizException(ErrorCode.TEAM_REQUIRED, "该功能需团队版"));

        mockMvc.perform(get("/api/dashboard/members"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("该功能需团队版"));
    }
}
