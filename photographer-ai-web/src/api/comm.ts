import { request } from './client';
import type { CommRequest, CommResponse } from '../types/models';

export const commApi = {
  generate: (req: CommRequest) =>
    request<CommResponse>({ url: '/ai/comm', method: 'POST', data: req }),
};
