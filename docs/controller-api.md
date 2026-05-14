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
- `POST /api/system/configs`
  用途：新增系统配置。
  请求体：`SysConfigSaveRequest`
  说明：`valueType` 支持 `TEXT` 和 `SECRET`；`SECRET` 会应用层加密落库。
- `PUT /api/system/configs/{id}`
  用途：更新系统配置。
  请求体：`SysConfigSaveRequest`
  说明：更新 `SECRET` 配置时，如果 `value` 为空则保留旧秘钥。

## 3. 项目接口

控制器：`ProjectController`

- `GET /api/projects`
  用途：查询项目列表。
  查询参数：`status`、`keyword`
  返回说明：项目摘要包含 `businessLineCode`、`businessLineName`、项目编码 `code`、项目名称 `name` 和项目组字段 `group`；`keyword` 支持匹配业务线编码/名称、项目编码/名称和项目组。
- `GET /api/projects/{id}`
  用途：查询项目详情。
- `POST /api/projects`
  用途：新建项目。
  请求体：`ProjectSaveRequest`，必填字段包含 `businessLineCode`、`businessLineName`、`code`、`name`、`type`、`group`、`ownerUserName`、`status`
- `PUT /api/projects/{id}`
  用途：更新项目。
  请求体：`ProjectSaveRequest`，必填字段包含 `businessLineCode`、`businessLineName`、`code`、`name`、`type`、`group`、`ownerUserName`、`status`
- `DELETE /api/projects/{id}`
  用途：删除项目；如果项目已被工作项、迭代、版本、支付绑定或研发评估记录引用，则拒绝删除。
- `GET /api/projects/groups`
  用途：查询项目组列表。
  查询参数：`keyword`
  返回说明：返回项目组名称、GitLab 组名或命名空间、说明和启用状态。
- `POST /api/projects/groups`
  用途：新增项目组。
  请求体：`ProjectGroupSaveRequest`，必填字段为 `groupName`，可选字段包含 `gitlabGroupName`、`description`、`enabled`
- `PUT /api/projects/groups/{id}`
  用途：更新项目组。
  请求体：`ProjectGroupSaveRequest`
- `DELETE /api/projects/groups/{id}`
  用途：删除项目组；如果项目组已被项目或项目组成员引用，则拒绝删除。

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

## 7. 需求管理接口

控制器：`IntakeController`

- `GET /api/intake`
  用途：查询需求管理列表。
  兼容路径：`/api/intake-records`
  查询参数：`status`、`requirementName`、`demandStatus`、`releasedStartDate`、`releasedEndDate`
  返回补充：`demandStatus` 仅在识别成功后才进入业务生命周期，统一取值为 `已收录/待澄清/待评估/待排期/待设计/开发中/测试中/待上线/待验收/已完成/终止关闭`；识别前请看 `enrichmentStatus`
- `GET /api/intake/{id}`
  用途：查询单条需求详情，包括结构化字段、附件、需求阶段状态、修改历史、enrichment 状态、研发分支名和关联禅道地址。
  查询参数：`recordView=true|false`，默认 `true`
- `DELETE /api/intake/{id}`
  用途：逻辑删除单条需求。
  行为说明：仅更新 `pm_intake_record` 主表的 `deleted/deleted_at/deleted_by`；附件、修改历史和本地文件保留不动。
