import { useState } from 'react';
import { Text, View } from '@tarojs/components';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { reminderApi } from '@photogai/shared/api';
import { queryKeys } from '@photogai/shared/hooks';
import type { Reminder, ReminderStatus } from '@photogai/shared/types';
import PageContainer from '@/components/PageContainer';
import StateView from '@/components/StateView';
import ReminderCard from '@/components/ReminderCard';
import { showApiError } from '@/lib/toast';
import './index.scss';

type ReminderFilter = ReminderStatus | 'ALL';

/**
 * 提醒事项页（「我的」下二级页）。
 * 数据来自 shared reminderApi.list（无对应 hooks，故直接 useQuery）。
 * 支持按状态筛选，未完成项可「完成」：调用 reminderApi.updateStatus 后使提醒缓存失效。
 */
export default function RemindersPage() {
  const [filter, setFilter] = useState<ReminderFilter>('ALL');
  const qc = useQueryClient();

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: [...queryKeys.reminders, filter],
    queryFn: () =>
      reminderApi.list(filter === 'ALL' ? undefined : (filter as ReminderStatus)),
  });

  const list = data ?? [];

  const updateStatus = useMutation({
    mutationFn: (vars: { id: number; status: ReminderStatus }) =>
      reminderApi.updateStatus(vars.id, vars.status),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: queryKeys.reminders });
    },
    onError: (err: unknown) => showApiError(err, '操作失败，请重试'),
  });

  const handleDone = (item: Reminder): void => {
    if (item.status === 'DONE') {
      return;
    }
    updateStatus.mutate({ id: item.id, status: 'DONE' });
  };

  return (
    <PageContainer>
      <View className="reminders-page">
        <View className="reminders-page__filter">
          <View
            className={`reminders-page__chip ${
              filter === 'ALL' ? 'reminders-page__chip--active' : ''
            }`}
            onClick={() => setFilter('ALL')}
          >
            <Text className="reminders-page__chip-text">全部</Text>
          </View>
          <View
            className={`reminders-page__chip ${
              filter === 'PENDING' ? 'reminders-page__chip--active' : ''
            }`}
            onClick={() => setFilter('PENDING')}
          >
            <Text className="reminders-page__chip-text">待办</Text>
          </View>
          <View
            className={`reminders-page__chip ${
              filter === 'DONE' ? 'reminders-page__chip--active' : ''
            }`}
            onClick={() => setFilter('DONE')}
          >
            <Text className="reminders-page__chip-text">已完成</Text>
          </View>
        </View>

        <StateView
          loading={isLoading}
          error={error}
          empty={!isLoading && !error && list.length === 0}
          emptyText="暂无提醒"
          onRetry={refetch}
        />

        {!isLoading && !error && list.length > 0 ? (
          <View className="reminders-page__list">
            {list.map((item) => (
              <ReminderCard key={item.id} reminder={item} onDone={handleDone} />
            ))}
          </View>
        ) : null}
      </View>
    </PageContainer>
  );
}
