import { useMutation } from '@tanstack/react-query';
import { pushApi } from '../api/push';
import type { DeviceTokenRegister } from '../types/multiterminal';

/**
 * 设备推送 hooks（架构 §3.3 W3–W5；RN App / 小程序使用，Web 暂无浏览器推送）。
 * 令牌获取（Expo Push token / 小程序订阅授权）由各端平台层完成后传入。
 */
export function useRegisterDevice() {
  return useMutation({
    mutationFn: (data: DeviceTokenRegister) => pushApi.registerDevice(data),
  });
}

/** 注销设备（换机/登出时调用）。 */
export function useUnregisterDevice() {
  return useMutation({
    mutationFn: (id: number) => pushApi.unregisterDevice(id),
  });
}

/** 发送测试推送（QA/Dev）。 */
export function useTestPush() {
  return useMutation({
    mutationFn: () => pushApi.test(),
  });
}
