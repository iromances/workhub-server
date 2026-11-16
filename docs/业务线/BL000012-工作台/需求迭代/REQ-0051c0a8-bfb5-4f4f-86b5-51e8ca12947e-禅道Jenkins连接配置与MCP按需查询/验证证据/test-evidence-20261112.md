# REQ-0051c0a8 验证证据（2026-11-12，本地 mvn test）

## 本需求新增测试（全部通过）
/Users/aslight/IDEAWorkspace/workhub-server/workhub-service/target/surefire-reports/cn.aslight.workhub.service.system.IntegrationConfigServiceTest.txt:Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.038 s -- in cn.aslight.workhub.service.system.IntegrationConfigServiceTest
/Users/aslight/IDEAWorkspace/workhub-server/workhub-service/target/surefire-reports/cn.aslight.workhub.service.system.IntegrationQueryServiceTest.txt:Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in cn.aslight.workhub.service.system.IntegrationQueryServiceTest
/Users/aslight/IDEAWorkspace/workhub-server/workhub-service/target/surefire-reports/cn.aslight.workhub.service.intake.ZentaoClientQueryTest.txt:Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in cn.aslight.workhub.service.intake.ZentaoClientQueryTest
/Users/aslight/IDEAWorkspace/workhub-server/workhub-service/target/surefire-reports/cn.aslight.workhub.service.agent.AgentJenkinsQueryClientTest.txt:Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in cn.aslight.workhub.service.agent.AgentJenkinsQueryClientTest
/Users/aslight/IDEAWorkspace/workhub-server/workhub-service/target/surefire-reports/cn.aslight.workhub.service.mcp.McpRuntimeServiceTest.txt:Tests run: 22, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.219 s -- in cn.aslight.workhub.service.mcp.McpRuntimeServiceTest

## 迁移解析（bootstrap）
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.038 s -- in cn.aslight.workhub.maintenance.IntegrationCatalogMigrationParseTest

## workhub-service 全量（1089 项）：3 处失败均为工作区并行在途改动，与本需求无关
- CodexCliPromptConsistencyTest.clarification_promptStaysByteIdenticalAfterMigration（澄清提示词迁移，并行会话）
- KnowledgeArchiveWorkflowTest.readingLegacyAnalysis...（知识归档路径，并行会话）
- BusinessLogSearchServiceTest.shouldPreserveActualExecution...（ELK 范围 fixture，并行会话）

## workhub-bootstrap 其余失败
- TeamAliasRecoveryStaticTest（依赖已删除的 ../docs/WorkHub/.../rollback.sql，并行会话 docs 重构）

## 前端
- npm run build（vue-tsc + tsc + vite）通过，2026-11-12

## MCP 实测（2026-11-12 20:24-21:03，用户重启后端后，经 /api/mcp/runtime）

- 迁移：Flyway 自动应用 `20261112.180000 - add integration read query capabilities` 成功（workhub-server.log 12:19:38-12:19:41Z）。
- tools/list：28 个工具，9 个新工具全部在列（search_zentao_bugs/get_zentao_bug/get_zentao_story/get_zentao_task/get_zentao_execution/list_jenkins_jobs/get_jenkins_job/get_jenkins_build/get_jenkins_build_log）。
- 禅道真实数据：productId=2 → 3 条缺陷（17852/17850/17842）；executionId=893 → 3 条；get_zentao_bug 17850（78 字段，viewUrl 正确）；get_zentao_story 786（产品 74，active）；get_zentao_task 7401（closed）；get_zentao_execution 893（name=26年等保测评与整改，project=892，products=[132]，viewUrl 正确）。
- 禅道全局列表限制：本实例（21.7.8）顶层 /bugs 三形态（limit=100/limit=20/无参）均 HTTP 400（匿名 401 证明路由在鉴权层之后）；多形态回退后返回明确指引"禅道全局缺陷列表不可用（HTTP 400），请改用 productId 或 executionId 检索"，审计留 ERROR。
- Jenkins：环境未配置 agent.jenkins/policy，list_jenkins_jobs 返回"尚未配置 Jenkins 实例及 Job 绑定（agent.jenkins/policy）"——未配置报错路径验证；真实实例查询由 HTTP 桩测试覆盖。
- 负向：limit=0 → "MCP 参数必须为正整数：limit"；bugId=0 → "MCP 参数必须为正整数：id"。
- 审计：新增工具 10 条 JSONL（tool/params/result/elapsedMs/error，含 system=INTEGRATION），不含返回数据；凭据字段 grep 零命中。
- 部署缺陷与修复：首发启动失败（IntegrationQueryService 缺 @Autowired 构造器标注），修复后重启正常。
