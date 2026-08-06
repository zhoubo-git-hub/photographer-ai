import { request } from '../http';
import type { AuthResponse } from '../types/models';
import type {
  WechatBindRequest,
  WechatLoginRequest,
  WechatLoginResponse,
} from '../types/multiterminal';

export interface LoginPayload {
  username: string;
  password: string;
}

export interface RegisterPayload {
  username: string;
  password: string;
  email?: string;
  studioName: string;
}

export const authApi = {
  login: (data: LoginPayload) =>
    request<AuthResponse>({
      url: '/auth/login',
      method: 'POST',
      data,
      isLoginRequest: true,
    }),
  register: (data: RegisterPayload) =>
    request<AuthResponse>({ url: '/auth/register', method: 'POST', data }),
  /** W1 微信登录（MP=wx.login code；APP/WEB=开放平台 OAuth code；同 unionid → 同 studio）。 */
  wechatLogin: (data: WechatLoginRequest) =>
    request<WechatLoginResponse>({
      url: '/auth/wechat/login',
      method: 'POST',
      data,
      isLoginRequest: true,
    }),
  /** W2 已登录用户绑定微信（需 JWT）。 */
  wechatBind: (data: WechatBindRequest) =>
    request<AuthResponse>({ url: '/auth/wechat/bind', method: 'POST', data }),
};
