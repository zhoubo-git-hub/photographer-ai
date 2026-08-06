import { useRef, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import CircularProgress from '@mui/material/CircularProgress';
import { billingApi } from '../api/billing';
import { useUiStore } from '../store/uiStore';
import { useAuth } from '../hooks/useAuth';
import type { SubscriptionView } from '../types/models';
import dayjs from 'dayjs';

const PLANS = [
  {
    planType: 'PRO' as const,
    name: '专业版',
    price: 39,
    features: ['不限订单数量', 'AI 报价无限次', '客户画像与标签', '合同 / 提醒 / 复购', '经营数据看板'],
  },
  {
    planType: 'TEAM' as const,
    name: '团队版',
    price: 99,
    features: ['含专业版全部能力', '多人协同接单', '订单分配与流转', '成员业绩拆分', '团队权限管理'],
  },
];

const PLAN_LABEL: Record<string, string> = { FREE: '免费版', PRO: '专业版', TEAM: '团队版' };
const STATUS_LABEL: Record<string, string> = {
  ACTIVE: '生效中',
  CANCELLED: '已退订',
  EXPIRED: '已到期',
  NONE: '未订阅',
};

function formatMoney(n?: number | null): string {
  return `¥${Math.round(n ?? 0)}`;
}

/**
 * 订阅 / 升级页（阶段3 批次 A）：套餐选择、下单、模拟支付闭环、退订。
 * 真实通道由后端接收回调；前端仅演示 mock 支付。
 */
export default function BillingPage() {
  const { studio, setStudioPlanType } = useAuth();
  const showToast = useUiStore((s) => s.showToast);
  const queryClient = useQueryClient();
  const [pay, setPay] = useState<{ outTradeNo: string; amount: number; qrCode?: string } | null>(null);
  const pendingPlanRef = useRef<'PRO' | 'TEAM'>('PRO');

  const currentPlan = studio?.planType ?? 'FREE';

  const { data: sub } = useQuery<SubscriptionView | null>({
    queryKey: ['subscription'],
    queryFn: () => billingApi.getSubscription(),
  });

  const subscribeMutation = useMutation({
    mutationFn: (planType: 'PRO' | 'TEAM') => billingApi.subscribe({ planType, channel: 'MOCK' }),
    onSuccess: (resp, variables) => { pendingPlanRef.current = variables; setPay({ outTradeNo: resp.outTradeNo, amount: resp.amount, qrCode: resp.qrCode }); },
    onError: (e: any) => showToast(e?.message ?? '下单失败', 'error'),
  });

  const mockPayMutation = useMutation({
    mutationFn: (outTradeNo: string) => billingApi.mockPay(outTradeNo),
    onSuccess: () => {
      showToast('订阅成功，已开通对应套餐', 'success');
      setPay(null);
      setStudioPlanType(pendingPlanRef.current);
      queryClient.invalidateQueries({ queryKey: ['subscription'] });
    },
    onError: (e: any) => showToast(e?.message ?? '支付失败', 'error'),
  });

  const cancelMutation = useMutation({
    mutationFn: () => billingApi.cancel(),
    onSuccess: () => {
      showToast('已关闭自动续费', 'info');
      queryClient.setQueryData<SubscriptionView | null>(
        ['subscription'],
        (old) => (old ? { ...old, autoRenew: false } : old),
      );
      queryClient.invalidateQueries({ queryKey: ['subscription'] });
    },
    onError: (e: any) => showToast(e?.message ?? '操作失败', 'error'),
  });

  const active = !!sub && sub.status === 'ACTIVE';

  return (
    <Box>
      <Typography variant="h5" fontWeight={800} gutterBottom>
        订阅 / 升级
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        当前套餐：<strong>{PLAN_LABEL[currentPlan]}</strong>
        {sub && sub.status !== 'NONE' && (
          <span>
            {' · '}
            状态 {STATUS_LABEL[sub.status] ?? sub.status}
            {sub.expiresAt && ` · 到期 ${dayjs(sub.expiresAt).format('YYYY-MM-DD')}`}
            {sub.autoRenew ? ' · 自动续费' : ''}
          </span>
        )}
      </Typography>

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 3 }}>
        {PLANS.map((p) => {
          const isCurrent = currentPlan === p.planType;
          return (
            <Paper
              key={p.planType}
              elevation={isCurrent ? 6 : 1}
              sx={{
                p: 3,
                flex: 1,
                border: isCurrent ? '2px solid' : '1px solid',
                borderColor: isCurrent ? 'primary.main' : 'divider',
                borderRadius: 3,
              }}
            >
              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                <Typography variant="h6" fontWeight={800}>
                  {p.name}
                </Typography>
                {isCurrent && <Chip size="small" color="primary" label="当前套餐" />}
              </Box>
              <Typography variant="h4" color="primary" fontWeight={800} gutterBottom>
                ¥{p.price}
                <Typography component="span" variant="body2" color="text.secondary">
                  {' '}
                  /月
                </Typography>
              </Typography>
              <Stack component="ul" spacing={0.5} sx={{ pl: 2.5, mb: 2 }}>
                {p.features.map((f) => (
                  <Typography component="li" key={f} variant="body2">
                    {f}
                  </Typography>
                ))}
              </Stack>
              {isCurrent && active && sub?.autoRenew ? (
                <Button
                  fullWidth
                  variant="outlined"
                  color="error"
                  disabled={cancelMutation.isPending}
                  onClick={() => cancelMutation.mutate()}
                >
                  关闭自动续费
                </Button>
              ) : isCurrent && active && !sub?.autoRenew ? (
                <Button fullWidth variant="outlined" disabled>
                  已关闭自动续费
                </Button>
              ) : (
                <Button
                  fullWidth
                  variant="contained"
                  disabled={subscribeMutation.isPending}
                  onClick={() => subscribeMutation.mutate(p.planType)}
                >
                  {currentPlan === 'FREE' ? '立即升级' : '切换套餐'}
                </Button>
              )}
            </Paper>
          );
        })}
      </Stack>

      <Dialog open={!!pay} onClose={() => setPay(null)} maxWidth="xs" fullWidth>
        <DialogTitle>完成支付（演示）</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            本次应付：<strong>{formatMoney(pay?.amount)}</strong>
            （商户单号 {pay?.outTradeNo}）
          </Typography>
          {pay?.qrCode ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', my: 2 }}>
              <img src={pay.qrCode} alt="qr" style={{ width: 200, height: 200 }} />
            </Box>
          ) : (
            <Typography variant="body2" sx={{ my: 2 }}>
              （演示环境无二维码，点击下方按钮模拟支付成功）
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPay(null)}>稍后</Button>
          <Button
            variant="contained"
            disabled={mockPayMutation.isPending}
            startIcon={mockPayMutation.isPending ? <CircularProgress size={16} /> : null}
            onClick={() => pay && mockPayMutation.mutate(pay.outTradeNo)}
          >
            模拟支付成功
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
