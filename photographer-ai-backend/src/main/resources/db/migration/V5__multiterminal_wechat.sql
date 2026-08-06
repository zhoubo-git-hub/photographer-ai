-- 移动端扩张 · T1 微信绑定（2026-07-24）
-- 幂等：IF NOT EXISTS；不改动 V1/V4 既有表。
-- 设备令牌(device_token) 与对象存储(upload_file) 由 T2 / T3 各自迁移补充。

-- 1) 微信绑定表（三端同一 studio 的纽带 = union_id）
CREATE TABLE IF NOT EXISTS user_wechat (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id),
    studio_id   BIGINT       NOT NULL REFERENCES studio(id),
    app_type    VARCHAR(10)  NOT NULL,            -- WEB | APP | MP
    openid      VARCHAR(64)  NOT NULL,
    union_id    VARCHAR(64),                       -- 微信开放平台 UnionID（三端打通关键）
    session_key VARCHAR(64),                       -- 仅 MP：wx.login 换取的 session_key
    nickname    VARCHAR(100),
    avatar_url  VARCHAR(512),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (app_type, openid)
);
CREATE INDEX IF NOT EXISTS idx_user_wechat_union  ON user_wechat(union_id);
CREATE INDEX IF NOT EXISTS idx_user_wechat_studio ON user_wechat(studio_id);

-- 2) users 扩 avatar_url（头像，支撑移动端展示）；VARCHAR 直接加列
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512);
