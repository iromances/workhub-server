# WorkHub Controller 接口说明

本文档按 controller 层说明当前已暴露给前端和外部系统的 HTTP 接口、主要用途与关键请求参数。

## 1. 认证接口

控制器：`AuthController`

- `POST /api/auth/login`
  用途：用户名密码登录，返回 JWT 和当前用户信息。
  请求体：`LoginRequest`
- `GET /api/auth/me`
  用途：获取当前登录用户资料。
  鉴权：需要 JWT

## 2. 系统接口

控制器：`SystemController`、`SysConfigController`

- `GET /api/system/ping`
  用途：系统健康检查。
  鉴权：匿名可访问
- `GET /api/system/configs`
  用途：查询系统配置列表。
  查询参数：`configGroup`、`keyword`
  返回说明：`SECRET` 类型配置只返回脱敏值，不返回明文。
- `GET /api/system/developer-options`
  用途：查询研发人员资源候选项，供研发任务负责人下拉框使用。
- `GET /api/system/developers`
  用途：查询研发人员资源列表。
  查询参数：`keyword`
- `POST /api/system/developers`
  用途：新增研发人员资源。
- `PUT /api/system/developers/{id}`
  用途：更新研发人员资源。
- `DELETE /api/system/developers/{id}`
  用途：删除研发人员资源。
- `POST /api/system/configs`
  用途：新增系统配置。
  请求体：`SysConfigSaveRequest`
  说明：`valueType` 支持 `TEXT` 和 `SECRET`；`SECRET` 会应用层加密落库。
- `PUT /api/system/configs/{id}`
  用途：更新系统配置。
  请求体：`SysConfigSaveRequest`
  说明：更新 `SECRET` 配置时，如果 `value` 为空则保留旧秘钥。

## 2.1 MCP 运维通道接口

控制器：`McpResourceController`

- `GET /api/mcp/resources`
  用途：查询 MCP 只读运维资源配置。
  查询参数：`resourceType` 可选，取值 `DATABASE/SERVER`；`businessLineCode`、`environmentCode`、`keyword`、`enabledOnly`
  返回说明：返回数据库目标、服务器目标、绑定业务线列表 `businessLineCodes`、白名单、权限 profile 和启用状态；敏感值不回显，只返回是否已配置。
- `POST /api/mcp/resources`
  用途：新增 MCP 资源配置。
  请求体：`McpResourceSaveRequest`
  说明：业务线通过 `businessLineCodes` 绑定，支持一个数据库或服务器目标绑定多个业务线；兼容字段 `businessLineCode` 表示首个业务线。数据库资源必须填写 `username`、`password`，`databaseSchema` 非必填；服务器资源必须填写 `systemNames`、`username`，并填写 `sshPassword` 或 `sshIdentityFile`，兼容字段 `systemName` 表示首个系统。如配置堡垒机，需填写 `sshBastionHost`、`sshBastionUser`，并填写 `sshBastionPassword` 或 `sshIdentityFile`。密码入库前由服务端加密。
- `PUT /api/mcp/resources/{id}`
  用途：更新 MCP 资源配置。
  请求体：`McpResourceSaveRequest`。编辑时密码字段留空表示不修改已有加密密码。
- `DELETE /api/mcp/resources/{id}`
  用途：删除 MCP 资源配置。
- `GET /api/mcp/catalog`
  用途：按已启用资源生成 MCP Server 可读取的 catalog JSON 结构。
  返回说明：包含 `businessLines`、`environments`、`databaseTargets`、`serverTargets`、`knowledge`、`gitlab`。业务线摘要包含 `gitlabGroupName`、业务线系统清单 `involvedSystems` 和全局中台系统清单 `globalSystems`；GitLab 摘要只返回 `webApiUrl`、`sshHost`、`accessTokenConfigured`，不返回 Access Token。
- `POST /api/mcp/runtime`
  用途：主服务内嵌 HTTP MCP runtime，接收 MCP JSON-RPC 请求并返回原始 JSON-RPC 响应。
  说明：支持 `initialize`、`tools/list`、`tools/call`。当前暴露 `list_mcp_targets`、`get_business_line_context`、`get_knowledge_context`、`run_readonly_query`、`get_service_status`、`read_service_logs`。服务器访问只允许读取页面配置的服务白名单和日志路径白名单，不暴露任意 SSH 命令、库结构读取或 `EXPLAIN` 工具。工具调用直接读取当前已启用的 MCP 资源配置，资源配置变更后下一次调用生效，不依赖本地 catalog 文件。GitLab Access Token 只在服务端需要访问 GitLab 时内部加载使用，不进入 MCP 返回结果。
