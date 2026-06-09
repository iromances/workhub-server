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
- 可选 ELK 日志监控，通过 ECS JSON 文件日志、Filebeat、Elasticsearch 和 Kibana 实现，不参与核心业务写入链路

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
workhub-mcp-server/
  src/main/java/cn/aslight/workhub/mcp/
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
- AI 运维只读工具的核心实现放在 `workhub-mcp-server`。主服务通过 HTTP MCP runtime 复用该实现并读取最新资源配置；MCP 对外访问统一走主服务 HTTP runtime。
- Spring Boot 启动、运行时配置和资源统一放在 `workhub-bootstrap`。

## 1.1 MCP 只读通道

MCP 只读通道用于在 AI 客户端、受控运维资源和 WorkHub 需求管理数据之间建立安全代理层。当前统一使用 HTTP transport：

- HTTP runtime：内嵌在 `workhub-server` 主服务中，通过 `/api/mcp/runtime` 接收 MCP JSON-RPC 请求，直接读取主库中已启用的 MCP 资源配置，配置变更后下一次调用生效。

当前 MCP runtime 只暴露受控只读工具，项目根目录 `MCP_CAPABILITIES.md` 维护完整能力清单：

- `list_mcp_targets`：返回业务线、业务线系统清单、全局中台系统清单、环境、数据库目标、服务器目标、知识库根路径和 GitLab 配置摘要。
- `get_business_line_context`：按业务线名称或编码返回 GitLab group、代码缓存路径规则、知识库路径规则、数据库目标和服务器目标。
- `get_knowledge_context`：按业务线返回项目知识库根路径、业务线 raw/wiki 根目录和需求归档路径约定。
- `search_intakes`：按审批编号和/或需求名称搜索需求管理记录，返回匹配摘要。
- `get_intake_detail`：按 intakeId 读取单条需求管理详情。
- `run_readonly_query`：按目标和 profile 执行受控只读 SQL。
- `get_service_status`：按服务器目标、profile 和服务白名单读取服务状态。
- `read_service_logs`：按服务器目标、profile 读取白名单服务日志或白名单日志文件 tail。
- `SERVER` 资源必须绑定业务线下的系统列表 `systemNames`，用于支持同一服务器目标承载一个或多个微服务系统；兼容字段 `systemName` 表示首个系统。

资源选择遵循：

```text
businessLineCodes + environmentCode + targetKey + profileKey
```

MCP 资源与业务线是多对多绑定关系。`mcp_resource_config.business_line_code` 仅保留首个业务线用于兼容和排序，实际查询业务线可访问的 DB/server 目标时以 `mcp_resource_business_line` 绑定表为准。

安全原则：

- 不把数据库密码、GitLab Access Token、堡垒机密码、服务器 SSH 密码或私钥暴露给 AI；管理端接收的密码使用 `workhub.mcp.master-key` 加密入库。HTTP runtime 调用时在服务端解密供受控工具使用；GitLab token 仅在服务端需要访问 GitLab 时内部加载使用，对 AI 只返回 `accessTokenConfigured`。
- 数据库 profile 应绑定只读 MySQL 账号，MCP SQL 校验只是第二层防护。
- MCP runtime 不暴露任意 SSH 命令；服务器访问仅限白名单服务状态、白名单服务日志和白名单日志文件 tail。
- 数据库堡垒机转发和服务器密码 SSH 通过 `sshpass` + 环境变量传递密码，避免把密码放进命令参数。
- 调用结果会做行数、字节数、日志行数和超时限制。
- 每次调用写入 `logs/mcp-audit.jsonl` 或 `WORKHUB_MCP_AUDIT_LOG` 指定文件。

## 4. 核心数据模型

建议主表：

- `sys_user`
- `sys_config_item`
- `pm_business_line`
- `pm_business_line_member`
- `pm_project_involved_system`
- `pm_project`
- `pm_sprint`
- `pm_release`
- `pm_work_item`
- `pm_work_item_follow_up`
- `pm_work_item_transition_log`
- `pm_intake_record`
- `pm_intake_todo`
- `pm_intake_development_analysis`
- `pm_intake_work_item_relation`
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
- `ops_monitor_config`
- `ops_system_alert_subsystem`
- `ops_system_alert_event`

建议关键字段：

### `pm_project`

