import Taro from '@tarojs/taro';
import { AxiosError } from 'axios';
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios';

/**
 * axios → Taro.request 适配器（小程序无 XMLHttpRequest，axios 默认适配器不可用）。
 *
 * 【为什么自研而不用 npm 的 axios-taro-adapter】
 * 架构 §1.2 原选型为 `axios-taro-adapter`，实读该包 0.0.3 源码后发现两处致命缺陷，
 * 直接使用会导致本项目**大面积功能不可用**，故在 app 侧自研替代（shared 仍零改动，符合裁定 R1/R2）：
 *  1. 完全忽略 `config.params`，不拼接 query string
 *     → `orderApi.list(status,page,size)` / `scheduleApi.month(year,month)` /
 *       `customerApi.list(keyword,...)` / `reminderApi.list(status,dueOnly)` 的入参会全部丢失。
 *  2. 不做 `validateStatus` 判定，任何 HTTP 状态码都走 resolve
 *     → shared HttpClient 响应拦截器的 401/402/403 分支**永远不会触发**，
 *       登录态过期不清 token、订阅到期不弹续费、专业版门禁不弹升级。
 *
 * 本实现严格对齐 axios 内建适配器语义：buildFullPath → params 序列化 → settle(validateStatus)，
 * 失败时抛出带 `response` / `code` 的 AxiosError，保证 shared 拦截器逻辑与 Web 端逐字一致。
 */

/** Taro.request 支持的 HTTP 方法。 */
type TaroMethod = NonNullable<Taro.request.Option['method']>;

const SUPPORTED_METHODS: readonly TaroMethod[] = [
  'OPTIONS',
  'GET',
  'HEAD',
  'POST',
  'PUT',
  'DELETE',
  'TRACE',
  'CONNECT',
];

/** 判断是否为绝对地址（含协议或协议相对）。 */
function isAbsoluteUrl(url: string): boolean {
  return /^([a-z][a-z\d+\-.]*:)?\/\//i.test(url);
}

/**
 * 序列化 query 参数。
 * 规则与 axios 默认 paramsSerializer 对齐：null/undefined 跳过（这点至关重要，
 * shared 大量端点传 `params: { status: undefined }` 表示"不筛选"）。
 */
export function serializeParams(params: unknown): string {
  if (params === null || params === undefined || typeof params !== 'object') {
    return '';
  }
  const parts: string[] = [];
  const push = (key: string, value: string): void => {
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`);
  };
  for (const [key, value] of Object.entries(params as Record<string, unknown>)) {
    if (value === null || value === undefined) {
      continue;
    }
    if (Array.isArray(value)) {
      for (const item of value) {
        if (item === null || item === undefined) {
          continue;
        }
        push(`${key}[]`, String(item));
      }
      continue;
    }
    if (value instanceof Date) {
      push(key, value.toISOString());
      continue;
    }
    if (typeof value === 'object') {
      push(key, JSON.stringify(value));
      continue;
    }
    push(key, String(value));
  }
  return parts.join('&');
}

/** 拼接 baseURL + url + query（等价 axios buildFullPath + buildURL）。 */
export function buildRequestUrl(
  baseURL: string | undefined,
  url: string | undefined,
  params: unknown,
): string {
  const rawUrl = url ?? '';
  let fullPath = rawUrl;
  if (!isAbsoluteUrl(rawUrl) && baseURL) {
    fullPath = `${baseURL.replace(/\/+$/, '')}/${rawUrl.replace(/^\/+/, '')}`;
  }
  const queryString = serializeParams(params);
  if (!queryString) {
    return fullPath;
  }
  return `${fullPath}${fullPath.includes('?') ? '&' : '?'}${queryString}`;
}

/** 把 AxiosHeaders / 普通对象统一拍平成 Taro.request 需要的 header 字典。 */
function normalizeHeaders(config: InternalAxiosRequestConfig): Record<string, string> {
  const result: Record<string, string> = {};
  const raw: unknown = config.headers;
  if (!raw || typeof raw !== 'object') {
    return result;
  }
  const maybeJson = raw as { toJSON?: () => unknown };
  const plain: Record<string, unknown> =
    typeof maybeJson.toJSON === 'function'
      ? (maybeJson.toJSON() as Record<string, unknown>)
      : (raw as Record<string, unknown>);
  for (const [key, value] of Object.entries(plain)) {
    if (value === null || value === undefined) {
      continue;
    }
    // 跳过 axios 的 common/get/post 等方法分组残留（它们是嵌套对象，不是真实 header）。
    if (typeof value === 'object') {
      continue;
    }
    result[key] = String(value);
  }
  return result;
}

/** 归一化 HTTP 方法，未知方法退化为 GET。 */
function normalizeMethod(method: string | undefined): TaroMethod {
  const upper = String(method ?? 'GET').toUpperCase() as TaroMethod;
  return SUPPORTED_METHODS.includes(upper) ? upper : 'GET';
}

/**
 * Taro 适配器主体。启动时由 lib/http.ts 赋给 shared 导出的 http 实例的 defaults.adapter。
 */
export const taroAxiosAdapter: AxiosAdapter = (config: InternalAxiosRequestConfig) =>
  new Promise<AxiosResponse>((resolve, reject) => {
    const url = buildRequestUrl(config.baseURL, config.url, config.params);
    const method = normalizeMethod(config.method);
    const timeout = typeof config.timeout === 'number' && config.timeout > 0 ? config.timeout : undefined;

    Taro.request({
      url,
      method,
      // 经 axios transformRequest 之后，对象类型的 data 已被序列化为 JSON 字符串。
      data: config.data as string | TaroGeneral.IAnyObject | ArrayBuffer | undefined,
      header: normalizeHeaders(config),
      timeout,
      success: (res) => {
        const response: AxiosResponse = {
          data: res.data,
          status: res.statusCode,
          statusText: String(res.statusCode),
          headers: (res.header ?? {}) as unknown as AxiosResponse['headers'],
          config,
          request: null,
        };
        const validateStatus =
          config.validateStatus ?? ((status: number): boolean => status >= 200 && status < 300);
        if (validateStatus(response.status)) {
          resolve(response);
          return;
        }
        reject(
          new AxiosError(
            `Request failed with status code ${response.status}`,
            response.status >= 400 && response.status < 500
              ? AxiosError.ERR_BAD_REQUEST
              : AxiosError.ERR_BAD_RESPONSE,
            config,
            null,
            response,
          ),
        );
      },
      fail: (err) => {
        const message = err?.errMsg ?? 'request:fail';
        const isTimeout = /timeout/i.test(message);
        reject(
          new AxiosError(
            message,
            isTimeout ? AxiosError.ECONNABORTED : AxiosError.ERR_NETWORK,
            config,
            null,
          ),
        );
      },
    });
  });

export default taroAxiosAdapter;
