# Design：平台内置 Agent 自主迭代一期

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e |
| 需求名称与唯一需求目录 | 平台内置 Agent 自主迭代一期；本目录与 Proposal 相同 |
| 涉及仓库 | workhub-server、workhub-web；职责见下文 |
| 所属项目或业务线 | BL000012-工作台；业务线执行目标取 WorkHub 配置 |
| 对应 Proposal / 状态 | v2 / 草案，待统一确认，尚未实施 |
| 当前事实核对环境、分支、时间 | 本地静态核查，2026-11-03 Asia/Shanghai；server `codex/development-20261102`，web `codex/development-20261103`；工作区均含既有改动，详见核查记录 |

## 1. 现状与证据

- `AiUseCaseDefinitions` 注册 5 个业务场景，`PROVIDER_SMOKE` 另有常量；无实施场景或写能力枚举。
- `DefaultAiGatewayClient.executeCli` 将 `LOCAL_REPOSITORY_READ` 转为 `allowLocalTools=true`；`CodexCliClient.buildCommand` 因此加入关闭沙箱的参数。不能称其为受限只读执行，更不能以此实现新的受限写场景。
- `IntakeService.advanceStage` 处理阶段动作、更新需求并安排后续自动化；`IntakeStageActionRequest` 无规格版本/哈希。当前阶段记录不能证明用户批准了哪一版代码实施计划。
- `WorkItemEntity` 无执行方式和仓库计划；`GitlabRepositoryService` 提供组发现、仓库查询和 clone/fetch，无提交/MR 能力。
- `AiUseCaseConfigService` 支持场景配置；并发与保留期不是现有 AI 场景字段，需单独的执行设置契约。
- `ProjectKnowledgeBaseService` 已有需求归档模式；业务线与平台需求共用 `docs/业务线/`，必须解析既有唯一目录，不能重新拼目录制造副本。
- 当前机器 `codex exec --help` 支持 `--sandbox workspace-write`、`--ephemeral`、`--ignore-user-config` 等；仅核对帮助与版本，未运行 Agent。CLI 沙箱限制命令写入与网络，不等于已隔离宿主机上的敏感文件读取。采用隔离主机账户/外层访问控制需先检查现成条件，不自行创建账户或安装设施。
- MCP 的业务线上下文与需求检索均因数据库连接失败，GitLab 项目映射、运行主机和部署状态未确认。完整路径和来源见 [核查记录](核查记录-20261103.md)。

## 2. 系统架构

### 正常流程

```mermaid
flowchart LR
    A[需求澄清与研发拆解] --> B[选择仓库并起草执行计划]
    B --> C[人工确认规格快照]
    C --> D[事务内持久化排队]
    D --> E[隔离工作区内实施]
    E --> F[平台执行已确认验收命令]
    F -->|失败且修复次数未耗尽| E
    F -->|通过| G[平台提交分支并创建 MR]
    F -->|无法通过或缺依赖| H[失败或待处理]
    G --> I[人工审阅合并与阶段推进]
```

这里只新增 Agent 执行生命周期，不改变需求和工作项原有业务状态。规格确认仍使用“确认设计完成”入口，界面增加对执行计划快照的审阅；开发中的补充或变更复用同一规格确认界面，不新增一层业务审批。

| 模块 | 职责 | 边界 |
| --- | --- | --- |
| AgentPlanService | 仓库映射、规格快照、批准与失效判断 | 不从需求标题猜仓库，不用旧批准覆盖新计划 |
| AgentExecutionService | 入队、权限与归属校验、状态条件更新、重试、取消 | 不执行模型或远端命令 |
| AgentExecutionEngine | 单持有者调度、并发上限、租约、时限与恢复 | 只领取持久化的已确认任务 |
| AgentWorkspaceManager | 指定基线的专用克隆、目录检查、保留与清理 | 不复用用户工作区；不向 Agent 暴露交付凭据 |
| AgentRunner / AgentVerifier | 受限 CLI 实施、进程树管理、可信验收命令及证据 | 不推送、建 MR、部署或安装依赖 |
| GitlabDeliveryService | 在受控交付目录生成提交、推送执行分支、创建/核对 MR | 不执行模型返回的 git/shell 命令，不合并 MR |
| AgentReportService | 原始证据归档与人工备注版本 | 不修改验收退出码或原始日志 |
| workhub-web | 执行计划、规格确认、运行详情、结果与设置页面 | 服务端负责权限、状态、目标与校验 |

