# Tasks：研发任务评估空拆解治理

## v2 执行记录（2026-11-14）

- 后续真实验证（用户明确授权现有 GLM 服务处理真实资料）：隔离需求评估与强制格式校正均通过，正式生成器均解析为 DRAFT、2 项、12h，8 个代码文件存在；第 120 轮 -32603 未复现，确切内部原因仍不可追溯。见 [真实验证记录](验证/20261114-real-zcode/验证记录.md)。此后续结果补充下方先前“待真实验证”时点，不代表应用已重启发布或业务数据已保存。

- 确认来源：用户本会话“修复下”；后续 -32603 反馈继续同一修复。范围限工具接入及协议错误诊断，不重启、不改配置、不自动重跑业务评估。
- 已确认：第 120 轮完成了仓库工具调用，失败原始错误正文未保留；不能将本轮故障继续归为工具不可见。
- 已完成：协议错误上下文脱敏、受控进度日志、针对性验证。真实内部错误根因仍待新诊断信息，不以单元测试替代真实需求闭环。
- 2026-11-14 20:51 验证：离线 Maven 定向 51/51 通过、0 跳过（ZCodeTaskConversationTest 20、ZCodeCliLocalProtocolTest 5、ZCodeCliClientTest 7、DefaultAiGatewayClientTest 19）。真 CLI 使用已安装 ZCode、模型和 MCP 使用本机夹具，未安装依赖。新增覆盖格式校正失败上下文、凭据脱敏及长度、错误正文缺失。
- 交付边界：未重启实例、未重跑需求、未改配置；需加载本次改动后获取实际错误详情。最新运行证据为 analysisId=120、intakeId=277、PID=53419，真实故障尚未闭环。

## 1. 元信息

- 需求编码：REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb
- 目标系统：WorkHub Server
- Proposal / Design：v1，见 [proposal.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb-研发任务评估空拆解治理/proposal.md>)、[design.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb-研发任务评估空拆解治理/design.md>)
- 目标分支：server `codex/development-20261102`
- 当前状态：已实施，自动化验证通过；真实重跑待部署验证

## 2. 确认记录

| 日期 | 范围/版本 | 确认人 | 结论 |
| --- | --- | --- | --- |
| 2026-11-13 | 诊断结论（模型零工具调查交"计划"+空拆解被当成功）与三层修复方案（零工具续跑一轮 / 空拆解显式失败 / 失败摘要带根因） | 用户 | 批准实施计划（2026-11-13） |

## 3. 任务与依赖

| 任务 | 交付物 | 依赖 | 验收映射 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | `ZCodeTaskConversation`：工具计数、`currentInputId`、`requireToolInvestigation` 标志与一次性续跑 | 规格确认 | AC-01、02、03 | 已完成 |
| T-02 | `CodexCliDevelopmentAnalysisGenerator`：空拆解拒绝保存（生成/SDD/调整） | 规格确认 | AC-04 | 已完成 |
| T-03 | `DefaultAiGatewayClient.summarizeException`：根因拼接 | 规格确认 | AC-05 | 已完成 |
| T-04 | 协议与生成器测试、定向回归、SDD 文档 | T-01～03 | AC-01～05 | 已完成 |

修改范围：`ZCodeTaskConversation`、`CodexCliDevelopmentAnalysisGenerator`、`DefaultAiGatewayClient`、`ZCodeTaskConversationTest`、`CodexCliDevelopmentAnalysisGeneratorTest`。其他已有未提交修改保持原状。

## 4. 实际结果（2026-11-13）

- 定向回归 75/75：ZCodeTaskConversationTest 15（新增续跑两用例）、CodexCliDevelopmentAnalysisGeneratorTest 3（新增空拆解两用例）、DefaultAiGatewayClientTest 19、DevelopmentAnalysisServiceTest 16、IntakeSddFlowTest 15、ZCodeCliClientTest 7；模块编译与 test-compile 通过。
- 诊断数据留痕：intake 277 的 8 轮评估记录（5 轮空拆解 DRAFT：110-113、117；run 116 纯文本 FAILED；114/115 协议失败根因丢失）为运行库既有数据，不回溯修改。
- 未覆盖项（待部署）：重启本地实例后重跑 202611050003 研发任务评估的真实链路——预期产出真实任务拆解或显式 FAILED；协议失败类报错应直接可见根因。
- 系统设计更新：现状.md §11a 已补记；迭代日志已记录。


