// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  render,
  renderHook,
  screen,
  fireEvent,
  waitFor,
  act,
  cleanup,
} from '@testing-library/react';
import type { AuthResponse, PlanType } from '../types/models';

/**
 * 修复 B 回归测试：模拟付费成功后套餐回写（原缺陷：付费成功但菜单仍灰、右上角仍"免费版"）。
 *
 * 覆盖链路：
 *   authStore.setStudioPlanType 局部更新 studio.planType
 *   → useAuth 暴露该 action
 *   → BillingPage.mockPayMutation.onSuccess 用 pendingPlanRef 调用 setStudioPlanType(PRO/TEAM)
 *   → SideBar 锁定项解锁（opacity 0.55 → 1、PRO 角标消失）+ TopBar 徽章变「专业版」。
 *
 * 环境说明：与 quoteFillOrder.test.ts 同，用 docblock 单文件切到 jsdom，做真实挂载 + 真实点击，
 * 不修改 vitest.config.ts 的全局 node 环境。这里刻意**不 mock useAuth**（与 phase3-ui.test.ts
 * 的受控 mock 不同），因为本用例要验证的正是"真实 store 回写 → 真实组件重渲染"这条链路。
 */

const mocks = vi.hoisted(() => ({ request: vi.fn() }));

vi.mock('../api/client', () => {
  class ApiError extends Error {
    code: number;
    constructor(code: number, message: string) {
      super(message);
      this.code = code;
      this.name = 'ApiError';
    }
  }
  return { ApiError, request: mocks.request, default: {} };
});

import { useAuthStore } from '../store/authStore';
import { useUiStore } from '../store/uiStore';
import { useAuth } from '../hooks/useAuth';
import BillingPage from '../pages/BillingPage';
import SideBar from '../layout/SideBar';
import TopBar from '../layout/TopBar';

const h = React.createElement;

/** 真实的 setStudioPlanType 实现（用于 spy 包装时调用透传）。 */
const realSetStudioPlanType = useAuthStore.getState().setStudioPlanType;

const AUTH: AuthResponse = {
  token: 'test-token',
  user: { id: 1, studioId: 1, username: 'lin', role: 'OWNER' },
  studio: { id: 1, name: '林记摄影', planType: 'FREE' },
};

const OUT_TRADE_NO = 'P20260731-0001';

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
  });
}

function renderWithProviders(node: React.ReactElement) {
  return render(
    h(QueryClientProvider, { client: makeClient() } as any, h(MemoryRouter, null, node)),
  );
}

/** 用 spy 包装 store 上的 setStudioPlanType（保留真实行为，便于同时断言"被调用"与"已回写"）。 */
function spyOnSetStudioPlanType() {
  const spy = vi.fn((plan: PlanType) => realSetStudioPlanType(plan));
  useAuthStore.setState({ setStudioPlanType: spy });
  return spy;
}

/** 读取导航项（NavLink 渲染为 <a>）的计算 opacity。 */
function navOpacity(label: string): string {
  const anchor = screen.getByText(label).closest('a');
  expect(anchor).toBeTruthy();
  return window.getComputedStyle(anchor as Element).opacity;
}

beforeEach(() => {
  if (!window.matchMedia) {
    window.matchMedia = ((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    })) as any;
  }

  mocks.request.mockReset();
  mocks.request.mockImplementation((cfg: any) => {
    const url: string = cfg?.url ?? '';
    if (url === '/billing/subscribe') {
      return Promise.resolve({ outTradeNo: OUT_TRADE_NO, amount: 39 });
    }
    if (url === '/billing/mock-pay') return Promise.resolve({ planType: 'PRO', status: 'ACTIVE' });
    if (url === '/billing/subscription') return Promise.resolve(null);
    if (url.startsWith('/reminders')) return Promise.resolve([]);
    return Promise.resolve(null);
  });

  // 还原真实 action，再灌入干净登录态（FREE）。
  useAuthStore.setState({ setStudioPlanType: realSetStudioPlanType });
  useAuthStore.getState().setAuth({ ...AUTH, studio: { ...AUTH.studio } });
  useUiStore.setState({ toast: null, expiredBanner: false, upgradeOpen: false });
});

afterEach(() => {
  cleanup();
});

describe('修复B-1：authStore / useAuth 的 setStudioPlanType', () => {
  it('局部更新 studio.planType，保留工作室其它字段与 token / user', () => {
    expect(useAuthStore.getState().studio?.planType).toBe('FREE');

    useAuthStore.getState().setStudioPlanType('PRO');

    const s = useAuthStore.getState();
    expect(s.studio).toEqual({ id: 1, name: '林记摄影', planType: 'PRO' });
    expect(s.token).toBe('test-token');
    expect(s.user?.username).toBe('lin');
  });

  it('未登录（studio 为 null）时调用不抛错且保持 null', () => {
    useAuthStore.getState().logout();
    expect(() => useAuthStore.getState().setStudioPlanType('TEAM')).not.toThrow();
    expect(useAuthStore.getState().studio).toBeNull();
  });

  it('useAuth 暴露 setStudioPlanType，且作用于真实 authStore', () => {
    const { result } = renderHook(() => useAuth());
    expect(typeof result.current.setStudioPlanType).toBe('function');

    act(() => result.current.setStudioPlanType('TEAM'));

    expect(useAuthStore.getState().studio?.planType).toBe('TEAM');
    expect(result.current.studio?.planType).toBe('TEAM');
  });
});