### 生命周期和时效

- 主路径：`QUEUED → RUNNING → VERIFYING → DELIVERING → DELIVERED`。
- 验证失败可 `VERIFYING → RUNNING`，最多 2 轮平台发起的修复；初次实施、修复、验证共用总时限，默认 3600 秒，上限 14400 秒。
- 缺缓存/配置/规格或隔离能力为 `BLOCKED`；明确执行错误为 `FAILED`；运行进程状态无法确认或实例中断为 `INTERRUPTED`。失败阶段与原因单独记录。
- `QUEUED → CANCELLED`；`RUNNING/VERIFYING → CANCELLING → CANCELLED`。只有确认该任务进程树停止后才写 CANCELLED；停止失败保持可见，不清理现场。
- `DELIVERING` 进入前以条件更新获取交付权；已进入该状态时取消返回冲突，提示先核对远端结果。取消成功的记录绝不能再取得交付权。已创建的 MR 不因取消被自动删除。
- 修复、重试都不修改需求状态。DELIVERED 表示分支/MR 已就绪，仍须人工交付确认。

## 3. 契约与数据模型

### 3.1 执行计划和规格确认

每个工作项一份可修订计划；每个版本独立保存：

- 归属：`intakeId`、`workItemId`、`projectId`、`businessLineCode`、`gitlabProjectId`、`repositoryPath`；由服务端核对关系。
- 目标：`targetEnvironmentCode` 必须属于业务线支持环境；`executionLocation=LOCAL_SANDBOX` 单独表示仅在本地实施测试，不代表向该环境部署。
- 代码：显式 `baseBranch`、查询得到的 `baseCommitSha`、`targetBranch`；页面可建议仓库默认分支，但必须展示并确认，已有需求研发分支不得静默替换。执行分支由平台生成并绑定计划，不能提交任意 refspec。
- 规格：Proposal/Design/Tasks 的实际内容快照与 SHA-256、验收标准、允许修改范围、前置工作项和依赖结论。原件路径须位于已核实的唯一需求目录；规格内容不由模型自述批准。
- 验证：命名命令清单，每项为已确认的可执行文件和参数数组、工作目录相对路径、是否必需、超时；禁止前端传入 shell 拼接串后直接执行。命令使用现有工具与缓存，不下载依赖。
- 版本：`version`、`planHash`、`DRAFT/CONFIRMED/STALE`、确认人、确认时间；确认对象包含完整上述内容。执行参数与工具策略在发起时另存实际生效快照。

“确认设计完成”的同一数据库事务中，锁定需求、相关工作项和待确认计划，比较客户端预览哈希、当前正式字段与文档内容；任何不一致返回冲突并要求刷新。冻结快照、记录确认人、推进阶段、创建 QUEUED 记录共同提交或回滚。afterCommit 只唤醒调度器，不能承担唯一入队动作。

后续修改规格、工作项内容/归属、仓库、基线、目标或验收命令时旧批准失效。排队前、领取时、开始交付前均检查计划有效、需求处于开发中且未暂停、工作项未终止、依赖已满足及授权有效。中途修改不能替换已经批准的输入；正在运行的旧任务取消/阻断。纯模型参数变化不改规格哈希，但记录到新的执行快照。

开发中首次启用 AGENT 或更改计划，使用规格确认接口确认新版本后才可手动发起；不能只检查“开发中”就跳过确认。已存在未合并 MR 的旧版本须由人处置，再启动替代交付，避免同一工作项多个活动 MR。

### 3.2 数据对象

使用现有 MySQL/Flyway；实施前再核对最新迁移序号，迁移文件落在 `workhub-bootstrap/src/main/resources/db/schema/mysql/`，本轮不执行迁移。

