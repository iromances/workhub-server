# Design：需求待办支持上传截图

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 需求待办支持上传截图（REQ-b4d668d3-cced-4c92-99e6-d7667433ddd7） |
| 涉及仓库与功能域 | workhub-server：附件服务与映射、待办服务与接口、需求详情组装、Flyway 迁移；workhub-web：需求待办页签的弹框、列表与预览 |
| 所属项目或业务线 | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 版本 | v1.1（2026-11-26 实施中修订交互细节，范围、契约与验收标准不变；修订点见 §6） |
| 状态 | 已确认并按本设计实施（v1.0 于 2026-11-26 经用户确认实施，索引方案于 2026-11-27 经用户回复“确认，含索引”明确确认） |
| 当前事实核对环境、分支、时间 | 2026-11-26 本机源码只读核对；server 分支 `codex/development-20261122`（HEAD `e7b7b7b`），web 同名分支（HEAD `469053e`，工作区另有未提交的数字员工相关改动，其中 `IntakeListView.vue`、`types/work-item.ts` 的在途改动不涉及待办，本次只做增量编辑） |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - 待办读写：`workhub-service/.../service/intake/IntakeTodoService.java`（新增、编辑、改状态、删除，均写需求历史）；接口在 `workhub-controller/.../controller/intake/IntakeController.java`，写操作统一为 `intake:todo:manage`。
  - 待办列表的两条出口：`GET /api/intake/{id}/todos` 走 `IntakeTodoService.list`；需求详情 `GET /api/intake/{id}` 的 `todos` 由 `IntakeService.toDetailResponse` 直接查 `intakeTodoMapper` 后用私有方法 `toTodoResponse` 组装。前端详情页签使用的是后者。
  - 附件存库：`AttachmentService.storeSingleFile` 把内容写入 `pm_attachment.file_content`（LONGBLOB），`storage_path` 为 NULL，同时记录 `file_size`、`file_sha256`；读取经 `AttachmentController.download`，只校验 `intake:record:view`，不区分业务类型。
  - 前端：`IntakeListView.vue` 的待办弹框（`todoFormVisible`）、待办表格和通用预览弹框（`previewVisible`，单图无切换）；新建需求弹框已有粘贴截图的处理 `handlePastedScreenshots`。
- 当前接口、数据及配置：`pm_attachment` 仅主键索引，`findByBiz` 等按 `biz_id` 的查询都是全表扫描；`biz_type` 为 `VARCHAR(32)`。multipart 上限在 `application.yml`：单文件 20MB、单请求 50MB。
- 事实来源：上述路径代码，以及 `V20260901_182458__store_attachment_content_in_database.sql`、`V1__init.sql` 中的 `pm_attachment`、`pm_intake_todo` 建表语句。
- 尚未核实的假设：生产库 `pm_attachment` 是否已有人工加过索引——2026-11-26 经只读查询核实只有主键索引，无同名冲突；表内共 511 行。生产 MySQL 的版本未核对，迁移与 `EXPLAIN` 只在临时 MySQL 8.4.10 上验证过。

## 2. 系统架构

- 总体方案：待办截图作为一种新的附件业务类型 `INTAKE_TODO_SCREENSHOT` 存入 `pm_attachment`，`biz_id` 为待办 ID，不建新表、不加业务列。待办字段仍走 JSON 接口，截图走独立的 multipart 接口，由前端在一次保存里先后调用。
- 主要流程：
  1. 新增待办：`POST /todos`（JSON）得到待办 → 有截图时 `POST /todos/{todoId}/screenshots` 上传。
  2. 编辑待办：`PUT /todos/{todoId}`（JSON）→ 有截图增删时 `POST /todos/{todoId}/screenshots`（同一请求带 `files` 与 `removedIds`）。
  3. 查看：需求详情的 `todos[].screenshots` 只带元数据；缩略图和预览按附件 ID 调用既有下载接口取内容。
  4. 删除待办：同一事务内删除待办行和它的全部截图行。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| AttachmentService / AttachmentMapper | 按待办保存、删除、批量查截图元数据；`INTAKE_TODO_SCREENSHOT` 的保存与分类名称 | 图片类型规则、需求历史、待办归属校验 | 待办 ID、文件或附件 ID → 附件响应 | `pm_attachment` |
| IntakeTodoService | 待办归属校验、图片类型校验、截图变更事务、历史记录、删除待办时级联清理、待办响应带截图清单 | 文件读写细节 | 接口入参 → `IntakeTodoResponse` | AttachmentService |
| IntakeService.toDetailResponse | 详情中的 `todos[].screenshots` | 截图变更 | 待办实体 → 详情响应 | AttachmentService |
| IntakeController | 暴露截图变更接口与权限 | 业务规则 | HTTP → `ApiResponse<IntakeTodoResponse>` | IntakeTodoService |
| IntakeListView.vue 等 | 选择、拖入、粘贴、暂存移除、保存编排、缩略图、预览切换 | 存储、校验规则 | 用户操作 → 接口调用 | `api/intake.ts` |

