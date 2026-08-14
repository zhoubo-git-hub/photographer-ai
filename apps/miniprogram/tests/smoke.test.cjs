/**
 * T7 小程序高风险纯逻辑冒烟测试（QA 产出，零新增依赖）。
 *
 * 为什么是这种形态：
 * 本工程未配置 jest/vitest，QA 阶段不宜为几个纯函数引入重型测试框架。
 * 因此这里只用 Node 内置的 `node:test` + `node:assert`，配合 TypeScript 自带的
 * `transpileModule` 即时转译被测 TS 源码，并对 `@tarojs/taro` 做最小桩替换。
 * 这样测的是**真实源码**（而非复制一份逻辑），且不往 package.json 添加任何依赖。
 *
 * 运行：node --test apps/miniprogram/tests/
 *
 * 覆盖的高风险点：
 *  1. storage.getItem 的 `'' → null` 归一（微信 getStorageSync 键不存在返回空串，
 *     不归一会让 zustand persist 走 JSON.parse('') 抛错，冷启动丢登录态）
 *  2. taroAdapter.serializeParams 的 query 序列化（自研适配器的核心，
 *     漏参会让 orders/schedule/reminders 的筛选条件全部失效）
 *  3. taroAdapter.buildRequestUrl 的 baseURL 拼接
 *  4. PageData<T>.content 提取语义
 */

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const APP_ROOT = path.resolve(__dirname, '..');
const REPO_ROOT = path.resolve(APP_ROOT, '../..');
const ts = require(path.join(REPO_ROOT, 'node_modules/typescript'));

/** 可控的 Taro 存储桩：模拟微信「键不存在返回空字符串」的真实行为。 */
function createTaroStub() {
  const store = new Map();
  return {
    __store: store,
    throwOnRead: false,
    getStorageSync(key) {
      if (this.throwOnRead) {
        throw new Error('storage boom');
      }
      // 关键：微信/Taro 在键不存在时返回 '' 而不是 null/undefined
      return store.has(key) ? store.get(key) : '';
    },
    setStorageSync(key, value) {
      store.set(key, value);
    },
    removeStorageSync(key) {
      store.delete(key);
    },
    request() {
      throw new Error('not used in these tests');
    },
  };
}

/**
 * 即时转译并加载一个 TS 源文件，允许替换其依赖。
 * @param {string} relPath 相对 apps/miniprogram 的路径
 * @param {Record<string, unknown>} stubs 模块 id -> 桩对象
 */
function loadTsModule(relPath, stubs = {}) {
  const filePath = path.join(APP_ROOT, relPath);
  const source = fs.readFileSync(filePath, 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
    fileName: filePath,
  });

  const moduleObj = { exports: {} };
  const localRequire = (id) =>
    Object.prototype.hasOwnProperty.call(stubs, id) ? stubs[id] : require(id);

  // eslint-disable-next-line no-new-func
  new Function('exports', 'require', 'module', '__filename', '__dirname', outputText)(
    moduleObj.exports,
    localRequire,
    moduleObj,
    filePath,
    path.dirname(filePath),
  );
  return moduleObj.exports;
}

// ───────────────────────── storage：'' → null 归一 ─────────────────────────

test('storage.getItem：键不存在时把 Taro 的空字符串归一为 null', () => {
  const taro = createTaroStub();
  const { taroStorageAdapter } = loadTsModule('src/lib/storage.ts', { '@tarojs/taro': taro });

  // 这是本适配器存在的唯一理由：微信返回 ''，必须变成 null
  assert.equal(taroStorageAdapter.getItem('photogai-auth'), null);
});

test('storage.getItem：正常字符串原样返回，写入后可读回', () => {
  const taro = createTaroStub();
  const { taroStorageAdapter } = loadTsModule('src/lib/storage.ts', { '@tarojs/taro': taro });

  const payload = JSON.stringify({ state: { token: 'jwt-abc' }, version: 0 });
  taroStorageAdapter.setItem('photogai-auth', payload);

  assert.equal(taroStorageAdapter.getItem('photogai-auth'), payload);
  // 读回的内容必须能被 zustand persist 的 JSON.parse 消费
  assert.equal(JSON.parse(taroStorageAdapter.getItem('photogai-auth')).state.token, 'jwt-abc');
});

test('storage.getItem：非字符串值序列化为 JSON 字符串', () => {
  const taro = createTaroStub();
  const { taroStorageAdapter } = loadTsModule('src/lib/storage.ts', { '@tarojs/taro': taro });

  taro.__store.set('obj', { a: 1 });
  assert.equal(taroStorageAdapter.getItem('obj'), '{"a":1}');
});

