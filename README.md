# Photographer AI · 摄影师 AI 接单跟单助手

面向摄影工作室的 AI 接单 / 跟单 / 报价助手。**三端单仓（monorepo）**：Web 管理后台、Taro 微信小程序、React Native App（规划中），共享一套 `@photogai/shared` 业务包；后端为独立的 Spring Boot 工程。

> 当前小程序首版为 **查看 / 轻操作版**（订单、档期、AI 报价、客户、提醒、通知的列表与查看），新建 / 编辑保留在 Web 端。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 前端（Web） | React 18 + TypeScript + Vite + MUI + Zustand + TanStack Query + Axios |
| 前端（小程序） | Taro 3 + React + TypeScript（消费 `@photogai/shared`） |
| 前端（RN App） | 规划中 |
| 共享包 | `@photogai/shared`（types / api / hooks / store / http / domain 子路径导出） |
| 后端 | Spring Boot 3.2 / Java 17 / Maven / JPA / PostgreSQL 16 / Flyway |
| 工程化 | pnpm workspace + turbo |

---

## 仓库结构

```
photographer-ai/
├─ packages/shared/            # @photogai/shared 共享业务包（TS 源码分发）
├─ apps/miniprogram/           # Taro 微信小程序（查看/轻操作版）
├─ photographer-ai-web/        # Vite Web 管理后台（顶层独立目录，单独装依赖）
├─ photographer-ai-backend/    # Spring Boot 后端（Maven 工程，故意不进 pnpm workspace）
├─ docs/                       # 各端设计文档（含 T7 小程序系统设计与类图/时序图）
├─ architecture*.md / prd*.md  # 总体与分阶段架构、需求文档
├─ pnpm-workspace.yaml         # workspace 仅含 packages/* 与 apps/*
├─ turbo.json / package.json   # 根任务编排 + pnpm.overrides 钉版本
└─ tsconfig.base.json
```

> ⚠️ `photographer-ai-web` 不在 `pnpm-workspace.yaml` 的 glob 内，需**单独安装依赖**（见下）。

---

## 快速开始

### 前置要求

- Node ≥ 18.18、pnpm ≥ 9
- JDK 17、Maven 3.9+
- PostgreSQL 16（后端 Flyway 自动迁移建表）

### 1. 安装依赖

```bash
# workspace 内（packages/*、apps/*）
pnpm install

# Web 是顶层独立目录，单独安装
cd photographer-ai-web && pnpm install
```

### 2. 微信小程序（Taro）

```bash
cd apps/miniprogram
pnpm install

pnpm dev:weapp      # 开发模式（watch），用微信开发者工具导入 apps/miniprogram 根目录
pnpm build:weapp    # 生产构建，产出 dist/，用微信开发者工具导入 dist/

pnpm test           # 冒烟测试（14 项）
pnpm verify:dist    # 产物门禁（单例性 / 产物完整性反查）
pnpm typecheck      # tsc --noEmit
```

> 微信 AppID 当前为占位 `touristappid`（见 `apps/miniprogram/project.config.json` 与 `MP_APPID` 环境变量）。真机预览需替换为真实小程序 AppID。

### 3. Web 管理后台（Vite）

```bash
cd photographer-ai-web
pnpm install

pnpm dev        # 本地开发（Vite dev server）
pnpm build      # 生产构建（tsc --noEmit && vite build）
pnpm preview    # 预览构建产物
pnpm test       # vitest run
pnpm typecheck  # tsc --noEmit
```

### 4. 后端（Spring Boot / Maven）

```bash
cd photographer-ai-backend
./mvnw spring-boot:run        # 或：mvn spring-boot:run
```

后端通过 Flyway 自动迁移建表，需本机运行 PostgreSQL 16。AI 能力（报价 / 沟通话术）对接 OpenAI 兼容或 DeepSeek，**LLM 不可用时自动降级**（规则价 / 话术模板），不抛 500。

---

## 共享包 `@photogai/shared`

前端三端共用，导出子路径：

- `@photogai/shared/types` — 领域类型 / API 契约
- `@photogai/shared/api` — 接口封装（order / customer / quote / schedule / reminder / auth …）
- `@photogai/shared/hooks` — 业务 hooks（useOrders / useQuote / useAuth …）
- `@photogai/shared/store` — Zustand stores（auth / ui）
- `@photogai/shared/http` — 配置化 axios 客户端
- `@photogai/shared/domain` — 标签 / 格式化等纯函数

> 各端通过 `initShared` 注入 `StorageAdapter` + `configureHttpClient` 并双向同步 auth 状态，保证 token / user / studio 一致。

---

## 环境变量

| 变量 | 说明 | 默认 / 占位 |
|---|---|---|
| `MP_APPID` | 微信小程序 AppID | `touristappid` |
| `API_BASE` | 后端基地址 | 本地后端 |
| 后端 `SPRING_DATASOURCE_*` | PostgreSQL 连接 | 见 `photographer-ai-backend` 配置 |

---

## 测试与质量门禁

- **小程序**：`pnpm test`（冒烟）+ `pnpm verify:dist`（产物门禁，反查依赖单例性与 `No QueryClient set` 出现次数）。
- **Web**：`pnpm test`（vitest）。
- **后端**：`mvn test`（AI 模块降级逻辑有单测覆盖）。
- 根任务：`pnpm typecheck` / `pnpm test` / `pnpm build` 经 turbo 编排。

---

## 文档

- 总体架构：`architecture-multiterminal.md`
- 小程序（T7）设计：`docs/system_design_t7_miniprogram.md` + 类图 / 时序图（同目录 `.mermaid`）
- 需求文档：`prd.md` / `prd-phase2.md` / `prd-phase3.md`

---

## 已知事项 / 路线图

- 小程序首版为**查看 / 轻操作版**，新建 / 编辑留在 Web（首版拍板结论）。
- 订阅消息推送（T2）、图片上传 dev LOCAL 为后置项。
- RN 端为规划中能力，建立时需沿用本仓的依赖单副本防御（见 `apps/miniprogram/config/index.js` 的 webpack alias + 根 `pnpm.overrides`）。
- Web 端 `typecheck` 存在预存告警（`TS2688 minimatch`，非小程序回归），待清理。

---

## License

私有项目，未经授权禁止外传或使用。
