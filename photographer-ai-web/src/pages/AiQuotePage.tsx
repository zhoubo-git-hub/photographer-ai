import { useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import AiQuoteForm from '../components/AiQuoteForm';
import { quotaApi } from '../api/quota';
import UpgradeModal from '../components/UpgradeModal';
import { useState } from 'react';

/**
 * AI 报价助手页：表单 + 结果 + 免费额度展示。
 */
export default function AiQuotePage() {
  const location = useLocation();
  const initialCustomerName = (location.state as any)?.customerName as string | undefined;
  const [upgradeOpen, setUpgradeOpen] = useState(false);

  const { data: quota } = useQuery({
    queryKey: ['quota'],
    queryFn: () => quotaApi.get(),
  });

  // 额度不足时引导升级
  const aiRemaining = quota?.remainingAiQuota ?? 0;
  const showQuotaHint = quota && quota.planType === 'FREE';

  return (
    <Box>
      <Typography variant="h6" color="ink" gutterBottom>
        AI 智能报价
      </Typography>
      <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <AiQuoteForm initialCustomerName={initialCustomerName} />

        <Paper sx={{ p: 2, minWidth: 240 }}>
          <Typography variant="subtitle2" gutterBottom>
            额度
          </Typography>
          {showQuotaHint ? (
            <>
              <Typography variant="body2" color="text.secondary">
                本月 AI 报价剩余：<b>{aiRemaining}</b> / 5 次
              </Typography>
              <Typography variant="caption" color="text.secondary">
                升级专业版可无限次使用 AI 功能。
              </Typography>
              <Box sx={{ mt: 1 }}>
                <Typography
                  component="span"
                  variant="caption"
                  sx={{ color: 'primary.main', cursor: 'pointer' }}
                  onClick={() => setUpgradeOpen(true)}
                >
                  了解专业版 →
                </Typography>
              </Box>
            </>
          ) : (
            <Typography variant="body2" color="success.main">
              专业版：AI 报价无限次
            </Typography>
          )}
        </Paper>
      </Box>

      <UpgradeModal open={upgradeOpen} onClose={() => setUpgradeOpen(false)} />
    </Box>
  );
}
