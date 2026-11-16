-- 采集连续失败计数与最近失败摘要：确定性失败有界重试后由领取方升级重开窗口，防止窗口永久卡死。
ALTER TABLE ops_system_alert_collection_state
    ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0;
ALTER TABLE ops_system_alert_collection_state
    ADD COLUMN last_failure_message VARCHAR(1000) NULL;