- 前端页面、交互与后端规则边界：
  - 弹框「截图」项：点击选择或拖入图片；粘贴时仅当剪贴板没有文本、只有图片才接收为截图，避免拦截往标题、内容里粘贴文字。粘贴监听挂在弹框打开期间的 `document` 上，弹框关闭即移除（见 §6 修订 1）。非图片文件在选择时被忽略并提示数量。
  - 编辑态下已有截图的移除是暂存，点「保存」才提交，点「取消」不生效。
  - 保存顺序为先字段后截图。截图请求失败时弹框不关闭，提示“待办已保存，但截图更新失败”，并后台刷新列表让已落库的待办显示出来；重试不会重复创建待办（新增态记住已创建的待办）。
  - 列表：缩略图放在「待办标题」单元格内标题下方，不新增表格列、不改列宽。截图区最多 4 格：不超过 4 张全部展示，超过时展示前 3 张并以「+N」格收起其余，保证单行。点击缩略图或「+N」打开预览弹框，可左右切换同一待办内的截图。
  - 缩略图按需加载：只在「需求待办」页签处于激活状态时才请求图片；按附件 ID 缓存，切换或关闭需求时清空并释放，截图或待办被删除后对应缓存失效。

## 3. 契约与数据模型

- API 提供方与消费者：提供方 workhub-server，消费者 workhub-web；MCP `get_intake_detail` 直接返回 `intakeService.detail(...)`，`todos[].screenshots` 会随之出现，其中 `downloadUrl` 对 MCP 无意义，本次不调整。
- 新增契约（权威文件为本节，实施后以代码为准）：

  `POST /api/intake/{id}/todos/{todoId}/screenshots`，`multipart/form-data`，权限 `intake:todo:manage`

  | 参数 | 位置 | 必填 | 说明 |
  | --- | --- | --- | --- |
  | `id`、`todoId` | 路径 | 是 | 需求 ID、待办 ID |
  | `files` | 表单文件，可多个 | 否 | 新增的截图，内容类型必须以 `image/` 开头 |
  | `removedIds` | 表单字段，可多个 | 否 | 要删除的截图附件 ID，必须都属于该待办 |

  行为：删除与新增在同一事务内完成，任一校验失败整体回滚。`files` 与 `removedIds` 都为空时不写数据、不写历史，返回当前待办。成功返回更新后的待办。

  | 场景 | 响应 |
  | --- | --- |
  | 需求不存在 | 400「需求不存在」 |
  | 待办不存在或不属于该需求 | 400「需求待办不存在」 |
  | 文件非图片 | 400「截图仅支持图片文件：{文件名}」 |
  | `removedIds` 含不属于该待办的附件 | 400「截图不存在或不属于当前待办」 |
  | 单文件超过 20MB 或单请求超过 50MB | 沿用现有 multipart 超限处理 |

- 修改契约：
  - `IntakeTodoResponse` 末尾新增 `screenshots` 数组，元素结构与需求附件 `AttachmentResponse` 相同（`id`、`category`、`fileName`、`contentType`、`downloadUrl`、`previewable`、`createdAt`），`category` 固定为“截图”；无截图为空数组。`GET /api/intake/{id}/todos` 与需求详情的 `todos[]` 同时生效。
  - `DELETE /api/intake/{id}/todos/{todoId}` 路径和返回不变，行为增加“同时删除该待办的全部截图”。
  - 读取内容沿用 `GET /api/attachments/{attachmentId}/download`，无变更。
- 幂等、并发、重复执行：截图变更接口的删除按 `biz_type + biz_id + id` 限定并先校验归属；重复提交同一批 `removedIds` 时第二次返回 400，不会误删其他数据。重复上传 `files` 会产生重复截图，前端在请求成功后关闭弹框并清空暂存，不自动重试。
- 数据归属与约束：`pm_attachment.biz_type = 'INTAKE_TODO_SCREENSHOT'`（22 字符，未超 `VARCHAR(32)`），`biz_id` 为待办 ID，`file_content` 非空，`storage_path` 为 NULL。待办删除是硬删除，截图随之硬删除；需求删除是逻辑删除（`markDeleted`），不做级联。
- 历史兼容：旧数据无待办截图，接口返回空数组；新增字段位于 record 末尾，旧前端忽略。
- 数据库变更：新增 `workhub-bootstrap/src/main/resources/db/schema/mysql/V20261126_170000__add_pm_attachment_biz_index.sql`，内容为 `ALTER TABLE pm_attachment ADD KEY idx_pm_attachment_biz_type_biz_id (biz_type, biz_id)`，由 Flyway 在应用启动时执行一次。现有查询 `WHERE biz_id = ? AND biz_type IN (...)` 与新增查询 `WHERE biz_type = ? AND biz_id IN (...)` 都能用该索引。回滚代码时索引可保留；如需撤销执行 `ALTER TABLE pm_attachment DROP KEY idx_pm_attachment_biz_type_biz_id`。

