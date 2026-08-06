import { useState } from 'react';
import { Button, Input, Text, View } from '@tarojs/components';
import { useQuote } from '@photogai/shared/hooks';
import type { QuoteRequest, QuoteResponse } from '@photogai/shared/types';
import { formatPriceRange } from '@photogai/shared/domain';
import PageContainer from '@/components/PageContainer';
import { showApiError, showInfo } from '@/lib/toast';
import './index.scss';

const SHOOT_TYPES = ['婚纱', '亲子', '个人写真', '全家福', '商业拍摄'];

/**
 * AI 报价页（查看/轻操作版）。
 * 表单收集报价请求 → useQuote().quote.mutate → 展示区间价/依据/话术/剩余次数。
 * 「一键填入订单」为 Web 端能力，小程序侧仅暂存 pendingQuote（经 uiStore），
 * 并提示用户到 Web 端完成下单，符合「查看/轻操作版」定位。
 */
export default function QuotePage() {
  const { quote } = useQuote();
  const [form, setForm] = useState<QuoteRequest>({
    shootType: '',
    durationHours: undefined,
    photoCount: undefined,
    region: '',
    style: '',
    customerName: '',
  });

  const result = quote.data as QuoteResponse | undefined;

  const setField = (key: keyof QuoteRequest, value: string): void => {
    setForm((prev) => {
      if (key === 'durationHours' || key === 'photoCount') {
        const num = value === '' ? undefined : Number(value);
        return { ...prev, [key]: Number.isFinite(num) ? num : undefined };
      }
      return { ...prev, [key]: value };
    });
  };

  const handleSubmit = (): void => {
    if (!form.shootType) {
      showApiError(new Error('请选择拍摄类型'), '请先选择拍摄类型');
      return;
    }
    quote.mutate(form, {
      onSuccess: () => showInfo('报价已生成，可在 Web 端一键填入订单'),
    });
  };

  return (
    <PageContainer>
      <View className="quote-page">
        <View className="quote-page__form">
          <Text className="quote-page__label">拍摄类型 *</Text>
          <View className="quote-page__types">
            {SHOOT_TYPES.map((t) => (
              <View
                key={t}
                className={`quote-page__type ${
                  form.shootType === t ? 'quote-page__type--active' : ''
                }`}
                onClick={() => setField('shootType', t)}
              >
                <Text className="quote-page__type-text">{t}</Text>
              </View>
            ))}
          </View>

          <Text className="quote-page__label">客户名称（选填）</Text>
          <Input
            className="quote-page__input"
            placeholder="如：王小姐"
            value={form.customerName}
            onInput={(e) => setField('customerName', e.detail.value)}
          />

          <Text className="quote-page__label">拍摄时长（小时，选填）</Text>
          <Input
            className="quote-page__input"
            type="number"
            placeholder="如：2"
            value={form.durationHours != null ? String(form.durationHours) : ''}
            onInput={(e) => setField('durationHours', e.detail.value)}
          />

          <Text className="quote-page__label">成片张数（选填）</Text>
          <Input
            className="quote-page__input"
            type="number"
            placeholder="如：30"
            value={form.photoCount != null ? String(form.photoCount) : ''}
            onInput={(e) => setField('photoCount', e.detail.value)}
          />

          <Text className="quote-page__label">地区（选填）</Text>
          <Input
            className="quote-page__input"
            placeholder="如：上海"
            value={form.region}
            onInput={(e) => setField('region', e.detail.value)}
          />

          <Text className="quote-page__label">风格（选填）</Text>
          <Input
            className="quote-page__input"
            placeholder="如：清新自然"
            value={form.style}
            onInput={(e) => setField('style', e.detail.value)}
          />
        </View>

        <Button
          className="quote-page__submit"
          loading={quote.isPending}
          onClick={handleSubmit}
        >
          生成报价
        </Button>

        {result ? (
          <View className="quote-page__result">
            <Text className="quote-page__price">
              {formatPriceRange(result.priceLow, result.priceHigh)}
            </Text>
            <Text className="quote-page__basis">{result.basis}</Text>
            <Text className="quote-page__script">{result.script}</Text>
            <Text className="quote-page__quota">
              本月剩余 AI 报价次数：{result.remainingQuota}
            </Text>
          </View>
        ) : null}
      </View>
    </PageContainer>
  );
}
