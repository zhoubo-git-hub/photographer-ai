import { Text, View } from '@tarojs/components';
import { useQuery } from '@tanstack/react-query';
import { reminderApi } from '@photogai/shared/api';
import { REMINDER_LABELS } from '@photogai/shared/types';
import { formatDateTime } from '@photogai/shared/domain';
import PageContainer from '@/components/PageContainer';
import StateView from '@/components/StateView';
import './index.scss';

/**
 * 通知中心（「我的」下二级页）。
 * 裁定 R2：通知中心用 reminderApi.list() 充当，作为只读信息流展示全部提醒/通知
 * （含订阅升级/到期等业务事件），与提醒事项页（可操作）共用同一数据源、不同定位。
 */
export default function NoticesPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['notices'],
    queryFn: () => reminderApi.list(),
  });

  const list = data ?? [];

  return (
    <PageContainer>
      <View className="notices-page">
        <StateView
          loading={isLoading}
          error={error}
          empty={!isLoading && !error && list.length === 0}
          emptyText="暂无通知"
          onRetry={refetch}
        />

        {!isLoading && !error && list.length > 0 ? (
          <View className="notices-page__list">
            {list.map((item) => (
              <View
                key={item.id}
                className={`notice-item notice-item--${item.status.toLowerCase()}`}
              >
                <View className="notice-item__main">
                  <Text className="notice-item__type">{REMINDER_LABELS[item.type]}</Text>
                  <Text className="notice-item__sub">
                    {item.orderTitle || item.customerName || '-'}
                  </Text>
                </View>
                {item.dueAt ? (
                  <Text className="notice-item__time">{formatDateTime(item.dueAt)}</Text>
                ) : null}
              </View>
            ))}
          </View>
        ) : null}
      </View>
    </PageContainer>
  );
}
