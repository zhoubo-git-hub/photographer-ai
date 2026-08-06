import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import AddIcon from '@mui/icons-material/Add';
import Stack from '@mui/material/Stack';
import StatusColumn from '../components/StatusColumn';
import ConflictDialog from '../components/ConflictDialog';
import UpgradeModal from '../components/UpgradeModal';
import OrderDetailDrawer from './OrderDetailDrawer';
import { orderApi, type OrderCreatePayload } from '../api/order';
import { customerApi } from '../api/customer';
import { ApiError } from '../api/client';
import {
  STATUS_COLUMNS,
  NEXT_STATUSES,
  STATUS_LABELS,
  type Conflict,
  type Order,
  type OrderStatus,
} from '../types/models';
import { useUiStore } from '../store/uiStore';

const EMPTY_FORM: OrderCreatePayload = {
  customerId: 0,
  title: '',
  shootType: '婚纱写真',
  amount: undefined,
  depositAmount: undefined,
  shootDate: undefined,
  shootEndDate: undefined,
  durationHours: undefined,
  photoCount: undefined,
  region: '上海',
  style: '轻奢',
};

/**
 * 订单看板：状态分栏 + 新建 + 状态流转 + 档期冲突/额度拦截。
 */
export default function OrdersPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const showToast = useUiStore((s) => s.showToast);
  const pendingQuoteRequest = useUiStore((s) => s.pendingQuoteRequest);
  const pendingQuote = useUiStore((s) => s.pendingQuote);
  const setPendingQuoteRequest = useUiStore((s) => s.setPendingQuoteRequest);
  const setPendingQuote = useUiStore((s) => s.setPendingQuote);

  const { data: ordersData } = useQuery({
    queryKey: ['orders'],
    queryFn: () => orderApi.list(undefined, 0, 200),
  });
  const { data: customersData } = useQuery({
    queryKey: ['customers-all'],
    queryFn: () => customerApi.list('', 0, 200),
  });

  const [drawerId, setDrawerId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState<OrderCreatePayload>(EMPTY_FORM);
  const [statusTarget, setStatusTarget] = useState<Order | null>(null);
  const [conflict, setConflict] = useState<{ open: boolean; list: Conflict[] }>({
    open: false,
    list: [],
  });
  const [upgradeOpen, setUpgradeOpen] = useState(false);

  const orders = useMemo(() => ordersData?.content ?? [], [ordersData]);
  const customers = customersData?.content ?? [];

  const grouped = useMemo(() => {
    const map = {} as Record<OrderStatus, Order[]>;
    STATUS_COLUMNS.forEach((s) => (map[s] = []));
    orders.forEach((o) => {
      (map[o.status] ??= []).push(o);
    });
    return map;
  }, [orders]);

  const createMutation = useMutation({
    mutationFn: () => orderApi.create(form),
    onSuccess: () => {
      showToast('订单已创建', 'success');
      setCreateOpen(false);
      setForm(EMPTY_FORM);
      queryClient.invalidateQueries({ queryKey: ['orders'] });
    },
    onError: async (e: any) => {
      if (e instanceof ApiError && e.code === 409) {
        try {
          const list = await orderApi.conflict(form.shootDate, form.shootEndDate);
          setConflict({ open: true, list });
        } catch {
          setConflict({ open: true, list: [] });
        }
      } else if (e instanceof ApiError && e.code === 403) {
        setUpgradeOpen(true);
      } else {
        showToast(e?.message ?? '创建失败', 'error');
      }
    },
  });

  const statusMutation = useMutation({
    mutationFn: (to: OrderStatus) =>
      orderApi.changeStatus(statusTarget!.id, to),
    onSuccess: () => {
      showToast('状态已更新', 'success');
      setStatusTarget(null);
      queryClient.invalidateQueries({ queryKey: ['orders'] });
    },
    onError: (e: any) => showToast(e?.message ?? '状态更新失败', 'error'),
  });

  const set = (k: keyof OrderCreatePayload, v: any) =>
    setForm((f) => ({ ...f, [k]: v }));

  const openCreate = () => {
    if (pendingQuoteRequest) {
      setForm(() => ({
        ...EMPTY_FORM,
        shootType: pendingQuoteRequest.shootType ?? EMPTY_FORM.shootType,
        durationHours: pendingQuoteRequest.durationHours,
        photoCount: pendingQuoteRequest.photoCount,
        region: pendingQuoteRequest.region ?? EMPTY_FORM.region,
        style: pendingQuoteRequest.style ?? EMPTY_FORM.style,
        title: pendingQuoteRequest.customerName
          ? `${pendingQuoteRequest.customerName} 的${pendingQuoteRequest.shootType}拍摄订单`
          : EMPTY_FORM.title,
        amount: pendingQuote ? pendingQuote.priceLow : undefined,
      }));
      setPendingQuoteRequest(null);
      setPendingQuote(null);
    }
    setCreateOpen(true);
  };

  const location = useLocation();
  useEffect(() => {
    const st = location.state as { openCreate?: boolean } | null;
    if (st?.openCreate) {
      openCreate();
      // 清除 state，避免后续渲染(如 replace 后)重复触发
      navigate('/orders', { replace: true, state: null });
    }
    // 仅在 location.state 变化时检查,openCreate 由闭包捕获当前实例
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.state]);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h6" color="ink">
          订单看板
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
          新建订单
        </Button>
      </Box>

      <Box sx={{ display: 'flex', gap: 2, overflowX: 'auto', pb: 1 }}>
        {STATUS_COLUMNS.map((col) => (
          <StatusColumn
            key={col}
            status={col}
            orders={grouped[col]}
            onView={(o) => setDrawerId(o.id)}
            onAiQuote={(o) =>
              navigate('/ai-quote', { state: { customerName: o.customerName } })
            }
            onUrge={() => showToast('已发送催定金提醒（演示）', 'info')}
            onAdvance={(o) => setStatusTarget(o)}
          />
        ))}
      </Box>

      <OrderDetailDrawer orderId={drawerId} onClose={() => setDrawerId(null)} />

      {/* 新建订单 */}
      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>新建订单</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              select
              label="客户"
              value={form.customerId || ''}
              onChange={(e) => set('customerId', Number(e.target.value))}
            >
              {customers.map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {c.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField label="订单标题" value={form.title} onChange={(e) => set('title', e.target.value)} />
            <TextField
              select
              label="拍摄类型"
              value={form.shootType}
              onChange={(e) => set('shootType', e.target.value)}
            >
              {['婚纱写真', '亲子', '毕业', '商务', '写真', '儿童', '孕婴', '婚礼跟拍'].map((t) => (
                <MenuItem key={t} value={t}>{t}</MenuItem>
              ))}
            </TextField>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField type="number" label="金额" value={form.amount ?? ''} onChange={(e) => set('amount', Number(e.target.value))} fullWidth />
              <TextField type="number" label="定金" value={form.depositAmount ?? ''} onChange={(e) => set('depositAmount', Number(e.target.value))} fullWidth />
            </Box>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField type="date" label="拍摄日" value={form.shootDate ?? ''} onChange={(e) => set('shootDate', e.target.value)} fullWidth InputLabelProps={{ shrink: true }} />
              <TextField type="date" label="结束日" value={form.shootEndDate ?? ''} onChange={(e) => set('shootEndDate', e.target.value)} fullWidth InputLabelProps={{ shrink: true }} />
            </Box>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField type="number" label="时长(h)" value={form.durationHours ?? ''} onChange={(e) => set('durationHours', Number(e.target.value))} fullWidth />
              <TextField type="number" label="张数" value={form.photoCount ?? ''} onChange={(e) => set('photoCount', Number(e.target.value))} fullWidth />
            </Box>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="地区" value={form.region} onChange={(e) => set('region', e.target.value)} fullWidth />
              <TextField label="风格" value={form.style} onChange={(e) => set('style', e.target.value)} fullWidth />
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>取消</Button>
          <Button variant="contained" disabled={createMutation.isPending} onClick={() => createMutation.mutate()}>
            创建
          </Button>
        </DialogActions>
      </Dialog>

      {/* 状态流转 */}
      <Dialog open={!!statusTarget} onClose={() => setStatusTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>
          推进 / 回退：{statusTarget ? STATUS_LABELS[statusTarget.status] : ''}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={1}>
            {statusTarget &&
              NEXT_STATUSES[statusTarget.status].map((s) => (
                <Button
                  key={s}
                  variant="outlined"
                  onClick={() => statusMutation.mutate(s)}
                  disabled={statusMutation.isPending}
                >
                  {STATUS_LABELS[s]}
                </Button>
              ))}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setStatusTarget(null)}>关闭</Button>
        </DialogActions>
      </Dialog>

      <ConflictDialog
        open={conflict.open}
        conflicts={conflict.list}
        onClose={() => setConflict({ open: false, list: [] })}
      />
      <UpgradeModal open={upgradeOpen} onClose={() => setUpgradeOpen(false)} />
    </Box>
  );
}
