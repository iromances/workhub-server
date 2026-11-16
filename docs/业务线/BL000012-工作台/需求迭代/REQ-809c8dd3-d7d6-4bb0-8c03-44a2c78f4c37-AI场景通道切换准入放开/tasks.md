# Tasks：AI 场景通道切换准入放开（ZCode 本地仓库读取）

## 1. 元信息

- 需求编码：REQ-809c8dd3-d7d6-4bb0-8c03-44a2c78f4c37
- 需求名称与唯一需求目录：AI 场景通道切换准入放开（ZCode 本地仓库读取）；`docs/业务线/BL000012-工作台/需求迭代/REQ-809c8dd3-d7d6-4bb0-8c03-44a2c78f4c37-AI场景通道切换准入放开/`
- 涉及仓库与功能域：workhub-server（`DefaultAiGatewayClient` + 单测）
- 所属项目或业务线：BL000012-工作台
- Proposal / Design 版本：v1（2026-11-12）
- 目标环境与分支：workhub-server `codex/development-20261102`
- 任务计划状态：规格已确认（2026-11-12），实施中
- 负责人/评审人：用户（sh）

## 2. 任务总览

| 任务 | 交付物 | 对应验收 | 状态 |
| --- | --- | --- | --- |
| T-01 | `isConfigured` ZCode 仓库读取改按前置条件判断 | AC-01、AC-02 | 待开始 |
| T-02 | `DefaultAiGatewayClientTest` 补 isConfigured 两用例并回归 | AC-01~03 | 待开始 |
| T-03 | 文档（迭代日志/notes） | — | 待开始 |

## 3. 任务明细

### T-01：isConfigured 修改

- 描述与目标：删除 ZCODE+LOCAL_REPOSITORY_READ 无条件 false，改为 token 已配置 && MCP 运行时就绪（toolNames 非空）才放行。
- 验收条件：
  - [ ] 前置齐备返回 true；缺 token / 运行时未就绪 / 工具清单为空返回 false；
  - [ ] 不新增构造依赖，异常安全（外层 catch 兜底不变）。

### T-02：单测

- 验收条件：
  - [ ] 新增 `isConfigured` ZCode 仓库读取两用例通过；
  - [ ] `DefaultAiGatewayClientTest` 既有用例全部通过。

### T-03：文档

- 验收条件：
  - [ ] 迭代日志补一行、notes 记过程。

## 4. 覆盖与评审

- AC-01~03 映射 T-01~T-03；非范围项保持。
- 完整规格统一确认：2026-11-12 用户确认（"就是要切换通道用""都要支持切换"），据此实施。

状态及真实结果：见下方执行记录（实施后填写）。

## 5. 执行记录（2026-11-12）

| 任务 | 状态 | 真实结果与证据 |
| --- | --- | --- |
| T-01 | 已完成 | [DefaultAiGatewayClient.isConfigured](../../../../../workhub-service/src/main/java/cn/aslight/workhub/service/ai/DefaultAiGatewayClient.java)：删除 ZCODE+LOCAL_REPOSITORY_READ 无条件 false，改为 `workhub.mcp.access-token` 已配置且 McpRuntimeService 就绪（toolNames 非空）才放行；无新增构造依赖 |
| T-02 | 已完成 | 新增 `isConfigured_allowsZcodeRepositorySceneWhenMcpBridgeReady`（前置齐备 → true）、`isConfigured_rejectsZcodeRepositorySceneWithoutMcpBridge`（缺 token / 工具清单为空 → false）；DefaultAiGatewayClientTest 18/18 通过（16 既有回归 + 2 新增），`mvn -pl workhub-service -am test` BUILD SUCCESS |
| T-03 | 已完成 | 迭代日志补记录、notes 过程记录 |

剩余待部署验证：本机需配置 `WORKHUB_MCP_ACCESS_TOKEN`（当前本地 application.yml 默认空）；配置后在 AI 配置页对"需求澄清分析"重新点可用性检测/直接发起澄清分析，走 ZCode 通道经 workhub MCP 只读工具完成一次真实分析。

### 追加（2026-11-12 晚）：研发拆解 ZCode 空转根因定位与修复

用户重试后研发拆解仍空（行 110/111 两轮一致：25-38s、workItems 空、summary 为"先并行搜索"式计划文本、MCP 审计零工具调用）。深挖定位真正根因：**`workhub.mcp.repository-read-roots` 未配置** → `RepositoryFileService.enabled()=false` → `list_repository_files`/`read_repository_text_file`/`search_repository_text` 三个仓库工具**从未注册进 MCP runtime**（佐证：运行实例工具清单里没有它们、审计文件自始 0 次仓库工具调用）→ ZCode 会话里模型看不见任何仓库工具，而提示词"代码读取约束"却让它用这些工具——只能输出计划文本+空 JSON。此前 isConfigured 放开时只校验"runtime 工具清单非空"，未校验仓库工具已注册，属本轮修复的缺口。

修复：① application.yml 补 `workhub.mcp.repository-read-roots`（默认 `/Users/aslight/IDEAWorkspace`，env `WORKHUB_MCP_REPOSITORY_READ_ROOTS` 可覆盖；既有只读/白名单/深度字节防护不变，生产部署用 env 配真实仓库根）；② isConfigured 的 ZCode 仓库读取分支收紧为必须注册 `list_repository_files`。DefaultAiGatewayClientTest 18/18（新增"runtime 有工具但缺仓库工具 → false"用例）。待重启后对 277 重新发起研发拆解实测工具调用与拆解产出。
