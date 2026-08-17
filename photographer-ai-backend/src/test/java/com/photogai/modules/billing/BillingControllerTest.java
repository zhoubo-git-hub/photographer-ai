package com.photogai.modules.billing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import com.photogai.modules.billing.dto.PaymentNotifyRequest;
import com.photogai.modules.billing.dto.SubscribeRequest;
import com.photogai.modules.billing.dto.SubscribeResponse;
import com.photogai.modules.billing.dto.SubscriptionCancelRequest;
import com.photogai.modules.billing.dto.SubscriptionView;
import java.math.BigDecimal;
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
 * 计费控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class BillingControllerTest {

    @Mock
    private BillingService billingService;

    @InjectMocks
    private BillingController controller;

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
    void subscribeReturnsPrecreate() throws Exception {
        SubscribeRequest req = SubscribeRequest.builder().planType("PRO").build();
        SubscribeResponse resp = SubscribeResponse.builder()
                .outTradeNo("P123").payUrl("https://pay").qrCode("qr").amount(BigDecimal.valueOf(39)).build();
        when(billingService.subscribe(anyLong(), any(SubscribeRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/billing/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.outTradeNo").value("P123"));
    }

    @Test
    void subscribePropagatesValidationError() throws Exception {
        SubscribeRequest req = SubscribeRequest.builder().planType("FREE").build();
        when(billingService.subscribe(anyLong(), any(SubscribeRequest.class)))
                .thenThrow(new BizException(ErrorCode.VALIDATION, "套餐类型仅支持 PRO / TEAM"));

        mockMvc.perform(post("/api/billing/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("套餐类型仅支持 PRO / TEAM"));
    }

    @Test
    void notifyReturnsSuccess() throws Exception {
        PaymentNotifyRequest req = PaymentNotifyRequest.builder().rawBody("<xml/>").build();
        when(billingService.handleNotify(anyString(), any(PaymentNotifyRequest.class))).thenReturn("success");

        mockMvc.perform(post("/api/billing/notify/WECHAT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("success"));
    }

    @Test
    void notifyPropagatesNotFound() throws Exception {
        PaymentNotifyRequest req = PaymentNotifyRequest.builder().rawBody("<xml/>").build();
        when(billingService.handleNotify(anyString(), any(PaymentNotifyRequest.class)))
                .thenThrow(new BizException(ErrorCode.SUBSCRIPTION_NOT_FOUND, "支付单不存在"));

        mockMvc.perform(post("/api/billing/notify/WECHAT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("支付单不存在"));
    }

    @Test
    void mockPayReturnsSubscriptionView() throws Exception {
        SubscriptionView view = SubscriptionView.builder()
                .planType("PRO").status("ACTIVE").autoRenew(true).build();
        when(billingService.mockPay(any())).thenReturn(view);

        mockMvc.perform(post("/api/billing/mock-pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("outTradeNo", "P123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planType").value("PRO"));
    }

    @Test
    void mockPayPropagatesForbidden() throws Exception {
        when(billingService.mockPay(any()))
                .thenThrow(new BizException(ErrorCode.FORBIDDEN, "生产环境已禁用模拟支付"));

        mockMvc.perform(post("/api/billing/mock-pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("outTradeNo", "P123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("生产环境已禁用模拟支付"));
    }

    @Test
    void subscriptionReturnsView() throws Exception {
        SubscriptionView view = SubscriptionView.builder()
                .planType("FREE").status("NONE").autoRenew(false).build();
        when(billingService.getSubscription(anyLong())).thenReturn(view);

        mockMvc.perform(get("/api/billing/subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.planType").value("FREE"));
    }

    @Test
    void subscriptionPropagatesSystemError() throws Exception {
        when(billingService.getSubscription(anyLong()))
                .thenThrow(new BizException(ErrorCode.SYSTEM, "订阅查询异常"));

        mockMvc.perform(get("/api/billing/subscription"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("订阅查询异常"));
    }

    @Test
    void cancelSucceeds() throws Exception {
        SubscriptionCancelRequest req = SubscriptionCancelRequest.builder().reason("no need").build();
        mockMvc.perform(post("/api/billing/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void cancelPropagatesForbidden() throws Exception {
        SubscriptionCancelRequest req = SubscriptionCancelRequest.builder().build();
        doThrow(new BizException(ErrorCode.FORBIDDEN, "无权限退订"))
                .when(billingService).cancel(anyLong(), anyString(), any(SubscriptionCancelRequest.class));

        mockMvc.perform(post("/api/billing/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权限退订"));
    }
}
