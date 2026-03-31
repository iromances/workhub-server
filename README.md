# WorkHub Server

WorkHub 后端仓库，负责认证鉴权、项目与工作项领域建模、企微入站接收、群机器人通知、AI 整理编排、附件处理以及统一 API 输出。

当前阶段先沉淀项目边界与实施基线，后续开发默认遵循以下文档：

- `docs/project-charter.md`
- `docs/backend-architecture.md`
- `docs/implementation-roadmap.md`

后端职责边界：

- 本地账号密码登录和 JWT 会话
- 项目、迭代、版本、工作项、跟踪记录、状态流转 API
- 企业微信回调接入和原始消息入库
- 群机器人通知
- AI 整理编排和草稿输出
- 附件上传、关联和审计留痕

当前技术方向：

- Java 25
- Spring Boot 4.0.x
- MyBatis Spring Boot Starter 4.0.x
- Druid Spring Boot 4 Starter 1.2.28
- MySQL 8

当前已落地的最小可运行骨架：

- `/api/system/ping`
- `/api/auth/login`
- `/api/auth/me`
- `/api/projects`
- `/api/sprints`
- `/api/releases`
- `/api/work-items`
- `/api/intake-records`
- `/api/intake`
- `/api/wecom/callback/messages`
- JWT 安全过滤器
- 初始化建表 SQL

## 启动方式

```bash
mvn spring-boot:run
```

默认后端地址：

- `http://127.0.0.1:8080`

默认演示账号：

- 用户名：`admin`
- 密码：`admin123`

## 当前已落地能力

- 项目、迭代、版本的列表、详情、新增、编辑
- 工作项列表、详情、新增、编辑
- 工作项跟踪记录、状态流转、指派动作
- 待整理箱列表、详情、人工录入、粘贴整理
- 截图/附件上传录入待整理箱
- 待整理箱审批型结构化字段抽取与展示
- 企微回调入待整理箱
- AI 整理草稿生成和任务拆解建议
- 待整理转正式工作项
- 转正式工作项时将 AI 拆解建议写入工作项跟踪记录
- 上传截图/附件后可选调用本地 Codex CLI 增强结构化字段抽取
- 非图片附件在服务端先提取正文摘要，再与图片一起用于 Codex 结构化增强
- 待整理详情可查看附件 enrichment 状态、失败摘要和最近更新时间
- 企业微信群机器人通知
- 到期提醒定时扫描

## 可选配置

可通过环境变量启用群机器人通知和到期提醒：

- `WORKHUB_WECOM_ROBOT_ENABLED=true`
- `WORKHUB_WECOM_ROBOT_WEBHOOK=你的企微机器人 webhook`
- `WORKHUB_REMINDER_ENABLED=true`
- `WORKHUB_REMINDER_DUE_SOON_HOURS=24`
- `WORKHUB_REMINDER_DEDUPE_MINUTES=60`
- `WORKHUB_REMINDER_CRON=0 */30 * * * *`
- `WORKHUB_AI_PROVIDER=heuristic|openrouter|minimax`
- `WORKHUB_AI_OPENROUTER_ENABLED=true`
- `WORKHUB_AI_OPENROUTER_API_KEY=...`
- `WORKHUB_AI_OPENROUTER_MODEL=openai/gpt-4o-mini`
- `WORKHUB_AI_MINIMAX_ENABLED=true`
- `WORKHUB_AI_MINIMAX_API_KEY=...`
- `WORKHUB_AI_MINIMAX_MODEL=M2-her`
- `WORKHUB_AI_CODEX_CLI_ENABLED=true`
- `WORKHUB_AI_CODEX_CLI_COMMAND=codex`
- `WORKHUB_AI_CODEX_CLI_MODEL=gpt-5.3-codex`
- `WORKHUB_AI_CODEX_CLI_TIMEOUT_SECONDS=120`
- `WORKHUB_STORAGE_LOCAL_PATH=data/uploads`

说明：

- 外部通知发送失败不会影响核心业务写入。
- 企微回调已按 `external_message_id` 做幂等保护。
- AI 草稿接口支持 `provider=openrouter|minimax|heuristic` 覆盖当前默认 provider。
- 截图和附件会以本地文件方式保存，并通过 `/api/attachments/{id}/download` 下载。
- 开启 `WORKHUB_AI_CODEX_CLI_ENABLED=true` 后，截图/附件上传成功后会异步触发 intake enrichment：
  - 图片附件通过 `codex exec -i` 直接传给 Codex
  - `pdf/docx/doc/xlsx/xls` 这类非图片附件先由服务端提取正文摘要，再拼进 Codex prompt
  - enrichment 只会更新 `structured_data_json` 和 enrichment 状态，不会直接创建正式工作项
- enrichment 状态字段：
  - `PENDING`：已入队，等待异步增强
  - `RUNNING`：正在提取附件正文或调用 Codex
  - `SUCCEEDED`：增强完成；若有局部附件提取失败，失败摘要会写入 `enrichmentErrorSummary`
  - `FAILED`：异步增强失败，可在 `GET /api/intake/{id}` 中查看失败摘要
- 当前 fallback 规则仍然是 `字段: 值` 文本抽取，但 enrichment 完成后会把非图片附件的文本摘要一并写入 `structured_data_json.attachmentSummaries`，供 AI 草稿阶段继续使用。
- 如果你的数据库是在本次变更前初始化的，需要为 `pm_intake_record` 补以下字段：
  - `external_message_id` 唯一索引
  - `structured_data_json`
  - `enrichment_status`
  - `enrichment_error_summary`
  - `enrichment_updated_at`

注意：

- AI 只负责生成整理建议，不直接写入正式工作项。
- 状态变更、转迭代、转版本、指派等动作使用显式业务接口，不走大而全的通用 update。
- 所有企业微信入站内容必须先入待整理箱。
