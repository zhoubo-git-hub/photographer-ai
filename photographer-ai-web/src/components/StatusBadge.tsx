import Chip from '@mui/material/Chip';
import { STATUS_LABELS, type OrderStatus } from '../types/models';

const COLOR_MAP: Record<OrderStatus, 'default' | 'primary' | 'secondary' | 'warning' | 'success' | 'info'> = {
  CONSULT: 'default',
  DEPOSIT: 'primary',
  SHOOT: 'secondary',
  EDIT: 'warning',
  DELIVER: 'success',
  REPURCHASE: 'info',
};

export default function StatusBadge({ status }: { status: OrderStatus }) {
  return <Chip size="small" color={COLOR_MAP[status]} label={STATUS_LABELS[status]} />;
}
