-- 需求管理支持在录入时选择数字员工（AI 开发执行 Agent）：
-- 该需求设计完成后触发 AI 分支开发时优先使用此 Agent，未选择回退系统配置 intake.aiDevelopment.agentId。
ALTER TABLE pm_intake_record
    ADD COLUMN ai_agent_id VARCHAR(64) NULL COMMENT '录入时选择的 AI 开发执行数字员工 ID，空则用系统配置默认' AFTER development_standard;
