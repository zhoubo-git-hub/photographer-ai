import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import OrderCard from './OrderCard';
import { STATUS_LABELS, type Order, type OrderStatus } from '../types/models';

/**
 * 看板状态分栏：单状态一列，渲染其下订单卡片。
 */
export default function StatusColumn({
  status,
  orders,
  onView,
  onAiQuote,
  onUrge,
  onAdvance,
}: {
  status: OrderStatus;
  orders: Order[];
  onView: (o: Order) => void;
  onAiQuote: (o: Order) => void;
  onUrge: (o: Order) => void;
  onAdvance: (o: Order) => void;
}) {
  return (
    <Box sx={{ minWidth: 240, flex: 1 }}>
      <Box
        sx={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          mb: 1,
          px: 0.5,
        }}
      >
        <Typography variant="subtitle2" color="ink">
          {STATUS_LABELS[status]}
        </Typography>
        <Typography variant="caption" color="text.secondary">
          {orders.length}
        </Typography>
      </Box>
      <Box>
        {orders.length === 0 ? (
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ display: 'block', p: 2, textAlign: 'center', border: '1px dashed', borderColor: 'divider', borderRadius: 2 }}
          >
            暂无
          </Typography>
        ) : (
          orders.map((o) => (
            <OrderCard
              key={o.id}
              order={o}
              onView={onView}
              onAiQuote={onAiQuote}
              onUrge={onUrge}
              onAdvance={onAdvance}
            />
          ))
        )}
      </Box>
    </Box>
  );
}