## v3 计划：202611190001 终稿失败治理（2026-11-20）

- 固定编码：REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb；名称与唯一目录沿用本文；仓库 workhub-server；业务线 BL000012；Proposal/Design 版本 v3。
- 分支 codex/development-20261102；运行库目标 bl000012-prod-db-10-10-116-32-3306/readonly；代码实施目标为本地，部署提交尚未核实。
- 统一确认记录：待用户确认；“那咋办”作为处理方案请求记录，不填作已批准新规格。v1/v2 历史结论保持原样。

| 任务 | 产物与范围 | 依赖 | 验收 | 状态 |
| --- | --- | --- | --- | --- |
| V3-T1 | ZCodeTaskConversation/ZCodeCliClient 校验分支拆分与共享限制；相关诊断测试 | v3 确认 | V3-01/02 | 待开始 |
| V3-T2 | 结构化结果一次有界压缩校正；不扩大最终结果上限 | T1 | V3-03/04 | 待开始 |
| V3-T3 | 现有协议、CLI、网关及评估生成器离线回归 | T2 | V3-01～05 | 待开始 |
| V3-T4 | 更新现状/迭代日志，单独记录部署与真实评估验证 | T3，部署/重跑授权 | V3-05 | 待开始 |

计划验证：缺失/null/对象/空白 response；中英文混合的 24,000/24,001 与 96,000/96,001 字节边界；非 stop finishReason；工具字段错误和非空；一次校正后成功/超长/无效 JSON；无工具授权与 deadline 保持；空任务拒绝。使用现有依赖，测试执行前读取代码风格；不能离线运行的检查如实记未验证，不安装依赖。

实际结果：已完成只读核查，128/129 均 FAILED 且无草稿；129 已不再运行。已编写 v3 规格草案；未实施代码、未执行测试、未改配置、未重启、未触发重跑。旧错误的具体子原因仍无法追溯。


### v3.1 确认与堆栈展示补充（2026-11-20）

用户明确要求“要把错误堆栈显示出来，方便排查问题，然后把这个功能修复好”，据此确认 v3 修复与新增堆栈展示，开始实施。范围扩展至 workhub-web 的评估轮次详情与类型；后端保留网关异常 cause，失败时将脱敏堆栈（最多 12,000 字符，截断标识）写入独立 error_stack TEXT 字段，通过原鉴权的评估详情/历史响应 errorStack 返回；最新失败和历史轮次均可展开纯文本堆栈。历史空值明确无堆栈，不伪造。新增 Flyway 可空列迁移，需先于新代码启用，回滚应用时可保留该列。测试覆盖原因链传递、脱敏/长度、持久化与响应、协议边界及前端构建。不执行运行库手工 DDL，不安装依赖。既有“无数据库变更”描述由本补充覆盖。部署和真实评估仍须单独记录，不以离线测试宣称原需求已成功。


### v3.1 实际交付（2026-11-20）