- 项目编码
- 项目名称
- 项目类型
- 业务线
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

### `ops_monitor_config`

- 监测类型：当前支持 `XXL_JOB`，`MQ` 后续接入
- 监测 key
- 业务线与环境
- XXL-JOB 数据库名，采集时按业务线与环境自动选择 MCP 只读数据库目标
- 上次采集状态、消息和采集时间
- XXL-JOB 列表统计优先读取 Admin 自带 `xxl_job_log_report` 日报表快照；失败 Job 明细只在详情、手动采集和定时告警时读取最近日志

### `ops_system_alert_subsystem`

- 业务线与环境
- 子系统名称
- 日志服务名，例如未来 ELK 中的 `service.name`
- 启用状态和备注
- 系统预警关注子系统独立维护，不复用项目涉及系统、全局中台系统或 XXL-JOB 执行器配置

### `ops_system_alert_event`

- 业务线、环境、子系统名称和日志服务名
- 日志级别、标题、错误消息、异常类型、堆栈
- traceId、requestId
- 发生时间
- 来源类型，当前为本地 `LOCAL`，未来 ELK 同步写入后仍通过本地表查询

### `sys_notification`

- 接收人用户名
- 通知类型
- 标题和内容
- 业务线与环境
- 去重 key
- 已读状态与已读时间
- 创建时间

已约定配置：

- `gitlab.global.webApiUrl`：GitLab Web/API 地址
- `gitlab.global.sshHost`：Git SSH 主机
- `gitlab.global.accessToken`：GitLab Access Token，按 `SECRET` 加密落库
- `knowledge.project.vaultPath`：项目知识库本地路径，任务评估时写入单需求迭代 Markdown，并检索相关 Markdown/TXT 摘要
- `ai.codexCli.model`：Codex CLI 模型，默认 `gpt-5.5`，优先级高于应用配置
- `ai.codexCli.reasoningEffort`：Codex CLI 推理强度，默认 `xhigh`，表示最高推理强度，优先级高于应用配置
- `intake.requirementFolder.basePath`：需求管理打开需求文件夹时使用的本地基础目录，默认 `/Users/aslight/Desktop/进行中的需求`

### `pm_business_line`

- 业务线名称
- GitLab 组名或命名空间，同时作为业务线编码使用
- 说明
- 启用状态

### `pm_business_line_member`

- 业务线
- 成员用户名
- 成员展示名
- 启用状态

### `pm_project_involved_system`

- 系统范围：`BUSINESS_LINE` 表示业务线系统，`MIDDLE_PLATFORM` 表示全局中台系统
- 业务线：业务线系统必填，中台系统固定为空
- 系统名称
- 说明
- 启用状态
- 排序
- 业务线系统可从业务线维护的 GitLab group 及子组仓库同步补齐；同步只新增缺失项，不覆盖已有手工维护项或停用项。中台系统仍由人工维护。

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
- 暂停前状态
- 暂停原因
- 暂停日期

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

### `pm_intake_todo`

- 需求 ID
- 待办标题
- 待办内容
- 待办处理状态：`待处理/处理中/已完成/已取消`
- 处理人
- 计划处理时间
- 完成时间
- 处理结果

### `pm_intake_development_analysis`

- 待整理需求 ID
- 项目 ID
- 业务线
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

### `pay_merchant_purpose`

