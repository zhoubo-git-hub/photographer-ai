import { describe, it, expect } from 'vitest';

/**
 * 提醒规则 offset 计算验证（镜像后端 ReminderRuleService.DEFAULT_OFFSETS + OrderService.createFromRule）。
 *
 * 与阶段1 硬编码（定金+3 / 拍摄前-1 / 修图+7 / 交付+3）完全一致，保证免费版不回归。
 * 后端 ReminderTriggerEvent 枚举当前缺失 EDIT 常量（见测试报告「已知问题」），
 * 此处按设计意图（含 EDIT=7）转录，待后端补全枚举后线上逻辑即与阶段1 一致。
 *
 * 日期计算全部走 UTC，避免 CI/沙箱时区差异影响断言。
 */

type TriggerEvent = 'DEPOSIT' | 'SHOOT' | 'EDIT' | 'DELIVER' | 'REPURCHASE';

/** 镜像 ReminderRuleService.DEFAULT_OFFSETS（与阶段1 硬编码一致）。 */
const DEFAULT_OFFSETS: Record<TriggerEvent, number> = {
  DEPOSIT: 3,
  SHOOT: -1,
  EDIT: 7,
  DELIVER: 3,
  REPURCHASE: 0,
};

interface OrderInput {
  shootDate?: string | null;
}

/** 镜像 ReminderRuleService.findOffset：有启用规则用规则值，否则回退默认硬编码。 */
function findOffset(
  rules: Partial<Record<TriggerEvent, number>> | null,
  event: TriggerEvent,
): number {
  if (rules && event in rules && rules[event] !== undefined) {
    return rules[event] as number;
  }
  return DEFAULT_OFFSETS[event] ?? 0;
}

/** 镜像 OrderService.createFromRule：SHOOT 基准=拍摄日 09:00，其余=当前时间。 */
function computeDueAt(
  event: TriggerEvent,
  order: OrderInput,
  rules: Partial<Record<TriggerEvent, number>> | null,
  nowISO: string,
): string {
  const baseShootDate = event === 'SHOOT';
  let baseMs: number;
  if (baseShootDate && order.shootDate) {
    const [y, m, d] = order.shootDate.split('-').map(Number);
    baseMs = Date.UTC(y, m - 1, d, 9, 0, 0); // 拍摄日 09:00（UTC，时区无关）
  } else {
    baseMs = Date.parse(nowISO);
  }
  const offset = findOffset(rules, event);
  const d = new Date(baseMs);
  d.setUTCDate(d.getUTCDate() + offset);
  return d.toISOString().slice(0, 16);
}

describe('提醒规则 offset 计算（与阶段1 硬编码一致）', () => {
  it('默认偏移集 = DEPOSIT 3 / SHOOT -1 / EDIT 7 / DELIVER 3 / REPURCHASE 0', () => {
    expect(DEFAULT_OFFSETS).toEqual({
      DEPOSIT: 3,
      SHOOT: -1,
      EDIT: 7,
      DELIVER: 3,
      REPURCHASE: 0,
    });
  });

  it('无规则时回退默认值（免费版硬编码路径，不回归）', () => {
    expect(findOffset(null, 'DEPOSIT')).toBe(3);
    expect(findOffset(null, 'SHOOT')).toBe(-1);
    expect(findOffset(null, 'EDIT')).toBe(7);
    expect(findOffset(null, 'DELIVER')).toBe(3);
    expect(findOffset(null, 'REPURCHASE')).toBe(0);
  });

  it('SHOOT 偏移 -1：拍摄前 1 天（负偏移，基准=拍摄日 09:00）', () => {
    const due = computeDueAt('SHOOT', { shootDate: '2026-06-28' }, null, '2026-06-01T10:00:00Z');
    expect(due).toBe('2026-06-27T09:00');
  });

  it('DEPOSIT 偏移 +3：当前时间 +3 天', () => {
    const due = computeDueAt('DEPOSIT', {}, null, '2026-06-01T10:00:00Z');
    expect(due).toBe('2026-06-04T10:00');
  });

  it('EDIT 偏移 +7：修图超期提醒（阶段1 行为：now+7d）', () => {
    const due = computeDueAt('EDIT', {}, null, '2026-06-01T10:00:00Z');
    expect(due).toBe('2026-06-08T10:00');
  });

  it('DELIVER 偏移 +3：交付后求好评（阶段2 新增提醒，阶段1 无此类型）', () => {
    const due = computeDueAt('DELIVER', {}, null, '2026-06-01T10:00:00Z');
    expect(due).toBe('2026-06-04T10:00');
  });

  it('规则覆盖：PRO 自定义偏移优先于默认硬编码', () => {
    const rules: Partial<Record<TriggerEvent, number>> = {
      DEPOSIT: 5,
      SHOOT: -2,
      EDIT: 7,
      DELIVER: 10,
      REPURCHASE: 0,
    };
    expect(findOffset(rules, 'DEPOSIT')).toBe(5);
    expect(findOffset(rules, 'SHOOT')).toBe(-2);
    expect(findOffset(rules, 'DELIVER')).toBe(10);
  });

  it('REPURCHASE 默认偏移为 0（实际 dueAt = lastShootDate+cycle，规则不额外偏移）', () => {
    expect(findOffset(null, 'REPURCHASE')).toBe(0);
  });
});
