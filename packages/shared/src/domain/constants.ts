/**
 * 三端一致的领域常量：主题色 token、错误码文案映射，
 * 并 re-export 状态机/标签常量（真源在 types/models.ts，避免双份定义）。
 */

export {
  STATUS_LABELS,
  STATUS_COLUMNS,
  NEXT_STATUSES,
  REMINDER_LABELS,
  EVENT_LABELS,
  SCENARIO_LABELS,
  SOURCE_CHANNELS,
} from '../types/models';

/**
 * 主题色 token（架构 §7"极简两主色"）：
 * 主色 #2D6CDF（主操作/激活）、墨色 #1A1A1A（文字/重要），最多两主色。
 * Web 用 MUI theme 映射、RN 用 nativewind 映射、小程序用 taro-ui 主题变量。
 */
export const THEME_COLORS = {
  /** 主色：主操作/激活。 */
  primary: '#2D6CDF',
  /** 墨色：文字/重要（secondary）。 */
  ink: '#1A1A1A',
  /** 页面背景（web MUI background.default）。 */
  background: '#f7f8fa',
  /** 卡片/纸面背景。 */
  paper: '#FFFFFF',
  /** 主文字色。 */
  textPrimary: '#1A1A1A',
  /** 次要文字色。 */
  textSecondary: '#6B7280',
  /** 浅灰分隔线。 */
  divider: '#F2F4F7',
  /** 成功。 */
  success: '#16A34A',
  /** 警告。 */
  warning: '#F59E0B',
  /** 错误。 */
  error: '#DC2626',
} as const;

/** 圆角规范（架构 §7：圆角 10–12）。 */
export const RADIUS = {
  base: 10,
  card: 12,
} as const;

/**
 * 错误码 → 默认文案（架构 §7 错误码约定；
 * 优先展示后端 message，仅在缺失时用此兜底）。
 */
export const ERROR_CODE_MESSAGES: Record<number, string> = {
  0: '请求失败',
  400: '请求参数有误',
  401: '未登录或登录已过期',
  402: '订阅已到期，请续费以继续使用',
  403: '该功能为专业版专属，请升级专业版',
  404: '资源不存在',
  409: '数据冲突，请刷新后重试',
  500: '服务器开小差了，请稍后重试',
};

/** 取错误码对应的默认文案（未知码回退 500 文案）。 */
export function messageOfErrorCode(code: number): string {
  return ERROR_CODE_MESSAGES[code] ?? ERROR_CODE_MESSAGES[500];
}
