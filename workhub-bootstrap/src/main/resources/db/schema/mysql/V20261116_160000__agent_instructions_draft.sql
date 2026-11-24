-- REQ-3e8d95c2：数字员工职责 AI 起草。新场景 agent.instructions.draft（对话式起草，无状态）；
-- 每次请求经 AiGatewayRequest 覆盖接入与模型，本行 provider 绑定仅为满足非空约束。

SET @tpl := '你是 WorkHub 平台的数字员工职责起草助手，依据用户对话产出或修订职责文本。
职责公式：身份 + 常态职责边界 + 输出习惯 + 升级规则。只写长期不变的常态职责，某次任务要交付的内容不写入职责。
业务线与环境只写业务语义（如叉车业务线生产环境），不写编码坐标、库表名或 targetKey；数据路由由平台数据指南统一注入，职责不携带。
巡检类职责建议包含：巡检的业务域、报告格式要点、无异常时也输出明确结论、需人工决策事项单列的升级规则。
用户调整意见优先于既有草稿；已有草稿时按最新意见修订并输出完整新版，不输出差异片段。

数字员工名称：${agentName}
授权业务范围：${scopeSummary}
起草对话（用户与助手，含此前草稿）：${conversation}

对话尚无草稿时产出初稿。只输出职责文本本身，不要解释、问候或代码围栏；使用中文，长度适中（通常 150 至 400 字）。';
INSERT INTO ai_use_case_config (
    use_case_code, use_case_name, domain, description, provider_config_id, model,
    reasoning_level, speed_mode, timeout_seconds, json_schema_enabled, schema_classpath,
    prompt_template, prompt_variables_desc, prompt_version, prompt_checksum, enabled, remark
)
SELECT
    'agent.instructions.draft',
    'Agent 职责起草',
    'AGENT',
    'Agent 配置页职责起草助手的系统提示词：对话式产出与修订职责文本',
    (SELECT provider.id FROM ai_provider_config provider ORDER BY provider.id LIMIT 1),
    NULL,
    NULL,
    NULL,
    NULL,
    0,
    NULL,
    @tpl,
    '[{"name":"agentName","description":"数字员工名称"},{"name":"scopeSummary","description":"授权业务范围摘要文本"},{"name":"conversation","description":"起草对话文本（用户/助手逐条，assistant 承载此前草稿）"}]',
    1,
    SHA2(@tpl, 256),
    1,
    '职责起草每次请求覆盖接入与模型；本行 provider 绑定仅为满足非空约束（REQ-3e8d95c2）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_use_case_config existing WHERE existing.use_case_code = 'agent.instructions.draft');