- V3-T1/T2 已实现：网关保留原始异常 cause（不序列化原异常）；生成器传递原因链；评估失败包括排队/外层兜底保存脱敏堆栈，成功清空堆栈；errorStack 随原详情/历史接口返回。最新评估及轮次详情支持展开纯文本。历史未保存堆栈有明确提示。
- 分别诊断结果缺失/类型/空白/UTF-8 超长、finishReason、工具字段与 JSON 格式；结构化终稿 24,001～96,000 字节进入一次无工具压缩校正，最终仍须 <=24,000 字节。保留原任务预算、调查规则及空任务拒绝。不能推断旧 128/129 轮的具体内部原因已确定。
- V3-T3 已完成：最终离线 Maven 定向 96/96 通过（service 77 + controller 19），0 失败/错误/跳过；前端 npm run build 通过（应用/配置类型检查及 Vite 构建）。目标改动 diff --check 通过。
- 测试包含 H2 MySQL 模式执行新增迁移，并通过实际 Mapper 往返验证旧记录 NULL、失败堆栈保存、详情/最新/历史读取及成功清理；仅是本地兼容验证，不替代真实 MySQL 发布验证。
- 首轮 Mockito 动态附加失败，改用本机已存在的 mockito-core 5.23.0 javaagent 后可运行；测试编写中修正夹具的调用次数、失败注入和 H2 不支持的多列 ALTER 语法。未安装依赖。
- 独立真实 ZCode CLI 夹具测试在较早一次执行中 6 项因未提供本机 CLI 路径被跳过，不计入最终 96 项。未做真实模型调用或实际页面端到端验证。
- V3-T4 文档已更新；部署、真实 MySQL 迁移、需求 202611190001 重跑和产物人工核对未执行。尚未把真实问题标记为已恢复。
- 发布顺序：V20261120_080000__development_analysis_error_stack.sql（可空列）→ 后端新代码 → 前端；沿用已有启动迁移机制，不手工写运行库。回滚应用可保留可空列。
- 前后端均保留工作区其他未提交改动；未提交 Git。

最终后端验证命令：
```sh
mvn -o -pl workhub-controller -am test -Dtest=ZCodeTaskConversationTest,ZCodeCliClientTest,AiFailureDiagnosticsTest,DevelopmentAnalysisErrorStackTest,DefaultAiGatewayClientTest,CodexCliDevelopmentAnalysisGeneratorTest,DevelopmentAnalysisServiceTest,IntakeControllerTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-javaagent:/Users/aslight/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar
```

## v4 执行计划与确认（2026-11-20）

范围 Proposal/Design v4；用户在本聊天回复“改吧”，承接前文无长度限制、原文保存/恢复、ELI5 和任务启动前校验方案；无需重复确认。旧版本状态和证据保持。

| 任务 | 状态 | 对应验收 |
|---|---|---|
| 独立事务准备与提交后调度 | 已完成 | AC-v4-01 |
| 原文列迁移、回调与输入指纹 | 已完成 | AC-v4-03/04 |
| 完整结果整理与 ELI5 | 已完成 | AC-v4-02/05 |
| 开发启动前校验 | 已完成 | AC-v4-05 |
| 隔离测试、自查和交付文档 | 已完成 | AC-v4-06 |

未验证：真实供应商、实际数据库迁移、运行实例重启与历史任务重跑。


### v4 实际交付

- 本地代码完成。两类分析的 REQUIRES_NEW 事务提交后才调度；队列拒绝独立事务回写失败。H2 真实事务测试覆盖 afterCommit 调用与独立连接立即读取任务。
- 三个 ZCode 仓库分析入口保存完整脱敏原文，取消 24,000/96,000 字节与整理 maxOutputTokens 人为限制；保留有限格式纠错、length 续接、原时间预算、调查要求及业务校验。既有草稿对话调整不在本次恢复范围。
- 指纹核对提示、schema、供应商/模型/推理和干净 Git HEAD；脏工作区或版本未知时保存但不复用。仅复用同需求同指纹最新且尚未整理完成的失败记录，格式成功后的业务失败不复用。H2 执行迁移并用实际 Mapper 验证完整大文本、隔离读取、写入状态与最新记录规则。
- 设计确认前核对数字员工、用户指派权限、归属、业务线/环境及供应商；专项验证正常授权、其他用户、越界、缺权限与员工停用，预检不创建任务。
- 定向回归 239 项：238 通过、1 项真实 CLI 未配置跳过；随后新增权限专项 1 项通过。合计 240 项中 239 通过、1 跳过，0 失败/错误。workhub-bootstrap 全模块 test-compile 通过。仅使用已有依赖与本机 Mockito javaagent，未安装软件。
- 测试使用内存 H2、临时 Git 仓库和本机模拟服务器；未调用真实 AI 或业务数据库。H2 MySQL 模式验证不替代真实 MySQL 发布验证。证据保存在验证目录。
- 自查：不重放仓库业务工具；业务失败不循环复用；原文保存失败即停止；保留权限、非空拆解和 SDD 完整性。目标代码 diff --check 通过。未提交 Git，保留其他既有修改。
- 未执行实际迁移、后端重启、真实模型调用和历史需求重跑。生效需先通过发布迁移流程应用 V20261120_180000__intake_analysis_result_recovery.sql，再启用新后端；本轮仅交付迁移文件，不手工写运行库。

