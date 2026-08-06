import type { OrderStatus } from '../types/models';

/**
 * TanStack Query 键统一定义（三端一致，避免各端手写字符串漂移）。
 */
export const queryKeys = {
  orders: (status?: OrderStatus) => ['orders', status ?? 'ALL'] as const,
  order: (id: number) => ['orders', 'detail', id] as const,
  customers: (keyword = '') => ['customers', keyword] as const,
  customer: (id: number) => ['customers', 'detail', id] as const,
  scheduleMonth: (year: number, month: number) => ['schedule', year, month] as const,
  quota: ['quota'] as const,
  subscription: ['subscription'] as const,
  reminders: ['reminders'] as const,
};
