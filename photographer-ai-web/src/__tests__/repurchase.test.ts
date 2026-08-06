import { describe, it, expect } from 'vitest';

/**
 * 复购引擎：候选筛选 + 幂等去重验证（镜像后端 RepurchaseService.scanStudio + CustomerRepository.findRepurchaseCandidates）。
 *
 * 关键约定（PRD US-P2-09 / 架构 §2.3）：
 *   - 候选：lastShootDate + repurchaseCycleDays ≤ today 且 repurchaseEnabled=true 且未软删；
 *   - 周期缺省默认 365 天；
 *   - 幂等：existsByStudioIdAndCustomerIdAndTypeAndStatus(REPURCHASE, PENDING) 已存在则跳过；
 *   - dueAt = lastShootDate + cycle（当天 00:00）。
 * 日期计算走 UTC，避免时区差异。
 */

interface CustomerInput {
  id: number;
  studioId: number;
  name: string;
  deletedAt: string | null;
  lastShootDate: string | null;
  repurchaseEnabled?: boolean | null;
  repurchaseCycleDays?: number | null;
}

interface RepurchaseReminder {
  studioId: number;
  customerId: number;
  type: 'REPURCHASE';
  dueAt: string;
  status: 'PENDING';
}

/** 时区安全的日期加天数。 */
function addDays(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  dt.setUTCDate(dt.getUTCDate() + days);
  return dt.toISOString().slice(0, 10);
}

/** 镜像 CustomerRepository.findRepurchaseCandidates 的 JPQL 过滤语义。 */
function findRepurchaseCandidates(customers: CustomerInput[], studioId: number, today: string): CustomerInput[] {
  return customers.filter((c) => {
    if (c.studioId !== studioId) return false;
    if (c.deletedAt !== null) return false;
    if (c.lastShootDate === null) return false;
    if ((c.repurchaseEnabled ?? true) !== true) return false;
    const cycle = c.repurchaseCycleDays ?? 365;
    return addDays(c.lastShootDate, cycle) <= today;
  });
}

/** 镜像 RepurchaseService.scanStudio 的幂等扫描。 */
function scan(customers: CustomerInput[], studioId: number, today: string, existing: RepurchaseReminder[]): RepurchaseReminder[] {
  const created: RepurchaseReminder[] = [];
  for (const c of findRepurchaseCandidates(customers, studioId, today)) {
    const exists = existing.some(
      (r) => r.studioId === studioId && r.customerId === c.id && r.type === 'REPURCHASE' && r.status === 'PENDING',
    );
    if (exists) continue; // 幂等：已有待办则不重复生成
    const cycle = c.repurchaseCycleDays ?? 365;
    created.push({
      studioId,
      customerId: c.id,
      type: 'REPURCHASE',
      dueAt: addDays(c.lastShootDate as string, cycle) + 'T00:00:00',
      status: 'PENDING',
    });
  }
  return created;
}

const STUDIO = 1;
const TODAY = '2026-06-20';

