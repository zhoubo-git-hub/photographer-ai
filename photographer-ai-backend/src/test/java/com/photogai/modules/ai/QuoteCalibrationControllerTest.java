package com.photogai.modules.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photogai.common.JwtUser;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.exception.GlobalExceptionHandler;
import com.photogai.modules.ai.dto.QuoteCalibrationApplyRequest;
import com.photogai.modules.ai.dto.QuoteCalibrationDTO;
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
 * AI 报价校准控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class QuoteCalibrationControllerTest {

    @Mock
    private QuoteCalibrationService calibrationService;

    @InjectMocks
    private QuoteCalibrationController controller;

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
    void listReturnsSuggestions() throws Exception {
        QuoteCalibrationDTO dto = QuoteCalibrationDTO.builder()
                .id(1L).dimensionKey("上海|婚纱写真").dimensionLabel("上海·婚纱写真")
                .sampleCount(30).currentCoef(BigDecimal.ONE)
                .suggestedCoef(BigDecimal.ONE).offsetPct(5)
                .withinBoundary(true).status("PENDING").build();
        when(calibrationService.list(anyLong())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/ai/quote-calibration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void applyReturnsUpdatedDto() throws Exception {
        QuoteCalibrationApplyRequest req = QuoteCalibrationApplyRequest.builder().id(1L).build();
        QuoteCalibrationDTO dto = QuoteCalibrationDTO.builder().id(1L).status("APPLIED").build();
        when(calibrationService.apply(anyLong(), anyLong())).thenReturn(dto);

        mockMvc.perform(post("/api/ai/quote-calibration/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPLIED"));
    }

    @Test
    void applyPropagatesNotFound() throws Exception {
        QuoteCalibrationApplyRequest req = QuoteCalibrationApplyRequest.builder().id(99L).build();
        when(calibrationService.apply(anyLong(), anyLong()))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "校准建议不存在"));

        mockMvc.perform(post("/api/ai/quote-calibration/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("校准建议不存在"));
    }
}
