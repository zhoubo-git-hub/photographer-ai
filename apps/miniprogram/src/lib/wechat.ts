import Taro from '@tarojs/taro';
import { authApi } from '@photogai/shared/api';
import { useAuthStore } from '@photogai/shared/store';
import type { WechatLoginResponse } from '@photogai/shared/types';

/**
 * 微信登录服务。
 *
 * 链路（架构 §6）：
 *   Taro.login() 取临时 code
 *     → authApi.wechatLogin({ appType: 'MP', code })   ← 端点/URL 由 shared 持有，端侧不拼路径
 *     → 后端 code2Session 解析 openid/unionid，命中或新建 studio
 *     → setAuth({token,user,studio})
 *     → zustand persist 经 storageBridge 落 Taro storage（键 'photogai-auth'）
 *
 * 安全纪律：AppSecret 只在后端，小程序侧**永不持有**。
 */

/** 取微信临时登录凭证 code。 */
async function getWechatCode(): Promise<string> {
  const res = await Taro.login();
  if (!res.code) {
    throw new Error(res.errMsg || '微信登录失败：未获取到 code');
  }
  return res.code;
}

/**
 * 执行微信一键登录并写入登录态。
 *
 * R9：`wechatLogin` 返回 `WechatLoginResponse`（比 `setAuth` 所需的 `AuthResponse`
 * 多出 isNewUser / needBind 两个字段，token/user/studio 同构）。
 * 这里按结构取三个字段传入，**不修改 shared 的类型定义**。
 */
export async function loginWithWechat(): Promise<WechatLoginResponse> {
  const code = await getWechatCode();
  const resp = await authApi.wechatLogin({ appType: 'MP', code });
  useAuthStore.getState().setAuth({
    token: resp.token,
    user: resp.user,
    studio: resp.studio,
  });
  return resp;
}

/** 退出登录（清空 authStore，persist 会同步清除 Taro storage）。 */
export function logoutWechat(): void {
  useAuthStore.getState().logout();
}
