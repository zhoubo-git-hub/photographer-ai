-- ============================================================
-- 阶段4 增量迁移（注册邮箱唯一约束）：2026-07-25
-- 目标：email 选填但全局唯一（格式校验在 DTO 层 @Email）。
-- 说明：
--   - 唯一性权威交给 Flyway 迁移；Hibernate ddl-auto=update 不负责该约束，
--     避免与 Flyway 对约束的管理产生冲突（日志已证实 Flyway 先于 Hibernate 执行）。
--   - PostgreSQL 的 UNIQUE 允许多个 NULL，因此选填语义下多个空邮箱不会冲突。
--   - 当前库为空库，迁移不会因既有重复数据失败。
-- ============================================================

ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
