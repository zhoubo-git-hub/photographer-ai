import { describe, it, expect, vi, beforeAll } from 'vitest';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

/**
 * 阶段3 UI 组件冒烟 + 关键行为/文案验证（批次 A/B/C/D）。
 *
 * 不依赖浏览器：用 react-dom/server 的 renderToStaticMarkup 在 node 环境直接渲染组件，
 * 配合 MemoryRouter（路由）与 QueryClientProvider（TanStack Query），并用 setQueryData
 * 注入 mock 数据，校验"组件渲染不抛错 + 关键文案/状态正确"。
 *
 * 关于 useAuth 的 mock：
 *  Zustand 的 React 适配在 SSR（renderToStaticMarkup）下，useSyncExternalStore 取的是
 *  getServerSnapshot → getInitialState()，即 store 创建时的初始态（studio=null），
 *  运行期 setAuth 不会反映到 SSR 渲染结果。因此在单测里直接 mock useAuth 返回受控的
 *  studio，才是稳定、可断言的做法（组件自身的套餐分支逻辑仍是真实代码）。
 *
 * 说明：
 *  - recharts 在 SSR 下需 ResizeObserver/DOM，整体 mock 以隔离图表、验证业务数据/文案。
 */

// recharts 在 SSR 下需 ResizeObserver/DOM，整体 mock 以隔离图表、验证业务数据/文案。
vi.mock('recharts', () => ({
  LineChart: () => null,
  Line: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  ResponsiveContainer: ({ children }: any) => children ?? null,
  BarChart: () => null,
  Bar: () => null,
  Cell: () => null,
}));

// 受控的认证态：测试里直接控制 studio.planType，绕过 Zustand SSR 快照限制。
const h = vi.hoisted(() => ({
  auth: {
    token: 't',
    user: { id: 1, studioId: 1, username: 'u', role: 'OWNER' } as any,
    studio: { id: 1, name: 's', planType: 'FREE' } as any,
  },
}));

vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    user: h.auth.user,
    studio: h.auth.studio,
    setAuth: () => {},
    logout: () => {},
  }),
}));

let C: any;

beforeAll(async () => {
  const store = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (k: string) => store.get(k) ?? null,
    setItem: (k: string, v: string) => void store.set(k, v),
    removeItem: (k: string) => void store.delete(k),
  });
  vi.stubGlobal('window', { location: { pathname: '/', href: '' } });

  const billing = (await import('../pages/BillingPage')).default;
  const team = (await import('../pages/TeamPage')).default;
  const dashboard = (await import('../pages/DashboardPage')).default;
  const calibration = (await import('../pages/QuoteCalibrationPanel')).default;
  const sidebar = (await import('../layout/SideBar')).default;
  const topbar = (await import('../layout/TopBar')).default;
  const upgrade = (await import('../components/UpgradeModal')).default;

  C = { billing, team, dashboard, calibration, sidebar, topbar, upgrade };
});

function setup(plan: string, queryData?: Record<string, any>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  if (queryData) {
    for (const [k, v] of Object.entries(queryData)) client.setQueryData([k], v);
  }
  // 受控套餐：直接改同一引用上的 planType（mock useAuth 实时读取）。
  h.auth.studio.planType = plan;
  return client;
}

function render(node: React.ReactElement, plan: string, queryData?: Record<string, any>) {
  const client = setup(plan, queryData);
  return renderToStaticMarkup(
    React.createElement(
      QueryClientProvider,
      { client } as any,
      React.createElement(MemoryRouter, null, node),
    ),
  );
}