- `GET /api/mcp/audits`
  用途：读取 MCP runtime 写入的 JSONL 审计日志。
  查询参数：`limit`，默认 `100`，最大 `500`
  说明：读取路径来自环境变量 `WORKHUB_MCP_AUDIT_LOG`，未配置时使用 `logs/mcp-audit.jsonl`。

## 2.2 运维监测接口

- `GET /api/ops/monitors`
  用途：查询业务线运维监测配置。
  查询参数：`monitorType`、`businessLineCode`、`environmentCode`、`keyword`、`enabledOnly`
- `POST /api/ops/monitors`
  用途：新增运维监测配置。
  说明：当前支持 `XXL_JOB`，需要配置业务线、环境和该 DBServer 上的 XXL-JOB 数据库名；多个业务线共用同一个 XXL-JOB 库时，可配置 `executorAppName` 作为关注执行器，多个执行器用英文逗号分隔。配置后统计、详情和告警只关注这些执行器；未配置时默认关注该业务线 XXL-JOB 库下所有执行器。数据库访问复用对应业务线环境的 MCP 只读数据库目标。`MQ` 菜单入口已预留，采集下一步接入。
- `PUT /api/ops/monitors/{id}`
  用途：更新运维监测配置。
- `DELETE /api/ops/monitors/{id}`
  用途：删除运维监测配置。
- `POST /api/ops/monitors/{id}/check`
  用途：手动触发一次真实采集。
  说明：`XXL_JOB` 会通过 MCP 只读数据库通道读取 `xxl_job_group`、`xxl_job_info`、`xxl_job_log_report` 和最近失败的 `xxl_job_log`，统计运行情况并定位失败任务；只做读取，不触发任务、不修改 XXL-JOB 配置。
- `GET /api/ops/monitors/xxl-job/dashboard`
  用途：查询已配置业务线 XXL-JOB 运行统计，列表不返回失败任务明细。
  查询参数：`businessLineCode`、`environmentCode`、`enabledOnly`
  返回说明：包含 `jobCount`、`executorCount`、`triggerCount`、`triggerSuccessCount`、`triggerFailedCount`、`triggerRunningCount`、`reportUpdatedAt` 等字段。未配置关注执行器时读取 XXL-JOB Admin 原生 `xxl_job_log_report` 快照；配置关注执行器时，因原生快照不带执行器维度，按 `xxl_job_log`、`xxl_job_info`、`xxl_job_group` 重新聚合关注执行器范围内的数据。
- `GET /api/ops/monitors/xxl-job/executors`
  用途：按业务线、环境和 XXL-JOB 数据库名查询可关注执行器下拉选项。
  查询参数：`businessLineCode`、`environmentCode`、`xxlJobDatabaseName`
  返回说明：读取对应 XXL-JOB 库的 `xxl_job_group`，返回 `appName` 和 `title`；仅用于页面选择，不修改 XXL-JOB 配置。
- `GET /api/ops/monitors/{id}/xxl-job/detail`
  用途：查询单条 XXL-JOB 监测详情。
  查询参数：`startDate`、`endDate`，格式 `yyyy-MM-dd`；未传时默认查询最近一个月。
  说明：详情默认按日期区间直接读取 XXL-JOB 原始 `xxl_job_log` 全部执行日志，可切换为只看失败日志；如监测配置了 `executorAppName` 关注执行器，明细和告警只返回这些执行器范围内的日志；未配置时默认查询全部执行器。详情支持分页、负责人和执行器模糊查询。
- `GET /api/ops/system-alerts`
  用途：查询系统预警本地错误事件看板。
  查询参数：`businessLineCode`、`environmentCode`、`serviceName`、`level`、`startTime`、`endTime`、`page`、`pageSize`
  说明：当前只查询 WorkHub 本地表 `ops_system_alert_event`，默认查询最近 1 小时 `ERROR` 事件；后续 ELK 接入只负责把错误日志同步或写入本地事件表，不改变该查询接口。
- `GET /api/ops/system-alerts/subsystems`
  用途：查询系统预警单独维护的关注子系统清单。
  查询参数：`businessLineCode`、`environmentCode`、`enabledOnly`、`keyword`
  说明：系统预警关注子系统独立于项目涉及系统和 XXL-JOB 执行器配置，不复用其他菜单配置。
- `POST /api/ops/system-alerts/subsystems`
  用途：新增系统预警关注子系统。
- `PUT /api/ops/system-alerts/subsystems/{id}`
  用途：更新系统预警关注子系统。
- `DELETE /api/ops/system-alerts/subsystems/{id}`
  用途：删除系统预警关注子系统。

## 2.3 站内信接口

- `GET /api/notifications`
  用途：查询当前登录用户站内信列表。
  查询参数：`limit`，默认 `50`，最大 `100`
- `GET /api/notifications/unread-count`
  用途：查询当前登录用户未读站内信数量。
