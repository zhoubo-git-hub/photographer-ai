import Taro from '@tarojs/taro';
import type { StorageAdapter } from '@photogai/shared/store';

/**
 * Taro 存储适配器（实现 shared 的 StorageAdapter 抽象）。
 *
 * 【关键坑】Taro/微信的 `getStorageSync` 在**键不存在时返回空字符串 `''`**，而不是 `null`。
 * zustand persist 会把 `''` 当作"有值但内容为空"，进而 JSON.parse('') 抛错导致水合失败、
 * 冷启动登录态丢失。因此这里必须显式把 `''` 归一化为 `null`。
 *
 * 注意：token 的持久化键由 shared authStore 的 persist 管理（name = 'photogai-auth'），
 * 端侧**不得**自行读写该键，只提供存储通道。
 */
export const taroStorageAdapter: StorageAdapter = {
  getItem(key: string): string | null {
    try {
      const value: unknown = Taro.getStorageSync(key);
      if (value === '' || value === null || value === undefined) {
        return null;
      }
      return typeof value === 'string' ? value : JSON.stringify(value);
    } catch {
      // 存储读取异常按"无值"处理，避免启动期直接崩溃。
      return null;
    }
  },

  setItem(key: string, value: string): void {
    try {
      Taro.setStorageSync(key, value);
    } catch {
      // 存储写入失败（如超出容量）时静默降级，不阻断业务流程。
    }
  },

  removeItem(key: string): void {
    try {
      Taro.removeStorageSync(key);
    } catch {
      // 同上，删除失败不影响主流程。
    }
  },
};

export default taroStorageAdapter;
