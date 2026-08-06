import type { OrderStatus } from '../types/models';
import { NEXT_STATUSES, STATUS_LABELS, STATUS_COLUMNS } from '../types/models';

/**
 * 订单纯领域逻辑（无副作用、无平台依赖；状态机与后端 OrderStateMachine 一致）。
 */

/** 判断状态流转是否合法（相邻正向 + 回退，见 NEXT_STATUSES）。 */
export function canTransition(from: OrderStatus, to: OrderStatus): boolean {
  return NEXT_STATUSES[from].includes(to);
}

/** 取某状态允许流转到的目标状态列表。 */
export function nextStatusesOf(status: OrderStatus): OrderStatus[] {
  return NEXT_STATUSES[status];
}

/** 状态中文标签。 */
export function orderStatusLabel(status: OrderStatus): string {
  return STATUS_LABELS[status];
}

/** 看板列序号（用于排序/比较，越小越靠前）。 */
export function statusIndexOf(status: OrderStatus): number {
  return STATUS_COLUMNS.indexOf(status);
}

/**
 * 金额格式化（三端一致）：人民币元、千分位、最多两位小数。
 * amount 为空时返回占位 '-'。
 */
export function formatAmount(amount?: number | null, currency = 'CNY'): string {
  if (amount === null || amount === undefined || Number.isNaN(amount)) {
    return '-';
  }
  const formatted = amount.toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
  return currency === 'CNY' ? `¥${formatted}` : `${formatted} ${currency}`;
}

/**
 * 日期格式化（三端一致）：ISO 字符串 → 'YYYY-MM-DD'。
 * 无效/空输入返回占位 '-'。展示时区约定 Asia/Shanghai（架构 §7），
 * 后端 shoot_date 为 DATE 语义，直接截取日期部分即可，避免时区漂移。
 */
export function formatDate(iso?: string | null): string {
  if (!iso) {
    return '-';
  }
  const match = /^(\d{4}-\d{2}-\d{2})/.exec(iso);
  if (match) {
    return match[1];
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return '-';
  }
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * 日期时间格式化：ISO 字符串 → 'YYYY-MM-DD HH:mm'（本地时区展示）。
 */
export function formatDateTime(iso?: string | null): string {
  if (!iso) {
    return '-';
  }
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) {
    return '-';
  }
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${day} ${hh}:${mm}`;
}
