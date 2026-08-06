/**
 * 共享环境/路径常量占位。
 *
 * 各端真实 base url 由自身环境变量注入（Web: VITE_API_BASE / RN: EXPO_PUBLIC_API_BASE /
 * 小程序: defineConstants.API_BASE），此处只放三端一致的路径前缀与键名约定。
 */

/** REST 统一路径前缀（架构 §7 约定）。 */
export const API_BASE = '/api';

/** 三端统一的 token 存储键名（架构 §7 约定）。 */
export const TOKEN_STORAGE_KEY = 'photogai_token';

/** 鉴权请求头名称。 */
export const AUTH_HEADER = 'Authorization';

/** 鉴权 token 前缀。 */
export const AUTH_SCHEME = 'Bearer';

/** 默认请求超时（毫秒）。 */
export const DEFAULT_TIMEOUT_MS = 20000;
