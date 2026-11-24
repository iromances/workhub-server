# Tasks：数字员工任务目标与验收标准改为可选

## 1. 元信息

- 需求编码：REQ-4252cd6c-c305-4b45-8ac6-1d05044f82ab
- 需求名称与唯一需求目录：数字员工任务目标与验收标准改为可选；docs/业务线/BL000012-工作台/需求迭代/REQ-4252cd6c-c305-4b45-8ac6-1d05044f82ab-数字员工任务目标与验收可选/
- 涉及仓库与功能域：workhub-server（model/service）；workhub-web（agent 任务表单）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：草案 v1
- Design/契约版本：草案 v1
- 目标环境与分支：workhub-server codex/development-20261102；workhub-web codex/development-20261103（实施时核对）
- 任务计划状态：规格已确认并实施完成
- 负责人/评审人：待确认

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 后端规格可选+归一化+单测 | 规格confirm | 无 | §2/AC-01、AC-03、AC-04 | 已完成 |
| T-02 | 前端表单必输放宽 | 规格confirm | 与 T-01 并行（契约已定） | §2/AC-02 | 已完成 |
| T-03 | 验证与交付记录 | T-01、T-02 | 无 | §5 | 已完成 |

执行顺序：T-01 → T-02（后端先发）→ T-03。

## 3. 任务明细

### T-01：AgentTaskSpecification 取消两字段必输并归一化

- 描述与目标：objective、acceptanceCriteria 移除 `@NotBlank`；紧凑构造器 null 归一化为 `""`；`@Size` 上限不变。
- 输入：design §1/§2；`AgentTaskSpecification.java`、`AgentWorkbenchServiceTest.java`。
- 输出：模型代码、单测（空规格建任务成功且存储为空串；空目标下删除审计与通知标题不抛错；存量规格读写不变）。
- 允许修改范围：`AgentTaskSpecification.java`、相关测试。
- 明确不修改范围：请求/响应 DTO 结构、service 业务逻辑、数据库。

验收条件：

- [ ] objective/acceptanceCriteria 传 null 或空白创建、更新任务成功，规格存储两字段为 `""`。
- [ ] 既有测试全部通过（含 AC-04 存量兼容）。

计划验证方式（尚未执行）：`./mvnw -pl workhub-service -am test -Dtest=AgentWorkbenchServiceTest`（或全量 agent 相关测试）。

状态及真实结果：已完成（2026-11-16）。改动：`AgentTaskSpecification.java` 移除两处 `@NotBlank`，紧凑构造器将 null/空白归一化为 `""`（@Size 上限不变）；新增测试 `blankObjectiveAndAcceptanceNormalizeToEmptyAndKeepDeleteAuditSafe`（空/空白规格建任务、更新清空、删除审计不抛 NPE）。验证：`mvn -pl workhub-service -am test -Dtest=AgentWorkbenchServiceTest` 68 项 0 失败 0 错误（1 项既有跳过）；`mvn compile` 全模块通过。剩余风险：无。

### T-02：AgentTaskDialog 必输放宽

- 描述与目标：`:50` 提交校验仅保留 expectedArtifacts 必选；两字段去 required 标记，placeholder 注明“可留空，默认按员工职责执行”。
- 输入：design §2 模块表；`workhub-web/src/views/agent/AgentTaskDialog.vue`。
- 输出：表单修改；前端构建通过。
- 前置依赖：契约（空串语义）已确认。
- 明确不修改范围：类型定义、API 层、其他视图。

验收条件：

- [ ] 两字段留空可提交；不选期望产物仍被拦截。
- [ ] 前端构建通过。

计划验证方式（尚未执行）：workhub-web 构建命令 + 表单手工核对路径说明。

状态及真实结果：已完成（2026-11-16，分支 codex/development-20261103）。改动：`AgentTaskDialog.vue` 提交校验仅保留期望产物必选（错误文案改为"请选择至少一种期望产物"），两表单项去 required，placeholder 注明"可留空，默认按员工职责执行"；提交时空值仍 trim 为空串，与后端归一化一致。验证：`npm run build`（vue-tsc 类型检查 + vite 构建）通过。表单留空提交的页面走查待部署后进行。

### T-03：验证与交付记录

- 描述与目标：两端验证证据归档；更新 BL000012 迭代日志与现状.md。
- 输出：tasks.md 证据、迭代日志条目、现状更新。

验收条件：

- [ ] T-01/T-02 证据写入本文件；迭代日志与现状已更新或说明原因。

计划验证方式（尚未执行）：见 T-01/T-02。

状态及真实结果：已完成（2026-11-16）。证据已写入本文件；迭代日志已加条目；现状.md 未描述任务规格字段必输约束，本轮无系统结构变化，故不更新现状（原因说明）。生产生效待部署（后端先发、前端随后）。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01/03/04 → T-01；AC-02 → T-02。
- Design 约束承接：归一化（§2）→ T-01；发布顺序与回滚（§5）→ T-03 记录。
- 未覆盖项及原因：无。
- 完整规格统一确认：范围=proposal v1 + design v1 + tasks v1；用户于 2026-11-16 确认"前后端一起实施"；实施完成后证据见各任务明细。
