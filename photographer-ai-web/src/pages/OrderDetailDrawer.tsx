import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Drawer from '@mui/material/Drawer';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Stack from '@mui/material/Stack';
import Divider from '@mui/material/Divider';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import CloseIcon from '@mui/icons-material/Close';
import { orderApi } from '../api/order';
import { teamApi } from '../api/team';
import StatusBadge from '../components/StatusBadge';
import CommAssistantDrawer from '../components/CommAssistantDrawer';
import {
  STATUS_LABELS,
  NEXT_STATUSES,
  type Order,
  type OrderStatus,
  type TeamMember,
} from '../types/models';
import { useUiStore } from '../store/uiStore';
import { useAuth } from '../hooks/useAuth';
import dayjs from 'dayjs';

/**
 * 订单详情抽屉：信息展示 + 状态流转（相邻）+ 编辑 + 软删 + 流转留痕 + AI 沟通助手 + 团队分配。
 */
export default function OrderDetailDrawer({
  orderId,
  onClose,
}: {
  orderId: number | null;
  onClose: () => void;
}) {
  const { studio } = useAuth();
  const queryClient = useQueryClient();
  const showToast = useUiStore((s) => s.showToast);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Partial<Order>>({});
  const [commOpen, setCommOpen] = useState(false);

  const isTeam = studio?.planType === 'TEAM';

  const { data: order, isLoading } = useQuery({
    enabled: !!orderId,
    queryKey: ['order', orderId],
    queryFn: () => orderApi.get(orderId as number),
  });

  const { data: members } = useQuery<TeamMember[]>({
    enabled: isTeam && !!orderId,
    queryKey: ['team-members-drawer', orderId],
    queryFn: () => teamApi.members(),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['orders'] });
    queryClient.invalidateQueries({ queryKey: ['order', orderId] });
  };

  const statusMutation = useMutation({
    mutationFn: (to: OrderStatus) => orderApi.changeStatus(order!.id, to),
    onSuccess: () => {
      showToast('状态已更新', 'success');
      invalidate();
    },
    onError: (e: any) => showToast(e?.message ?? '状态更新失败', 'error'),
  });

  const updateMutation = useMutation({
    mutationFn: () => orderApi.update(order!.id, draft as any),
    onSuccess: () => {
      showToast('订单已更新', 'success');
      setEditing(false);
      invalidate();
    },
    onError: (e: any) => showToast(e?.message ?? '更新失败', 'error'),
  });

  const deleteMutation = useMutation({
    mutationFn: () => orderApi.remove(order!.id),
    onSuccess: () => {
      showToast('订单已删除', 'success');
      onClose();
      queryClient.invalidateQueries({ queryKey: ['orders'] });
    },
    onError: (e: any) => showToast(e?.message ?? '删除失败', 'error'),
  });

  const assignMutation = useMutation({
    mutationFn: (memberId: number | null) => orderApi.assign(order!.id, memberId),
    onSuccess: () => {
      showToast('已分配负责成员', 'success');
      invalidate();
    },
    onError: (e: any) => showToast(e?.message ?? '分配失败', 'error'),
  });

  return (
    <Drawer anchor="right" open={!!orderId} onClose={onClose}>
      <Box sx={{ width: 420, p: 3, maxWidth: '100vw' }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6">订单详情</Typography>
          <IconButton onClick={onClose}>
            <CloseIcon />
          </IconButton>
        </Box>

        {isLoading && <Typography>加载中…</Typography>}
        {order && (
          <>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
              <Typography variant="subtitle1" fontWeight={700}>
                {editing ? (
                  <TextField
                    size="small"
                    value={draft.title ?? order.title}
                    onChange={(e) => setDraft({ ...draft, title: e.target.value })}
                  />
                ) : (
                  order.title
                )}
              </Typography>
              <StatusBadge status={order.status} />
            </Box>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              客户：{order.customerName}
            </Typography>

            {isTeam && (
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="body2" color="text.secondary">
                  负责成员
                </Typography>
                <FormControl size="small" sx={{ minWidth: 160 }}>
                  <Select
                    value={order.assignedTo != null ? String(order.assignedTo) : ''}
                    disabled={assignMutation.isPending}
                    onChange={(e) =>
                      assignMutation.mutate(e.target.value === '' ? null : Number(e.target.value))
                    }
                  >
                    <MenuItem value="">未分配</MenuItem>
                    {(members ?? []).map((m) => (
                      <MenuItem key={m.id} value={String(m.id)}>
                        {m.username}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Box>
            )}

            <Divider sx={{ my: 2 }} />

            <Stack spacing={1.5}>
              <Field label="拍摄类型" value={order.shootType} editing={editing} draft={draft} field="shootType" setDraft={setDraft} />
              <Field label="金额" value={order.amount != null ? `¥${order.amount}` : '—'} editing={editing} draft={draft} field="amount" setDraft={setDraft} type="number" />
              <Field label="定金" value={order.depositAmount != null ? `¥${order.depositAmount}` : '—'} editing={editing} draft={draft} field="depositAmount" setDraft={setDraft} type="number" />
              <Field label="拍摄日" value={order.shootDate} editing={editing} draft={draft} field="shootDate" setDraft={setDraft} type="date" />
              <Field label="结束日" value={order.shootEndDate} editing={editing} draft={draft} field="shootEndDate" setDraft={setDraft} type="date" />
              <Field label="时长(小时)" value={order.durationHours} editing={editing} draft={draft} field="durationHours" setDraft={setDraft} type="number" />
              <Field label="张数" value={order.photoCount} editing={editing} draft={draft} field="photoCount" setDraft={setDraft} type="number" />
              <Field label="地区" value={order.region} editing={editing} draft={draft} field="region" setDraft={setDraft} />
              <Field label="风格" value={order.style} editing={editing} draft={draft} field="style" setDraft={setDraft} />
            </Stack>

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle2" gutterBottom>
              推进 / 回退状态
            </Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 1 }}>
              <Button
                size="small"
                variant="contained"
                color="secondary"
                onClick={() => setCommOpen(true)}
                disabled={!order}
              >
                AI 沟通助手
              </Button>
            </Box>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
              {NEXT_STATUSES[order.status].map((s) => (
                <Button
                  key={s}
                  size="small"
                  variant="outlined"
                  onClick={() => statusMutation.mutate(s)}
                  disabled={statusMutation.isPending}
                >
                  {STATUS_LABELS[s]}
                </Button>
              ))}
            </Box>

            {order.history && order.history.length > 0 && (
              <>
                <Typography variant="subtitle2" sx={{ mt: 2 }} gutterBottom>
                  流转记录
                </Typography>
                {order.history.map((h) => (
                  <Typography key={h.id} variant="caption" color="text.secondary" display="block">
                    {dayjs(h.createdAt).format('MM-DD HH:mm')} ：
                    {h.fromStatus ? STATUS_LABELS[h.fromStatus] : '—'} → {STATUS_LABELS[h.toStatus]}
                  </Typography>
                ))}
              </>
            )}

            <Divider sx={{ my: 2 }} />

            <Box sx={{ display: 'flex', gap: 1 }}>
              {editing ? (
                <>
                  <Button variant="contained" onClick={() => updateMutation.mutate()}>
                    保存
                  </Button>
                  <Button onClick={() => { setEditing(false); setDraft({}); }}>取消</Button>
                </>
              ) : (
                <Button variant="outlined" onClick={() => { setEditing(true); setDraft({}); }}>
                  编辑
                </Button>
              )}
              <Button color="error" onClick={() => deleteMutation.mutate()}>
                删除
              </Button>
            </Box>
          </>
        )}
      </Box>

      <CommAssistantDrawer
        orderId={order?.id ?? null}
        open={commOpen}
        onClose={() => setCommOpen(false)}
      />
    </Drawer>
  );
}

function Field({
  label,
  value,
  editing,
  draft,
  field,
  setDraft,
  type,
}: {
  label: string;
  value?: string | number;
  editing: boolean;
  draft: Partial<Order>;
  field: keyof Order;
  setDraft: (value: Partial<Order> | ((prev: Partial<Order>) => Partial<Order>)) => void;
  type?: string;
}) {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      {editing ? (
        <TextField
          size="small"
          type={type ?? 'text'}
          value={(draft[field] as any) ?? (value ?? '')}
          onChange={(e) =>
            setDraft((d) => ({
              ...d,
              [field]: type === 'number' ? Number(e.target.value) : e.target.value,
            }))
          }
          sx={{ width: 160 }}
        />
      ) : (
        <Typography variant="body2">{value ?? '—'}</Typography>
      )}
    </Box>
  );
}
