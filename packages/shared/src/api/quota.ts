import { request } from '../http';
import type { QuotaInfo } from '../types/models';

export const quotaApi = {
  get: () => request<QuotaInfo>({ url: '/quota' }),
};
