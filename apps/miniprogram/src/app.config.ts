/**
 * 小程序全局配置：页面注册 + tabBar + 窗口主题。
 *
 * tabBar 微信上限 5 个（架构 §1.1 D7）：订单 / 档期 / 报价 / 客户 / 我的；
 * 提醒（reminders）与通知中心（notices）作为「我的」下的二级页面。
 * 颜色对齐 shared THEME_COLORS：primary=#2D6CDF、textSecondary=#6B7280、background=#f7f8fa。
 */
export default {
  pages: [
    // 首个页面为启动页（tab 首页）；未登录时由 PageContainer 守卫 reLaunch 到登录页。
    'pages/orders/index',
    'pages/schedule/index',
    'pages/quote/index',
    'pages/customers/index',
    'pages/mine/index',
    // 非 tab 页面
    'pages/login/index',
    'pages/order-detail/index',
    'pages/customer-detail/index',
    'pages/reminders/index',
    'pages/notices/index',
  ],
  window: {
    backgroundTextStyle: 'light',
    navigationBarBackgroundColor: '#ffffff',
    navigationBarTitleText: '摄影师助手',
    navigationBarTextStyle: 'black',
    backgroundColor: '#f7f8fa',
    enablePullDownRefresh: false,
  },
  tabBar: {
    color: '#6B7280',
    selectedColor: '#2D6CDF',
    backgroundColor: '#ffffff',
    borderStyle: 'white',
    list: [
      { pagePath: 'pages/orders/index', text: '订单' },
      { pagePath: 'pages/schedule/index', text: '档期' },
      { pagePath: 'pages/quote/index', text: '报价' },
      { pagePath: 'pages/customers/index', text: '客户' },
      { pagePath: 'pages/mine/index', text: '我的' },
    ],
  },
};