- 商户 ID
- 支持用途编码

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
- 需求管理通过显式阶段动作推进需求状态，不提供通用大而全编辑接口；动作执行过程中可补录关键时间，提交测试时录入实际开发工时和实际提测日期，并需记录修改历史。
- 需求暂停和恢复通过独立动作接口执行，不混入阶段推进接口；暂停必须记录暂停前状态、暂停原因和暂停日期，恢复只能回到暂停前状态，并写入修改历史。
- 需求关闭也必须通过显式阶段动作执行，关闭原因必填，状态进入 `终止关闭` 后不再允许继续推进。
- 研发需求澄清阶段可选择是否启用 AI 辅助澄清；默认在 `开始澄清` 后自动提交，也支持手动重新分析。后端按业务线维护的 GitLab 组名读取该组下全部可访问仓库，结合需求材料、项目知识库和代码输出逐项待确认项与风险项。AI 澄清结果作为辅助参考，不阻塞人工执行 `澄清完成`；`澄清完成` 后自动触发 AI 任务评估。
- 研发需求任务评估按业务线维护的 GitLab 组名读取该组下全部可访问仓库，包括子组项目；后端从 GitLab 拉取最新代码到隔离缓存，并把全部仓库目录交给 Codex CLI。执行 AI 评估前，后端必须先将需求截图/附件正文摘要、结构化字段和原始内容归档为 `raw/requirements/{业务线}/{项目名称}/{年份}/{审批编号}/source.md`，再整理为 `wiki/projects/{业务线}/需求迭代/{项目名称}/{年份}/{审批编号}-{需求名称}.md`。该 wiki Markdown 只做散落需求材料归档，不总结需求点、不拆任务，并在任务评估草稿中返回 wiki 路径和 Obsidian 打开链接。AI 工作流必须第二步再基于该需求 Markdown 和已确认的澄清结果输出内敛且不重复的需求点，第三步再带着需求点和核查问题定向查看代码、配置、接口和数据模型，最后拆解独立任务项；任务项可覆盖代码改造、配置变更、数据变更、SQL 脚本、运维操作和验证项，并记录系统标签、改动对象、改地点、判断依据、置信度、工时和负责人建议。知识库检索必须先用业务线中文名圈定资料范围，再按需求中出现的业务/项目路径标签收敛范围，避免同知识库下其他业务线或同业务线其他业务资料污染评估。系统标签表示系统归属或影响范围，必须来自当前业务线系统清单或全局中台系统清单，不等同于一定需要改代码。
- 研发任务评估只强制人工确认总预估工时和测试工时，系统按任务项自动汇总开发预估工时并合计总工时；存在任务项时，任务涉及系统必填。需求和任务评估只关联业务线及对应系统，不依赖 `pm_project` 正式项目；需求本身不单独维护涉及系统字段，需求列表和详情从最新研发任务草稿的 `systemTags` 自动去重汇总。
- 需求管理支持按 `intake.requirementFolder.basePath` 打开本地需求文件夹，目录名为 `审批编号-需求名称`，用于沉淀需求相关本地文件；同时支持按 `knowledge.project.vaultPath` 打开知识库开发方案文件夹，目录为 `wiki/projects/{业务线}/需求迭代/{项目名称}/{年份}`。
- 研发任务确认后需求进入待排期；只有草稿已关联正式项目 ID 时才创建正式工作项，并通过 `pm_intake_work_item_relation` 保留需求与正式任务的强关联。需求详情从该关联表展示已创建的正式研发任务。任务负责人录入后同步写入独立研发人员资源，后续任务评估负责人下拉框从研发人员资源和当前草稿负责人合并展示。
- 禅道同步当前为预留能力，只记录接口调用和预留状态，不向外部禅道写入。

## 9. 支付接入配置原则

- 支付配置域只负责“渠道/商户/参数/敏感凭据/秘钥/项目绑定”的管理，不直接承担真实支付交易编排。
- 商户号按渠道和环境维度管理，生产与测试配置不得混用在同一条商户记录中。
- 商户号需要先维护可支持的用途集合；项目与商户是多对多关系，通过多用途绑定表达“某项目在该商户支持用途的子集下使用哪个商户”。
- 共享协议商户、正常分账商户、代偿回购分账商户、子商户、被分账商户和退款主户等关系必须结构化存储到绑定关联关系中，不能只写备注。
- Excel 中的账户密码、交易密码、接口用户名/密码、AESKEY、私钥密码等敏感凭据独立加密保存，接口只返回脱敏值和指纹。
- 秘钥采用版本化保存，新增秘钥默认视为轮换行为，需要保留历史版本和审计日志。
- 新增秘钥支持 JSON 明文录入和 multipart 文件上传两种入口；文件上传必须显式声明 `TEXT` 或 `BINARY`，文本文件按 UTF-8 内容加密落库，二进制文件按 Base64 编码后加密落库，不按扩展名或 MIME 类型自动推断。

## 10. 工程约束

1. Controller 不写核心业务编排。
2. Service 层按业务动作拆分，不写超长万能方法。
3. Mapper 只负责数据访问，不承载业务规则。
4. 所有关键写操作必须产生日志或留痕记录。
5. 表结构变更必须同步更新文档和初始化 SQL。
6. 可观测性基础设施只能作为旁路能力接入，采集失败不得影响核心台账 API 可用性。
