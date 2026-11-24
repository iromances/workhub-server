# Proposal：ZCode 终稿格式校正 60 秒取消修复

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-98a19dce-f751-4bde-a19e-7e6b2c841e62 |
| 需求名称 | ZCode 终稿格式校正 60 秒取消修复 |
| 涉及仓库与功能域 | workhub-server（workhub-service：`ZCodeTaskConversation`）；无前端、无数据库变更 |
| 所属项目或业务线 | BL000012-工作台（WorkHub 平台） |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-98a19dce-f751-4bde-a19e-7e6b2c841e62-ZCode终稿格式校正60秒取消修复/ |
| 版本/状态 | 草案，待确认 |
| 目标系统 | WorkHub 后端（ZCode CLI 原生协议适配层） |
| 原始需求来源及版本 | 2026-11-16 用户报告「消费分期巡检失败」，当日排查定位 |
| 目标环境/分支 | 生产（本机 WorkHub 运行库 PROD）；研发分支 codex/development-20261102 |
| 负责人/评审人 | 负责人 sh；评审人待确认 |

## 1. Why：背景与问题

- 当前问题及证据：11-14 至 11-16，消费分期、新消费分期、叉车、保费分期 4 个巡检 Agent 共 9 轮运行以同一错误失败：「CLI 协议执行失败……ZCode 协议请求被拒绝，workspace/generateText，阶段 FORMATTING，错误码 -32603：Model request was cancelled.」9 次失败的格式校正耗时全部为 60–62 秒，同期 14 次成功为 20–52 秒。
- 根因（已用本地复现实锤）：ZCode CLI 0.17.1 对 `workspace/generateText` 的实现中，请求参数不带 `operationId` 时挂 `AbortSignal.timeout(60000)`（常量 `Goi=6e4`，无任何配置入口，且取消被标记不可重试）；带 `operationId` 时改走可显式取消通道、无超时。WorkHub 的格式校正请求（`ZCodeTaskConversation` 构造 `wh-format-result`）恰好不带 `operationId`。模型响应慢时（近期 GLM-5.3-Flash + reasoningLevel=max 普遍 40–75 秒）请求在 60 秒被 CLI 掐断，WorkHub 按协议错误终止整轮运行，主轮 4–8 分钟调查成果被丢弃。先行通网关本身无 60 秒限制（curl 非流式直连 180 秒完整返回 200）。
- 受影响用户/角色：所有经 CLI 通道运行的数字员工巡检/结构化任务（Agent 巡检、需求研发评估、结构化抽取等共用 `ZCodeTaskConversation`）。
- 不解决的后果：模型响应速度波动时巡检持续间歇性失败；失败率随模型负载升高。

## 2. What：目标与范围

### 本期目标

1. 格式校正请求带上 `operationId`，绕开 CLI 内部 60 秒默认超时，使该步骤时限回归 WorkHub 自己的时间预算（任务 `maxRunSeconds` 与 provider `callTimeoutSeconds`，当前 1800 秒）。

### 本期交付范围

1. `ZCodeTaskConversation` FORMATTING 阶段 `workspace/generateText` 请求参数新增 `operationId`（会话内唯一，基于本轮 `inputId`）。
2. 单元测试断言格式校正请求携带非空 `operationId`。
3. SDD 归档与迭代日志、过程记录。

### 明确不做

1. 不改格式校正的推理档位与重试策略（修复后时限充裕，暂不引入；如后续仍有需要另行立项）。
2. 不修改 ZCode CLI 应用本身（`/Applications/ZCode.app` 不动）。
3. 不做数据库、前端变更。

### 约束与依赖

- 依赖的代码和文档：`ZCodeTaskConversation.java`（协议实现）、`ZCodeTaskConversationTest.java`；CLI 行为依据为 0.17.1 bundle 逆向 + 本地协议级复现实验（见 design 验证节）。
- 兼容性：`operationId` 为 CLI 协议一等参数（配套 `workspace/cancelGenerateText`）；CLI 对未知参数宽容（WorkHub 现有请求已带 CLI 可选字段），版本升级风险低。

## 3. 验收标准

1. AC-01：单元测试断言格式校正请求 params 含非空 `operationId`，且现有 `ZCodeTaskConversationTest` 全部通过。
2. AC-02：本地按 WorkHub 同样协议路径复现：带 `operationId` 的格式校正长请求（>60 秒生成）完整返回，不再出现 -32603 取消（复现实验已在修复前完成对照：不带 60.8s 被取消、带 75.6s 成功）。
3. AC-03：workhub-service 模块测试全绿，无与本次 diff 相关的失败。

## 4. 风险与开放问题

- `operationId` 语义依据打包 bundle 逆向确认（0.17.1）；CLI 升级后需回归验证该分支行为是否保持。
- 同一 CLI 进程内重复 `operationId` 会被拒（-32600 already active）；格式校正每会话最多一次，取值含唯一 `inputId`，不构成风险。
