# Design：数字员工固定报告结构与分表存储

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-72ecd9bc-ba95-4d1e-b927-253f019e0235 |
| 需求名称 | 数字员工固定报告结构与分表存储 |
| 唯一目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-72ecd9bc-ba95-4d1e-b927-253f019e0235-数字员工固定报告结构与分表存储 |
| 业务线 | BL000012-工作台 |
| 涉及仓库 | workhub-server（契约、存储、执行接入）；workhub-web（展示与导出） |
| 分支/环境 | 后端 codex/development-20261102（HEAD 5ff1954，已有未提交修改）；前端 codex/development-20261103；本机开发验证 |
| 版本/状态 | v1 已确认，实施中；2026-11-22 用户在本会话回复“确认” |
| 需求来源 | 2026-11-22 当前会话：按既定结构返回，参考智能柜退款修复审查报告，并在本地分表存储 |
| 负责人/评审人 | Codex 起草；用户确认 |

## 1. 当前实现证据
- AgentNativeCliAdapter.RESULT_SCHEMA：原有结果顶层字段及监测问题数组。
- ZCodeTaskConversation / AgentResultFormatter：原文先存，后者仅识别整体 JSON 或起始围栏；实际错误回执只保留 code。
- AgentRunStore.finishSavedNative：验收与结果终态入口；AgentIssueService：从 CLI_RESULT 摄取问题，task+signature 去重。
- AgentReportView：安全 Markdown 渲染与摘要摘取；既有 AgentRunDetailResponse 提供 run/configuration/events/artifacts。
- 现有 pm_agent_event/pm_agent_artifact 保存轨迹与产物，pm_agent_issue 保存跨轮修复状态。

## 2. 契约与输出流程
详见 [contract.md](contract.md)。沿用外层 type（RESULT/NEEDS_INPUT）和 deliveries 交付契约，新增 schemaVersion=2 和 report；summary 作为过渡兼容字段由服务从 report.conclusion 派生，不让 AI 重复填写报告。

业务执行结束 → 先保存脱敏原文 → 确定性提取单个 JSON → 完整字段/嵌套类型/枚举/引用校验 → 既有执行与交付验收 → 同事务写报告五表和运行结果/终态 → 既有通知与工单摄取。
- JSON Schema 由后端统一维护，提示词引用同一契约；原生渠道支持结构化输出且不影响工具调用时使用原生能力，实施时验证，不能把“提示词写了 schema”当作协议强约束。
- 不支持原生强约束时仍保留服务端完整校验。工具权限、时间预算、租约、取消和交付核验不改变。
- 提取先尝试完整 JSON，再尝试唯一完整 json 围栏；多个候选、未知版本或正文截断拒绝，不以贪婪花括号匹配捡任意子对象。
- 合法候选直接采用，不调用模型改写。语义/结构非法时保留原文及字段路径错误，显示待恢复；用户走已有重试入口时只重新整理已保存原文，不重新执行业务；移除新协议正常完成路径的自动模型整理依赖。
- 兼容已在运行中的旧版结果，不能将重启前的旧契约直接判错。旧结果无结构时沿用旧显示；旧版唯一 JSON 的格式恢复可直接提取，并保留来源版本。
- 错误详情保留脱敏 code/message 和请求关联标识，页面显示简短错误，不泄漏令牌、配置、模型原始敏感载荷。

## 3. 数据库设计（五张新表）
所有 ID 与现有库一致采用 VARCHAR(36)，时间 DATETIME(6)/UTC；正文 LONGTEXT，不做静默截断；表归属字段由服务按已鉴权运行补齐，禁止模型指定数据库主键或归属。JSON 元数据采用现有 LONGTEXT JSON 风格并在应用验证。

| 表 | 主要字段 | 约束/索引 |
| --- | --- | --- |
| pm_agent_report | id, run_id, task_id, agent_id, schema_version, report_type, title, conclusion, assessment, scope_json, limitations_json, original_event_id, payload_hash, created_at | UNIQUE(run_id)，INDEX(task_id,created_at) |
| pm_agent_report_check | id, report_id, item_key, ordinal, category, title, outcome, result_text | UNIQUE(report_id,item_key)，INDEX(report_id,ordinal) |
| pm_agent_report_finding | id, report_id, item_key, ordinal, title, severity, verification_status, disposition, category, trigger_text, analysis_text, impact_text, recommendation_text, signature, domain, issue_id | UNIQUE(report_id,item_key)，INDEX(report_id,ordinal)，INDEX(issue_id) |
| pm_agent_report_evidence | id, report_id, item_key, ordinal, target_type, target_key, evidence_type, source, locator, observed_at, environment_code, verification_method, result_text, limitations_text | UNIQUE(report_id,item_key)，INDEX(report_id,target_type,target_key) |
| pm_agent_report_action | id, report_id, item_key, ordinal, finding_key, priority, description, acceptance_criteria, prerequisite_text | UNIQUE(report_id,item_key)，INDEX(report_id,ordinal) |

