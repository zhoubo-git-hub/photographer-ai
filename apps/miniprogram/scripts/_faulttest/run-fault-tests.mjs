/**
 * 包装脚本缺陷 A/B/C 的故障注入回归。
 * 通过 TARO_BIN 注入不同的"假 taro"行为，验证 build-weapp.mjs 的修复：
 *   - 缺陷 A（孤儿进程）：产物就绪后 killTree 同步杀掉整棵进程树。
 *   - 缺陷 B（超时太短）：超时兜底失败退出。
 *   - 缺陷 C（假通过）：子进程非 0 退出必须失败；运行前先清 dist 杜绝陈旧产物误判。
 */
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, rmSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { dirname } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const ROOT = join(__dirname, '..', '..'); // apps/miniprogram
const FAULT = __dirname;
const WRAPPER = join(ROOT, 'scripts', 'build-weapp.mjs');
const DIST = join(ROOT, 'dist');
const PAGES = [
  'login', 'orders', 'order-detail', 'schedule', 'quote',
  'customers', 'customer-detail', 'reminders', 'notices', 'mine',
];

let pass = 0;
let fail = 0;

function seedStaleDist() {
  rmSync(DIST, { recursive: true, force: true });
  mkdirSync(join(DIST, 'pages', 'login'), { recursive: true });
  writeFileSync(join(DIST, 'app.js'), 'stale');
  for (const ext of ['js', 'json', 'wxml', 'wxss']) {
    writeFileSync(join(DIST, 'pages', 'login', `index.${ext}`), 'stale');
  }
}

function runCase({ name, taroBin, env = {}, setup, expectExit, postCheck }) {
  if (setup) setup();
  const res = spawnSync(process.execPath, [WRAPPER], {
    cwd: ROOT,
    env: { ...process.env, TARO_BIN: join(FAULT, taroBin), ...env },
    encoding: 'utf8',
    timeout: 120000,
  });
  const exitOk = res.status === expectExit;
  let postOk = true;
  if (postCheck) {
    try {
      postOk = postCheck();
    } catch {
      postOk = false;
    }
  }
  const ok = exitOk && postOk;
  if (ok) pass += 1;
  else fail += 1;
  console.log(
    `[${ok ? 'PASS' : 'FAIL'}] ${name} | exit=${res.status} (expect ${expectExit}) | post=${postOk}`,
  );
  if (!exitOk && res.error) console.log('   err:', res.error.message);
  return ok;
}

// 缺陷 A：产物就绪后，整棵进程树（含孤儿孙子）必须被同步杀掉。
runCase({
  name: 'A-孤儿进程同步杀掉',
  taroBin: 'fake-taro-orphan.cjs',
  expectExit: 0,
  postCheck() {
    const pidFile = join(FAULT, 'grandchild.pid');
    if (!existsSync(pidFile)) return false;
    const pid = readFileSync(pidFile, 'utf8').trim();
    // 给 taskkill /T 一点传播时间。
    const r = spawnSync('tasklist', ['/FI', `PID eq ${pid}`], { encoding: 'utf8' });
    const stillAlive = r.stdout.includes(pid);
    return !stillAlive;
  },
});

// 缺陷 C（a）：产物齐全但子进程非 0 退出 → 必须失败。
runCase({
  name: 'C-产物齐全但退出码非0',
  taroBin: 'fake-taro-stale.cjs',
  expectExit: 1,
});

// 竞态收口：产物先齐全、子进程 4s 后才失败（post-build 钩子挂了）。
// 宽限期内非 0 退出必须判失败，不能因为产物齐全就提前 exit 0。
runCase({
  name: 'race-产物先齐子进程后失败',
  taroBin: 'fake-taro-race.cjs',
  expectExit: 1,
});

// 缺陷 C（b）：子进程崩溃且不产出，但运行前预置了陈旧 dist → 先清 dist，必须失败（不假通过）。
runCase({
  name: 'C-陈旧dist不假通过',
  taroBin: 'fake-taro-crash.cjs',
  setup: seedStaleDist,
  expectExit: 1,
});

// 缺陷 B：超时兜底 → 失败退出。
runCase({
  name: 'B-超时兜底失败',
  taroBin: 'fake-taro-hang.cjs',
  env: { BUILD_TIMEOUT_MS: '5000' },
  expectExit: 1,
});

console.log(`\n结果：${pass} PASS / ${fail} FAIL`);
writeFileSync(join(__dirname, 'last-result.txt'), `${pass} PASS / ${fail} FAIL\n`, 'utf8');
process.exit(fail === 0 ? 0 : 1);
