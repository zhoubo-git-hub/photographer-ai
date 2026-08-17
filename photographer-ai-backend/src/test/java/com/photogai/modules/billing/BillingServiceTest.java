package com.photogai.modules.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.billing.dto.SubscribeRequest;
import com.photogai.modules.billing.dto.SubscribeResponse;
import com.photogai.modules.billing.dto.SubscriptionView;
import com.photogai.modules.billing.entity.PaymentOrder;
import com.photogai.modules.billing.entity.Subscription;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 计费编排服务单元测试（Mockito，不连 PG）。
 *
 * <p>@Value 注入的 mockEnabled/proPrice/teamPrice 在 setUp 中通过反射注入；
 * 覆盖下单/模拟支付/回调主路径 + 套餐非法/支付单缺失/生产禁用模拟支付异常路径。
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private BillingService billingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(billingService, "mockEnabled", true);
        ReflectionTestUtils.setField(billingService, "proPrice", 39);
        ReflectionTestUtils.setField(billingService, "teamPrice", 99);
    }

    private PaymentOrder pendingOrder(String outTradeNo, String planType, String channel) {
        PaymentOrder po = new PaymentOrder();
        po.setId(1L);
        po.setStudioId(1L);
        po.setPlanType(planType);
        po.setChannel(channel);
        po.setOutTradeNo(outTradeNo);
        po.setAmount(BigDecimal.valueOf(39));
        po.setStatus("PENDING");
        return po;
    }

    /** 下单：生成支付单并预创建支付入口。 */
    @Test
    void subscribeCreatesPaymentOrder() {
        SubscribeRequest req = SubscribeRequest.builder().planType("PRO").channel("MOCK").build();
        when(paymentGateway.createOrder(anyString(), anyString()))
                .thenReturn(PrecreateResult.builder().payUrl("url").qrCode("qr").build());
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(i -> {
            PaymentOrder po = i.getArgument(0);
            po.setId(100L);
            return po;
        });

        SubscribeResponse resp = billingService.subscribe(1L, req);
        assertNotNull(resp.getOutTradeNo());
        assertEquals(0, BigDecimal.valueOf(39).compareTo(resp.getAmount()));
    }

    /** 下单：非法套餐类型抛 VALIDATION(400)。 */
    @Test
    void subscribeThrowsValidationForInvalidPlan() {
        SubscribeRequest req = SubscribeRequest.builder().planType("BAD").build();

        BizException ex = assertThrows(BizException.class, () -> billingService.subscribe(1L, req));
        assertEquals(ErrorCode.VALIDATION.getCode(), ex.getCode());
    }

    /** 模拟支付：正常闭环激活订阅并返回视图。 */
    @Test
    void mockPayActivatesSubscription() {
        PaymentOrder po = pendingOrder("P20240101-1234", "PRO", "MOCK");
        when(paymentOrderRepository.findByOutTradeNo("P20240101-1234")).thenReturn(Optional.of(po));

        Subscription sub = new Subscription();
        sub.setId(7L);
        sub.setStudioId(1L);
        sub.setPlanType("PRO");
        sub.setStatus("ACTIVE");
        when(subscriptionService.activate(anyLong(), anyString(), anyInt(), anyString()))
                .thenReturn(sub);
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenReturn(po);

        SubscriptionView view = billingService.mockPay("P20240101-1234");
        assertEquals("PRO", view.getPlanType());
    }

    /** 模拟支付：生产环境禁用时抛 FORBIDDEN(403)。 */
    @Test
    void mockPayThrowsForbiddenWhenDisabled() {
        ReflectionTestUtils.setField(billingService, "mockEnabled", false);

        BizException ex = assertThrows(BizException.class, () -> billingService.mockPay("x"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    /** 支付成功入口：支付单不存在抛 SUBSCRIPTION_NOT_FOUND(404)。 */
    @Test
    void onPaidThrowsWhenPaymentOrderNotFound() {
        when(paymentOrderRepository.findByOutTradeNo(anyString())).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> billingService.onPaid("missing"));
        assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND.getCode(), ex.getCode());
    }
}