## 4. 非功能约束

- 安全与权限：写截图与写待办同用 `intake:todo:manage`，读取沿用下载接口的 `intake:record:view`，与需求截图的可见范围一致。内容类型由客户端提供，服务端只校验前缀，不做文件内容识别；图片只经下载接口以附件形式返回，或由 `<img>` 渲染，不内联执行。
- 性能、容量和时效：
  - 详情与待办列表只查截图元数据，不读取 `file_content`；一次详情请求对待办截图只增加一条 `IN` 查询，待办为空时不发起查询。
  - 列表缩略图使用原图，不做服务端压缩。加载受三点约束：仅页签激活时加载、按附件 ID 缓存、切换需求即释放。单张大小受全局 20MB 上限约束。
  - 不单独设置每张大小和每个待办张数上限，与现有需求截图一致。
- 失败处理：截图变更接口失败时前端保留弹框与已选文件并提示；缩略图加载失败不弹逐条提示，单元格保留占位，控制台记录错误。
- 日志、监控、告警与审计：截图新增、删除写需求历史，摘要「更新需求待办截图」，明细为“待办标题、新增截图文件名、删除截图文件名”。删除待办沿用「删除需求待办」历史，不额外记录截图数量。

## 5. 交付与回滚

- 影响系统和发布顺序：先执行迁移（随后端启动自动完成），再发布后端，最后发布前端。新前端配旧后端时保存待办可用，但截图请求失败并提示，因此前端不得先于后端发布。
- 灰度或开关：无。
- 数据备份与前后校验：本需求不迁移存量数据，除常规备份外不需要额外的备份步骤。InnoDB 二级索引 `ADD KEY` 默认为 online DDL；`现状.md` 记录到 2026-11-25 时 2026-09 之前的附件共 294 条，表体量小。迁移只加索引，不改表结构其余部分。上线后可用 `SELECT COUNT(*) FROM pm_attachment WHERE biz_type='INTAKE_TODO_SCREENSHOT' AND file_content IS NULL` 核对，结果应为 0。
- 回滚条件、步骤与不可逆部分：回滚后端后，旧代码不读取 `INTAKE_TODO_SCREENSHOT` 记录，功能不可见、数据保留；旧代码删除待办时不会清理其截图，会留下不可见的孤立记录，需要时执行 `DELETE FROM pm_attachment WHERE biz_type='INTAKE_TODO_SCREENSHOT' AND biz_id IN (待办 ID)`。回滚前端只需还原提交，无数据影响。
- 生效并核实后需要更新的文档：`docs/业务线/BL000012-工作台/现状.md` 补充待办截图的存储与接口说明；`迭代日志.md` 记录本次进展；`测试用例/需求清单测试用例-20261103.md` 的「需求待办」小节补充截图用例。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1 → §2 流程 1、2 与弹框交互，§3 新增契约；目标 2 → §2 流程 3、4 与列表、预览交互。
- 明确非范围是否被保持：未引入服务端缩略图、权限码、MCP 工具、新表或业务列；待办状态流转与需求附件接口未动。
- 待确认问题的处理（v1.0 起草时提出；用户 2026-11-26 确认实施，2026-11-27 回复“确认，含索引”明确答复）：
  1. 附带新增 `pm_attachment` 联合索引：用户明确确认，已随本需求实施。
  2. 列表缩略图使用原图、不做服务端压缩：随规格一并确认。截图普遍较大或待办很多时，可另立需求做服务端缩略图。
- v1.1 实施修订（不改变范围、契约与验收标准）：
  1. 粘贴监听位置：草案把监听挂在弹框内的包裹元素上，依赖粘贴事件从表单内元素冒泡。走查发现点掉某张截图的「×」后焦点回到页面主体，之后的粘贴不再被接收。改为弹框打开期间在 `document` 上监听、关闭时移除。
  2. 缩略图展示规则：草案为“最多 4 张加「+N」”，在 180px 的标题列里「+N」会被挤到第二行。改为截图区最多 4 格，超过时展示前 3 张并以「+N」格收起其余；不改列宽。
  3. 与本功能直接相关的三处收尾：详情数据的三处附件接口回写（追加、替换、删除附件后）原先没有经过 `normalizeIntakeDetail`，现统一经过，保证待办始终带 `screenshots`；删除待办时同步释放其缩略图缓存；待办保存成功后若详情刷新失败，单独提示“保存成功，但详情刷新失败，请重新打开详情”，不再与保存失败混为一条提示。
- 契约、ADR 及 Tasks 的关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)（验证证据与未执行项见 tasks T-08）；无 ADR。
