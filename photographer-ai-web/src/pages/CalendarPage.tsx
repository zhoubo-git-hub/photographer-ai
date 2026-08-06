import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { scheduleApi } from '../api/schedule';
import { type ScheduleItem } from '../types/models';
import dayjs, { Dayjs } from 'dayjs';
import OrderDetailDrawer from './OrderDetailDrawer';

const WEEK = ['一', '二', '三', '四', '五', '六', '日'];

/**
 * 档期日历：月视图，拍摄日标记，重叠冲突红色高亮。
 */
export default function CalendarPage() {
  const [current, setCurrent] = useState<Dayjs>(dayjs());
  const [drawerId, setDrawerId] = useState<number | null>(null);

  const { data = [] } = useQuery({
    queryKey: ['schedule', current.year(), current.month() + 1],
    queryFn: () => scheduleApi.month(current.year(), current.month() + 1),
  });

  const days = useMemo(() => {
    const monthStart = current.startOf('month');
    const gridStart = monthStart.subtract((monthStart.day() + 6) % 7, 'day');
    return Array.from({ length: 42 }, (_, i) => gridStart.add(i, 'day'));
  }, [current]);

  const itemsOnDay = (day: Dayjs): ScheduleItem[] =>
    data.filter((it) => {
      const start = dayjs(it.shootDate);
      const end = dayjs(it.shootEndDate ?? it.shootDate);
      return !day.isBefore(start, 'day') && !day.isAfter(end, 'day');
    });

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
        <Typography variant="h6" color="ink" sx={{ mr: 1 }}>
          {current.year()}年{current.month() + 1}月
        </Typography>
        <IconButton onClick={() => setCurrent((c) => c.subtract(1, 'month'))}>
          <ChevronLeftIcon />
        </IconButton>
        <IconButton onClick={() => setCurrent((c) => c.add(1, 'month'))}>
          <ChevronRightIcon />
        </IconButton>
        <Button size="small" onClick={() => setCurrent(dayjs())}>
          今天
        </Button>
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 1 }}>
        {WEEK.map((w) => (
          <Typography key={w} variant="caption" align="center" color="text.secondary">
            {w}
          </Typography>
        ))}
        {days.map((day) => {
          const items = itemsOnDay(day);
          const hasConflict = items.some((it) => it.conflict);
          const inMonth = day.month() === current.month();
          return (
            <Paper
              key={day.format('YYYY-MM-DD')}
              variant="outlined"
              sx={{
                minHeight: 92,
                p: 1,
                bgcolor: hasConflict ? '#FEE2E2' : 'background.paper',
                borderColor: hasConflict ? 'error.main' : 'divider',
                opacity: inMonth ? 1 : 0.4,
              }}
            >
              <Typography variant="caption" color={day.isSame(dayjs(), 'day') ? 'primary' : 'text.secondary'}>
                {day.date()}
              </Typography>
              {items.map((it) => (
                <Box
                  key={it.orderId}
                  onClick={() => setDrawerId(it.orderId)}
                  sx={{
                    mt: 0.5,
                    px: 0.5,
                    py: 0.25,
                    borderRadius: 1,
                    fontSize: 11,
                    cursor: 'pointer',
                    bgcolor: it.conflict ? 'error.main' : 'primary.main',
                    color: '#fff',
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                  title={it.title}
                >
                  {it.title}
                </Box>
              ))}
            </Paper>
          );
        })}
      </Box>

      <Typography variant="caption" color="error.main" sx={{ display: 'block', mt: 1 }}>
        ⚠ 红色 = 档期冲突（同摄影师时间段重叠）
      </Typography>

      <OrderDetailDrawer orderId={drawerId} onClose={() => setDrawerId(null)} />
    </Box>
  );
}
