-- REQ-4ff2e6e6：数字员工岗位选择。
-- 字典 agentPosition 种子（IT 岗位划分 + 全栈开发 + 公司常见岗位，sort 分段），经 ON DUPLICATE KEY 可重复维护；
-- agent.instructions.draft 模板升版 v2 增加"数字员工岗位"段（运行库已应用 v1，checksum 不可变，故新建迁移更新）。

INSERT INTO dict(dict_type, dict_desc, enum_code, enum_desc, sort_order) VALUES
('agentPosition','数字员工岗位','FULL_STACK','全栈开发',10),
('agentPosition','数字员工岗位','BACKEND','后端开发',20),
('agentPosition','数字员工岗位','FRONTEND','前端开发',30),
('agentPosition','数字员工岗位','MOBILE','移动开发',40),
('agentPosition','数字员工岗位','TEST','测试',50),
('agentPosition','数字员工岗位','OPS','运维',60),
('agentPosition','数字员工岗位','DBA','数据库管理员',70),
('agentPosition','数字员工岗位','SECURITY','安全',80),
('agentPosition','数字员工岗位','DATA','数据/BI',90),
('agentPosition','数字员工岗位','ARCHITECT','架构',100),
('agentPosition','数字员工岗位','PM','项目管理',110),
('agentPosition','数字员工岗位','SUPPORT','技术支持',120),
('agentPosition','数字员工岗位','PRODUCT','产品经理',200),
('agentPosition','数字员工岗位','OPERATION','运营',210),
('agentPosition','数字员工岗位','FINANCE','财务',220),
('agentPosition','数字员工岗位','RISK','风控',230),
('agentPosition','数字员工岗位','LEGAL','法务',240),
('agentPosition','数字员工岗位','CUSTOMER_SERVICE','客服',250),
('agentPosition','数字员工岗位','HR','人事',260),
('agentPosition','数字员工岗位','ADMIN','行政',270),
('agentPosition','数字员工岗位','SALES','销售',280),
('agentPosition','数字员工岗位','MARKETING','市场',290)
ON DUPLICATE KEY UPDATE dict_desc=VALUES(dict_desc), enum_desc=VALUES(enum_desc), sort_order=VALUES(sort_order), is_delete=0;

-- agent.instructions.draft v2：新增岗位段与岗位画像指引（变量增 positionName）
SET @tpl := '你是 WorkHub 平台的数字员工职责起草助手，依据用户对话产出或修订职责文本。
职责公式：身份 + 常态职责边界 + 输出习惯 + 升级规则。只写长期不变的常态职责，某次任务要交付的内容不写入职责。
业务线与环境只写业务语义（如叉车业务线生产环境），不写编码坐标、库表名或 targetKey；数据路由由平台数据指南统一注入，职责不携带。
岗位决定职责画像：起草口径贴合岗位（如运维岗侧重巡检发现、口径与升级规则，财务岗侧重账务核对与差异说明，开发岗侧重代码交付与验证习惯）。
巡检类职责建议包含：巡检的业务域、报告格式要点、无异常时也输出明确结论、需人工决策事项单列的升级规则。
用户调整意见优先于既有草稿；已有草稿时按最新意见修订并输出完整新版，不输出差异片段。

数字员工名称：${agentName}
数字员工岗位：${positionName}
授权业务范围：${scopeSummary}
起草对话（用户与助手，含此前草稿）：${conversation}

对话尚无草稿时产出初稿。只输出职责文本本身，不要解释、问候或代码围栏；使用中文，长度适中（通常 150 至 400 字）。';
UPDATE ai_use_case_config
SET prompt_template = @tpl,
    prompt_variables_desc = '[{"name":"agentName","description":"数字员工名称"},{"name":"positionName","description":"数字员工岗位中文名"},{"name":"scopeSummary","description":"授权业务范围摘要文本"},{"name":"conversation","description":"起草对话文本（用户/助手逐条，assistant 承载此前草稿）"}]',
    prompt_checksum = SHA2(@tpl, 256),
    prompt_version = prompt_version + 1,
    modify_time = CURRENT_TIMESTAMP,
    modify_by = 'flyway-agent-position'
WHERE use_case_code = 'agent.instructions.draft'
  AND is_delete = 0
  AND (prompt_checksum IS NULL OR prompt_checksum <> SHA2(@tpl, 256));
