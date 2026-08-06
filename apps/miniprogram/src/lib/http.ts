import Taro from '@tarojs/taro';
import { configureHttpClient, http } from '@photogai/shared/http';
import { useAuthStore } from '@photogai/shared/store';
import { taroAxiosAdapter } from './taroAdapter';
import { toLogin } from './nav';

/** 请求超时（ms）。小程序弱网较常见，与 shared 默认保持一致。 */
const REQUEST_TIMEOUT = 15000;

let configured = false;

/**
 * HTTP 启动装配（全局仅执行一次，须在任何请求发起前调用）。
 *
 * 铁律：
 * - **复用 shared 导出的 http 实例**，只改 defaults.adapter；绝不新建 axios 实例
 *   （新建会丢掉 shared 的请求/响应拦截器，Bearer 注入与 401/402/403 语义全部失效）。
 * - 必须在 setStorageAdapter 之后调用：getToken 读的是 authStore，
 *   而 authStore 需先经存储适配器 rehydrate 才有 token。
 */
export function setupHttp(): void {
  if (configured) {
    return;
  }
  configured = true;

  // ① 注入 Taro 适配器（小程序无 XMLHttpRequest）
  // 注意：shared 的 http 实例经 pnpm 软链解析到的 axios 类型与 miniprogram 侧 axios
  // 在 TS 下被视为两个不同「身份」（同构但不同物理副本）。运行期 webpack 会统一解析为
  // 同一份 axios，故此处经 unknown 桥接赋值，避免名义类型不匹配阻断编译。
  http.defaults.adapter = taroAxiosAdapter as unknown as typeof http.defaults.adapter;

  // ② 注入平台差异项：baseURL 与 401/402 的导航动作
  configureHttpClient({
    baseURL: API_BASE,
    timeout: REQUEST_TIMEOUT,
    // 与 shared 默认实现一致，此处显式声明便于排查登录态问题。
    getToken: () => useAuthStore.getState().token,
    // 401（非登录请求）：shared 已清 token，端侧只负责跳登录。
    onUnauthorized: () => {
      toLogin();
    },
    // 402 订阅到期：小程序不承载支付，引导去 Web 端续费。
    onPaymentRequired: () => {
      void Taro.showModal({
        title: '订阅已到期',
        content: '请前往 Web 端续费后继续使用该功能。',
        showCancel: false,
        confirmText: '我知道了',
      });
    },
  });
}

export default setupHttp;
