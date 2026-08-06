// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, fireEvent, waitFor, within, cleanup } from '@testing-library/react';
import type { QuoteRequest, QuoteResponse } from '../types/models';

/**
 * 修复 A 回归测试：AI 报价「一键填入订单」端到端预填。
 *
 * 覆盖链路：
 *   AiQuoteForm 提交成功 → uiStore.pendingQuote / pendingQuoteRequest 被写入
 *   → OrdersPage 点击「新建订单」→ openCreate 读取待填数据预填表单
 *   → 预填后 pending 状态被清空（一次性消费，不污染下一次新建）。
 *
 * 环境说明：
 *   现有 phase3-ui.test.ts 走的是 react-dom/server 的 SSR 渲染，无法触发 effect / 事件 /
 *   useState 更新，因此无法验证"点击按钮后表单被预填"这类交互行为。本文件通过 docblock
 *   `@vitest-environment jsdom` 单文件切换到 jsdom（不改动 vitest.config.ts 的全局 node 环境），
 *   使用 React Testing Library 做真实挂载 + 真实点击，断言真实 DOM 与真实 Zustand store。
 *
 * 网络隔离（T5 后）：
 *   api 模块的唯一出口是 @photogai/shared/http 的 request（基于 axios，替代原 web 本地
 *   `../api/client`）。这里 mock 该模块、保留其全部真实导出（http / ApiError /
 *   configureHttpClient），仅用 mocks.request 按 url 分发假数据；源码抛出的 ApiError 即
 *   为真实 ApiError，无需自行构造。
 */

const mocks = vi.hoisted(() => ({ request: vi.fn() }));

// 网络隔离（T5 后）：api 模块的唯一出口是 @photogai/shared/http 的 request（基于 axios）。
// 仅用 mocks.request 按 url 分发假数据，其余导出（http / ApiError / configureHttpClient）保持真实。
vi.mock('@photogai/shared/http', async (importOriginal) => {
  const actual = (await importOriginal()) as Record<string, unknown>;
  return { ...actual, request: mocks.request };
});

import { useUiStore } from '../store/uiStore';
import AiQuoteForm from '../components/AiQuoteForm';
import OrdersPage from '../pages/OrdersPage';

const h = React.createElement;

/** AI 报价接口的固定返回，作为"一键填入订单"的数据源。 */
const QUOTE: QuoteResponse = {
  priceLow: 1200,
  priceHigh: 1800,
  basis: '上海·亲子·3小时·50张',
  script: '王小姐您好，这个套系建议 1200-1800 元。',
  remainingQuota: 7,
};

/** 与 AiQuoteForm 默认表单 + initialCustomerName 对应的提交参数。 */
const DEFAULT_QUOTE_REQ: QuoteRequest = {
  shootType: '婚纱写真',
  durationHours: 4,
  photoCount: 80,
  region: '上海',
  style: '轻奢',
  customerName: '王小姐',
};

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 200 };

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

/** 等待 OrdersPage 首屏就绪（两个列表查询落地）。 */
async function findNewOrderButton() {
  return screen.findByRole('button', { name: '新建订单' });
}

beforeEach(() => {
  // MUI 的部分组件在挂载期会读取 matchMedia，jsdom 未实现，需最小 polyfill。
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
    if (url === '/ai/quote') return Promise.resolve(QUOTE);
    if (url === '/orders') return Promise.resolve(EMPTY_PAGE);
    if (url === '/customers') return Promise.resolve(EMPTY_PAGE);
    if (url.startsWith('/reminders')) return Promise.resolve([]);
    return Promise.resolve(null);
  });

  // 每个用例都从"无待填数据"的干净状态开始。
  useUiStore.setState({ pendingQuote: null, pendingQuoteRequest: null, toast: null });
});

afterEach(() => {
  cleanup();
});

