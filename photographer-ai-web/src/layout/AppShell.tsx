import { Outlet } from 'react-router-dom';
import Box from '@mui/material/Box';
import SideBar from './SideBar';
import TopBar from './TopBar';
import UpgradeModal from '../components/UpgradeModal';
import { useUiStore } from '../store/uiStore';

/**
 * 应用主框架：左侧导航 + 顶部栏 + 内容区 + 升级弹窗。
 * 全局 Toast 已抽离到顶层 ToastHost 组件，故此处不再渲染 Snackbar。
 */
export default function AppShell() {
  const upgradeOpen = useUiStore((s) => s.upgradeOpen);
  const upgradeMessage = useUiStore((s) => s.upgradeMessage);
  const closeUpgrade = useUiStore((s) => s.closeUpgrade);

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <SideBar />
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <TopBar />
        <Box component="main" sx={{ flex: 1, p: { xs: 2, md: 3 } }}>
          <Outlet />
        </Box>
      </Box>

      <UpgradeModal
        open={upgradeOpen}
        message={upgradeMessage}
        onClose={closeUpgrade}
      />
    </Box>
  );
}
