import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 前端独立运行于 5173；后端默认 8080。跨域由后端 CORS 放开，故无需 proxy。
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
});
