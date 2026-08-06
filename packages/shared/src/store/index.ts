/**
 * 状态管理出口（Zustand，逻辑三端共享）：
 * - storage  ：StorageAdapter 抽象 + memoryStorage 占位 + setStorageAdapter 注入点
 * - authStore：登录态（经 StorageAdapter 持久化）
 * - uiStore  ：toast / 待填报价 / 升级弹窗 / 到期黄条
 */

export * from './storage';
export * from './authStore';
export * from './uiStore';
