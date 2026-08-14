import axios, { AxiosInstance, AxiosRequestConfig } from 'axios';

/**
 * 扩展 axios 请求配置类型：用于标记"登录请求"，
 * 使响应拦截器能区分 401 是"凭据错误"还是"未登录/过期"。
 * （与 web api/client.ts 语义逐字一致。）
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    isLoginRequest?: boolean;
  }
}

import { useAuthStore } from '../store/authStore';
import { useUiStore } from '../store/uiStore';

/** 业务异常：携带后端语义错误码（400/401/402/403/404/409/500）。 */
export class ApiError extends Error {
  code: number;
  constructor(code: number, message: string) {
    super(message);
    this.code = code;
    this.name = 'ApiError';
  }
}

/** 后端统一响应包裹（架构 §7：{code,data,message}）。 */
export interface Result<T> {
  code: number;
  data: T;
  message: string;
}

/**
 * HttpClient 平台可配置项。
 *
 * shared 内不读 import.meta.env / window / localStorage：
 * - baseURL：各端启动时注入（Web=VITE_API_BASE、RN=EXPO_PUBLIC_API_BASE、MP=defineConstants.API_BASE）
 * - getToken：默认从 shared authStore 取（authStore 底层走 StorageAdapter，平台无关）
 * - onUnauthorized：401（非登录请求）清 token 后的"跳登录"导航动作，由各端注入
 * - onPaymentRequired：402 的"跳订阅/续费页"导航动作，由各端注入
 */
export interface HttpClientOptions {
  baseURL: string;
  timeout: number;
  getToken: () => string | null;
  onUnauthorized: () => void;
  onPaymentRequired: () => void;
}

const options: HttpClientOptions = {
  baseURL: 'http://localhost:8083/api',
  timeout: 15000,
  getToken: () => useAuthStore.getState().token,
  onUnauthorized: () => {},
  onPaymentRequired: () => {},
};

/** axios 实例（三端共用；导航/环境差异全部经 configureHttpClient 注入）。 */
export const http: AxiosInstance = axios.create({
  baseURL: options.baseURL,
  timeout: options.timeout,
});

/**
 * 配置 HttpClient（各端入口调用一次）。
 * 未覆盖的项保持默认（token 取自 authStore、导航为 no-op）。
 */
export function configureHttpClient(overrides: Partial<HttpClientOptions>): void {
  Object.assign(options, overrides);
  if (overrides.baseURL !== undefined) {
    http.defaults.baseURL = overrides.baseURL;
  }
  if (overrides.timeout !== undefined) {
    http.defaults.timeout = overrides.timeout;
  }
}

/** 读取当前生效配置（测试/调试用）。 */
export function getHttpClientOptions(): Readonly<HttpClientOptions> {
  return options;
}

// 请求拦截：注入 Bearer JWT（token 由外部 getToken 提供，不直接读 localStorage）
http.interceptors.request.use((config) => {
  const token = options.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截：401 非登录请求清 token + 触发跳登录；登录请求 401 视为"凭据错误"；
// 402 触发续费引导 + 跳订阅页；403 统一弹出升级引导；非 0 code 抛出 ApiError；
// 其余兜底为友好中文文案。（语义与 web api/client.ts 逐字一致，导航动作抽象为回调。）
http.interceptors.response.use(
  (resp) => resp,
  (error) => {
    const data = error.response?.data;
    if (error.response?.status === 401) {
      // 登录请求的 401 表示"用户名或密码错误"，不应被当成"未登录"清 token / 跳登录。
      const isLoginRequest = error.config?.isLoginRequest === true;
      if (!isLoginRequest) {
        useAuthStore.getState().logout();
        options.onUnauthorized();
      }
      return Promise.reject(
        new ApiError(401, isLoginRequest ? '用户名或密码错误' : '未登录或登录已过期'),
      );
    }
    // 订阅到期/未续费：402 引导续费（架构 §7）
    if (error.response?.status === 402 || (data && data.code === 402)) {
      const message = data?.message || '订阅已到期，请续费以继续使用';
      useUiStore.getState().setExpiredBanner(true);
      useUiStore.getState().openUpgrade(message);
      options.onPaymentRequired();
      return Promise.reject(new ApiError(402, message));
    }
    // 专业版门禁：后端 requirePro 抛 PRO_REQUIRED / FORBIDDEN(403)，统一弹升级弹窗。
    // 团队版专属功能文案含"团队"时注入团队版引导（仍复用升级弹窗）。
    if (error.response?.status === 403 || (data && data.code === 403)) {
      const message = data?.message || '该功能为专业版专属，请升级专业版';
      useUiStore.getState().openUpgrade(message);
      return Promise.reject(new ApiError(403, message));
    }
    if (data && typeof data.code === 'number' && data.code !== 0) {
      return Promise.reject(new ApiError(data.code, data.message || '请求失败'));
    }
    // 兜底：区分网络层错误（无 response）与有 response 但未匹配上述分支的 HTTP 错误。
    if (!error.response) {
      const isTimeout =
        error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '');
      if (isTimeout) {
        return Promise.reject(new ApiError(0, '请求超时，请稍后重试'));
      }
      return Promise.reject(
        new ApiError(0, '无法连接服务器，请确认后端服务已启动（' + options.baseURL + '）'),
      );
    }
    // 有 response 但未匹配（如 500 / 400 无 code 等）：优先透传后端 message，否则给通用提示。
    const status = error.response.status;
    const serverMessage = (data as { message?: string } | undefined)?.message;
    return Promise.reject(
      new ApiError(
        status,
        serverMessage || '服务器开小差了（' + status + '），请稍后重试',
      ),
    );
  },
);

/** 统一发起请求并解包 {code,data,message}。 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const resp = await http.request<Result<T>>(config);
  const body = resp.data;
  if (body.code !== 0) {
    throw new ApiError(body.code, body.message || '请求失败');
  }
  return body.data;
}

export default http;
