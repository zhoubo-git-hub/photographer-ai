package com.photogai.modules.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.photogai.common.JwtUser;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.exception.GlobalExceptionHandler;
import com.photogai.modules.order.dto.ReminderDTO;
import com.photogai.modules.order.enums.ReminderStatus;
import com.photogai.modules.order.enums.ReminderType;
import java.util.List;
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
 * 提醒控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class ReminderControllerTest {

    @Mock
    private ReminderService reminderService;

    @InjectMocks
    private ReminderController controller;

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
    void listReturnsReminders() throws Exception {
        ReminderDTO dto = ReminderDTO.builder().id(1L).type(ReminderType.DEPOSIT_DUE)
                .status(ReminderStatus.PENDING).build();
        when(reminderService.listByStudioAndStatus(anyLong(), any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void listDueOnlyReturnsReminders() throws Exception {
        ReminderDTO dto = ReminderDTO.builder().id(1L).type(ReminderType.DEPOSIT_DUE)
                .status(ReminderStatus.PENDING).build();
        when(reminderService.listDueOnly(anyLong())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/reminders").param("dueOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void updateStatusReturnsReminder() throws Exception {
        ReminderDTO dto = ReminderDTO.builder().id(1L).status(ReminderStatus.DONE).build();
        when(reminderService.updateStatus(anyLong(), anyLong(), any())).thenReturn(dto);

        mockMvc.perform(put("/api/reminders/1").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    void updateStatusPropagatesNotFound() throws Exception {
        when(reminderService.updateStatus(anyLong(), anyLong(), any()))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "提醒不存在"));

        mockMvc.perform(put("/api/reminders/99").param("status", "DONE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("提醒不存在"));
    }
}
