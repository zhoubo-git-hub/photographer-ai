import { request } from '../http';
import type { Conflict, Customer, Order, OrderStatus, PageData } from '../types/models';

export interface OrderCreatePayload {
  customerId: number;
  title: string;
  shootType?: string;
  status?: OrderStatus;
  amount?: number;
  depositAmount?: number;
  currency?: string;
  shootDate?: string;
  shootEndDate?: string;
  durationHours?: number;
  photoCount?: number;
  region?: string;
  style?: string;
  quoteSuggestion?: string;
}

export interface OrderUpdatePayload {
  title?: string;
  shootType?: string;
  amount?: number;
  depositAmount?: number;
  currency?: string;
  shootDate?: string;
  shootEndDate?: string;
  durationHours?: number;
  photoCount?: number;
  region?: string;
  style?: string;
  quoteSuggestion?: string;
}

export const orderApi = {
  list: (status?: OrderStatus, page = 0, size = 100) =>
    request<PageData<Order>>({
      url: '/orders',
      params: { status, page, size },
    }),
  get: (id: number) => request<Order>({ url: `/orders/${id}` }),
  create: (data: OrderCreatePayload) =>
    request<Order>({ url: '/orders', method: 'POST', data }),
  update: (id: number, data: OrderUpdatePayload) =>
    request<Order>({ url: `/orders/${id}`, method: 'PUT', data }),
  remove: (id: number) =>
    request<void>({ url: `/orders/${id}`, method: 'DELETE' }),
  changeStatus: (id: number, toStatus: OrderStatus) =>
    request<Order>({
      url: `/orders/${id}/status`,
      method: 'POST',
      data: { toStatus },
    }),
  // B6 分配订单给团队成员（团队版；memberId 传 null 回退未分配）
  assign: (id: number, memberId: number | null) =>
    request<Order>({
      url: `/orders/${id}/assign`,
      method: 'POST',
      data: { memberId },
    }),
  conflict: (shootDate?: string, shootEndDate?: string, excludeOrderId?: number) =>
    request<Conflict[]>({
      url: '/orders/conflict',
      params: { shootDate, shootEndDate, excludeOrderId },
    }),
};

export type { Customer };
