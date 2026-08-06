import { useRouter } from '@tarojs/taro';
import { Text, View } from '@tarojs/components';
import { useCustomer } from '@photogai/shared/hooks';
import { formatAmount, formatDate } from '@photogai/shared/domain';
import PageContainer from '@/components/PageContainer';
import StateView from '@/components/StateView';
import StatusTag from '@/components/StatusTag';
import FieldRow from '@/components/FieldRow';
import { parseIdParam, toOrderDetail } from '@/lib/nav';
import './index.scss';

/**
 * 客户详情页（查看版，只读展示）。
 * 展示画像字段（联系方式/标签/复购周期/生日等）与历史订单（可点进订单详情）。
 */
export default function CustomerDetailPage() {
  const router = useRouter();
  const id = parseIdParam(router.params.id);
  const { data: customer, isLoading, error, refetch } = useCustomer(id, id > 0);
  const orders = customer?.orders ?? [];

  return (
    <PageContainer>
      <View className="customer-detail">
        <StateView
          loading={isLoading}
          error={error}
          empty={!isLoading && !error && !customer}
          emptyText="客户不存在"
          onRetry={refetch}
        />

        {!isLoading && !error && customer ? (
          <View className="customer-detail__body">
            <Text className="customer-detail__name">{customer.name}</Text>

            <FieldRow label="电话" value={customer.phone} />
            <FieldRow label="微信号" value={customer.wechatId} />
            <FieldRow label="标签" value={customer.tags} />
            <FieldRow label="来源渠道" value={customer.sourceChannel} />
            <FieldRow label="生日" value={customer.birthday ? formatDate(customer.birthday) : undefined} />
            <FieldRow
              label="纪念日"
              value={customer.anniversary ? formatDate(customer.anniversary) : undefined}
            />
            <FieldRow
              label="最近拍摄"
              value={customer.lastShootDate ? formatDate(customer.lastShootDate) : undefined}
            />
            <FieldRow
              label="复购周期(天)"
              value={customer.repurchaseCycleDays != null ? String(customer.repurchaseCycleDays) : undefined}
            />
            <FieldRow label="备注" value={customer.note} multiline />
            <FieldRow
              label="累计订单"
              value={customer.orderCount != null ? `${customer.orderCount} 单` : undefined}
            />
            <FieldRow
              label="最近下单"
              value={customer.lastOrderAt ? formatDate(customer.lastOrderAt) : undefined}
            />
            <FieldRow label="最近金额" value={formatAmount(customer.lastAmount)} />

            {orders.length > 0 ? (
              <View className="customer-detail__orders">
                <Text className="customer-detail__orders-title">历史订单</Text>
                {orders.map((order) => (
                  <View
                    key={order.id}
                    className="customer-detail__order"
                    onClick={() => toOrderDetail(order.id)}
                  >
                    <Text className="customer-detail__order-title">{order.title}</Text>
                    <StatusTag status={order.status} small />
                  </View>
                ))}
              </View>
            ) : null}
          </View>
        ) : null}
      </View>
    </PageContainer>
  );
}
