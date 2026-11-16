-- 修复 20261104 一批建表未显式声明字符集的迁移：表继承了库默认 utf8mb4_unicode_ci，
-- 与主表（DEFAULT CHARSET=utf8mb4，即 utf8mb4_0900_ai_ci）按 VARCHAR 主键/外键 join 时
-- 报 1267 Illegal mix of collations（AgentDeploymentMapper.due / AgentVerificationMapper.due 定时任务持续失败）。
-- 统一转换为主流 utf8mb4_0900_ai_ci；CONVERT TO 会重建表但保留全部数据，仅新表无存量风险。
ALTER TABLE pm_agent_release_plan CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE pm_agent_release_plan_step CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE pm_agent_release_review CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE pm_agent_release_step_dependency CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE pm_agent_verification CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE pm_agent_verification_call CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE pm_agent_verification_sample CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE ops_system_alert_collection_page CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE ops_system_alert_collection_state CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE pm_autonomy_case_resolution CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
