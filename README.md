# WorkHub Server

WorkHub 后端仓库，负责认证鉴权、项目与工作项领域建模、企微入站接收、群机器人通知、需求截图/附件结构化增强、研发需求代码影响分析、附件处理、系统配置、支付接入配置管理以及统一 API 输出。

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
- 研发需求 GitLab 代码影响分析与工作项草稿编排
- 系统级配置项管理
- 附件上传、关联和审计留痕
- 支付渠道、商户、参数、敏感凭据、秘钥与项目绑定配置管理

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
- `/api/system/configs`
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

- 项目、迭代、版本的列表、详情、新增、编辑，项目支持维护项目组和 GitLab 组名
- 工作项列表、详情、新增、编辑
- 工作项跟踪记录、状态流转、指派动作
- 需求管理列表、详情
- 截图/附件上传录入待整理箱
- 待整理箱审批型结构化字段抽取与展示
- 需求类型、需求摘要自动抽取
- 研发需求自动生成研发分支名
- 需求详情支持维护关联禅道地址
- 数据提取/运维类需求支持生成 SQL 草稿，供人工确认后自行执行
- 数据提取/运维类需求确认完成时支持上传交付数据文件，并可在需求详情中下载
- 研发需求支持按项目组 GitLab 组名拉取该组下全部仓库，先总结需求变化点，再结合跨微服务代码按变化点生成系统功能改造任务草稿、工时、涉及系统标签和负责人建议
- 研发需求任务评估异步执行，前端提交后立即返回，后台完成后再更新草稿和状态
- 研发需求任务评估前会先把需求截图、附件正文摘要、结构化字段和原始内容归档到项目知识库 raw 目录，再整理成 wiki 目录下的需求迭代 Markdown；该 Markdown 只做材料归档，不总结需求点，AI 后续再基于该 Markdown 梳理需求点和任务拆解
- 研发任务评估当前只强制录入评估工时和测试工时，系统自动合计总工时；任务拆解、负责人、系统标签、改动点等信息为可选辅助
- 研发工作项草稿支持人工调整对应需求变化点、改造功能、系统标签、改动点、工时、负责人、优先级、预计开始日期、截止日期和风险，也支持新增、删除改造功能；存在任务项时，确认后按调整后的草稿创建正式工作项
- 研发工作项草稿支持 AI 对话调整，人工确认后才可能创建正式工作项；仅确认工时时只推进需求到待排期，不创建正式工作项
- 禅道同步接口已预留，当前只记录预留状态，不调用外部禅道
- 需求管理阶段动作推进与历史留痕
- 需求详情查看/编辑历史
- 企微回调入待整理箱
- 上传截图/附件后可选调用本地 Codex CLI 增强结构化字段抽取
- 需求详情支持新增、替换和删除需求截图/需求附件
- 非图片附件在服务端先提取正文摘要，再与图片一起用于 Codex 结构化增强
- 待整理详情可查看附件 enrichment 状态、失败摘要和最近更新时间
- 支付渠道、商户、项目绑定、参数、敏感凭据、秘钥的配置管理接口
- 系统配置项管理接口，支持普通文本和加密秘钥配置
- 商户按多用途绑定到现有项目，并支持一个商户号被多个项目复用
- 绑定可维护共享协议、分账、代偿回购、子商户、被分账商户、退款主户等关联商户关系
- 敏感参数、账户/交易/接口凭据和秘钥按应用层加密落库，接口只返回脱敏值、版本和指纹
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
- `WORKHUB_AI_CODEX_CLI_MODEL=gpt-5.5`
- `WORKHUB_AI_CODEX_CLI_REASONING_EFFORT=xhigh`
- `WORKHUB_AI_CODEX_CLI_TIMEOUT_SECONDS=0`
- `WORKHUB_STORAGE_LOCAL_PATH=data/uploads`
- `WORKHUB_PAYMENT_MASTER_KEY=请替换为生产主密钥`

说明：

- 外部通知发送失败不会影响核心业务写入。
- 企微回调已按 `external_message_id` 做幂等保护。
- 截图和附件会以本地文件方式保存，并通过 `/api/attachments/{id}/download` 下载。
- 支付域会在服务启动时自动补齐 `pay_*` 配置表；如数据库账号无 `CREATE TABLE` 权限，需先手工执行初始化 SQL。
- 支付敏感参数、敏感凭据和秘钥统一做应用层加密存储，详情接口仅返回脱敏值、指纹和版本信息，不直接回显明文。
- 开启 `WORKHUB_AI_CODEX_CLI_ENABLED=true` 后，截图/附件上传成功后会异步触发 intake enrichment：
  - 图片附件通过 `codex exec -i` 直接传给 Codex
  - `pdf/docx/doc/xlsx/xls` 这类非图片附件先由服务端提取正文摘要，再拼进 Codex prompt
  - enrichment 只会更新 `structured_data_json` 和 enrichment 状态，不会直接创建正式工作项
  - 识别结果会自动归类需求类型为 `研发需求` 或 `数据提取/运维`
  - 对 `研发需求` 会自动生成研发分支名，格式为 `feature/summary_words_yyyyMMdd`
  - `数据提取/运维` 可在详情页人工触发 SQL 草稿生成；系统只生成只读 SQL 文本，不连接数据库、不执行 SQL
  - `WORKHUB_AI_CODEX_CLI_TIMEOUT_SECONDS=0` 表示异步等待 Codex 完成，不额外截断 CLI 运行时长
