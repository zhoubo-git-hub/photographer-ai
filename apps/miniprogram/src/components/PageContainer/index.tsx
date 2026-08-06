import type { PropsWithChildren, ReactNode } from 'react';
import { useEffect } from 'react';
import { View } from '@tarojs/components';
import { useAuth } from '@photogai/shared/hooks';
import { toLogin } from '../../lib/nav';
import './index.scss';

interface PageContainerProps {
  /** 是否需要登录态守卫，未登录时 reLaunch 到登录页。默认 true。 */
  requireAuth?: boolean;
  /** 是否加内边距，纯列表页可关掉自行控制。默认 true。 */
  padded?: boolean;
  /** 固定在内容区上方的区域（如筛选栏、搜索框）。 */
  header?: ReactNode;
  /** 固定在页面底部的区域（如说明条）。 */
  footer?: ReactNode;
  /** 附加类名。 */
  className?: string;
}

/**
 * 页面外壳：统一背景色、内边距与登录态守卫。
 * 所有页面都应包一层，避免各页重复实现守卫与布局。
 */
export default function PageContainer({
  requireAuth = true,
  padded = true,
  header,
  footer,
  className = '',
  children,
}: PropsWithChildren<PageContainerProps>) {
  const { isAuthenticated } = useAuth();
  const blocked = requireAuth && !isAuthenticated;

  useEffect(() => {
    if (blocked) {
      toLogin();
    }
  }, [blocked]);

  return (
    <View className={`page-container ${className}`}>
      {header ? <View className="page-container__header">{header}</View> : null}
      <View
        className={`page-container__body ${padded ? 'page-container__body--padded' : ''}`}
      >
        {blocked ? null : children}
      </View>
      {footer ? <View className="page-container__footer">{footer}</View> : null}
    </View>
  );
}
