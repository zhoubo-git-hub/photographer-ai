import { request } from '../http';
import type { ReminderRule, ReminderRuleRequest } from '../types/models';

export const reminderRuleApi = {
  list: () => request<ReminderRule[]>({ url: '/reminder-rules' }),
  create: (data: ReminderRuleRequest) =>
    request<ReminderRule>({ url: '/reminder-rules', method: 'POST', data }),
  update: (id: number, data: ReminderRuleRequest) =>
    request<ReminderRule>({ url: `/reminder-rules/${id}`, method: 'PUT', data }),
  remove: (id: number) =>
    request<void>({ url: `/reminder-rules/${id}`, method: 'DELETE' }),
};
