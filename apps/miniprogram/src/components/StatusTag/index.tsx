import { Text } from '@tarojs/components';
import type { OrderStatus } from '@photogai/shared/types';
import { orderStatusLabel } from '@photogai/shared/domain';
import './index.scss';

interface StatusTagProps {
  status: OrderStatus;
  /** 小号样式（用于列表项密集展示）。 */
  small?: boolean;
}

/**
 * 订单状态徽标。
 * 文案一律取 shared 的 `orderStatusLabel`（底层 STATUS_LABELS），**禁止硬编码中文状态名**，
 * 保证与 Web / RN 端逐字一致。
 */
export default function StatusTag({ status, small = false }: StatusTagProps) {
  return (
    <Text
      className={`status-tag status-tag--${status.toLowerCase()} ${
        small ? 'status-tag--small' : ''
      }`}
    >
      {orderStatusLabel(status)}
    </Text>
  );
}
