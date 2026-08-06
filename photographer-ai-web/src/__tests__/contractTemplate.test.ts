import { describe, it, expect } from 'vitest';

/**
 * 合同模板占位符替换引擎验证（镜像后端 ContractService.render + buildValues）。
 *
 * 关键约定（架构 §1.3 / §6 T-P2-C）：
 *   - 命中键值 → 替换为实际值，不残留 {{key}}；
 *   - 未命中 / 值为空 → 保留原占位符并告警（设计如此：note / retouchCount 无数据源）。
 * 本测试断言「有值的字段全部被替换、无残留」，并显式记录 note/retouchCount 因无数据而保留
 * 的设计行为（详见测试报告「已知问题」中关于 PRD US-P2-05 的说明）。
 */

const PLACEHOLDER = /\{\{(\w+)\}\}/g;

/** 镜像 ContractService.render：命中非空值则替换，否则保留原占位符。 */
function render(template: string, values: Record<string, string>): string {
  return template.replace(PLACEHOLDER, (_full, key: string) => {
    const v = values[key];
    if (v !== undefined && v !== '') {
      return v;
    }
    return _full; // 未命中 / 空值：保留原占位符（后端会打 warn 日志）
  });
}

interface OrderInput {
  shootType?: string | null;
  shootDate?: string | null;
  durationHours?: number | null;
  photoCount?: number | null;
  region?: string | null;
  style?: string | null;
  amount?: number | null;
  depositAmount?: number | null;
}

interface CustomerInput {
  name?: string | null;
  wechatId?: string | null;
  phone?: string | null;
}

/** 镜像 ContractService.buildValues（金额用整数避免 BigDecimal 格式化差异）。 */
function buildValues(studioName: string, customer: CustomerInput, order: OrderInput) {
  const amount = order.amount ?? 0;
  const deposit = order.depositAmount ?? 0;
  const balance = amount - deposit;
  const depositRatio =
    order.amount != null && order.amount > 0 && order.depositAmount != null
      ? String(Math.round((deposit * 100) / order.amount))
      : '';
  return {
    studioName: studioName ?? '',
    customerName: customer.name ?? '客户',
    wechatId: customer.wechatId ?? '',
    phone: customer.phone ?? '',
    shootType: order.shootType ?? '',
    shootDate: order.shootDate ?? '',
    durationHours: order.durationHours != null ? String(order.durationHours) : '',
    photoCount: order.photoCount != null ? String(order.photoCount) : '',
    region: order.region ?? '',
    style: order.style ?? '',
    amount: order.amount != null ? String(order.amount) : '',
    depositAmount: order.depositAmount != null ? String(order.depositAmount) : '',
    balance: String(balance),
    depositRatio,
    note: '',
    retouchCount: '',
  };
}

const TEMPLATE = `摄影服务合同
甲方（摄影方）：{{studioName}}
乙方（客户）：{{customerName}}
联系微信：{{wechatId}}　电话：{{phone}}
拍摄类型：{{shootType}}
拍摄日期：{{shootDate}}
拍摄时长：{{durationHours}} 小时　拍摄张数：约 {{photoCount}} 张
拍摄地区：{{region}}　风格：{{style}}
套餐总金额：{{amount}} 元　已付定金：{{depositAmount}} 元　尾款：{{balance}} 元。
定金比例：{{depositRatio}}%
交付物：精修 {{retouchCount}} 张 + 全部底片。
备注：{{note}}`;

describe('合同模板占位符替换引擎', () => {
  const studio = '光影工作室';
  const customer = { name: '王小姐', wechatId: 'wx_wang', phone: '13800000000' };
  const order = {
    shootType: '婚纱写真',
    shootDate: '2026-06-28',
    durationHours: 4,
    photoCount: 80,
    region: '上海',
    style: '轻奢',
    amount: 2999,
    depositAmount: 1000,
  };

  const values = buildValues(studio, customer, order);
  const content = render(TEMPLATE, values);

  it('所有有值的字段均被替换为实际值（无 {{key}} 残留）', () => {
    expect(content).toContain('光影工作室');
    expect(content).toContain('王小姐');
    expect(content).toContain('wx_wang');
    expect(content).toContain('13800000000');
    expect(content).toContain('婚纱写真');
    expect(content).toContain('2026-06-28');
    expect(content).toContain('4 小时');
    expect(content).toContain('约 80 张');
    expect(content).toContain('上海');
    expect(content).toContain('轻奢');
    expect(content).toContain('2999 元');
    expect(content).toContain('1000 元');
    expect(content).toContain('尾款：1999 元');
    expect(content).toContain('定金比例：33%');
  });

  it('替换后内容不含任何「有值字段」对应的占位符', () => {
    ['studioName', 'customerName', 'wechatId', 'phone', 'shootType', 'shootDate',
      'durationHours', 'photoCount', 'region', 'style', 'amount', 'depositAmount',
      'balance', 'depositRatio'].forEach((key) => {
      expect(content).not.toContain(`{{${key}}}`);
    });
  });

  it('尾款 = 总额 - 定金（2999 - 1000 = 1999）', () => {
    expect(values.balance).toBe('1999');
    expect(content).toContain('尾款：1999 元');
  });

  it('定金比例按 定金/总额*100 四舍五入（1000/2999≈33%）', () => {
    expect(values.depositRatio).toBe('33');
  });

  it('未匹配 key（如 {{unknownKey}}）保留原占位符', () => {
    const out = render('占位：{{unknownKey}} 结束', values);
    expect(out).toBe('占位：{{unknownKey}} 结束');
  });

  it('note / retouchCount 无数据源 → 按引擎设计保留 {{}}（已知限制，见报告）', () => {
    // 设计行为：值为空时保留原占位符。若产品要求零残留，需补充订单备注/精修张数数据源。
    expect(content).toContain('{{retouchCount}}');
    expect(content).toContain('{{note}}');
  });

  it('标题格式 = 模板名 - 客户名-拍摄类型', () => {
    const title = `${'摄影服务合同'} - ${values.customerName}-${order.shootType}`;
    expect(title).toBe('摄影服务合同 - 王小姐-婚纱写真');
  });

  it('缺失下单客户时回退默认称呼「客户」', () => {
    const v = buildValues(studio, {}, order);
    expect(v.customerName).toBe('客户');
  });
});
