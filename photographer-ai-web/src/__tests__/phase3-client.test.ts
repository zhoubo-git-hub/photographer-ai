import { describe, it, expect, vi, beforeAll } from 'vitest';

/**
 * 阶段3 client.ts 响应拦截分流验证（批次 A 收费墙 + 团队引导）。
 *
 * 覆盖：
 *  - 402 PAYMENT_REQUIRED → 置 expiredBanner + openUpgrade + 跳 /billing
 *  - 403 且 message 含"团队" → openUpgrade 透传团队文案（UpgradeModal 据此显示团队引导）
 *  - 403 普通 → openUpgrade 通用文案、不触发 expiredBanner、不跳 /billing
 *  - 状态码兜底（仅 status、无 data.code 也能分流）
 *
 * 复用既有手法：vi.mock('axios') 捕获真实响应失败拦截器（mocks.respErr）。
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
      // 捕获真实的响应失败拦截器
      response: { use: (_success: any, error: any) => { mocks.respErr = error; } },
    },
    defaults: {},
  };
  return { default: { create: () => client } };
});

describe('阶段3 client.ts 拦截分流（402/403/团队引导）', () => {
  beforeAll(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, v),
      removeItem: (k: string) => void store.delete(k),
    });
    vi.stubGlobal('window', { location: { pathname: '/orders', href: '' } });
  });

  it('402 PAYMENT_REQUIRED → expiredBanner=true + openUpgrade + 跳 /billing + reject ApiError(402)', async () => {
    const { ApiError } = await import('../api/client');
    const { useUiStore } = await import('../store/uiStore');
    useUiStore.getState().setExpiredBanner(false);
    useUiStore.getState().closeUpgrade();

    await expect(
      mocks.respErr({ response: { status: 402, data: { code: 402, message: '订阅已到期，请续费' } } }),
    ).rejects.toBeInstanceOf(ApiError);

    expect(useUiStore.getState().expiredBanner).toBe(true);
    expect(useUiStore.getState().upgradeOpen).toBe(true);
    expect((window as any).location.href).toBe('/billing');
  });

  it('403 且 message 含"团队" → openUpgrade 透传团队文案（Modal 据 message 显示团队引导），不触发 expiredBanner', async () => {
    const { ApiError } = await import('../api/client');
    const { useUiStore } = await import('../store/uiStore');
    useUiStore.getState().closeUpgrade();
    useUiStore.getState().setExpiredBanner(false);

    await expect(
      mocks.respErr({ response: { status: 403, data: { code: 403, message: '该功能需团队版' } } }),
    ).rejects.toBeInstanceOf(ApiError);

    expect(useUiStore.getState().upgradeOpen).toBe(true);
    expect(useUiStore.getState().upgradeMessage).toContain('团队');
    expect(useUiStore.getState().expiredBanner).toBe(false);
  });

  it('403 普通（无团队文案） → openUpgrade 通用文案、不触发 expiredBanner、不跳 /billing', async () => {
    const { ApiError } = await import('../api/client');
    const { useUiStore } = await import('../store/uiStore');
    useUiStore.getState().closeUpgrade();
    useUiStore.getState().setExpiredBanner(false);
    (window as any).location.href = '/orders';

    await expect(
      mocks.respErr({ response: { status: 403, data: { code: 403, message: '该功能为专业版专属，请升级专业版' } } }),
    ).rejects.toBeInstanceOf(ApiError);

    expect(useUiStore.getState().upgradeOpen).toBe(true);
    expect(useUiStore.getState().expiredBanner).toBe(false);
    expect((window as any).location.href).toBe('/orders');
  });

  it('402 仅靠 status（无 data.code）也能触发 expiredBanner + /billing', async () => {
    const { ApiError } = await import('../api/client');
    const { useUiStore } = await import('../store/uiStore');
    useUiStore.getState().setExpiredBanner(false);
    useUiStore.getState().closeUpgrade();
    (window as any).location.href = '/orders';

    await expect(mocks.respErr({ response: { status: 402 } })).rejects.toBeInstanceOf(ApiError);

    expect(useUiStore.getState().expiredBanner).toBe(true);
    expect((window as any).location.href).toBe('/billing');
  });

  it('403 仅靠 status（无 data）也走 openUpgrade', async () => {
    const { ApiError } = await import('../api/client');
    const { useUiStore } = await import('../store/uiStore');
    useUiStore.getState().closeUpgrade();

    await expect(mocks.respErr({ response: { status: 403 } })).rejects.toBeInstanceOf(ApiError);

    expect(useUiStore.getState().upgradeOpen).toBe(true);
  });
});