describe('修复A：AI 报价「一键填入订单」', () => {
  it('AiQuoteForm 报价成功后，把报价结果与请求参数写入 uiStore（pendingQuote / pendingQuoteRequest）', async () => {
    // Arrange：初始无待填数据
    expect(useUiStore.getState().pendingQuote).toBeNull();
    expect(useUiStore.getState().pendingQuoteRequest).toBeNull();
    renderWithProviders(h(AiQuoteForm, { initialCustomerName: '王小姐' }));

    // Act：点击生成报价
    fireEvent.click(screen.getByRole('button', { name: '生成报价' }));

    // Assert：onSuccess 同时落库 QuoteResponse 与提交时的 QuoteRequest
    await waitFor(() => {
      expect(useUiStore.getState().pendingQuote).not.toBeNull();
    });
    expect(useUiStore.getState().pendingQuote).toEqual(QUOTE);
    expect(useUiStore.getState().pendingQuoteRequest).toEqual(DEFAULT_QUOTE_REQ);

    // 结果卡片与「一键填入订单」入口应同时出现
    expect(document.body.textContent).toContain('¥1200');
    expect(document.body.textContent).toContain('¥1800');
    expect(screen.getByRole('button', { name: /一键填入订单/ })).toBeTruthy();

    // 请求确实按 QuoteRequest 契约发出
    expect(mocks.request).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/ai/quote', method: 'POST', data: DEFAULT_QUOTE_REQ }),
    );
  });

  it('AiQuoteForm 报价失败时不得写入待填数据，仅提示错误', async () => {
    mocks.request.mockImplementation(() => Promise.reject(new Error('额度不足')));
    renderWithProviders(h(AiQuoteForm, { initialCustomerName: '李先生' }));

    fireEvent.click(screen.getByRole('button', { name: '生成报价' }));

    await waitFor(() => {
      expect(useUiStore.getState().toast).not.toBeNull();
    });
    expect(useUiStore.getState().toast?.severity).toBe('error');
    expect(useUiStore.getState().pendingQuote).toBeNull();
    expect(useUiStore.getState().pendingQuoteRequest).toBeNull();
  });

  it('OrdersPage 存在待填数据时，点击「新建订单」应预填全部字段并清空待填状态', async () => {
    // Arrange：模拟"AI 报价页已生成报价"的遗留状态
    useUiStore.setState({
      pendingQuoteRequest: {
        shootType: '亲子',
        durationHours: 3,
        photoCount: 50,
        region: '北京',
        style: '复古',
        customerName: '王小姐',
      },
      pendingQuote: QUOTE,
    });

    renderWithProviders(h(OrdersPage));
    const btn = await findNewOrderButton();

    // Act
    fireEvent.click(btn);
    const dialog = await screen.findByRole('dialog');

    // Assert：文本类字段
    const title = within(dialog).getByLabelText('订单标题') as HTMLInputElement;
    expect(title.value).toBe('王小姐 的亲子拍摄订单');
    expect((within(dialog).getByLabelText('金额') as HTMLInputElement).value).toBe('1200');
    expect((within(dialog).getByLabelText('时长(h)') as HTMLInputElement).value).toBe('3');
    expect((within(dialog).getByLabelText('张数') as HTMLInputElement).value).toBe('50');
    expect((within(dialog).getByLabelText('地区') as HTMLInputElement).value).toBe('北京');
    expect((within(dialog).getByLabelText('风格') as HTMLInputElement).value).toBe('复古');

    // Assert：拍摄类型（MUI Select，取隐藏原生 input 的值）
    const nativeSelects = Array.from(
      dialog.querySelectorAll('input.MuiSelect-nativeInput'),
    ) as HTMLInputElement[];
    expect(nativeSelects.map((i) => i.value)).toContain('亲子');

    // Assert：金额取报价区间下限
    expect((within(dialog).getByLabelText('金额') as HTMLInputElement).value).toBe(
      String(QUOTE.priceLow),
    );

    // Assert：待填状态被一次性消费
    expect(useUiStore.getState().pendingQuoteRequest).toBeNull();
    expect(useUiStore.getState().pendingQuote).toBeNull();
  });

  it('OrdersPage 无待填数据时，「新建订单」应保持空白默认表单（防"永远预填"回归）', async () => {
    renderWithProviders(h(OrdersPage));
    fireEvent.click(await findNewOrderButton());
    const dialog = await screen.findByRole('dialog');

    expect((within(dialog).getByLabelText('订单标题') as HTMLInputElement).value).toBe('');
    expect((within(dialog).getByLabelText('金额') as HTMLInputElement).value).toBe('');
    expect((within(dialog).getByLabelText('时长(h)') as HTMLInputElement).value).toBe('');
    expect((within(dialog).getByLabelText('张数') as HTMLInputElement).value).toBe('');
    expect((within(dialog).getByLabelText('地区') as HTMLInputElement).value).toBe('上海');
    expect((within(dialog).getByLabelText('风格') as HTMLInputElement).value).toBe('轻奢');
  });

  it('端到端：AiQuoteForm 生成报价 → OrdersPage 新建订单表单被自动预填', async () => {
    // Act 1：在报价页生成报价
    const quoteView = renderWithProviders(h(AiQuoteForm, { initialCustomerName: '王小姐' }));
    fireEvent.click(screen.getByRole('button', { name: '生成报价' }));
    await waitFor(() => {
      expect(useUiStore.getState().pendingQuoteRequest).not.toBeNull();
    });
    quoteView.unmount();

    // Act 2：跳到订单页并新建订单
    renderWithProviders(h(OrdersPage));
    fireEvent.click(await findNewOrderButton());
    const dialog = await screen.findByRole('dialog');

    // Assert：预填来自报价表单的默认值（婚纱写真 / 4h / 80张 / 上海 / 轻奢 / 王小姐）
    expect((within(dialog).getByLabelText('订单标题') as HTMLInputElement).value).toBe(
      '王小姐 的婚纱写真拍摄订单',
    );
    expect((within(dialog).getByLabelText('时长(h)') as HTMLInputElement).value).toBe('4');
    expect((within(dialog).getByLabelText('张数') as HTMLInputElement).value).toBe('80');
    expect((within(dialog).getByLabelText('地区') as HTMLInputElement).value).toBe('上海');
    expect((within(dialog).getByLabelText('风格') as HTMLInputElement).value).toBe('轻奢');
    expect((within(dialog).getByLabelText('金额') as HTMLInputElement).value).toBe('1200');

    const nativeSelects = Array.from(
      dialog.querySelectorAll('input.MuiSelect-nativeInput'),
    ) as HTMLInputElement[];
    expect(nativeSelects.map((i) => i.value)).toContain('婚纱写真');

    expect(useUiStore.getState().pendingQuote).toBeNull();
    expect(useUiStore.getState().pendingQuoteRequest).toBeNull();
  });
});