- enrichment 状态字段：
  - `PENDING`：已入队，等待异步增强
  - `RUNNING`：正在提取附件正文或调用 Codex
  - `SUCCEEDED`：增强完成；若有局部附件提取失败，失败摘要会写入 `enrichmentErrorSummary`
  - `FAILED`：异步增强失败，可在 `GET /api/intake/{id}` 中查看失败摘要，并可通过 `POST /api/intake/{id}/enrichment/retry` 重新入队识别
- 当前 fallback 规则仍然是 `字段: 值` 文本抽取，但 enrichment 完成后会把非图片附件的文本摘要一并写入 `structured_data_json.attachmentSummaries`，供后续人工确认使用。
- 需求管理页面展示的是 intake 阶段的派生生命周期，不替代正式工作项状态：
  - 识别前只看 `enrichmentStatus`：`PENDING / RUNNING / FAILED / SUCCEEDED`
- 识别成功后 `demandStatus` 进入业务生命周期：`已收录 -> 待澄清 -> 待评估 -> 待排期 -> 待设计 -> 开发中 -> 测试中 -> 待上线 -> 待验收 -> 已完成`，也可在详情中填写关闭原因后进入 `终止关闭`
  - 业务阶段不再通过通用编辑接口直接修改，而是通过显式阶段动作推进，例如：
    - `已收录 -> 开始澄清 -> 待澄清`
    - `待澄清 -> 澄清完成 -> 待评估`
    - `待评估 -> 评估完成 -> 待排期`
    - `待排期 -> 排期确认 -> 待设计`
    - `待设计 -> 设计完成 -> 开发中`
    - `开发中 -> 提交测试 -> 测试中`
    - `测试中 -> 测试通过 -> 待上线`
    - `待上线 -> 确认上线 -> 待验收`
    - `待验收 -> 验收通过 -> 已完成`
    - `任一非终态 -> 关闭需求 -> 终止关闭`，关闭原因必填并写入修改历史
  - 需求录入时必须填写研发负责人，后端单独落库到 `pm_intake_record.development_owner_user_name`，不再写入 `structured_data_json`
- 研发需求代码影响分析依赖系统配置和项目组 GitLab 组名：
  - 全局系统配置分组：`gitlab.global`
  - 全局 GitLab Web/API 地址：`configKey=webApiUrl`、`valueType=TEXT`
  - 全局 Git SSH 主机：`configKey=sshHost`、`valueType=TEXT`
  - 全局 GitLab Access Token：`configKey=accessToken`、`valueType=SECRET`
  - Token 通过应用层加密落库，接口只回显脱敏值
  - 项目管理中的项目组必须维护 `gitlabGroupName`，该值对应 GitLab group 命名空间
  - 分析时按需求录入或识别出的项目组匹配项目；历史数据缺少项目组时，可在任务评估弹框补选项目组并写回结构化数据
  - 后端会通过 `gitlab.global.webApiUrl + accessToken` 查询该 GitLab group 下全部可访问仓库，包括子组项目
  - 每次分析都会从 GitLab fetch/reset 最新代码到 `data/git-cache/group-*` 隔离缓存，并把该项目组下全部仓库目录交给 Codex CLI；Codex CLI 不读取当前开发工作区
  - AI 输出的 `systemTags` 必须从该项目组仓库名中选择，用于表示任务涉及的微服务/前端系统
  - 可选项目知识库配置分组：`knowledge.project`
  - 项目知识库地址：`configKey=vaultPath`、`valueType=TEXT`，默认值为 `/Users/aslight/Obsidian Vault/Company Obsidian Vault`
  - 任务评估时后端会先按 `raw/requirements/{项目组}/{项目名称}/{年份}/{审批编号}/source.md` 写入或覆盖散落原始材料，再按 `wiki/projects/{项目组}/{项目名称}/需求迭代/{年份}/{审批编号}-{需求名称}.md` 写入或覆盖整理后的需求迭代 Markdown；该步骤不总结需求点，任务评估草稿会返回 wiki 文件路径和 Obsidian 打开链接
  - 随后后端会从知识库 Markdown/TXT 文档中检索少量相关摘要放入 AI prompt，用于辅助判断不确定业务口径；不让 Codex 直接扫描整个知识库目录
  - Codex CLI 系统配置分组：`ai.codexCli`
  - Codex CLI 模型：`configKey=model`、`valueType=TEXT`，默认值为 `gpt-5.5`
  - Codex CLI 推理强度：`configKey=reasoningEffort`、`valueType=TEXT`，默认值为 `xhigh`，表示最高推理强度
  - `ai.codexCli` 系统配置优先级高于 `application.yml` 和环境变量中的 Codex CLI 默认值
  - AI 输出的是研发工作项草稿；任务项为可选辅助，只有草稿里存在任务项并点击确认后才会调用工作项创建接口生成正式工作项
  - 当前禅道同步能力是预留接口，不会向外部禅道发起请求
- 服务启动时会自动检查并补齐 `pm_intake_record` 的以下兼容字段和索引：
  - `external_message_id` 唯一索引
  - `structured_data_json`
  - `enrichment_status`
  - `enrichment_error_summary`
  - `enrichment_updated_at`
  - `deleted`
  - `deleted_at`
  - `deleted_by`
- 如果数据库账号没有 `ALTER TABLE` 权限，服务会启动失败；这时需要先手工补齐上述字段和索引，再重新启动。

注意：

- 结构化增强只负责补充需求字段，不直接写入正式工作项。
- SQL 草稿只用于辅助人工处理数据运维需求，必须由人工确认库表、字段、权限和导出范围后自行执行。
- 状态变更、转迭代、转版本、指派等动作使用显式业务接口，不走大而全的通用 update。
- 所有企业微信入站内容必须先入待整理箱。
