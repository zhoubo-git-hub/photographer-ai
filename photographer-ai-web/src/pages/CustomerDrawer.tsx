import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Drawer from '@mui/material/Drawer';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Stack from '@mui/material/Stack';
import Divider from '@mui/material/Divider';
import Chip from '@mui/material/Chip';
import Switch from '@mui/material/Switch';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormControlLabel from '@mui/material/FormControlLabel';
import CloseIcon from '@mui/icons-material/Close';
import { customerApi } from '../api/customer';
import { useUiStore } from '../store/uiStore';
import StatusBadge from '../components/StatusBadge';
import { SOURCE_CHANNELS } from '../types/models';
import dayjs from 'dayjs';

/**
 * 客户档案抽屉：联系方式 / 标签编辑 / 历史订单聚合。
 */
export default function CustomerDrawer({
  customerId,
  onClose,
}: {
  customerId: number | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const showToast = useUiStore((s) => s.showToast);
  const [tags, setTags] = useState('');
  const [note, setNote] = useState('');
  // 阶段2 复购画像字段
  const [lastShootDate, setLastShootDate] = useState('');
  const [repurchaseCycleDays, setRepurchaseCycleDays] = useState<number | ''>('');
  const [birthday, setBirthday] = useState('');
  const [anniversary, setAnniversary] = useState('');
  const [repurchaseEnabled, setRepurchaseEnabled] = useState(true);
  const [sourceChannel, setSourceChannel] = useState('');

  const { data: customer, isLoading } = useQuery({
    enabled: !!customerId,
    queryKey: ['customer', customerId],
    queryFn: () => customerApi.get(customerId as number),
  });

  // 客户切换时同步本地编辑态（含阶段2 画像字段）
  useEffect(() => {
    if (!customer) return;
    setTags(customer.tags ?? '');
    setNote(customer.note ?? '');
    setLastShootDate(customer.lastShootDate ?? '');
    setRepurchaseCycleDays(customer.repurchaseCycleDays ?? '');
    setBirthday(customer.birthday ?? '');
    setAnniversary(customer.anniversary ?? '');
    setRepurchaseEnabled(customer.repurchaseEnabled ?? true);
    setSourceChannel(customer.sourceChannel ?? '');
  }, [customer]);

  const updateMutation = useMutation({
    mutationFn: () =>
      customerApi.update(customer!.id, {
        tags,
        note,
        lastShootDate: lastShootDate || undefined,
        repurchaseCycleDays: repurchaseCycleDays === '' ? undefined : Number(repurchaseCycleDays),
        birthday: birthday || undefined,
        anniversary: anniversary || undefined,
        repurchaseEnabled,
        sourceChannel: sourceChannel || undefined,
      }),
    onSuccess: () => {
      showToast('客户信息已更新', 'success');
      queryClient.invalidateQueries({ queryKey: ['customer', customerId] });
      queryClient.invalidateQueries({ queryKey: ['customers-all'] });
    },
    onError: (e: any) => showToast(e?.message ?? '更新失败', 'error'),
  });

  const deleteMutation = useMutation({
    mutationFn: () => customerApi.remove(customer!.id),
    onSuccess: () => {
      showToast('客户已删除', 'success');
      onClose();
      queryClient.invalidateQueries({ queryKey: ['customers-all'] });
    },
    onError: (e: any) => showToast(e?.message ?? '删除失败（可能存在进行中订单）', 'error'),
  });

  return (
    <Drawer anchor="right" open={!!customerId} onClose={onClose}>
      <Box sx={{ width: 400, p: 3, maxWidth: '100vw' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6">客户档案</Typography>
          <IconButton onClick={onClose}>
            <CloseIcon />
          </IconButton>
        </Box>

        {isLoading && <Typography>加载中…</Typography>}
        {customer && (
          <>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom>
              {customer.name}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              微信：{customer.wechatId ?? '—'} · 电话：{customer.phone ?? '—'}
            </Typography>

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle2" gutterBottom>
              标签
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mb: 1 }}>
              {(customer.tags ?? '').split(',').filter(Boolean).map((t) => (
                <Chip key={t} size="small" label={t} />
              ))}
            </Box>
            <TextField
              label="编辑标签(逗号分隔)"
              size="small"
              fullWidth
              value={tags}
              placeholder={customer.tags ?? ''}
              onChange={(e) => setTags(e.target.value)}
              sx={{ mb: 2 }}
            />

            <Typography variant="subtitle2" gutterBottom>
              备注
            </Typography>
            <TextField
              label="备注"
              size="small"
              multiline
              minRows={2}
              fullWidth
              value={note}
              placeholder={customer.note ?? ''}
              onChange={(e) => setNote(e.target.value)}
              sx={{ mb: 2 }}
            />

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle2" gutterBottom>
              复购画像
            </Typography>
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
              <TextField
                label="最近拍摄日"
                type="date"
                size="small"
                value={lastShootDate}
                onChange={(e) => setLastShootDate(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ flex: '1 1 160px' }}
              />
              <TextField
                label="复购周期(天)"
                type="number"
                size="small"
                value={repurchaseCycleDays}
                onChange={(e) =>
                  setRepurchaseCycleDays(e.target.value === '' ? '' : Number(e.target.value))
                }
                sx={{ flex: '1 1 120px' }}
              />
            </Box>
            <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mt: 1 }}>
              <TextField
                label="生日"
                type="date"
                size="small"
                value={birthday}
                onChange={(e) => setBirthday(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ flex: '1 1 160px' }}
              />
              <TextField
                label="纪念日(婚期)"
                type="date"
                size="small"
                value={anniversary}
                onChange={(e) => setAnniversary(e.target.value)}
                InputLabelProps={{ shrink: true }}
                sx={{ flex: '1 1 160px' }}
              />
            </Box>
            <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mt: 1, flexWrap: 'wrap' }}>
              <FormControl size="small" sx={{ flex: '1 1 160px' }}>
                <InputLabel>来源渠道</InputLabel>
                <Select
                  label="来源渠道"
                  value={sourceChannel}
                  onChange={(e) => setSourceChannel(e.target.value)}
                >
                  <MenuItem value="">未设置</MenuItem>
                  {SOURCE_CHANNELS.map((c) => (
                    <MenuItem key={c} value={c}>
                      {c}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControlLabel
                control={
                  <Switch
                    checked={repurchaseEnabled}
                    onChange={(e) => setRepurchaseEnabled(e.target.checked)}
                  />
                }
                label="开启复购提醒"
              />
            </Box>

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle2" gutterBottom>
              历史订单（{customer.orderCount ?? 0} 单）
            </Typography>
            <Stack spacing={1}>
              {(customer.orders ?? []).map((o) => (
                <Box key={o.id} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, p: 1.5 }}>
                  <Box sx={{ display: 'flex', justifyContent: 'space-between' }}>
                    <Typography variant="body2" fontWeight={600}>
                      {o.title}
                    </Typography>
                    <StatusBadge status={o.status} />
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    {o.shootDate ? dayjs(o.shootDate).format('YYYY-MM-DD') : '待定'} · {o.amount != null ? `¥${o.amount}` : ''}
                  </Typography>
                </Box>
              ))}
            </Stack>

            <Divider sx={{ my: 2 }} />
            <Button variant="outlined" color="error" onClick={() => deleteMutation.mutate()}>
              删除客户
            </Button>
            <Button variant="contained" sx={{ ml: 1 }} onClick={() => updateMutation.mutate()}>
              保存修改
            </Button>
          </>
        )}
      </Box>
    </Drawer>
  );
}
