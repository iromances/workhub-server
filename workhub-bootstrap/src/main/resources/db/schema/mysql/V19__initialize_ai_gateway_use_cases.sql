INSERT INTO `ai_provider_config` (
    `provider_code`,
    `provider_name`,
    `channel_type`,
    `vendor`,
    `model_provider`,
    `api_protocol`,
    `api_base_url`,
    `api_key`,
    `cli_command`,
    `cli_working_directory`,
    `connect_timeout_seconds`,
    `read_timeout_seconds`,
    `call_timeout_seconds`,
    `site_url`,
    `app_name`,
    `enabled`,
    `remark`
)
SELECT
    'codex-cli-default',
    'Codex CLI 默认通道',
    'CLI',
    NULL,
    'OPENAI',
    NULL,
    NULL,
    NULL,
    'codex',
    NULL,
    30,
    300,
    1800,
    NULL,
    NULL,
    1,
    'WorkHub 内置 AI 场景默认本地通道'
WHERE NOT EXISTS (
    SELECT 1 FROM `ai_provider_config` WHERE `provider_code` = 'codex-cli-default'
);

INSERT INTO `ai_use_case_config` (
    `use_case_code`,
    `use_case_name`,
    `domain`,
    `description`,
    `provider_config_id`,
    `model`,
    `reasoning_level`,
    `speed_mode`,
    `timeout_seconds`,
    `json_schema_enabled`,
    `schema_classpath`,
    `prompt_template`,
    `prompt_variables_desc`,
    `prompt_version`,
    `prompt_checksum`,
    `enabled`,
    `remark`
)
SELECT
    seed.`use_case_code`,
    seed.`use_case_name`,
    'INTAKE',
    seed.`description`,
    provider.`id`,
    'gpt-5.5',
    'XHIGH',
    'STANDARD',
    seed.`timeout_seconds`,
    1,
    NULL,
    CONCAT('$', '{prompt}'),
    '[{"name":"prompt","description":"业务代码生成的受控完整提示词"}]',
    1,
    SHA2(CONCAT('$', '{prompt}'), 256),
    1,
    seed.`remark`
FROM (
    SELECT 'intake.structured.extract' AS `use_case_code`,
           '需求结构化提取' AS `use_case_name`,
           '从文本、附件摘要和显式图片中提取结构化需求字段' AS `description`,
           600 AS `timeout_seconds`,
           '支持 API 或 CLI；本地图片只作为显式输入' AS `remark`
    UNION ALL
    SELECT 'intake.sql-draft.generate',
           'SQL草稿生成',
           '根据数据运维需求生成只读 SQL 草稿，不连接数据库、不执行 SQL',
           600,
           '支持 API 或 CLI；只生成草稿'
    UNION ALL
    SELECT 'intake.clarification.analyze',
           '需求澄清分析',
           '结合需求材料、知识库和本地代码仓库生成待确认项与风险项',
           1800,
           '依赖本地仓库，只允许 CLI 通道'
    UNION ALL
    SELECT 'intake.development.analyze',
           '研发方案分析',
           '结合需求和本地代码仓库生成研发拆解草稿',
           1800,
           '依赖本地仓库，只允许 CLI 通道'
    UNION ALL
    SELECT 'intake.development.adjust',
           '研发方案调整',
           '根据人工反馈和本地代码仓库调整研发拆解草稿',
           1800,
           '依赖本地仓库，只允许 CLI 通道'
) seed
JOIN `ai_provider_config` provider ON provider.`provider_code` = 'codex-cli-default'
WHERE NOT EXISTS (
    SELECT 1
    FROM `ai_use_case_config` existing
    WHERE existing.`use_case_code` = seed.`use_case_code`
);
