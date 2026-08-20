# ELK 系统配置与真实字段映射自动化测试用例

## 1. 需求信息

- 需求名称：ELK 系统配置与真实字段映射
- 研发方案文件：`docs/研发设计/迭代/2026-07-23-elk-system-config-field-mapping.md`
- 测试用例文件路径：`docs/test-cases/2026-07-23-elk-system-config-field-mapping.md`
- 涉及系统：workhub-server、workhub-web、Elasticsearch
- 涉及模块：系统配置、系统审计、ELK 定时采集
- 编写时间：2026-07-23
- 编写人或代理：Codex

## 2. 测试范围

- 核心场景：系统配置优先、真实字段查询与映射、子系统多索引、PIT 分页、敏感配置审计。
- 边界场景：环境字段为空、系统开关缺失、配置关闭、参数上下限。
- 异常场景：认证失败、非法 JSON、非法配置、PIT 关闭失败。
- 不纳入自动化测试：真实生产 ELK 联调，原因是需要临时凭据和生产网络授权。

## 3. 测试环境

- 后端环境：本机 JDK 25、Maven。
- 前端环境：本机 Node/npm。
- 数据库环境：单元测试使用 Mapper 替身；Flyway 脚本通过构建和静态校验。
- 外部依赖：本地临时 HTTP Server 模拟 Elasticsearch。
- 测试数据：模拟 `amp-saps-2026.07.23` 日志命中。

## 4. 自动化测试用例

| 用例编号 | 类型 | 覆盖目标 | 前置条件 | 测试方法 | 预期结果 | 执行结果 | 备注 |
|---|---|---|---|---|---|---|---|
| TC-001 | 核心 | 系统配置及子系统多索引 | 子系统配置两个索引模式 | `ElkAlertRuntimeConfigServiceTest` | 子系统索引表达式优先，字段默认正确 | 通过 |  |
| TC-002 | 兼容 | 环境变量回退 | 系统开关不存在 | `ElkAlertRuntimeConfigServiceTest` | 使用旧 ECS 字段 | 通过 |  |
| TC-003 | 边界 | 系统开关关闭 | 环境变量开、系统配置关 | `ElkAlertRuntimeConfigServiceTest` | 系统配置关闭权威生效 | 通过 |  |
| TC-004 | 异常 | 数值配置非法 | pageSize 超上限 | `ElkAlertRuntimeConfigServiceTest` | 安全失败并指出配置键 | 通过 |  |
| TC-005 | 核心 | 真实字段查询映射 | 模拟 amp-saps 日志 | `ElasticsearchSystemAlertClientTest` | 查询 keyword 字段、映射 source 字段 | 通过 |  |
| TC-006 | 边界 | 无环境字段 | environmentQueryField 为空 | 检查请求 JSON | 不包含环境过滤条件 | 通过 |  |
| TC-007 | 核心 | PIT 分页与关闭 | 每页 1 条 | 既有客户端测试 | search_after 推进并关闭 PIT | 通过 |  |
| TC-008 | 异常 | 认证失败脱敏 | ES 返回 401 | 既有客户端测试 | 只返回安全错误，不含响应体 | 通过 |  |
| TC-009 | 异常 | 非法 JSON | ES 返回非 JSON | 既有客户端测试 | 报响应异常且关闭 PIT | 通过 |  |
| TC-010 | 安全 | 配置审计脱敏 | 更新 SECRET | `SysConfigServiceTest` | 前后快照无明文/密文 | 通过 |  |
| TC-011 | 核心 | 采集幂等和通知 | 模拟事件/子系统 | `ElkSystemAlertCollectorTest` | 正常落库、游标和通知；失败隔离 | 通过 |  |
| TC-012 | 页面 | 页面说明和编译 | 前端依赖完整 | `npm run build` | 构建成功，文案包含 ELK 约定 | 通过 |  |
| TC-013 | 核心 | 子系统多索引持久化 | 请求含重复和多个模式 | `SystemAlertServiceTest` | 按输入顺序去重并保存两个关系行 | 通过 |  |
| TC-014 | 核心 | 采集器组合多个索引 | 子系统存在两个关系行 | `ElkSystemAlertCollectorTest` | 组合为逗号分隔表达式传给 ES 客户端 | 通过 |  |
| TC-015 | 接口 | 响应返回多个索引 | 看板含子系统 | `SystemAlertControllerTest` | `indexPatterns` 数组按顺序返回 | 通过 |  |
| TC-016 | 数据库 | Flyway 关系表 | 执行新增迁移 | 检查 `flyway_schema_history` 和表约束 | 版本成功，唯一约束和排序索引存在，且不要求 `REFERENCES` 权限 | 待环境验证 | 本地首次执行发现运行账号无 `REFERENCES` 权限，迁移已调整为服务层事务清理关联数据 |
| TC-017 | 兼容 | 历史子系统无关系行 | `indexPatterns` 为空 | 配置解析测试 | 回退旧服务级、全局或环境变量索引 | 通过 |  |
| TC-018 | 性能 | 高频错误每轮上限 | ES 单页返回多条，`maxEventsPerRun=1` | `ElasticsearchSystemAlertClientTest` | 仅消费最近 1 条并关闭 PIT | 通过 |  |
| TC-019 | 降噪 | 多事件汇总通知 | 同一子系统返回多条新事件 | `ElkSystemAlertCollectorTest` | 事件逐条入库，本轮只创建 1 条汇总通知 | 通过 |  |
| TC-020 | 幂等 | 重复事件不通知 | 事件插入返回 0 | `ElkSystemAlertCollectorTest` | 不重复落库，也不创建通知 | 通过 |  |

