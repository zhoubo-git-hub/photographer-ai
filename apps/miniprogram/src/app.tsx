import type { PropsWithChildren } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { setStorageAdapter } from '@photogai/shared/store';
import { taroStorageAdapter } from './lib/storage';
import { setupHttp } from './lib/http';
import { bindUiStore } from './lib/toast';
import { queryClient } from './lib/queryClient';
import './app.scss';

// ─────────────────────────────────────────────────────────────
// 启动初始化：顺序不可颠倒（架构 §2.2 / 时序图 ①）
//
// ① setStorageAdapter：注入 Taro 存储。shared authStore 监听到适配器切换后
//    会自动 persist.rehydrate()，把上次登录的 token 恢复到内存。
// ② setupHttp：注入 Taro 适配器 + baseURL + 401/402 导航回调。
//    它的 getToken 读的是 authStore，因此**必须**排在 ① 之后，
//    否则冷启动首个请求会因 token 尚未水合而漏带 Authorization，直接 401。
// ③ bindUiStore：把 shared 写入 uiStore 的 toast / 升级弹窗桥接到 Taro 原生 UI。
//
// 放在模块顶层作用域执行，保证早于任何页面渲染与请求。
// ─────────────────────────────────────────────────────────────
setStorageAdapter(taroStorageAdapter);
setupHttp();
bindUiStore();

/** 应用根组件：仅负责挂载 QueryClientProvider，业务逻辑全在 shared。 */
function App({ children }: PropsWithChildren<unknown>) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

export default App;
