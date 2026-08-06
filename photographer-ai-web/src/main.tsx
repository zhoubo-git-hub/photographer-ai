import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles/index.css';
import { initShared } from './lib/initShared';

// 必须在任何组件渲染前执行，确保 shared 包的 axios 拦截器和 store 已就绪
initShared();

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
