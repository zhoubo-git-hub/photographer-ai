import axios, { AxiosRequestConfig } from 'axios';

/**
 * 扩展 axios 请求配置类型：用于标记"登录请求"，
 * 使响应拦截器能区分 401 是"凭据错误"还是"未登录/过期"。
 */
declare module 'axios' {
  export interface AxiosRequestConfig {
    isLoginRequest?: boolean;
  }
}
import { useAuthStore } from '../store/authStore';
import { useUiStore } from '../store/uiStore';

/** 业务异常：携带后端语义错误码（400/401/403/404/409/500）。 */
export class ApiError extends Error {
  code: number;
  constructor(code: number, message: string) {
    super(message);
    this.code = code;
    this.name = 'ApiError';
  }
}

export interface Result<T> {
  code: number;
  data: T;
  message: string;
}

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? 'http://localhost:8083/api',
  timeout: 15000,
});

// 请求拦截：注入 Bearer JWT
client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截：401 非登录请求清 token 跳登录，登录请求 401 视为"凭据错误"；
// 402 跳订阅页续费；403 统一弹出升级引导；非 0 code 抛出 ApiError；其余兜底为友好中文文案。
client.interceptors.response.use(
  (resp) => resp,
  (error) => {
    const data = error.response?.data;
    if (error.response?.status === 401) {
      // 登录请求的 401 表示"用户名或密码错误"，不应被当成"未登录"清 token / 跳登录。
      const isLoginRequest = error.config?.isLoginRequest === true;
      if (!isLoginRequest) {
        useAuthStore.getState().logout();
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
      }
      return Promise.reject(
        new ApiError(401, isLoginRequest ? '用户名或密码错误' : '未登录或登录已过期'),
      );
    }
    // 订阅到期/未续费：402 跳订阅页并提示续费（架构 §7）
    if (error.response?.status === 402 || (data && data.code === 402)) {
      const message = data?.message || '订阅已到期，请续费以继续使用';
      useUiStore.getState().setExpiredBanner(true);
      useUiStore.getState().openUpgrade(message);
      if (window.location.pathname !== '/billing') {
        window.location.href = '/billing';
      }
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
      // 网络层错误：后端未启动 / 跨域 / DNS 解析失败，axios 不会给出 response。
      const isTimeout =
        error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '');
      if (isTimeout) {
        return Promise.reject(new ApiError(0, '请求超时，请稍后重试'));
      }
      const apiBase =
        import.meta.env.VITE_API_BASE ?? 'http://localhost:8083/api';
      return Promise.reject(
        new ApiError(0, '无法连接服务器，请确认后端服务已启动（' + apiBase + '）'),
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
  const resp = await client.request<Result<T>>(config);
  const body = resp.data;
  if (body.code !== 0) {
    throw new ApiError(body.code, body.message || '请求失败');
  }
  return body.data;
}

export default client;
