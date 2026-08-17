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
import com.photogai.modules.ai.dto.QuoteRequest;
import com.photogai.modules.ai.dto.QuoteResponse;
import java.math.BigDecimal;
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
 * AI 报价控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class AiQuoteControllerTest {

    @Mock
    private AiQuoteService aiQuoteService;

    @InjectMocks
    private AiQuoteController controller;

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
    void quoteReturnsPriceRange() throws Exception {
        QuoteRequest req = QuoteRequest.builder().shootType("婚纱写真").build();
        QuoteResponse resp = QuoteResponse.builder()
                .priceLow(BigDecimal.valueOf(1000))
                .priceHigh(BigDecimal.valueOf(1200))
                .basis("基础价").script("话术").remainingQuota(5).build();
        when(aiQuoteService.quote(any(QuoteRequest.class), anyLong())).thenReturn(resp);

        mockMvc.perform(post("/api/ai/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.priceLow").value(1000));
    }

    @Test
    void quotePropagatesQuotaBizExceptionAsForbidden() throws Exception {
        QuoteRequest req = QuoteRequest.builder().shootType("婚纱写真").build();
        when(aiQuoteService.quote(any(QuoteRequest.class), anyLong()))
                .thenThrow(new BizException(ErrorCode.FORBIDDEN,
                        "免费版本月 AI 报价已用满 5 次，请升级专业版解锁无限次"));

        mockMvc.perform(post("/api/ai/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("免费版本月 AI 报价已用满 5 次，请升级专业版解锁无限次"));
    }
}
