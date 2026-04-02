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
- `pm_project`
- `pm_sprint`
- `pm_release`
- `pm_work_item`
- `pm_work_item_follow_up`
- `pm_work_item_transition_log`
- `pm_intake_record`
- `pm_attachment`

建议关键字段：

### `pm_project`

- 项目编码
- 项目名称
- 项目类型
- 项目状态
- 项目描述

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
- 转正式工作项 ID

## 5. API 分组

- `/api/auth/*`
- `/api/projects/*`
- `/api/sprints/*`
- `/api/releases/*`
- `/api/work-items/*`
- `/api/intake/*`
- `/api/attachments/*`
- `/api/users/*`
- `/api/wecom/callback/*`

动作型接口原则：

- 状态流转单独接口
- 指派单独接口
- 转版本单独接口
- 转迭代单独接口
- 上传增强异步处理，不单独暴露 AI 草稿接口

## 6. 安全方案

第一阶段：

- 本地账号密码登录
- 密码 BCrypt 存储
- 登录成功返回 JWT
- 网关能力先不引入
- 细粒度权限先不做，先做登录拦截和基础身份识别

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

约束：

- 增强结果只更新 `structured_data_json` 和 enrichment 状态，不直接写正式表。
- 增强过程必须保留原始文本和附件，便于回溯。
- 需求管理列表展示的 `demandStatus` 优先使用人工维护的需求阶段状态；历史数据或未维护数据再回退到系统按 enrichment 状态、结构化结果和是否已转工作项推导的结果。
- 需求管理通过显式阶段动作推进需求状态，不提供通用大而全编辑接口；动作执行过程中可补录工时和关键时间，并需记录修改历史。

## 9. 工程约束

1. Controller 不写核心业务编排。
2. Service 层按业务动作拆分，不写超长万能方法。
3. Mapper 只负责数据访问，不承载业务规则。
4. 所有关键写操作必须产生日志或留痕记录。
5. 表结构变更必须同步更新文档和初始化 SQL。
