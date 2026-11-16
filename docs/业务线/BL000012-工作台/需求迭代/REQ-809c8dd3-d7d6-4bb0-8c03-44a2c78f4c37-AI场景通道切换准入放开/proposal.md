# Proposal：AI 场景通道切换准入放开（ZCode 本地仓库读取）

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-809c8dd3-d7d6-4bb0-8c03-44a2c78f4c37 |
| 需求名称 | AI 场景通道切换准入放开（ZCode 本地仓库读取） |
| 涉及仓库与功能域 | workhub-server：AI 网关准入检查（`DefaultAiGatewayClient.isConfigured`）+ 单测 |
| 所属项目或业务线 | WorkHub 平台自身功能，业务线 BL000012-工作台 |
| 唯一需求目录 | `docs/业务线/BL000012-工作台/需求迭代/REQ-809c8dd3-d7d6-4bb0-8c03-44a2c78f4c37-AI场景通道切换准入放开/` |
| 版本/状态 | v1 已确认（2026-11-12），随实施更新 |
| 目标系统 | WorkHub Server |
| 原始需求来源及版本 | 2026-11-12 用户会话：澄清分析切到 ZCode 通道报"AI 场景未配置或已停用"；用户明确"我就是要切换通道用""AI 任务里配置的都要支持供应商、通道、模型厂商等的切换" |
| 目标环境/分支 | workhub-server `codex/development-20261102` |
| 负责人/评审人 | 用户（sh） |

## 1. Why：背景与问题

AI 场景（`ai_use_case_config`）在配置页允许把场景绑定到任意已启用的 Provider（供应商/通道/CLI 类型自由选择，保存校验 `AiUseCaseConfigService` 依据 `Definition.allowedCliTypes` 均放行双 CLI），但运行时 `DefaultAiGatewayClient.isConfigured` 对 **ZCode + LOCAL_REPOSITORY_READ**（澄清分析、研发方案分析/调整、SDD 设计评估四个场景）无条件返回 false，导致：能保存、能切换、一点执行就报"AI 场景未配置或已停用"。而 ZCode 跑这类场景的执行链路早已实现并有测试覆盖（`executeCli` ZCODE 分支经 workhub MCP 只读工具桥接仓库读取；`DefaultAiGatewayClientTest.zcodeRepositoryScenePassesRepositoryContextToNativeClient`）——准入检查与执行能力脱节。不解决则用户无法按需把研发分析类场景切到 ZCode/GLM 通道。

现状核对（2026-11-12 运行库只读查询）：9 个 AI 场景全部启用，其中 8 个已绑定 ZCode 通道（先行通-GLM CLI），仅 `agent.api.step` 绑 Codex CLI。

## 2. What：目标与范围

### 本期目标

1. `isConfigured` 对 ZCode + LOCAL_REPOSITORY_READ 改为按真实前置条件判断：`workhub.mcp.access-token` 已配置且 MCP 运行时（`McpRuntimeService`）就绪、工具清单非空——前置齐备即可用，不再一刀切禁止。
2. 保持其余边界不变：LOCAL_REPOSITORY_READ 场景仍仅允许 CLI 通道（API 模型无本地仓库访问能力，`requireChannel` 拦截维持）；模型厂商/供应商本来就是配置项（`use_case.model` 或 `provider.default_model`），无需改动。

### 本期交付范围

1. `DefaultAiGatewayClient.isConfigured` 一处修改；`DefaultAiGatewayClientTest` 补 `isConfigured` 两个用例（前置齐备 → true；缺 token 或 MCP 运行时未就绪 → false）。
2. 文档：迭代日志、notes 过程记录。

### 明确不做

1. 不放开 LOCAL_REPOSITORY_READ 场景使用 API 通道（需要 API 侧 function-calling 桥接，另立需求）。
2. 不改 `ZCodeCliClient` 执行协议、MCP 只读工具集与提示词约束。
3. 不改 AI 场景配置页保存校验（`allowedCliTypes` 已放行双 CLI，无实际拦截）。

### 约束与依赖

- 依赖：`workhub.mcp.access-token`（环境变量 `WORKHUB_MCP_ACCESS_TOKEN` 或 `workhub.mcp.access-token` 配置）已配置；MCP 运行时已启动。未配置时场景在 AI 配置页可用性检测与执行前校验会按 false/明确报错处理，不静默降级为无工具分析。

## 3. Done：成功标准

| 编号 | 可观察的验收条件 | 核对方式 | 来源/确认人 |
| --- | --- | --- | --- |
| AC-01 | ZCode Provider + LOCAL_REPOSITORY_READ 场景，在 token 与 MCP 运行时就绪时 `isConfigured` 返回 true，澄清分析可发起并经 MCP 只读工具完成 | 单测 + 页面实际执行一次澄清分析 | 用户确认"就是要切换通道用" |
| AC-02 | 缺 `workhub.mcp.access-token` 或 MCP 运行时未就绪时 `isConfigured` 返回 false；执行路径仍给出明确中文报错（既有行为） | 单测 | 既有语义保持 |
| AC-03 | Codex CLI、API（MODEL_ONLY/IMAGE_INPUT 场景）等既有准入结果不受影响 | 既有单测全数通过 | 回归 |
