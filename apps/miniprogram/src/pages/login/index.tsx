import { useState } from 'react';
import { Button, Text, View } from '@tarojs/components';
import { loginWithWechat } from '@/lib/wechat';
import { toHome } from '@/lib/nav';
import { showApiError } from '@/lib/toast';
import './index.scss';

/**
 * 微信一键登录页。
 *
 * 架构 §6：Taro.login() 取 code → authApi.wechatLogin 命中/新建 studio → setAuth 落盘。
 * 成功后跳订单 tab（toHome）。任何异常（含 ApiError 401「凭据错误」、
 * 网络错误）统一交给 showApiError 提示，不在此处分支判断。
 *
 * 本页是登录页本身，**不套 PageContainer 的登录守卫**（否则会自跳转成环）。
 */
export default function LoginPage() {
  const [loading, setLoading] = useState(false);

  const handleLogin = async (): Promise<void> => {
    if (loading) {
      return;
    }
    setLoading(true);
    try {
      await loginWithWechat();
      toHome();
    } catch (err) {
      showApiError(err, '微信登录失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View className="login-page">
      <View className="login-page__brand">
        <Text className="login-page__logo">摄影师助手</Text>
        <Text className="login-page__slogan">AI 接单 · 报价 · 档期管理</Text>
      </View>

      <Button className="login-page__btn" loading={loading} onClick={handleLogin}>
        微信一键登录
      </Button>

      <Text className="login-page__tip">登录即同意《用户协议》与《隐私政策》</Text>
    </View>
  );
}