- `POST /api/notifications/{id}/read`
  用途：将当前登录用户的一条站内信标记为已读。
- `POST /api/notifications/read-all`
  用途：将当前登录用户全部未读站内信标记为已读。

## 3. 项目接口

控制器：`ProjectController`

- `GET /api/projects`
  用途：查询项目列表。
  查询参数：`status`、`keyword`、`businessLine`、`page`、`pageSize`；未传 `page/pageSize` 时保持返回全量列表，传入后返回分页数据。
  返回说明：项目摘要包含项目编码 `code`、项目名称 `name` 和业务线字段 `businessLine`；`keyword` 支持匹配项目编码/名称和业务线。
- `GET /api/projects/{id}`
  用途：查询项目详情。
- `POST /api/projects`
  用途：新建项目。
  请求体：`ProjectSaveRequest`，必填字段包含 `code`、`name`、`type`、`businessLine`、`ownerUserName`、`status`
- `PUT /api/projects/{id}`
  用途：更新项目。
  请求体：`ProjectSaveRequest`，必填字段包含 `code`、`name`、`type`、`businessLine`、`ownerUserName`、`status`
- `DELETE /api/projects/{id}`
  用途：删除项目；如果项目已被工作项、迭代、版本、支付绑定或研发评估记录引用，则拒绝删除。
- `GET /api/projects/business-lines`
  用途：查询业务线列表。
  查询参数：`keyword`、`page`、`pageSize`；未传 `page/pageSize` 时保持返回全量列表，传入后返回分页数据。
  返回说明：返回业务线名称、GitLab 组名或命名空间、说明和启用状态。
- `POST /api/projects/business-lines`
  用途：新增业务线。
  请求体：`BusinessLineSaveRequest`，必填字段为 `businessLineName`，可选字段包含 `gitlabGroupName`、`description`、`enabled`
- `PUT /api/projects/business-lines/{id}`
  用途：更新业务线。
  请求体：`BusinessLineSaveRequest`
- `DELETE /api/projects/business-lines/{id}`
  用途：删除业务线；如果业务线已被项目、业务线成员或业务线涉及系统引用，则拒绝删除。
- `POST /api/projects/business-lines/{id}/involved-systems/sync-git`
  用途：按业务线维护的 GitLab 组名同步研发涉及系统清单。
  行为说明：读取该 GitLab group 及子组下的可访问项目仓库，把仓库名称作为业务线系统补齐到 `pm_project_involved_system`；仅新增缺失项，不覆盖已有手工维护项或停用项。前置条件为业务线已启用且已维护 `gitlabGroupName`，系统配置存在 `gitlab.global/webApiUrl` 和 `gitlab.global/accessToken`。
- `GET /api/projects/involved-systems`
  用途：查询研发涉及系统清单。
  查询参数：`systemScope` 可选，取值 `BUSINESS_LINE/MIDDLE_PLATFORM`；`businessLine` 可选；`enabledOnly` 可选；`keyword` 可选。
  返回说明：返回系统范围、业务线、系统名称、说明、启用状态和排序。
- `GET /api/projects/involved-systems/selectable`
  用途：查询某业务线研发任务可选择的涉及系统。
  查询参数：`businessLine` 必填。
  返回说明：返回启用中的当前业务线系统和启用中的全局中台系统。
- `POST /api/projects/involved-systems`
  用途：新增研发涉及系统。
  请求体：`systemScope`、`businessLine`、`systemName`、`description`、`enabled`、`sortOrder`；业务线系统必须传 `businessLine`，中台系统忽略 `businessLine`。
- `PUT /api/projects/involved-systems/{id}`
  用途：更新研发涉及系统。
  行为说明：系统名称已被研发任务使用时不能改名；可通过 `enabled=false` 停用。
- `DELETE /api/projects/involved-systems/{id}`
  用途：删除研发涉及系统。
  行为说明：系统已被研发任务使用时拒绝删除，需改为停用。

## 4. 迭代接口

控制器：`SprintController`

- `GET /api/sprints`
  用途：查询迭代列表。
  查询参数：`projectId`、`status`
- `GET /api/sprints/{id}`
  用途：查询迭代详情。
- `POST /api/sprints`
  用途：新建迭代。
  请求体：`SprintSaveRequest`
- `PUT /api/sprints/{id}`
  用途：更新迭代。
  请求体：`SprintSaveRequest`

## 5. 版本接口

控制器：`ReleaseController`

- `GET /api/releases`
  用途：查询版本列表。
  查询参数：`projectId`、`status`
- `GET /api/releases/{id}`
  用途：查询版本详情。
- `POST /api/releases`
  用途：新建版本。
  请求体：`ReleaseSaveRequest`
