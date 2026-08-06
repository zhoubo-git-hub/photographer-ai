/**
 * StorageAdapter：平台无关的键值存储抽象。
 *
 * shared 不直接触碰 localStorage / MMKV / Taro storage；
 * 各端在 apps/*\/lib/storage.ts 实现该接口并在启动时调用 setStorageAdapter：
 * - Web ：window.localStorage
 * - RN  ：MMKV / AsyncStorage
 * - MP  ：Taro.getStorageSync / setStorageSync / removeStorageSync
 *
 * 统一 token 键名见 config.ts 的 TOKEN_STORAGE_KEY（架构 §7）。
 */
export interface StorageAdapter {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

/** 默认内存实现（未注入平台适配器前的占位；进程内有效，不持久化）。 */
export const memoryStorage: StorageAdapter = (() => {
  const data = new Map<string, string>();
  return {
    getItem: (key: string): string | null => (data.has(key) ? (data.get(key) as string) : null),
    setItem: (key: string, value: string): void => {
      data.set(key, value);
    },
    removeItem: (key: string): void => {
      data.delete(key);
    },
  };
})();

let currentAdapter: StorageAdapter = memoryStorage;

type AdapterChangeListener = () => void;
const listeners: AdapterChangeListener[] = [];

/**
 * 注入平台存储适配器（各端入口调用一次）。
 * 注入后会通知监听者（如 authStore 触发 persist 重新水合）。
 */
export function setStorageAdapter(adapter: StorageAdapter): void {
  currentAdapter = adapter;
  for (const listener of listeners) {
    listener();
  }
}

/** 获取当前生效的存储适配器。 */
export function getStorageAdapter(): StorageAdapter {
  return currentAdapter;
}

/** 订阅适配器切换事件（返回取消订阅函数）。 */
export function onStorageAdapterChange(listener: AdapterChangeListener): () => void {
  listeners.push(listener);
  return () => {
    const idx = listeners.indexOf(listener);
    if (idx >= 0) {
      listeners.splice(idx, 1);
    }
  };
}

/**
 * 稳定引用的桥接对象：始终委托到"当前"适配器。
 * 供 zustand persist 的 createJSONStorage 使用，避免闭包捕获旧适配器。
 */
export const storageBridge: StorageAdapter = {
  getItem: (key: string): string | null => currentAdapter.getItem(key),
  setItem: (key: string, value: string): void => currentAdapter.setItem(key, value),
  removeItem: (key: string): void => currentAdapter.removeItem(key),
};
