import { useAuthStore } from '../store/authStore';

/**
 * 认证 Hook：聚合登录态与操作。
 */
export function useAuth() {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);
  const studio = useAuthStore((s) => s.studio);
  const setAuth = useAuthStore((s) => s.setAuth);
  const logout = useAuthStore((s) => s.logout);
  const setStudioPlanType = useAuthStore((s) => s.setStudioPlanType);

  return {
    isAuthenticated: !!token,
    user,
    studio,
    setAuth,
    logout,
    setStudioPlanType,
  };
}