- `PUT /api/releases/{id}`
  用途：更新版本。
  请求体：`ReleaseSaveRequest`

## 6. 工作项接口

控制器：`WorkItemController`

- `GET /api/work-items`
  用途：查询工作项列表。
  查询参数：`projectId`、`status`、`type`、`keyword`
- `GET /api/work-items/{id}`
  用途：查询工作项详情。
  返回补充：暂停中的工作项返回 `pausePreviousStatus`、`pauseReason`、`pauseDate`，用于展示暂停前状态、暂停原因和暂停日期。
- `POST /api/work-items`
  用途：新建工作项。
  请求体：`WorkItemCreateRequest`
- `PUT /api/work-items/{id}`
  用途：更新工作项基础信息。
  请求体：`WorkItemUpdateRequest`
- `POST /api/work-items/{id}/assign`
  用途：指派负责人和跟进人。
  请求体：`WorkItemAssignRequest`
- `GET /api/work-items/{id}/follow-ups`
  用途：查询工作项跟踪记录。
- `POST /api/work-items/{id}/follow-ups`
  用途：新增工作项跟踪记录。
  请求体：`WorkItemFollowUpRequest`
- `GET /api/work-items/{id}/transitions`
  用途：查询工作项状态流转日志。
- `POST /api/work-items/{id}/transitions`
  用途：执行状态流转。
  请求体：`WorkItemTransitionRequest`
  行为说明：暂停工作项时传 `toStatus=已暂停`，必须填写 `reason` 和 `pauseDate`；系统保存暂停前状态。恢复时仍调用该接口，`toStatus` 必须等于 `pausePreviousStatus`，恢复后清空当前暂停元数据并写入流转日志。

## 7. 需求管理接口

控制器：`IntakeController`

- `GET /api/intake`
  用途：查询需求管理列表。
  兼容路径：`/api/intake-records`
  查询参数：`status`、`requirementName`、`demandStatus`、`releasedStartDate`、`releasedEndDate`
  返回补充：`demandStatus` 仅在识别成功后才进入业务生命周期；研发需求取值为 `已收录/待澄清/待评估/待排期/待设计/开发中/测试中/待验收/待上线/已暂停/已完成/终止关闭`，数据提取/运维取值为 `已收录/待澄清/待处理/处理中/待验收/已暂停/已完成/终止关闭`；识别前请看 `enrichmentStatus`
- `GET /api/intake/{id}`
  用途：查询单条需求详情，包括结构化字段、附件、需求阶段状态、修改历史、enrichment 状态、研发分支名和关联禅道地址。
  查询参数：`recordView=true|false`，默认 `true`
  返回补充：返回 `todos` 需求待办列表，包含待办标题、内容、处理状态、处理人、计划处理时间、完成时间和处理结果；暂停中的需求返回 `pausePreviousDemandStatus`、`pauseReason`、`pauseDate`。
- `POST /api/intake/{id}/pause`
  用途：暂停需求。
  请求体：`IntakePauseRequest`
  行为说明：任一未到 `已完成/终止关闭/已暂停` 的需求可暂停；必须填写 `reason` 和 `pauseDate`，后端保存暂停前需求状态并写入修改历史。
- `POST /api/intake/{id}/resume`
  用途：恢复暂停中的需求。
  行为说明：仅 `已暂停` 需求可恢复；系统只能恢复到 `pausePreviousDemandStatus`，恢复后清空当前暂停元数据并写入修改历史。
- `POST /api/intake/{id}/requirement-folder/open`
  用途：创建并打开需求本地文件夹。
  行为说明：基础目录读取系统配置 `intake.requirementFolder.basePath`，默认 `/Users/aslight/Desktop/进行中的需求`；最终目录按 `审批编号-需求名称` 命名，审批编号缺失时回退到需求 ID，需求名称缺失时回退为 `未命名需求`。
- `POST /api/intake/{id}/development-plan-folder/open`
  用途：创建并打开知识库开发方案文件夹。
  行为说明：基础目录读取系统配置 `knowledge.project.vaultPath`；最终目录按 `wiki/projects/{业务线}/需求迭代/{项目名称}/{年份}` 生成，需求迭代 Markdown 文件命名为 `{审批编号}-{需求名称}.md`。
- `DELETE /api/intake/{id}`
  用途：逻辑删除单条需求。
  行为说明：仅更新 `pm_intake_record` 主表的 `deleted/deleted_at/deleted_by`；附件、修改历史和本地文件保留不动。
