-- =====================================================================
-- 摄影师 AI 接单跟单助手 - 初始化建表（阶段1 MVP）
-- 多租户根：studio；所有业务表均带 studio_id 隔离
-- 软删除：deleted_at IS NULL 视为有效
-- =====================================================================

-- 工作室（多租户根，MVP 单 studio；P2 团队复用）
CREATE TABLE studio (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    plan_type     VARCHAR(20)  NOT NULL DEFAULT 'FREE',  -- FREE | PRO
    owner_user_id BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 用户（MVP 单人；P2 成员/权限）
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    studio_id   BIGINT       NOT NULL REFERENCES studio(id),
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    email       VARCHAR(120),
    role        VARCHAR(20)  NOT NULL DEFAULT 'OWNER',   -- OWNER | MEMBER
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 客户库
CREATE TABLE customer (
    id         BIGSERIAL PRIMARY KEY,
    studio_id  BIGINT       NOT NULL REFERENCES studio(id),
    name       VARCHAR(100) NOT NULL,
    wechat_id  VARCHAR(100),
    phone      VARCHAR(30),
    tags       TEXT,                     -- MVP 逗号分隔；P1 可转标签表
    note       TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- 订单（核心）
CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    studio_id        BIGINT       NOT NULL REFERENCES studio(id),
    customer_id      BIGINT       NOT NULL REFERENCES customer(id),
    title            VARCHAR(200) NOT NULL,
    shoot_type       VARCHAR(50),       -- 婚纱写真/亲子/毕业/商务...
    status           VARCHAR(20)  NOT NULL DEFAULT 'CONSULT',
    amount           NUMERIC(12,2),
    deposit_amount   NUMERIC(12,2),
    currency         VARCHAR(10)  DEFAULT 'CNY',
    shoot_date       DATE,              -- 拍摄日（用于档期）
    shoot_end_date   DATE,              -- 拍摄结束日（跨天/多场重叠判定）
    duration_hours   INT,
    photo_count      INT,
    region           VARCHAR(50),
    style            VARCHAR(50),
    quote_suggestion TEXT,              -- AI 报价回填
    created_by       BIGINT       REFERENCES users(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ
);

-- 状态流转留痕
CREATE TABLE status_history (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES orders(id),
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    operator_id BIGINT       REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 到期自动提醒（P0 仅站内；P1 扩微信/短信）
CREATE TABLE reminder (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT       NOT NULL REFERENCES orders(id),
    studio_id  BIGINT       NOT NULL REFERENCES studio(id),
    type       VARCHAR(30)  NOT NULL,   -- DEPOSIT_DUE | SHOOT_TOMORROW | EDIT_OVERDUE
    due_at     TIMESTAMPTZ,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | DONE | DISMISSED
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 额度（免费版控制）
CREATE TABLE quota (
    id                  BIGSERIAL PRIMARY KEY,
    studio_id           BIGINT       NOT NULL REFERENCES studio(id) UNIQUE,
    plan_type           VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    order_count         INT          NOT NULL DEFAULT 0,  -- 在管订单数（非软删）
    ai_quote_used_month INT          NOT NULL DEFAULT 0,  -- 当月 AI 报价已用
    quota_month         VARCHAR(7)   NOT NULL,            -- 'YYYY-MM'
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 隔离与查询索引
CREATE INDEX idx_orders_studio_status ON orders(studio_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_orders_shoot        ON orders(studio_id, shoot_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_customer_studio     ON customer(studio_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_reminder_studio     ON reminder(studio_id, status);
