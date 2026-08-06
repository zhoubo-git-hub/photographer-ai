import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import FormControlLabel from '@mui/material/FormControlLabel';
import LockIcon from '@mui/icons-material/Lock';
import { reminderRuleApi } from '../api/reminderRule';
import { EVENT_LABELS, type ReminderRule, type ReminderRuleRequest } from '../types/models';
import { useUiStore } from '../store/uiStore';

/**
 * 提醒规则设置页（专业版）：启停 + 偏移天数配置。
 * 免费版访问会被后端 403 拦截并弹出升级弹窗，本页展示空态引导。
 */
export default function ReminderRulePage() {
  const queryClient = useQueryClient();
  const showToast = useUiStore((s) => s.showToast);
  const [drafts, setDrafts] = useState<Record<number, { offsetDays: number; enabled: boolean }>>({});

  const { data: rules = [], error, isLoading } = useQuery({
    queryKey: ['reminder-rules'],
    queryFn: () => reminderRuleApi.list(),
  });

  // 后端 403 → 统一升级弹窗已弹；此处仅作空态提示
  const isForbidden = (error as any)?.code === 403;

  useEffect(() => {
    const map: Record<number, { offsetDays: number; enabled: boolean }> = {};
    rules.forEach((r) => {
      map[r.id] = { offsetDays: r.offsetDays, enabled: r.enabled };
    });
    setDrafts(map);
  }, [rules]);

  const saveMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: ReminderRuleRequest }) =>
      reminderRuleApi.update(id, payload),
    onSuccess: () => {
      showToast('规则已保存', 'success');
      queryClient.invalidateQueries({ queryKey: ['reminder-rules'] });
    },
    onError: (e: any) => showToast(e?.message ?? '保存失败', 'error'),
  });

  if (isForbidden) {
    return <EmptyPro hint="提醒规则为专业版功能，升级后可为不同节点自定义提醒节奏。" />;
  }

  if (isLoading) {
    return <Typography sx={{ p: 3 }}>加载中…</Typography>;
  }

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} gutterBottom>
        提醒规则
        <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1 }}>
          （专业版）
        </Typography>
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        配置各节点的触发时机与提前量，关闭后该事件不再生成提醒。修改对新触发的订单生效。
      </Typography>

      <Stack spacing={2} sx={{ maxWidth: 560 }}>
        {rules.map((r: ReminderRule) => {
          const draft = drafts[r.id] ?? { offsetDays: r.offsetDays, enabled: r.enabled };
          return (
            <Card key={r.id}>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Typography fontWeight={600}>{EVENT_LABELS[r.event]}</Typography>
                  <FormControlLabel
                    control={
                      <Switch
                        checked={draft.enabled}
                        onChange={(e) =>
                          setDrafts((d) => ({
                            ...d,
                            [r.id]: { ...draft, enabled: e.target.checked },
                          }))
                        }
                      />
                    }
                    label={draft.enabled ? '启用' : '停用'}
                  />
                </Box>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
                  <Typography variant="body2" color="text.secondary">
                    {r.event === 'REPURCHASE' ? '周期后' : r.event === 'SHOOT' ? '拍摄前' : '之后'}
                  </Typography>
                  <TextField
                    type="number"
                    size="small"
                    value={draft.offsetDays}
                    onChange={(e) =>
                      setDrafts((d) => ({
                        ...d,
                        [r.id]: { ...draft, offsetDays: Number(e.target.value) },
                      }))
                    }
                    sx={{ width: 90 }}
                  />
                  <Typography variant="body2" color="text.secondary">天</Typography>
                  <Box sx={{ flex: 1 }} />
                  <Button
                    variant="contained"
                    size="small"
                    disabled={saveMutation.isPending}
                    onClick={() =>
                      saveMutation.mutate({
                        id: r.id,
                        payload: {
                          event: r.event,
                          offsetDays: draft.offsetDays,
                          enabled: draft.enabled,
                          channel: r.channel,
                        },
                      })
                    }
                  >
                    保存
                  </Button>
                </Box>
              </CardContent>
            </Card>
          );
        })}
      </Stack>
    </Box>
  );
}

function EmptyPro({ hint }: { hint: string }) {
  return (
    <Box sx={{ maxWidth: 480, mx: 'auto', mt: 8, textAlign: 'center' }}>
      <LockIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
      <Typography variant="h6" gutterBottom>
        专业版功能
      </Typography>
      <Typography variant="body2" color="text.secondary">
        {hint}
      </Typography>
    </Box>
  );
}
