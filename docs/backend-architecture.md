# WorkHub 后端架构说明

## 1. 技术栈

- Java 25
- Spring Boot 4.0.x
- MyBatis Spring Boot Starter 4.0.x
- Druid Spring Boot 4 Starter 1.2.28
- MySQL 8
- JWT
- BCrypt
- Maven

外部集成：

- 企业微信自建应用回调
- 企业微信群机器人 webhook
- AI 模型 HTTP API
- 本地 Codex CLI
- GitLab 仓库只读拉取
- 本地项目知识库需求迭代 Markdown 写入与摘要检索
- 支付渠道网关配置管理

## 2. 设计目标

1. 后端以领域模型和业务动作清晰为优先，不做大而杂的 CRUD 堆砌。
2. 所有正式工作项都必须可审计、可追踪、可回溯。
3. 所有外部入站内容先进入待整理箱，不直接污染核心业务表。
4. AI 只做辅助整理，不拥有最终写入权限。

## 3. 包结构建议

建议基础包名：

`cn.aslight.workhub`

建议多模块与分层：

```text
workhub-model/
  src/main/java/cn/aslight/workhub/model/
workhub-support/
  src/main/java/cn/aslight/workhub/config/
workhub-dao/
  src/main/java/cn/aslight/workhub/dao/
workhub-service/
  src/main/java/cn/aslight/workhub/service/
  src/main/java/cn/aslight/workhub/integration/
  src/main/java/cn/aslight/workhub/security/JwtTokenService.java
workhub-controller/
  src/main/java/cn/aslight/workhub/controller/
  src/main/java/cn/aslight/workhub/common/
  src/main/java/cn/aslight/workhub/security/JwtAuthenticationFilter.java
workhub-job/
  src/main/java/cn/aslight/workhub/job/
workhub-bootstrap/
  src/main/java/cn/aslight/workhub/WorkhubServerApplication.java
  src/main/java/cn/aslight/workhub/config/
  src/main/resources/
```

约束：

- 所有对前端和第三方暴露的 HTTP 接口统一放在 `workhub-controller`。
- 所有前后端契约对象、实体和值对象统一放在 `workhub-model`。
- MyBatis 接口统一放在 `workhub-dao`。
- 业务编排、规则和动作服务统一放在 `workhub-service`。
- 定时任务统一放在 `workhub-job`。
- Spring Boot 启动、运行时配置和资源统一放在 `workhub-bootstrap`。

## 4. 核心数据模型

建议主表：

- `sys_user`
- `sys_config_item`
- `pm_project_group_member`
- `pm_project`
- `pm_sprint`
- `pm_release`
- `pm_work_item`
- `pm_work_item_follow_up`
- `pm_work_item_transition_log`
- `pm_intake_record`
- `pm_intake_development_analysis`
- `pm_attachment`
- `pay_channel`
- `pay_merchant_account`
- `pay_merchant_param`
- `pay_merchant_secret`
- `pay_merchant_credential`
- `pay_project_merchant_binding`
- `pay_project_merchant_binding_purpose`
- `pay_project_merchant_binding_relation`
- `pay_operation_log`

建议关键字段：

### `pm_project`

- 业务线编码
- 业务线名称
- 项目编码
- 项目名称
- 项目类型
- 项目组
- 项目状态
- 项目描述

### `sys_config_item`

- 配置分组
- 配置键
- 配置名称
- 值类型
- 明文值
- 密文值
- 脱敏值
- 启用状态
- 备注

已约定配置：

- `gitlab.global.webApiUrl`：GitLab Web/API 地址
- `gitlab.global.sshHost`：Git SSH 主机
- `gitlab.global.accessToken`：GitLab Access Token，按 `SECRET` 加密落库
- `knowledge.project.vaultPath`：项目知识库本地路径，任务评估时写入单需求迭代 Markdown，并检索相关 Markdown/TXT 摘要
- `ai.codexCli.model`：Codex CLI 模型，默认 `gpt-5.5`，优先级高于应用配置
- `ai.codexCli.reasoningEffort`：Codex CLI 推理强度，默认 `xhigh`，表示最高推理强度，优先级高于应用配置

