import { Text, View } from '@tarojs/components';
import type { Reminder } from '@photogai/shared/types';
import { REMINDER_LABELS } from '@photogai/shared/types';
import { formatDateTime } from '@photogai/shared/domain';
import './index.scss';

interface ReminderCardProps {
  reminder: Reminder;
  /** 标记完成回调（仅未完成的提醒可点）。 */
  onDone?: (reminder: Reminder) => void;
}

/**
 * 提醒卡片。
 * 类型标签取自 shared REMINDER_LABELS（与 Web 端逐字一致）。
 * 已完成状态置灰并禁用操作；未完成提供「完成」动作。
 */
export default function ReminderCard({ reminder, onDone }: ReminderCardProps) {
  const done = reminder.status === 'DONE';

  return (
    <View className={`reminder-card ${done ? 'reminder-card--done' : ''}`}>
      <View className="reminder-card__main">
        <Text className="reminder-card__type">{REMINDER_LABELS[reminder.type]}</Text>
        <Text className="reminder-card__sub">
          {reminder.orderTitle || reminder.customerName || '-'}
        </Text>
        {reminder.dueAt ? (
          <Text className="reminder-card__due">截止 {formatDateTime(reminder.dueAt)}</Text>
        ) : null}
      </View>

      {!done ? (
        <View
          className="reminder-card__action"
          onClick={() => onDone?.(reminder)}
        >
          <Text className="reminder-card__action-text">完成</Text>
        </View>
      ) : (
        <Text className="reminder-card__done-tag">已完成</Text>
      )}
    </View>
  );
}
