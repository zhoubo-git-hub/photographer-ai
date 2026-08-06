/**
 * 开发环境配置。
 *
 * API_BASE 走 defineConstants 注入（值必须 JSON.stringify，否则会被当成标识符）。
 * 后端地址优先取环境变量 API_BASE_URL，缺省指向本地后端，便于不同同学本机联调。
 */
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api';

module.exports = {
  env: {
    NODE_ENV: '"development"',
  },
  defineConstants: {
    API_BASE: JSON.stringify(API_BASE_URL),
  },
  mini: {},
  h5: {},
};