| 对象 | 关键字段与约束 |
| --- | --- |
| `pm_work_item` 新列 | `execution_mode VARCHAR(16) NOT NULL DEFAULT 'HUMAN'`；HUMAN/AGENT；不改变存量语义 |
| `pm_agent_plan` | id、intake_id、work_item_id、version、status、plan_hash CHAR(64)、snapshot_json MEDIUMTEXT、confirmed_by/at、created_by/at；唯一 `(work_item_id, version)`；确认后快照不可覆写 |
| `pm_agent_execution` | id、plan_id、work_item_id、root_execution_id、attempt、status、failure_phase/reason、effective_config_json、base_sha、result_sha、branch_name、workspace_path、verification_json、merge_request_iid/url、report_path、created_by/at、started_at、finished_at、updated_at |
| 执行并发字段 | `status_version`、`owner_token`、`lease_until`、`cancel_requested_at`、`repair_count`；状态与 owner 的条件更新阻止迟到回调覆盖 |
| 执行唯一性 | 使用活动工作项生成列/等价数据库约束，活动状态为 QUEUED/RUNNING/VERIFYING/CANCELLING/DELIVERING/INTERRUPTED，`UNIQUE(active_work_item_id)`；终态为空；重试序号唯一 `(root_execution_id, attempt)`；MySQL 版本与生成列支持在迁移测试时确认 |
| `pm_agent_dispatcher_lease` | 单行调度租约、持有者与递增 fence；只允许一个实例调度，实例内按并发设置运行；多实例任意启动不得把其他持有者任务改失败 |
| 报告修订 | 原始执行证据不可变；人工备注版本与修订人/时间/前版关联持久化，文件写入失败可重试归档，不重跑代码 |
| 执行器设置 | 沿用平台系统配置存储机制新增独立命名空间，包含 enabled（初始 false）、maxConcurrent（默认 1，1–4）、failedRetentionDays（默认 7，1–30）；所有修改走管理权限与审计，不混入 AI 场景模型参数 |

报告修订实现时可采用 `pm_agent_report_revision`（execution_id + version 唯一，content MEDIUMTEXT、operator、created_at）。权限新增 `agent:execution:view`、`agent:execution:operate`、`agent:execution:manage`。权限定义迁移采用幂等插入；角色授权使用实际角色编码，本期建议仅管理员初始启用，研发负责人按既有管理界面授予，不能从名称猜角色或自行授权。

### 3.3 HTTP 契约

复用现有响应封装和错误处理。所有接口验证登录主体、操作权限及对象归属；不存在/不可访问沿用平台规则，状态或快照冲突清楚提示刷新，不返回其他业务线内容或磁盘敏感路径。

| 方法/路径 | 权限 | 请求和结果 |
| --- | --- | --- |
| `PUT /api/work-items/{id}/execution-mode` | operate | HUMAN/AGENT；活动执行期间不能直接切回 HUMAN，先取消；AGENT 本身不授权执行 |
| `PUT /api/work-items/{id}/agent-plan` | operate | 基于 expectedVersion 保存草稿，返回计划 ID、版本、哈希和预览；仓库候选只来自该业务线配置 |
| `GET /api/work-items/{id}/agent-plan` | view | 当前草稿/批准版本与失效原因、计划预览 |
| 既有需求阶段动作 | 既有推进权限 + 涉及 AGENT 时的 operate | ACTION_CONFIRM_DESIGN 可增加 `agentPlans:[{workItemId,planId,planHash}]`；有 AGENT 项时必填并完整匹配；纯 HUMAN 请求保持兼容 |
| `POST /api/agent-plans/{id}/confirm` | operate + 既有设计确认权限 | 开发中新增/变更计划的规格确认；body 含 planHash；确认界面与阶段动作一致，记录历史，不重复推进业务阶段 |
| `POST /api/agent-executions` | operate | `{intakeId,workItemId,planId,planHash}`；只接受有效批准计划；已有相同活动执行返回原记录，其他计划冲突 |
| `GET /api/agent-executions?intakeId=...` | view | 有界分页，默认 20、上限 100，按创建时间与 ID 排序 |
| `GET /api/agent-executions/{id}` | view | 状态、阶段、实际参数、快照摘要、验证证据、MR 与报告；不返回凭据或原始绝对工作区路径 |
| `POST /api/agent-executions/{id}/cancel` | operate | QUEUED/RUNNING/VERIFYING 按状态处理；重复取消幂等；DELIVERING/DELIVERED 返回明确冲突 |
| `POST /api/agent-executions/{id}/retry` | operate | 仅 FAILED/BLOCKED 可重试；重新校验批准与环境，实施失败新建 attempt；交付失败只恢复同一 resultSha，未知远端结果先对账；INTERRUPTED 须先确认进程退出 |
| `POST /api/agent-executions/{id}/report-revisions` | operate | `{baseVersion,comment}`；追加人工修订，不覆盖原始验证证据 |
| `GET/PUT /api/agent-executor/settings` | manage | 读取/调整执行器启停、并发、保留期；新增任务与后续领取使用新值，进行中执行保留快照 |

