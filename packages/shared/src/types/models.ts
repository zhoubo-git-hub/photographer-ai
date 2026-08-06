// 与后端共享约定的前端类型镜像（枚举/字段对齐 architecture.md §7）。

export type OrderStatus =
  | 'CONSULT'
  | 'DEPOSIT'
  | 'SHOOT'
  | 'EDIT'
  | 'DELIVER'
  | 'REPURCHASE';

export type ReminderType =
  | 'DEPOSIT_DUE'
  | 'SHOOT_TOMORROW'
  | 'EDIT_OVERDUE'
  | 'DELIVER_REVIEW'
  | 'REPURCHASE'
  | 'SUBSCRIPTION_UPGRADED'
  | 'SUBSCRIPTION_EXPIRED';
export type ReminderStatus = 'PENDING' | 'DONE' | 'DISMISSED';

/** 提醒触发事件（提醒规则）。 */
export type ReminderTriggerEvent = 'DEPOSIT' | 'SHOOT' | 'DELIVER' | 'REPURCHASE';

/** AI 沟通助手场景。 */
export type CommScenario =
  | 'URGE_DEPOSIT'
  | 'URGE_FINAL'
  | 'PRE_SHOOT'
  | 'DELIVER_REVIEW'
  | 'FAQ'
  | 'REPURCHASE';

/** 沟通助手请求。 */
export interface CommRequest {
  orderId?: number | null;
  customerId?: number | null;
  scenario: CommScenario;
}

/** 沟通助手响应。 */
export interface CommResponse {
  text: string;
  scenario: CommScenario;
  fallback: boolean;
}

/** 提醒规则。 */
export interface ReminderRule {
  id: number;
  studioId: number;
  event: ReminderTriggerEvent;
  offsetDays: number;
  enabled: boolean;
  channel: string;
}

/** 提醒规则增改请求。 */
export interface ReminderRuleRequest {
  event: ReminderTriggerEvent;
  offsetDays: number;
  enabled: boolean;
  channel?: string;
}

/** 合同模板。 */
export interface ContractTemplate {
  id: number;
  studioId: number | null;
  name: string;
  category?: string;
  content: string;
  builtin: boolean;
  createdBy?: number;
}

/** 合同生成请求。 */
export interface ContractGenerateRequest {
  orderId: number;
  templateId: number;
}

/** 合同生成响应。 */
export interface ContractGenerateResponse {
  title: string;
  content: string;
}

/** 复购任务。 */
export interface RepurchaseTask {
  reminderId: number;
  customerId: number;
  customerName?: string;
  shootType?: string;
  lastShootDate?: string;
  repurchaseCycleDays?: number;
  dueAt?: string;
  status: ReminderStatus;
}

export type PlanType = 'FREE' | 'PRO' | 'TEAM';

export interface User {
  id: number;
  studioId: number;
  username: string;
  email?: string;
  role: string;
  createdAt?: string;
}

export interface Studio {
  id: number;
  name: string;
  planType: PlanType;
  ownerUserId?: number;
}

export interface AuthResponse {
  token: string;
  user: User;
  studio: Studio;
}

export interface Order {
  id: number;
  studioId: number;
  customerId: number;
  customerName?: string;
  title: string;
  shootType?: string;
  status: OrderStatus;
  amount?: number;
  depositAmount?: number;
  currency?: string;
  shootDate?: string;
  shootEndDate?: string;
  durationHours?: number;
  photoCount?: number;
  region?: string;
  style?: string;
  quoteSuggestion?: string;
  createdBy?: number;
  /** 分配给的成员用户 ID（团队协作，可空表示未分配）。 */
  assignedTo?: number | null;
  createdAt?: string;
  updatedAt?: string;
  history?: StatusHistory[];
}

export interface StatusHistory {
  id: number;
  orderId: number;
  fromStatus?: OrderStatus;
  toStatus: OrderStatus;
  operatorId?: number;
  createdAt?: string;
}

export interface Customer {
  id: number;
  studioId: number;
  name: string;
  wechatId?: string;
  phone?: string;
  tags?: string;
  note?: string;
  lastShootDate?: string;
  repurchaseCycleDays?: number;
  birthday?: string;
  anniversary?: string;
  repurchaseEnabled?: boolean;
  sourceChannel?: string;
  createdAt?: string;
  updatedAt?: string;
  orderCount?: number;
  lastOrderAt?: string;
  lastAmount?: number;
  orders?: Order[];
}

/** 新建客户请求（含阶段2 画像字段）。 */
export interface CustomerCreateRequest {
  name: string;
  wechatId?: string;
  phone?: string;
  tags?: string;
  note?: string;
  lastShootDate?: string;
  repurchaseCycleDays?: number;
  birthday?: string;
  anniversary?: string;
  repurchaseEnabled?: boolean;
  sourceChannel?: string;
}

/** 更新客户请求（含阶段2 画像字段）。 */
export interface CustomerUpdateRequest {
  name?: string;
  wechatId?: string;
  phone?: string;
  tags?: string;
  note?: string;
  lastShootDate?: string;
  repurchaseCycleDays?: number;
  birthday?: string;
  anniversary?: string;
  repurchaseEnabled?: boolean;
  sourceChannel?: string;
}

export interface Conflict {
  orderId: number;
  title: string;
  shootDate?: string;
  shootEndDate?: string;
}

export interface QuoteRequest {
  shootType: string;
  durationHours?: number;
  photoCount?: number;
  region?: string;
  style?: string;
  customerName?: string;
}

