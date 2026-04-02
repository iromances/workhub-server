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
  返回补充：`demandStatus` 仅在识别成功后才进入业务生命周期，取值为 `已收录/待评估/已评估/研发中/待测试/测试中/待验收/待上线/已上线`；识别前请看 `enrichmentStatus`
- `GET /api/intake/{id}`
  用途：查询单条需求详情，包括结构化字段、附件、需求阶段状态、修改历史、enrichment 状态、研发分支名、关联禅道地址和转正式工作项所需信息。
  查询参数：`recordView=true|false`，默认 `true`
- `POST /api/intake/upload`
  用途：通过需求截图和需求附件录入待整理需求。
  请求方式：`multipart/form-data`
  表单字段：`senderName`、`sourceChannel`、`rawContent`
  文件字段：`screenshots`、`attachments`
  返回说明：上传成功即返回，Codex 结构化识别在后台异步执行；识别完成后会自动归类 `研发需求/数据提取/运维`，其中 `研发需求` 会生成研发分支名。
- `POST /api/intake/{id}/stage-actions`
  用途：按需求业务阶段执行显式动作，推进需求状态并记录变更历史。
  请求体：`IntakeStageActionRequest`
  动作编码：
  - `EVALUATE_EFFORT`：录入 `estimatedEffort`、`plannedDueDate`，状态推进到 `已评估`
  - `START_DEVELOPMENT`：记录研发开始日期，状态推进到 `研发中`
  - `SUBMIT_TESTING`：录入 `actualEffort`、`actualCompletedTime`，状态推进到 `待测试`
  - `START_TESTING`：记录测试开始日期，状态推进到 `测试中`
  - `SUBMIT_ACCEPTANCE`：状态推进到 `待验收`
  - `CONFIRM_ACCEPTANCE`：记录 `acceptanceTime`，状态推进到 `待上线`
  - `CONFIRM_RELEASE`：记录上线时间，状态推进到 `已上线`
- `POST /api/intake/{id}/zentao-link`
  用途：为单条需求维护关联禅道地址，并记录修改历史。
  请求体：`IntakeZentaoLinkRequest`
- `POST /api/intake/{id}/convert`
  用途：将待整理需求转成正式工作项。
  请求体：`IntakeConvertRequest`

## 8. 附件接口

控制器：`AttachmentController`

- `GET /api/attachments/{id}/download`
  用途：下载附件原文件。

## 9. 企业微信回调接口

控制器：`WecomCallbackController`

- `POST /api/wecom/callback/messages`
  用途：接收企业微信回调并写入待整理箱。
  请求体：`WecomCallbackRequest`
  鉴权：匿名可访问
