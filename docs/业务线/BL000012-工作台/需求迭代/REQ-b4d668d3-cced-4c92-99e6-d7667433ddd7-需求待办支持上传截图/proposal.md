# Proposal：需求待办支持上传截图

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 需求待办支持上传截图（REQ-b4d668d3-cced-4c92-99e6-d7667433ddd7） |
| 涉及仓库与功能域 | workhub-server：需求待办（`IntakeTodoService`、`IntakeController`）、附件（`AttachmentService`、`AttachmentMapper`）、需求详情（`IntakeService.toDetailResponse`）、Flyway 迁移；workhub-web：需求清单详情「需求待办」页签（`IntakeListView.vue`、`intakeTodoForms.ts`、`api/intake.ts`、`types/work-item.ts`） |
| 所属项目或业务线 | 工作台（BL000012） |
| 版本 | v1.0 |
| 状态 | 已确认（用户 2026-11-26 回复“开工吧”确认实施，2026-11-27 回复“确认，含索引”明确确认索引方案）；已实施并完成验证，未提交、未部署 |
| 目标系统 | WorkHub Server、WorkHub Web、MySQL（`pm_attachment`） |
| 原始需求来源及版本 | 2026-11-26 用户：“需求待办支持上传截图”，并补充“截图保存到数据库里” |
| 研发分支 | server `codex/development-20261122`；web `codex/development-20261122` |
| 负责人 | AI（ZCode）实施 |
| 评审人 | 用户 |

## 1. Why：背景与问题

- 当前问题及证据：需求待办只有标题、内容、处理人、计划时间、状态和处理结果（表 `pm_intake_todo`，`IntakeTodoService`），没有文件字段；`IntakeTodoCreateRequest`、`IntakeTodoUpdateRequest` 无附件入参，`IntakeTodoResponse` 无附件出参。问题截图只能放进需求级「识别与附件」，无法对应到具体待办。
- 受影响用户/角色：新增、编辑待办的用户（`intake:todo:manage`）；查看需求详情的用户（`intake:record:view`）。
- 不解决的后果：待办无法承载截图证据，处理人要在待办文字里转述截图内容或到需求附件里翻找。

## 2. What：目标与范围

### 本期目标

1. 新增、编辑需求待办时可以添加截图（点击选择、拖入、粘贴），截图内容保存到数据库。
2. 待办列表能看到截图缩略图，点击可看大图；待办删除后截图一并清理。

### 本期交付范围

1. 数据：`pm_attachment` 新增业务类型 `INTAKE_TODO_SCREENSHOT`（`biz_id` 为待办 ID），文件内容存 `file_content`；同时给 `pm_attachment` 补 `(biz_type, biz_id)` 联合索引（该表目前只有主键，按业务对象查附件均为全表扫描）。
2. 接口：待办响应新增 `screenshots`；新增 `POST /api/intake/{id}/todos/{todoId}/screenshots`（multipart，一次提交新增与删除）；`DELETE /api/intake/{id}/todos/{todoId}` 同事务删除该待办全部截图。
3. 前端：待办弹框的截图选择、粘贴、移除与保存编排；列表标题下方显示缩略图；预览弹框支持同一待办内多图切换。
4. 后端单元测试、前端 helper 用例，以及现状、迭代日志和测试用例文档更新。

### 明确不做

1. 不做服务端缩略图或图片压缩，列表缩略图直接使用原图。
2. 不改待办状态流转、列表既有列和需求截图/附件的既有接口与页面。
3. 不新增权限码，不新增 MCP 工具，不新增表和业务列。

### 约束与依赖

- 依赖的代码和文档：附件内容存库能力（`AttachmentService.storeSingleFile`）、下载接口 `GET /api/attachments/{id}/download`、全局 multipart 上限（单文件 20MB、单请求 50MB）。
- 业务/合规约束：仓库安全规范，未经授权不安装依赖、不修改配置。
- 既有系统/接口/数据约束：需求详情弹框最大宽度 1400px，待办表格各列最小宽度合计 1288px，没有空间再加一列；`IntakeTodoResponse` 是按位置构造的 record，新增字段需要同步全部构造点。
- 外部依赖与前置条件：发布顺序为数据库迁移 → 后端 → 前端。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | 新增待办时选择、拖入或粘贴多张图片，保存后待办列表出现对应缩略图；刷新页面后仍存在 | 页面走查 + 查询 `pm_attachment` 中 `biz_type='INTAKE_TODO_SCREENSHOT'` 且 `file_content` 非空的记录 | 用户 |
| AC-02 | 编辑待办可以新增、移除截图；点「取消」后服务端截图不变 | 页面走查 + 接口核对 | 用户 |
| AC-03 | 非图片文件在选择时被忽略并提示；绕过页面直接调用接口上传非图片返回 400，且不写入任何记录 | 页面走查 + 单元测试 | AI |
| AC-04 | 列表缩略图点击打开大图预览，同一待办多张截图可左右切换，切换不超出该待办范围 | 页面走查 | 用户 |
| AC-05 | 删除待办后，该待办的截图记录全部删除，`pm_attachment` 无残留 | 单元测试 + SQL 核对 | AI |
| AC-06 | 截图变更接口携带不属于该待办的 `removedIds` 时返回 400，不删除、不新增任何记录 | 单元测试 | AI |
| AC-07 | 需求详情接口和 `GET /api/intake/{id}/todos` 返回的每个待办都带 `screenshots`（无截图为空数组）；查询截图清单不读取 `file_content`，执行计划命中新增索引 | 单元测试 + `EXPLAIN` | AI |
| AC-08 | 截图新增、删除写入需求历史（摘要「更新需求待办截图」），内容包含待办标题及新增、删除的文件名 | 单元测试 + 页面走查「修改历史」 | AI |
| AC-09 | 后端相关模块单元测试通过；前端 `npm run build` 与 helper 用例通过 | 命令输出 | AI |

设计与任务见 [design.md](design.md)、[tasks.md](tasks.md)。