### 3.4 新 AI 场景与隔离

注册 `workitem.agent.implement`、能力 `LOCAL_REPOSITORY_WRITE`，仅 CLI 通道。引入明确的执行策略参数，区分既有分析模式与新受限写模式，不再用布尔 `allowLocalTools` 混淆权限。新模式：

- `--sandbox workspace-write`，`approval_policy=never`，命令网络关闭；不使用关闭沙箱参数、不删除外层沙箱限制环境变量。
- 只允许平台专用目录及本任务临时目录写入；验证真实路径与符号链接，拒绝用户工作区、共享父目录和任意 `--add-dir`。
- 独立、最小化运行配置；不继承用户的 MCP、插件、浏览器、hooks、凭据助手或外部写工具。应用必要模型认证与交付凭据隔离，兼容已安装 CLI 的功能以预检和测试为准。
- 显式环境变量白名单；不传数据库、SSH、GitLab 或平台管理凭据。工作区内不放生产配置与敏感输入。
- 写沙箱不能保证敏感文件不可读，因此执行主机还需现成的账户/文件访问隔离并通过探针；不满足即 BLOCKED。不能以提示词替代该前置条件，也不自行变更主机账户或权限。
- Agent 所读需求、仓库说明与构建输出属于任务资料，不能更改平台执行策略。已有仓库 hook、子模块、凭据配置不能由交付服务自动执行。

工作区路径使用内部 ID：`data/agent-workspaces/{executionId}`，不直接拼接用户输入的组名。从批准的 baseCommitSha 准备快照；分支不存在或 SHA 不可达明确阻断，不回退到 master/默认分支。

### 3.5 独立验证、交付与凭据

模型输出 schema 包含变更说明、风险、待办、MR 标题/描述建议；测试结论是“模型说明”字段，不是执行成功开关。模型退出后平台保存变更摘要，按批准清单在隔离上下文运行验收命令，收集命令 ID、退出码、超时、日志摘要与哈希。必需命令未执行/失败、输出缺失、越过批准范围均禁止交付。

受控交付目录由平台管理，不接受模型修改其 `.git` 配置。平台将核验过的变更形成提交；验证后检查内容哈希未变，记录 resultSha，禁止未经重新验证的人工修改混入同次交付。交付子进程禁用仓库 hooks、全局凭据助手和未经批准的外部 diff/filter，不复用模型返回的命令。

GitLab 凭据由服务端配置在单次传输上下文提供，不拼在 URL/命令参数，不落 `.git/config` 或 Agent 环境；实现可用隔离的临时凭据通道，但不得在工作区生成含 token 的脚本。进程及异常日志脱敏，网络调用有超时；基础设施缺项需另行授权准备。

源分支只允许平台生成并存档的 `feature/...` 执行分支。默认分支和 GitLab 受保护分支均拒绝推送，不使用 force/mirror/tags；单次交付明确 projectId/sourceBranch/targetBranch/resultSha，向已经确认的目标创建 MR，不调用合并 API。

交付具有稳定键 `(planId, sourceBranch, targetBranch)`。推送已成功但响应丢失时先查远端 SHA；相同则继续，不同则冲突待人处理。MR 超时后按 source/target/计划标识查询；存在则关联，不重复创建。重试交付沿用原 resultSha，不能重新让模型生成另一份代码。

### 3.6 队列、恢复与证据

数据库约束控制单工作项活动执行；CAS 的 QUEUED→RUNNING 与持有者租约控制领取。afterCommit 丢失唤醒时仍由内部队列扫描恢复，不新增业务候选发现定时任务。

