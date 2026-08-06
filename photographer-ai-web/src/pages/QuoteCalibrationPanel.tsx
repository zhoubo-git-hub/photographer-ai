import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import { calibrationApi } from '../api/quoteCalibration';
import { useAuth } from '../hooks/useAuth';
import { useUiStore } from '../store/uiStore';
import type { QuoteCalibration } from '../types/models';

const STATUS_LABEL: Record<string, string> = {
  PENDING: '待采纳',
  APPLIED: '已采纳',
  REJECTED: '已拒绝',
};
const MIN_SAMPLE = 20;
const MAX_OFFSET = 15;

/**
 * AI 报价校准面板（阶段3 批次 D，受限版）：展示分维度校准建议，人工采纳写回。
 * 安全边界：样本≥20 且 偏离≤±15% 才可采纳；免费版不开放。
 */
export default function QuoteCalibrationPanel() {
  const { studio } = useAuth();
  const showToast = useUiStore((s) => s.showToast);
  const openUpgrade = useUiStore((s) => s.openUpgrade);
  const queryClient = useQueryClient();
  const isFree = studio?.planType === 'FREE';

  const { data: list, isLoading } = useQuery<QuoteCalibration[]>({
    enabled: !isFree,
    queryKey: ['calibration'],
    queryFn: () => calibrationApi.list(),
  });

  const applyMutation = useMutation({
    mutationFn: (id: number) => calibrationApi.apply(id),
    onSuccess: () => {
      showToast('已采纳校准建议，系数已写回', 'success');
      queryClient.invalidateQueries({ queryKey: ['calibration'] });
    },
    onError: (e: any) => showToast(e?.message ?? '采纳失败', 'error'),
  });

  if (isFree) {
    return (
      <Box>
        <Typography variant="h5" fontWeight={800} gutterBottom>
          AI 报价校准
        </Typography>
        <Paper sx={{ p: 4, textAlign: 'center', borderRadius: 3 }} elevation={1}>
          <Typography variant="body1" gutterBottom>
            AI 自学习报价校准为<strong>专业版</strong>能力
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            升级后可基于实际成交数据生成分维度校准建议，并人工采纳写回。
          </Typography>
          <Button variant="contained" onClick={() => openUpgrade('AI 报价校准需升级专业版')}>
            升级专业版
          </Button>
        </Paper>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h5" fontWeight={800} gutterBottom>
        AI 报价校准
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        系统基于实际成交（交付 / 复购）数据，按 地区 / 拍摄类型 / 风格 维度对比规则价与成交价，生成校准建议。
        安全边界：样本≥{MIN_SAMPLE} 且 偏离≤±{MAX_OFFSET}%。仅可人工采纳，不自动覆盖。
      </Typography>
      {isLoading && <Typography variant="body2">扫描中…</Typography>}
      <Stack spacing={1.5}>
        {(list ?? []).map((c) => (
          <Paper key={c.id} sx={{ p: 2, borderRadius: 3 }} elevation={0} variant="outlined">
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={2}
              justifyContent="space-between"
              alignItems={{ sm: 'center' }}
            >
              <Box>
                <Typography variant="body1" fontWeight={600}>
                  {c.dimensionLabel}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  样本 {c.sampleCount} · 当前系数 {c.currentCoef.toFixed(2)} → 建议{' '}
                  {c.suggestedCoef.toFixed(2)} · 偏离 {c.offsetPct.toFixed(1)}%
                </Typography>
                <Box sx={{ mt: 0.5 }}>
                  <Chip
                    size="small"
                    label={STATUS_LABEL[c.status] ?? c.status}
                    color={c.status === 'APPLIED' ? 'success' : 'default'}
                    sx={{ mr: 0.5 }}
                  />
                  {c.withinBoundary ? (
                    <Chip size="small" label="边界内" color="success" variant="outlined" />
                  ) : (
                    <Chip size="small" label="超出安全边界" color="warning" variant="outlined" />
                  )}
                </Box>
                {!c.withinBoundary && (
                  <Typography variant="caption" color="error" display="block" sx={{ mt: 0.5 }}>
                    {c.sampleCount < MIN_SAMPLE
                      ? `样本不足（需≥${MIN_SAMPLE}）`
                      : `偏离超出安全边界（±${MAX_OFFSET}%）`}
                  </Typography>
                )}
              </Box>
              <Button
                variant="contained"
                disabled={!c.withinBoundary || c.status === 'APPLIED' || applyMutation.isPending}
                onClick={() => applyMutation.mutate(c.id)}
              >
                {c.status === 'APPLIED' ? '已采纳' : '采纳建议'}
              </Button>
            </Stack>
          </Paper>
        ))}
        {list && list.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            暂无足够成交样本，继续完成更多订单后将自动生成校准建议。
          </Typography>
        )}
      </Stack>
    </Box>
  );
}
