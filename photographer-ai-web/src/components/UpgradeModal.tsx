import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import Box from '@mui/material/Box';
import { useNavigate } from 'react-router-dom';
import { useUiStore } from '../store/uiStore';

/**
 * 免费转付费引导弹窗：由 403（专业版门禁）/ 402（到期）统一触发。
 * 文案随 message 自适应：团队版 / 续费 / 新购专业版。
 */
export default function UpgradeModal({
  open,
  message,
  onClose,
}: {
  open: boolean;
  message?: string;
  onClose: () => void;
}) {
  const showToast = useUiStore((s) => s.showToast);
  const navigate = useNavigate();

  const isTeam = !!message && message.includes('团队');
  const isRenew = !!message && (message.includes('续费') || message.includes('到期'));

  const title = isTeam
    ? '升级团队版，协同接单'
    : isRenew
      ? '订阅已到期，请续费'
      : '升级专业版，解锁更多';

  const bullets = isTeam
    ? ['多人协同接单与订单分配', '成员业绩拆分', '团队权限管理', '含专业版全部能力']
    : isRenew
      ? ['继续不限订单数量', 'AI 报价无限次', '数据看板 / 合同 / 提醒 / 复购', '成员协同（团队版）']
      : ['不限订单数量', 'AI 报价无限次', '客户库标签与画像增强', '数据看板 / 合同 / 提醒 / 复购'];

  const goBilling = () => {
    onClose();
    navigate('/billing');
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        {message && (
          <Box sx={{ mb: 2 }}>
            <Typography variant="body2" color="primary" fontWeight={600}>
              {message}
            </Typography>
          </Box>
        )}
        <Box component="ul" sx={{ pl: 3, m: 0, mb: 1 }}>
          {bullets.map((b) => (
            <li key={b}>
              <Typography variant="body2" color="text.secondary">
                {b}
              </Typography>
            </li>
          ))}
        </Box>
        <Typography variant="h6" color="primary">
          {isTeam ? '¥99 / 月' : '¥39 / 月'} · 年付更省
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>稍后再说</Button>
        <Button variant="outlined" onClick={goBilling}>
          前往订阅页
        </Button>
        <Button
          variant="contained"
          onClick={() => {
            showToast(isRenew ? '请前往订阅页完成续费' : '请前往订阅页完成升级', 'info');
            goBilling();
          }}
        >
          {isTeam ? '升级团队版' : '升级专业版'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
