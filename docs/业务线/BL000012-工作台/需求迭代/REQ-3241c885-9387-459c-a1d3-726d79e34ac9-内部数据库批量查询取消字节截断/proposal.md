# Proposal：内部数据库批量查询取消字节截断

| 项目 | 内容 |
| --- | --- |
| 名称与编码 | 内部数据库批量查询取消字节截断（REQ-3241c885-9387-459c-a1d3-726d79e34ac9） |
| 业务线 | 工作台（BL000012） |
| 涉及仓库 | workhub-server，MCP 数据库执行与运维批量查询 |
| 分支与核查环境 | codex/development-20261122；本机源码与隔离测试 |
| 版本 | v1 |
| 来源与确认 | 2026-11-22 用户提供 XXL-JOB 查询回执异常，明确“你帮我查下，然后删除该限制”；按该授权实施字节限制移除 |
| 负责人/评审 | Codex 实施；用户授权 |

## 背景和范围
XXL-JOB 定时监测 collect → FunctionCallService → McpXxlJobDatabaseQueryClient → DatabaseDiagnosticService.runReadonlyQueries。执行端按 profile.maxResultBytes（默认 65536）截断，消费者拒绝截断回执，导致监测 ERROR。实际环境/监测项未知，仅源码机制已核实，不将推断写成线上定因。
取消内部批量结果字节截断，不删除完整回执校验。该公共内部通路也服务 FunctionCallService 的其他监测/核验调用方。外部 MCP 单条诊断输出预算、行数、超时、只读及脱敏不变；不改真实配置、不部署。

## 验收
- AC-01：批量读取超过 64 KiB 的中文单行、多行均保留完整值，truncatedByBytes=false。
- AC-02：外部单条诊断仍遵循配置预算及既有默认预算，超限标记为 true。
- AC-03：缺失、类型错误和真实截断仍拒绝，不变成空结果或成功；错误给出批次序号及原因。
- AC-04：行数及脱敏保留，运行既有回执/审计回归，不安装依赖、不访问业务数据库。
