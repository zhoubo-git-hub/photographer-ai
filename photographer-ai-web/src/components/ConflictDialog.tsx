import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import { type Conflict } from '../types/models';
import dayjs from 'dayjs';

/**
 * 档期冲突弹窗（硬阻断，红色提示）。
 */
export default function ConflictDialog({
  open,
  conflicts,
  onClose,
}: {
  open: boolean;
  conflicts: Conflict[];
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ color: 'error.main' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <WarningAmberIcon /> 档期冲突
        </Box>
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          以下订单的拍摄时间与本次重叠，无法保存：
        </Typography>
        <Box component="ul" sx={{ pl: 3, m: 0, color: 'error.main' }}>
          {conflicts.map((c) => (
            <li key={c.orderId}>
              {c.title}（
              {dayjs(c.shootDate).format('MM-DD')}
              {c.shootEndDate && c.shootEndDate !== c.shootDate
                ? `~${dayjs(c.shootEndDate).format('MM-DD')}`
                : ''}
              ）
            </li>
          ))}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button variant="contained" onClick={onClose}>
          我知道了
        </Button>
      </DialogActions>
    </Dialog>
  );
}
