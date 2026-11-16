# Tasks：AI提示词统一配置化

## 1. 元信息

- 需求编码：REQ-46a1aa8e-a89d-4981-81e0-0c7a063f7f30
- 需求名称与唯一需求目录：AI提示词统一配置化；docs/业务线/BL000012-工作台/需求迭代/REQ-46a1aa8e-a89d-4981-81e0-0c7a063f7f30-AI提示词统一配置化/
- 涉及仓库与功能域：workhub-server（workhub-service、workhub-bootstrap）
- 所属项目或业务线：BL000012-工作台
- Proposal 版本：v1.0
- Design/契约版本：v1.0
- 目标环境与分支：codex/development-20261102
- 任务计划状态：规格已确认（用户 2026-11-05 批准实施计划）
- 负责人/评审人：sh
- 建议研发任务标题：系统提示词统一迁入 AI 任务配置

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | AiUseCaseDefinitions 变量白名单 + agent.api.step / agent.cli.step 注册 | 无 | 与 T-02 并行 | §3 变量白名单 | 已完成 |
| T-02 | AiPromptTemplateRenderer 抽取 + 网关改造 + AiGatewayRequest variables 工厂 | 无 | 与 T-01 并行 | §2 渲染器 | 已完成 |
| T-03 | 4 个 intake generator 变量组装改造 | T-01、T-02 | — | §2/§3；AC-02 | 已完成 |
| T-04 | 2 个 Agent 适配器模板加载渲染改造 | T-01、T-02 | 与 T-03 并行 | §3；AC-05 | 已完成 |
| T-05 | Flyway 迁移 SQL | T-01（场景编码定稿） | 与 T-03/T-04 并行 | §3 迁移；AC-06 | 已完成 |
| T-06 | 单测更新 + 渲染器单测 | T-01~T-05 | — | AC-02/04/05/06 | 已完成 |
| T-07 | 文档更新（现状/迭代日志）+ 全量测试 | T-06 | — | AC-01/06 | 已完成（库上迁移验证待部署） |

执行顺序：T-01、T-02 → T-03/T-04/T-05 → T-06 → T-07。共享文件（AiUseCaseDefinitions、各 generator）按串行修改，避免冲突。

## 3. 任务明细

### T-01：AiUseCaseDefinitions 变量白名单与新场景

- 描述与目标：5 个 intake 场景白名单按 Design §3 细化；注册 `agent.api.step`（API、MODEL_ONLY）、`agent.cli.step`（CLI、MODEL_ONLY）。
- 输入：Design §3；现 AiUseCaseDefinitions.java。
- 输出：workhub-service/.../service/ai/AiUseCaseDefinitions.java。
- 前置依赖：无。
- 允许修改范围：该文件。
- 明确不修改范围：既有场景编码、能力边界语义。
- 项目补充任务字段：建议研发任务标题=系统提示词统一迁入 AI 任务配置。

验收条件：

- [x] 条件 1：新场景可被 `require()` 命中且通道校验生效（单测覆盖）。
- [x] 条件 2：白名单与迁移模板占位符一一对应（T-05 一致性测试）。

计划验证方式（尚未执行）：

- AiUseCaseConfigServiceTest 扩展 + T-05 一致性测试。

状态及真实结果：已完成。`PromptTemplateMigrationConsistencyTest` 断言 7 个场景模板占位集合与 `AiUseCaseDefinitions` 白名单逐一相等，且 agent 两场景模板首行与原护栏文本一致。

### T-02：渲染器抽取与网关改造

- 描述与目标：抽出 renderPrompt 为 AiPromptTemplateRenderer（保留未授权变量/缺变量/残留占位三类校验）；DefaultAiGatewayClient 改用；AiGatewayRequest 提供具名 variables 工厂，移除 `structured()` 单 prompt 工厂。
- 输入：Design §2；现 DefaultAiGatewayClient.java、AiGatewayRequest.java。
- 输出：service/ai/AiPromptTemplateRenderer.java（新）、DefaultAiGatewayClient.java、AiGatewayRequest.java。
- 前置依赖：无。
- 允许修改范围：上述三文件。
- 明确不修改范围：探测/连通测试提示词及返回体校验（Proposal 明确不做）。

验收条件：

- [x] 条件 1：三类校验行为与迁移前一致（DefaultAiGatewayClientTest 回归 + 渲染器单测）。
- [x] 条件 2：边界情形——模板含白名单外变量时报错并指明变量名。

