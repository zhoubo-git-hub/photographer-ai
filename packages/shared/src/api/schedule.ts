import { request } from '../http';
import type { ScheduleItem } from '../types/models';

export const scheduleApi = {
  month: (year: number, month: number) =>
    request<ScheduleItem[]>({
      url: '/schedule/month',
      params: { year, month },
    }),
};
