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
 * 提醒铃铛：展示待处理站内提醒，可一键标记完成。
 */
export default function ReminderList() {
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const queryClient = useQueryClient();
  const showToast = useUiStore((s) => s.showToast);

  const { data = [] } = useQuery({
    queryKey: ['reminders', 'PENDING'],
    queryFn: () => reminderApi.list('PENDING'),
  });

  const doneMutation = useMutation({
    mutationFn: (id: number) => reminderApi.updateStatus(id, 'DONE'),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reminders'] });
      showToast('提醒已标记完成', 'success');
    },
  });

  const pending = data.filter((r) => r.status === 'PENDING');

  return (
    <>
      <IconButton onClick={(e) => setAnchorEl(e.currentTarget)}>
        <Badge badgeContent={pending.length} color="error">
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
        <Box sx={{ width: 320, p: 2 }}>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            站内提醒
          </Typography>
          {pending.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              暂无待处理提醒
            </Typography>
          ) : (
            pending.map((r: Reminder) => (
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
                  {r.orderTitle}
                  {r.dueAt ? ` · 到期 ${dayjs(r.dueAt).format('MM-DD HH:mm')}` : ''}
                </Typography>
                <Box sx={{ mt: 1, textAlign: 'right' }}>
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