scope_json 是固定结构的范围信息（业务线、环境、时间窗、仓库与提交、比较基线及包含/排除范围），不是整篇报告。limitations_json 只保存报告级未验证事项列表；主要业务事实落明细表。权限范围以运行配置为准，不能信任 AI 声称的业务线/环境。
证据 target_type=REPORT/CHECK/FINDING，target_key 对应本报告 item_key；行动可关联 finding_key。服务校验引用在同一报告内，跨报告引用拒绝。数组顺序持久化，空值与未验证事实明确区分。

报告为单轮不可变快照，不随着 pm_agent_issue 的后续修复变更内容；现有工单不是新增的第六张表。仅既有监测域与有效 signature 的发现接入原有工单；一般审查发现保存报告即可。旧 ingest 使用结构化报告适配，不再从 Markdown 猜问题；同轮去重不能重复增加 occurrence_count。

原子与幂等：锁运行并核验 owner/version；唯一 run_id 防重复。相同 payload_hash 重复完成幂等返回，冲突内容拒绝覆盖。五表保存失败则整个事务回滚，不能先标记 COMPLETED。FAILED/NEEDS_INPUT 的报告状态不自动转换为业务成功。已有报告删除、任务删除、Agent 删除入口需纳入五表计数及子表先删除，保留现有审计。

## 4. 阅读接口与前端
新增 GET /api/agents/{agentId}/runs/{runId}/report，返回 {available,report}；无新报告返回 available=false，继续现有详情读取旧文本。复用原运行查看权限，校验 agent/run 归属，拒绝跨业务线访问。
report 返回固定字段及有序 checks/findings/evidence/actions，不将数据库内部主键暴露为模型契约。列表/历史页不加载全部证据，详情读取完整报告，不按正文长度截断。

页面和导出共用模板：结论 → 范围表 → 检查状态表 → 问题总览及详情 → 未闭环/未验证事项 → 验证结果 → 后续行动与验收 → 证据索引。验证章节由 evidence 中验证类型数据生成，问题详情通过引用关联同一份证据，不让模型重复写一套正文。空章节显示“无记录”或按适用性隐藏，不显示“全部正常”。所有文本安全渲染，脚本和危险链接不执行。

## 5. 迁移、发布与回滚
Flyway 新增五表，无破坏性改列或历史清洗，不修改既有已应用迁移。顺序：迁移及兼容后端 → 前端；后端无 report 的旧数据仍可读，前端可回退旧显示。新契约仅用于已接入新存储的执行链。
验证先隔离库；现有本地服务连接的是共享 workhub 数据库，不能因“本地”而默认为可随意修改数据。真实执行迁移/重启时须记录目标及结果，不能以编译成功代替。
回滚程序可保留新增表和历史快照，禁止回滚时删除报告数据。未自动转换历史记录；单条旧原文恢复须可追溯并标明来源版本、无新增业务执行。
更新工作台现状/迭代日志及前端架构文档，区分源码完成、验证完成和部署完成。

## 6. 确认与实施补充
2026-11-22 用户确认 v1 五表、通用契约、既有工单复用和模板展示整体范围；实施结果见 tasks.md，无软件安装。附件仅作内容编排样例，未沿用其中的真实业务结论或执行其建议。

实施细化：API 旧 plan/summary 决策继续兼容，新增 v2 直接落报告表；ZCode 新任务强制校验 v2，旧原文恢复可保留旧格式。报告读取使用只读一致性事务；跨轮工单链接只更新关联元数据，不改写报告原始内容。


