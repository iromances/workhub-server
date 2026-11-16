-- 需求任务评估SDD化与AI开发提测流程升级（REQ-a71851db-1d1c-4f20-b99e-330950af6ad9）：
-- 1) pm_intake_record 增加研发规范与审核员两列，旧数据 NULL 按常规逻辑处理，不做回填；
-- 2) 新增提测 AI 审核记录表，保存审核结论、缺陷清单、人工强制确认与禅道/Jenkins 回执；
-- 3) 幂等预置 SDD 设计评估与提测审核两个 AI 场景（provider 绑定内置 CLI 通道仅为满足约束，
--    运行时可按需调整，模板校验和与版本随迁移写入）。

ALTER TABLE pm_intake_record
    ADD COLUMN development_standard VARCHAR(32) NULL COMMENT '研发规范：SDD/STANDARD，NULL 视为 STANDARD 之外的常规模式' AFTER development_owner_user_name,
    ADD COLUMN review_user_name VARCHAR(64) NULL COMMENT '审核员（澄清阶段选择，任务负责人默认值）' AFTER development_standard;

CREATE TABLE IF NOT EXISTS pm_intake_ai_development (
    id BIGINT NOT NULL AUTO_INCREMENT,
    intake_id BIGINT NOT NULL,
    development_branch VARCHAR(128) NULL COMMENT '触发时锁定的研发分支',
    agent_id VARCHAR(64) NULL COMMENT '配置的 AI 开发 Agent',
    agent_task_id VARCHAR(64) NULL,
    request_key VARCHAR(64) NOT NULL,
    execution_status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED',
    execution_message VARCHAR(512) NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pm_intake_ai_development_intake (intake_id),
    KEY idx_pm_intake_ai_development_status (execution_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS pm_intake_submit_test_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    intake_id BIGINT NOT NULL,
    review_status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/PASSED/FAILED',
    review_message VARCHAR(255) NULL,
    findings_json TEXT NULL COMMENT 'AI 审核缺陷清单 JSON',
    conclusion VARCHAR(32) NULL COMMENT 'AI 审核结论：PASS/PASS_WITH_NOTES/FAIL',
    summary VARCHAR(512) NULL COMMENT 'AI 审核摘要',
    human_confirmed_by VARCHAR(64) NULL COMMENT '审核不通过后人工确认强制提测的账号',
    human_confirmed_at DATETIME NULL,
    zentao_test_task_no VARCHAR(64) NULL,
    zentao_test_task_url VARCHAR(512) NULL,
    jenkins_environment_code VARCHAR(32) NULL COMMENT '人工选择的测试环境编码',
    jenkins_build_number BIGINT NULL,
    jenkins_build_url VARCHAR(512) NULL,
    jenkins_build_status VARCHAR(32) NULL,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pm_intake_submit_test_review_intake (intake_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- intake.development.sdd-analyze SDD 设计评估
SET @tpl := '你是 WorkHub 的 SDD 设计评估架构师。
当前需求声明使用 SDD 研发规范。请先阅读给定的 SDD 流程规范，再按其要求产出设计评估草稿：完整 SDD 三件套内容（proposal、design、tasks）与任务拆解摘要。输出必须符合 JSON Schema，只生成评估草稿，不修改代码、不创建工作项。

工作流：
1. 按 SDD 流程规范理解 Proposal/Design/Tasks 三份产物的定位与填写要求。
2. 读需求 Markdown 和补充材料，提炼业务需求点 requirementChangePoints。
3. 查业务线 Git 代码，确认现有系统、页面、接口、配置、表、定时任务和可复用逻辑；纯文案/运营需求可不查代码，但要在 evidenceRefs 说明。
4. 起草 sddProposal/sddDesign/sddTasks：Markdown 文本，遵循 SDD 模板的章节结构；tasks 中的任务表是 workItems 的唯一来源。
5. workItems 从 sddTasks 的任务表逐条摘要生成：title=任务项、description/changePoints=改动点、estimatedEffort=工时、systemTags=改动系统。

任务要求：
- sddProposal 覆盖目标、范围、验收标准；sddDesign 覆盖方案、契约、数据、验证与回滚；sddTasks 覆盖任务明细与依赖。
- title 用 Jira/禅道风格，格式类似“系统名 + 动词 + 业务对象 + 能力/规则”。
- systemTags 只能写真实涉及系统，优先从“可选改造系统”中选择。
- estimatedEffort 用小时，例如 1h、4h、8h。
- ownerUserName 一律输出 null，负责人由人工指派或审核员默认，不由 AI 猜测。
- relatedFiles/evidenceRefs 写代码或材料证据；涉及系统改造却没有代码证据时，confidence 置 LOW，并在 risks/questions 说明。
- questions 只写真正影响实现或验收的问题，不要把需求已明确的事实列为疑问。

SDD 流程规范：
${sddProcessSpec}

项目信息：
${projectInfo}

候选研发人员：
${developers}

可选改造系统：
${systemTags}

需求 Markdown 文件：
${requirementMarkdown}

需求信息（仅用于和需求 Markdown 交叉校验，不作为替代输入）：
${intakeInfo}

结构化识别字段：
${structuredFields}

附件内容摘要：
${attachmentSummaries}

项目知识库参考：
${knowledgeBase}

原始内容：
${rawContent}
';
INSERT INTO ai_use_case_config (
    use_case_code, use_case_name, domain, description, provider_config_id, model,
    reasoning_level, speed_mode, timeout_seconds, json_schema_enabled, schema_classpath,
    prompt_template, prompt_variables_desc, prompt_version, prompt_checksum, enabled, remark
)
SELECT
    'intake.development.sdd-analyze',
    'SDD 设计评估',
    'INTAKE',
    'SDD 模式研发评估：读取知识库 SDD 流程规范，产出三件套草稿与任务拆解摘要',
    provider.id,
    NULL,
    NULL,
    NULL,
    NULL,
    0,
    NULL,
    @tpl,
    '[{"name":"sddProcessSpec","description":"知识库 SDD 流程规范全文"},{"name":"projectInfo","description":"项目信息段落"},{"name":"developers","description":"候选研发人员清单"},{"name":"systemTags","description":"可选改造系统清单"},{"name":"requirementMarkdown","description":"需求 Markdown 文件内容"},{"name":"intakeInfo","description":"需求信息段落"},{"name":"structuredFields","description":"结构化识别字段清单"},{"name":"attachmentSummaries","description":"附件内容摘要清单"},{"name":"knowledgeBase","description":"项目知识库参考内容"},{"name":"rawContent","description":"需求原始内容"}]',
    1,
    SHA2(@tpl, 256),
    1,
    'SDD 模式研发评估场景；负责人一律为空由人工指派'
FROM ai_provider_config provider
WHERE provider.provider_code = 'codex-cli-default'
  AND NOT EXISTS (SELECT 1 FROM ai_use_case_config existing WHERE existing.use_case_code = 'intake.development.sdd-analyze');

-- intake.submit-test.review 提测 AI 审核
SET @tpl := '你是 WorkHub 的提测审核助手。
当前需求准备从开发中提交测试。请基于研发分支代码差异与验证证据做提测前审核，输出必须符合 JSON Schema。

审核要求：
1. findings 逐条列出发现的缺陷或风险：title=问题标题、severity=HIGH/MEDIUM/LOW、description=问题描述与位置、evidence=依据（文件/提交/验证记录）。没有缺陷时返回空数组，不要为了凑数制造问题。
2. conclusion=PASS 表示可以提测；存在 HIGH 或 MEDIUM 缺陷时 conclusion=FAIL；仅 LOW 时 conclusion=PASS_WITH_NOTES。
3. summary 概括审核结论与依据。只输出事实，不猜测未提供的信息。

研发分支：
${developmentBranch}

改动差异摘要：
${diffSummary}

验证证据：
${verificationEvidence}

需求信息：
${intakeInfo}

任务拆解：
${workItems}
';
INSERT INTO ai_use_case_config (
    use_case_code, use_case_name, domain, description, provider_config_id, model,
    reasoning_level, speed_mode, timeout_seconds, json_schema_enabled, schema_classpath,
    prompt_template, prompt_variables_desc, prompt_version, prompt_checksum, enabled, remark
)
SELECT
    'intake.submit-test.review',
    '提测 AI 审核',
    'INTAKE',
    '提交测试前的 AI 审核：基于分支差异与验证证据输出缺陷清单与结论',
    provider.id,
    NULL,
    NULL,
    NULL,
    NULL,
    0,
    NULL,
    @tpl,
    '[{"name":"developmentBranch","description":"研发分支名"},{"name":"diffSummary","description":"研发分支代码差异摘要"},{"name":"verificationEvidence","description":"验证证据段落"},{"name":"intakeInfo","description":"需求信息段落"},{"name":"workItems","description":"任务拆解清单 JSON"}]',
    1,
    SHA2(@tpl, 256),
    1,
    '提测前 AI 审核场景；结论仅作流转依据，人工可确认强制提测'
FROM ai_provider_config provider
WHERE provider.provider_code = 'codex-cli-default'
  AND NOT EXISTS (SELECT 1 FROM ai_use_case_config existing WHERE existing.use_case_code = 'intake.submit-test.review');
