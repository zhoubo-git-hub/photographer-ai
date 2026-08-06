import { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import Box from '@mui/material/Box';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Chip from '@mui/material/Chip';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import PeopleIcon from '@mui/icons-material/People';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import DescriptionIcon from '@mui/icons-material/Description';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import RepeatIcon from '@mui/icons-material/Repeat';
import InsightsIcon from '@mui/icons-material/Insights';
import GroupsIcon from '@mui/icons-material/Groups';
import WorkspacePremiumIcon from '@mui/icons-material/WorkspacePremium';
import Typography from '@mui/material/Typography';
import { useAuth } from '../hooks/useAuth';

type NavItem = {
  to: string;
  label: string;
  icon: ReactNode;
  pro?: boolean;
  team?: boolean;
};

const NAV: NavItem[] = [
  { to: '/orders', label: '订单看板', icon: <ReceiptLongIcon /> },
  { to: '/customers', label: '客户库', icon: <PeopleIcon /> },
  { to: '/calendar', label: '档期日历', icon: <CalendarMonthIcon /> },
  { to: '/ai-quote', label: 'AI 报价', icon: <AutoAwesomeIcon /> },
  { to: '/dashboard', label: '数据看板', icon: <InsightsIcon />, pro: true },
  { to: '/contract', label: '合同生成', icon: <DescriptionIcon />, pro: true },
  { to: '/reminder-rules', label: '提醒规则', icon: <NotificationsActiveIcon />, pro: true },
  { to: '/repurchases', label: '复购引擎', icon: <RepeatIcon />, pro: true },
  { to: '/team', label: '团队协作', icon: <GroupsIcon />, team: true },
  { to: '/billing', label: '订阅 / 升级', icon: <WorkspacePremiumIcon /> },
];

/**
 * 左侧导航：极简卡片化，激活态主色高亮。
 * 专业版功能（pro）对免费版灰显 + PRO 角标；团队版功能（team）对非团队灰显 + TEAM 角标。
 * 订阅页对所有人开放（免费版由此升级）。
 */
export default function SideBar() {
  const { studio } = useAuth();
  const isFree = studio?.planType === 'FREE';
  const isTeam = studio?.planType === 'TEAM';

  const isLocked = (item: NavItem): boolean => {
    if (item.team) return !isTeam;
    if (item.pro) return isFree;
    return false;
  };

  return (
    <Box
      component="nav"
      sx={{
        width: 220,
        flexShrink: 0,
        borderRight: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
        display: { xs: 'none', md: 'block' },
      }}
    >
      <Box sx={{ p: 3, pb: 1 }}>
        <Typography variant="subtitle1" fontWeight={800} color="primary">
          摄影师 AI 助手
        </Typography>
        <Typography variant="caption" color="text.secondary">
          接单 · 跟单 · 复购
        </Typography>
      </Box>
      <List sx={{ px: 1.5 }}>
        {NAV.map((item) => {
          const locked = isLocked(item);
          return (
            <ListItemButton
              key={item.to}
              component={NavLink}
              to={item.to}
              sx={{
                borderRadius: 2,
                mb: 0.5,
                opacity: locked ? 0.55 : 1,
                '&.active': {
                  bgcolor: 'primary.main',
                  color: '#fff',
                  '& .MuiListItemIcon-root': { color: '#fff' },
                },
              }}
            >
              <ListItemIcon sx={{ minWidth: 36 }}>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
              {item.pro && isFree && (
                <Chip size="small" label="PRO" color="primary" sx={{ height: 20, fontSize: 11 }} />
              )}
              {item.team && !isTeam && (
                <Chip size="small" label="TEAM" color="secondary" sx={{ height: 20, fontSize: 11 }} />
              )}
            </ListItemButton>
          );
        })}
      </List>
    </Box>
  );
}
