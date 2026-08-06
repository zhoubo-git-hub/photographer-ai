import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import { aiApi } from '../api/ai';
import { useUiStore } from '../store/uiStore';
import type { QuoteRequest, QuoteResponse } from '../types/models';

const SHOOT_TYPES = ['婚纱写真', '亲子', '毕业', '商务', '写真', '儿童', '孕婴', '婚礼跟拍'];
const STYLES = ['轻奢', '高级感', '复古', '简约', '韩式', '自然'];
const REGIONS = ['上海', '北京', '广州', '深圳', '成都', '杭州', '武汉', '其他'];

/**
 * AI 报价表单 + 结果展示。支持复制话术、一键填入订单。
 */
export default function AiQuoteForm({
  initialCustomerName,
}: {
  initialCustomerName?: string;
}) {
  const navigate = useNavigate();
  const showToast = useUiStore((s) => s.showToast);
  const setPendingQuote = useUiStore((s) => s.setPendingQuote);
  const setPendingQuoteRequest = useUiStore((s) => s.setPendingQuoteRequest);

  const [form, setForm] = useState<QuoteRequest>({
    shootType: '婚纱写真',
    durationHours: 4,
    photoCount: 80,
    region: '上海',
    style: '轻奢',
    customerName: initialCustomerName ?? '',
  });
  const [result, setResult] = useState<QuoteResponse | null>(null);

  const mutation = useMutation({
    mutationFn: (req: QuoteRequest) => aiApi.quote(req),
    onSuccess: (data, variables) => {
      setResult(data);
      setPendingQuote(data);
      setPendingQuoteRequest(variables);
    },
    onError: (e: any) => {
      showToast(e?.message ?? '报价失败', 'error');
    },
  });

  const setField = (k: keyof QuoteRequest, v: any) =>
    setForm((f) => ({ ...f, [k]: v }));

  const copyScript = () => {
    if (result?.script) {
      navigator.clipboard?.writeText(result.script);
      showToast('话术已复制', 'success');
    }
  };

  return (
    <Box>
      <Paper sx={{ p: 3, maxWidth: 520 }}>
        <Stack spacing={2}>
          <TextField
            select
            label="拍摄类型"
            value={form.shootType}
            onChange={(e) => setField('shootType', e.target.value)}
          >
            {SHOOT_TYPES.map((t) => (
              <MenuItem key={t} value={t}>{t}</MenuItem>
            ))}
          </TextField>
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              type="number"
              label="时长(小时)"
              value={form.durationHours}
              onChange={(e) => setField('durationHours', Number(e.target.value))}
              fullWidth
            />
            <TextField
              type="number"
              label="张数"
              value={form.photoCount}
              onChange={(e) => setField('photoCount', Number(e.target.value))}
              fullWidth
            />
          </Box>
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              select
              label="地区"
              value={form.region}
              onChange={(e) => setField('region', e.target.value)}
              fullWidth
            >
              {REGIONS.map((r) => (
                <MenuItem key={r} value={r}>{r}</MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="风格"
              value={form.style}
              onChange={(e) => setField('style', e.target.value)}
              fullWidth
            >
              {STYLES.map((s) => (
                <MenuItem key={s} value={s}>{s}</MenuItem>
              ))}
            </TextField>
          </Box>
          <TextField
            label="客户称呼(选填)"
            value={form.customerName}
            onChange={(e) => setField('customerName', e.target.value)}
            placeholder="用于生成话术，如 王小姐"
          />
          <Button
            variant="contained"
            size="large"
            disabled={mutation.isPending}
            onClick={() => mutation.mutate(form)}
          >
            {mutation.isPending ? '生成中…' : '生成报价'}
          </Button>
        </Stack>
      </Paper>

      {result && (
        <Paper sx={{ p: 3, maxWidth: 520, mt: 2, borderColor: 'primary.main', borderLeft: '4px solid' }}>
          <Typography variant="h5" color="primary" fontWeight={800}>
            ¥{result.priceLow} – ¥{result.priceHigh}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            依据：{result.basis}
          </Typography>
          <Typography variant="body2" sx={{ mt: 1.5, whiteSpace: 'pre-wrap' }}>
            {result.script}
          </Typography>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            免费剩余：本月 {result.remainingQuota} 次
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
            <Button startIcon={<ContentCopyIcon />} onClick={copyScript}>
              复制话术
            </Button>
      <Button
        variant="outlined"
        startIcon={<ArrowForwardIcon />}
        onClick={() => navigate('/orders', { state: { openCreate: true } })}
      >
        一键填入订单
      </Button>
          </Stack>
        </Paper>
      )}
    </Box>
  );
}
