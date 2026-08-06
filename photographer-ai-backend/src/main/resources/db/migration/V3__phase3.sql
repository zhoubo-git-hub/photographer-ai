-- ============================================================
-- 阶段3 增量迁移（支付 / 团队协作 / 数据看板 / AI 校准受限版）：2026-07-24
-- 幂等：CREATE 用 IF NOT EXISTS；ALTER 用 IF NOT EXISTS。
-- 不改动 V1/V2 既有表与列定义。
-- 说明：studio.plan_type 列本身为 VARCHAR(20)，直接接受 'TEAM' 新值，无需 ALTER；
--       users.role 列同理接受 'ADMIN'/'READONLY'，无需 ALTER。
--       安全边界常量（MAX_OFFSET_PCT=15 / MIN_SAMPLE=20）写在 QuoteCalibrationService，不入库。
-- ============================================================

-- 1) 订阅表（PRO/TEAM 有效期的唯一真源）
CREATE TABLE IF NOT EXISTS subscription (
    id          BIGSERIAL PRIMARY KEY,
    studio_id   BIGINT       NOT NULL REFERENCES studio(id),
    plan_type   VARCHAR(20)  NOT NULL,            -- PRO | TEAM
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | CANCELLED | EXPIRED
    started_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,            -- 到期时间（激活时 = started_at + 30d）
    auto_renew  BOOLEAN      NOT NULL DEFAULT TRUE,
    channel     VARCHAR(20),                      -- WECHAT | ALIPAY | MOCK
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_subscription_studio ON subscription(studio_id);
CREATE INDEX IF NOT EXISTS idx_subscription_active ON subscription(studio_id, status, expires_at);

-- 2) 支付单表（下单→支付→激活订阅）
CREATE TABLE IF NOT EXISTS payment_order (
    id             BIGSERIAL PRIMARY KEY,
    studio_id      BIGINT       NOT NULL REFERENCES studio(id),
    plan_type      VARCHAR(20)  NOT NULL,         -- PRO | TEAM
    channel        VARCHAR(20)  NOT NULL,         -- WECHAT | ALIPAY | MOCK
    out_trade_no   VARCHAR(64)  NOT NULL UNIQUE,  -- 支付通道商户订单号
    amount         NUMERIC(12,2) NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | PAID | FAILED
    paid_at        TIMESTAMPTZ,
    subscription_id BIGINT      REFERENCES subscription(id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_payment_order_studio ON payment_order(studio_id);
CREATE INDEX IF NOT EXISTS idx_payment_order_out ON payment_order(out_trade_no);

-- 3) 团队邀请表
CREATE TABLE IF NOT EXISTS team_invitation (
    id             BIGSERIAL PRIMARY KEY,
    studio_id      BIGINT       NOT NULL REFERENCES studio(id),
    inviter_id     BIGINT       NOT NULL REFERENCES users(id),
    email          VARCHAR(120),
    phone          VARCHAR(30),
    role           VARCHAR(20)  NOT NULL,         -- ADMIN | MEMBER | READONLY
    token          VARCHAR(64)  NOT NULL UNIQUE,  -- 接受邀请凭证
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | ACCEPTED | EXPIRED
    expires_at     TIMESTAMPTZ  NOT NULL,          -- 默认邀请后 7d
    accepted_user_id BIGINT     REFERENCES users(id),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_team_invitation_studio ON team_invitation(studio_id);
CREATE INDEX IF NOT EXISTS idx_team_invitation_token ON team_invitation(token);

-- 4) 报价校准建议表（受限版：建议+采纳留痕，不自动覆盖）
CREATE TABLE IF NOT EXISTS quote_calibration (
    id              BIGSERIAL PRIMARY KEY,
    studio_id       BIGINT       NOT NULL REFERENCES studio(id),
    dimension_key   VARCHAR(80)  NOT NULL,        -- 如 上海|婚纱写真 或 上海|婚纱写真|轻奢
    dimension_label VARCHAR(120) NOT NULL,        -- 展示名
    sample_count    INT          NOT NULL DEFAULT 0,
    current_coef    NUMERIC(8,4) NOT NULL DEFAULT 1.0,
    suggested_coef  NUMERIC(8,4) NOT NULL DEFAULT 1.0,
    offset_pct      INT          NOT NULL DEFAULT 0,  -- 建议在 -15..+15 截断
    within_boundary BOOLEAN      NOT NULL DEFAULT TRUE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING | APPLIED | REJECTED
    applied_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_quote_calibration_studio ON quote_calibration(studio_id);

-- 5) orders 扩 assigned_to（订单分配成员，可空）
ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_to BIGINT REFERENCES users(id);
CREATE INDEX IF NOT EXISTS idx_orders_assigned ON orders(studio_id, assigned_to) WHERE assigned_to IS NOT NULL;
