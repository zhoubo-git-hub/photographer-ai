/**
 * QA 静态回归校验脚本（T7 微信小程序）
 *
 * 目的：不依赖真机/微信开发者工具，纯静态地验证三件事：
 *  1) shared 的 TS 源码确实被编译进 weapp 产物（验证 config/index.js 的
 *     prebundle=false + mini.compile.include 是否真的生效，而不是"构建没报错"）；
 *  2) 关键三方依赖（react / @tanstack/react-query / zustand / axios）在
 *     miniprogram 侧与 shared 侧是否解析到**同一物理副本**——webpack
 *     resolve.symlinks 默认 true，会把 pnpm 的软链/junction 解析成真实路径，
 *     一旦真实路径不同就会打进两份实例，导致 React dispatcher / QueryClient
 *     Context 对不上，运行期必崩，而 tsc 与 build 都发现不了；
 *  3) app.json 声明的页面与 dist 产物四件套是否一一对应。
 *
 * 用法：node qa-verify.cjs   （在 apps/miniprogram 下执行）
 * 退出码：0 = 全部通过；1 = 存在阻断问题。
 */
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const REPO = path.resolve(ROOT, '../..');

let failed = 0;
function ok(msg) {
  console.log('  PASS  ' + msg);
}
function bad(msg) {
  console.log('  FAIL  ' + msg);
  failed += 1;
}

/** 把中文串同时按原文和 \uXXXX 转义两种形态去产物里找（webpack 可能转义输出）。 */
function bundleContains(files, literal) {
  const escaped = Array.from(literal)
    .map((c) => '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0'))
    .join('');
  return files.filter((f) => {
    try {
      const text = fs.readFileSync(f, 'utf8');
      return text.includes(literal) || text.includes(escaped);
    } catch {
      return false;
    }
  });
}

/** 模拟 webpack 的解析口径：先按 node 规则找到包，再 realpath 抹平软链。 */
function resolveReal(fromDir, spec) {
  try {
    const pkgJson = require.resolve(spec + '/package.json', { paths: [fromDir] });
    return fs.realpathSync(path.dirname(pkgJson));
  } catch {
    return null;
  }
}

// ── 检查 1：app.json 声明页面 vs dist 产物四件套 ───────────────────────────
console.log('\n[1] dist 产物完整性（app.json 声明页 ↔ 四件套）');
const appJsonPath = path.join(ROOT, 'dist/app.json');
if (!fs.existsSync(appJsonPath)) {
  bad('dist/app.json 不存在，请先执行 pnpm build:weapp');
} else {
  const appJson = JSON.parse(fs.readFileSync(appJsonPath, 'utf8'));
  const missing = [];
  appJson.pages.forEach((p) => {
    ['js', 'json', 'wxml', 'wxss'].forEach((ext) => {
      const f = path.join(ROOT, 'dist', p + '.' + ext);
      if (!fs.existsSync(f)) missing.push(p + '.' + ext);
    });
  });
  if (missing.length) bad('缺失产物：' + missing.join(', '));
  else ok('声明 ' + appJson.pages.length + ' 页，四件套齐全');
}

// ── 检查 2：shared 源码是否真的进包 ────────────────────────────────────────
console.log('\n[2] shared TS 源码是否被编译进 weapp 产物（R3 生效性）');
const bundleFiles = ['dist/app.js', 'dist/vendors.js', 'dist/common.js']
  .map((f) => path.join(ROOT, f))
  .filter((f) => fs.existsSync(f));
const pagesDir = path.join(ROOT, 'dist/pages');
if (fs.existsSync(pagesDir)) {
  fs.readdirSync(pagesDir).forEach((p) => {
    const f = path.join(pagesDir, p, 'index.js');
    if (fs.existsSync(f)) bundleFiles.push(f);
  });
}
// 这些字面量只存在于 packages/shared/src 中，端侧代码没有复制过一份。
const sharedProbes = {
  'domain/constants.ts messageOfErrorCode(401)': '未登录或登录已过期',
  'types/models.ts STATUS_LABELS（orderStatusLabel 的数据源）': '咨询中',
  'api/schedule.ts 端点': '/schedule/month',
  'api/auth.ts 微信登录端点': '/auth/wechat',
};
Object.entries(sharedProbes).forEach(([name, literal]) => {
  const hits = bundleContains(bundleFiles, literal);
  if (hits.length) ok(name + ' → 命中 ' + hits.length + ' 个产物文件');
  else bad(name + ' → 未在任何产物中找到（shared 可能没被打进包）');
});

// ── 检查 3：依赖单例性（最容易被 tsc/build 漏掉的运行期杀手）──────────────
console.log('\n[3] 依赖单例性：miniprogram 侧 与 shared 侧 是否同一物理副本');
const fromMini = path.join(ROOT, 'src/lib');
const fromShared = path.join(REPO, 'packages/shared/src/hooks');
const fromRenderer = resolveReal(path.join(ROOT, 'src'), '@tarojs/react');
['react', '@tanstack/react-query', '@tanstack/query-core', 'zustand', 'axios'].forEach((spec) => {
  const a = resolveReal(fromMini, spec);
  const b = resolveReal(fromShared, spec);
  if (!a || !b) {
    bad(spec + ' → 解析失败 (mini=' + a + ', shared=' + b + ')');
    return;
  }
  if (a === b) {
    ok(spec + ' → 单副本 ' + path.relative(REPO, a));
  } else {
    bad(
      spec +
        ' → 双副本！mini=' +
        path.relative(REPO, a) +
        ' | shared=' +
        path.relative(REPO, b)
    );
  }
});
if (fromRenderer) {
  const rendererReact = resolveReal(fromRenderer, 'react');
  const sharedReact = resolveReal(fromShared, 'react');
  if (rendererReact && sharedReact && rendererReact !== sharedReact) {
    bad(
      '@tarojs/react(渲染器) 与 shared hooks 的 react 不同副本 → ' +
        'shared 内的 React Hook 取不到 dispatcher，页面渲染必崩'
    );
  } else if (rendererReact) {
    ok('@tarojs/react 渲染器与 shared 共用同一 react 副本');
  }
}

// ── 检查 4：产物里实际打进了几份 react-query ──────────────────────────────
console.log('\n[4] 产物内三方库实例计数（每副本各 1 次特征串）');
const vendors = path.join(ROOT, 'dist/vendors.js');
if (fs.existsSync(vendors)) {
  const text = fs.readFileSync(vendors, 'utf8');
  const rq = (text.match(/No QueryClient set/g) || []).length;
  if (rq <= 1) ok('@tanstack/react-query 实例数 = ' + rq);
  else bad('@tanstack/react-query 被打进 ' + rq + ' 份实例（QueryClientProvider 的 Context 将失配）');
} else {
  bad('dist/vendors.js 不存在');
}

// ── 检查 5：AppID 占位 ────────────────────────────────────────────────────
console.log('\n[5] project.config.json AppID 占位');
const proj = JSON.parse(fs.readFileSync(path.join(ROOT, 'project.config.json'), 'utf8'));
if (proj.appid === 'touristappid') ok('appid = touristappid（占位符，无真实 AppID 泄露）');
else bad('appid = ' + proj.appid + '（疑似硬编码真实 AppID）');

console.log('\n────────────────────────────');
console.log(failed === 0 ? 'RESULT: ALL PASS' : 'RESULT: ' + failed + ' 项失败');
process.exit(failed === 0 ? 0 : 1);