计划验证方式（尚未执行）：

- AiPromptTemplateRendererTest 新增 + DefaultAiGatewayClientTest 回归。

状态及真实结果：已完成。新增 `AiPromptTemplateRendererTest` 6 项（合法渲染、未授权变量、缺变量、残留占位、JSON 序列化、空模板）；与迁移前唯一行为差异：不再 `trim()` 模板——字节级一致性测试证明模板尾部换行必须保留，改为仅空值校验。`DefaultAiGatewayClientTest` 全部回归通过。

### T-03：intake generator 变量化

- 描述与目标：CodexCliStructuredExtractor、CodexCliSqlDraftGenerator、CodexCliClarificationAnalysisGenerator、CodexCliDevelopmentAnalysisGenerator 删除指令文本，`buildPrompt` → 变量组装；小节标题随数据片段组装，空缺给空串；DevelopmentAnalysisGenerator 移除恒为 null 的 extraInstruction 参数。
- 输入：Design §3 变量表；现四个 generator 源码（指令文本原样平移入 T-05 模板）。
- 输出：四个 generator 源码；T-06 一致性对比测试。
- 前置依赖：T-01、T-02。
- 允许修改范围：四个 generator 及其调用点签名适配。
- 明确不修改范围：解析/归一化逻辑（normalizeWorkItems 等）、输出 schema。

验收条件：

- [x] 条件 1：单测对比——迁移文件模板 + 变量渲染结果 == 迁移前 buildPrompt 输出（逐字一致）。
- [x] 条件 2：边界情形——附件/知识库为空时对应段落为空串且模板仍完整渲染。

计划验证方式（尚未执行）：

- CodexCliPromptConsistencyTest 新增（按场景逐字对比）。

状态及真实结果：已完成。`CodexCliPromptConsistencyTest` 按迁移前代码独立重建期望值，5 个场景（含空附件边界）字节级一致；曾抓出渲染器 trim 模板导致尾部换行丢失的缺陷并修复。

### T-04：Agent 适配器改造

- 描述与目标：AgentApiExecutionAdapter / AgentNativeCliAdapter 注入 AiUseCaseConfigService + 渲染器；prompt() 改为加载 agent.api.step / agent.cli.step 模板渲染；配置缺失/停用 → store.finish FAILED 且信息含场景编码。
- 输入：现两个适配器 prompt() 方法；AiUseCaseConfigService。
- 输出：两个适配器源码。
- 前置依赖：T-01、T-02。
- 允许修改范围：两个适配器。
- 明确不修改范围：工具授权、MANUAL_RECONCILIATION、completionTargets 等强制逻辑。

验收条件：

- [x] 条件 1：适配器单测覆盖正常渲染路径（指令文本来自模板）。
- [x] 条件 2：失败情形——配置缺失/停用时运行 FAILED 且错误信息含场景编码（AC-05）。

计划验证方式（尚未执行）：

- AgentWorkbenchServiceTest、AgentInspectionServiceTest、AutonomyCaseServiceTest 回归 + 新增失败路径断言。

状态及真实结果：已完成。两个适配器 prompt() 改为读取 `agent.api.step`/`agent.cli.step` 模板渲染；prompt 构建异常统一转 FAILED 且信息含场景编码。三个 Agent 测试类以 `TestPromptTemplates.stubEnabledConfigService()`（迁移文件真实模板）回归通过，轨迹/职责断言证明变量渲染到位。2026-11-06 补齐 AC-05 专项断言：`AutonomyCaseServiceTest.apiStepFailsRunWithSceneCodeWhenPromptConfigMissing` 与 `AgentWorkbenchServiceTest.nativeCliStepFailsRunWithSceneCodeWhenPromptConfigMissing`（配置缺失 → FAILED 且 finish 事件含场景编码，网关/协议零调用），均通过。

### T-05：Flyway 迁移

- 描述与目标：V20261105_HHMMSS__move_ai_prompts_into_use_case_config.sql——AGENT 字典、5 行模板 UPDATE（checksum/version 同步）、2 行 agent 预置（幂等）。
- 输入：Design §3；T-03 平移出的模板文本。
- 输出：迁移 SQL 文件；占位符一致性测试。
- 前置依赖：T-01（场景编码定稿）。
- 允许修改范围：新增迁移文件与配套测试。
- 明确不修改范围：既有迁移文件。