- `POST /api/intake/upload`
  用途：通过需求截图和需求附件录入待整理需求。
  请求方式：`multipart/form-data`
  表单字段：`senderName`、`developmentOwnerUserName`、`sourceChannel`、`rawContent`、`projectGroup`
  文件字段：`screenshots`、`attachments`
  补充说明：`developmentOwnerUserName` 必填，单独保存到 `pm_intake_record.development_owner_user_name`；`projectGroup` 会写入原始录入内容并作为结构化字段 `projectHint` 的优先来源，便于后续研发需求任务评估匹配项目。
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
  - `START_CLARIFICATION`：开始澄清，状态从 `已收录` 推进到 `待澄清`
  - `CONFIRM_CLARIFICATION`：澄清完成，状态从 `待澄清` 推进到 `待评估`
  - `COMPLETE_EVALUATION`：录入 `estimatedEffort`、`plannedDueDate`，状态从 `待评估` 推进到 `待排期`
  - `CONFIRM_SCHEDULING`：排期确认，状态从 `待排期` 推进到 `待设计`
  - `CONFIRM_DESIGN`：记录研发开始日期，状态从 `待设计` 推进到 `开发中`
  - `SUBMIT_TESTING`：录入 `actualEffort`、`actualCompletedTime`，状态从 `开发中` 推进到 `测试中`
  - `PASS_TESTING`：记录测试开始日期，状态从 `测试中` 推进到 `待上线`
  - `CONFIRM_RELEASE`：记录上线时间，状态从 `待上线` 推进到 `待验收`
  - `CONFIRM_ACCEPTANCE`：记录 `acceptanceTime`，状态从 `待验收` 推进到 `已完成`
  - `CLOSE_REQUIREMENT`：关闭需求；任一未到 `已完成/终止关闭` 的需求可执行，必须填写 `closeReason`，可选填写 `occurredAt` 作为关闭日期，状态推进到 `终止关闭`
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
- `GET /api/intake/{id}/development-analysis`
  用途：查询研发需求任务评估和工作项草稿。
  返回说明：如果尚未生成草稿，返回空数据。
- `POST /api/intake/{id}/development-analysis`
  用途：为 `研发需求` 生成任务评估和研发工作项草稿。
  查询参数：`projectGroup`，可选；历史数据缺少项目组时，前端可在任务评估弹框选择后传入，后端会先写回结构化数据再执行评估。
  前置条件：需求已识别为 `研发需求`；项目管理中存在匹配项目组；该项目组已维护 GitLab 组名；系统配置中存在 `gitlab.global/webApiUrl` 和 `gitlab.global/accessToken`。
  返回说明：接口只负责提交后台任务，立即返回当前分析状态，前端不需要等待 AI 完成。
  行为说明：后端会按项目组维护的 GitLab 组名查询该 group 下全部可访问仓库，包括子组项目。每次分析都会先把需求截图、附件正文摘要、结构化字段和原始内容归档到 `raw/requirements/{项目组}/{项目名称}/{年份}/{审批编号}/source.md`，再整理成 `wiki/projects/{项目组}/{项目名称}/需求迭代/{年份}/{审批编号}-{需求名称}.md`；该 wiki Markdown 只做散落需求材料归档，不总结需求点、不拆任务，任务评估草稿会返回 `requirementRawPath`、`requirementWikiPath` 和 `requirementWikiUrl`。随后从 GitLab fetch/reset 最新代码到隔离缓存，并把全部仓库目录交给 Codex CLI。AI 必须第二步再基于该需求 Markdown 输出 `requirementChangePoints` 需求点，第三步再带着需求点和核查问题定向查看代码、配置、接口和数据模型，最后拆解独立任务项。需求点要求内敛、互斥、无重复，不按系统/页面/接口拆分，不把实现方式写成需求点。任务项通过 `taskType` 区分代码改造、配置变更、数据变更、SQL 脚本、运维操作、验证项和待确认项，并包含对应需求点、影响系统标签、改动对象、改动点、判断依据、置信度、工时和负责人建议；系统标签表示系统归属或影响范围，不表示一定要改代码，优先来自项目组下的仓库名。该接口只生成草稿，不创建正式工作项。AI 任务评估在后台异步执行；成功后更新草稿与评估结果，但需求主状态仍停留在人工可见阶段，需人工执行 `COMPLETE_EVALUATION` 后再进入 `待排期`；失败时可通过 `GET /api/intake/{id}/development-analysis` 查看 `FAILED` 状态和失败摘要。
- `POST /api/intake/{id}/development-analysis/chat`
  用途：与 AI 对话调整已有研发工作项草稿。
  请求体：`DevelopmentAnalysisChatRequest`
  行为说明：会基于当前草稿、用户反馈、候选研发人员和项目组下全部代码仓库重新生成完整草稿。
