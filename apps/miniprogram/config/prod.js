/**
 * 生产环境配置。
 *
 * 正式域名待运维就位后通过 API_BASE_URL 环境变量注入（微信后台还需配置 request 合法域名）。
 * 此处缺省值仅为占位，**不含任何密钥**（AppSecret 仅后端持有）。
 */
const API_BASE_URL = process.env.API_BASE_URL || 'https://api.example.com/api';

module.exports = {
  env: {
    NODE_ENV: '"production"',
  },
  defineConstants: {
    API_BASE: JSON.stringify(API_BASE_URL),
  },
  mini: {},
  h5: {},
};