describe('复购引擎：候选筛选', () => {
  it('lastShootDate + cycle ≤ today 且开启复购 → 命中', () => {
    const cs: CustomerInput[] = [
      { id: 1, studioId: STUDIO, name: '王', deletedAt: null, lastShootDate: '2025-06-20', repurchaseEnabled: true, repurchaseCycleDays: 365 },
    ];
    expect(findRepurchaseCandidates(cs, STUDIO, TODAY)).toHaveLength(1);
  });

  it('周期边界当天（lastShoot+cycle == today）视为触发', () => {
    const cs: CustomerInput[] = [
      { id: 1, studioId: STUDIO, name: '王', deletedAt: null, lastShootDate: '2025-06-20', repurchaseEnabled: true, repurchaseCycleDays: 365 },
    ];
    expect(findRepurchaseCandidates(cs, STUDIO, '2026-06-20')).toHaveLength(1);
    expect(findRepurchaseCandidates(cs, STUDIO, '2026-06-19')).toHaveLength(0);
  });

  it('未到周期（lastShoot+cycle > today）不触发', () => {
    const cs: CustomerInput[] = [
      { id: 1, studioId: STUDIO, name: '李', deletedAt: null, lastShootDate: '2025-12-01', repurchaseEnabled: true, repurchaseCycleDays: 365 },
    ];
    expect(findRepurchaseCandidates(cs, STUDIO, TODAY)).toHaveLength(0);
  });

  it('repurchaseEnabled=false 不触发', () => {
    const cs: CustomerInput[] = [
      { id: 1, studioId: STUDIO, name: '王', deletedAt: null, lastShootDate: '2025-06-20', repurchaseEnabled: false, repurchaseCycleDays: 365 },
    ];
    expect(findRepurchaseCandidates(cs, STUDIO, TODAY)).toHaveLength(0);
  });

  it('lastShootDate 为空不触发', () => {
    const cs: CustomerInput[] = [
      { id: 1, studioId: STUDIO, name: '王', deletedAt: null, lastShootDate: null, repurchaseEnabled: true, repurchaseCycleDays: 365 },
    ];
    expect(findRepurchaseCandidates(cs, STUDIO, TODAY)).toHaveLength(0);
  });

  it('软删除客户不触发', () => {
    const cs: CustomerInput[] = [
      { id: 1, studioId: STUDIO, name: '王', deletedAt: '2026-01-01', lastShootDate: '2025-06-20', repurchaseEnabled: true, repurchaseCycleDays: 365 },
    ];
    expect(findRepurchaseCandidates(cs, STUDIO, TODAY)).toHaveLength(0);
  });

  it('非本工作室客户不触发（多租户隔离）', () => {
    const cs: CustomerInput[] = [
      { id: 1, studioId: 2, name: '王', deletedAt: null, lastShootDate: '2025-06-20', repurchaseEnabled: true, repurchaseCycleDays: 365 },
    ];
    expect(findRepurchaseCandidates(cs, STUDIO, TODAY)).toHaveLength(0);
  });

  it('周期缺省按 365 天计算', () => {
    const cs: CustomerInput[] = [
      { id: 1, studioId: STUDIO, name: '王', deletedAt: null, lastShootDate: '2025-06-20', repurchaseEnabled: true, repurchaseCycleDays: null },
    ];
    expect(findRepurchaseCandidates(cs, STUDIO, TODAY)).toHaveLength(1);
  });
});

describe('复购引擎：幂等去重', () => {
  const candidates: CustomerInput[] = [
    { id: 1, studioId: STUDIO, name: '王', deletedAt: null, lastShootDate: '2025-06-20', repurchaseEnabled: true, repurchaseCycleDays: 365 },
    { id: 2, studioId: STUDIO, name: '李', deletedAt: null, lastShootDate: '2025-03-01', repurchaseEnabled: true, repurchaseCycleDays: 365 },
  ];

  it('首次扫描为每个候选生成一条 REPURCHASE 提醒', () => {
    const created = scan(candidates, STUDIO, TODAY, []);
    expect(created).toHaveLength(2);
    expect(created[0].dueAt).toBe('2026-06-20T00:00:00'); // 2025-06-20 + 365
    expect(created.every((r) => r.type === 'REPURCHASE' && r.status === 'PENDING')).toBe(true);
  });

  it('存在 PENDING 提醒后再次扫描不再重复生成（幂等）', () => {
    const first = scan(candidates, STUDIO, TODAY, []);
    const second = scan(candidates, STUDIO, TODAY, first);
    expect(second).toHaveLength(0);
  });

  it('部分已存在时，仅缺失客户被补充生成', () => {
    const partial = scan(candidates.slice(0, 1), STUDIO, TODAY, []); // 仅客户1
    const next = scan(candidates, STUDIO, TODAY, partial);
    expect(next).toHaveLength(1);
    expect(next[0].customerId).toBe(2);
  });
});
