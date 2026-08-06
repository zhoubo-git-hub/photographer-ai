import { create } from 'zustand';
import type { QuoteResponse, QuoteRequest } from '../types/models';

export type ToastSeverity = 'success' | 'error' | 'info' | 'warning';

interface UiState {
  toast: { message: string; severity: ToastSeverity } | null;
  showToast: (message: string, severity?: ToastSeverity) => void;
  clearToast: () => void;
  /** AI 报价结果暂存，用于"一键填入订单"。 */
  pendingQuote: QuoteResponse | null;
  setPendingQuote: (q: QuoteResponse | null) => void;
  /** AI 报价请求参数暂存，用于"一键填入订单"预填表单（拍摄类型/时长/张数/地区/风格/客户名）。 */
  pendingQuoteRequest: QuoteRequest | null;
  setPendingQuoteRequest: (q: QuoteRequest | null) => void;
  /** 升级专业版弹窗（由 403 统一触发）。 */
  upgradeOpen: boolean;
  upgradeMessage: string;
  openUpgrade: (message?: string) => void;
  closeUpgrade: () => void;
  /** 订阅到期黄条（402 触发，引导去订阅页续费）。 */
  expiredBanner: boolean;
  setExpiredBanner: (v: boolean) => void;
}

/**
 * UI 通用状态（三端共享逻辑；toast/弹窗的"渲染"由各端自行实现，
 * 这里只承载与业务语义绑定的状态与动作，与 web 版逐字一致）。
 */
export const useUiStore = create<UiState>((set) => ({
  toast: null,
  showToast: (message, severity = 'info') => set({ toast: { message, severity } }),
  clearToast: () => set({ toast: null }),
  pendingQuote: null,
  setPendingQuote: (q) => set({ pendingQuote: q }),
  pendingQuoteRequest: null,
  setPendingQuoteRequest: (q) => set({ pendingQuoteRequest: q }),
  upgradeOpen: false,
  upgradeMessage: '',
  openUpgrade: (message) => set({ upgradeOpen: true, upgradeMessage: message ?? '' }),
  closeUpgrade: () => set({ upgradeOpen: false }),
  expiredBanner: false,
  setExpiredBanner: (v) => set({ expiredBanner: v }),
}));
