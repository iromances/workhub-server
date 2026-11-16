# Function Call 数据库访问规范

> 适用范围：WorkHub 后端（workhub-server）所有需要连接**业务方数据库**取数的场景。上位规范见 [仓库 AGENTS.md](../AGENTS.md)；需求来源：REQ-9f3c2a84（运维Job管理与FunctionCall统一SQL访问）。

## 1. 规则

凡是访问业务线数据库（经 MCP 数据库目标的 readonly profile），必须：

1. **先登记**：在「运维 → Function Call → SQL 管理」登记场景 SQL（表 `func_call_sql`），拿到 `sql_code`（唯一）并按需挂 `scenario_code`（一个场景可对应多条分支模板，按 `branch_order` 组合）。
2. **再调用**：代码中经 `FunctionCallService`（workhub-service `cn.aslight.workhub.service.ops.FunctionCallService`）按编码取模板渲染执行：
   - `render(sqlCode, params)` / `renderScenario(scenarioCode, params)`：渲染已校验参数的 SQL 文本；
   - `run(...)` / `runVerifiedBatch(...)`：经 MCP 只读网关执行（`SqlPolicyGuard`：仅 SELECT/WITH/SHOW/DESC/DESCRIBE/EXPLAIN、单条、强制 LIMIT）。
3. **禁止**在业务代码里直接拼业务库 SQL 字符串（本地 workhub 自身库的 MyBatis Mapper 不受此约束，该约束只针对**跨库**业务数据访问）。

## 2. 模板写法

- 占位符统一 `:name` 命名参数（只支持该形式，`%s`/`%d`/`${}` 均被校验拒绝）。
- 参数**值**的安全责任在调用方：必须先经既有白名单校验/转义（`quoteDatabaseName`、`sqlLiteral`、`escapeSqlLikeLiteral`、`validateExecutorAppName` 等）再传入；FunctionCallService 只做字面量替换，最终 SQL 仍过只读网关。
- 可选片段（执行器过滤、in 列表、条件谓词）作为字符串参数传入，允许空串；片段构建逻辑保留在调用方代码内（白名单不下放模板）。
- 模板必须以 SELECT/WITH/SHOW/DESC 开头；保存时做语法占位校验。

## 3. 登记与维护

- **页面维护优先**：Function Call 页面可新增/编辑/启停模板，保存后即时生效（每次调用直查 `func_call_sql`，无缓存）。
- **代码种子**：初始 14 条模板（OPS_XXL_* 8 条、OPS_MQ_* 6 条）由 `FuncCallSqlSeeds` 在启动时幂等回写（`sql_code` 已存在不覆盖，页面修改不会被种子冲掉）。修改种子需同步更新等价性测试 `FunctionCallTemplateEquivalenceTest`。
- **停用影响**：停用模板会使其依赖方（业务监测巡检）立即报错并落 `last_status=ERROR`，页面上有二次确认。

## 4. 新场景接入步骤（示例）

以「新增一个查询 XXL 某库任务总数」为例：

1. Function Call 页面新增 SQL：`sql_code=OPS_XXL_MY_COUNT`，模板 `SELECT COUNT(1) AS total FROM :databaseName.xxl_job_info`，参数说明写清 `databaseName=反引号库名`。
2. 代码调用：

```java
String sql = functionCallService.render("OPS_XXL_MY_COUNT",
        FunctionCallService.params("databaseName", quoteDatabaseName("amp_xxl_job_250")));
List<Map<String, Object>> rows = functionCallService.runRendered(sql, businessLineCode, environmentCode);
```

3. 页面「试运行」可先用真实参数验证结果再接入代码。

## 5. 代码评审检查项

- 业务库访问是否出现 `queryClient.runRows(..., 字符串拼接)`（新增禁止）；
- 新场景是否有 `func_call_sql` 登记编码并在参数说明中列全参数；
- 模板渲染等价性测试是否覆盖被修改的种子。
