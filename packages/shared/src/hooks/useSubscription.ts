import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { billingApi } from '../api/subscription';
import { quotaApi } from '../api/quota';
import { useAuthStore } from '../store/authStore';
import type { SubscribeRequest, SubscriptionView } from '../types/models';
import { queryKeys } from './queryKeys';

/**
 * 订阅/计费 hooks（阶段3 批次 A 语义）：
 * - useSubscription：当前订阅视图（无有效订阅为 null）
 * - useQuota：配额信息（套餐/订单量/AI 报价次数）
 * - useSubscribe / useMockPay / useCancelSubscription：下单、沙箱支付、退订；
 *   支付成功后回写 authStore.setStudioPlanType（与 web BillingPage 行为一致）。
 */
export function useSubscription() {
  return useQuery({
    queryKey: queryKeys.subscription,
    queryFn: () => billingApi.getSubscription(),
  });
}

export function useQuota() {
  return useQuery({
    queryKey: queryKeys.quota,
    queryFn: () => quotaApi.get(),
  });
}

function useInvalidateBilling() {
  const qc = useQueryClient();
  return async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: queryKeys.subscription }),
      qc.invalidateQueries({ queryKey: queryKeys.quota }),
    ]);
  };
}

/** A1 订阅下单。 */
export function useSubscribe() {
  return useMutation({
    mutationFn: (data: SubscribeRequest) => billingApi.subscribe(data),
  });
}

/** A3 模拟支付成功（沙箱），成功后局部回写套餐并刷新订阅/配额。 */
export function useMockPay() {
  const invalidate = useInvalidateBilling();
  const setStudioPlanType = useAuthStore((s) => s.setStudioPlanType);
  return useMutation({
    mutationFn: (outTradeNo: string) => billingApi.mockPay(outTradeNo),
    onSuccess: (view: SubscriptionView) => {
      setStudioPlanType(view.planType);
      void invalidate();
    },
  });
}

/** A5 退订（关闭自动续费）。 */
export function useCancelSubscription() {
  const invalidate = useInvalidateBilling();
  return useMutation({
    mutationFn: (reason?: string) => billingApi.cancel(reason),
    onSuccess: () => invalidate(),
  });
}
