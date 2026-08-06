import { Text, View } from '@tarojs/components';
import type { Order } from '@photogai/shared/types';
import StatusTag from '@/components/StatusTag';
import { formatAmount, formatDate } from '@photogai/shared/domain';
import './index.scss';

interface OrderCardProps {
  order: Order;
  /** 点击卡片回调（传入订单 id）。 */
  onClick?: (id: number) => void;
}

/**
 * 订单列表卡片。
 * 状态徽标用 StatusTag（底层 shared orderStatusLabel），金额/日期用 shared 格式化函数，
 * 空值由格式化函数统一返回 '-'。文案与 Web 端逐字一致，禁止硬编码。
 */
export default function OrderCard({ order, onClick }: OrderCardProps) {
  return (
    <View className="order-card" onClick={() => onClick?.(order.id)}>
      <View className="order-card__top">
        <Text className="order-card__title">{order.title || '未命名订单'}</Text>
        <StatusTag status={order.status} small />
      </View>

      <View className="order-card__meta">
        <Text className="order-card__customer">{order.customerName || '散客'}</Text>
        {order.shootType ? (
          <Text className="order-card__type">{order.shootType}</Text>
        ) : null}
      </View>

      <View className="order-card__bottom">
        <Text className="order-card__amount">{formatAmount(order.amount)}</Text>
        {order.shootDate ? (
          <Text className="order-card__date">拍摄 {formatDate(order.shootDate)}</Text>
        ) : null}
      </View>
    </View>
  );
}
