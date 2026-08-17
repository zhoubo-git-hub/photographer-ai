package com.photogai.modules.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.photogai.common.PageData;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.exception.GlobalExceptionHandler;
import com.photogai.modules.order.dto.ConflictDTO;
import com.photogai.modules.order.dto.OrderCreateRequest;
import com.photogai.modules.order.dto.OrderDTO;
import com.photogai.modules.order.dto.OrderUpdateRequest;
import com.photogai.modules.order.dto.StatusChangeRequest;
import com.photogai.modules.order.enums.OrderStatus;
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
 * 订单控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private ScheduleConflictService scheduleConflictService;

    @InjectMocks
    private OrderController controller;

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
    void listReturnsPage() throws Exception {
        OrderDTO dto = OrderDTO.builder().id(1L).title("婚纱订单").build();
        PageData<OrderDTO> page = PageData.<OrderDTO>builder()
                .content(List.of(dto)).totalElements(1).totalPages(1).number(0).size(20).build();
        when(orderService.list(anyLong(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content[0].title").value("婚纱订单"));
    }

    @Test
    void createReturnsOrder() throws Exception {
        OrderCreateRequest req = OrderCreateRequest.builder().customerId(1L).title("婚纱订单").build();
        OrderDTO dto = OrderDTO.builder().id(1L).title("婚纱订单").build();
        when(orderService.create(anyLong(), anyLong(), any(OrderCreateRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void createPropagatesConflict() throws Exception {
        OrderCreateRequest req = OrderCreateRequest.builder().customerId(1L).title("婚纱订单").build();
        when(orderService.create(anyLong(), anyLong(), any(OrderCreateRequest.class)))
                .thenThrow(new BizException(ErrorCode.CONFLICT, "档期冲突：与已存在订单拍摄时间重叠"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("档期冲突：与已存在订单拍摄时间重叠"));
    }

    @Test
    void conflictReturnsList() throws Exception {
        ConflictDTO dto = ConflictDTO.builder().orderId(2L).title("冲突单").build();
        when(scheduleConflictService.checkConflict(anyLong(), any(), any(), any())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/orders/conflict").param("shootDate", "2024-05-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].orderId").value(2));
    }

    @Test
    void detailReturnsOrder() throws Exception {
        OrderDTO dto = OrderDTO.builder().id(1L).title("婚纱订单").build();
        when(orderService.get(anyLong(), anyLong())).thenReturn(dto);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void detailPropagatesNotFound() throws Exception {
        when(orderService.get(anyLong(), anyLong()))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "订单不存在"));

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("订单不存在"));
    }

    @Test
    void updateReturnsOrder() throws Exception {
        OrderUpdateRequest req = OrderUpdateRequest.builder().title("改后").build();
        OrderDTO dto = OrderDTO.builder().id(1L).title("改后").build();
        when(orderService.update(anyLong(), anyLong(), any(OrderUpdateRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/orders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("改后"));
    }

    @Test
    void deleteSucceeds() throws Exception {
        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void deletePropagatesNotFound() throws Exception {
        doThrow(new BizException(ErrorCode.NOT_FOUND, "订单不存在"))
                .when(orderService).delete(anyLong(), anyLong());

        mockMvc.perform(delete("/api/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("订单不存在"));
    }

    @Test
    void assignReturnsOrder() throws Exception {
        OrderDTO dto = OrderDTO.builder().id(1L).assignedTo(5L).build();
        when(orderService.assign(anyLong(), anyLong(), any(), anyLong(), anyString())).thenReturn(dto);

        mockMvc.perform(post("/api/orders/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("memberId", 5L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.assignedTo").value(5));
    }

    @Test
    void changeStatusReturnsOrder() throws Exception {
        StatusChangeRequest req = StatusChangeRequest.builder().toStatus(OrderStatus.DEPOSIT).build();
        OrderDTO dto = OrderDTO.builder().id(1L).status(OrderStatus.DEPOSIT).build();
        when(orderService.changeStatus(anyLong(), anyLong(), any(StatusChangeRequest.class), anyLong()))
                .thenReturn(dto);

        mockMvc.perform(post("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("DEPOSIT"));
    }

    @Test
    void changeStatusPropagatesValidationError() throws Exception {
        StatusChangeRequest req = StatusChangeRequest.builder().toStatus(OrderStatus.DELIVER).build();
        when(orderService.changeStatus(anyLong(), anyLong(), any(StatusChangeRequest.class), anyLong()))
                .thenThrow(new BizException(ErrorCode.VALIDATION,
                        "非法的状态流转：CONSULT → DELIVER（仅允许相邻状态）"));

        mockMvc.perform(post("/api/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("非法的状态流转：CONSULT → DELIVER（仅允许相邻状态）"));
    }
}
