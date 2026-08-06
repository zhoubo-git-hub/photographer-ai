import { request } from '../http';
import type { FunnelDTO, MemberPerfDTO, OverviewDTO } from '../types/models';

/**
 * 经营看板接口（阶段3 批次 C）。时间窗 from/to 为 ISO 字符串（默认近 30 天）。
 */
export const dashboardApi = {
  /** C1 概览：收入/订单/客单价/复购/趋势。 */
  overview: (from?: string, to?: string) =>
    request<OverviewDTO>({ url: '/dashboard/overview', params: { from, to } }),

  /** C2 转化漏斗。 */
  funnel: (from?: string, to?: string) =>
    request<FunnelDTO>({ url: '/dashboard/funnel', params: { from, to } }),

  /** C3 成员业绩拆分（团队版）。 */
  members: () => request<MemberPerfDTO[]>({ url: '/dashboard/members', method: 'GET' }),
};
