# Design：内部数据库批量查询取消字节截断

| 项目 | 内容 |
| --- | --- |
| 名称与编码 | 内部数据库批量查询取消字节截断（REQ-3241c885-9387-459c-a1d3-726d79e34ac9） |
| 业务线 | 工作台（BL000012） |
| 涉及仓库 | workhub-server，MCP 数据库执行与运维批量查询 |
| 分支与核查环境 | codex/development-20261122；本机源码与隔离测试 |
| 版本 | v1 |
| 来源与确认 | 2026-11-22 用户提供 XXL-JOB 查询回执异常，明确“你帮我查下，然后删除该限制”；按该授权实施字节限制移除 |
| 负责人/评审 | Codex 实施；用户授权 |

## 实现
内部 runReadonlyQueries 在 runStatements 中选择无字节预算的读取方法；runReadonlyQuery/explainQuery 保持有限预算。resultSetToRows 提供无预算重载，公共读取过程仅在预算存在时累计 UTF-8 字节并截断；无预算保留同样的 columns/rows/truncatedByBytes/dataMasked 响应形状。配置及数据库无变更。
McpXxlJobDatabaseQueryClient 逐项校验并补充查询序号；区分回执缺失、截断标记缺失或错误、实际截断、rows 类型及行结构错误，不输出 SQL、结果值或凭据。

## 约束和回滚
大字段/多行结果增加内存用量，仍受 SQL/JDBC 行数和查询超时约束。不得用增大默认值冒充删除限制，不得忽略截断标记。发布后生效，回滚对应程序即可，无 DDL 或数据回填。

## 验证
CachedRowSet 构造合成中文长字段，走有限和无预算实际转换代码；回执测试覆盖批次错误、空结果、目标/readonly 绑定。线上数据大小和实际失败 SQL 待提供环境/监测项核实。
