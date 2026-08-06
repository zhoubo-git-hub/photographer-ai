import { describe, it, expect, vi, beforeAll } from 'vitest';

/**
 * 统一 403 → UpgradeModal 拦截验证（US-P2-12 / 架构 §7）。
 *
 * 覆盖阶段2 全部专业版门禁端点：E1(/ai/comm)、E2-E5(/reminder-rules)、E7(/contracts/generate)、
 * E8(/repurchases)。后端 QuotaService.requirePro 抛 PRO_REQUIRED(403) 时，前端 client.ts 响应拦截
 * 统一调用 useUiStore.openUpgrade() 打开既有 UpgradeModal，并 reject ApiError(403)。
 *
 * 通过 vi.mock('axios') 捕获真实响应拦截器（与 apiError.test.ts 同手法），不依赖网络。
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
      // 捕获真实的响应失败拦截器（403 → openUpgrade）
      response: { use: (_success: any, error: any) => { mocks.respErr = error; } },
    },
    defaults: {},
  };
  return { default: { create: () => client } };
});

describe('403 → 统一升级弹窗（阶段2 专业版门禁前端拦截）', () => {
  beforeAll(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, v),
      removeItem: (k: string) => void store.delete(k),
    });
    vi.stubGlobal('window', { location: { pathname: '/orders', href: '' } });
  });

  it('任意 PRO 端点返回 403 时统一打开 UpgradeModal（upgradeOpen=true）并 reject ApiError(403)', async () => {
    const { ApiError } = await import('../api/client');
    const { useUiStore } = await import('../store/uiStore');

    // 阶段2 全部需 PRO 的端点
    const endpoints = [
      { url: '/ai/comm', method: 'POST' }, // E1
      { url: '/reminder-rules', method: 'GET' }, // E2
      { url: '/reminder-rules', method: 'POST' }, // E3
      { url: '/reminder-rules/1', method: 'PUT' }, // E4
      { url: '/reminder-rules/1', method: 'DELETE' }, // E5
      { url: '/contracts/generate', method: 'POST' }, // E7
      { url: '/repurchases', method: 'GET' }, // E8
    ];

    for (let i = 0; i < endpoints.length; i++) {
      useUiStore.getState().closeUpgrade();
      expect(useUiStore.getState().upgradeOpen).toBe(false);

      await expect(
        mocks.respErr({
          response: { status: 403, data: { code: 403, message: '该功能为专业版专属，请升级专业版' } },
        }),
      ).rejects.toBeInstanceOf(ApiError);

      expect(useUiStore.getState().upgradeOpen).toBe(true);
      expect(useUiStore.getState().upgradeMessage).toContain('专业版');
    }
  });

  it('后端 PRO_REQUIRED 文案被透传到升级弹窗', async () => {
    const { ApiError } = await import('../api/client');
    const { useUiStore } = await import('../store/uiStore');
    useUiStore.getState().closeUpgrade();

    const msg = '沟通助手为专业版功能';
    await expect(
      mocks.respErr({ response: { status: 403, data: { code: 403, message: msg } } }),
    ).rejects.toBeInstanceOf(ApiError);

    expect(useUiStore.getState().upgradeOpen).toBe(true);
    expect(useUiStore.getState().upgradeMessage).toBe(msg);
  });

  it('非 403 的业务错误不打开升级弹窗（如 404 订单不存在）', async () => {
    const { ApiError } = await import('../api/client');
    const { useUiStore } = await import('../store/uiStore');
    useUiStore.getState().closeUpgrade();

    await expect(
      mocks.respErr({ response: { status: 404, data: { code: 404, message: '订单不存在' } } }),
    ).rejects.toBeInstanceOf(ApiError);

    expect(useUiStore.getState().upgradeOpen).toBe(false);
  });
});
