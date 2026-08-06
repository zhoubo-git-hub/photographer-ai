import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { AuthResponse, Studio, User, PlanType } from '../types/models';
import { onStorageAdapterChange, storageBridge } from './storage';

interface AuthState {
  token: string | null;
  user: User | null;
  studio: Studio | null;
  setAuth: (auth: AuthResponse) => void;
  logout: () => void;
  /** 局部更新当前工作室的套餐类型（订阅/支付成功后回写，避免整体重置）。 */
  setStudioPlanType: (planType: PlanType) => void;
}

/**
 * 认证状态（Zustand + StorageAdapter 持久化）：token / 用户 / 工作室。
 * token 用于 HttpClient 拦截注入 Bearer。
 *
 * 与 web 版行为一致（persist name 'photogai-auth'），差异仅在于：
 * 持久化介质由各端注入的 StorageAdapter 决定（Web=localStorage、RN=MMKV、MP=Taro storage），
 * 未注入前落在 memoryStorage 占位上。
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      studio: null,
      setAuth: (auth: AuthResponse) =>
        set({ token: auth.token, user: auth.user, studio: auth.studio }),
      logout: () => set({ token: null, user: null, studio: null }),
      setStudioPlanType: (planType) =>
        set((s) => ({ studio: s.studio ? { ...s.studio, planType } : s.studio })),
    }),
    {
      name: 'photogai-auth',
      storage: createJSONStorage(() => storageBridge),
    },
  ),
);

// 各端注入真实 StorageAdapter 后，重新从持久化介质水合登录态。
onStorageAdapterChange(() => {
  void useAuthStore.persist.rehydrate();
});
