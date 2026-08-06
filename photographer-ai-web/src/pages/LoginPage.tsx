import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { authApi } from '../api/auth';
import { useAuth } from '../hooks/useAuth';
import { useUiStore } from '../store/uiStore';

/**
 * 登录 / 注册页（Web 账号密码）。
 */
export default function LoginPage() {
  const navigate = useNavigate();
  const { setAuth } = useAuth();
  const showToast = useUiStore((s) => s.showToast);
  const [tab, setTab] = useState(0);

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [studioName, setStudioName] = useState('');

  const mutation = useMutation({
    mutationFn: () =>
      tab === 0
        ? authApi.login({ username, password })
        : authApi.register({ username, password, email, studioName }),
    onSuccess: (auth) => {
      setAuth(auth);
      showToast(tab === 0 ? '登录成功' : '注册成功，工作室已创建', 'success');
      navigate('/orders');
    },
    onError: (e: any) => showToast(e?.message ?? '操作失败', 'error'),
  });

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
        p: 2,
      }}
    >
      <Paper sx={{ width: 380, p: 4 }}>
        <Typography variant="h6" color="primary" gutterBottom>
          摄影师 AI 接单跟单助手
        </Typography>
        <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
          <Tab label="登录" />
          <Tab label="注册" />
        </Tabs>

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label="用户名"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            fullWidth
          />
          <TextField
            label="密码"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            fullWidth
          />
          {tab === 1 && (
            <>
              <TextField
                label="工作室名称"
                value={studioName}
                onChange={(e) => setStudioName(e.target.value)}
                fullWidth
              />
              <TextField
                label="邮箱(选填)"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                fullWidth
              />
            </>
          )}
          <Button
            variant="contained"
            size="large"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {tab === 0 ? '登录' : '注册并创建工作室'}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
}
