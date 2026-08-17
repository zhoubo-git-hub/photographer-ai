package com.photogai.modules.customer;

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
import com.photogai.modules.customer.dto.CustomerCreateRequest;
import com.photogai.modules.customer.dto.CustomerDTO;
import com.photogai.modules.customer.dto.CustomerUpdateRequest;
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
 * 客户库控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private CustomerController controller;

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
        CustomerDTO dto = CustomerDTO.builder().id(1L).name("张三").build();
        PageData<CustomerDTO> page = PageData.<CustomerDTO>builder()
                .content(List.of(dto)).totalElements(1).totalPages(1).number(0).size(20).build();
        when(customerService.list(anyLong(), anyString(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content[0].name").value("张三"));
    }

    @Test
    void createReturnsCustomer() throws Exception {
        CustomerCreateRequest req = CustomerCreateRequest.builder().name("张三").phone("139").build();
        CustomerDTO dto = CustomerDTO.builder().id(1L).name("张三").build();
        when(customerService.create(anyLong(), any(CustomerCreateRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void detailReturnsCustomer() throws Exception {
        CustomerDTO dto = CustomerDTO.builder().id(1L).name("张三").build();
        when(customerService.detail(anyLong(), anyLong())).thenReturn(dto);

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("张三"));
    }

    @Test
    void detailPropagatesNotFound() throws Exception {
        when(customerService.detail(anyLong(), anyLong()))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "客户不存在"));

        mockMvc.perform(get("/api/customers/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("客户不存在"));
    }

    @Test
    void updateReturnsCustomer() throws Exception {
        CustomerUpdateRequest req = CustomerUpdateRequest.builder().name("李四").build();
        CustomerDTO dto = CustomerDTO.builder().id(1L).name("李四").build();
        when(customerService.update(anyLong(), anyLong(), any(CustomerUpdateRequest.class))).thenReturn(dto);

        mockMvc.perform(put("/api/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("李四"));
    }

    @Test
    void deleteSucceeds() throws Exception {
        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void deletePropagatesForbiddenWhenInProgress() throws Exception {
        doThrow(new BizException(ErrorCode.FORBIDDEN, "该客户存在进行中订单，无法删除"))
                .when(customerService).delete(anyLong(), anyLong());

        mockMvc.perform(delete("/api/customers/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("该客户存在进行中订单，无法删除"));
    }
}