export interface QuoteResponse {
  priceLow: number;
  priceHigh: number;
  basis: string;
  script: string;
  remainingQuota: number;
}

export interface ScheduleItem {
  orderId: number;
  title: string;
  shootDate?: string;
  shootEndDate?: string;
  status: OrderStatus;
  conflict: boolean;
}

export interface QuotaInfo {
  planType: PlanType;
  orderCount: number;
  orderLimit: number;
  aiQuoteUsedMonth: number;
  aiQuoteLimit: number;
  quotaMonth: string;
  remainingOrderQuota: number;
  remainingAiQuota: number;
}

export interface Reminder {
  id: number;
  orderId?: number;
  customerId?: number;
  type: ReminderType;
  dueAt?: string;
  status: ReminderStatus;
  orderTitle?: string;
  customerName?: string;
}

export interface PageData<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ===== 阶段3 增量类型（支付 / 团队 / 看板 / 校准），与后端 architecture-phase3 §7 对齐 =====

export type SubscriptionStatus = 'ACTIVE' | 'CANCELLED' | 'EXPIRED' | 'NONE';
export type PaymentChannel = 'WECHAT' | 'ALIPAY' | 'MOCK';

export interface SubscribeRequest {
  planType: 'PRO' | 'TEAM';
  channel?: PaymentChannel;
}

export interface SubscribeResponse {
  outTradeNo: string;
  payUrl?: string;
  qrCode?: string;
  amount: number;
}

export interface SubscriptionView {
  planType: PlanType;
  status: SubscriptionStatus;
  expiresAt?: string;
  autoRenew: boolean;
}

export type TeamRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'READONLY';

export interface TeamInvitation {
  id: number;
  studioId: number;
  inviterId: number;
  email?: string;
  phone?: string;
  role: TeamRole;
  token: string;
  status: string;
  expiresAt?: string;
  acceptedUserId?: number;
}

export interface TeamMember {
  id: number;
  username: string;
  email?: string;
  role: TeamRole;
  orderCount: number;
  token?: string;
  invitationId?: number;
}

export interface AcceptInvitationRequest {
  token: string;
  username: string;
  password: string;
}

export interface RevenuePointDTO {
  period: string;
  revenue: number;
}

export interface OverviewDTO {
  revenue: number;
  orderCount: number;
  aov: number;
  repurchaseRate: number;
  conversion: {
    consult: number;
    deposit: number;
    shoot: number;
    deliver: number;
  };
  revenuePoints: RevenuePointDTO[];
}

export interface FunnelDTO {
  stages: {
    status: string;
    count: number;
    rate: number;
  }[];
}

export interface MemberPerfDTO {
  memberId: number;
  name: string;
  orderCount: number;
  revenue: number;
  aov: number;
}

export interface QuoteCalibration {
  id: number;
  dimensionKey: string;
  dimensionLabel: string;
  sampleCount: number;
  currentCoef: number;
  suggestedCoef: number;
  offsetPct: number;
  withinBoundary: boolean;
  status: string;
  note?: string;
}

export interface QuoteCalibrationApplyRequest {
  id: number;
}

// 状态展示标签
export const STATUS_LABELS: Record<OrderStatus, string> = {
  CONSULT: '咨询中',
  DEPOSIT: '定金',
  SHOOT: '拍摄',
  EDIT: '修图',
  DELIVER: '交付',
  REPURCHASE: '复购',
};

// 看板列顺序
export const STATUS_COLUMNS: OrderStatus[] = [
  'CONSULT',
  'DEPOSIT',
  'SHOOT',
  'EDIT',
  'DELIVER',
  'REPURCHASE',
];

// 状态机：允许的相邻流转（正向 + 回退），与后端 OrderStateMachine 一致
export const NEXT_STATUSES: Record<OrderStatus, OrderStatus[]> = {
  CONSULT: ['DEPOSIT'],
  DEPOSIT: ['CONSULT', 'SHOOT'],
  SHOOT: ['DEPOSIT', 'EDIT'],
  EDIT: ['SHOOT', 'DELIVER'],
  DELIVER: ['EDIT', 'REPURCHASE'],
  REPURCHASE: ['DELIVER'],
};

export const REMINDER_LABELS: Record<ReminderType, string> = {
  DEPOSIT_DUE: '定金待付',
  SHOOT_TOMORROW: '拍摄前提醒',
  EDIT_OVERDUE: '修图超期',
  DELIVER_REVIEW: '交付后求好评',
  REPURCHASE: '复购提醒',
  SUBSCRIPTION_UPGRADED: '订阅升级成功',
  SUBSCRIPTION_EXPIRED: '订阅已到期',
};

// 提醒触发事件标签
export const EVENT_LABELS: Record<ReminderTriggerEvent, string> = {
  DEPOSIT: '定金后催款',
  SHOOT: '拍摄前提醒',
  DELIVER: '交付后求好评',
  REPURCHASE: '复购提醒',
};

// AI 沟通助手场景标签
export const SCENARIO_LABELS: Record<CommScenario, string> = {
  URGE_DEPOSIT: '催定金',
  URGE_FINAL: '催尾款',
  PRE_SHOOT: '拍摄前提醒',
  DELIVER_REVIEW: '交付后求好评',
  FAQ: '通用答疑',
  REPURCHASE: '复购邀约',
};

export const SOURCE_CHANNELS = ['微信', '小红书', '转介绍', '其他'] as const;
