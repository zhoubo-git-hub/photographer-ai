import { request } from '../http';
import type { Customer, PageData } from '../types/models';

export interface CustomerCreatePayload {
  name: string;
  wechatId?: string;
  phone?: string;
  tags?: string;
  note?: string;
  lastShootDate?: string;
  repurchaseCycleDays?: number;
  birthday?: string;
  anniversary?: string;
  repurchaseEnabled?: boolean;
  sourceChannel?: string;
}

export interface CustomerUpdatePayload {
  name?: string;
  wechatId?: string;
  phone?: string;
  tags?: string;
  note?: string;
  lastShootDate?: string;
  repurchaseCycleDays?: number;
  birthday?: string;
  anniversary?: string;
  repurchaseEnabled?: boolean;
  sourceChannel?: string;
}

export const customerApi = {
  list: (keyword = '', page = 0, size = 50) =>
    request<PageData<Customer>>({
      url: '/customers',
      params: { keyword, page, size },
    }),
  get: (id: number) => request<Customer>({ url: `/customers/${id}` }),
  create: (data: CustomerCreatePayload) =>
    request<Customer>({ url: '/customers', method: 'POST', data }),
  update: (id: number, data: CustomerUpdatePayload) =>
    request<Customer>({ url: `/customers/${id}`, method: 'PUT', data }),
  remove: (id: number) =>
    request<void>({ url: `/customers/${id}`, method: 'DELETE' }),
};
