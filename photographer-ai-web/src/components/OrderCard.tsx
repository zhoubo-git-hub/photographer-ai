import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import StatusBadge from './StatusBadge';
import { type Order } from '../types/models';
import dayjs from 'dayjs';

/**
 * 订单卡片：列表/看板的最小单元，含快捷操作。
 */
export default function OrderCard({
  order,
  onView,
  onAiQuote,
  onUrge,
  onAdvance,
}: {
  order: Order;
  onView: (o: Order) => void;
  onAiQuote: (o: Order) => void;
  onUrge: (o: Order) => void;
  onAdvance: (o: Order) => void;
}) {
  return (
    <Card variant="outlined" sx={{ mb: 1.5 }}>
      <CardContent sx={{ pb: 1 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle2" noWrap title={order.title}>
            {order.title}
          </Typography>
          <StatusBadge status={order.status} />
        </Box>
        <Typography variant="body2" color="text.secondary">
          {order.customerName ?? '客户'}
        </Typography>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 1 }}>
          <Typography variant="body2" fontWeight={600} color="primary">
            {order.amount != null ? `¥${order.amount}` : '—'}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {order.shootDate ? dayjs(order.shootDate).format('MM-DD 拍摄') : '待定档期'}
          </Typography>
        </Box>
      </CardContent>
      <CardActions sx={{ px: 1.5, pt: 0, flexWrap: 'wrap', gap: 0.5 }}>
        <Button size="small" onClick={() => onAdvance(order)}>
          改状态
        </Button>
        <Button size="small" onClick={() => onAiQuote(order)}>
          AI报价
        </Button>
        <Button size="small" onClick={() => onUrge(order)}>
          催定金
        </Button>
        <Button size="small" onClick={() => onView(order)}>
          详情
        </Button>
      </CardActions>
    </Card>
  );
}
