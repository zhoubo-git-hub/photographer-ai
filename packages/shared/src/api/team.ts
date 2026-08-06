import { request } from '../http';
import type {
  AcceptInvitationRequest,
  AuthResponse,
  TeamMember,
  TeamRole,
} from '../types/models';

/**
 * 团队协作接口（阶段3 批次 B）。
 */
export const teamApi = {
  /** B1 邀请成员（返回含 token 的邀请记录）。 */
  invite: (email: string, role: TeamRole, phone?: string) =>
    request<TeamMember>({
      url: '/team/invite',
      method: 'POST',
      data: { email, role, phone },
    }),

  /** B2 成员列表。 */
  members: () => request<TeamMember[]>({ url: '/team/members', method: 'GET' }),

  /** B3 修改成员角色。 */
  updateRole: (id: number, role: TeamRole) =>
    request<TeamMember>({
      url: `/team/members/${id}`,
      method: 'PUT',
      data: { role },
    }),

  /** B4 移除成员（其名下订单回退未分配）。 */
  remove: (id: number) =>
    request<void>({ url: `/team/members/${id}`, method: 'DELETE' }),

  /** B5 接受邀请：凭 token 建用户并登录（匿名可访问）。 */
  accept: (data: AcceptInvitationRequest) =>
    request<AuthResponse>({ url: '/team/accept', method: 'POST', data }),
};
