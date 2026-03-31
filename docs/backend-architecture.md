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

建议分层：

```text
src/main/java/cn/aslight/workhub/
  common/
  config/
  security/
  controller/
  service/
  mapper/
  domain/
    auth/
    project/
    sprint/
    release/
    workitem/
    intake/
    attachment/
    system/
  integration/
    wecom/
    ai/
  job/
```

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
- AI 整理结果
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
- AI 整理单独接口

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

## 8. AI 处理原则

AI 输入：

- 企微原始消息
- 人工粘贴内容
- 附件文本摘要
- 已抽取的结构化审批字段

AI 输出：

- 标题建议
- 描述建议
- 类型建议
- 优先级建议
- 项目归属建议
- 验收标准建议
- 任务拆解建议

约束：

- AI 输出落入草稿区，不直接写正式表。
- AI 输出必须保留原始结果，便于回溯。

## 9. 工程约束

1. Controller 不写核心业务编排。
2. Service 层按业务动作拆分，不写超长万能方法。
3. Mapper 只负责数据访问，不承载业务规则。
4. 所有关键写操作必须产生日志或留痕记录。
5. 表结构变更必须同步更新文档和初始化 SQL。
