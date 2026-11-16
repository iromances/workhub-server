# Proposal：FunctionCall执行审计

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-b4d7e6f2-3a1c-4d5e-9f8b-2c7d4e9a1b53（任务来源：REQ-9f3c2a84 Function Call 的后续增补） |
| 需求名称 | FunctionCall执行审计 |
| 涉及仓库与功能域 | workhub-server（FunctionCallService/两个 Collector/新迁移/新清理任务）；workhub-web（Function Call 执行记录页签） |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-b4d7e6f2-3a1c-4d5e-9f8b-2c7d4e9a1b53-FunctionCall执行审计/ |
| 版本/状态 | v1.0（用户 2026-11-14 指示：Function Call 增加每个 SQL 的执行记录，留给审计） |

## 1. Why

Function Call 已统一业务库 SQL 的登记与渲染，但**执行**仍由 Collector 直接调查询客户端，无任何执行留痕：谁在什么时间用哪个模板对哪个业务库执行了什么 SQL、返回多少行、是否成功、耗时多少，事后无法审计。

## 2. What 与 Done

把执行统一收口到 `FunctionCallService`，逐语句落 `func_call_sql_audit_log`，保留 365 天。

| 编号 | 验收条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | 两个 Collector 全部 8 处业务库执行点改经 FunctionCallService（渲染+执行+审计一体），行为与改造前一致（现有测试全绿） | mvn test |
| AC-02 | 每次执行的每条语句落一行审计：sql_code（组合语句为参与码 `+` 连接）、scenario_code（可推导时）、来源（SYSTEM/MANUAL）、操作人（页面为登录用户，巡检 SYSTEM）、业务线/环境、渲染后 SQL 全文、状态、行数、耗时、错误信息 | 单测 + DB 核对 |
| AC-03 | 试运行走同一通道，来源 MANUAL、操作人为当前登录用户 | 单测/页面 |
| AC-04 | Function Call 页新增「执行记录」页签：按编码/来源/状态/时间筛选，可查看渲染 SQL 详情（view 权限） | 页面走查 |
| AC-05 | 审计保留 365 天，每日 4:00 由 WorkhubJob（OPS_FUNC_CALL_AUDIT_CLEANUP）清理，任务出现在 Job 管理 | DB/Job 页 |

约束：审计写入失败不阻断业务查询（记 ERROR 日志）；组合语句（统计+分页外壳、MQ 分支 UNION）按一条语句记一行，code 为参与码连接。

# Design（同文件承载）

- 表 `func_call_sql_audit_log`：id、sql_code VARCHAR(160)、scenario_code VARCHAR(64) NULL、source VARCHAR(16)（SYSTEM/MANUAL）、operator VARCHAR(64)、business_line_code VARCHAR(128)、environment_code VARCHAR(64)、rendered_sql MEDIUMTEXT、status VARCHAR(16)、row_count INT NULL、duration_ms BIGINT、error_message TEXT、started_at/finished_at DATETIME(3)；索引 (sql_code, started_at)、(started_at)。迁移 `V20261114_120000__add_func_call_audit.sql`，FuncCallSchemaInitializer 兜底建表。
- `FunctionCallService`：`record Statement(String sqlCode, String sql)`；`executeStatements(line, env, List<Statement>)`（runRowsBatch+逐条审计）、`executeVerified(...)`（runVerifiedBatch+审计）、`run(sqlCode, params, line, env)`（MANUAL+SecurityContext 操作人）；审计 mapper 为空（测试构造）时跳过写入；单条执行时长以整批计时近似；scenario 由 sql_code 反查（组合码不反查）。
- Collector：`*Sql()` 私有方法返回 `Statement`（模板码内聚）；组合语句调用点显式拼参与码；删除直连 queryClient（one-arg 测试构造以 fake client 装入 FunctionCallService）。
- API：`GET /api/ops/function-call/audits`（sqlCode/source/status/startDate/endDate/page/pageSize，view 权限）。
- 清理：workhub-job 新增 `FuncCallAuditCleanupJob` implements WorkhubJob（OPS_FUNC_CALL_AUDIT_CLEANUP，CRON `0 0 4 * * *`），删除 365 天前记录。

# Tasks

| 任务 | 状态 |
| --- | --- |
| T-01 迁移+实体+Mapper+Initializer 兜底 | 已完成 |
| T-02 FunctionCallService Statement/执行审计/操作人 | 已完成 |
| T-03 两个 Collector 执行收口 | 已完成 |
| T-04 审计查询 API + 前端执行记录页签 + 清理 Job | 已完成 |
| T-05 单测（审计落库/来源/操作人/组合码）+ mvn test + npm build + 文档 | 已完成 |

## 实施证据（2026-11-14）

- 执行收口：XXL 4 处 + MQ 5 处（含原内联 discoverDatabases SQL 登记为 OPS_MQ_DISCOVER_DATABASES 种子）全部改经 `FunctionCallService.executeStatements/executeVerified`；`*Sql()` 方法返回 `Statement(sqlCode, sql)` 编码内聚；组合语句（XXL 日志页、MQ 消息页、MQ 分支 UNION）审计编码为参与码 `+` 连接。
- 审计落库：`func_call_sql_audit_log`（迁移 V20261114_120000 + Initializer 兜底）；每次执行每条语句一行（编码/场景/来源 SYSTEM-MANUAL/操作人/业务线/环境/渲染 SQL 全文/状态/行数/耗时/错误）；审计写失败仅记日志不阻断业务；试运行走 `run()`（MANUAL + SecurityContext 操作人，无登录回落 UNKNOWN）。
- 查询与保留：`GET /api/ops/function-call/audits`（编码/来源/状态/时间窗分页，view 权限）；前端 Function Call 新增「执行记录」页签（筛选+SQL 详情弹框）；`OPS_FUNC_CALL_AUDIT_CLEANUP` WorkhubJob 每日 4:00 清理 365 天前记录（自动出现在 Job 管理）。
- 验证：`FunctionCallAuditTest` 4/4（逐条落库与场景反查、组合码跳过场景且错误留痕重抛、MANUAL 来源与操作人回落、无审计 mapper 跳过）；ops 域全量 77/77（等价性 10、XXL 31、MQ 11、OpsMonitor 13 等）；`npm run build` 通过。测试侧调整：MQ fake 对单条非组合语句退化为 runRows 路由（匹配执行统一走批量接口）、组合语句断言改为按外壳标志取条目。
- 待部署走查：审计表随启动兜底创建；Job 管理出现清理任务；执行记录页签数据。
