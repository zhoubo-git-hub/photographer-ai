import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import MenuItem from '@mui/material/MenuItem';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import DownloadIcon from '@mui/icons-material/Download';
import { contractApi } from '../api/contract';
import { orderApi } from '../api/order';
import type { Order, ContractGenerateResponse } from '../types/models';
import { useUiStore } from '../store/uiStore';

/**
 * 合同生成页（专业版）：选模板 + 选订单 → 预览 → 复制 / 下载 .md。
 * 免费版生成会被后端 403 拦截并弹升级弹窗。
 */
export default function ContractPage() {
  const showToast = useUiStore((s) => s.showToast);

  const { data: templates = [] } = useQuery({
    queryKey: ['contract-templates'],
    queryFn: () => contractApi.templates(),
  });

  const { data: ordersPage } = useQuery({
    queryKey: ['orders', 'all'],
    queryFn: () => orderApi.list(undefined, 0, 100),
  });
  const orders: Order[] = (ordersPage as any)?.content ?? [];

  const [templateId, setTemplateId] = useState<number | ''>('');
  const [orderId, setOrderId] = useState<number | ''>('');
  const [result, setResult] = useState<ContractGenerateResponse | null>(null);

  const genMutation = useMutation({
    mutationFn: () =>
      contractApi.generate({ orderId: Number(orderId), templateId: Number(templateId) }),
    onSuccess: (res) => {
      setResult(res);
      showToast('合同已生成', 'success');
    },
    onError: (e: any) => showToast(e?.message ?? '生成失败', 'error'),
  });

  const copy = () => {
    if (!result) return;
    navigator.clipboard?.writeText(`${result.title}\n\n${result.content}`);
    showToast('全文已复制', 'success');
  };

  const download = () => {
    if (!result) return;
    const blob = new Blob([`${result.title}\n\n${result.content}`], {
      type: 'text/markdown;charset=utf-8',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${result.title}.md`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>
        合同生成
        <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1 }}>
          （专业版）
        </Typography>
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        选模板与订单，一键生成可复制 / 下载的草案。字段自动从订单信息填充。
      </Typography>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: 'flex-start' }}>
        <Card sx={{ width: { xs: '100%', md: 320 }, flexShrink: 0 }}>
          <CardContent>
            <Typography variant="subtitle2" gutterBottom>选择模板</Typography>
            <TextField
              select
              fullWidth
              size="small"
              value={templateId}
              onChange={(e) => setTemplateId(Number(e.target.value))}
              sx={{ mb: 2 }}
            >
              {templates.map((t) => (
                <MenuItem key={t.id} value={t.id}>
                  {t.name}{t.builtin ? '（内置）' : ''}
                </MenuItem>
              ))}
            </TextField>

            <Typography variant="subtitle2" gutterBottom>关联订单</Typography>
            <TextField
              select
              fullWidth
              size="small"
              value={orderId}
              onChange={(e) => setOrderId(Number(e.target.value))}
              sx={{ mb: 2 }}
            >
              {orders.map((o) => (
                <MenuItem key={o.id} value={o.id}>
                  {o.title}
                </MenuItem>
              ))}
            </TextField>

            <Button
              variant="contained"
              fullWidth
              disabled={!templateId || !orderId || genMutation.isPending}
              onClick={() => genMutation.mutate()}
            >
              生成合同
            </Button>
          </CardContent>
        </Card>

        <Card sx={{ flex: 1, width: '100%' }}>
          <CardContent>
            <Typography variant="subtitle2" gutterBottom>预览</Typography>
            {result ? (
              <>
                <Typography variant="subtitle1" fontWeight={700} gutterBottom>
                  {result.title}
                </Typography>
                <Box
                  component="pre"
                  sx={{
                    whiteSpace: 'pre-wrap',
                    fontFamily: 'inherit',
                    fontSize: 14,
                    border: '1px solid',
                    borderColor: 'divider',
                    borderRadius: 2,
                    p: 2,
                    maxHeight: 460,
                    overflowY: 'auto',
                    m: 0,
                  }}
                >
                  {result.content}
                </Box>
                <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
                  <Button startIcon={<ContentCopyIcon />} onClick={copy}>
                    复制全文
                  </Button>
                  <Button startIcon={<DownloadIcon />} variant="outlined" onClick={download}>
                    下载 .md
                  </Button>
                </Stack>
              </>
            ) : (
              <Typography variant="body2" color="text.secondary">
                选择模板与订单后点击「生成合同」查看预览。
              </Typography>
            )}
          </CardContent>
        </Card>
      </Stack>
    </Box>
  );
}
