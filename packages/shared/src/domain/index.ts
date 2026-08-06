/**
 * 纯领域逻辑出口（无副作用、无平台依赖）：
 * - order.ts    ：状态机推导 / 金额与日期格式化
 * - quote.ts    ：报价区间展示 / "一键填入订单"草稿
 * - constants.ts：主题色 token / 错误码文案 / 状态机常量 re-export
 */

export * from './order';
export * from './quote';
export * from './constants';