- `POST /api/intake/upload`
  用途：通过需求截图和需求附件录入待整理需求。
  请求方式：`multipart/form-data`
  表单字段：`senderName`、`developmentOwnerUserName`、`sourceChannel`、`rawContent`、`businessLine`
  文件字段：`screenshots`、`attachments`
  补充说明：`developmentOwnerUserName` 必填，单独保存到 `pm_intake_record.development_owner_user_name`；`businessLine` 会写入原始录入内容并作为结构化字段 `projectHint` 的优先来源，便于后续研发需求任务评估匹配项目。
  返回说明：上传成功即返回，Codex 结构化识别在后台异步执行；识别完成后会自动归类 `研发需求/数据提取/运维`，其中 `研发需求` 会生成研发分支名。
- `POST /api/intake/{id}/enrichment/retry`
  用途：对识别失败的需求重新触发截图和附件结构化识别。
  行为说明：仅当 `enrichmentStatus=FAILED` 时允许调用；接口会记录“重试需求识别”修改历史，将识别状态重新置为 `PENDING/RUNNING` 并异步执行，不直接创建正式工作项。
- `POST /api/intake/{id}/attachments`
  用途：为已录入需求补充上传需求截图和需求附件。
  请求方式：`multipart/form-data`
  文件字段：`screenshots`、`attachments`
  行为说明：只追加附件并记录修改历史，不自动覆盖已识别结构化字段；后续 SQL 草稿和研发任务评估会读取最新附件清单。
- `POST /api/intake/{id}/attachments/{attachmentId}/replace`
  用途：替换单条需求截图或需求附件。
  请求方式：`multipart/form-data`
  文件字段：`file`
  行为说明：保留原附件类型和附件 ID，只替换文件内容、文件名、存储路径和内容类型；替换时会移除旧文件对应的附件正文摘要并记录修改历史。
- `DELETE /api/intake/{id}/attachments/{attachmentId}`
  用途：删除单条需求截图或需求附件。
  行为说明：仅允许删除原始需求材料类型的附件，不删除阶段交付产生的数据文件；删除时会移除对应附件正文摘要并记录修改历史。
- `POST /api/intake/{id}/stage-actions`
  用途：按需求业务阶段执行显式动作，推进需求状态并记录变更历史。
  请求方式：
  - 默认使用 `application/json`，请求体为 `IntakeStageActionRequest`
  - 需要同时上传阶段产出文件时，也支持 `multipart/form-data`；表单字段仍为 `IntakeStageActionRequest`，并可选附带文件字段 `dataFiles`
  动作编码：
  - `START_CLARIFICATION`：开始澄清，状态从 `已收录` 推进到 `待澄清`。研发需求可选传 `aiClarificationEnabled` 控制是否启用 AI 辅助澄清；未传时默认启用并自动提交澄清分析，传 `false` 时只进入人工澄清。
  - `CONFIRM_RECORDED`：数据提取/运维确认收录，状态从 `已收录` 推进到 `待处理`
  - `CONFIRM_CLARIFICATION`：澄清完成；研发需求从 `待澄清` 推进到 `待评估`，数据提取/运维从 `待澄清` 推进到 `待处理`。澄清完成不校验 AI 澄清分析状态，也不强制逐项回复待确认项或风险项；研发需求完成后自动触发 AI 任务评估。
  - `START_PROCESSING`：数据提取/运维开始处理，状态从 `待处理` 推进到 `处理中`
  - `SUBMIT_ACCEPTANCE`：数据提取/运维提交验收，状态从 `处理中` 推进到 `待验收`，可选填写 `actualEffort`、`actualCompletedTime`
  - `COMPLETE_EVALUATION`：普通阶段动作不再支持手工推进研发评估；研发需求必须通过 `POST /api/intake/{id}/development-analysis/confirm` 确认研发任务评估后进入 `待排期`
  - `CONFIRM_SCHEDULING`：排期确认，可选填写预估开发日期 `plannedDevelopmentStartDate`、预估提测日期 `plannedTestingStartDate`、预估上线日期 `plannedReleaseDate`，并写入需求结构化数据；状态从 `待排期` 推进到 `待设计`
  - `CONFIRM_DESIGN`：记录研发开始日期，状态从 `待设计` 推进到 `开发中`
  - `SUBMIT_TESTING`：录入实际开发工时 `actualEffort`、实际开发完成日期 `actualCompletedTime` 和实际提测日期 `testingStartedDate`，状态从 `开发中` 推进到 `测试中`
  - `PASS_TESTING`：录入预约验收日期 `scheduledAcceptanceDate`、实际测试工时 `actualTestingEffort`、实际测试完成日期 `actualTestingCompletedDate`，状态从 `测试中` 推进到 `待验收`
  - `CONFIRM_ACCEPTANCE`：记录实际验收日期 `acceptanceTime`，状态从 `待验收` 推进到 `待上线`
  - `CONFIRM_RELEASE`：记录确认上线时录入的实际上线日期 `releasedTime`，状态从 `待上线` 推进到 `已完成`
  - `CLOSE_REQUIREMENT`：关闭需求；任一未到 `已完成/终止关闭` 的需求可执行，必须填写 `closeReason`，可选填写 `occurredAt` 作为关闭日期，状态推进到 `终止关闭`
