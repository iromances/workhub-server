-- REQ-3e8d95c2 后续变更：数字员工职责 AI 起草功能整体撤销（用户决定）。
-- 场景行软删除（V20261116_160000/170000 已在本地库应用，checksum 不可变，故新建迁移清理）；岗位字典 agentPosition 不受影响。

UPDATE ai_use_case_config
SET enabled = 0,
    is_delete = 1,
    modify_time = CURRENT_TIMESTAMP,
    modify_by = 'flyway-remove-agent-instructions-draft'
WHERE use_case_code = 'agent.instructions.draft'
  AND is_delete = 0;
