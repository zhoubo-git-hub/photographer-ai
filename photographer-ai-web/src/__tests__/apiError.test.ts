import { describe, it, expect, vi, beforeAll } from 'vitest';

/**
 * 统一响应与前端错误拦截验证（架构 §7 约定）：
 *   - Result 结构 {code,data,message}，code=0 成功，code>0 业务错误；
 *   - 前端 ApiError 携带语义错误码（400/401/403/404/409/500）；
 *   - 响应拦截：非 0 code 统一抛 ApiError；401 清空 token 并跳转 /login。
 *
 * 通过 vi.mock('axios') 注入可控的 axios 实例，并捕获真实的响应拦截器
 * （成功/失败 handler），从而在不依赖网络的情况下测试 client.ts 的真实逻辑。
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
      // 捕获真实的响应失败拦截器（401 清 token / 非 0 code 抛 ApiError）
      response: { use: (_success: any, error: any) => { mocks.respErr = error; } },
    },
    defaults: {},
  };
  return { default: { create: () => client } };
});

describe('ApiError 与统一响应拦截', () => {
  it('ApiError 正确携带错误码与信息', async () => {
    const { ApiError } = await import('../api/client');
    const e = new ApiError(409, '档期冲突');
    expect(e).toBeInstanceOf(Error);
    expect(e.name).toBe('ApiError');
    expect(e.code).toBe(409);
    expect(e.message).toBe('档期冲突');
  });

  it('request() 在 code=0 时解包返回 data', async () => {
    const { request } = await import('../api/client');
    mocks.request.mockResolvedValue({
      data: { code: 0, data: { id: 1, title: '测试' }, message: 'ok' },
    });
    const data = await request<{ id: number; title: string }>({ url: '/orders' });
    expect(data).toEqual({ id: 1, title: '测试' });
  });

  it('request() 在 code!=0（如 409）时抛出 ApiError', async () => {
    const { request, ApiError } = await import('../api/client');
    mocks.request.mockResolvedValue({
      data: { code: 409, data: null, message: '档期冲突' },
    });
    await expect(request({ url: '/orders' })).rejects.toBeInstanceOf(ApiError);
    try {
      await request({ url: '/orders' });
    } catch (e: any) {
      expect(e.code).toBe(409);
      expect(e.message).toBe('档期冲突');
    }
  });

  describe('401 拦截：清空 token 并跳转 /login', () => {
    beforeAll(() => {
      const store = new Map<string, string>();
      vi.stubGlobal('localStorage', {
        getItem: (k: string) => store.get(k) ?? null,
        setItem: (k: string, v: string) => void store.set(k, v),
        removeItem: (k: string) => void store.delete(k),
      });
      vi.stubGlobal('window', { location: { pathname: '/orders', href: '' } });
    });

    it('响应拦截器收到 401 时清空 token 并跳转 /login', async () => {
      const { ApiError } = await import('../api/client');
      const { useAuthStore } = await import('../store/authStore');
      useAuthStore.getState().setAuth({
        token: 'fake-token',
        user: { id: 1, studioId: 1, username: 'u', role: 'OWNER' },
        studio: { id: 1, name: 's', planType: 'FREE' },
      });
      expect(useAuthStore.getState().token).toBe('fake-token');

      // 直接调用被捕获的真实响应失败拦截器
      await expect(
        mocks.respErr({
          response: { status: 401, data: { code: 401, message: '未登录或登录已过期' } },
        }),
      ).rejects.toBeInstanceOf(ApiError);

      expect(useAuthStore.getState().token).toBeNull();
      expect((window as any).location.href).toBe('/login');
    });

    it('响应拦截器收到非 0 code（如 403）时抛出对应 ApiError', async () => {
      const { ApiError } = await import('../api/client');
      await expect(
        mocks.respErr({
          response: { status: 403, data: { code: 403, message: '额度不足' } },
        }),
      ).rejects.toBeInstanceOf(ApiError);
      try {
        await mocks.respErr({
          response: { status: 403, data: { code: 403, message: '额度不足' } },
        });
      } catch (e: any) {
        expect(e.code).toBe(403);
      }
    });
  });
});
