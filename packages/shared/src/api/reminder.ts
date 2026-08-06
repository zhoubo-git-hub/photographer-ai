import { request } from '../http';
import type { Reminder, ReminderStatus } from '../types/models';

export const reminderApi = {
  list: (status?: ReminderStatus, dueOnly = false) =>
    request<Reminder[]>({ url: '/reminders', params: { status, dueOnly } }),
  updateStatus: (id: number, status: ReminderStatus) =>
    request<Reminder>({ url: `/reminders/${id}`, method: 'PUT', params: { status } }),
};
