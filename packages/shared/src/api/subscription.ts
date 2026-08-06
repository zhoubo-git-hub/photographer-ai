import { request } from '../http';
import type { SubscribeRequest, SubscribeResponse, SubscriptionView } from '../types/models';

/**
 * 计费/订阅接口（阶段3 批次 A；架构 §2.2 命名为 subscription 模块）。
 * 真实通道回调由后端接收，前端仅负责：下单、模拟支付（沙箱）、查询订阅、退订。
 * 保留 web 现有导出名 billingApi，另提供 subscriptionApi 别名（同一对象）。
 */
export const billingApi = {
  /** A1 订阅下单：生成支付单（PENDING）并返回支付入口。 */
  subscribe: (data: SubscribeRequest) =>
    request<SubscribeResponse>({ url: '/billing/subscribe', method: 'POST', data }),

  /** A3 模拟支付成功（仅后端 mock 模式可用）。 */
  mockPay: (outTradeNo: string) =>
    request<SubscriptionView>({
      url: '/billing/mock-pay',
      method: 'POST',
      data: { outTradeNo },
    }),

  /** A4 查询当前订阅视图（无有效订阅返回 null）。 */
  getSubscription: () =>
    request<SubscriptionView | null>({ url: '/billing/subscription', method: 'GET' }),

  /** A5 退订（关闭自动续费）。 */
  cancel: (reason?: string) =>
    request<void>({
      url: '/billing/cancel',
      method: 'POST',
      data: reason ? { reason } : {},
    }),
};

/** 架构 §2.2 模块命名别名（与 billingApi 同一实例）。 */
export const subscriptionApi = billingApi;
