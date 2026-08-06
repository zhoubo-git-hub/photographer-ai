import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import InputAdornment from '@mui/material/InputAdornment';
import SearchIcon from '@mui/icons-material/Search';
import AddIcon from '@mui/icons-material/Add';
import { customerApi, type CustomerCreatePayload } from '../api/customer';
import { useUiStore } from '../store/uiStore';
import CustomerDrawer from './CustomerDrawer';

/**
 * 客户库：搜索列表 + 新建 + 档案抽屉。
 */
export default function CustomersPage() {
  const queryClient = useQueryClient();
  const showToast = useUiStore((s) => s.showToast);
  const [keyword, setKeyword] = useState('');
  const [drawerId, setDrawerId] = useState<number | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<CustomerCreatePayload>({
    name: '',
    wechatId: '',
    phone: '',
    tags: '',
  });

  const { data, isFetching } = useQuery({
    queryKey: ['customers', keyword],
    queryFn: () => customerApi.list(keyword, 0, 50),
  });

  const createMutation = useMutation({
    mutationFn: () => customerApi.create(form),
    onSuccess: () => {
      showToast('客户已创建', 'success');
      setOpen(false);
      setForm({ name: '', wechatId: '', phone: '', tags: '' });
      queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
    onError: (e: any) => showToast(e?.message ?? '创建失败', 'error'),
  });

  const customers = data?.content ?? [];

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h6" color="ink">
          客户库
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>
          新建客户
        </Button>
      </Box>

      <TextField
        size="small"
        placeholder="搜索客户 / 微信 / 电话"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        sx={{ mb: 2, width: 320, maxWidth: '100%' }}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon />
            </InputAdornment>
          ),
        }}
      />

      {isFetching && <Typography variant="body2">加载中…</Typography>}

      <Stack spacing={1.5}>
        {customers.map((c) => (
          <Card
            key={c.id}
            variant="outlined"
            sx={{ p: 2, cursor: 'pointer' }}
            onClick={() => setDrawerId(c.id)}
          >
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Box>
                <Typography variant="subtitle2">{c.name}</Typography>
                <Typography variant="caption" color="text.secondary">
                  微信：{c.wechatId ?? '—'} · {c.phone ?? '—'}
                </Typography>
              </Box>
              <Box sx={{ textAlign: 'right' }}>
                <Typography variant="body2" color="primary" fontWeight={600}>
                  {c.orderCount ?? 0} 单
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {c.lastAmount != null ? `¥${c.lastAmount}` : ''}
                </Typography>
              </Box>
            </Box>
            {c.tags && (
              <Box sx={{ mt: 1, display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                {c.tags.split(',').filter(Boolean).map((t) => (
                  <Chip key={t} size="small" label={t} />
                ))}
              </Box>
            )}
          </Card>
        ))}
        {!isFetching && customers.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            暂无客户，点击右上角新建。
          </Typography>
        )}
      </Stack>

      <CustomerDrawer customerId={drawerId} onClose={() => setDrawerId(null)} />

      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>新建客户</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField label="客户名称" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <TextField label="微信号" value={form.wechatId} onChange={(e) => setForm({ ...form, wechatId: e.target.value })} />
            <TextField label="电话" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            <TextField label="标签(逗号分隔)" value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} placeholder="婚纱/高客单" />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>取消</Button>
          <Button variant="contained" disabled={createMutation.isPending} onClick={() => createMutation.mutate()}>
            创建
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