### `pm_project_group_member`

- 项目组
- 成员用户名
- 成员展示名
- 启用状态

### `pm_work_item`

- 工作项编号
- 项目 ID
- 迭代 ID
- 版本 ID
- 类型
- 标题
- 描述
- 来源类型
- 来源渠道
- 优先级
- 紧急程度
- 当前状态
- 创建人
- 负责人
- 跟进人
- 提出人
- 验收标准
- 计划开始时间
- 计划结束时间
- 实际完成时间

### `pm_intake_record`

- 来源类型
- 来源渠道
- 外部消息 ID
- 原始文本
- 结构化需求 JSON
- 发送人
- 发送时间
- 结构化增强结果
- 整理状态
- 历史关联工作项 ID（兼容保留）

### `pm_intake_development_analysis`

- 待整理需求 ID
- 项目 ID
- 项目组
- GitLab 仓库地址
- 分析状态
- 研发工作项草稿 JSON
- 禅道同步状态
- 禅道同步说明

### `pay_channel`

- 渠道编码
- 渠道名称
- 厂商名称
- 状态
- 描述

### `pay_merchant_account`

- 渠道 ID
- 商户号
- 商户名称
- 环境
- AppId
- 结算主体
- 状态

### `pay_project_merchant_binding`

- 项目 ID
- 商户 ID
- 主用途编码（兼容字段）
- 优先级
- 是否默认
- 绑定状态

### `pay_project_merchant_binding_purpose`

- 绑定 ID
- 用途编码

### `pay_project_merchant_binding_relation`

- 绑定 ID
- 关联商户 ID
- 关联角色
- 关系名称
- 优先级
- 备注

### `pay_merchant_credential`

- 商户 ID
- 凭据键
- 凭据名称
- 凭据类型
- 密文
- 脱敏值
- 指纹
- 状态
- 备注

### `pay_merchant_secret`

- 商户 ID
- 秘钥名称
- 秘钥类型
- 密文
- 脱敏值
- 指纹
- 版本号
- 生效区间
- 状态

## 5. API 分组

- `/api/auth/*`
- `/api/projects/*`
- `/api/sprints/*`
- `/api/releases/*`
- `/api/work-items/*`
- `/api/intake/*`
- `/api/system/configs/*`
- `/api/payment/*`
- `/api/attachments/*`
- `/api/users/*`
- `/api/wecom/callback/*`

动作型接口原则：

- 状态流转单独接口
- 指派单独接口
- 转版本单独接口
- 转迭代单独接口
- 上传增强异步处理，正式写入必须经过人工确认
- 研发需求代码影响分析单独暴露草稿接口，但只生成草稿，不直接创建正式工作项

## 6. 安全方案

第一阶段：

- 本地账号密码登录
- 密码 BCrypt 存储
- 登录成功返回 JWT
- 网关能力先不引入
- 细粒度权限先不做，先做登录拦截和基础身份识别
- 支付敏感参数、敏感凭据和秘钥采用应用层加密落库，接口只返回脱敏值、指纹和版本摘要

## 7. 企业微信接入设计

入站：

- 企业微信自建应用回调接收消息/事件
- 原始报文持久化到 `pm_intake_record`
- 转化为待整理箱记录

出站：

- 企业微信群机器人 webhook
- 用于状态变更、负责人变更、到期提醒、版本发布提醒

原则：

- 企微回调只负责收件和落库，不在回调线程内做重业务处理。
- 回调验签、去重、重试策略必须优先设计。

## 8. 结构化增强原则

增强输入：

- 企微原始消息
- 需求截图
- 附件文本摘要
- 已抽取的结构化审批字段

增强输出：

- 审批编号
- 提出人
- 提交时间
- 需求类型
- 需求摘要
- 需求名称
- 需求描述
- 所在部门
- 业务线
- 备注等结构化字段
- 数据提取/运维类需求的 SQL 草稿、参数说明、推断假设、待确认问题和风险提示

