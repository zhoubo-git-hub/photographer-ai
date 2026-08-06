import { useMutation } from '@tanstack/react-query';
import { aiApi } from '../api/ai';
import { useUiStore } from '../store/uiStore';
import type { QuoteRequest } from '../types/models';

/**
 * AI 报价 Hook：
 * - quote：发起报价 mutation（成功后把结果与请求参数写入 uiStore 待填状态，
 *   与 web AiQuoteForm onSuccess 行为一致）
 * - pendingQuote / pendingQuoteRequest：供"一键填入订单"消费
 * - consumePending：一次性消费待填数据（读取并清空，防"永远预填"）
 */
export function useQuote() {
  const pendingQuote = useUiStore((s) => s.pendingQuote);
  const pendingQuoteRequest = useUiStore((s) => s.pendingQuoteRequest);
  const setPendingQuote = useUiStore((s) => s.setPendingQuote);
  const setPendingQuoteRequest = useUiStore((s) => s.setPendingQuoteRequest);

  const quote = useMutation({
    mutationFn: (req: QuoteRequest) => aiApi.quote(req),
    onSuccess: (resp, req) => {
      setPendingQuote(resp);
      setPendingQuoteRequest(req);
    },
  });

  const consumePending = () => {
    const snapshot = {
      quote: useUiStore.getState().pendingQuote,
      request: useUiStore.getState().pendingQuoteRequest,
    };
    setPendingQuote(null);
    setPendingQuoteRequest(null);
    return snapshot;
  };

  return {
    quote,
    pendingQuote,
    pendingQuoteRequest,
    setPendingQuote,
    setPendingQuoteRequest,
    consumePending,
  };
}
