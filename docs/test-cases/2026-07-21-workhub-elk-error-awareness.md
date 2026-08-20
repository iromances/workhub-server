# WorkHub 自动感知 ELK 错误日志测试用例

## 1. 需求信息

- 需求名称：WorkHub 自动感知 ELK 错误日志
- 研发方案文件：`docs/研发设计/迭代/2026-07-21-workhub-elk-error-awareness.md`
- 测试用例文件路径：`docs/test-cases/2026-07-21-workhub-elk-error-awareness.md`
- 涉及系统：WorkHub Server、MySQL、Elasticsearch
- 涉及模块：model、support、dao、service、job、bootstrap
- 编写时间：2026-07-21
- 编写人或代理：Codex

## 2. 测试范围

- 核心场景：增量查询、ECS 字段映射、幂等落库、游标推进、业务线成员通知。
- 边界场景：空结果、同时间戳分页、缺失可选字段、批次上限、无业务线成员。
- 异常场景：ELK 未启用、认证失败、超时、非 JSON、响应字段异常、单子系统失败和重复执行。
- 不纳入自动化测试：真实 ELK 网络、TLS、目标索引权限和浏览器页面联调；需要部署环境及受控只读账号，采用非生产手工冒烟替代。

## 3. 测试环境

- 后端环境：本地 Maven/JDK 25 单元测试。
- 前端环境：不涉及前端代码改动。
- 数据库环境：Mapper SQL 代码检查及现有测试替身；真实 Flyway 迁移在部署前验证。
- 外部依赖：单元测试使用本地 HTTP Mock，不访问真实 Elasticsearch。
- 测试数据准备方式：构造 ECS `_search` JSON 响应、关注子系统、同步游标和用户列表。

## 4. 自动化测试用例

| 用例编号 | 场景类型 | 覆盖目标 | 前置条件 | 执行命令或测试方法 | 预期结果 | 开发完成后执行结果 | 失败原因或备注 |
|---|---|---|---|---|---|---|---|
| TC-001 | 核心 | 正常构造 ELK 查询并映射事件 | 配置启用且返回一条 ERROR | 客户端单测 | 查询包含服务、环境、级别、游标；字段映射正确 | 通过 | PIT 请求及 ECS 字段断言通过 |
| TC-002 | 核心 | 新事件幂等落库并通知 | Mapper 首次插入返回 1 | 采集器单测 | 插入事件、推进游标、通知业务线成员 | 通过 | 插入调用、UTC 游标和接收人断言通过 |
| TC-003 | 核心 | 重复事件不重复落库，通知写入保持幂等 | Mapper 插入返回 0 | 采集器单测 | 不重复落库；通知使用稳定 dedupe key | 通过 | Mapper 返回 0 时仍以同一 dedupe key 补偿通知 |
| TC-004 | 边界 | 空结果 | ELK 返回空 hits | 客户端单测 | 停止分页，不生成事件 | 通过 | 第二页空 hits 正常结束并关闭 PIT |
| TC-005 | 边界 | 同时间戳多页稳定分页 | PIT 返回达到 pageSize 且带 `@timestamp + _shard_doc` sort | 客户端/采集器单测 | 使用最新 PIT ID 和 `search_after` 继续读取，关闭 PIT；下一轮边界重复由唯一索引消除 | 通过 | 第二次请求包含 `search_after`，最终 PIT 已关闭 |
| TC-006 | 边界 | 缺少可选异常字段 | 只有 `@timestamp`、service 和 message | 客户端单测 | 事件保留，异常类型、堆栈允许为空 | 待执行 | 当前自动化未单独构造该响应，代码为空值兼容 |
| TC-007 | 边界 | 无业务线成员 | 用户查询为空 | 采集器单测 | 通知接收人回退 `admin` | 通过 | admin 回退断言通过 |
| TC-008 | 异常 | 总开关关闭 | enabled=false | 任务/采集器单测 | 不访问 ELK、不写数据库 | 通过 | 外部源调用次数和插入次数均为 0 |
| TC-009 | 异常 | ELK 401/403、超时和连接失败 | Mock 返回失败或延迟 | 客户端单测 | 返回安全分类，不包含凭据和原始正文 | 部分通过 | 401 与原始正文不泄露已覆盖；403、超时和连接失败未逐项造数 |
| TC-010 | 异常 | 非 JSON 或响应结构错误 | Mock 返回非法内容 | 客户端单测 | 当前子系统失败，游标不推进 | 通过 | 非 JSON 返回安全错误且 PIT 仍关闭 |
| TC-011 | 异常 | 单子系统失败隔离 | 两个子系统，一个失败 | 采集器单测 | 继续采集另一个子系统 | 通过 | 失败数 1、后续新增和成功数均为 1 |
| TC-012 | 数据库 | Flyway 结构变更 | 隔离 MySQL | 执行迁移并查询元数据 | 新表、字段和唯一索引存在，历史事件兼容 | 待执行 | 需部署前验证 |
| TC-013 | 回归 | 现有系统预警查询 | 本地存在 LOCAL/ELK 事件 | 运行现有 Service/Controller 测试 | 默认最近一小时 ERROR 查询保持兼容 | 通过 | Service 与 Controller 既有用例各 1 条通过 |
| TC-014 | 编译 | 全仓模块依赖正确 | 代码完成 | `mvn -q -DskipTests compile` | 编译成功 | 通过 | 全仓编译成功 |
| TC-015 | 回归 | Spring 容器可创建 ELK 客户端与采集器 Bean | 当前多模块已安装，Flyway 和 ELK 采集关闭 | 临时启动 `workhub-bootstrap` | 应用正常启动，不再查找无参构造器 | 通过 | 2026-07-22 修复两个多构造器 Bean 的显式注入，应用 1.375 秒启动成功 |

