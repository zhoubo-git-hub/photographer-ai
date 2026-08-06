/**
 * 故障注入：taro 伪造出"看起来成功"的完整产物，但 1.5s 后以非 0 退出。
 * 验证"子进程退出码优先"——即便产物齐全也必须判失败（缺陷 C）。
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

setTimeout(() => process.exit(1), 1500);
