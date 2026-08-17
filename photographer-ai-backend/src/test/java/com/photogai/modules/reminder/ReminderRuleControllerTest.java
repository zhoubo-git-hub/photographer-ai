package com.photogai.modules.reminder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.photogai.modules.reminder.ReminderTriggerEvent;
import com.photogai.modules.reminder.dto.ReminderRuleDTO;
import com.photogai.modules.reminder.dto.ReminderRuleRequest;
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
 * 提醒规则控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class ReminderRuleControllerTest {

    @Mock
    private ReminderRuleService reminderRuleService;

    @InjectMocks
    private ReminderRuleController controller;

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
    void listReturnsRules() throws Exception {
        ReminderRuleDTO dto = ReminderRuleDTO.builder().id(1L)
                .event(ReminderTriggerEvent.DEPOSIT).offsetDays(3).enabled(true).channel("INAPP").build();
        when(reminderRuleService.listByStudio(anyLong())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/reminder-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].event").value("DEPOSIT"));
    }

    @Test
    void listPropagatesProRequired() throws Exception {
        when(reminderRuleService.listByStudio(anyLong()))
                .thenThrow(new BizException(ErrorCode.PRO_REQUIRED, "该功能为专业版专属，请升级专业版"));

        mockMvc.perform(get("/api/reminder-rules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("该功能为专业版专属，请升级专业版"));
    }

    @Test
    void createReturnsRule() throws Exception {
        ReminderRuleRequest req = ReminderRuleRequest.builder()
                .event(ReminderTriggerEvent.DEPOSIT).offsetDays(3).build();
        ReminderRuleDTO dto = ReminderRuleDTO.builder().id(1L)
                .event(ReminderTriggerEvent.DEPOSIT).offsetDays(3).enabled(true).channel("INAPP").build();
        when(reminderRuleService.create(anyLong(), any(ReminderRuleRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/reminder-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void updateReturnsRule() throws Exception {
        ReminderRuleRequest req = ReminderRuleRequest.builder()
                .event(ReminderTriggerEvent.DEPOSIT).offsetDays(5).build();
        ReminderRuleDTO dto = ReminderRuleDTO.builder().id(1L)
                .event(ReminderTriggerEvent.DEPOSIT).offsetDays(5).enabled(true).channel("INAPP").build();
        when(reminderRuleService.update(anyLong(), anyLong(), any(ReminderRuleRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/reminder-rules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.offsetDays").value(5));
    }

    @Test
    void updatePropagatesNotFound() throws Exception {
        ReminderRuleRequest req = ReminderRuleRequest.builder()
                .event(ReminderTriggerEvent.DEPOSIT).offsetDays(5).build();
        when(reminderRuleService.update(anyLong(), anyLong(), any(ReminderRuleRequest.class)))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "提醒规则不存在"));

        mockMvc.perform(put("/api/reminder-rules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("提醒规则不存在"));
    }

    @Test
    void deleteSucceeds() throws Exception {
        mockMvc.perform(delete("/api/reminder-rules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void deletePropagatesNotFound() throws Exception {
        doThrow(new BizException(ErrorCode.NOT_FOUND, "提醒规则不存在"))
                .when(reminderRuleService).delete(anyLong(), anyLong());

        mockMvc.perform(delete("/api/reminder-rules/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("提醒规则不存在"));
    }
}
