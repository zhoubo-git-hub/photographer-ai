import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { orderApi, OrderCreatePayload, OrderUpdatePayload } from '../api/order';
import type { OrderStatus } from '../types/models';
import { queryKeys } from './queryKeys';

/**
 * 订单列表 + 增删改/流转/分配 hooks（三端共用；UI 反馈由调用方处理）。
 */
export function useOrders(status?: OrderStatus, page = 0, size = 100) {
  return useQuery({
    queryKey: [...queryKeys.orders(status), page, size],
    queryFn: () => orderApi.list(status, page, size),
  });
}

/** 订单详情（含状态流转历史）。 */
export function useOrder(id: number, enabled = true) {
  return useQuery({
    queryKey: queryKeys.order(id),
    queryFn: () => orderApi.get(id),
    enabled,
  });
}

/** 使订单相关缓存失效（列表/详情/档期/配额）。 */
function useInvalidateOrders() {
  const qc = useQueryClient();
  return async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['orders'] }),
      qc.invalidateQueries({ queryKey: ['schedule'] }),
      qc.invalidateQueries({ queryKey: queryKeys.quota }),
    ]);
  };
}

export function useCreateOrder() {
  const invalidate = useInvalidateOrders();
  return useMutation({
    mutationFn: (data: OrderCreatePayload) => orderApi.create(data),
    onSuccess: () => invalidate(),
  });
}

export function useUpdateOrder() {
  const invalidate = useInvalidateOrders();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: OrderUpdatePayload }) =>
      orderApi.update(id, data),
    onSuccess: () => invalidate(),
  });
}

export function useDeleteOrder() {
  const invalidate = useInvalidateOrders();
  return useMutation({
    mutationFn: (id: number) => orderApi.remove(id),
    onSuccess: () => invalidate(),
  });
}

/** 状态流转（合法性由 domain/order.canTransition 预判 + 后端最终裁决）。 */
export function useChangeOrderStatus() {
  const invalidate = useInvalidateOrders();
  return useMutation({
    mutationFn: ({ id, toStatus }: { id: number; toStatus: OrderStatus }) =>
      orderApi.changeStatus(id, toStatus),
    onSuccess: () => invalidate(),
  });
}

/** B6 分配订单给团队成员（memberId 传 null 回退未分配）。 */
export function useAssignOrder() {
  const invalidate = useInvalidateOrders();
  return useMutation({
    mutationFn: ({ id, memberId }: { id: number; memberId: number | null }) =>
      orderApi.assign(id, memberId),
    onSuccess: () => invalidate(),
  });
}