## 2026-11-22 v1.1：报告三层格式保障（本会话用户确认）
用户在了解三层方案后明确要求“三层都做”，确认本轮替代 v1 的新任务格式错误立即失败行为。仅 workhub-server，本机开发验证；不改模型配置、不安装依赖、不重启或恢复真实运行。
Codex 保留 --output-schema。读取已安装 ZCode 分发中 workspace/generateText 的参数转发函数 FLi，只有 selection/prompt/messages/tools/querySource/maxOutputTokens，没有 Schema 参数；本版不能启用原生强约束，不伪造参数或绕过配置直连供应商。ZCode 在原文保存成功后本地提取校验，不合格时通过无工具 workspace/generateText 整理；携带原文、完整 Schema 和具体校验错误，要求额外字段内容归入正式字段且保留已有内容，不编造事实。新 v2 任务全程强制 v2；旧原文恢复保持兼容，但已识别 v2 的原文禁止降级。v2 最多两次请求（包括 length 续接），沿用总运行预算、取消与原文恢复边界。最终仍经完整结构、枚举、引用及 assessment 业务校验后才能进入既有事务入库。


## 2026-11-22 v1.2：结果报告可读性与全量时间范围（草案，待确认）

对应 Proposal v1.2；需求名称/编码、仓库和分支沿用其元信息。

### 事实与根因
- workhub-web/src/views/agent/structuredAgentReport.ts：时间直接 from/to || 未记录；businessLines 直接拼编码；正文仅换行，检查状态无标签。
- AgentRunPanel.vue：页面与下载共享结构化渲染器，可传入相同展示上下文。
- AgentReportService：scope_json 按报告原样存取，未在读取时丢失时间；Schema 允许 from/to 为 null。
- AgentNativeCliAdapter/AgentApiExecutionAdapter 的 ELI5 提示要求通俗解释，最新结论因此混入大量比喻和明细。改为简明中文、结论与明细分工，不用截断解决冗余。
- 2026-11-22 使用 WorkHub MCP 运行库 PROD readonly 查询最新报告及检查项；无数据写入。浏览器连接超时，未完成页面观察。

### 实现职责与数据边界
1. 前端共享渲染：范围、状态、正文块格式化；保持结构化报告 v2、原始字符串和不可变快照，页面/下载传入同一上下文。
2. 时间：显式 from/to 优先；均空且本报告 basis 明确 FULL/全量不受时间窗限制时显示全量说明。历史依据不足时显示“未记录时间范围”，单边空值明确缺失。不从当前任务默认 FULL 或执行时间倒推历史报告；来源覆盖范围仍独立展示，不把局部 ELK 窗口当整体窗口。
3. 名称：从既有业务线范围/字典获取 code→name；已有“名称（编码）”不重复拼接，名称缺失只保留原编码。
4. 结论：assessment 独立状态标签，正文按自然段与明确枚举显示；现有完整结论不自动改写。新报告生成提示要求 conclusion 先给短结论，再按重点问题/待核实项分行，具体样例和长解释归 checks/findings/evidence，禁止重复完整明细。CLI/API 共用规则或同步约束。
5. 状态：PASS 绿色、FAIL 红色、PARTIAL 橙色、NOT_VERIFIED 灰色、NOT_APPLICABLE 中性；保留明确文字与状态释义，不能仅靠颜色。其他结果/核验/处理状态同样使用固定映射，不改变原判定。
6. 全文块渲染：对说明类字段支持原有换行、明确序列 1./1、/（1）/①/第一等及叙述性分号；按情况/例子输出列表或独立段落。短标题、编号、仓库、locator 等标识字段不统一拆分；引号/括号/代码片段内标点保护，单个模糊分隔符不强行推断结构。保留原顺序、数值及“线索非定论”等限制语义，不生成新业务结论。
7. 渲染安全：只拼接受控标签和样式类，文本仍转义；模型输出 HTML 不执行。不引入新依赖，不调用模型改写已存报告。

### 验证与回滚
- 用脱敏样本覆盖 FULL 空时间、显式时间、真正缺失/单边缺失、业务线字典及未知编码、五种检查状态、分号/序号/嵌套说明和恶意 HTML、含 SQL/URL/小数/时间的反例。
- 前端既有测试、类型检查/构建，提示词定向验证；浏览器宽/窄屏及下载一致性，无法连接须标未验证。
- 只回退本轮渲染/提示词改动即可；无数据库迁移、数据回填或运行配置修改。
- 实施完成后更新工作台现状/迭代日志和必要的前端架构说明，区分实现、验证及部署。