约束：

- 增强结果只更新 `structured_data_json` 和 enrichment 状态，不直接写正式表。
- SQL 草稿生成只写入 `structured_data_json.sqlDraft` 和修改历史，不连接数据库、不执行 SQL。
- SQL 草稿必须面向人工复制执行，默认只允许只读 `SELECT` 查询，并提示人工确认库表环境、字段口径、数据权限和导出范围。
- 增强过程必须保留原始文本和附件，便于回溯。
- 需求管理列表展示的 `demandStatus` 优先使用人工维护的需求阶段状态；历史数据或未维护数据再回退到系统按 enrichment 状态和结构化结果推导的结果。
- 需求管理通过显式阶段动作推进需求状态，不提供通用大而全编辑接口；动作执行过程中可补录工时和关键时间，并需记录修改历史。
- 需求关闭也必须通过显式阶段动作执行，关闭原因必填，状态进入 `终止关闭` 后不再允许继续推进。
- 研发需求任务评估按项目组维护的 GitLab 组名读取该组下全部可访问仓库，包括子组项目；后端从 GitLab 拉取最新代码到隔离缓存，并把全部仓库目录交给 Codex CLI。执行 AI 评估前，后端必须先将需求截图/附件正文摘要、结构化字段和原始内容归档为 `raw/requirements/{项目组}/{项目名称}/{年份}/{审批编号}/source.md`，再整理为 `wiki/projects/{项目组}/{项目名称}/需求迭代/{年份}/{审批编号}-{需求名称}.md`。该 wiki Markdown 只做散落需求材料归档，不总结需求点、不拆任务，并在任务评估草稿中返回 wiki 路径和 Obsidian 打开链接。AI 工作流必须第二步再基于该需求 Markdown 输出内敛且不重复的需求点，第三步再带着需求点和核查问题定向查看代码、配置、接口和数据模型，最后拆解独立任务项；任务项可覆盖代码改造、配置变更、数据变更、SQL 脚本、运维操作和验证项，并记录系统标签、改动对象、改动点、判断依据、置信度、工时和负责人建议。知识库检索必须先用项目组中文名圈定资料范围，再按需求中出现的业务/项目路径标签收敛范围，避免同知识库下其他项目组或同项目组其他业务资料污染评估。系统标签表示系统归属或影响范围，优先来自项目组下的仓库名，不等同于一定需要改代码。
- 研发任务评估只强制人工确认评估工时和测试工时，系统自动合计总工时；任务拆解、负责人、系统标签、改动点等为可选辅助。只有草稿中存在任务项且经人工确认后，才允许调用正式工作项创建接口。
- 禅道同步当前为预留能力，只记录接口调用和预留状态，不向外部禅道写入。

## 9. 支付接入配置原则

- 支付配置域只负责“渠道/商户/参数/敏感凭据/秘钥/项目绑定”的管理，不直接承担真实支付交易编排。
- 商户号按渠道和环境维度管理，生产与测试配置不得混用在同一条商户记录中。
- 项目与商户是多对多关系，通过多用途绑定表达“某项目在一个或多个支付能力下使用哪个商户”。
- 共享协议商户、正常分账商户、代偿回购分账商户、子商户、被分账商户和退款主户等关系必须结构化存储到绑定关联关系中，不能只写备注。
- Excel 中的账户密码、交易密码、接口用户名/密码、AESKEY、私钥密码等敏感凭据独立加密保存，接口只返回脱敏值和指纹。
- 秘钥采用版本化保存，新增秘钥默认视为轮换行为，需要保留历史版本和审计日志。

## 10. 工程约束

1. Controller 不写核心业务编排。
2. Service 层按业务动作拆分，不写超长万能方法。
3. Mapper 只负责数据访问，不承载业务规则。
4. 所有关键写操作必须产生日志或留痕记录。
5. 表结构变更必须同步更新文档和初始化 SQL。
