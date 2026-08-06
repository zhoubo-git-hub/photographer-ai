import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import RepeatIcon from '@mui/icons-material/Repeat';
import { repurchaseApi } from '../api/repurchase';
import { commApi } from '../api/comm';
import type { RepurchaseTask } from '../types/models';
import { useUiStore } from '../store/uiStore';

/**
 * 复购引擎页（专业版）：列出系统每日自动识别的复购候选客户，
 * 点击「生成话术」复用 AI 沟通助手（scenario=REPURCHASE）产出续拍邀约。
 * 免费版访问被后端 403 拦截并弹升级引导，本页展示空态。
 */
export default function RepurchasePage() {
  const showToast = useUiStore((s) => s.showToast);
  const [active, setActive] = useState<RepurchaseTask | null>(null);
  const [script, setScript] = useState('');

  const { data: tasks = [], error, isLoading } = useQuery({
    queryKey: ['repurchases'],
    queryFn: () => repurchaseApi.list(),
  });

  const isForbidden = (error as any)?.code === 403;

  const genMutation = useMutation({
    mutationFn: (t: RepurchaseTask) =>
      commApi.generate({ customerId: t.customerId, scenario: 'REPURCHASE' }),
    onSuccess: (resp) => setScript(resp.text),
    onError: (e: any) => showToast(e?.message ?? '生成失败', 'error'),
  });

  const handleGenerate = (t: RepurchaseTask) => {
    setActive(t);
    setScript('');
    genMutation.mutate(t);
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(script);
      showToast('已复制到剪贴板', 'success');
    } catch {
      showToast('复制失败', 'error');
    }
  };

  if (isForbidden) {
    return (
      <Box sx={{ maxWidth: 480, mx: 'auto', mt: 8, textAlign: 'center' }}>
        <RepeatIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
        <Typography variant="h6" gutterBottom>
          专业版功能
        </Typography>
        <Typography variant="body2" color="text.secondary">
          复购引擎为专业版功能，升级后系统将每日自动识别到期客户并生成续拍邀约话术。
        </Typography>
      </Box>
    );
  }

  if (isLoading) {
    return <Typography sx={{ p: 3 }}>加载中…</Typography>;
  }

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>
        复购引擎
        <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1 }}>
          （专业版 · 每日自动扫描）
        </Typography>
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        系统每日凌晨自动识别「最近拍摄日 + 复购周期」已到期的客户并生成复购提醒；点击可一键生成 AI 续拍邀约话术。
      </Typography>

      {tasks.length === 0 ? (
        <Card>
          <CardContent>
            <Typography variant="body2" color="text.secondary">
              暂无待处理复购提醒。当客户满足复购周期后将自动出现在此处。
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <Stack spacing={2} sx={{ maxWidth: 720 }}>
          {tasks.map((t) => (
            <Card key={t.reminderId}>
              <CardContent>
                <Box
                  sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: 2,
                  }}
                >
                  <Box>
                    <Typography fontWeight={600}>{t.customerName ?? '客户'}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {[t.shootType, t.lastShootDate, t.repurchaseCycleDays != null ? `周期 ${t.repurchaseCycleDays} 天` : null]
                        .filter(Boolean)
                        .join(' · ')}
                    </Typography>
                  </Box>
                  <Button
                    variant="contained"
                    size="small"
                    startIcon={<RepeatIcon />}
                    disabled={genMutation.isPending}
                    onClick={() => handleGenerate(t)}
                  >
                    生成话术
                  </Button>
                </Box>
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}

      <Dialog open={!!active} onClose={() => setActive(null)} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>复购邀约话术</span>
          <IconButton onClick={() => setActive(null)} aria-label="关闭">
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent dividers>
          {genMutation.isPending ? (
            <Typography variant="body2" color="text.secondary">
              生成中…
            </Typography>
          ) : (
            <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
              {script}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCopy} startIcon={<ContentCopyIcon />} disabled={!script}>
            复制
          </Button>
          <Button
            onClick={() => active && handleGenerate(active)}
            disabled={genMutation.isPending || !active}
          >
            重新生成
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