- `POST /api/intake/{id}/development-analysis/owners`
  用途：人工调整研发工作项草稿中的任务负责人。
  请求体：`DevelopmentAnalysisOwnerUpdateRequest`，按工作项序号传入 `index` 和 `ownerUserName`。
  行为说明：只更新当前研发拆解草稿，不创建正式工作项；如果录入了新的负责人名称，会追加到草稿的候选研发人员列表，并写入全局用户候选表，后续其他项目任务评估也可选择该负责人；后续确认创建工作项时使用更新后的负责人。
- `POST /api/intake/{id}/development-analysis/draft`
  用途：人工整体调整研发工作项草稿。
  请求体：`DevelopmentAnalysisDraftUpdateRequest`，传入草稿总评估工时、开发工时、测试工时、开发完成日期、预估测试开始日期、预估测试完成日期、预估上线日期和完整 `workItems` 列表。
  行为说明：支持修改草稿总评估工时、开发工时、测试工时、开发完成日期、预估测试开始日期、预估测试完成日期、预估上线日期，并支持修改、新增和删除任务项；每个任务项可维护标题、说明、对应需求变化点、任务类型、系统标签、改动对象、改动点、判断依据、置信度、涉及文件、工时、负责人、优先级、预计开始日期、截止日期、依赖和风险。保存时会把任务项中的负责人同步写入全局用户候选表，后续其他项目任务评估也可选择。`taskType` 取值为 `CODE_CHANGE/CONFIG_CHANGE/DATA_CHANGE/SQL_SCRIPT/OPS_ACTION/VERIFY/UNKNOWN`；`confidence` 取值为 `HIGH/MEDIUM/LOW`；优先级取值为 `1/2/3/4`，数字越小优先级越高；日期格式为 `yyyy/MM/dd`。接口只更新当前研发拆解草稿，不创建正式工作项；保存后确认创建工作项会使用最新草稿。
- `POST /api/intake/{id}/development-analysis/confirm`
  用途：人工确认研发工作项草稿，并创建正式工作项。
  行为说明：调用工作项创建服务生成正式工作项，状态仍遵循正式工作项生命周期；同时将需求主状态推进为“待排期”并记录需求修改历史。
- `POST /api/intake/{id}/zentao-sync`
  用途：预留禅道同步入口。
  行为说明：当前只记录 `RESERVED` 状态和说明，不调用外部禅道 API。

## 8. 支付配置接口

控制器：`PaymentChannelController`、`PaymentMerchantController`、`PaymentBindingController`

- `GET /api/payment/channels`
  用途：查询支付渠道列表。
  查询参数：`status`、`keyword`
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
  查询参数：`status`、`channelId`、`projectId`、`purposeCode`、`keyword`
- `GET /api/payment/merchants/{id}`
  用途：查询支付商户详情，返回基础信息、脱敏参数、秘钥版本摘要和敏感凭据摘要。
- `POST /api/payment/merchants`
  用途：新建支付商户。
  请求体：`PaymentMerchantSaveRequest`
- `PUT /api/payment/merchants/{id}`
  用途：更新支付商户。
  请求体：`PaymentMerchantSaveRequest`
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
- `GET /api/payment/bindings`
  用途：查询项目商户绑定列表，返回业务线、项目、商户、多用途编码和关联商户关系。
  查询参数：`projectId`、`merchantId`、`purposeCode`、`status`
- `POST /api/payment/bindings`
  用途：新建项目商户绑定。
  请求体：`PaymentProjectBindingSaveRequest`，支持 `purposeCodes` 多选用途和 `relations` 关联商户关系；`purposeCode` 保留为兼容字段，默认取 `purposeCodes` 第一项。
- `PUT /api/payment/bindings/{id}`
  用途：更新项目商户绑定。
  请求体：`PaymentProjectBindingSaveRequest`，支持 `purposeCodes` 多选用途和 `relations` 关联商户关系。
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
