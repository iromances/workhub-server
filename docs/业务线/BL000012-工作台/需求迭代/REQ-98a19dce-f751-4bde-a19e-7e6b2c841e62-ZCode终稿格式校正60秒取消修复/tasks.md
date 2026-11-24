# Tasks：ZCode 终稿格式校正 60 秒取消修复

| 项目 | 内容 |
| --- | --- |
| 范围与规格版本 | proposal v1 / design v1，2026-11-16 |
| 确认结论 | 用户于 2026-11-16 会话中确认按 operationId 方案实施（方案即 proposal/design 所载内容，范围无增减） |
| 确认人/日期 | sh / 2026-11-16 |

## 任务清单

| # | 任务 | 状态 | 证据/结果 |
| --- | --- | --- | --- |
| 0 | 排查定位（已完成）：失败分布统计、根因逆向、复现实验 | 完成 | 9 次失败全部 60–62s、成功 20–52s；bundle `Goi=6e4` + `operationId` 分支；对照复现 60.8s 取消 / 75.6s 成功 |
| 1 | `ZCodeTaskConversation` FORMATTING 请求加 `operationId` | 完成 | `wh-format-result` params 新增 `operationId: "wh-format-result-" + inputId`，附注释说明 CLI 60 秒默认超时分支 |
| 2 | `ZCodeTaskConversationTest` 补充 `operationId` 断言并全类回归 | 完成 | `malformedStructuredOutput...` 用例新增断言；类内 20/20 通过（surefire：Tests run: 20, Failures: 0, Errors: 0） |
| 3 | workhub-service 模块测试 | 完成 | `mvn -pl workhub-service -am test`：1179 例，2 失败均为迭代日志（REQ-9f3c2a84，2026-11-14）在案既有问题（KnowledgeArchiveWorkflow 路径口径、BusinessLogSearch ELK 范围未配置 BL000001.PROD），与本次 diff 无交集 |
| 4 | 迭代日志、docs/notes.md 过程记录 | 完成 | 迭代日志 2026-11-16 条目；notes.md 排查与修复过程记录 |
| 5 | 部署后观察：下一轮巡检（消费分期等）格式校正不再因 60 秒取消失败 | 待部署 | 验证口径：pm_agent_run 新失败不再含「Model request was cancelled」，FORMATTING 阶段耗时可超 60s 正常完成 |

## 复现实验附录（2026-11-16，本机）

按 WorkHub 相同私有目录/env（HOME/ZCODE_LOG_DIR/provider 配置副本）启动 `zcode.cjs app-server --stdio`，同一大负载格式校正请求（约 9.7K 字符、要求逐条保留、GLM-5.3-Flash + reasoningLevel=max）：

- 不带 `operationId`：60.8s 返回 `-32603 Model request was cancelled`；CLI 日志 `model.request.failed durationMs:60001 reason:cancelled retryable:false`。
- 带 `operationId`：75.6s 完整返回，`finishReason=stop`，文本 9445 字符。
- 服务端排除：curl 以 Anthropic 协议非流式直连先行通网关 180.4s 返回 HTTP 200（34,932 字节），网关无 60 秒限制。

## 剩余风险

- CLI 升级改变 `operationId` 分支行为时需回归（ proposal 风险节）。
