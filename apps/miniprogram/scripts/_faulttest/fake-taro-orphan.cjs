/**
 * 故障注入：模拟 taro build 在产物落盘后"不退出"（老问题），
 * 并派生一个永远循环的孙子进程（孤儿）以验证 killTree 能否同步杀掉整棵树。
 */
const { spawn } = require('child_process');
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

// 派生一个"孤儿"孙子进程，永远循环（detached + unref，模拟 taro 卡住后的残留）。
const grandchild = spawn(process.execPath, ['-e', 'setInterval(()=>{}, 1000)'], {
  detached: true,
  stdio: 'ignore',
});
grandchild.unref();
fs.writeFileSync(path.join(__dirname, 'grandchild.pid'), String(grandchild.pid));

// 父（taro）自身也永远不退出，模拟"编译完但不退出"。
setInterval(() => {}, 1000);
