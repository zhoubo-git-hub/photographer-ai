package com.photogai.modules.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photogai.common.JwtUser;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.exception.GlobalExceptionHandler;
import com.photogai.modules.ai.dto.CommRequest;
import com.photogai.modules.ai.dto.CommResponse;
import com.photogai.modules.ai.enums.CommScenario;
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
 * AI 沟通助手控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class AiCommControllerTest {

    @Mock
    private AiCommService aiCommService;

    @InjectMocks
    private AiCommController controller;

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
    void commReturnsGeneratedText() throws Exception {
        CommRequest req = CommRequest.builder().scenario(CommScenario.REPURCHASE).customerId(10L).build();
        CommResponse resp = CommResponse.builder()
                .text("话术正文").scenario(CommScenario.REPURCHASE).fallback(false).build();
        when(aiCommService.generate(any(CommRequest.class), anyLong())).thenReturn(resp);

        mockMvc.perform(post("/api/ai/comm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.text").value("话术正文"));
    }

    @Test
    void commPropagatesBizExceptionAsForbidden() throws Exception {
        CommRequest req = CommRequest.builder().scenario(CommScenario.REPURCHASE).customerId(10L).build();
        when(aiCommService.generate(any(CommRequest.class), anyLong()))
                .thenThrow(new BizException(ErrorCode.PRO_REQUIRED, "该功能为专业版专属，请升级专业版"));

        mockMvc.perform(post("/api/ai/comm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("该功能为专业版专属，请升级专业版"));
    }
}
