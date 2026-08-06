import type { QuoteRequest, QuoteResponse } from '../types/models';
import type { OrderCreatePayload } from '../api/order';

/**
 * AI 报价纯领域逻辑（"一键填入订单"预填规则，与 web OrdersPage 行为一致，
 * 见 web __tests__/quoteFillOrder.test.ts 的断言口径）。
 */

/** 报价区间展示：'¥1200 - ¥1800'。 */
export function formatPriceRange(priceLow: number, priceHigh: number): string {
  return `¥${priceLow} - ¥${priceHigh}`;
}

/**
 * 由报价请求生成默认订单标题：'王小姐 的亲子拍摄订单'；
 * 无客户名时退化为 '亲子拍摄订单'。
 */
export function buildOrderTitle(req: QuoteRequest): string {
  const type = req.shootType || '';
  return req.customerName ? `${req.customerName} 的${type}拍摄订单` : `${type}拍摄订单`;
}

/** 新建订单表单可预填的字段子集（customerId 由页面选择客户后补全）。 */
export type OrderDraftFromQuote = Omit<OrderCreatePayload, 'customerId'>;

/**
 * 报价结果 → 新建订单预填草稿（一次性消费语义由调用方负责清空 pending 状态）：
 * - 标题：'{客户名} 的{拍摄类型}拍摄订单'
 * - 金额：取报价区间下限 priceLow
 * - 时长/张数/地区/风格：透传报价请求参数
 * - quoteSuggestion：报价依据 + 话术，落到订单留痕
 */
export function buildOrderDraftFromQuote(
  req: QuoteRequest,
  resp: QuoteResponse,
): OrderDraftFromQuote {
  return {
    title: buildOrderTitle(req),
    shootType: req.shootType,
    amount: resp.priceLow,
    durationHours: req.durationHours,
    photoCount: req.photoCount,
    region: req.region,
    style: req.style,
    quoteSuggestion: `${formatPriceRange(resp.priceLow, resp.priceHigh)}｜${resp.basis}｜${resp.script}`,
  };
}