- `GET /api/intake/{id}/todos`
  用途：查询需求待办列表。
- `POST /api/intake/{id}/todos`
  用途：新增需求待办，默认状态为 `待处理`。
  请求体：`IntakeTodoCreateRequest`
- `PUT /api/intake/{id}/todos/{todoId}`
  用途：更新需求待办标题、内容、处理人和计划处理时间。
  请求体：`IntakeTodoUpdateRequest`
- `POST /api/intake/{id}/todos/{todoId}/status`
  用途：更新需求待办处理状态和处理结果。
  请求体：`IntakeTodoStatusRequest`
  行为说明：状态取值为 `待处理/处理中/已完成/已取消`；状态改为 `已完成` 时必须填写 `processResult`，未传 `completedAt` 时由后端自动写当前时间。新增、编辑和状态更新都会写入需求修改历史。
- `POST /api/intake/{id}/zentao-link`
  用途：为单条需求维护关联禅道地址，并记录修改历史。
  请求体：`IntakeZentaoLinkRequest`
- `POST /api/intake/{id}/development-branch`
  用途：手动修改自动生成的研发分支名，并记录修改历史。
  请求体：`IntakeDevelopmentBranchRequest`
  行为说明：只更新结构化数据中的研发分支，不推进需求状态；后续保存禅道地址等旁路字段时不会重新覆盖已有研发分支。
- `POST /api/intake/{id}/sql-draft`
  用途：为 `数据提取/运维` 类需求生成 SQL 草稿，并写入 `structuredData.sqlDraft` 与修改历史。
  行为说明：只调用 Codex CLI 生成文本草稿，不连接数据库、不执行 SQL；AI 会尽量自行推断表名和字段，无法确认时在 `questions` 中列出待人工确认项。
  返回说明：返回更新后的 `IntakeDetailResponse`，其中 `structuredData.sqlDraft` 包含 `dialect`、`sql`、`explanation`、`parameters`、`assumptions`、`questions`、`riskWarnings`、`generatedAt` 和 `generator`。
- `GET /api/intake/{id}/clarification-analysis`
  用途：查询研发需求 AI 澄清分析结果，包括逐项待确认项和风险项。
- `POST /api/intake/{id}/clarification-analysis`
  用途：手动重新提交研发需求澄清分析。
  行为说明：仅 `待澄清` 的研发需求可调用；后端按业务线 GitLab 组名读取代码和需求材料，输出逐项 `QUESTION/RISK` 澄清项，不创建工作项。
- `POST /api/intake/{id}/clarification-analysis/items/reply`
  用途：逐项回复待确认项或确认风险项。
  请求体：`itemType`、`itemIndex`、`responseText`；`QUESTION` 必须填写回复内容，`RISK` 可选填处理说明。
- `GET /api/intake/{id}/development-analysis`
  用途：查询研发需求任务评估和工作项草稿。
  返回说明：如果尚未生成草稿，研发需求会返回一个可人工录入的空草稿，并合并业务线成员和全局历史录入负责人作为 `developerPool`；非研发需求返回空数据。
- `POST /api/intake/{id}/development-analysis`
  用途：为 `研发需求` 生成任务评估和研发工作项草稿。
  查询参数：`businessLine`，可选；历史数据缺少业务线时，前端可在任务评估弹框选择后传入，后端会先写回结构化数据再执行评估。
  前置条件：需求已识别为 `研发需求`；项目管理中存在匹配业务线；该业务线已维护 GitLab 组名；系统配置中存在 `gitlab.global/webApiUrl` 和 `gitlab.global/accessToken`。
  返回说明：接口只负责提交后台任务，立即返回当前分析状态，前端不需要等待 AI 完成。
  行为说明：后端会按业务线维护的 GitLab 组名查询该 group 下全部可访问仓库，包括子组项目。每次分析都会先把需求截图、附件正文摘要、结构化字段和原始内容归档到 `raw/requirements/{业务线}/{项目名称}/{年份}/{审批编号}/source.md`，再整理成 `wiki/projects/{业务线}/需求迭代/{项目名称}/{年份}/{审批编号}-{需求名称}.md`；该 wiki Markdown 只做散落需求材料归档，不总结需求点、不拆任务，任务评估草稿会返回 `requirementRawPath`、`requirementWikiPath` 和 `requirementWikiUrl`。随后从 GitLab fetch/reset 最新代码到隔离缓存，并把全部仓库目录交给 Codex CLI。AI 必须第二步再基于该需求 Markdown 输出 `requirementChangePoints` 需求点，第三步再带着需求点和核查问题定向查看代码、配置、接口和数据模型，最后拆解独立任务项。需求点要求内敛、互斥、无重复，不按系统/页面/接口拆分，不把实现方式写成需求点。任务项通过 `taskType` 区分代码改造、配置变更、数据变更、SQL 脚本、运维操作、验证项和待确认项，并包含对应需求点、影响系统标签、改动对象、改地点、判断依据、置信度、工时和负责人建议；系统标签表示系统归属或影响范围，不表示一定需要改代码。该接口只生成草稿，不创建正式工作项。AI 任务评估在后台异步执行；成功后更新草稿与评估结果，但需求主状态仍停留在人工可见阶段，需人工确认研发任务评估后再进入 `待排期`；失败时可通过 `GET /api/intake/{id}/development-analysis` 查看 `FAILED` 状态和失败摘要。
