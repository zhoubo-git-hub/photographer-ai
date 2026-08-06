import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Badge from '@mui/material/Badge';
import IconButton from '@mui/material/IconButton';
import Popover from '@mui/material/Popover';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import NotificationsIcon from '@mui/icons-material/Notifications';
import { reminderApi } from '../api/reminder';
import { REMINDER_LABELS, type Reminder } from '../types/models';
import { useUiStore } from '../store/uiStore';
import dayjs from 'dayjs';

/**
 * 通知中心：铃铛角标（已到期且 PENDING 数）+ 抽屉式列表。
 * 取代阶段1 的 ReminderList，覆盖定金/拍摄前/修图/交付好评/复购全部提醒类型。
 */
export default function NotificationCenter() {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const queryClient = useQueryClient();
  const showToast = useUiStore((s) => s.showToast);

  const { data: due = [] } = useQuery({
    queryKey: ['reminders', 'due'],
    queryFn: () => reminderApi.list('PENDING', true),
  });

  const { data: all = [] } = useQuery({
    queryKey: ['reminders', 'PENDING'],
    queryFn: () => reminderApi.list('PENDING'),
    enabled: !!anchorEl,
  });

  const doneMutation = useMutation({
    mutationFn: (id: number) => reminderApi.updateStatus(id, 'DONE'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reminders'] });
      showToast('提醒已标记完成', 'success');
    },
  });

  const dismissMutation = useMutation({
    mutationFn: (id: number) => reminderApi.updateStatus(id, 'DISMISSED'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reminders'] });
      showToast('提醒已忽略', 'info');
    },
  });

  const items: Reminder[] = anchorEl ? all : due;

  return (
    <>
      <IconButton onClick={(e) => setAnchorEl(e.currentTarget)}>
        <Badge badgeContent={due.length} color="error">
          <NotificationsIcon />
        </Badge>
      </IconButton>
      <Popover
        open={!!anchorEl}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Box sx={{ width: 340, p: 2, maxHeight: 420, overflowY: 'auto' }}>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            通知中心
          </Typography>
          {items.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              暂无待处理提醒
            </Typography>
          ) : (
            items.map((r: Reminder) => (
              <Box
                key={r.id}
                sx={{
                  border: '1px solid',
                  borderColor: 'divider',
                  borderRadius: 2,
                  p: 1.5,
                  mb: 1,
                }}
              >
                <Typography variant="body2" fontWeight={600}>
                  {REMINDER_LABELS[r.type]}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {r.orderTitle ?? r.customerName ?? '—'}
                  {r.dueAt ? ` · 到期 ${dayjs(r.dueAt).format('MM-DD HH:mm')}` : ''}
                </Typography>
                <Box sx={{ mt: 1, display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
                  <Button size="small" onClick={() => dismissMutation.mutate(r.id)}>
                    忽略
                  </Button>
                  <Button size="small" onClick={() => doneMutation.mutate(r.id)}>
                    标记完成
                  </Button>
                </Box>
              </Box>
            ))
          )}
        </Box>
      </Popover>
    </>
  );
}
