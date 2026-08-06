import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import IconButton from '@mui/material/IconButton';
import CloseIcon from '@mui/icons-material/Close';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { commApi } from '../api/comm';
import { SCENARIO_LABELS, type CommScenario } from '../types/models';
import { useUiStore } from '../store/uiStore';

const SCENARIOS: CommScenario[] = [
  'URGE_DEPOSIT',
  'URGE_FINAL',
  'PRE_SHOOT',
  'DELIVER_REVIEW',
  'FAQ',
];

/**
 * AI 沟通助手浮层（挂在订单详情内）。选场景 → 生成话术 → 复制 / 重新生成。
 * 免费版点生成会被后端 403 拦截并弹出升级弹窗（话术不出现）。
 */
export default function CommAssistantDrawer({
  orderId,
  open,
  onClose,
}: {
  orderId: number | null;
  open: boolean;
  onClose: () => void;
}) {
  const showToast = useUiStore((s) => s.showToast);
  const [scenario, setScenario] = useState<CommScenario>('URGE_FINAL');
  const [text, setText] = useState('');

  const genMutation = useMutation({
    mutationFn: () => commApi.generate({ orderId, scenario }),
    onSuccess: (res) => {
      setText(res.text);
      if (res.fallback) {
        showToast('已使用规则模板话术（AI 暂不可用）', 'info');
      }
    },
    onError: (e: any) => showToast(e?.message ?? '生成失败', 'error'),
  });

  const copy = () => {
    navigator.clipboard?.writeText(text);
    showToast('话术已复制', 'success');
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        AI 沟通助手
        <IconButton onClick={onClose} size="small">
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          选择场景，一键生成可复制的微信沟通话术。
        </Typography>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1, my: 1.5 }}>
          {SCENARIOS.map((s) => (
            <Button
              key={s}
              size="small"
              variant={scenario === s ? 'contained' : 'outlined'}
              onClick={() => setScenario(s)}
            >
              {SCENARIO_LABELS[s]}
            </Button>
          ))}
        </Stack>

        <Box
          sx={{
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 2,
            p: 2,
            minHeight: 96,
            whiteSpace: 'pre-wrap',
            bgcolor: 'background.default',
          }}
        >
          {text ? (
            <Typography variant="body2">{text}</Typography>
          ) : (
            <Typography variant="body2" color="text.secondary">
              点击「生成话术」获取文案
            </Typography>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={copy} startIcon={<ContentCopyIcon />} disabled={!text}>
          复制话术
        </Button>
        <Button
          variant="contained"
          onClick={() => genMutation.mutate()}
          disabled={genMutation.isPending}
        >
          生成话术
        </Button>
      </DialogActions>
    </Dialog>
  );
}