## 5. 执行命令与结果

| 执行时间 | 执行人 | 命令 | 结果 | 关键输出 | 备注 |
|---|---|---|---|---|---|
| 2026-07-23 | Codex | `mvn -q -pl workhub-service,workhub-controller -am -Dtest=ElkAlertRuntimeConfigServiceTest,ElasticsearchSystemAlertClientTest,ElkSystemAlertCollectorTest,SystemAlertServiceTest,SystemAlertControllerTest,SysConfigServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过 | 相关测试通过 | 覆盖多索引持久化、组合查询、历史回退、本地 HTTP、采集上限、汇总通知和接口响应 |
| 2026-07-23 | Codex | 本地 ELK 定时采集联调 | 通过 | 4 个子系统成功，0 失败 | `amp-saps` 每轮限制 200 条；通知按子系统汇总 |
| 2026-07-23 | Codex | `mvn -q -pl workhub-controller -am -DskipTests compile` | 通过 | Controller 及依赖模块编译成功 |  |
| 2026-07-23 | Codex | `npm run build` | 通过 | Vue 类型检查和 Vite 构建成功 |  |
| 2026-07-23 | Codex | `mvn -q test` | 未全绿 | 共执行 258 个测试，1 个既有 `IntakeServiceTest` 失败 | 期望运维工作量 `4h`，实际为空；与本次 ELK 改动文件无关 |

## 6. 未覆盖风险

- 未覆盖：生产 ELK 的真实权限、网络超时和数据量表现。
- 原因：未使用或持久化生产凭据。
- 影响：上线前仍需执行只读联调。
- 后续：配置完成后观察同步状态，抽查最新 ERROR 事件和游标。

## 7. 测试结论

- 是否完成计划自动化测试：后端核心自动化和前端构建已完成。
- 是否存在失败用例：本需求针对性测试无失败；全量回归存在 1 个需求录入模块既有失败。
- 是否允许进入交付：本需求范围可以交付，但仓库全量测试不是全绿状态。
- 结论：真实字段、配置优先级、脱敏审计、采集兼容逻辑和页面构建已被验证；全量回归失败项需由需求录入模块单独处理。
