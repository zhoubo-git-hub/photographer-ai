import { useRouter } from '@tarojs/taro';
import { Text, View } from '@tarojs/components';
import { useOrder, useQuote } from '@photogai/shared/hooks';
import { formatAmount, formatDate, formatDateTime } from '@photogai/shared/domain';
import PageContainer from '@/components/PageContainer';
import StateView from '@/components/StateView';
import StatusTag from '@/components/StatusTag';
import FieldRow from '@/components/FieldRow';
import { parseIdParam, toCustomerDetail } from '@/lib/nav';
import './index.scss';

/**
 * 订单详情页（查看版，只读展示）。
 * 路由参数 id 经 parseIdParam 安全解析；id 非法时不发起请求。
 * 若 uiStore 存在待填入的 AI 报价（来自报价页），展示一条引导 banner。
 */
export default function OrderDetailPage() {
  const router = useRouter();
  const id = parseIdParam(router.params.id);
  const { data: order, isLoading, error, refetch } = useOrder(id, id > 0);
  const { pendingQuote } = useQuote();

  return (
    <PageContainer>
      <View className="order-detail">
        <StateView
          loading={isLoading}
          error={error}
          empty={!isLoading && !error && !order}
          emptyText="订单不存在"
          onRetry={refetch}
        />

        {!isLoading && !error && order ? (
          <View className="order-detail__body">
            <View className="order-detail__header">
              <Text className="order-detail__title">{order.title}</Text>
              <StatusTag status={order.status} />
            </View>

            {pendingQuote ? (
              <View className="order-detail__banner">
                <Text className="order-detail__banner-text">
                  已有待填入的 AI 报价结果，请到报价页一键填入订单
                </Text>
              </View>
            ) : null}

            <FieldRow
              label="客户"
              value={order.customerName}
              onClick={() => toCustomerDetail(order.customerId)}
            />
            <FieldRow label="拍摄类型" value={order.shootType} />
            <FieldRow label="金额" value={formatAmount(order.amount)} />
            <FieldRow label="定金" value={formatAmount(order.depositAmount)} />
            <FieldRow
              label="拍摄日期"
              value={order.shootDate ? formatDate(order.shootDate) : undefined}
            />
            <FieldRow
              label="结束日期"
              value={order.shootEndDate ? formatDate(order.shootEndDate) : undefined}
            />
            <FieldRow
              label="时长(小时)"
              value={order.durationHours != null ? String(order.durationHours) : undefined}
            />
            <FieldRow
              label="张数"
              value={order.photoCount != null ? String(order.photoCount) : undefined}
            />
            <FieldRow label="地区" value={order.region} />
            <FieldRow label="风格" value={order.style} />
            <FieldRow label="报价建议" value={order.quoteSuggestion} multiline />
            <FieldRow
              label="创建时间"
              value={order.createdAt ? formatDateTime(order.createdAt) : undefined}
            />
          </View>
        ) : null}
      </View>
    </PageContainer>
  );
}
