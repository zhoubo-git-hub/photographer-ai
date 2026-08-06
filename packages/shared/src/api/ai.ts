import { request } from '../http';
import type {
  CommRequest,
  CommResponse,
  QuoteCalibration,
  QuoteCalibrationApplyRequest,
  QuoteRequest,
  QuoteResponse,
} from '../types/models';

export const aiApi = {
  quote: (data: QuoteRequest) =>
    request<QuoteResponse>({ url: '/ai/quote', method: 'POST', data }),
};

/** AI 沟通助手接口（阶段2）。 */
export const commApi = {
  generate: (req: CommRequest) =>
    request<CommResponse>({ url: '/ai/comm', method: 'POST', data: req }),
};

/**
 * AI 自学习报价校准接口（阶段3 批次 D，受限版）。
 */
export const calibrationApi = {
  /** D1 校准建议列表（首次触发懒扫描）。 */
  list: () =>
    request<QuoteCalibration[]>({ url: '/ai/quote-calibration', method: 'GET' }),

  /** D2 采纳某条建议，写回系数（安全边界外由后端拒绝）。 */
  apply: (id: number) =>
    request<QuoteCalibration>({
      url: '/ai/quote-calibration/apply',
      method: 'POST',
      data: { id } satisfies QuoteCalibrationApplyRequest,
    }),
};
