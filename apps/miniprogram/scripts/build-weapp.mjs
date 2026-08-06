/**
 * weapp 构建包装脚本（解决 Taro build 产物落盘后进程不退出、导致 CI 挂到超时的问题）。
 *
 * 已知坑（QA 回归 f8d0b74 打回后修掉的 3 个缺陷）：
 *  - 缺陷 A（孤儿进程）：killTree 必须是同步的，否则 process.exit 会抢在 taskkill 之前
 *    干掉父进程，detached 的子进程树残留吃 CPU。这里用 spawnSync 确保先杀再退。
 *  - 缺陷 B（超时太短）：原生 taro build 墙钟可达 ~20 分钟（boot/扫描/落盘），默认超时
 *    提到 30 分钟，可用 BUILD_TIMEOUT_MS 覆盖。
 *  - 缺陷 C（假通过）：只按"文件存在"判定会误把陈旧/失败的产物判成功。这里先 rm -rf
 *    dist 再启动，且子进程非 0 退出时无论产物是否齐全都必须失败（子进程退出码优先）。
 *  - 竞态收口（Known Issue）：产物先齐全、子进程随后才失败（如 post-build 钩子挂了）
 *    的窄窗口。产物齐全后引入 GRACE_MS 宽限，期间子进程非 0 退出即判失败，否则判成功
 *    （覆盖"taro 编译完不退出"）；可用 BUILD_GRACE_MS 覆盖。
 *
 * 策略：
 *  - 启动前清空 dist（从根上杜绝陈旧产物误判）；
 *  - 子进程跑真实的 `taro build --type weapp`；
 *  - 轮询 dist，产物（10 页四件套 + app.js）就绪即判定成功，杀掉 taro 进程树并退出 0
 *    （覆盖"编译完但不退出"这个老大难）；
 *  - 子进程非 0 退出立即失败；超时（默认 30 分钟）也失败；
 *  - TARO_BIN 环境变量可覆盖 taro 入口，用于本地故障注入测试。
 */
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, rmSync } from 'node:fs';
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

// 缺陷 C 修复：先清 dist，杜绝陈旧/失败产物被误判成功。
rmSync(DIST, { recursive: true, force: true });

const taroBin = process.env.TARO_BIN
  ? process.env.TARO_BIN
  : findTaroBin();

const child = spawn(process.execPath, [taroBin, 'build', '--type', 'weapp'], {
  stdio: ['ignore', 'inherit', 'inherit'],
  detached: true,
});

let resolved = false;
let childExited = false;
let childCode = null;

/** 缺陷 A 修复：同步杀掉进程树（spawnSync 会阻塞到 taskkill 真正执行完毕）。 */
function killTree() {
  if (!child.pid) return;
  try {
    if (process.platform === 'win32') {
      spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F'], {
        stdio: 'ignore',
      });
    } else {
      spawnSync('kill', ['-9', String(-child.pid)], { stdio: 'ignore' });
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

// 缺陷 B 修复：默认 30 分钟（墙钟可达 ~20 分钟），可用 BUILD_TIMEOUT_MS 覆盖。
const TIMEOUT_MS = Number(process.env.BUILD_TIMEOUT_MS || 1800000);
// 竞态宽限（Known Issue 收口）：产物先齐全、子进程随后才失败的窄窗口。
// 产物齐全后给子进程一个窗口确认最终退出码；窗口内非 0 退出判失败，否则判成功
// （覆盖"taro 编译完不退出"）。可用 BUILD_GRACE_MS 覆盖。
const GRACE_MS = Number(process.env.BUILD_GRACE_MS || 4000);
const start = Date.now();
let graceDeadline = null;

/** 产物首次齐全时记录宽限截止时间（只记一次）。 */
function markArtifactsReady() {
  if (graceDeadline === null) graceDeadline = Date.now() + GRACE_MS;
}

const poll = setInterval(() => {
  if (resolved) return;
  // 子进程已失败：无论产物如何都必须失败（子进程退出码优先）。
  if (childExited && childCode !== 0) {
    clearInterval(poll);
    finish(childCode ?? 1);
    return;
  }
  if (artifactsReady()) {
    markArtifactsReady();
    // 子进程已确认成功退出且产物齐全 → 直接成功（保持快速路径）。
    if (childExited && childCode === 0) {
      clearInterval(poll);
      finish(0);
      return;
    }
    // 产物齐全但子进程尚未退出："编译完不退出"或"post-build 钩子可能失败"。
    // 宽限期内非 0 退出会在 on('exit') 被判失败；宽限到则判成功。
    if (Date.now() >= graceDeadline) {
      clearInterval(poll);
      finish(0);
      return;
    }
  }
  if (Date.now() - start > TIMEOUT_MS) {
    clearInterval(poll);
    finish(1);
  }
}, 2000);

child.on('exit', (code) => {
  if (resolved) return;
  childExited = true;
  childCode = typeof code === 'number' ? code : 1;
  // 构建失败：立即失败（即便产物齐全也不算成功）——竞态窗口的关键防线。
  if (childCode !== 0) {
    clearInterval(poll);
    finish(childCode);
    return;
  }
  // 构建成功退出：产物已就绪则成功；否则交回 poll（半截产物最终超时判失败）。
  if (artifactsReady()) {
    markArtifactsReady();
    clearInterval(poll);
    finish(0);
  }
});
