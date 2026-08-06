package com.photogai.modules.billing;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.billing.dto.PaymentNotifyRequest;
import com.photogai.modules.billing.dto.SubscribeRequest;
import com.photogai.modules.billing.dto.SubscribeResponse;
import com.photogai.modules.billing.dto.SubscriptionCancelRequest;
import com.photogai.modules.billing.dto.SubscriptionView;
import com.photogai.modules.billing.entity.PaymentOrder;
import com.photogai.modules.billing.entity.Subscription;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计费编排：下单 → 支付单 → 回调/mock → 置订阅 → 置 PRO/TEAM。
 *
 * <p>真实回调与 {@code mock-pay} 共用 {@link #onPaid} 状态机，确保闭环一致。
 * 多租户隔离：所有读写按 {@code studio_id}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final SubscriptionService subscriptionService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGateway paymentGateway;

    @Value("${app.payment.mock:true}")
    private boolean mockEnabled;

    @Value("${app.payment.price.pro:39}")
    private int proPrice;

    @Value("${app.payment.price.team:99}")
    private int teamPrice;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 订阅下单：生成支付单（PENDING）并调网关预创建。 */
    @Transactional
    public SubscribeResponse subscribe(Long studioId, SubscribeRequest req) {
        String planType = req.getPlanType();
        if (!"PRO".equals(planType) && !"TEAM".equals(planType)) {
            throw new BizException(ErrorCode.VALIDATION, "套餐类型仅支持 PRO / TEAM");
        }
        int price = "TEAM".equals(planType) ? teamPrice : proPrice;
        String channel = mockEnabled ? "MOCK"
                : (req.getChannel() == null || req.getChannel().isBlank() ? "WECHAT" : req.getChannel());

        String outTradeNo = generateOutTradeNo();
        PrecreateResult pre = paymentGateway.createOrder(planType, outTradeNo);

        PaymentOrder po = new PaymentOrder();
        po.setStudioId(studioId);
        po.setPlanType(planType);
        po.setChannel(channel);
        po.setOutTradeNo(outTradeNo);
        po.setAmount(BigDecimal.valueOf(price));
        po.setStatus("PENDING");
        paymentOrderRepository.save(po);

        return SubscribeResponse.builder()
                .outTradeNo(outTradeNo)
                .payUrl(pre.getPayUrl())
                .qrCode(pre.getQrCode())
                .amount(po.getAmount())
                .build();
    }

    /**
     * 支付成功统一入口（真实回调与 mock 共用）。幂等：已 PAID 直接返回当前订阅视图。
     */
    @Transactional
    public SubscriptionView onPaid(String outTradeNo) {
        PaymentOrder po = paymentOrderRepository.findByOutTradeNo(outTradeNo)
                .orElseThrow(() -> new BizException(ErrorCode.SUBSCRIPTION_NOT_FOUND, "支付单不存在"));
        if ("PAID".equals(po.getStatus())) {
            return toView(subscriptionService.current(po.getStudioId()).orElse(null));
        }
        if ("FAILED".equals(po.getStatus())) {
            throw new BizException(ErrorCode.PAYMENT_FAILED, "该支付单已失败");
        }

        po.setStatus("PAID");
        po.setPaidAt(LocalDateTime.now());
        Subscription sub = subscriptionService.activate(po.getStudioId(), po.getPlanType(), 1, po.getChannel());
        po.setSubscriptionId(sub.getId());
        paymentOrderRepository.save(po);

        return toView(sub);
    }

    /** 支付通道回调：验签解析出 outTradeNo 后走 onPaid。 */
    @Transactional
    public String handleNotify(String channel, PaymentNotifyRequest req) {
        String outTradeNo = paymentGateway.verifyAndParse(req.getRawBody());
        onPaid(outTradeNo);
        return "success";
    }

    /** 模拟支付成功（仅 mock 模式可用）。 */
    @Transactional
    public SubscriptionView mockPay(String outTradeNo) {
        if (!mockEnabled) {
            throw new BizException(ErrorCode.FORBIDDEN, "生产环境已禁用模拟支付");
        }
        return onPaid(outTradeNo);
    }

    /** 查询当前订阅视图（无有效订阅返回 null）。 */
    @Transactional(readOnly = true)
    public SubscriptionView getSubscription(Long studioId) {
        return subscriptionService.current(studioId)
                .map(this::toView)
                .orElse(null);
    }

    /** 退订（关闭自动续费）。 */
    @Transactional
    public void cancel(Long studioId, String operatorRole, SubscriptionCancelRequest req) {
        subscriptionService.cancelAutoRenew(studioId, operatorRole);
    }

    private SubscriptionView toView(Subscription sub) {
        if (sub == null) {
            return SubscriptionView.builder().planType("FREE").status("NONE").autoRenew(false).build();
        }
        return SubscriptionView.builder()
                .planType(sub.getPlanType())
                .status(sub.getStatus())
                .expiresAt(sub.getExpiresAt())
                .autoRenew(sub.isAutoRenew())
                .build();
    }

    /** 生成商户订单号：P + 日期 + 4 位随机。 */
    private String generateOutTradeNo() {
        int rnd = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "P" + LocalDateTime.now().format(DATE_FMT) + "-" + rnd;
    }
}
