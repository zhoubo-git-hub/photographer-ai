package com.photogai.modules.contract;

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
import com.photogai.modules.contract.dto.ContractGenerateRequest;
import com.photogai.modules.contract.dto.ContractGenerateResponse;
import com.photogai.modules.contract.dto.ContractTemplateDTO;
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
 * 合同控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class ContractControllerTest {

    @Mock
    private ContractService contractService;

    @InjectMocks
    private ContractController controller;

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
    void templatesReturnsList() throws Exception {
        ContractTemplateDTO dto = ContractTemplateDTO.builder()
                .id(1L).name("标准合同").content("{{customerName}}").builtin(true).build();
        when(contractService.listTemplates(anyLong())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/contract-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("标准合同"));
    }

    @Test
    void generateReturnsContent() throws Exception {
        ContractGenerateRequest req = ContractGenerateRequest.builder()
                .orderId(1L).templateId(2L).build();
        ContractGenerateResponse resp = ContractGenerateResponse.builder()
                .title("合同-张三").content("客户：张三").build();
        when(contractService.generate(anyLong(), any(ContractGenerateRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/contracts/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("合同-张三"));
    }

    @Test
    void generatePropagatesNotFound() throws Exception {
        ContractGenerateRequest req = ContractGenerateRequest.builder()
                .orderId(1L).templateId(2L).build();
        when(contractService.generate(anyLong(), any(ContractGenerateRequest.class)))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "合同模板不存在"));

        mockMvc.perform(post("/api/contracts/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("合同模板不存在"));
    }
}