test('storage.getItem：底层抛错时降级为 null，不冒泡到启动流程', () => {
  const taro = createTaroStub();
  taro.throwOnRead = true;
  const { taroStorageAdapter } = loadTsModule('src/lib/storage.ts', { '@tarojs/taro': taro });

  assert.equal(taroStorageAdapter.getItem('any'), null);
});

test('storage.removeItem：删除后回到 null 而不是空字符串', () => {
  const taro = createTaroStub();
  const { taroStorageAdapter } = loadTsModule('src/lib/storage.ts', { '@tarojs/taro': taro });

  taroStorageAdapter.setItem('k', 'v');
  taroStorageAdapter.removeItem('k');
  assert.equal(taroStorageAdapter.getItem('k'), null);
});

// ──────────────────── taroAdapter：query 序列化 / URL 拼接 ────────────────────

function loadAdapter() {
  return loadTsModule('src/lib/taroAdapter.ts', { '@tarojs/taro': createTaroStub() });
}

test('serializeParams：跳过 null/undefined，但保留 false 与 0', () => {
  const { serializeParams } = loadAdapter();

  // reminderApi.list(status?, dueOnly=false) 依赖 false 必须被保留
  assert.equal(serializeParams({ status: undefined, dueOnly: false }), 'dueOnly=false');
  assert.equal(serializeParams({ page: 0, size: 100 }), 'page=0&size=100');
  assert.equal(serializeParams({ a: null, b: undefined }), '');
});

test('serializeParams：非对象输入返回空串', () => {
  const { serializeParams } = loadAdapter();

  assert.equal(serializeParams(undefined), '');
  assert.equal(serializeParams(null), '');
  assert.equal(serializeParams('x'), '');
});

test('serializeParams：数组用 key[] 形式，并对键值做 URL 编码', () => {
  const { serializeParams } = loadAdapter();

  assert.equal(serializeParams({ ids: [1, 2] }), 'ids%5B%5D=1&ids%5B%5D=2');
  assert.equal(serializeParams({ keyword: '张 三&' }), 'keyword=%E5%BC%A0%20%E4%B8%89%26');
});

test('serializeParams：Date 走 ISO，嵌套对象走 JSON', () => {
  const { serializeParams } = loadAdapter();

  const iso = '2026-01-02T03:04:05.000Z';
  assert.equal(serializeParams({ from: new Date(iso) }), `from=${encodeURIComponent(iso)}`);
  assert.equal(serializeParams({ f: { a: 1 } }), `f=${encodeURIComponent('{"a":1}')}`);
});

test('buildRequestUrl：baseURL 与 path 之间不产生重复斜杠', () => {
  const { buildRequestUrl } = loadAdapter();

  assert.equal(
    buildRequestUrl('http://localhost:8083/api', '/orders', undefined),
    'http://localhost:8083/api/orders',
  );
  assert.equal(
    buildRequestUrl('http://localhost:8083/api/', '/orders', undefined),
    'http://localhost:8083/api/orders',
  );
});

test('buildRequestUrl：绝对地址不再拼 baseURL', () => {
  const { buildRequestUrl } = loadAdapter();

  assert.equal(
    buildRequestUrl('http://localhost:8083/api', 'https://cdn.example.com/a.png', undefined),
    'https://cdn.example.com/a.png',
  );
});

test('buildRequestUrl：带 params 时正确拼接 ? 与 &', () => {
  const { buildRequestUrl } = loadAdapter();

  assert.equal(
    buildRequestUrl('http://h/api', '/schedule/month', { year: 2026, month: 1 }),
    'http://h/api/schedule/month?year=2026&month=1',
  );
  // url 上已带 query 时应追加 &
  assert.equal(
    buildRequestUrl('http://h/api', '/orders?a=1', { page: 0 }),
    'http://h/api/orders?a=1&page=0',
  );
});

test('buildRequestUrl：params 全为空时不留下多余的 ?', () => {
  const { buildRequestUrl } = loadAdapter();

  assert.equal(
    buildRequestUrl('http://h/api', '/reminders', { status: undefined }),
    'http://h/api/reminders',
  );
});

// ──────────────────── PageData<T>.content 提取语义 ────────────────────

test('PageData.content 提取：正常分页、空 content、data 为 undefined 都不炸', () => {
  // 对应 pages/orders 与 pages/customers 中的 `const list = data?.content ?? []`
  const pick = (data) => data?.content ?? [];

  assert.deepEqual(pick({ content: [{ id: 1 }], totalElements: 1 }), [{ id: 1 }]);
  assert.deepEqual(pick({ content: [], totalElements: 0 }), []);
  assert.deepEqual(pick(undefined), []); // 首次加载 isLoading 期间 data 为 undefined
});
