import { request } from '../http';
import type { RepurchaseTask } from '../types/models';

/** 复购引擎接口：E8 查询当前工作室复购任务（PRO 专属）。 */
export const repurchaseApi = {
  list: () => request<RepurchaseTask[]>({ url: '/repurchases' }),
};
