import { defineConfig } from 'vitest/config';

// 仅用于单元测试（纯逻辑），不加载 React 渲染环境。
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/__tests__/**/*.test.ts'],
  },
});
