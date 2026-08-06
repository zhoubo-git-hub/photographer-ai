import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  Cell,
} from 'recharts';
import { dashboardApi } from '../api/dashboard';
import { useAuth } from '../hooks/useAuth';
import type {
  FunnelDTO,
  MemberPerfDTO,
  OverviewDTO,
  OrderStatus,
} from '../types/models';
import { STATUS_LABELS } from '../types/models';

const FUNNEL_COLORS = ['#90caf9', '#64b5f6', '#42a5f5', '#2196f3', '#1976d2'];

function statCards(o: OverviewDTO | undefined): { label: string; value: string }[] {
  return [
    { label: '总收入', value: `¥${Math.round(o?.revenue ?? 0)}` },
    { label: '订单数', value: `${o?.orderCount ?? 0}` },
    { label: '客单价', value: `¥${Math.round(o?.aov ?? 0)}` },
    { label: '复购率', value: `${Math.round((o?.repurchaseRate ?? 0) * 100)}%` },
  ];
}

/**
 * 经营看板页（阶段3 批次 C）：纯聚合洞察。
 * 收入趋势（折线）+ 转化漏斗（横向柱）+ 转化率 + 成员业绩（团队版）。
 */
export default function DashboardPage() {
  const { studio } = useAuth();
  const isTeam = studio?.planType === 'TEAM';

  const { data: overview } = useQuery<OverviewDTO>({
    queryKey: ['dashboard-overview'],
    queryFn: () => dashboardApi.overview(),
  });
  const { data: funnel } = useQuery<FunnelDTO>({
    queryKey: ['dashboard-funnel'],
    queryFn: () => dashboardApi.funnel(),
  });
  const { data: members } = useQuery<MemberPerfDTO[]>({
    enabled: isTeam,
    queryKey: ['dashboard-members'],
    queryFn: () => dashboardApi.members(),
  });

  const stages = funnel?.stages ?? [];

  return (
    <Box>
      <Typography variant="h5" fontWeight={800} gutterBottom>
        经营看板
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        基于订单与流转数据的纯聚合洞察（无埋点）。
      </Typography>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {statCards(overview).map((c) => (
          <Grid key={c.label} item xs={6} md={3}>
            <Paper sx={{ p: 2, borderRadius: 3 }} elevation={1}>
              <Typography variant="caption" color="text.secondary">
                {c.label}
              </Typography>
              <Typography variant="h5" fontWeight={800}>
                {c.value}
              </Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <Paper sx={{ p: 2.5, borderRadius: 3 }} elevation={1}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom>
              收入趋势
            </Typography>
            <ResponsiveContainer width="100%" height={260}>
              <LineChart
                data={overview?.revenuePoints ?? []}
                margin={{ top: 8, right: 16, bottom: 8, left: 0 }}
              >
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="period" fontSize={12} />
                <YAxis fontSize={12} />
                <Tooltip />
                <Line
                  type="monotone"
                  dataKey="revenue"
                  name="收入"
                  stroke="#1976d2"
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </Paper>
        </Grid>
        <Grid item xs={12} md={5}>
          <Paper sx={{ p: 2.5, borderRadius: 3 }} elevation={1}>
            <Typography variant="subtitle1" fontWeight={700} gutterBottom>
              转化漏斗
            </Typography>
            <ResponsiveContainer width="100%" height={260}>
              <BarChart
                layout="vertical"
                data={stages}
                margin={{ top: 8, right: 16, bottom: 8, left: 8 }}
              >
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis type="number" fontSize={12} />
                <YAxis type="category" dataKey="status" fontSize={12} width={56} />
                <Tooltip />
                <Bar dataKey="count" name="数量" radius={[0, 4, 4, 0]}>
                  {stages.map((_, i) => (
                    <Cell key={i} fill={FUNNEL_COLORS[i % FUNNEL_COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </Paper>
        </Grid>
      </Grid>

      {funnel?.stages && funnel.stages.length > 0 && (
        <Paper sx={{ p: 2.5, borderRadius: 3, mt: 2 }} elevation={1}>
          <Typography variant="subtitle1" fontWeight={700} gutterBottom>
            漏斗转化率
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {funnel.stages.map((s) => (
              <Chip
                key={s.status}
                label={`${STATUS_LABELS[s.status as OrderStatus] ?? s.status}：${s.count} 单 · ${Math.round((s.rate ?? 0) * 100)}%`}
                variant="outlined"
              />
            ))}
          </Stack>
        </Paper>
      )}

      {isTeam && (
        <Paper sx={{ p: 2.5, borderRadius: 3, mt: 2 }} elevation={1}>
          <Typography variant="subtitle1" fontWeight={700} gutterBottom>
            成员业绩拆分
          </Typography>
          <Stack spacing={1}>
            {(members ?? []).map((m) => (
              <Box key={m.memberId} sx={{ display: 'flex', justifyContent: 'space-between' }}>
                <Typography variant="body2">{m.name}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {m.orderCount} 单 · ¥{Math.round(m.revenue)} · 客单价 ¥{Math.round(m.aov)}
                </Typography>
              </Box>
            ))}
            {(!members || members.length === 0) && (
              <Typography variant="body2" color="text.secondary">
                暂无成员业绩数据。
              </Typography>
            )}
          </Stack>
        </Paper>
      )}
    </Box>
  );
}