- `POST /api/intake/{id}/development-analysis/chat`
  用途：与 AI 对话调整已有研发工作项草稿。
  请求体：`DevelopmentAnalysisChatRequest`
  行为说明：会基于当前草稿、用户反馈、候选研发人员和业务线下全部代码仓库重新生成完整草稿。
- `POST /api/intake/{id}/development-analysis/owners`
  用途：人工调整研发工作项草稿中的任务负责人。
  请求体：`DevelopmentAnalysisOwnerUpdateRequest`，按工作项序号传入 `index` 和 `ownerUserName`。
  行为说明：只更新当前研发拆解草稿，不创建正式工作项；如果录入了新的负责人名称，会追加到草稿的候选研发人员列表，并写入全局用户候选表，后续其他项目任务评估也可选择该负责人。
- `POST /api/intake/{id}/development-analysis/draft`
  用途：人工整体调整研发工作项草稿。
  请求体：`DevelopmentAnalysisDraftUpdateRequest`，传入草稿总预估工时、测试工时和完整 `workItems` 列表；开发预估工时由任务项工时自动汇总。
  行为说明：支持在任意研发需求阶段修改草稿任务项工时和测试工时，并由系统自动合计开发预估工时和总预估工时；预估开发日期、预估提测日期、预估上线日期不在任务评估阶段录入，统一在排期确认动作中录入。每个任务项可维护标题、说明、对应需求变化点、任务类型、系统标签、改动对象、改地点、判断依据、置信度、涉及文件、工时、负责人、优先级、预计开始日期、截止日期、依赖和风险。存在任务项时，`systemTags` 必填，且必须来自当前业务线启用系统或全局启用中台系统；已停用但已经存在于当前草稿的系统可继续保留用于历史展示。保存时会把任务项中的负责人同步写入全局用户候选表，后续其他项目任务评估也可选择。`taskType` 取值为 `CODE_CHANGE/CONFIG_CHANGE/DATA_CHANGE/SQL_SCRIPT/OPS_ACTION/VERIFY/UNKNOWN`；`confidence` 取值为 `HIGH/MEDIUM/LOW`；优先级取值为 `1/2/3/4`，数字越小优先级越高；日期格式为 `yyyy/MM/dd`。接口只更新当前研发拆解草稿，不创建正式工作项；任务评估和需求只关联业务线及对应系统，不依赖正式项目。需求列表和详情返回的 `involvedSystems` 从最新草稿所有任务项的 `systemTags` 自动去重汇总，不单独录入。
- `POST /api/intake/{id}/development-analysis/confirm`
  用途：人工确认研发工作项草稿。
  行为说明：将需求主状态推进为“待排期”并记录需求修改历史；任务评估和需求只关联业务线及对应系统，不要求 `pm_project` 中存在正式项目。若草稿已有关联正式项目 ID，则可调用工作项创建服务生成正式工作项，并写入 `pm_intake_work_item_relation` 维护需求与正式工作项的强关联；否则只确认评估结果，不创建正式工作项。需求详情的 `relatedWorkItems` 返回已创建的关联正式任务，任务系统标签和负责人以研发评估草稿为准。
- `POST /api/intake/{id}/zentao-sync`
  用途：预留禅道同步入口。
  行为说明：当前只记录 `RESERVED` 状态和说明，不调用外部禅道 API。

## 8. 支付配置接口

控制器：`PaymentChannelController`、`PaymentMerchantController`、`PaymentBindingController`

- `GET /api/payment/channels`
  用途：查询支付渠道列表。
  查询参数：`channelId`、`status`
- `GET /api/payment/channels/{id}`
  用途：查询支付渠道详情。
- `POST /api/payment/channels`
  用途：新建支付渠道。
  请求体：`PaymentChannelSaveRequest`
- `PUT /api/payment/channels/{id}`
  用途：更新支付渠道。
  请求体：`PaymentChannelSaveRequest`
