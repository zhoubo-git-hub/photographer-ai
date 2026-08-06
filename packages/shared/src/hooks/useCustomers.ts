import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  customerApi,
  CustomerCreatePayload,
  CustomerUpdatePayload,
} from '../api/customer';
import { queryKeys } from './queryKeys';

/**
 * 客户列表 + 增删改 hooks（三端共用）。
 */
export function useCustomers(keyword = '', page = 0, size = 50) {
  return useQuery({
    queryKey: [...queryKeys.customers(keyword), page, size],
    queryFn: () => customerApi.list(keyword, page, size),
  });
}

/** 客户详情（含订单历史）。 */
export function useCustomer(id: number, enabled = true) {
  return useQuery({
    queryKey: queryKeys.customer(id),
    queryFn: () => customerApi.get(id),
    enabled,
  });
}

function useInvalidateCustomers() {
  const qc = useQueryClient();
  return async () => {
    await qc.invalidateQueries({ queryKey: ['customers'] });
  };
}

export function useCreateCustomer() {
  const invalidate = useInvalidateCustomers();
  return useMutation({
    mutationFn: (data: CustomerCreatePayload) => customerApi.create(data),
    onSuccess: () => invalidate(),
  });
}

export function useUpdateCustomer() {
  const invalidate = useInvalidateCustomers();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: CustomerUpdatePayload }) =>
      customerApi.update(id, data),
    onSuccess: () => invalidate(),
  });
}

export function useDeleteCustomer() {
  const invalidate = useInvalidateCustomers();
  return useMutation({
    mutationFn: (id: number) => customerApi.remove(id),
    onSuccess: () => invalidate(),
  });
}