describe('阶段3 UI 组件渲染与关键文案/状态', () => {
  // ===== 批次 A：BillingPage 套餐定价 + UpgradeModal 团队引导 =====
  it('BillingPage 展示 PRO ¥39 / TEAM ¥99 两个套餐', () => {
    const html = render(React.createElement(C.billing), 'FREE');
    expect(html).toContain('¥39');
    expect(html).toContain('¥99');
    expect(html).toContain('专业版');
    expect(html).toContain('团队版');
  });

  // 说明：UpgradeModal 基于 MUI Dialog/Modal，其内容需 mount effect 后才渲染，
  // 而 renderToStaticMarkup（SSR）不会触发 effect，故无法在此校验弹窗内文案。
  // 升级弹窗的"触发路径"（402/403 → openUpgrade(message) + 跳 /billing）已由
  // phase3-client.test.ts 覆盖；弹窗内文案分支（团队/续费/专业版）建议补充 jsdom 用例。


  // ===== 批次 B：TeamPage 套餐级可见性 =====
  it('TeamPage：FREE 套餐显示升级引导；TEAM 套餐显示邀请表单', () => {
    const freeHtml = render(React.createElement(C.team), 'FREE');
    expect(freeHtml).toContain('团队协作需要');
    expect(freeHtml).toContain('团队版');

    const teamHtml = render(React.createElement(C.team), 'TEAM');
    expect(teamHtml).toContain('邀请新成员');
  });

  // ===== 批次 A/C：TopBar 套餐标签 =====
  it('TopBar 套餐标签：PRO→专业版 / TEAM→团队版 / FREE→免费版', () => {
    expect(render(React.createElement(C.topbar), 'PRO')).toContain('专业版');
    expect(render(React.createElement(C.topbar), 'TEAM')).toContain('团队版');
    expect(render(React.createElement(C.topbar), 'FREE')).toContain('免费版');
  });

  // ===== 套餐级角标：SideBar =====
  it('SideBar 角标：FREE 显示 PRO/TEAM 角标；TEAM 不显示锁定角标', () => {
    const freeHtml = render(React.createElement(C.sidebar), 'FREE');
    expect(freeHtml).toContain('PRO');
    expect(freeHtml).toContain('TEAM');

    const teamHtml = render(React.createElement(C.sidebar), 'TEAM');
    // TEAM 全部解锁，不应再有 PRO / TEAM 锁定角标
    expect(teamHtml).not.toContain('>PRO<');
    expect(teamHtml).not.toContain('>TEAM<');
  });

  // ===== 批次 D：QuoteCalibrationPanel 边界 UI =====
  it('QuoteCalibrationPanel：FREE 显示专业版引导', () => {
    const html = render(React.createElement(C.calibration), 'FREE');
    // 注意：'专业版' 在源码中被 <strong> 包裹，故分段断言，避免依赖连续字符串。
    expect(html).toContain('AI 自学习报价校准为');
    expect(html).toContain('专业版');
    expect(html).toContain('能力');
    expect(html).toContain('升级专业版');
  });

  it('QuoteCalibrationPanel：PRO 下，样本不足/越界 → "采纳"按钮禁用 + 安全边界文案', () => {
    const list = [
      {
        id: 1, dimensionKey: '上海|婚纱写真', dimensionLabel: '上海·婚纱写真',
        sampleCount: 5, currentCoef: 1, suggestedCoef: 1.08, offsetPct: 8,
        withinBoundary: false, status: 'PENDING',
      },
      {
        id: 2, dimensionKey: '北京|亲子', dimensionLabel: '北京·亲子',
        sampleCount: 24, currentCoef: 1, suggestedCoef: 0.95, offsetPct: -5,
        withinBoundary: true, status: 'PENDING',
      },
    ];
    const html = render(React.createElement(C.calibration), 'PRO', { calibration: list });

    // 边界内项
    expect(html).toContain('边界内');
    // 越界/样本不足项
    expect(html).toContain('超出安全边界');
    expect(html).toContain('样本不足');
    // 样本不足项"采纳"按钮应被禁用
    expect(html).toContain('disabled');
  });

  // ===== 批次 C：DashboardPage 数据形状 + 转化率展示 =====
  it('DashboardPage：概览/漏斗渲染不抛错，关键指标与转化率显示合理', () => {
    const overview = {
      revenue: 12345,
      orderCount: 5,
      aov: 2469,
      repurchaseRate: 0.2,
      conversion: { consult: 50, deposit: 32, shoot: 28, deliver: 26 },
      revenuePoints: [],
    };
    const funnel = {
      stages: [
        { status: 'CONSULT', count: 50, rate: 1 },
        { status: 'DEPOSIT', count: 32, rate: 0.64 },
        { status: 'SHOOT', count: 28, rate: 0.56 },
        { status: 'EDIT', count: 27, rate: 0.54 },
        { status: 'DELIVER', count: 26, rate: 0.52 },
      ],
    };
    let html = '';
    expect(() => {
      html = render(React.createElement(C.dashboard), 'PRO', {
        'dashboard-overview': overview,
        'dashboard-funnel': funnel,
      });
    }).not.toThrow();

    expect(html).toContain('总收入');
    expect(html).toContain('¥12345');
    expect(html).toContain('转化漏斗');
    expect(html).toContain('咨询');

    // 转化率应显示为合理百分比（如 64%），不应把"到达数(32)"当比率渲染成 3200%
    expect(html).not.toMatch(/3200%|5000%|2800%|2600%/);
  });

  it('DashboardPage：TEAM 套餐显示成员业绩拆分', () => {
    const members = [{ memberId: 2, name: '小李', orderCount: 3, revenue: 3000, aov: 1000 }];
    const html = render(React.createElement(C.dashboard), 'TEAM', {
      'dashboard-members': members,
    });
    expect(html).toContain('成员业绩拆分');
    expect(html).toContain('小李');
  });
});
