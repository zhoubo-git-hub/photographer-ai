/**
 * @photogai/shared 统一出口。
 *
 * 三端（Web / RN App / Taro 小程序）唯一真源：
 * - types  : 领域类型（与后端 DTO 镜像）
 * - http   : 平台无关 HttpClient + ApiError（T4 填充）
 * - api    : REST 端点函数（T4 填充）
 * - domain : 纯领域逻辑与常量（T4 填充）
 * - store  : Zustand store + StorageAdapter 抽象（T4 填充）
 * - hooks  : TanStack Query hooks（T4 填充）
 *
 * 注意：本包不依赖任何 UI 库（MUI / Taro / RN 组件均不得引入）。
 */

export * from './types';
export * from './api';
export * from './hooks';
export * from './store';
export * from './http';
export * from './domain';
export * from './config';
