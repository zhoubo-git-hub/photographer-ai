import { describe, it, expect } from 'vitest';

/**
 * 档期冲突检测 —— 重叠判定逻辑验证。
 *
 * 本测试把后端 `OrderRepository.findConflicts` 的 @Query 重叠条件原样转录为 JS，
 * 用于在没有 JDK17/Maven 的环境下独立运行、实证后端 SQL 的重叠判定是否存在缺陷。
 *
 * 后端 SQL（OrderRepository.java）的两条重叠条件是：
 *   AND COALESCE(o.shootEndDate, o.shootDate) >= COALESCE(:end, :start)   // o_end >= c_end
 *   AND o.shootDate <= COALESCE(:end, :start)                            // o_start <= c_end
 * 其中 c_end = COALESCE(candidate.shootEndDate, candidate.shootDate)。
 *
 * 正确的区间重叠应为：candidate_start <= o_end AND o_start <= c_end。
 * 后端把第二条件的左操作数写成了 c_end（候选结束日）而不是 candidate_start（候选开始日），
 * 导致"候选跨多天、且已有订单落在候选区间内/前部"的重叠被漏判。
 */

type DateStr = string; // 'YYYY-MM-DD'

function coalesce(end: DateStr | null | undefined, start: DateStr): DateStr {
  return end && end.length > 0 ? end : start;
}

/** 后端当前实现（逐字转录 OrderRepository.findConflicts 的重叠条件）。 */
function backendOverlap(
  candStart: DateStr,
  candEnd: DateStr | null,
  oStart: DateStr,
  oEnd: DateStr | null,
): boolean {
  const cEnd = coalesce(candEnd, candStart);
  const oEndReal = coalesce(oEnd, oStart);
  return oEndReal >= cEnd && oStart <= cEnd; // 注意：第二条件用的是 cEnd 而非 candStart
}

/** 正确的区间重叠判定（规格要求）。 */
function correctOverlap(
  candStart: DateStr,
  candEnd: DateStr | null,
  oStart: DateStr,
  oEnd: DateStr | null,
): boolean {
  const cEnd = coalesce(candEnd, candStart);
  const oEndReal = coalesce(oEnd, oStart);
  return candStart <= oEndReal && oStart <= cEnd;
}

describe('档期冲突：重叠判定（含后端 BUG 实证）', () => {
  describe('正确重叠逻辑（规格）应判为冲突的场景', () => {
    it('同日同天 → 冲突', () => {
      expect(correctOverlap('2026-06-28', '2026-06-28', '2026-06-28', '2026-06-28')).toBe(true);
    });

    it('候选跨多天，已有订单落在候选区间内（被候选包含）→ 冲突', () => {
      expect(correctOverlap('2026-07-01', '2026-07-05', '2026-07-03', '2026-07-03')).toBe(true);
    });

    it('候选跨多天，已有订单与候选前部部分重叠 → 冲突', () => {
      expect(correctOverlap('2026-07-03', '2026-07-10', '2026-07-01', '2026-07-04')).toBe(true);
    });

    it('已有订单完全包含候选 → 冲突', () => {
      expect(correctOverlap('2026-07-02', '2026-07-03', '2026-07-01', '2026-07-05')).toBe(true);
    });
  });

  describe('正确重叠逻辑（规格）不应判为冲突的场景', () => {
    it('不同日、不重叠 → 不冲突', () => {
      expect(correctOverlap('2026-06-29', '2026-06-29', '2026-06-28', '2026-06-28')).toBe(false);
    });

    it('多天不重叠 → 不冲突', () => {
      expect(correctOverlap('2026-07-10', '2026-07-12', '2026-07-01', '2026-07-03')).toBe(false);
    });
  });

  describe('后端当前 SQL 实现（实证 BUG）', () => {
    it('同日同天 → 后端也判冲突（此场景正确）', () => {
      expect(backendOverlap('2026-06-28', '2026-06-28', '2026-06-28', '2026-06-28')).toBe(true);
    });

    it('已有订单完全包含候选 → 后端也判冲突（此场景正确）', () => {
      expect(backendOverlap('2026-07-02', '2026-07-03', '2026-07-01', '2026-07-05')).toBe(true);
    });

    /**
     * BUG 实证：候选跨多天、已有订单落在候选区间内（被候选包含）时，
     * 正确的重叠逻辑应判冲突，但后端当前 SQL 返回 false（漏判）。
     */
    it('【后端 BUG】候选跨多天、已有订单被候选包含 → 后端漏判（应为冲突）', () => {
      const candStart = '2026-07-01';
      const candEnd = '2026-07-05';
      const oStart = '2026-07-03';
      const oEnd = '2026-07-03';
      // 规格要求：应为 true（冲突）
      expect(correctOverlap(candStart, candEnd, oStart, oEnd)).toBe(true);
      // 后端当前实现：错误地返回 false（漏判）
      expect(backendOverlap(candStart, candEnd, oStart, oEnd)).toBe(false);
    });

    /**
     * BUG 实证：候选跨多天、已有订单与候选前部部分重叠时，后端同样漏判。
     */
    it('【后端 BUG】候选跨多天、与已有订单前部部分重叠 → 后端漏判（应为冲突）', () => {
      const candStart = '2026-07-03';
      const candEnd = '2026-07-10';
      const oStart = '2026-07-01';
      const oEnd = '2026-07-04';
      expect(correctOverlap(candStart, candEnd, oStart, oEnd)).toBe(true);
      expect(backendOverlap(candStart, candEnd, oStart, oEnd)).toBe(false);
    });
  });
});
