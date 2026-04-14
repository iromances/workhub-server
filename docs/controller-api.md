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

控制器：`SystemController`

- `GET /api/system/ping`
  用途：系统健康检查。
  鉴权：匿名可访问

## 3. 项目接口

控制器：`ProjectController`

- `GET /api/projects`
  用途：查询项目列表。
  查询参数：`status`、`keyword`
- `GET /api/projects/{id}`
  用途：查询项目详情。
- `POST /api/projects`
  用途：新建项目。
  请求体：`ProjectSaveRequest`
- `PUT /api/projects/{id}`
  用途：更新项目。
  请求体：`ProjectSaveRequest`

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
  查询参数：`status`、`sourceType`、`keyword`、`demandStatus`、`enrichmentStatus`
  返回补充：`demandStatus` 仅在识别成功后才进入业务生命周期。`研发需求` 取值为 `已收录/待评估/已评估/研发中/待测试/测试中/待验收/待上线/已上线`；`数据提取/运维` 取值为 `已收录/待评估/已评估/研发中/已上线`；识别前请看 `enrichmentStatus`
- `GET /api/intake/{id}`
  用途：查询单条需求详情，包括结构化字段、附件、需求阶段状态、修改历史、enrichment 状态、研发分支名和关联禅道地址。
  查询参数：`recordView=true|false`，默认 `true`
- `POST /api/intake/upload`
  用途：通过需求截图和需求附件录入待整理需求。
  请求方式：`multipart/form-data`
  表单字段：`senderName`、`sourceChannel`、`rawContent`
  文件字段：`screenshots`、`attachments`
  返回说明：上传成功即返回，Codex 结构化识别在后台异步执行；识别完成后会自动归类 `研发需求/数据提取/运维`，其中 `研发需求` 会生成研发分支名。
- `POST /api/intake/{id}/stage-actions`
  用途：按需求业务阶段执行显式动作，推进需求状态并记录变更历史。
  请求方式：
  - 默认使用 `application/json`，请求体为 `IntakeStageActionRequest`
  - 当动作是 `COMPLETE_DELIVERY` 时，也支持 `multipart/form-data`；表单字段仍为 `IntakeStageActionRequest`，并可选附带文件字段 `dataFiles`
  动作编码：
  - `EVALUATE_EFFORT`：录入 `estimatedEffort`、`plannedDueDate`，状态推进到 `已评估`
  - `START_DEVELOPMENT`：必须填写 `developmentOwnerUserName`，同时记录研发开始日期，状态推进到 `研发中`
  - `SUBMIT_TESTING`：仅 `研发需求` 可用；录入 `actualEffort`、`actualCompletedTime`，状态推进到 `待测试`
  - `START_TESTING`：仅 `研发需求` 可用；记录测试开始日期，状态推进到 `测试中`
  - `SUBMIT_ACCEPTANCE`：仅 `研发需求` 可用；状态推进到 `待验收`
  - `CONFIRM_ACCEPTANCE`：仅 `研发需求` 可用；记录 `acceptanceTime`，状态推进到 `待上线`
  - `CONFIRM_RELEASE`：仅 `研发需求` 可用；记录上线时间，状态推进到 `已上线`
  - `COMPLETE_DELIVERY`：仅 `数据提取/运维` 可用；可补录 `actualEffort`、`actualCompletedTime`，并记录完成日期，状态从 `研发中` 直接推进到 `已上线`。若有交付结果文件，可选上传到 `dataFiles`，会作为“数据文件”附件留存并写入修改历史
- `POST /api/intake/{id}/zentao-link`
  用途：为单条需求维护关联禅道地址，并记录修改历史。
  请求体：`IntakeZentaoLinkRequest`

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
  用途：查询支付商户详情，返回基础信息、脱敏参数和秘钥版本摘要。
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
- `GET /api/payment/merchants/{merchantId}/secrets`
  用途：查询商户秘钥版本列表，接口仅返回脱敏值、指纹和版本号。
- `POST /api/payment/merchants/{merchantId}/secrets`
  用途：新增秘钥版本；`activateNow=true` 时会自动使同名旧版本失效。
  请求体：`PaymentSecretSaveRequest`
- `GET /api/payment/bindings`
  用途：查询项目商户绑定列表。
  查询参数：`projectId`、`merchantId`、`purposeCode`、`status`
- `POST /api/payment/bindings`
  用途：新建项目商户绑定。
  请求体：`PaymentProjectBindingSaveRequest`
- `PUT /api/payment/bindings/{id}`
  用途：更新项目商户绑定。
  请求体：`PaymentProjectBindingSaveRequest`
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