取消、超时以及转为 FAILED/BLOCKED 前，先停止任务及后代进程并等待回收，再写终态和释放活动约束；仅凭线程 interrupt 或父进程退出不算已停止。进程注册包含本实例、执行 ID、PID 与启动时间，恢复时不得按过期 PID 误杀其他进程。租约失效的实例不能获得新的交付权；已开始的远端调用可能仍完成，必须先对账后再恢复。

重启只处理本实例遗留或确定过期的租约；RUNNING/VERIFYING 转 INTERRUPTED 并验证进程已停止，随后才可失败重试。DELIVERING 先对账远端；QUEUED 继续按有效批准领取。关闭执行器暂停新发起和领取，保留队列；运行任务按原政策完成或人工取消，不把“关闭”展示为紧急停机。

执行记录是状态权威，原始证据包含规格快照/哈希、基线与结果 SHA、参数、实际验证、失败阶段和外部结果。文件归档在既有唯一需求目录的 `agent执行/` 下，以执行 ID 和修订版本命名；解析目录沿用归档服务，不改变本需求的 SDD 位置。归档失败只重试归档，不重做实施或交付。

成功工作区在证据持久化并确认无人占用后清理；失败/取消工作区默认保留 7 天。INTERRUPTED、未完成远端对账或正在运行的目录不得自动清理；清理基于受管根目录、执行记录和真实路径，拒绝符号链接越界。

## 4. 非功能约束

- 角色按钮权限之外继续检查项目/需求/业务线关系，用户不能通过替换 ID 操作其他对象。系统没有对应对象授权规则时必须补齐，不能默认“所有登录用户可执行”。
- 并发默认 1，范围 1–4；降低上限仅影响后续领取。模型场景超时保留实际值并限制在 60–14400 秒；平台总时限涵盖修复和验证，交付另设有界请求超时。
- 日志、输出与报告设置有限字节上限并标明截断。启动前检查可用空间，空间不足不领取；不靠自动删除用户文件释放空间。
- 审计记录发起、批准、取消、重试、配置修改、交付与报告修订的操作者和对象；不保存明文凭据。
- 本期不引入新的执行基础设施或第三方依赖；如果现有能力不能实现隔离要求，报告缺口，保持关闭，不能降级为无沙箱执行。

## 5. 交付与回滚

1. 实现与离线验证完成后，核对实际数据库版本、迁移顺序和试点环境；准备迁移、发布与回退包供审阅。不能将启动应用作为无副作用测试，因为现有 Flyway 启动会迁移数据库。
2. 先迁移/发布后端，再发布前端；初始功能关闭、不自动绑定角色、不自动开启真实需求。环境操作按具体授权执行。
3. 验证隔离、工具链、离线缓存、GitLab 配置和 WorkHub 自身仓库映射；选择一条平台需求和一条业务线需求形成各自批准计划，再按正常确认流程试点。
4. 异常时停止新领取并取消运行任务、完成远端对账；保留证据、新表和默认 HUMAN 字段。代码回滚不自动 DROP 表、不自动删除远端分支/MR。
5. 实际核实后更新业务线现状与迭代日志，分别记录“已实现/已测试/已部署/已验收”。本轮仅规格修改，不把设计写成已生效能力。

## 6. 一致性与待确认

- AC-01/11 → §3.1–3.3；AC-02 → §3.4；AC-03/10 → §3.5；AC-04 → §3.2/4；AC-05/06/07 → §3.6；AC-08/09 → §5 与 Tasks。
- 延续一期平台内置 Agent、两个人工确认点、分支/MR、本地实现测试和业务线通用方向；v2 补足可执行边界、输入版本与验收，未引入自动合并/部署。
- v1 未决细节采用提议默认值：MR 后人工推进、失败工作区 7 天、初始仅管理员可操作。均随 v2 一次确认，不另拆审批；运行时授予权限仍核实实际角色。
- 待用户说明：长期目标是否包括主动提出改进需求；不把未答复视作同意增加定时任务。
- 实施前需统一确认 Proposal/Design/Tasks v2；运行前待核实业务线映射、部署主机/环境、权限、预备缓存与真实试点需求。本地编码与这些运行操作分阶段验收。
- [proposal.md](proposal.md) · [tasks.md](tasks.md) · [v1 历史](历史版本/v1-20261103/design.md)。
