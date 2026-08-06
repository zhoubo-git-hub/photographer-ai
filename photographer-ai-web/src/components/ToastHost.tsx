import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';
import { useUiStore } from '../store/uiStore';

/**
 * 全局 Toast 宿主组件。
 *
 * 之所以独立挂在路由顶层而非 AppShell 内，是因为 /login 路由在 AppShell 之外，
 * 若 Toast 渲染逻辑只存在于 AppShell，登录失败写入的 toast 状态将没有组件消费，
 * 提示被「吞掉」。把它抽到顶层后，/login 与所有受保护页面都能正确显示 Toast。
 */
export default function ToastHost() {
  const toast = useUiStore((s) => s.toast);
  const clearToast = useUiStore((s) => s.clearToast);

  return (
    <Snackbar
      open={!!toast}
      autoHideDuration={3000}
      onClose={clearToast}
      anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
    >
      {toast ? (
        <Alert
          onClose={clearToast}
          severity={toast.severity}
          variant="filled"
          sx={{ width: '100%' }}
        >
          {toast.message}
        </Alert>
      ) : undefined}
    </Snackbar>
  );
}
