# WorkHub Server

WorkHub 后端仓库，负责认证鉴权、项目与工作项领域建模、企微入站接收、群机器人通知、需求截图/附件结构化增强、附件处理、支付接入配置管理以及统一 API 输出。

当前阶段先沉淀项目边界与实施基线，后续开发默认遵循以下文档：

- `docs/project-charter.md`
- `docs/backend-architecture.md`
- `docs/implementation-roadmap.md`

后端职责边界：

- 本地账号密码登录和 JWT 会话
- 项目、迭代、版本、工作项、跟踪记录、状态流转 API
- 企业微信回调接入和原始消息入库
- 群机器人通知
- 需求截图和附件的结构化增强编排
- 附件上传、关联和审计留痕
- 支付渠道、商户、参数、秘钥与项目绑定配置管理

当前技术方向：

- Java 25
- Spring Boot 4.0.x
- MyBatis Spring Boot Starter 4.0.x
- Druid Spring Boot 4 Starter 1.2.28
- MySQL 8

当前工程已拆成多模块 Maven 结构：

- `workhub-model`：请求体、响应体、实体和值对象
- `workhub-support`：配置属性模型
- `workhub-dao`：MyBatis 数据访问接口
- `workhub-service`：业务编排、领域动作、AI/企微集成与 JWT 令牌服务
- `workhub-controller`：HTTP 接口、统一响应和异常处理
- `workhub-job`：定时任务
- `workhub-bootstrap`：Spring Boot 启动入口、运行时配置和资源文件

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
- `/api/payment`
- `/api/wecom/callback/messages`
- JWT 安全过滤器
- 初始化建表 SQL

## 启动方式

```bash
mvn -pl workhub-bootstrap spring-boot:run
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
- 需求管理列表、详情
- 截图/附件上传录入待整理箱
- 待整理箱审批型结构化字段抽取与展示
- 需求类型、需求摘要自动抽取
- 研发需求自动生成研发分支名
- 需求详情支持维护关联禅道地址
- 需求管理阶段动作推进与历史留痕
- 需求详情查看/编辑历史
- 企微回调入待整理箱
- 上传截图/附件后可选调用本地 Codex CLI 增强结构化字段抽取
- 非图片附件在服务端先提取正文摘要，再与图片一起用于 Codex 结构化增强
- 待整理详情可查看附件 enrichment 状态、失败摘要和最近更新时间
- 支付渠道、商户、项目绑定、参数、秘钥的配置管理接口
- 商户按用途绑定到现有项目，并支持一个商户号被多个项目复用
- 敏感参数和秘钥按应用层加密落库，接口只返回脱敏值、版本和指纹
- 企业微信群机器人通知
- 到期提醒定时扫描

## Controller 接口说明

- controller 层接口文档见 `docs/controller-api.md`

## 可选配置

可通过环境变量启用群机器人通知和到期提醒：

- `WORKHUB_WECOM_ROBOT_ENABLED=true`
- `WORKHUB_WECOM_ROBOT_WEBHOOK=你的企微机器人 webhook`
- `WORKHUB_REMINDER_ENABLED=true`
- `WORKHUB_REMINDER_DUE_SOON_HOURS=24`
- `WORKHUB_REMINDER_DEDUPE_MINUTES=60`
- `WORKHUB_REMINDER_CRON=0 */30 * * * *`
- `WORKHUB_AI_CODEX_CLI_ENABLED=true`
- `WORKHUB_AI_CODEX_CLI_COMMAND=codex`
- `WORKHUB_AI_CODEX_CLI_MODEL=gpt-5.3-codex`
- `WORKHUB_AI_CODEX_CLI_TIMEOUT_SECONDS=0`
- `WORKHUB_STORAGE_LOCAL_PATH=data/uploads`
- `WORKHUB_PAYMENT_MASTER_KEY=请替换为生产主密钥`

说明：

- 外部通知发送失败不会影响核心业务写入。
- 企微回调已按 `external_message_id` 做幂等保护。
- 截图和附件会以本地文件方式保存，并通过 `/api/attachments/{id}/download` 下载。
- 支付域会在服务启动时自动补齐 `pay_*` 配置表；如数据库账号无 `CREATE TABLE` 权限，需先手工执行初始化 SQL。
- 支付敏感参数和秘钥统一做应用层加密存储，详情接口仅返回脱敏值、指纹和版本信息，不直接回显明文。
- 开启 `WORKHUB_AI_CODEX_CLI_ENABLED=true` 后，截图/附件上传成功后会异步触发 intake enrichment：
  - 图片附件通过 `codex exec -i` 直接传给 Codex
  - `pdf/docx/doc/xlsx/xls` 这类非图片附件先由服务端提取正文摘要，再拼进 Codex prompt
  - enrichment 只会更新 `structured_data_json` 和 enrichment 状态，不会直接创建正式工作项
  - 识别结果会自动归类需求类型为 `研发需求` 或 `数据提取/运维`
  - 对 `研发需求` 会自动生成研发分支名，格式为 `feature/req-审批编号`
  - `WORKHUB_AI_CODEX_CLI_TIMEOUT_SECONDS=0` 表示异步等待 Codex 完成，不额外截断 CLI 运行时长
- enrichment 状态字段：
  - `PENDING`：已入队，等待异步增强
  - `RUNNING`：正在提取附件正文或调用 Codex
  - `SUCCEEDED`：增强完成；若有局部附件提取失败，失败摘要会写入 `enrichmentErrorSummary`
  - `FAILED`：异步增强失败，可在 `GET /api/intake/{id}` 中查看失败摘要
- 当前 fallback 规则仍然是 `字段: 值` 文本抽取，但 enrichment 完成后会把非图片附件的文本摘要一并写入 `structured_data_json.attachmentSummaries`，供后续人工确认使用。
- 需求管理页面展示的是 intake 阶段的派生生命周期，不替代正式工作项状态：
  - 识别前只看 `enrichmentStatus`：`PENDING / RUNNING / FAILED / SUCCEEDED`
  - 识别成功后 `demandStatus` 进入业务生命周期：`已收录 -> 待评估 -> 已评估 -> 研发中 -> 待测试 -> 测试中 -> 待验收 -> 待上线 -> 已上线`
  - 业务阶段不再通过通用编辑接口直接修改，而是通过显式阶段动作推进，例如：
    - `已收录/待评估 -> 评估工时 -> 已评估`
    - `已评估 -> 开始研发 -> 研发中`
    - `研发中 -> 提交测试 -> 待测试`
    - `待测试 -> 开始测试 -> 测试中`
    - `测试中 -> 提交验收 -> 待验收`
    - `待验收 -> 确认验收 -> 待上线`
    - `待上线 -> 确认上线 -> 已上线`
- 服务启动时会自动检查并补齐 `pm_intake_record` 的以下兼容字段和索引：
  - `external_message_id` 唯一索引
  - `structured_data_json`
  - `enrichment_status`
  - `enrichment_error_summary`
  - `enrichment_updated_at`
- 如果数据库账号没有 `ALTER TABLE` 权限，服务会启动失败；这时需要先手工补齐上述字段和索引，再重新启动。

注意：

- 结构化增强只负责补充需求字段，不直接写入正式工作项。
- 状态变更、转迭代、转版本、指派等动作使用显式业务接口，不走大而全的通用 update。
- 所有企业微信入站内容必须先入待整理箱。
