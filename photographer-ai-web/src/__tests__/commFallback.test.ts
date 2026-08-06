import { describe, it, expect } from 'vitest';

/**
 * AI 沟通助手：场景话术 + LLM 降级验证（镜像后端 AiCommService.buildFallback + generate 降级分支）。
 *
 * 关键约定（US-P2-01/02/03/10 / 架构 §7）：
 *   - 每个场景产出非空中文话术，含客户称呼与上下文（尾款金额 / 拍摄日等）；
 *   - LLM 不可用（密钥缺失 / 调用异常）→ 降级为规则模板，fallback=true，不抛异常、不空；
 *   - 复购话术复用同一端点（scenario=REPURCHASE）。
 */

type Scenario =
  | 'URGE_DEPOSIT'
  | 'URGE_FINAL'
  | 'PRE_SHOOT'
  | 'DELIVER_REVIEW'
  | 'FAQ'
  | 'REPURCHASE';

interface OrderInput {
  shootType?: string | null;
  shootDate?: string | null;
  amount?: number | null;
  depositAmount?: number | null;
}
interface CustomerInput {
  name?: string | null;
}

function balance(order: OrderInput | null): number {
  const a = order?.amount ?? 0;
  const d = order?.depositAmount ?? 0;
  return a - d;
}
function fmt(v: number): string {
  return '¥' + v;
}

/** 镜像 AiCommService.buildFallback。 */
function buildFallback(scenario: Scenario, order: OrderInput | null, customer: CustomerInput | null): string {
  const name = customer?.name ?? '客户';
  const type = order?.shootType ?? '拍摄';
  const shootDate = order?.shootDate ?? '约定档期';
  switch (scenario) {
    case 'URGE_DEPOSIT':
      return `${name}您好，您的${type}拍摄定金还差一步到位，方便时微信转我就行，我好帮您锁定档期~`;
    case 'URGE_FINAL':
      return `${name}您好，您${type}尾款 ${fmt(balance(order))} 还差最后一步，方便时微信转我就行~`;
    case 'PRE_SHOOT':
      return `${name}您好，您${type}拍摄安排在 ${shootDate}，当天建议早睡、素颜护肤，具体流程我稍后发您~`;
    case 'DELIVER_REVIEW':
      return `${name}您好，您的${type}成片已交付，方便时给个好评/晒图就更好啦，后续有任何修图需求随时找我~`;
    case 'FAQ':
      return `${name}您好，关于${type}拍摄您有任何疑问都可以问我，我会第一时间帮您解答~`;
    case 'REPURCHASE':
      return `${name}您好，去年为您拍摄的${type}一周年啦，预约续拍享老客专属礼遇，有空聊聊？~`;
    default:
      return `${name}您好，感谢您的信任，期待为您服务~`;
  }
}

interface CommResp {
  text: string;
  scenario: Scenario;
  fallback: boolean;
}

/** 镜像 AiCommService.generate：LLM 抛异常 → 降级 fallback=true，不抛异常。 */
function generate(
  req: { scenario: Scenario; order?: OrderInput | null; customer?: CustomerInput | null },
  llmThrows: boolean,
): CommResp {
  const scenario = req.scenario;
  const order = req.order ?? null;
  const customer = req.customer ?? null;
  try {
    if (llmThrows) throw new Error('LLM 不可用');
    // 正常路径用降级文本代替（仅验证降级分支与契约）
    return { text: buildFallback(scenario, order, customer), scenario, fallback: false };
  } catch {
    return { text: buildFallback(scenario, order, customer), scenario, fallback: true };
  }
}

describe('AI 沟通助手：场景话术 + LLM 降级', () => {
  const customer: CustomerInput = { name: '王小姐' };
  const order: OrderInput = { shootType: '婚纱写真', shootDate: '2026-06-28', amount: 2999, depositAmount: 1000 };

  it('催尾款话术含客户称呼与尾款金额（¥1999）', () => {
    const t = buildFallback('URGE_FINAL', order, customer);
    expect(t).toContain('王小姐');
    expect(t).toContain(fmt(balance(order)));
    expect(t).toContain('尾款');
  });

  it('催定金话术含拍摄类型', () => {
    const t = buildFallback('URGE_DEPOSIT', order, customer);
    expect(t).toContain('婚纱写真');
    expect(t).toContain('定金');
  });

  it('拍摄前提醒含拍摄日', () => {
    const t = buildFallback('PRE_SHOOT', order, customer);
    expect(t).toContain('2026-06-28');
  });

  it('交付后求好评话术非空', () => {
    expect(buildFallback('DELIVER_REVIEW', order, customer).length).toBeGreaterThan(0);
  });

  it('通用答疑话术非空', () => {
    expect(buildFallback('FAQ', order, customer).length).toBeGreaterThan(0);
  });

  it('复购话术含「一周年」且复用同一端点（scenario=REPURCHASE）', () => {
    const t = buildFallback('REPURCHASE', order, customer);
    expect(t).toContain('一周年');
    expect(t).toContain('王小姐');
  });

  it('降级：LLM 抛异常时返回 fallback=true 且话术非空，不抛异常', () => {
    const res = generate({ scenario: 'URGE_FINAL', order, customer }, true);
    expect(res.fallback).toBe(true);
    expect(res.text.length).toBeGreaterThan(0);
    expect(res.scenario).toBe('URGE_FINAL');
  });

  it('降级：复购话术（REPURCHASE，仅传 customer）LLM 异常同样 fallback=true 非空', () => {
    const res = generate({ scenario: 'REPURCHASE', customer }, true);
    expect(res.fallback).toBe(true);
    expect(res.text).toContain('一周年');
  });

  it('正常：LLM 可用时 fallback=false，话术非空', () => {
    const res = generate({ scenario: 'URGE_DEPOSIT', order, customer }, false);
    expect(res.fallback).toBe(false);
    expect(res.text.length).toBeGreaterThan(0);
  });

  it('客户为 null 时回退默认称呼「客户」，不抛异常', () => {
    const res = generate({ scenario: 'URGE_FINAL', order: null, customer: null }, true);
    expect(res.text).toContain('客户');
    expect(res.fallback).toBe(true);
  });
});
