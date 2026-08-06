import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Avatar from '@mui/material/Avatar';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Tooltip from '@mui/material/Tooltip';
import Button from '@mui/material/Button';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import NotificationCenter from '../components/NotificationCenter';
import { useAuth } from '../hooks/useAuth';
import { useUiStore } from '../store/uiStore';

const PLAN_LABEL: Record<string, string> = { FREE: '免费版', PRO: '专业版', TEAM: '团队版' };

/**
 * 顶部栏：套餐标签 + 订阅到期黄条（402）+ 提醒铃铛 + 用户菜单（登出）。
 */
export default function TopBar() {
  const { user, studio, logout } = useAuth();
  const navigate = useNavigate();
  const expiredBanner = useUiStore((s) => s.expiredBanner);
  const setExpiredBanner = useUiStore((s) => s.setExpiredBanner);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const handleLogout = () => {
    setAnchorEl(null);
    logout();
    window.location.href = '/login';
  };

  const goBilling = () => {
    setExpiredBanner(false);
    navigate('/billing');
  };

  const planText = studio ? PLAN_LABEL[studio.planType] ?? studio.planType : '';

  return (
    <Box sx={{ borderBottom: '1px solid', borderColor: 'divider', bgcolor: 'background.paper' }}>
      {expiredBanner && (
        <Box
          sx={{
            px: 3,
            py: 1,
            bgcolor: 'warning.main',
            color: 'warning.contrastText',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Typography variant="body2" fontWeight={600}>
            订阅已到期，部分功能受限，请续费以继续使用。
          </Typography>
          <Button size="small" variant="contained" color="inherit" onClick={goBilling}>
            去续费
          </Button>
        </Box>
      )}
      <Box
        sx={{
          height: 60,
          px: 3,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Typography variant="h6" color="ink">
          工作台
        </Typography>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="caption" color="text.secondary">
            {planText}
          </Typography>
          <NotificationCenter />
          <Tooltip title={user?.username ?? '账号'}>
            <IconButton onClick={(e) => setAnchorEl(e.currentTarget)}>
              <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: 14 }}>
                {user?.username?.[0]?.toUpperCase() ?? 'U'}
              </Avatar>
            </IconButton>
          </Tooltip>
          <Menu anchorEl={anchorEl} open={!!anchorEl} onClose={() => setAnchorEl(null)}>
            <Box sx={{ px: 2, py: 1 }}>
              <Typography variant="body2" fontWeight={600}>
                {user?.username}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {studio?.name} · {planText}
              </Typography>
            </Box>
            <MenuItem onClick={handleLogout}>退出登录</MenuItem>
          </Menu>
        </Box>
      </Box>
    </Box>
  );
}
