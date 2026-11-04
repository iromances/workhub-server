-- 审阅用；本轮未在真实库执行。
-- 执行前预期0行，执行后预期2行；先确认DATABASE()为已批准的目标库。
SELECT TABLE_NAME FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('ops_system_alert_collection_state', 'ops_system_alert_collection_page');
-- 执行后核对主键、分页唯一键和队列/时间索引。
SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('ops_system_alert_collection_state', 'ops_system_alert_collection_page')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;
-- 历史只读核对：同区间页号不应重复；此SQL不推进游标。
SELECT window_id, page_number, COUNT(*) AS duplicate_count
FROM ops_system_alert_collection_page GROUP BY window_id, page_number HAVING COUNT(*) > 1;
