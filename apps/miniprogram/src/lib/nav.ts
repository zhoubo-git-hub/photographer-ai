import Taro from '@tarojs/taro';

/**
 * 路由常量与导航封装。
 * 页面**禁止**手写路径字符串，一律用 ROUTES + 本文件的封装函数，避免路径漂移。
 */
export const ROUTES = {
  login: '/pages/login/index',
  orders: '/pages/orders/index',
  orderDetail: '/pages/order-detail/index',
  schedule: '/pages/schedule/index',
  quote: '/pages/quote/index',
  customers: '/pages/customers/index',
  customerDetail: '/pages/customer-detail/index',
  mine: '/pages/mine/index',
  reminders: '/pages/reminders/index',
  notices: '/pages/notices/index',
} as const;

/** tabBar 一级页面（跳这些页面必须用 switchTab，navigateTo 会失败）。 */
export const TAB_ROUTES: readonly string[] = [
  ROUTES.orders,
  ROUTES.schedule,
  ROUTES.quote,
  ROUTES.customers,
  ROUTES.mine,
];

/** 当前页面路径（带前导斜杠），取不到时返回空串。 */
export function currentRoute(): string {
  const pages = Taro.getCurrentPages();
  const current = pages[pages.length - 1];
  if (!current || !current.route) {
    return '';
  }
  return current.route.startsWith('/') ? current.route : `/${current.route}`;
}

/** 通用跳转：自动区分 tab 页与普通页。 */
export function navigateTo(url: string): void {
  const path = url.split('?')[0];
  if (TAB_ROUTES.includes(path)) {
    void Taro.switchTab({ url: path });
    return;
  }
  void Taro.navigateTo({ url });
}

/** 重启到指定页面（清空页面栈）。 */
export function reLaunchTo(url: string): void {
  void Taro.reLaunch({ url });
}

/** 跳登录页（清栈）。已在登录页时不重复跳转，避免 401 时的重入循环。 */
export function toLogin(): void {
  if (currentRoute() === ROUTES.login) {
    return;
  }
  reLaunchTo(ROUTES.login);
}

/** 登录成功后进入主页（订单 tab）。 */
export function toHome(): void {
  void Taro.switchTab({ url: ROUTES.orders });
}

/** 订单详情。 */
export function toOrderDetail(id: number): void {
  navigateTo(`${ROUTES.orderDetail}?id=${id}`);
}

/** 客户详情。 */
export function toCustomerDetail(id: number): void {
  navigateTo(`${ROUTES.customerDetail}?id=${id}`);
}

/** 提醒列表（「我的」下二级页）。 */
export function toReminders(): void {
  navigateTo(ROUTES.reminders);
}

/** 通知中心（「我的」下二级页）。 */
export function toNotices(): void {
  navigateTo(ROUTES.notices);
}

/** 返回上一页；无上一页时回到主页。 */
export function back(): void {
  const pages = Taro.getCurrentPages();
  if (pages.length > 1) {
    void Taro.navigateBack();
    return;
  }
  toHome();
}

/** 从页面路由参数中安全解析数字 id。 */
export function parseIdParam(raw: string | undefined): number {
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}
