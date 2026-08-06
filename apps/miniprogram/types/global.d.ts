/**
 * 小程序端全局类型声明。
 */

/** 由 config/{dev,prod}.js 的 defineConstants 注入的后端 API 根地址。 */
declare const API_BASE: string;

/** Taro 页面/应用配置的类型辅助函数（由 Taro 运行时提供，仅编译期使用）。 */
declare function defineAppConfig<T>(config: T): T;
declare function definePageConfig<T>(config: T): T;

declare module '*.scss';
declare module '*.sass';
declare module '*.less';
declare module '*.css';
declare module '*.png';
declare module '*.jpg';
declare module '*.svg';

declare namespace NodeJS {
  interface ProcessEnv {
    /** Taro 构建目标平台（weapp / h5 / rn ...）。 */
    TARO_ENV: 'weapp' | 'swan' | 'alipay' | 'h5' | 'rn' | 'tt' | 'qq' | 'jd';
    NODE_ENV: 'development' | 'production' | 'test';
    /** 后端 API 根地址（构建期读取，见 config/dev.js）。 */
    API_BASE_URL?: string;
  }
}
