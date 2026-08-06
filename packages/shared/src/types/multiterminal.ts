// 多端扩张新增类型（架构 architecture-mobile.md §3.4 逐字落地）。
// 微信登录 / 设备推送令牌 / 对象存储预签名直传。

import type { User, Studio } from './models';

export type WechatAppType = 'WEB' | 'APP' | 'MP';
export type PushPlatform = 'IOS' | 'ANDROID' | 'MP';

/** W1 微信登录请求（MP=wx.login code；APP/WEB=开放平台 OAuth code）。 */
export interface WechatLoginRequest {
  appType: WechatAppType;
  code: string;
  encryptedData?: string;
  iv?: string;
  bindToken?: string;
}

/** W1 微信登录响应（命中同 unionid → 同 studio）。 */
export interface WechatLoginResponse {
  token: string;
  user: User;
  studio: Studio;
  isNewUser: boolean;
  needBind: boolean;
}

/** W2 已登录用户绑定微信请求。 */
export interface WechatBindRequest {
  appType: WechatAppType;
  code: string;
}

/** W3 注册/更新设备推送令牌请求。 */
export interface DeviceTokenRegister {
  platform: PushPlatform;
  token: string;
  appVersion?: string;
}

/** W3 设备令牌 DTO。 */
export interface DeviceTokenDTO {
  id: number;
  platform: PushPlatform;
  token: string;
  appVersion?: string;
}

/** W6 预签名直传请求。 */
export interface PresignRequest {
  fileName: string;
  contentType: string;
  bizType: 'ORDER_SAMPLE' | 'CUSTOMER_AVATAR' | 'STUDIO_LOGO';
}

/** W6 预签名直传响应（dev LOCAL 时返回占位 + 走 W8 upload 兜底）。 */
export interface PresignResponse {
  uploadUrl: string;
  fileKey: string;
  method: 'PUT' | 'POST';
  headers?: Record<string, string>;
  expiresAt?: string;
}

/** W7/W8 上传文件记录 DTO。 */
export interface UploadFileDTO {
  id: number;
  url: string;
  fileKey: string;
  bizType: string;
}
