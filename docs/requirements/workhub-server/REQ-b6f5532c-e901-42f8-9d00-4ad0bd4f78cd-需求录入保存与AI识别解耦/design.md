# Design：需求录入保存与 AI 识别解耦

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-b6f5532c-e901-42f8-9d00-4ad0bd4f78cd |
| 对应 Proposal | 本目录 proposal.md，v1 |
| 状态 | v1，用户已统一确认；实施完成、自动化已验证，运行联调待验证 |
| 现状核对 | 2026-10-08；server codex/dev-20260820，web codex/dev-20260707；本地代码只读核查 |

## 1. 现状与证据

- `workhub-service/.../intake/IntakeService.java#createUploaded`：`@Transactional`；创建主记录，保存文件，调用 scheduleUploadedEnrichment，返回详情。录入时规则抽取是本地处理，不调用 AI。
- `IntakeEnrichmentService#scheduleUploadedEnrichment`：保存 PENDING，注册 afterCommit 回调后通过 Executor.execute 提交；回调缺少拒绝处理。无同步事务时直接提交。
- `IntakeEnrichmentService#enrichUploadedIntake`：先写 RUNNING，再进入 try；Codex 返回失败时保存 FAILED，抛异常时捕获并保存失败状态。原文和附件无删除逻辑。
- `IntakeService#retryEnrichment` 与 `IntakeController`：`POST /api/intake/{id}/enrichment/retry`，只允许 FAILED，记录历史并提交同一记录的后台识别；权限 `intake:ai:operate`。
- `workhub-web/src/views/intake/IntakeListView.vue`：submitCreate 等待上传接口后提示成功并加载列表；列表与详情已有失败重试按钮。`loadIntakeRecords` 自己捕获加载异常。
- `workhub-web/src/api/intake.ts#createUploadIntake` 已将表单 projectGroup 转为 businessLineCode；本次不重复修改该适配。
- `IntakeDemandStatusRules` 将识别进度与业务生命周期区分，失败不自动进入正式业务阶段；本次保持。
- 未核实运行数据库中的具体记录、实际执行器容量与当前故障发生时的环境。任务拒绝是代码可确定的未处理边界，不作为该次 Codex 报错的已证实根因。

## 2. 架构与执行方案

流程：校验与原始材料保存 → 保存 PENDING → 数据库事务提交 → 提交异步识别 → RUNNING → SUCCEEDED 或 FAILED → 用户按需重试同一记录。

| 模块 | 职责与改动 |
| --- | --- |
| IntakeService | 保留主记录、附件、识别待处理状态的事务与现有失败重试规则；核实上传路径不直接执行 AI |
| IntakeEnrichmentService | 两个 Executor.execute 入口复用同一提交处理，捕获 RejectedExecutionException；把后台任务开始阶段纳入现有失败处理；识别与提交故障只影响识别状态 |
| Spring 事务 | afterCommit 只在成功提交后触发；提交拒绝后的失败状态写入使用独立事务 |
| IntakeListView | 成功提示明确“需求已保存，AI 将在后台识别，失败后可重新识别”；保留重试入口与识别状态展示 |

### 任务提交故障

- 只处理有明确依据的执行器拒绝异常，使用标准 RejectedExecutionException（含 Spring 拒绝异常的兼容类型）；不捕获所有保存异常来冒充成功。
- 提交被拒绝时，不运行 AI，不改变主记录和附件，写 FAILED、失败摘要和更新时间。摘要说明后台识别任务未能提交、可稍后重试。
- afterCommit 回调中原事务资源可能尚未释放；失败状态不能依赖原事务提交。使用现有事务管理器及 REQUIRES_NEW（例如 TransactionTemplate）独立保存 FAILED，确保回调结束后数据库可见。
- 写失败状态时保持现有业务状态规则，不重新创建需求、不自动进入业务阶段。
- 如果保存已经提交而失败状态回写本身遇到数据库故障，保留已保存结果，记录带 intakeId 的错误日志，不宣称状态回写成功。该复合故障不承诺立即可重试；后续人工核查，不新增全量异常吞噬或自动恢复机制。
- 后台执行起始的查找记录和 RUNNING 写入纳入现有 try 范围；识别异常复用独立事务的 FAILED 回写处理。持续数据库不可用时不能保证状态落盘，明确记录日志。

## 3. 契约与数据模型

- 上传接口 `POST /api/intake/upload`、重试接口及 IntakeDetailResponse 不新增字段，继续返回原需求 ID。
- 状态沿用 PENDING、RUNNING、SUCCEEDED、FAILED；不增加业务生命周期状态。
- 原始内容、录入人、研发负责人、业务线、上传文件与附件关联在保存阶段持久化；AI 仅增强结构化内容。
- 重试读取已保存记录与材料；PENDING/RUNNING 不通过普通重试创建重复任务。已有 FAILED 检查的并发竞争不在本次扩展为新的调度系统。
- afterCommit 回调拒绝发生在方法返回详情构造后，响应快照可能仍为 PENDING；以随后列表/详情中的持久化状态为准，成功提示不承诺 AI 已经启动。
- 不迁移旧数据，不自动重置历史 PENDING/RUNNING；不发起真实数据库修改来修复本次未核实的记录。

## 4. 非功能与失败边界

- 权限沿用 `intake:record:create`、`intake:ai:operate`，AI 不能绕过人工确认创建正式工作项。
- 请求不等待 AI 完成，不在请求线程上兜底执行 AI。
- 保存业务校验、附件存储与数据库提交失败仍显式报错。数据库已提交后的可选 AI 提交失败不覆盖保存成功结果。
- 故障日志带需求 ID 和故障类别；不新增原文、密钥和附件内容的日志输出。
- 本期不保证进程退出后后台队列自动恢复，不引入自动重试或新依赖。

## 5. 验证、交付与回滚

- 用可控执行器、模拟 AI 结果与 Spring 事务支持测试：提交前未提交任务、提交后启动、回滚不启动、提交拒绝后的独立 FAILED 持久化、AI 失败不删除材料、同 ID 重试及真实保存失败。
- 事务测试须验证 REQUIRES_NEW 边界及已保存数据可见性；单纯核对 Mapper 调用顺序不能代表事务验证完成。如现有测试设施不能支持数据库验证，记录范围并保留人工数据库联调项，不安装新依赖。
- 回归 IntakeServiceTest、IntakeEnrichmentServiceTest、相关 Controller 与结构化抽取测试；按变更增补有意义的用例。
- 前端使用既有依赖执行构建；页面联调由用户运行的应用提供环境，不自行启动。未联调不得标记联调通过。
- 后端与前端可分别更新；无数据库变更。回滚仅还原本次代码/文案，不删除已录入需求、材料或修改工作区无关内容。
- 实施与验证完成后，更新 WorkHub 系统设计、迭代日志和 tasks 的实际结果；本稿不写为已生效事实。

## 6. 一致性与确认

Proposal AC-01/03/06 对应事务与提交边界；AC-02/04 对应失败状态和同记录重试；AC-05 对应页面保存反馈。

用户于 2026-10-08 在本次聊天回复“执行吧”，统一确认 proposal/design/tasks v1。已按本方案完成代码、测试和文案修改；实际结果见 tasks.md，运行数据库与页面联调尚未验证。
