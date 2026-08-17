package com.photogai.modules.quota;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.photogai.common.JwtUser;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.exception.GlobalExceptionHandler;
import com.photogai.modules.quota.dto.QuotaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 额度控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class QuotaControllerTest {

    @Mock
    private QuotaService quotaService;

    @InjectMocks
    private QuotaController controller;

    private MockMvc mockMvc;

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
    void getReturnsQuota() throws Exception {
        QuotaDTO dto = QuotaDTO.builder().planType("FREE").orderCount(1).orderLimit(10)
                .aiQuoteUsedMonth(0).aiQuoteLimit(5).quotaMonth("2024-05")
                .remainingOrderQuota(9).remainingAiQuota(5).build();
        when(quotaService.getQuota(anyLong())).thenReturn(dto);

        mockMvc.perform(get("/api/quota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planType").value("FREE"));
    }

    @Test
    void getPropagatesSystemError() throws Exception {
        when(quotaService.getQuota(anyLong()))
                .thenThrow(new BizException(ErrorCode.SYSTEM, "额度查询异常"));

        mockMvc.perform(get("/api/quota"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("额度查询异常"));
    }
}
