/**
 * 故障注入（竞态收口）：taro 立即写出"看起来成功"的完整产物，但 4s 后（模拟
 * post-build 钩子失败）以非 0 退出。验证"产物先齐全、子进程后失败"这个窄窗口：
 * 宽限期内子进程非 0 退出必须判失败，不能因为产物齐全就提前 exit 0。
 */
const fs = require('fs');
const path = require('path');

const DIST = path.join(process.cwd(), 'dist');
const PAGES = [
  'login', 'orders', 'order-detail', 'schedule', 'quote',
  'customers', 'customer-detail', 'reminders', 'notices', 'mine',
];

fs.mkdirSync(DIST, { recursive: true });
fs.writeFileSync(path.join(DIST, 'app.js'), 'ok');
for (const p of PAGES) {
  const d = path.join(DIST, 'pages', p);
  fs.mkdirSync(d, { recursive: true });
  for (const ext of ['js', 'json', 'wxml', 'wxss']) {
    fs.writeFileSync(path.join(d, `index.${ext}`), 'ok');
  }
}

// 产物齐全后，post-build 钩子随后失败。
setTimeout(() => process.exit(1), 4000);
