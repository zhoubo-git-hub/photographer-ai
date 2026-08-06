/**
 * QA 临时探针（非测试用例）：判定 shared 的领域常量/逻辑是否真的被编译进小程序产物。
 * 用于复核架构裁定 R3（关 prebundle + mini.compile.include 命中 shared 源码）是否端到端生效。
 * 运行：node apps/miniprogram/tests/_probe-bundle.cjs
 */
const fs = require('node:fs');
const path = require('node:path');

const DIST = path.resolve(__dirname, '../dist');

/** 把字符串里的非 ASCII 字符转成 \uXXXX 形式，用于匹配压缩器转义后的产物。 */
function toUnicodeEscaped(s) {
  return [...s]
    .map((c) => {
      const code = c.charCodeAt(0);
      return code > 127 ? '\\u' + code.toString(16).padStart(4, '0') : c;
    })
    .join('');
}

function collectFiles(dir) {
  const out = [];
  (function walk(d) {
    for (const name of fs.readdirSync(d)) {
      const p = path.join(d, name);
      if (fs.statSync(p).isDirectory()) walk(p);
      else if (/\.(js|wxml|json|wxss)$/.test(name)) out.push(p);
    }
  })(dir);
  return out;
}

const files = collectFiles(DIST);
console.log(`扫描产物文件数: ${files.length}\n`);

// 注意：这里的字符串必须与 packages/shared/src/types/models.ts 的真实取值一致，
// 否则会误报 MISS（QA 首轮就踩过：把 STATUS_LABELS 猜成了「待沟通/已交付」）。
const targets = [
  ['shared types/STATUS_LABELS', '咨询中'],
  ['shared types/STATUS_LABELS', '复购'],
  ['shared types/REMINDER_LABELS', '定金待付'],
  ['shared types/REMINDER_LABELS', '交付后求好评'],
  ['shared domain/constants 错误码文案', '登录已过期'],
  ['shared domain/order.formatAmount 货币', 'zh-CN'],
  ['app 侧 http.ts 402 文案', '请前往 Web 端续费后继续使用该功能。'],
  ['shared store/authStore persist key', 'photogai-auth'],
  ['shared http/HttpClient Bearer 注入', 'Bearer '],
  ['app 侧 defineConstants 注入的 baseURL', 'api.example.com'],
];

for (const [label, needle] of targets) {
  const escaped = toUnicodeEscaped(needle);
  const hits = files.filter((f) => {
    const c = fs.readFileSync(f, 'utf8');
    return c.includes(needle) || (escaped !== needle && c.includes(escaped));
  });
  const rel = hits.map((f) => path.relative(DIST, f)).slice(0, 3);
  console.log(
    `${hits.length ? 'FOUND' : 'MISS '}  ${label.padEnd(38)} "${needle}"  ->  ${rel.join(', ') || '(无)'}`,
  );
}
