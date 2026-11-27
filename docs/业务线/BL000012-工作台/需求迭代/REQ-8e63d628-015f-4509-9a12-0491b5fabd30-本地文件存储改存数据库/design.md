# Design：本地文件存储改存数据库

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 本地文件存储改存数据库（REQ-8e63d628-015f-4509-9a12-0491b5fabd30） |
| 涉及仓库与功能域 | workhub-server：`AttachmentService`、`AuthService`、`AuthController`（路径参数语义）、`StorageProperties` 与 `application.yml`；workhub-web 无改动 |
| 所属项目或业务线 | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 已确认（随 Proposal 一并确认）；头像改造已于 2026-11-26 在本机实例核对生效 |
| 当前事实核对环境、分支、时间 | 2026-11-25；workhub-server 分支 `codex/development-20261122`；只读核对源码、本机文件和运行库 |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - 头像上传：`POST /api/auth/me/avatar` → `AuthService.uploadAvatar`：校验类型、扩展名和 2 MB 上限后，把文件写到 `{workhub.storage.local-path}/avatars/{随机名}.{扩展名}`，再把 `sys_user.avatar_url` 设为 `/api/auth/avatars/{文件名}`，并删除旧文件。
  - 头像读取：`GET /api/auth/avatars/{fileName}`（匿名可访问）→ `AuthService.loadAvatar` 从磁盘读文件。
  - 本地存储配置：`workhub.storage.local-path: data/uploads`（`application.yml` 第 111 行），绑定到 `StorageProperties`，只有 `AuthService` 使用。
  - 需求附件：`AttachmentService.storeSingleFile` 已把内容写入 `pm_attachment.file_content`，`storage_path` 置空；读取时库内没有内容才按 `storage_path` 回退读文件。
  - 替换附件：`POST /api/attachments/{id}/replace` → `replaceIntakeMaterial`，用新文件内容更新 `file_content`、`file_size`、`file_sha256` 并把 `storage_path` 置空，随后删除原路径上的文件。
- 当前接口、数据及配置：`pm_attachment` 以 `biz_type` 与 `biz_id` 区分业务，现有业务类型都是 `INTAKE_*`；`sys_user.avatar_url` 是字符串，用户管理可以手工填写。运行库只读核对：`sys_user` 共 18 个用户，都没有头像；`pm_attachment` 中只存路径且文件仍在的只有附件 355；`data/` 下 28 个文件中，27 个与库内内容的 SHA-256 一致，只有附件 355 在库内没有内容。
- 事实来源：上述源码；本机 `data/` 目录；运行库只读查询。
- 尚未核实的假设：无。

## 2. 系统架构

- 总体方案和设计目标：头像复用 `pm_attachment` 表存内容，系统不再向仓库目录写任何文件。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| `AttachmentService` | 以业务类型 `USER_AVATAR` 保存、读取、删除头像 | 头像校验和用户资料更新 | 用户 ID 与上传文件 → 附件 ID；附件 ID → 内容与类型 | `AttachmentMapper`、`pm_attachment` |
| `AuthService` | 头像校验、更新 `sys_user.avatar_url`、清理旧头像、审计 | 文件读写 | 不变 | `AttachmentService` |
| `AuthController`、安全配置 | 接口路径与匿名访问规则 | — | 不变 | 不变 |

- 上传流程：校验（规则不变）→ 写入一行 `USER_AVATAR` 附件 → 把头像地址设为 `/api/auth/avatars/{附件ID}` → 旧地址形如 `/api/auth/avatars/{数字}` 时删除对应附件行 → 写审计 → 返回用户资料。整个过程在 `uploadAvatar` 现有的事务内完成。
- 读取流程：路径参数必须是数字 ID → 读取该附件且业务类型必须为 `USER_AVATAR` → 返回内容和上传时的 `Content-Type`。不满足时沿用现有错误“头像文件不存在”。
- 附件 355 入库：用现有替换接口提交同一份文件，由 `replaceIntakeMaterial` 写入内容并删除原文件，不新增代码。
- 前端页面、交互与后端规则边界：前端把头像地址当作不透明的 URL 使用，无需改动。

## 3. 契约与数据模型

- API/事件提供方与消费者：
  - `POST /api/auth/me/avatar` 的请求和响应不变。
  - `GET /api/auth/avatars/{id}` 的路径参数由文件名改为附件 ID（数字）。运行库没有存量头像地址，不保留旧格式。
- 新增业务类型：`pm_attachment.biz_type` 增加取值 `USER_AVATAR`，`biz_id` 为用户 ID。需求附件的查询都按 `INTAKE_*` 类型过滤，不会带出头像。
- `sys_user.avatar_url`：上传头像时为 `/api/auth/avatars/{附件ID}`；用户管理手工填写的地址原样保存，不受影响。
- 配置：删除 `workhub.storage.local-path` 和 `StorageProperties`，`application.yml` 与 `@EnableConfigurationProperties` 同步移除。
- 已知限制：
  - 用户管理里把头像地址改成其他地址后，此前上传的头像附件行不会被清理。
  - MCP `get_intake_attachment` 不带需求 ID 时按附件 ID 读取，因此也能读到头像；图片只返回元信息。
- 数据库变更：无结构变更，无迁移脚本。唯一的数据写入是附件 355 由替换接口完成。
- 幂等、并发、重试和重复执行行为：重复上传会依次替换头像并删除上一行的附件行，与原来删除旧文件的行为一致。

## 4. 非功能约束

- 安全与权限：头像接口的匿名访问范围、上传大小和类型校验、审计记录均不变。
- 性能、容量和时效：读取头像由读磁盘改为按主键读取一行，单张不超过 2 MB。
- 失败处理、降级与补偿：写库失败时上传事务回滚，接口报错，不会留下半成品。
- 日志、监控、告警与审计：沿用现有审计，不新增日志。

## 5. 交付与回滚

- 影响系统和发布顺序：合入代码 → 用户授权后重新加载运行实例 → 用替换接口把附件 355 入库并核对 → 删除 `data/` → 更新文档。实际执行：2026-11-25 按用户指示先完成附件 355 入库（17:49）与 `data/` 删除（18:01），其余步骤待头像改造确认后进行。
- 灰度或开关：无。
- 数据备份与前后校验：删除 `data/` 前核对附件 355 的内容哈希与原文件一致；其余 27 个文件库内已有相同内容，不做备份。
- 回滚条件、步骤与不可逆部分：还原代码并重新加载后，头像恢复写本地文件，已入库的头像行不再被读取。不可逆部分只有一处：附件 355 的原文件会被替换接口删除，内容已在库中。
- 生效并核实后需要更新的文档：`现状.md`、迭代日志。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1 → §2 上传与读取流程；目标 2 → §2 附件 355 入库；目标 3 → §3 配置与 §5 发布顺序。
- 明确非范围是否被保持：294 条丢失的旧附件、读取回退逻辑、表结构、前端、两个文件夹功能均未改动。
- 待确认问题及影响（用户 2026-11-25 确认第 2、3 点，2026-11-26 确认第 1 点）：
  1. 头像复用 `pm_attachment`，不新建表。已确认。
  2. 附件 355 通过现有替换接口入库，会删除原文件；由 AI 在登录页面内经接口操作。已确认并执行。
  3. 删除整个 `data/`。已确认并执行。
- 契约、ADR 及 Tasks 的关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)；无 ADR。
