import { request } from '../http';
import type { DeviceTokenDTO, DeviceTokenRegister } from '../types/multiterminal';

/**
 * 设备推送接口（架构 §3.3 W3–W5）。
 * Web 暂无浏览器推送（架构 §8-7），主要供 RN App / 小程序使用。
 */
export const pushApi = {
  /** W3 注册/更新设备令牌（JWT）。 */
  registerDevice: (data: DeviceTokenRegister) =>
    request<DeviceTokenDTO>({ url: '/push/device', method: 'POST', data }),

  /** W4 注销设备（换机/登出）。 */
  unregisterDevice: (id: number) =>
    request<void>({ url: `/push/device/${id}`, method: 'DELETE' }),

  /** W5 向当前用户设备发测试推送（QA/Dev）。 */
  test: () => request<void>({ url: '/push/test', method: 'POST' }),
};