- `GET /api/payment/merchants`
  用途：查询支付商户列表。
  查询参数：`status`、`channelId`、`projectId`、`purposeCode`、`keyword`；`purposeCode` 按商户号支持用途过滤，`keyword` 按商户号或商户名模糊匹配。
- `GET /api/payment/merchants/{id}`
  用途：查询支付商户详情，返回基础信息、支持用途、脱敏参数、秘钥版本摘要和敏感凭据摘要。
- `POST /api/payment/merchants`
  用途：新建支付商户。
  请求体：`PaymentMerchantSaveRequest`，支持 `purposeCodes` 维护商户号支持用途。
- `PUT /api/payment/merchants/{id}`
  用途：更新支付商户。
  请求体：`PaymentMerchantSaveRequest`，支持 `purposeCodes` 维护商户号支持用途。
- `GET /api/payment/merchants/{merchantId}/params`
  用途：查询商户参数列表。
- `POST /api/payment/merchants/{merchantId}/params`
  用途：按参数键新增或覆盖商户参数。
  请求体：`PaymentMerchantParamSaveRequest`
- `PUT /api/payment/merchants/{merchantId}/params/{paramId}`
  用途：更新指定商户参数值。
  请求体：`PaymentMerchantParamSaveRequest`
- `GET /api/payment/merchants/{merchantId}/credentials`
  用途：查询商户敏感凭据列表，接口仅返回脱敏值和指纹。
- `POST /api/payment/merchants/{merchantId}/credentials`
  用途：新增或覆盖商户敏感凭据。
  请求体：`PaymentMerchantCredentialSaveRequest`；导入历史 Excel 时可传 `plainStorage=true`，表示 `encrypted_value` 暂存明文，后续需专项重新加密。
- `PUT /api/payment/merchants/{merchantId}/credentials/{credentialId}`
  用途：更新指定商户敏感凭据。
  请求体：`PaymentMerchantCredentialSaveRequest`；导入历史 Excel 时可传 `plainStorage=true`，表示 `encrypted_value` 暂存明文，后续需专项重新加密。
- `GET /api/payment/merchants/{merchantId}/secrets`
  用途：查询商户秘钥版本列表，接口仅返回脱敏值、指纹和版本号。
- `POST /api/payment/merchants/{merchantId}/secrets`
  用途：新增秘钥版本；`activateNow=true` 时会自动使同名旧版本失效。
  请求体：`PaymentSecretSaveRequest`
- `POST /api/payment/merchants/{merchantId}/secrets/file`
  用途：通过上传文件新增秘钥版本；`activateNow=true` 时会自动使同名旧版本失效。
  请求格式：`multipart/form-data`
  字段：`secretName`、`secretType`、`fileValueType`、`file`、`activateNow`、`validFrom`、`validTo`、`remark`
  说明：`fileValueType` 必须显式传 `TEXT` 或 `BINARY`。`TEXT` 按 UTF-8 读取文件正文后加密落库；`BINARY` 按原始字节 Base64 编码后加密落库。后端不按文件扩展名或 MIME 类型自动推断。
- `GET /api/payment/bindings`
  用途：查询项目商户绑定列表，返回业务线、项目、商户、多用途编码和关联商户关系。
  查询参数：`projectId`、`merchantId`、`purposeCode`、`status`
- `POST /api/payment/bindings`
  用途：新建项目商户绑定。
  请求体：`PaymentProjectBindingSaveRequest`，支持 `purposeCodes` 多选用途和 `relations` 关联商户关系；`purposeCode` 保留为兼容字段，默认取 `purposeCodes` 第一项；绑定用途必须是该商户号支持用途的子集。
- `PUT /api/payment/bindings/{id}`
  用途：更新项目商户绑定。
  请求体：`PaymentProjectBindingSaveRequest`，支持 `purposeCodes` 多选用途和 `relations` 关联商户关系；绑定用途必须是该商户号支持用途的子集。
- `GET /api/payment/projects/{projectId}/bindings/resolve`
  用途：按 `projectId + purposeCode` 解析当前应使用的生效商户绑定。
  查询参数：`purposeCode`
- `GET /api/payment/purposes`
  用途：查询系统内置支付用途字典，例如 `BIND_CARD`、`WITHHOLD`、`PAY_OUT`、`SPLIT_SETTLEMENT` 等。

## 9. 附件接口

控制器：`AttachmentController`

- `GET /api/attachments/{id}/download`
  用途：下载附件原文件。

## 10. 企业微信回调接口

控制器：`WecomCallbackController`

- `POST /api/wecom/callback/messages`
  用途：接收企业微信回调并写入待整理箱。
  请求体：`WecomCallbackRequest`
  鉴权：匿名可访问
