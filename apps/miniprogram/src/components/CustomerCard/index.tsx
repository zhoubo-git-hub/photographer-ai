import { Text, View } from '@tarojs/components';
import type { Customer } from '@photogai/shared/types';
import { formatDate } from '@photogai/shared/domain';
import './index.scss';

interface CustomerCardProps {
  customer: Customer;
  /** 点击卡片回调（传入客户 id）。 */
  onClick?: (id: number) => void;
}

/**
 * 客户列表卡片。
 * 展示姓名、订单数、联系方式、最近拍摄日期；空值由各自格式化/占位处理。
 * 文案对齐 shared 字段，禁止硬编码。
 */
export default function CustomerCard({ customer, onClick }: CustomerCardProps) {
  return (
    <View className="customer-card" onClick={() => onClick?.(customer.id)}>
      <View className="customer-card__top">
        <Text className="customer-card__name">{customer.name}</Text>
        {customer.orderCount != null ? (
          <Text className="customer-card__count">{customer.orderCount} 单</Text>
        ) : null}
      </View>

      <View className="customer-card__meta">
        {customer.phone ? (
          <Text className="customer-card__line">电话 {customer.phone}</Text>
        ) : null}
        {customer.wechatId ? (
          <Text className="customer-card__line">微信 {customer.wechatId}</Text>
        ) : null}
        {!customer.phone && !customer.wechatId ? (
          <Text className="customer-card__line customer-card__line--empty">暂无联系方式</Text>
        ) : null}
      </View>

      {customer.lastShootDate ? (
        <Text className="customer-card__last">最近拍摄 {formatDate(customer.lastShootDate)}</Text>
      ) : null}
    </View>
  );
}
