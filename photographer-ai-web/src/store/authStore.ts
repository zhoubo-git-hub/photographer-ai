import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthResponse, Studio, User, PlanType } from '../types/models';

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
 * 认证状态（Zustand + 本地持久化）：token / 用户 / 工作室。
 * token 用于 Axios 拦截注入 Bearer。
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
    { name: 'photogai-auth' },
  ),
);