describe('修复B-2：BillingPage 模拟支付成功后回写套餐', () => {
  it('升级专业版 → 模拟支付成功 → setStudioPlanType("PRO") 被调用且 store 已回写', async () => {
    const spy = spyOnSetStudioPlanType();
    renderWithProviders(h(BillingPage));

    // Act 1：FREE 状态下两张卡片都是「立即升级」，索引 0 = 专业版
    const upgradeButtons = await screen.findAllByRole('button', { name: '立即升级' });
    expect(upgradeButtons.length).toBe(2);
    fireEvent.click(upgradeButtons[0]);

    // Act 2：下单成功后弹出支付演示弹窗
    await screen.findByText('完成支付（演示）');
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: '/billing/subscribe',
        method: 'POST',
        data: { planType: 'PRO', channel: 'MOCK' },
      }),
    );

    // Act 3：模拟支付成功
    fireEvent.click(screen.getByRole('button', { name: '模拟支付成功' }));

    // Assert：回写动作被触发，参数为下单时选择的套餐
    await waitFor(() => expect(spy).toHaveBeenCalled());
    expect(spy).toHaveBeenCalledTimes(1);
    expect(spy).toHaveBeenCalledWith('PRO');
    expect(useAuthStore.getState().studio?.planType).toBe('PRO');

    // 支付请求携带下单返回的商户单号
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({
        url: '/billing/mock-pay',
        method: 'POST',
        data: { outTradeNo: OUT_TRADE_NO },
      }),
    );
  });

  it('升级团队版 → 模拟支付成功 → setStudioPlanType("TEAM")（pendingPlanRef 跟随所选套餐）', async () => {
    const spy = spyOnSetStudioPlanType();
    renderWithProviders(h(BillingPage));

    const upgradeButtons = await screen.findAllByRole('button', { name: '立即升级' });
    fireEvent.click(upgradeButtons[1]); // 索引 1 = 团队版

    await screen.findByText('完成支付（演示）');
    fireEvent.click(screen.getByRole('button', { name: '模拟支付成功' }));

    await waitFor(() => expect(spy).toHaveBeenCalled());
    expect(spy).toHaveBeenCalledWith('TEAM');
    expect(useAuthStore.getState().studio?.planType).toBe('TEAM');
  });

  it('支付失败时不得回写套餐（仍为 FREE）', async () => {
    const spy = spyOnSetStudioPlanType();
    mocks.request.mockImplementation((cfg: any) => {
      const url: string = cfg?.url ?? '';
      if (url === '/billing/subscribe') {
        return Promise.resolve({ outTradeNo: OUT_TRADE_NO, amount: 39 });
      }
      if (url === '/billing/mock-pay') return Promise.reject(new Error('支付通道异常'));
      return Promise.resolve(null);
    });

    renderWithProviders(h(BillingPage));
    fireEvent.click((await screen.findAllByRole('button', { name: '立即升级' }))[0]);
    await screen.findByText('完成支付（演示）');
    fireEvent.click(screen.getByRole('button', { name: '模拟支付成功' }));

    await waitFor(() => expect(useUiStore.getState().toast?.severity).toBe('error'));
    expect(spy).not.toHaveBeenCalled();
    expect(useAuthStore.getState().studio?.planType).toBe('FREE');
  });
});

describe('修复B-3：套餐回写后 SideBar 解锁 / TopBar 徽章刷新', () => {
  it('planType 由 FREE 变为 PRO 后：专业版菜单解锁、PRO 角标消失、右上角显示「专业版」', async () => {
    renderWithProviders(h(React.Fragment, null, h(TopBar), h(SideBar)));

    // Assert 初始（FREE）：菜单灰显 + PRO 角标 + 免费版徽章
    expect(screen.getByText('免费版')).toBeTruthy();
    expect(navOpacity('数据看板')).toBe('0.55');
    expect(navOpacity('合同生成')).toBe('0.55');
    expect(screen.getAllByText('PRO').length).toBeGreaterThan(0);
    // 不受套餐限制的菜单本来就不灰
    expect(navOpacity('订单看板')).toBe('1');

    // Act：模拟"支付成功回写"
    act(() => {
      useAuthStore.getState().setStudioPlanType('PRO');
    });

    // Assert：解锁 + 徽章刷新
    await waitFor(() => expect(screen.getByText('专业版')).toBeTruthy());
    expect(screen.queryByText('免费版')).toBeNull();
    expect(navOpacity('数据看板')).toBe('1');
    expect(navOpacity('合同生成')).toBe('1');
    expect(navOpacity('提醒规则')).toBe('1');
    expect(navOpacity('复购引擎')).toBe('1');
    expect(screen.queryAllByText('PRO').length).toBe(0);

    // 团队版专属仍然锁定（PRO 不应越权解锁 TEAM 功能）
    expect(navOpacity('团队协作')).toBe('0.55');
    expect(screen.getAllByText('TEAM').length).toBeGreaterThan(0);
  });

  it('planType 变为 TEAM 后：团队协作解锁，右上角显示「团队版」', async () => {
    renderWithProviders(h(React.Fragment, null, h(TopBar), h(SideBar)));
    expect(navOpacity('团队协作')).toBe('0.55');

    act(() => {
      useAuthStore.getState().setStudioPlanType('TEAM');
    });

    await waitFor(() => expect(screen.getByText('团队版')).toBeTruthy());
    expect(navOpacity('团队协作')).toBe('1');
    expect(navOpacity('数据看板')).toBe('1');
    expect(screen.queryAllByText('TEAM').length).toBe(0);
  });
});
