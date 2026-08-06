import { createTheme } from '@mui/material/styles';

/**
 * 全局 MUI 主题：两主色 ——
 * 主色 #2D6CDF（主操作/激活）、墨色 #1A1A1A（文字/重要）、背景白、浅灰分隔 #F2F4F7。
 */
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: { main: '#2D6CDF' },
    secondary: { main: '#1A1A1A' },
    background: { default: '#f7f8fa', paper: '#FFFFFF' },
    text: { primary: '#1A1A1A', secondary: '#6B7280' },
    divider: '#F2F4F7',
    success: { main: '#16A34A' },
    warning: { main: '#F59E0B' },
    error: { main: '#DC2626' },
  },
  shape: { borderRadius: 10 },
  typography: {
    fontFamily:
      'Inter, -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif',
    h6: { fontWeight: 700 },
    subtitle1: { fontWeight: 600 },
  },
  components: {
    MuiCard: {
      styleOverrides: {
        root: { borderRadius: 12, border: '1px solid #F2F4F7' },
      },
    },
    MuiButton: {
      styleOverrides: { root: { textTransform: 'none', fontWeight: 600 } },
    },
  },
});
