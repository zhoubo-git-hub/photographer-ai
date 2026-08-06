import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Chip from '@mui/material/Chip';
import { teamApi } from '../api/team';
import { useAuth } from '../hooks/useAuth';
import { useUiStore } from '../store/uiStore';
import type { TeamMember, TeamRole } from '../types/models';

const ROLE_LABELS: Record<TeamRole, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  MEMBER: '成员',
  READONLY: '只读',
};

/**
 * 团队协作页（阶段3 批次 B）：邀请成员、角色管理、移除。
 * 仅团队版可用；免费/专业版展示升级引导。
 */
export default function TeamPage() {
  const { studio } = useAuth();
  const showToast = useUiStore((s) => s.showToast);
  const openUpgrade = useUiStore((s) => s.openUpgrade);
  const queryClient = useQueryClient();
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [role, setRole] = useState<TeamRole>('MEMBER');

  const isTeam = studio?.planType === 'TEAM';

  const { data: members, isLoading } = useQuery<TeamMember[]>({
    enabled: isTeam,
    queryKey: ['team-members'],
    queryFn: () => teamApi.members(),
  });

  const inviteMutation = useMutation({
    mutationFn: () => teamApi.invite(email.trim(), role, phone.trim() || undefined),
    onSuccess: () => {
      showToast('邀请已发送', 'success');
      setEmail('');
      setPhone('');
      setRole('MEMBER');
      queryClient.invalidateQueries({ queryKey: ['team-members'] });
    },
    onError: (e: any) => showToast(e?.message ?? '邀请失败', 'error'),
  });

  const updateRoleMutation = useMutation({
    mutationFn: ({ id, role }: { id: number; role: TeamRole }) => teamApi.updateRole(id, role),
    onSuccess: () => {
      showToast('角色已更新', 'success');
      queryClient.invalidateQueries({ queryKey: ['team-members'] });
    },
    onError: (e: any) => showToast(e?.message ?? '更新失败', 'error'),
  });

  const removeMutation = useMutation({
    mutationFn: (id: number) => teamApi.remove(id),
    onSuccess: () => {
      showToast('成员已移除，其名下订单回退未分配', 'success');
      queryClient.invalidateQueries({ queryKey: ['team-members'] });
    },
    onError: (e: any) => showToast(e?.message ?? '移除失败', 'error'),
  });

  if (!isTeam) {
    return (
      <Box>
        <Typography variant="h5" fontWeight={800} gutterBottom>
          团队协作
        </Typography>
        <Paper sx={{ p: 4, textAlign: 'center', borderRadius: 3 }} elevation={1}>
          <Typography variant="body1" gutterBottom>
            团队协作需要<strong>团队版</strong>套餐
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            升级后可邀请成员、分配订单、查看成员业绩拆分。
          </Typography>
          <Button variant="contained" onClick={() => openUpgrade('团队协作功能需升级团队版')}>
            升级团队版
          </Button>
        </Paper>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="h5" fontWeight={800} gutterBottom>
        团队协作
      </Typography>

      <Paper sx={{ p: 2.5, mb: 3, borderRadius: 3 }} elevation={1}>
        <Typography variant="subtitle1" fontWeight={700} gutterBottom>
          邀请新成员
        </Typography>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems="flex-end">
          <TextField label="邮箱" type="email" value={email} onChange={(e) => setEmail(e.target.value)} fullWidth />
          <TextField label="手机号（可选）" value={phone} onChange={(e) => setPhone(e.target.value)} fullWidth />
          <FormControl sx={{ minWidth: 140 }}>
            <InputLabel>角色</InputLabel>
            <Select
              label="角色"
              value={role}
              onChange={(e) => setRole(e.target.value as TeamRole)}
            >
              <MenuItem value="ADMIN">管理员</MenuItem>
              <MenuItem value="MEMBER">成员</MenuItem>
              <MenuItem value="READONLY">只读</MenuItem>
            </Select>
          </FormControl>
          <Button
            variant="contained"
            disabled={!email.trim() || inviteMutation.isPending}
            onClick={() => inviteMutation.mutate()}
          >
            发送邀请
          </Button>
        </Stack>
      </Paper>

      <Typography variant="subtitle1" fontWeight={700} gutterBottom>
        成员列表（{members?.length ?? 0}）
      </Typography>
      {isLoading && <Typography variant="body2" color="text.secondary">加载中…</Typography>}
      <Stack spacing={1.5}>
        {(members ?? []).map((m) => (
          <Paper key={m.id} sx={{ p: 2, borderRadius: 2 }} elevation={0} variant="outlined">
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={2}
              justifyContent="space-between"
              alignItems={{ sm: 'center' }}
            >
              <Box>
                <Typography variant="body1" fontWeight={600}>
                  {m.username}
                  {m.email ? ` · ${m.email}` : ''}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  负责订单 {m.orderCount} 单
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip
                  size="small"
                  label={ROLE_LABELS[m.role] ?? m.role}
                  color={m.role === 'OWNER' ? 'primary' : 'default'}
                />
                <FormControl size="small" sx={{ minWidth: 110 }}>
                  <Select
                    value={m.role}
                    disabled={m.role === 'OWNER'}
                    onChange={(e) => updateRoleMutation.mutate({ id: m.id, role: e.target.value as TeamRole })}
                  >
                    <MenuItem value="ADMIN">管理员</MenuItem>
                    <MenuItem value="MEMBER">成员</MenuItem>
                    <MenuItem value="READONLY">只读</MenuItem>
                  </Select>
                </FormControl>
                {m.role !== 'OWNER' && (
                  <Button color="error" size="small" onClick={() => removeMutation.mutate(m.id)}>
                    移除
                  </Button>
                )}
              </Stack>
            </Stack>
          </Paper>
        ))}
        {members && members.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            暂无其他成员，发送邀请开始协作。
          </Typography>
        )}
      </Stack>
    </Box>
  );
}
