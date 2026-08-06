/**
 * Web 端 shared 包初始化。
 *
 * 根因（一）：Web 端此前从未注入 shared 的 StorageAdapter，也未配置 HttpClient，
 * 导致 shared 的 axios 实例（见 packages/shared/src/http/HttpClient.ts）
 * 在发送请求时通过 `getToken()` 读取的是 shared 自己的 authStore —— 而该
 * store 底层走 storageBridge，默认指向内存 Map（memoryStorage），并非
 * localStorage。因此 shared 的 authStore 永远读不到 Web 登录页写入的
 * token（key 同为 'photogai-auth'，但落在 localStorage），请求也就不带
 * `Authorization: Bearer xxx`，后端 SecurityConfig 拦截后返回 401。
 *
 * 根因（二）：web 与 shared 是**两套独立的 Zustand authStore 实例**——
 *   - web/src/store/authStore.ts    ：登录页 `setAuth()` 写的是这个；
 *   - shared/src/store/authStore.ts ：shared http 拦截器 `getToken()` 读的是这个。
 * 二者 persist name 同为 'photogai-auth'，但**运行时内存状态互不相通**：
 * 仅注入 StorageAdapter 只能保证「刷新后各自水合出同一份数据」，无法覆盖
 * 「本次会话内登录」这一路径。于是登录后 shared store 仍为空 → 受保护页面
 * 任意 shared-http 请求（AI 报价 / 订单 / 客户等）无 Bearer → 后端 401 →
 * shared 响应拦截器触发 `onUnauthorized` → 跳回 /login → 无限循环。
 *
 * 本模块在应用入口（main.tsx）渲染 <App /> 之前调用一次，完成三件事：
 *   1. setStorageAdapter(localStorage)：让 shared 的 storageBridge 指向
 *      localStorage，从而 shared authStore 与 web authStore 读写同一份数据。
 *   2. 双向订阅同步 web ↔ shared 的 authStore 运行时状态（token/user/studio），
 *      并对刷新场景做一次初始水合补推。
 *   3. configureHttpClient({ onUnauthorized, onPaymentRequired })：注入 401/402
 *      的导航动作，语义与 web/src/api/client.ts 保持一致（非登录/订阅页才跳转，
 *      避免无限重定向）。
 */
import { setStorageAdapter, type StorageAdapter } from '@photogai/shared/store/storage';
import { configureHttpClient } from '@photogai/shared/http/HttpClient';
import { useAuthStore as webAuthStore } from '../store/authStore';
import { useAuthStore as sharedAuthStore } from '@photogai/shared/store/authStore';

/** 注入 localStorage 适配器，使 shared authStore 能读写与 web authStore 同一份 localStorage 数据。 */
const webStorageAdapter: StorageAdapter = {
  getItem: (key: string): string | null => localStorage.getItem(key),
  setItem: (key: string, value: string): void => {
    localStorage.setItem(key, value);
  },
  removeItem: (key: string): void => {
    localStorage.removeItem(key);
  },
};

/**
 * 两个 store 之间同步的最小快照。
 *
 * web 与 shared 各自从自己的 `types/models` 引入 User / Studio，结构一致但来源不同；
 * 这里统一用 `unknown` 中转，落库时再按目标 store 的状态类型断言，避免类型耦合。
 */
interface AuthSnapshot {
  token: string | null;
  user: unknown;
  studio: unknown;
}

/** 目标 store 的可写补丁类型（由各自 store 的状态类型推导，避免手写重复定义）。 */
type SharedAuthPatch = Partial<ReturnType<typeof sharedAuthStore.getState>>;
type WebAuthPatch = Partial<ReturnType<typeof webAuthStore.getState>>;

/**
 * 防递归守卫。
 *
 * 两个 subscribe 互相调用对方的 setState，若不拦截会形成
 * web.set → shared.subscribe → shared.set → web.subscribe → … 的死循环。
 * 同步动作是同步执行的（Zustand 的 listener 在 setState 内同步触发），
 * 因此一个模块级布尔量足以覆盖整个重入窗口。
 */
let syncing = false;

/** 只取需要同步的三个字段，避免把 setAuth/logout 等 action 覆盖掉。 */
const pick = (state: AuthSnapshot): AuthSnapshot => ({
  token: state.token,
  user: state.user,
  studio: state.studio,
});

/** web store 变更 → 推送到 shared store（登录成功的主链路）。 */
const syncWebToShared = (state: AuthSnapshot): void => {
  if (syncing) return;
  syncing = true;
  try {
    sharedAuthStore.setState(pick(state) as unknown as SharedAuthPatch);
  } finally {
    syncing = false;
  }
};

/** shared store 变更 → 推送到 web store（401 后 shared logout 清态的回流链路）。 */
const syncSharedToWeb = (state: AuthSnapshot): void => {
  if (syncing) return;
  syncing = true;
  try {
    webAuthStore.setState(pick(state) as unknown as WebAuthPatch);
  } finally {
    syncing = false;
  }
};

/**
 * 初始化 shared 包的平台能力。
 *
 * 必须在任何组件渲染、任何 API 调用之前执行，确保 shared 的 storageBridge
 * 与 http 配置在请求发出前生效。
 */
export function initShared(): void {
  // 1. 存储：让 shared 的 Zustand persist 落到 localStorage（与 web authStore 同 key 'photogai-auth'）。
  setStorageAdapter(webStorageAdapter);

  // 2. 登录态：打通 web / shared 两套 authStore 实例的运行时状态。
  //    - 登录：web.setAuth() → syncWebToShared → shared 拿到 token → 请求带 Bearer，不再 401；
  //    - 登出/401：shared.logout() → syncSharedToWeb → web 同步清空 → <Protected> 正确跳登录页。
  webAuthStore.subscribe(syncWebToShared);
  sharedAuthStore.subscribe(syncSharedToWeb);

  // 3. 初始补推：刷新场景下 web store 已从 localStorage 水合出 token，
  //    但 shared store 的 rehydrate 可能尚未完成或为空，这里补推一次兜底。
  const hydrated = webAuthStore.getState();
  if (hydrated.token) {
    syncWebToShared(hydrated);
  }

  // 4. HTTP：配置 401 / 402 的导航动作，与 web/src/api/client.ts 语义一致。
  configureHttpClient({
    onUnauthorized: (): void => {
      // 非登录页才跳转，避免登录失败时在 /login 上无限重定向。
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    },
    onPaymentRequired: (): void => {
      // 非订阅/账单页才跳转，避免续费引导循环。
      if (window.location.pathname !== '/billing') {
        window.location.href = '/billing';
      }
    },
  });
}
