import { useState } from 'react';
import { Text, View } from '@tarojs/components';
import { useOrders } from '@photogai/shared/hooks';
import type { OrderStatus } from '@photogai/shared/types';
import { STATUS_COLUMNS, STATUS_LABELS } from '@photogai/shared/types';
import PageContainer from '@/components/PageContainer';
import StateView from '@/components/StateView';
import OrderCard from '@/components/OrderCard';
import { toOrderDetail } from '@/lib/nav';
import './index.scss';

type StatusFilter = OrderStatus | 'ALL';

/**
 * 订单列表页（tab 首页）。
 * 顶部为状态筛选（全部 + 看板六态，标签取自 shared STATUS_LABELS），
 * 下方为 OrderCard 列表。数据来自 shared useOrders，三态由 StateView 统一渲染。
 */
export default function OrdersPage() {
  const [filter, setFilter] = useState<StatusFilter>('ALL');
  const { data, isLoading, error, refetch } = useOrders(
    filter === 'ALL' ? undefined : filter,
  );
  const list = data?.content ?? [];
  const total = data?.totalElements ?? 0;

  return (
    <PageContainer>
      <View className="orders-page">
        <View className="orders-page__filter">
          <View
            className={`orders-page__chip ${filter === 'ALL' ? 'orders-page__chip--active' : ''}`}
            onClick={() => setFilter('ALL')}
          >
            <Text className="orders-page__chip-text">全部</Text>
          </View>
          {STATUS_COLUMNS.map((status) => (
            <View
              key={status}
              className={`orders-page__chip ${
                filter === status ? 'orders-page__chip--active' : ''
              }`}
              onClick={() => setFilter(status)}
            >
              <Text className="orders-page__chip-text">{STATUS_LABELS[status]}</Text>
            </View>
          ))}
        </View>

        <StateView
          loading={isLoading}
          error={error}
          empty={!isLoading && !error && list.length === 0}
          emptyText="暂无订单"
          onRetry={refetch}
        />

        {!isLoading && !error && list.length > 0 ? (
          <View className="orders-page__list">
            <Text className="orders-page__count">共 {total} 笔订单</Text>
            {list.map((order) => (
              <OrderCard key={order.id} order={order} onClick={toOrderDetail} />
            ))}
          </View>
        ) : null}
      </View>
    </PageContainer>
  );
}