## 5. 执行命令与结果

开发完成后只填写实际执行结果。

| 执行时间 | 执行人或代理 | 命令 | 结果 | 关键输出 | 备注 |
|---|---|---|---|---|---|
| 2026-07-21 20:06 CST | Codex | `mvn -q -pl workhub-service,workhub-controller -am -Dtest=SystemAlertServiceTest,SystemAlertControllerTest,ElasticsearchSystemAlertClientTest,ElkSystemAlertCollectorTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过 | 9 个测试，0 失败、0 错误 | 沙箱外本地随机端口 Mock ELK |
| 2026-07-21 20:05 CST | Codex | `mvn -q -DskipTests compile` | 通过 | 退出码 0 | 全仓编译 |
| 2026-07-21 18:02 CST | Codex | `npm run build`（workhub-web） | 通过 | 1731 modules transformed，built in 7.82s | 生产构建 |
| 2026-07-22 09:16 CST | Codex | ELK 与系统预警相关 9 个 Maven 测试 | 通过 | 9 个测试，0 失败、0 错误 | 构造器注入修复回归 |
| 2026-07-22 09:18 CST | Codex | `FLYWAY_ENABLED=false WORKHUB_ELK_ALERT_ENABLED=false mvn -q -pl workhub-bootstrap spring-boot:run` | 通过 | `Started WorkhubServerApplication in 1.375 seconds` | 验证后已主动停止进程 |

## 6. 未覆盖风险

- 未覆盖场景：真实 MySQL Flyway 执行、生产 ELK 实际字段差异、TLS 证书链、完整账号失败分类、超大积压、真实通知噪音。
- 未覆盖原因：依赖目标环境与只读凭据，不能在本地单元测试中安全复现。
- 可能影响：采集失败、字段缺失、同步延迟或通知数量超过预期。
- 后续补充计划：非生产单服务灰度，核对请求耗时、事件数、游标和通知后再开启生产。

## 7. 测试结论

- 是否完成计划自动化测试：部分完成；核心采集、幂等、通知、隔离、回归、编译和前端构建已完成，真实数据库与 ELK 联调待部署环境执行。
- 是否存在失败用例：无失败用例；TC-006、TC-012 待执行，TC-009 部分通过。
- 是否允许进入交付：允许进入代码交付；生产开关保持关闭，完成非生产 Flyway 与 ELK 冒烟后再开启。
- 结论说明：自动化测试共 9 条全部通过；未验证真实外部环境，不声明生产链路已打通。
