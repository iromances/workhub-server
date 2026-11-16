# Design：AI 场景通道切换准入放开（ZCode 本地仓库读取）

| 项目 | 内容 |
| --- | --- |
| 需求编码与唯一需求目录 | REQ-809c8dd3-d7d6-4bb0-8c03-44a2c78f4c37；`docs/业务线/BL000012-工作台/需求迭代/REQ-809c8dd3-d7d6-4bb0-8c03-44a2c78f4c37-AI场景通道切换准入放开/` |
| 涉及仓库与功能域 | workhub-server：`DefaultAiGatewayClient` 准入检查 + 单测 |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | v1（2026-11-12） |
| 状态 | v1 已确认，随实施更新 |
| 当前事实核对环境、分支、时间 | workhub-server `codex/development-20261102`（2026-11-12 源码 + 运行库 ai_use_case_config 只读核查） |

## 1. 现状与证据

- `DefaultAiGatewayClient.isConfigured` L120：`if (AiCliType.is(provider, "ZCODE") && definition.capability() == LOCAL_REPOSITORY_READ) return false;`——无条件拦截。
- 执行路径 `executeCli` L371-384：ZCODE 分支对 LOCAL_REPOSITORY_READ 已实现 MCP 只读桥接（prompt 追加 `repositoryHint`，传 `repositoryMcpUrl()`/`mcpAccessToken()`/`repositoryToolNames()`），前置缺失时抛明确中文报错（"ZCode 通道本地仓库读取需配置 workhub.mcp.access-token"/"MCP 运行时未就绪"）。
- 测试 `zcodeRepositoryScenePassesRepositoryContextToNativeClient` 已覆盖 ZCode 仓库读取执行全链路；`zcodeRepositorySceneRequiresMcpTokenBeforeStarting` 覆盖缺 token 执行失败。
- 定义层 `AiUseCaseDefinitions`：四个 LOCAL_REPOSITORY_READ 场景 `allowedChannelTypes=CLI`、`allowedCliTypes={CODEX,ZCODE}`；运行库 9 场景 8 个已绑 ZCode 通道。

## 2. 方案

`isConfigured` 中将无条件 `return false` 改为前置条件检查（与执行路径前置一致）：

```java
if (AiCliType.is(provider, "ZCODE") && definition.capability() == AiUseCaseDefinitions.Capability.LOCAL_REPOSITORY_READ) {
    if (!StringUtils.hasText(mcpAccessToken())) return false;
    McpRuntimeService runtime = mcpRuntimeProvider.getIfAvailable();
    if (runtime == null || runtime.toolNames().isEmpty()) return false;
}
```

- 依赖注入（`mcpRuntimeProvider`、`mcpProperties`、环境 token）均已存在，无新增构造参数。
- 前置缺失 → false（配置页可用性检测不通过、生成器抛"AI 场景未配置或已停用"）；前置齐备 → 进入既有模型/启用检查。执行期前置突变的兜底仍是执行路径的明确报错。

## 3. 契约与数据模型

无 API/DB 变更；`isConfigured(boolean)` 语义按上述收窄。幂等、只读检查。

## 4. 非功能约束

- 安全面不变：ZCode 经 workhub_task MCP 只读三工具（list_repository_files / read_repository_text_file / search_repository_text）读代码，授权目录由 `repositoryHint` 注入；`isConfigured` 不放宽提示词变量白名单、不放宽输出 schema 边界。
- 性能：`runtime.toolNames()` 为内存目录读取，无外部调用。

## 5. 交付与回滚

- 单类单方法改动，回滚即还原该检查；无数据迁移。
- 生效后更新：迭代日志、notes。

## 6. 一致性与待确认

- AC-01~03 ↔ §2 改动与既有测试；非范围（API 通道跑仓库读取、ZCodeCliClient 协议改动）保持。
- 待确认：无（用户 2026-11-12 明确"就是要切换通道用""都要支持切换"）。
