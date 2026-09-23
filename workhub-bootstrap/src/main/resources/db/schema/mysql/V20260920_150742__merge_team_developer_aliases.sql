-- 原版本在存储过程权限检查处失败；本修正版仅为准备阶段：只创建维护用表，不修改业务数据。
CREATE TABLE IF NOT EXISTS wh_bak_20260920_team_before LIKE pm_developer_resource;
CREATE TABLE IF NOT EXISTS wh_bak_20260920_team_after LIKE pm_developer_resource;
CREATE TABLE IF NOT EXISTS wh_bak_20260920_intake_before LIKE pm_intake_record;
CREATE TABLE IF NOT EXISTS wh_bak_20260920_intake_after LIKE pm_intake_record;
CREATE TABLE IF NOT EXISTS wh_bak_20260920_work_before LIKE pm_work_item;
CREATE TABLE IF NOT EXISTS wh_bak_20260920_work_after LIKE pm_work_item;
CREATE TABLE IF NOT EXISTS wh_bak_20260920_analysis_before LIKE pm_intake_development_analysis;
CREATE TABLE IF NOT EXISTS wh_bak_20260920_analysis_after LIKE pm_intake_development_analysis;

CREATE TABLE IF NOT EXISTS wh_team_merge_20260920_checks (
    check_name VARCHAR(128) NOT NULL PRIMARY KEY,
    passed TINYINT NOT NULL,
    CONSTRAINT chk_wh_team_merge_20260920_passed CHECK (passed = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
