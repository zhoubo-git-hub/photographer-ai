import { describe, it, expect } from 'vitest';
import {
  STATUS_COLUMNS,
  NEXT_STATUSES,
  STATUS_LABELS,
  type OrderStatus,
} from '../types/models';

// 与后端 OrderStateMachine 的流转链完全一致：
// CONSULT → DEPOSIT → SHOOT → EDIT → DELIVER → REPURCHASE（仅相邻可流转）。
const CANONICAL_CHAIN: OrderStatus[] = [
  'CONSULT',
  'DEPOSIT',
  'SHOOT',
  'EDIT',
  'DELIVER',
  'REPURCHASE',
];

describe('订单状态机（前端 NEXT_STATUSES ↔ 后端 OrderStateMachine 一致性）', () => {
  it('状态列顺序与流转链声明顺序一致', () => {
    expect(STATUS_COLUMNS).toEqual(CANONICAL_CHAIN);
  });

  it('每个状态仅允许相邻状态流转（正向 + 回退），且与后端双向相邻集合一致', () => {
    const expected: Record<OrderStatus, OrderStatus[]> = {
      CONSULT: ['DEPOSIT'],
      DEPOSIT: ['CONSULT', 'SHOOT'],
      SHOOT: ['DEPOSIT', 'EDIT'],
      EDIT: ['SHOOT', 'DELIVER'],
      DELIVER: ['EDIT', 'REPURCHASE'],
      REPURCHASE: ['DELIVER'],
    };

    (Object.keys(expected) as OrderStatus[]).forEach((from) => {
      // 顺序无关比较
      expect([...NEXT_STATUSES[from]].sort()).toEqual([...expected[from]].sort());
    });
  });

  it('非相邻跳变不在允许集合内（如 CONSULT→SHOOT、EDIT→REPURCHASE）', () => {
    expect(NEXT_STATUSES.CONSULT).not.toContain('SHOOT');
    expect(NEXT_STATUSES.CONSULT).not.toContain('EDIT');
    expect(NEXT_STATUSES.EDIT).not.toContain('REPURCHASE');
    expect(NEXT_STATUSES.DELIVER).not.toContain('CONSULT');
  });

  it('相邻流转是对称的（A→B 允许则 B→A 也允许）', () => {
    for (let i = 0; i < CANONICAL_CHAIN.length; i++) {
      const a = CANONICAL_CHAIN[i];
      const b = CANONICAL_CHAIN[i + 1];
      if (!b) break;
      expect(NEXT_STATUSES[a]).toContain(b);
      expect(NEXT_STATUSES[b]).toContain(a);
    }
  });

  it('所有状态均有中文标签', () => {
    CANONICAL_CHAIN.forEach((s) => {
      expect(STATUS_LABELS[s]).toBeTruthy();
    });
  });
});
