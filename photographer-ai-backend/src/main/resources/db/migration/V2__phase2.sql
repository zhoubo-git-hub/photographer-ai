-- ============================================================
-- 阶段2 增量迁移（P1 五项）：2026-07-23
-- 幂等：所有 ALTER 用 IF NOT EXISTS / IF EXISTS；不改动 V1 既有的表与列。
-- 说明：复购提醒(REPURCHASE)无关联订单，故将 reminder.order_id 改为可空；
--       studio_id / type / status 等阶段1 列均保持不变。
-- ============================================================

-- 1) 客户画像扩展（供复购引擎 + 沟通助手）
ALTER TABLE customer ADD COLUMN IF NOT EXISTS last_shoot_date DATE;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS repurchase_cycle_days INT;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS birthday DATE;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS anniversary DATE;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS repurchase_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS source_channel VARCHAR(30);  -- 微信/小红书/转介绍

-- 2) 提醒规则表（可配置触发）
CREATE TABLE IF NOT EXISTS reminder_rule (
    id          BIGSERIAL PRIMARY KEY,
    studio_id   BIGINT       NOT NULL REFERENCES studio(id),
    event       VARCHAR(20)  NOT NULL,   -- DEPOSIT | SHOOT | DELIVER | REPURCHASE
    offset_days INT          NOT NULL DEFAULT 0,   -- 负=提前(如拍摄前1天=-1)
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    channel     VARCHAR(20)  NOT NULL DEFAULT 'INAPP',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_reminder_rule_studio ON reminder_rule(studio_id);

-- 3) reminder 扩 customer_id（复购提醒无关联订单）；order_id 改为可空以支持复购提醒
ALTER TABLE reminder ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE reminder ADD COLUMN IF NOT EXISTS customer_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_reminder_customer ON reminder(customer_id) WHERE customer_id IS NOT NULL;

-- 4) 合同模板表（内置 studio_id=NULL）
CREATE TABLE IF NOT EXISTS contract_template (
    id          BIGSERIAL PRIMARY KEY,
    studio_id   BIGINT       REFERENCES studio(id),  -- NULL = 系统内置
    name        VARCHAR(100) NOT NULL,
    category    VARCHAR(50),
    content     TEXT         NOT NULL,                -- 含 {{占位符}}
    builtin     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by  BIGINT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_contract_template_studio ON contract_template(studio_id);

-- 5) 内置合同模板种子（studio_id=NULL，纯字段替换，不依赖 LLM）
INSERT INTO contract_template (studio_id, name, category, content, builtin, created_by)
VALUES
(NULL, '摄影服务合同',
 'service',
 '摄影服务合同

甲方（摄影方）：{{studioName}}
乙方（客户）：{{customerName}}
联系微信：{{wechatId}}　电话：{{phone}}

一、服务项目
拍摄类型：{{shootType}}
拍摄日期：{{shootDate}}
拍摄时长：{{durationHours}} 小时　拍摄张数：约 {{photoCount}} 张
拍摄地区：{{region}}　风格：{{style}}

二、费用与支付
套餐总金额：{{amount}} 元　已付定金：{{depositAmount}} 元　尾款：{{balance}} 元。

三、交付物
精修 {{retouchCount}} 张 + 全部底片（云盘交付）。

四、其他约定
{{note}}
-------------------------
本合同由「摄影师 AI 助手」自动生成，仅供双方协商参考，正式签署前请核对信息。',
 TRUE, NULL),

(NULL, '肖像权授权书',
 'portrait',
 '肖像权授权书

授权人（乙方）：{{customerName}}
被授权人（甲方）：{{studioName}}

本人同意甲方在{{shootType}}拍摄中所摄本人肖像，用于甲方作品展示、宣传及案例分享（不含第三方商业转授权）。授权期限自{{shootDate}}起两年。

授权人签名：__________　日期：__________',
 TRUE, NULL),

(NULL, '定金协议',
 'deposit',
 '定金协议

甲方（摄影方）：{{studioName}}
乙方（客户）：{{customerName}}

乙方向甲方预约{{shootType}}拍摄，支付定金 {{depositAmount}} 元（占总金额 {{amount}} 元的 {{depositRatio}}%）。
定金支付后保留档期{{shootDate}}；乙方取消拍摄，定金按约定不予退还，可协商改期一次。

甲方：__________　乙方：__________　日期：{{shootDate}}',
 TRUE, NULL);
