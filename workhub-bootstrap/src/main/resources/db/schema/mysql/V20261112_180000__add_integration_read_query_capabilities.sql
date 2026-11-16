-- REQ-0051c0a8：禅道/Jenkins 面向 AI 的只读查询能力登记为已接入（READ/ACTIVE）。
-- 幂等：存在即跳过；回滚仅需删除对应能力行，不影响运行。

INSERT INTO `sys_integration_capability` (`system_id`, `capability_code`, `capability_name`, `capability_type`, `impl_status`, `description`, `enabled`, `sort_order`)
SELECT sys.`id`, c.`capability_code`, c.`capability_name`, c.`capability_type`, c.`impl_status`, c.`description`, 1, c.`sort_order`
FROM `sys_integration_system` sys
JOIN (
  SELECT 'ZENTAO' AS `system_code`, 'search-bugs' AS `capability_code`, '缺陷检索' AS `capability_name`, 'READ' AS `capability_type`, 'ACTIVE' AS `impl_status`,
         'MCP search_zentao_bugs：按产品/迭代/关键词只读检索缺陷（作用域端点缺失时回退全局列表过滤）' AS `description`, 50 AS `sort_order` UNION ALL
  SELECT 'ZENTAO', 'query-bug', '缺陷详情', 'READ', 'ACTIVE', 'MCP get_zentao_bug：只读查询单个缺陷详情', 60 UNION ALL
  SELECT 'ZENTAO', 'query-story', '需求详情', 'READ', 'ACTIVE', 'MCP get_zentao_story：只读查询单个需求详情', 70 UNION ALL
  SELECT 'ZENTAO', 'query-task', '任务详情', 'READ', 'ACTIVE', 'MCP get_zentao_task：只读查询单个任务详情', 80 UNION ALL
  SELECT 'ZENTAO', 'query-execution', '迭代详情', 'READ', 'ACTIVE', 'MCP get_zentao_execution：只读查询单个迭代详情（含所属项目/产品）', 90 UNION ALL
  SELECT 'JENKINS', 'list-jobs', 'Job 列表', 'READ', 'ACTIVE', 'MCP list_jenkins_jobs：只读列出实例根目录或 folder 下 Job，不触发构建', 60 UNION ALL
  SELECT 'JENKINS', 'query-job', 'Job 详情', 'READ', 'ACTIVE', 'MCP get_jenkins_job：只读查询 Job 详情与参数清单，不触发构建', 70 UNION ALL
  SELECT 'JENKINS', 'query-build', '构建详情', 'READ', 'ACTIVE', 'MCP get_jenkins_build：只读查询构建状态、参数与耗时', 80 UNION ALL
  SELECT 'JENKINS', 'query-build-log', '构建日志尾读', 'READ', 'ACTIVE', 'MCP get_jenkins_build_log：只读尾读构建控制台日志（行数/字节受限）', 90
) AS c ON c.`system_code` = sys.`system_code`
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_integration_capability` existing
  WHERE existing.`system_id` = sys.`id` AND existing.`capability_code` = c.`capability_code`
);