验收条件：

- [x] 条件 1：模板占位符与白名单一致；SHA2 校验和与模板一致（一致性测试）。
- [x] 条件 2：幂等——WHERE NOT EXISTS / ON DUPLICATE KEY 可重复执行（UPDATE 附加校验和差异守卫，重复执行不再翻版本号）。

计划验证方式（尚未执行）：

- 迁移占位符一致性测试（SQL 文本级）；库上可重复执行待部署环境验证。

状态及真实结果：已完成。`V20261105_193000__move_ai_prompts_into_use_case_config.sql` 含 AGENT 字典、5 行模板 UPDATE（SHA2 校验和、版本+1、flyway-prompt-config 落款）、2 行 Agent 幂等预置；占位符一致性由 `PromptTemplateMigrationConsistencyTest` 校验。库上重复执行待部署环境验证。

### T-06：测试

- 描述与目标：更新 generator/gateway/config/agent 相关单测；新增渲染器与迁移占位符一致性测试。
- 输入：T-01~T-05 产物。
- 输出：全部受影响单测通过。
- 前置依赖：T-01~T-05。
- 允许修改范围：workhub-service、workhub-controller 测试源码。

验收条件：

- [x] 条件 1：受影响模块 `mvn test` 通过。
- [x] 条件 2：新增用例覆盖 AC-02/04/05。

计划验证方式（尚未执行）：

- `mvn -q -pl workhub-service -am test` 及全模块测试。

状态及真实结果：已完成。2026-11-05 首轮 workhub-service 989 项、2026-11-06 并行重构落地后补跑 997 项（含新增 2 个 AC-05 断言），本次范围全部通过；其余模块 controller 63、mcp-server 91、model/dao/support/job 全部通过。

### T-07：文档与全量验证

- 描述与目标：现状.md AI 任务配置章节、迭代日志.md 追加条目；全模块 `mvn test`。
- 输入：T-06 结果。
- 输出：文档更新、全量测试证据。
- 前置依赖：T-06。
- 允许修改范围：docs/业务线/BL000012-工作台/。

验收条件：

- [x] 条件 1：`grep "你是"` src/main 仅剩探测/整理协议文本（AC-01）——实际 grep 全仓 main 源码 0 命中，探测/整理文本不含该词且按范围保留在代码。
- [x] 条件 2：全模块 `mvn test` 通过（AC-06）——2026-11-06 全模块补跑通过（详见 §4；bootstrap 1 项失败为并行文档迁移删除测试夹具所致，非本需求范围）。

计划验证方式（尚未执行）：

- grep 核对 + `mvn test`。

状态及真实结果：文档已更新（现状.md 新增"AI 提示词统一配置化"小节、迭代日志 2026-11-05 条目）。库上迁移验证待部署执行。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01→T-03/T-04/T-07；AC-02→T-03；AC-03→上线后人工核对；AC-04→T-02；AC-05→T-04（2026-11-06 专项断言已补齐）；AC-06→T-05/T-07。
- Design 约束承接：变量白名单代码固定（T-01）；能力边界不动（T-04 不修改强制逻辑）；幂等迁移（T-05）。
- 未覆盖项及原因：迁移库上重复执行验证待部署环境；AC-03 页面改模板即时生效待上线后人工核对。2026-11-06 补跑结果：workhub-service 997 项（本次范围全绿，2 项失败为并行文档/ELK 改动）、controller 63 项、mcp-server 91 项、model/dao/support/job 全部通过；bootstrap 14 项中 `TeamAliasRecoveryStaticTest` 1 项因文档目录迁移删除了 `docs/WorkHub/团队重复人员合并/SDD/rollback.sql` 夹具而失败，属并行文档迁移未同步测试，非本需求范围。
- 已生效事实核实与目标项目系统设计、迭代日志及专项文档更新：现状.md 已新增"AI 提示词统一配置化（2026-11-05）"小节；迭代日志已记 2026-11-05 条目；grep 核实主代码无业务提示词残留。
- 关联 Proposal、Design 与契约的版本链接：proposal.md v1.0 / design.md v1.0（同目录）。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：范围=本目录三份规格 v1.0；结论=批准实施；确认人=sh；日期=2026-11-05（实施计划批准）。
