/**
 * REST 端点函数出口（三端共用，全部走平台无关 HttpClient）。
 * 覆盖：既有 web 全量端点 + 多端新增（wechat 登录 / push / storage）。
 */

export * from './auth';
export * from './order';
export * from './customer';
export * from './schedule';
export * from './ai';
export * from './quota';
export * from './subscription';
export * from './push';
export * from './storage';
export * from './reminder';
export * from './reminderRule';
export * from './repurchase';
export * from './contract';
export * from './dashboard';
export * from './team';
