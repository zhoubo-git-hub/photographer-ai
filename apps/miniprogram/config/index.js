const path = require('path');
const fs = require('fs');

/**
 * 解析 @photogai/shared 的**源码真实路径**。
 *
 * 背景（架构 §13 风险 R1，本次实现的头号坑）：
 * shared 的 package.json `main`/`types`/`exports` 全部指向未编译的 TS 源码 `./src/index.ts`，
 * 而 Taro 默认**不编译 node_modules**。叠加 pnpm `node-linker=hoisted` 下 workspace 包是软链，
 * 若不把 shared 源码目录显式纳入 `mini.compile.include`，webpack 会把 .ts 源码当普通 JS 处理并直接报错。
 *
 * 这里同时兼容两种布局，并用 realpathSync 抹平软链（webpack resolve.symlinks 默认为 true，
 * 命中的是软链解析后的真实路径，include 必须与之一致，否则不生效）：
 *  1. monorepo 内相对路径 <root>/packages/shared/src
 *  2. 经 node_modules 软链的 apps/miniprogram/node_modules/@photogai/shared/src
 */
function resolveSharedSrc() {
  const candidates = [
    path.resolve(__dirname, '../../../packages/shared/src'),
    path.resolve(__dirname, '../node_modules/@photogai/shared/src'),
    path.resolve(__dirname, '../../../node_modules/@photogai/shared/src'),
  ];
  const resolved = [];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      const real = fs.realpathSync(candidate);
      if (!resolved.includes(real)) {
        resolved.push(real);
      }
    }
  }
  if (resolved.length === 0) {
    // 兜底：即使当前不存在也返回 monorepo 约定路径，避免 config 直接抛错掩盖真实原因。
    return [candidates[0]];
  }
  return resolved;
}

const SHARED_SRC_DIRS = resolveSharedSrc();

const config = {
  projectName: 'photogai-miniprogram',
  date: '2025-01-01',
  // 750 设计稿口径：样式里写的 px 会被 pxtransform 换算为 rpx（1px -> 1rpx）。
  designWidth: 750,
  deviceRatio: {
    640: 2.34 / 2,
    750: 1,
    828: 1.81 / 2,
  },
  sourceRoot: 'src',
  outputRoot: 'dist',
  plugins: [],
  defineConstants: {},
  copy: {
    patterns: [],
    options: {},
  },
  framework: 'react',
  compiler: {
    type: 'webpack5',
    // R1 关键项：关闭依赖预打包。prebundle 会把 node_modules 依赖提前打成 esm bundle，
    // 而 shared 是未编译 TS 源码，预打包阶段无法处理，必须关闭走常规编译链路。
    prebundle: {
      enable: false,
    },
  },
  cache: {
    enable: false,
  },
  alias: {
    '@': path.resolve(__dirname, '..', 'src'),
  },
  sass: {
    // 全局注入主题变量与 mixin，页面/组件 scss 无需重复 @import。
    // 两个文件都只含变量/mixin 定义，不产出 CSS 规则，重复注入无副作用。
    resource: [
      path.resolve(__dirname, '..', 'src/styles/theme.scss'),
      path.resolve(__dirname, '..', 'src/styles/mixins.scss'),
    ],
  },
  mini: {
    compile: {
      // R1 关键项：把 shared 源码目录纳入 Taro 的 babel/ts 编译范围。
      include: SHARED_SRC_DIRS,
    },
    postcss: {
      pxtransform: {
        enable: true,
        config: {},
      },
      url: {
        enable: true,
        config: {
          limit: 1024,
        },
      },
      cssModules: {
        enable: false,
      },
    },
  },
  h5: {
    publicPath: '/',
    staticDirectory: 'static',
    esnextModules: ['taro-ui'],
    postcss: {
      autoprefixer: {
        enable: true,
        config: {},
      },
      cssModules: {
        enable: false,
      },
    },
  },
};

module.exports = function (merge) {
  if (process.env.NODE_ENV === 'development') {
    return merge({}, config, require('./dev'));
  }
  return merge({}, config, require('./prod'));
};
