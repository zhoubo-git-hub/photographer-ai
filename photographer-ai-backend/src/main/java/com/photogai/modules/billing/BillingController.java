package com.photogai.modules.billing;

import com.photogai.common.CurrentUser;
import com.photogai.common.Result;
import com.photogai.modules.billing.dto.PaymentNotifyRequest;
import com.photogai.modules.billing.dto.SubscribeRequest;
import com.photogai.modules.billing.dto.SubscribeResponse;
import com.photogai.modules.billing.dto.SubscriptionCancelRequest;
import com.photogai.modules.billing.dto.SubscriptionView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计费接口：订阅下单 / 通道回调 / 模拟支付 / 订阅查询 / 退订。
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    /** A1 订阅下单。 */
    @PostMapping("/subscribe")
    public Result<SubscribeResponse> subscribe(@Valid @RequestBody SubscribeRequest req) {
        return Result.ok(billingService.subscribe(CurrentUser.getStudioId(), req));
    }

    /** A2 支付通道回调（按 outTradeNo 定位）。 */
    @PostMapping("/notify/{channel}")
    public Result<String> notify(@PathVariable String channel,
                                 @RequestBody PaymentNotifyRequest req) {
        return Result.ok(billingService.handleNotify(channel, req));
    }

    /** A3 模拟支付成功（仅 mock 模式）。 */
    @PostMapping("/mock-pay")
    public Result<SubscriptionView> mockPay(@RequestBody java.util.Map<String, String> body) {
        String outTradeNo = body == null ? null : body.get("outTradeNo");
        return Result.ok(billingService.mockPay(outTradeNo));
    }

    /** A4 当前订阅状态。 */
    @GetMapping("/subscription")
    public Result<SubscriptionView> subscription() {
        return Result.ok(billingService.getSubscription(CurrentUser.getStudioId()));
    }

    /** A5 退订（关闭自动续费）。 */
    @PostMapping("/cancel")
    public Result<Void> cancel(@RequestBody(required = false) SubscriptionCancelRequest req) {
        billingService.cancel(CurrentUser.getStudioId(), CurrentUser.getRole(),
                req == null ? SubscriptionCancelRequest.builder().build() : req);
        return Result.ok();
    }
}
