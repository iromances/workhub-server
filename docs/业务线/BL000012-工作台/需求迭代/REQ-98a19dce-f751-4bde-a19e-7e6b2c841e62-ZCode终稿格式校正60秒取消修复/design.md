# Design：ZCode 终稿格式校正 60 秒取消修复

## 1. 方案

`ZCodeTaskConversation` FORMATTING 阶段发出的 `wh-format-result`（`workspace/generateText`）请求 params 新增 `operationId`，取值 `"wh-format-result-" + inputId`（会话内唯一，每会话最多一次格式校正）。

依据（CLI 0.17.1 bundle 逆向 + 协议级复现实验）：

- `generateWorkspaceTextImpl`：`abortSignal ?? AbortSignal.timeout(60000)`——调用方不传 signal 即挂 60 秒定时器，超时取消归类 `ModelRequestCancelled`、`retryable=false`（CLI 自带 10 次重试不会重试取消）。
- RPC 层 `withWorkspaceGenerateTextSignal`：params 提取 `operationId`（非空字符串）→ 注册 `AbortController`，signal 仅响应 `workspace/cancelGenerateText`，无超时；无 `operationId` → 直接执行，落入上述 60 秒分支。
- 实验对照（按 WorkHub 相同私有目录/env 启动 CLI，同一大负载格式校正请求）：不带 `operationId` 60.8s 返回 `-32603 Model request was cancelled`（CLI 日志 `durationMs:60001 reason:cancelled`）；带 `operationId` 75.6s 完整返回 `finishReason=stop`。先行通网关无 60 秒限制（curl 非流式直连 180s 返回 200），排除服务端因素。

改动点：`ZCodeTaskConversation.java` FORMATTING 请求构造处一个字段 + 一行注释；不涉及其他调用方（巡检、研发评估、结构化抽取共用该会话类，单点覆盖）。

## 2. 契约与数据

- 协议契约：`workspace/generateText` params 新增字段 `operationId: string`，为 CLI 已定义参数（非 WorkHub 私有扩展）；不新增接口、不改数据库。
- 超时归属：格式校正时限从 CLI 内部 60 秒回归 WorkHub 预算（`AgentNativeCliAdapter` 的 `maxRunSeconds`/`callTimeoutSeconds` deadline，当前 1800 秒），该预算链路不变。

## 3. 验证

1. 单元测试：`ZCodeTaskConversationTest` 现有格式化用例补充断言 `operationId` 存在且非空；全类回归。
2. 协议级复现（修复前已完成对照，作为 AC-02 证据，实验脚本与日志见 tasks 附录说明）：带 `operationId` 后 >60 秒长生成请求完整成功。
3. 模块测试：workhub-service 全量回归。

## 4. 回滚

单字段新增，回滚即移除该字段；CLI 对参数宽容，不存在中间态不兼容。回滚后恢复现状（格式校正重新暴露于 CLI 60 秒取消线）。
