import { useMemo, useState } from 'react';
import { Text, View } from '@tarojs/components';
import dayjs from 'dayjs';
import { useQuery } from '@tanstack/react-query';
import { scheduleApi } from '@photogai/shared/api';
import { queryKeys } from '@photogai/shared/hooks';
import { formatDate } from '@photogai/shared/domain';
import PageContainer from '@/components/PageContainer';
import StateView from '@/components/StateView';
import StatusTag from '@/components/StatusTag';
import { toOrderDetail } from '@/lib/nav';
import './index.scss';

/**
 * 档期页（月视图）。
 * shared 仅导出 scheduleApi.month(year,month)，无 hooks，故用 useQuery 直接消费；
 * queryKey 复用 shared queryKeys.scheduleMonth，保证与 Web 端缓存键一致。
 * 按月切换：prev/next 改 cursor，queryFn 自动随 queryKey 变化重新拉取。
 */
export default function SchedulePage() {
  const [cursor, setCursor] = useState<dayjs.Dayjs>(dayjs());
  const year = cursor.year();
  const month = cursor.month() + 1;

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: queryKeys.scheduleMonth(year, month),
    queryFn: () => scheduleApi.month(year, month),
  });

  const items = data ?? [];

  // 按拍摄日期分组（无日期的归到「未排期」），组内按日期升序。
  const grouped = useMemo(() => {
    const map = new Map<string, typeof items>();
    for (const item of items) {
      const key = item.shootDate ? formatDate(item.shootDate) : '未排期';
      if (!map.has(key)) {
        map.set(key, []);
      }
      map.get(key)?.push(item);
    }
    return Array.from(map.entries());
  }, [items]);

  const changeMonth = (delta: number): void => {
    setCursor((prev) => prev.add(delta, 'month'));
  };

  return (
    <PageContainer>
      <View className="schedule-page">
        <View className="schedule-page__bar">
          <View className="schedule-page__arrow" onClick={() => changeMonth(-1)}>
            <Text className="schedule-page__arrow-text">‹</Text>
          </View>
          <Text className="schedule-page__month">
            {year} 年 {month} 月
          </Text>
          <View className="schedule-page__arrow" onClick={() => changeMonth(1)}>
            <Text className="schedule-page__arrow-text">›</Text>
          </View>
        </View>

        <StateView
          loading={isLoading}
          error={error}
          empty={!isLoading && !error && items.length === 0}
          emptyText="本月暂无拍摄安排"
          onRetry={refetch}
        />

        {!isLoading && !error && grouped.length > 0
          ? grouped.map(([date, group]) => {
              const hasConflict = group.some((g) => g.conflict);
              return (
                <View key={date} className="schedule-page__group">
                  <Text className="schedule-page__date">{date}</Text>
                  {group.map((item) => (
                    <View
                      key={item.orderId}
                      className={`schedule-page__item ${
                        item.conflict ? 'schedule-page__item--conflict' : ''
                      }`}
                      onClick={() => toOrderDetail(item.orderId)}
                    >
                      <View className="schedule-page__item-main">
                        <Text className="schedule-page__item-title">{item.title}</Text>
                        <Text className="schedule-page__item-range">
                          {item.shootDate && item.shootEndDate
                            ? `${formatDate(item.shootDate)} ~ ${formatDate(item.shootEndDate)}`
                            : '全天'}
                        </Text>
                      </View>
                      <StatusTag status={item.status} small />
                    </View>
                  ))}
                  {hasConflict ? (
                    <Text className="schedule-page__conflict-note">该日存在档期冲突，请核对</Text>
                  ) : null}
                </View>
              );
            })
          : null}
      </View>
    </PageContainer>
  );
}
