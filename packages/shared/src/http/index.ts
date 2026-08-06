/**
 * HTTP 层出口（平台无关）：
 * axios 实例 + ApiError + Result{code,data,message} 解包 + 401/402/403 语义拦截。
 * 各端启动时用 configureHttpClient 注入 baseURL 与导航回调。
 */

export {
  http,
  request,
  configureHttpClient,
  getHttpClientOptions,
  ApiError,
} from './HttpClient';
export type { Result, HttpClientOptions } from './HttpClient';
export { default } from './HttpClient';
