import { Button, Text, View } from '@tarojs/components';
import { useAuth, useSubscription } from '@photogai/shared/hooks';
import { useUiStore } from '@photogai/shared/store';
import type { PlanType } from '@photogai/shared/types';
import { formatDate } from '@photogai/shared/domain';
import PageContainer from '@/components/PageContainer';
import { ROUTES, reLaunchTo, toNotices, toReminders } from '@/lib/nav';
import { logoutWechat } from '@/lib/wechat';
import './index.scss';

const PLAN_LABELS: Record<PlanType, string> = {
  FREE: '免费版',
  PRO: '专业版',
  TEAM: '团队版',
};

/**
 * 「我的」页（tab 末位）。
 * 展示登录用户/工作室信息、订阅状态与到期黄条（来自 uiStore.expiredBanner），
 * 提供提醒事项/通知中心入口与退出登录。
 */
export default function MinePage() {
  const { user, studio } = useAuth();
  const { data: subscription } = useSubscription();
  const expiredBanner = useUiStore((s) => s.expiredBanner);

  const handleLogout = (): void => {
    logoutWechat();
    reLaunchTo(ROUTES.login);
  };

  const planLabel = studio ? PLAN_LABELS[studio.planType] : '';

  return (
    <PageContainer>
      <View className="mine-page">
        {expiredBanner ? (
          <View className="mine-page__banner">订阅已到期，部分功能受限，请前往 Web 端续费</View>
        ) : null}

        <View className="mine-page__profile">
          <Text className="mine-page__name">{user?.username ?? '摄影师'}</Text>
          <Text className="mine-page__studio">{studio?.name ?? ''}</Text>
          <Text className="mine-page__plan">{planLabel}</Text>
        </View>

        {subscription ? (
          <View className="mine-page__sub">
            <Text className="mine-page__sub-row">订阅状态：{subscription.status}</Text>
            {subscription.expiresAt ? (
              <Text className="mine-page__sub-row">
                到期时间：{formatDate(subscription.expiresAt)}
              </Text>
            ) : null}
          </View>
        ) : null}

        <View className="mine-page__menu">
          <View className="mine-page__menu-item" onClick={toReminders}>
            <Text className="mine-page__menu-text">提醒事项</Text>
            <Text className="mine-page__menu-arrow">›</Text>
          </View>
          <View className="mine-page__menu-item" onClick={toNotices}>
            <Text className="mine-page__menu-text">通知中心</Text>
            <Text className="mine-page__menu-arrow">›</Text>
          </View>
        </View>

        <Button className="mine-page__logout" onClick={handleLogout}>
          退出登录
        </Button>
      </View>
    </PageContainer>
  );
}
