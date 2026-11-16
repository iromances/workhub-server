# Tasks：CLI 通道限制清理与 ZCode 仓库读取

## 1. 元信息

- 需求编码：REQ-d97d16b2-1380-4d78-8fb5-a6022efdd7b1
- 需求名称与唯一需求目录：CLI 通道限制清理与 ZCode 仓库读取；docs/业务线/BL000012-工作台/需求迭代/REQ-d97d16b2-1380-4d78-8fb5-a6022efdd7b1-CLI通道限制清理与ZCode仓库读取/
- 涉及仓库与功能域：workhub-server / workhub-mcp-server、workhub-service
- 所属项目或业务线：BL000012-工作台
- Proposal/Design 版本：草案 v1
- 目标环境与分支：本地开发环境；codex/development-20261102
- 任务计划状态：实施已获用户计划批准，验证进行中
- 负责人/评审人：用户；AI 协作（实施）

## 2. 任务总览与依赖

| 任务 | 交付物 | 状态 | 证据 |
| --- | --- | --- | --- |
| T-01 | SDD 规格三份 | 已完成 | 本目录 proposal/design/tasks |
| T-02 | RepositoryFileService（白名单只读） | 已完成 | workhub-service/.../service/mcp/RepositoryFileService.java |
| T-03 | McpRuntimeService 注册三工具 + setter 注入 | 已完成 | registerRepositoryFileTools / setRepositoryFileService |
| T-04 | AiUseCaseDefinitions 三场景放开 | 已完成 | allowedCliTypes=CODEX+ZCODE |
| T-05 | 网关删守卫 + MCP 注入 + 分支/提示附注 | 已完成 | ObjectProvider 延迟解析防循环依赖；repositoryHint |
| T-06 | ZCodeCliClient execute 10 参透传 | 已完成 | 连接测试传 null/null/List.of() 保持现状 |
| T-07 | 测试同步 | 已完成 | 网关 3 用例（无 token 报错/仓库透传/速度回归）+ RepositoryFileServiceTest 4 用例 |
| T-08 | 定向单测 | 已完成 | 39 项执行全部通过（见 §3） |
| T-09 | 端到端（澄清分析绑先行通） | 待执行 | 需配置 + 页面操作 |
| T-10 | 交付回写（本文件、迭代日志） | 进行中 | — |

## 3. 计划验证方式与真实结果

- 定向单测（javac 编译至 target/test-classes + JUnit Platform MiniRunner，规避全量 test-compile 被在途 AgentWorkbenchServiceTest 阻塞）：
  - DefaultAiGatewayClientTest / ZCodeTaskConversationTest / ZCodeCliLocalProtocolTest / CodexCliStructuredExtractorTest：41 用例，35 执行全部通过，0 失败（6 跳过为既有 @Disabled/条件用例）
  - RepositoryFileServiceTest：4 用例全部通过
- 全模块 `mvn -pl workhub-service -am compile` 通过（369 类），在途 Agent 域改动同期收敛
- 遗留：全量 test-compile 仍被 AgentWorkbenchServiceTest 阻塞（在途需求测试未跟上 main 的 AgentTaskUpdateRequest 引用），非本需求范围

## 4. 覆盖与评审

- AC-01→T-09（待端到端）；AC-02→T-07/T-08；AC-03→T-07/T-08；AC-04→T-08（文本任务与连接测试用例回归）；AC-05→T-08（速度回归用例）
- 端到端执行步骤（T-09）：
  1. 配置 `workhub.mcp.access-token` 与 `workhub.mcp.repository-read-roots=/Users/aslight/IDEAWorkspace`（application.yml 或环境变量）
  2. 重启服务
  3. AI 任务配置页将"需求澄清分析"接入改绑"先行通-GLM CLI通道"
  4. 对任一已收录研发需求执行"开始澄清"
  5. 核日志：session/create 携带 mcpServers、tools/list 含三工具、分析 SUCCEEDED
- 完整规格确认：用户批准实施计划（含范围边界与通路选型）即本轮确认；确认人：用户；日期：2026-11-11
