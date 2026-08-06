/**
 * weapp 构建包装脚本（解决 Taro build 产物落盘后进程不退出、导致 CI 挂到超时的问题）。
 *
 * 现象：taro build --type weapp 在产物完整落盘后仍常驻，webpack 的 worker / 文件句柄
 * 未释放，进程持续吃 CPU。CI 中这一步会一直挂到 job 超时。
 *
 * 策略（与 QA 约定的「产物存在性判定成功 + CI 超时」一致）：
 *   - 子进程跑真实的 `taro build --type weapp`；
 *   - 轮询 dist/，一旦 10 个页面的四件套（js/json/wxml/wxss）+ app.js 全部就绪，
 *     即判定构建成功，杀掉 taro 进程树并退出 0；
 *   - 若超过 BUILD_TIMEOUT_MS（默认 8 分钟）仍未就绪，退出 1（真失败，不被误判成功）；
 *   - 若 taro 自己正常退出，则沿用其退出码。
 *
 * 用法：在 apps/miniprogram 下 `node scripts/build-weapp.mjs`（package.json 的
 * build:weapp 已指向本脚本）。
 */
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = process.cwd();
const DIST = join(ROOT, 'dist');
const PAGES = [
  'login',
  'orders',
  'order-detail',
  'schedule',
  'quote',
  'customers',
  'customer-detail',
  'reminders',
  'notices',
  'mine',
];

/** dist 产物是否四件套 + app.js 齐全（与 qa-verify.cjs [1] 同一判定口径）。 */
function artifactsReady() {
  if (!existsSync(join(DIST, 'app.js'))) return false;
  return PAGES.every((page) => {
    const base = join(DIST, 'pages', page, 'index');
    return ['js', 'json', 'wxml', 'wxss'].every((ext) =>
      existsSync(`${base}.${ext}`),
    );
  });
}

/** 沿目录向上查找 hoisted 的 Taro CLI bin（pnpm hoisted 下在仓库根 node_modules）。 */
function findTaroBin() {
  let dir = ROOT;
  for (let i = 0; i < 6; i += 1) {
    const candidate = join(
      dir,
      'node_modules',
      '@tarojs',
      'cli',
      'bin',
      'taro',
    );
    if (existsSync(candidate)) return candidate;
    const parent = join(dir, '..');
    if (parent === dir) break;
    dir = parent;
  }
  return join(ROOT, 'node_modules', '@tarojs', 'cli', 'bin', 'taro');
}

const taroBin = findTaroBin();
const child = spawn(process.execPath, [taroBin, 'build', '--type', 'weapp'], {
  stdio: ['ignore', 'inherit', 'inherit'],
  detached: true,
});

let resolved = false;

/** 跨平台杀掉进程树，避免 taro 的 worker 残留导致 CI 作业挂起。 */
function killTree() {
  try {
    if (process.platform === 'win32') {
      spawn('taskkill', ['/pid', String(child.pid), '/T', '/F'], {
        stdio: 'ignore',
      });
    } else if (child.pid) {
      process.kill(-child.pid, 'SIGKILL');
    }
  } catch {
    /* 尽力而为，忽略异常 */
  }
}

function finish(code) {
  if (resolved) return;
  resolved = true;
  killTree();
  process.exit(code);
}

const TIMEOUT_MS = Number(process.env.BUILD_TIMEOUT_MS || 480000);
const start = Date.now();

const poll = setInterval(() => {
  if (resolved) return;
  if (artifactsReady()) {
    clearInterval(poll);
    finish(0);
  } else if (Date.now() - start > TIMEOUT_MS) {
    clearInterval(poll);
    finish(1);
  }
}, 2000);

child.on('exit', (code) => {
  if (resolved) return;
  resolved = true;
  clearInterval(poll);
  process.exit(typeof code === 'number' ? code : 0);
});