验证命令：离线 Maven workhub-service 定向执行 IntakeAnalysisTransactionTest、IntakeResultCheckpointMapperTest、IntakeResultRecoveryTest、DevelopmentAnalysisServiceTest、IntakeClarificationAnalysisServiceTest、IntakeAiDevelopmentServiceTest、AgentResultFormatterTest、ZCodeTaskConversationTest、DefaultAiGatewayClientTest、CodexCliDevelopmentAnalysisGeneratorTest、CodexCliClarificationAnalysisGeneratorTest、IntakeSddFlowTest、IntakeServiceTest、AgentWorkbenchServiceTest；补测 AgentWorkbenchServiceTest#designPreflightChecksAssignmentAccessScopeAndAvailabilityWithoutCreatingTasks。均使用本机 mockito-core/5.23.0 javaagent、-Dsurefire.failIfNoSpecifiedTests=false。编译命令 mvn -o -pl workhub-bootstrap -am test-compile -DskipTests。


## v5：AI 执行不设人为并发上限（2026-11-21）

确认：用户 2026-11-21 明确要求“不设限制，能开多少开多少”，本轮按取消人为并发限制实施，不重复确认。涉及 workhub-server 与 workhub-web；后端分支 codex/development-20261102。计划：修改执行器与调度、更新界面及兼容字段、隔离并发测试与回归、现状和迭代记录。部署/运行实例重启不在本轮执行。状态：实施中。


### v5 实际交付（2026-11-21）

- 已完成：需求管理共享异步执行器改为每任务一个虚拟线程，不设置并发/队列容量；容器关闭时关闭执行器并等待在途任务最多 5 秒。该共享执行器的其他既有使用方同步采用相同行为。
- 已完成：AgentExecutionJob 移除全局配置读取；协调器持续领取至无可运行任务；领取保留数据库锁、条件更新、租约、启停与取消校验，不再计数限制全局或每员工并发。候选查询排除停用员工，避免其旧排队记录占据前 100 条阻塞可运行任务；100 是每次查询批量，不是运行上限。
- 兼容：maxConcurrentRuns 存量字段保留，不参与调度；后端取消 1～8 校验，前端将数量输入框改为“不设并发上限”。原全局 max-concurrent-runs 配置不再生效，无需修改运行配置。
- 隔离测试：48 个任务在其他任务均未结束时启动，均为虚拟线程；40 个同员工运行由多个领取线程全部且仅领取一次；协调器单次派发 40 个，超过旧 32 上限。原幂等、租约恢复、取消与权限回归保持。
- 最终 Maven 定向共 222 项：221 通过、1 项真实 CLI 未配置跳过，0 失败/错误。service 221 项、bootstrap 1 项；所有上游模块与测试编译通过。前端 npm run build 通过。目标 diff --check 通过。首轮编译发现其他测试夹具仍传旧 claim 参数，已更新并通过最终回归。
- 没有安装依赖、调用真实供应商或访问运行数据库。未重启、部署、提交 Git；前端仅构建，未做浏览器人工验收。无新增数据库迁移，前轮 v4 的迁移要求仍保留。真实容量与供应商限额未做压力验证。
- 本轮仅取消人为并发配额，不等于修复 v4 排查指出的需求重复触发/迟到结果覆盖；既有保护保持，未扩大宣称。
- 验证证据：验证/20261121-v5-concurrency.log、验证/20261121-v5-web-build.log。

验证命令：
```sh
mvn -o -pl workhub-bootstrap -am test -Dtest=AsyncTaskConfigTest,AgentExecutionCoordinatorTest,AgentWorkbenchServiceTest,AgentInspectionServiceTest,AgentVerificationServiceTest,AutonomyCaseServiceTest,IntakeAnalysisTransactionTest,IntakeAiDevelopmentServiceTest,DevelopmentAnalysisServiceTest,IntakeClarificationAnalysisServiceTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-javaagent:/Users/aslight/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar
# workhub-web
npm run build
```
