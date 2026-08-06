import { describe, it, expect } from 'vitest';

/**
 * 免费额度阈值逻辑验证，镜像后端 `QuotaService`：
 *   - 免费版在管订单 ≤ 10（第 11 单被拦截，触发升级）
 *   - 免费版 AI 报价 5 次/月（第 6 次被拦截）
 *   - 专业版（PRO）订单与 AI 报价均无限
 * 在没有 JDK/Maven 的环境下独立运行，验证阈值与 PRD/架构一致。
 */

const FREE_ORDER_LIMIT = 10;
const FREE_AI_QUOTE_LIMIT = 5;
const UNLIMITED = 999;

class ForbiddenError extends Error {
  constructor(public message: string) {
    super(message);
  }
}

interface QuotaState {
  planType: 'FREE' | 'PRO';
  activeOrders: number;
  aiQuoteUsedMonth: number;
}

/** 镜像 QuotaService.ensureWithinLimit：建单前校验。 */
function ensureWithinLimit(q: QuotaState): void {
  if (q.planType === 'FREE' && q.activeOrders >= FREE_ORDER_LIMIT) {
    throw new ForbiddenError(`免费版在管订单已达 ${FREE_ORDER_LIMIT} 单上限，请升级专业版`);
  }
}

/** 镜像 QuotaService.checkAiQuoteLimit：AI 报价前校验。 */
function checkAiQuoteLimit(q: QuotaState): void {
  if (q.planType === 'FREE' && q.aiQuoteUsedMonth >= FREE_AI_QUOTE_LIMIT) {
    throw new ForbiddenError(`免费版本月 AI 报价已用满 ${FREE_AI_QUOTE_LIMIT} 次`);
  }
}

/** 镜像 QuotaService.getRemainingAiQuota。 */
function getRemainingAiQuota(q: QuotaState): number {
  if (q.planType !== 'FREE') return UNLIMITED;
  return Math.max(0, FREE_AI_QUOTE_LIMIT - q.aiQuoteUsedMonth);
}

describe('免费额度：订单数 ≤10 与 AI 报价 5 次/月', () => {
  it('免费版：已有 10 单时，第 11 单被拦截（触发升级）', () => {
    const q: QuotaState = { planType: 'FREE', activeOrders: 10, aiQuoteUsedMonth: 0 };
    expect(() => ensureWithinLimit(q)).toThrow(ForbiddenError);
  });

  it('免费版：已有 9 单时，第 10 单仍可创建（≤10 允许）', () => {
    const q: QuotaState = { planType: 'FREE', activeOrders: 9, aiQuoteUsedMonth: 0 };
    expect(() => ensureWithinLimit(q)).not.toThrow();
  });

  it('免费版：AI 报价第 6 次被拦截（5 次/月）', () => {
    const q: QuotaState = { planType: 'FREE', activeOrders: 0, aiQuoteUsedMonth: 5 };
    expect(() => checkAiQuoteLimit(q)).toThrow(ForbiddenError);
  });

  it('免费版：AI 报价第 5 次仍允许', () => {
    const q: QuotaState = { planType: 'FREE', activeOrders: 0, aiQuoteUsedMonth: 4 };
    expect(() => checkAiQuoteLimit(q)).not.toThrow();
  });

  it('免费版：剩余 AI 次数随使用递减', () => {
    expect(getRemainingAiQuota({ planType: 'FREE', activeOrders: 0, aiQuoteUsedMonth: 0 })).toBe(5);
    expect(getRemainingAiQuota({ planType: 'FREE', activeOrders: 0, aiQuoteUsedMonth: 3 })).toBe(2);
    expect(getRemainingAiQuota({ planType: 'FREE', activeOrders: 0, aiQuoteUsedMonth: 5 })).toBe(0);
  });

  it('专业版：订单与 AI 报价均无限（不拦截）', () => {
    const q: QuotaState = { planType: 'PRO', activeOrders: 999, aiQuoteUsedMonth: 999 };
    expect(() => ensureWithinLimit(q)).not.toThrow();
    expect(() => checkAiQuoteLimit(q)).not.toThrow();
    expect(getRemainingAiQuota(q)).toBe(UNLIMITED);
  });
});
