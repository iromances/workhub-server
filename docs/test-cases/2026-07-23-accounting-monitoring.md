# 测试用例：账务监测

## 1. 测试范围

- 需求：运维菜单新增账务监测。
- 环境：开发代码验证；生产数据库只通过 WorkHub MCP `readonly` profile 执行小时间窗聚合查询。
- 分支：WorkHub 当前开发工作树；业务系统基线为生产 `master`。
- 涉及模块：model、dao、service、controller、job、bootstrap、workhub-web。
- 业务线：WorkHub 已配置的 10 条业务线；生产只读实测覆盖当前可访问的嘉泰消费分期和嘉泰设备租赁。
- 安全边界：未执行生产 DDL/DML，未触发支付、核销、退款、放款或外部通知。

## 2. 用例

| 编号 | 类型 | 场景 | 预期 | 结果 |
| --- | --- | --- | --- | --- |
| TC-001 | 单元 | 规则包注册 | 通用、assets、amp、物流规则包包含对应专项规则 | 通过 |
| TC-002 | 单元 | 小时区间 SQL | 使用 `[start,end)`，不使用 `BETWEEN` | 通过 |
| TC-003 | 单元 | 非法 schema | 包含非字母、数字、下划线时拒绝构造 SQL | 通过 |
| TC-004 | 单元 | 非整日区间执行日级规则 | 规则标记 `SKIPPED`，批次标记 `PARTIAL` | 通过 |
| TC-005 | 单元 | 小时级规则发现异常 | 保存异常数、差异金额和脱敏样例，批次标记 `WARNING` | 通过 |
| TC-006 | 单元 | 手动时间未对齐整点 | 请求被拒绝且不创建批次 | 通过 |
| TC-007 | 单元 | 手动区间超过 31 天 | 请求被拒绝 | 通过 |
| TC-008 | Controller | 看板摘要 | 返回监测数、状态数和异常数 | 通过 |
| TC-009 | Controller | 手动请求缺少起止时间 | 返回 HTTP 400 | 通过 |
| TC-010 | 前端构建 | 类型、菜单、路由和页面 | `vue-tsc` 与 Vite 生产构建通过 | 通过 |
| TC-011 | 生产只读 | 嘉泰交易金额小时规则 | 聚合 SQL 可执行，不读取未脱敏业务明细 | 通过 |
| TC-012 | 生产只读 | 嘉泰支付明细与科目明细小时规则 | 聚合 SQL 可执行 | 通过 |
| TC-013 | 生产只读 | 设备租赁对账单孤儿资源小时规则 | 聚合 SQL 可执行 | 通过 |
| TC-014 | 生产只读 | 日级账单金额字段分布 | 确认历史 `red_amount` 允许为空并按零处理 | 通过，已修正规则 |
| TC-015 | 集成 | Flyway 正式执行 | 新增表、逻辑关联索引、权限和种子配置成功，且不要求 `REFERENCES` 权限 | 通过；失败现场审计和 `repair` 后，无外键迁移执行成功 |
| TC-016 | 生产只读 | 其余业务线真实规则 | DB 资源可连接并完成小窗口验证 | 阻塞；`db-prod`、`amp-db-prod` 堡垒机认证失败，BL000009/BL000010 未绑定生产 DB |
| TC-017 | 单元 | 规则覆盖配置全部关闭 | 不回退为执行规则包全部规则 | 通过 |
| TC-018 | 单元 | 对账线程池拒绝任务 | 批次标记失败并提示稍后重试 | 通过 |

## 3. 执行记录

| 时间 | 执行人 | 命令或方式 | 结果 | 说明 |
| --- | --- | --- | --- | --- |
| 2026-07-23 | Codex | `mvn -q -pl workhub-controller,workhub-job -am -DskipTests compile` | 通过 | 后端受影响模块编译通过 |
| 2026-07-23 | Codex | 定向测试，显式挂载 Byte Buddy `javaagent` | 通过 | 11 个账务监测定向用例通过 |
| 2026-07-23 | Codex | 全量 Maven 测试，显式挂载 Byte Buddy `javaagent` | 部分通过 | 共 267 个用例，266 个通过；1 个既有 `IntakeServiceTest` 与本需求无关，预期 `4h` 实际为空 |
| 2026-07-23 | Codex | `npm run build` | 通过 | TypeScript 检查和 Vite 生产构建通过 |
| 2026-07-23 | Codex | WorkHub MCP 生产 `readonly` 小时间窗聚合 SQL | 通过 | 嘉泰消费分期、嘉泰设备租赁规则 SQL 成功执行 |
| 2026-07-24 | Codex | 审计 `flyway_schema_history`、目标表和约束 | 通过 | 确认失败版本为 `V20260723_210000`，仅残留 `ops_account_monitor_config`，失败原因为迁移账号缺少 `REFERENCES` 权限 |
| 2026-07-24 | Codex | 移除数据库外键、执行 Flyway `repair` 并重新迁移 | 通过 | 版本 `20260723.210000` 最终为 `success=true`；4 张表全部存在，仅保留主键、唯一约束和逻辑关联索引 |
| 2026-07-24 | Codex | 账务监测定向测试，显式挂载 Byte Buddy `javaagent` | 通过 | 9 个账务监测 service/controller 用例通过 |
| 2026-07-24 | Codex | `mvn -q -DskipTests clean package`、应用启动及 `/api/system/ping` | 通过 | Flyway validate 通过，schema 无待执行迁移，应用启动成功，健康状态为 `UP` |

## 4. 剩余风险

- 开发库已完成 Flyway 失败现场审计、`repair`、无外键迁移和二次启动校验；其他环境仍需按相同流程核对 `flyway_schema_history`。
- `db-prod`、`amp-db-prod` 连接恢复后，需逐业务线执行 `EXPLAIN` 和小时间窗试跑。
- `assets-saps` 三条业务线启用前，必须补充项目/产品映射，避免共享 schema 数据串线。
- 生产每日调度总开关和每条业务线 `daily_enabled` 当前均默认关闭。
- 仓库全量测试仍有 1 个与本需求无关的既有失败：`IntakeServiceTest.list_shouldFallbackOperationsEffortAndDatesFromStructuredData`。
