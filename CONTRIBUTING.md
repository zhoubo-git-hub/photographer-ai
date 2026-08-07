# Contributing

感谢参与 Photographer AI 的开发。该仓库是多端单仓项目，提交前请先阅读根目录 `README.md`、相关 PRD 和架构文档。

## 开发环境

- Node.js ≥ 18.18
- pnpm ≥ 9
- JDK 17
- Maven 3.9+
- PostgreSQL 16

安装 workspace 依赖：

```bash
pnpm install
```

Web 管理后台不属于根 pnpm workspace，需要单独安装：

```bash
cd photographer-ai-web
pnpm install
```

## 仓库边界

- `packages/shared`：跨端共享的类型、API、hooks、store、HTTP 和领域逻辑
- `apps/miniprogram`：Taro 微信小程序
- `photographer-ai-web`：Vite Web 管理后台
- `photographer-ai-backend`：Spring Boot 后端，不属于 pnpm workspace
- `docs`：系统设计和图表

跨端契约优先放入 `@photogai/shared`，不要在 Web 与小程序中复制同一套领域类型或接口定义。

## 提交原则

- 一次提交聚焦一个清晰目标。
- 修改接口契约时同步更新共享类型、调用方和相关文档。
- 不提交构建产物、真实账号、Token、数据库密码、小程序 AppID 或其他密钥。
- 新增环境变量时同时更新安全的示例配置和 README。
- 不在无关改动中批量格式化整个仓库。
- 变更架构或产品边界时同步更新对应 PRD、架构文档与 Mermaid 图。

## 检查清单

根据受影响范围运行对应检查。

根 workspace：

```bash
pnpm typecheck
pnpm test
pnpm build
```

小程序：

```bash
cd apps/miniprogram
pnpm typecheck
pnpm test
pnpm build:weapp
pnpm verify:dist
```

Web：

```bash
cd photographer-ai-web
pnpm typecheck
pnpm test
pnpm build
```

后端：

```bash
cd photographer-ai-backend
./mvnw test
```

如果某项检查因已知历史问题无法通过，请在提交说明中写明失败命令、错误摘要和本次改动是否相关。

## Pull Request 说明

PR 或变更说明至少应包含：

- 修改了什么
- 为什么修改
- 影响哪些端或共享包
- 如何验证
- 是否涉及数据库迁移、配置变化或兼容性问题
- UI 改动的截图或录屏（如适用）

## 安全问题

发现密钥泄露、越权、敏感数据暴露或其他安全问题时，不要在公开渠道披露完整细节。请先撤销相关凭据并通过仓库所有者认可的私密渠道报告。
