import { describe, it, expect, vi, beforeAll, beforeEach } from 'vitest';
import { billingApi } from '../api/billing';
import { teamApi } from '../api/team';
import { dashboardApi } from '../api/dashboard';
import { calibrationApi } from '../api/quoteCalibration';

/**
 * 阶段3 前端 API 模块契约验证（批次 A/B/C/D）。
 * 校验各 api/* 模块正确拼装请求（url / method / data / params），
 * 不依赖网络：vi.mock('axios') 注入可控 client，捕获真实 client.request 调用参数。
 *
 * 注意：api 模块为静态 import，确保 vi.mock('axios') 工厂在模块加载期即生效，
 * 使 mocks.request 在 beforeAll 前已就绪（动态 import 会在测试内才触发工厂，
 * 导致 beforeAll 访问时 mocks.request 仍为 null）。
 */

const mocks = vi.hoisted(() => ({
  request: null as any,
  respErr: null as any,
}));

vi.mock('axios', () => {
  const request = vi.fn();
  mocks.request = request;
  const client = {
    request,
    interceptors: {
      request: { use: vi.fn() },
      // 与既有测试同构：捕获响应失败拦截器（本文件未直接断言，保留兼容）。
      response: { use: (_success: any, error: any) => { mocks.respErr = error; } },
    },
    defaults: {},
  };
  return { default: { create: () => client } };
});

describe('阶段3 API 模块契约（billing/team/dashboard/calibration）', () => {
  beforeAll(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, v),
      removeItem: (k: string) => void store.delete(k),
    });
    vi.stubGlobal('window', { location: { pathname: '/orders', href: '' } });
    mocks.request.mockResolvedValue({ data: { code: 0, data: null, message: 'ok' } });
  });

  // 每个用例前清空 spy 调用记录，避免跨用例累计导致误判。
  beforeEach(() => {
    if (mocks.request) mocks.request.mockClear();
  });

  it('billingApi: subscribe / mockPay / getSubscription / cancel 构造正确请求', async () => {
    await billingApi.subscribe({ planType: 'PRO', channel: 'MOCK' });
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/billing/subscribe', method: 'POST', data: { planType: 'PRO', channel: 'MOCK' } }),
    );

    await billingApi.mockPay('P20260724-1234');
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/billing/mock-pay', method: 'POST', data: { outTradeNo: 'P20260724-1234' } }),
    );

    await billingApi.getSubscription();
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/billing/subscription', method: 'GET' }),
    );

    await billingApi.cancel('不想续了');
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/billing/cancel', method: 'POST', data: { reason: '不想续了' } }),
    );

    await billingApi.cancel();
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/billing/cancel', method: 'POST', data: {} }),
    );
  });

  it('teamApi: invite / members / updateRole / remove / accept 构造正确请求', async () => {
    await teamApi.invite('a@b.com', 'MEMBER', '13800000000');
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/team/invite', method: 'POST', data: { email: 'a@b.com', role: 'MEMBER', phone: '13800000000' } }),
    );

    await teamApi.members();
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/team/members', method: 'GET' }),
    );

    await teamApi.updateRole(7, 'ADMIN');
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/team/members/7', method: 'PUT', data: { role: 'ADMIN' } }),
    );

    await teamApi.remove(7);
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/team/members/7', method: 'DELETE' }),
    );

    await teamApi.accept({ token: 'tok', username: 'u', password: 'p' });
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/team/accept', method: 'POST', data: { token: 'tok', username: 'u', password: 'p' } }),
    );
  });

  it('dashboardApi: overview / funnel / members 参数与时间窗透传', async () => {
    await dashboardApi.overview('2026-01-01', '2026-01-31');
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/dashboard/overview', params: { from: '2026-01-01', to: '2026-01-31' } }),
    );

    await dashboardApi.funnel();
    // 注：源码 funnel() 未显式写 method，axios 默认 GET，故仅断言 url（不强制 method）。
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/dashboard/funnel' }),
    );

    await dashboardApi.members();
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/dashboard/members', method: 'GET' }),
    );
  });

  it('calibrationApi: list / apply 构造正确请求', async () => {
    await calibrationApi.list();
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/ai/quote-calibration', method: 'GET' }),
    );

    await calibrationApi.apply(9);
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/ai/quote-calibration/apply', method: 'POST', data: { id: 9 } }),
    );
  });
});
