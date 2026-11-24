-- 数字员工恢复 CLI 原生能力；保留管理员对其他提示内容的定制。
UPDATE ai_use_case_config
SET prompt_template = REPLACE(REPLACE(prompt_template,
    '工作目录仅存放本次 CLI 的临时文件；企业信息从 workhub_task MCP 访问。不要读取宿主配置、记忆、技能或其他项目。',
    '可使用当前 CLI 原生的文件读写、Shell、Git、网络、技能及其他已配置能力，同时通过 workhub_task MCP 调度 WorkHub 工具。临时工作目录用于运行数据；研发任务应先确认真实代码仓库与目标分支，遵循仓库指引，完成修改、测试、提交和推送，并以实际远端分支与提交记录核验交付。'),
    '未接入 MCP 时只依据职责描述、任务与既有轨迹执行，不得虚构任何查询或验证结果。',
    '未接入 WorkHub MCP 时仍可使用 CLI 原生能力；依赖 WorkHub 的资料缺失须明确说明，不得虚构查询或验证结果。'),
    prompt_checksum = SHA2(prompt_template, 256),
    prompt_version = prompt_version + 1,
    modify_time = CURRENT_TIMESTAMP,
    modify_by = 'flyway-agent-native-cli'
WHERE use_case_code = 'agent.cli.step' AND is_delete = 0
  AND (prompt_template LIKE '%工作目录仅存放本次 CLI 的临时文件%'
    OR prompt_template LIKE '%未接入 MCP 时只依据职责描述%');
