# Tasks：业务线支持环境配置与环境编码统一大写

## 1. 元信息

- 需求编码：REQ-95321398-940e-470a-abde-3fc77a348b98
- 需求名称与唯一需求目录：业务线支持环境配置与环境编码统一大写；`docs/业务线/BL000012-工作台/需求迭代/REQ-95321398-940e-470a-abde-3fc77a348b98-业务线支持环境配置与环境编码统一大写/`
- 涉及仓库与功能域：workhub-server（project/mcp/ops/notification/db schema）、workhub-web（业务线编辑、MCP 资源页、运维监测页）
- 所属项目或业务线：不适用（WorkHub 平台自身功能）
- Proposal 版本：v1
- Design/契约版本：v1
- 目标环境与分支：server `codex/development-20261102`；web 当前开发分支（待确认）
- 任务计划状态：v1 已确认，已实施（2026-11-03）
- 负责人/评审人：用户

## 2. 任务总览与依赖

| 任务 | 交付物 | 前置任务 | 可并行条件 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- | --- |
| T-01 | 迁移 SQL（V20261103_140000） | 无 | 与 T-02 并行 | §3 数据库变更 / AC-05 | 已完成 |
| T-02 | 后端业务线支持环境模型与校验（project 域） | 无 | 与 T-01 并行 | §3 契约 / AC-01、AC-02 | 已完成 |
| T-03 | 后端 MCP 资源环境大写与归属校验（mcp 域） | T-02（复用归一化工具方法） | 与 T-01 并行 | §3 契约 / AC-03、AC-04 | 已完成 |
| T-04 | 后端运维/通知写入点大写归一化（ops/notification 域） | T-02 | 与 T-01、T-03 并行 | §3 契约 / AC-04 | 已完成 |
| T-05 | 后端单测更新与补充 | T-02、T-03、T-04 | 串行收口 | AC-02、AC-03、AC-04、AC-06 | 已完成 |
| T-06 | 前端类型与业务线编辑弹窗（workhub-web） | T-02（契约确定后） | 与 T-03、T-04 并行 | AC-01 | 已完成 |
| T-07 | 前端 MCP 资源页与运维监测页环境选项 | T-06 | 与 T-05 并行 | AC-03、AC-04 | 已完成 |
| T-08 | 前端构建验证 | T-06、T-07 | 串行收口 | AC-01、AC-03 | 已完成 |
| T-09 | 系统设计与迭代日志更新 | 全部 | 串行收口 | §5 文档更新 | 已完成 |

执行顺序：T-01/T-02 → T-03/T-04（可并行）→ T-05、T-06 → T-07 → T-08 → T-09。前端两仓库分支独立提交，互不写入对方文件。

## 3. 任务明细

### T-01：迁移 SQL

- 描述与目标：新增 Flyway 脚本，完成支持环境列新增、存量环境编码大写迁移、业务线支持环境回填。
- 输入：Design §3 数据库变更；现有 schema 文件清单。
- 输出：`V20261103_140000__business_line_supported_environments_and_uppercase.sql`。
- 前置依赖：无。
- 允许修改范围：`workhub-bootstrap/src/main/resources/db/schema/mysql/` 新增一个文件。
- 明确不修改范围：既有迁移脚本、Java 代码。
- 项目补充任务字段：建议研发任务标题“业务线支持环境配置迁移脚本”。

验收条件：

- [ ] 脚本含备份表创建、8 张表 `UPPER` 更新、`supported_environments` 回填三类语句。
- [ ] 回填来源覆盖访问配置与 MCP 资源两个表，编码去重、大写、稳定排序。
- [ ] 本地对测试库或 H2/内存库执行通过（如项目验证方式支持）。

计划验证方式（尚未执行）：SQL 语句静态核对 + 启动日志确认 Flyway 执行成功。

状态及真实结果：已完成；`V20261103_140000__business_line_supported_environments_and_uppercase.sql` 含备份表、8 张表 UPPER 迁移与访问配置+MCP 资源并集回填。Flyway 执行待应用下次启动验证。

### T-02：后端业务线支持环境模型与校验

- 描述与目标：`BusinessLineEntity/SaveRequest/Response` 增加 `supportedEnvironments`；`BusinessLineMapper` 列与 SQL 扩展；`ProjectService` 支持环境归一化（非空、去重、大写、格式校验）、访问配置环境大写化并校验归属；响应输出有序列表。
- 输入：Design §3；`ProjectService` 现有归一化方法。
- 输出：workhub-model、workhub-dao、workhub-service 对应改动。
- 前置依赖：无。
- 允许修改范围：`model/project`、`dao/project/BusinessLineMapper`、`service/project/ProjectService`。
- 明确不修改范围：项目（project）其他接口、MCP/运维服务。
- 项目补充任务字段：建议研发任务标题“业务线支持环境配置后端”。

验收条件：

- [ ] 保存业务线：支持环境空/重复/非法字符被拒绝；访问配置环境不属于支持环境被拒绝；合法数据落库为 CSV 大写。
- [ ] 响应 `supportedEnvironments` 为有序大写列表。

计划验证方式（尚未执行）：`ProjectServiceBusinessLineAccessConfigTest` 扩展 + 新增支持环境用例。

状态及真实结果：已完成；BusinessLineEntity/SaveRequest/Response、BusinessLineMapper、ProjectService 支持环境归一化与访问配置归属校验（含保留存量时的存量行校验）落地。

### T-03：后端 MCP 资源环境大写与归属校验

- 描述与目标：`McpResourceService` 保存时环境编码大写归一化 + 格式校验 + 归属所属业务线支持环境校验。
- 输入：Design §3；T-02 的归一化方法（或抽到共用处）。
- 输出：`McpResourceService` 改动。
- 前置依赖：T-02。
- 允许修改范围：`service/mcp/McpResourceService`（及必要的 model 注释）。
- 明确不修改范围：MCP 运行时匹配逻辑（`normalizeText` 已大小写不敏感）、连接测试器。
- 项目补充任务字段：建议研发任务标题“MCP 资源环境编码大写与归属校验”。

验收条件：

- [ ] 保存 MCP 资源：环境小写输入被归一为大写落库；越界环境被拒绝。
- [ ] 业务线未配置支持环境（历史数据）时保存行为明确定义并测试。

计划验证方式（尚未执行）：`McpResourceServiceTest` 扩展。

状态及真实结果：已完成；McpResourceService 保存环境大写归一化 + 格式校验 + 归属业务线支持环境校验；无支持环境的历史业务线仅归一化。

### T-04：后端运维/通知写入点大写归一化

- 描述与目标：`OpsMonitorService`、`SystemAlertScopeService`、`SystemAlertService`（人工接入）、`NotificationService`、`SystemAlertCleanupTaskService`、`AccountingMonitorService` 的环境编码写入统一大写归一化（不做归属校验）。
- 输入：Design §3 写入点清单。
- 输出：上述服务的小幅改动。
- 前置依赖：T-02。
- 允许修改范围：列出的服务类。
- 明确不修改范围：ELK 采集器（继承范围行环境编码，无需改）、查询服务。
- 项目补充任务字段：建议研发任务标题“运维与通知环境编码大写归一化”。

验收条件：

- [ ] 各写入点落库值为大写；空值语义保持不变（`sys_notification.environment_code` 可空）。

计划验证方式（尚未执行）：相关单测扩展或断言补充。

状态及真实结果：已完成；OpsMonitorService、SystemAlertScopeService、SystemAlertService、NotificationService、SystemAlertCleanupTaskService、AccountingMonitorService 写入点大写归一化；BusinessLogSearchService 入口环境参数大写化保证 MCP 小写参数兼容。

### T-05：后端单测更新与补充

- 描述与目标：更新受影响断言（原小写断言），补支持环境与大写归一化用例，回归 MCP 运行时匹配用例。
- 输入：T-02～T-04 改动。
- 输出：测试通过证据。
- 前置依赖：T-02、T-03、T-04。
- 允许修改范围：`workhub-service/src/test`、`workhub-controller/src/test` 中受影响用例。
- 明确不修改范围：与本次无关的测试。
- 项目补充任务字段：无。

验收条件：

- [ ] `mvn test`（或项目惯用命令）相关模块全部通过。

计划验证方式（尚未执行）：Maven 测试报告。

状态及真实结果：已完成；workhub-service 全量 462 项、workhub-controller 59 项测试通过（0 失败 0 错误），含新增支持环境越界/重复/为空用例与 MCP 运行时回归；旧小写断言夹具按迁移后大写口径更新。

### T-06：前端类型与业务线编辑弹窗

- 描述与目标：`types/work-item.ts` 的 `ProjectGroup` 增加 `supportedEnvironments`；`api/project.ts` 归一化映射；业务线弹窗增加支持环境编辑器（多选可创建，预设 DEV/FAT/TEST/UAT/STAGING/PROD），访问配置行环境下拉取当前表单支持环境，默认值取首个支持环境。
- 输入：T-02 契约；`ProjectListView.vue` 现有表单。
- 输出：workhub-web 改动。
- 前置依赖：T-02（契约确定）。
- 允许修改范围：`src/types/work-item.ts`、`src/api/project.ts`、`src/views/project/ProjectListView.vue`。
- 明确不修改范围：MCP/运维/支付页面（T-07 范围除外）、后端仓库。
- 项目补充任务字段：无。

验收条件：

- [ ] 编辑弹窗可增删支持环境；访问配置环境选项随支持环境联动；提交载荷为大写编码数组。

计划验证方式（尚未执行）：前端构建 + 页面核对。

状态及真实结果：已完成；ProjectGroup 类型、normalizeBusinessLine、保存载荷接入 supportedEnvironments；业务线弹窗新增支持环境编辑器（预设 + 可创建，变更时大写去重），访问地址环境选项联动、默认取首个支持环境。

### T-07：前端 MCP 资源页与运维监测页环境选项

- 描述与目标：`McpResourceView.vue` 表单环境选项按所选业务线的支持环境动态取值，筛选环境下拉取全部启用业务线支持环境并集；`OpsMonitorView.vue` 静态环境选项改为大写口径（本期不动态化）。
- 输入：T-06 的类型与数据。
- 输出：两个视图的改动。
- 前置依赖：T-06。
- 允许修改范围：`src/views/mcp/McpResourceView.vue`、`src/views/ops/OpsMonitorView.vue`。
- 明确不修改范围：支付配置页、其他页面。
- 项目补充任务字段：无。

验收条件：

- [ ] MCP 资源表单切换业务线后环境选项联动；存量行（迁移后大写）标签可正确显示。
- [ ] 运维监测页选项与迁移后数据口径一致。

计划验证方式（尚未执行）：前端构建 + 页面核对。

状态及真实结果：已完成；McpResourceView 筛选/表单环境选项按业务线支持环境动态取值（含公共资源回退全集与联动重置），行内标签用共享映射；OpsMonitorView 选项改大写静态口径。支付页未改。

### T-08：前端构建验证

- 描述与目标：workhub-web 构建/类型检查通过。
- 输入：T-06、T-07 改动。
- 输出：构建证据。
- 前置依赖：T-06、T-07。
- 允许修改范围：无（只读验证）。
- 明确不修改范围：无。
- 项目补充任务字段：无。

验收条件：

- [ ] `npm run build`（或项目惯用命令）通过。

计划验证方式（尚未执行）：构建日志。

状态及真实结果：已完成；workhub-web `vue-tsc --noEmit` 类型检查与 `vite build` 通过（2026-11-03，于全部前端改动完成后重新执行）。补充排障：首次构建早于 ProjectListView 重构，漏检 `supportedEnvironmentPresets` 未导入的引用错误导致业务线页/MCP 页白屏；补齐 import 后重建，并经 WebKit 浏览器实测登录、工作台、业务线、MCP、业务监测页全部正常渲染。

### T-09：系统设计与迭代日志更新

- 描述与目标：更新 `docs/业务线/BL000012-工作台/现状.md` 环境口径相关章节与 `docs/业务线/BL000012-工作台/迭代日志.md` 本条目。
- 输入：全部任务结果。
- 输出：文档更新。
- 前置依赖：全部。
- 允许修改范围：上述两个文档及本目录 tasks 记录。
- 明确不修改范围：其他文档。
- 项目补充任务字段：无。

验收条件：

- [ ] 文档反映新的环境口径与业务线支持环境能力。

计划验证方式（尚未执行）：文档核对。

状态及真实结果：已完成；系统设计新增第 12 节，迭代日志新增 2026-11-03 条目。

## 4. 覆盖与评审

- Proposal 成功标准映射：AC-01→T-06、AC-02→T-02/T-05、AC-03→T-03/T-07/T-05、AC-04→T-04/T-05、AC-05→T-01、AC-06→T-05，均已覆盖。
- Design 接口/数据/非功能约束承接：契约→T-02～T-04；数据库变更→T-01；发布顺序与回滚→Design §5，随 T-09 落迭代日志。
- 未覆盖项及原因：无。
- 已生效事实的核实与文档更新：T-09。
- 关联版本链接：[proposal.md](proposal.md)、[design.md](design.md)。
- 完整规格统一确认的范围、规格版本、结论、确认人与日期：Proposal/Design/Tasks v1 全量确认，2026-11-03 由用户确认（原始需求描述 + 两项取舍按推荐执行：支持环境必填非空、存量历史数据全部迁移大写），确认后实施。

## 5. 实施记录（2026-11-03）

- 后端改动：`workhub-model`（BusinessLineEntity/SaveRequest/Response）、`workhub-dao/BusinessLineMapper`、`workhub-service`（ProjectService、McpResourceService、OpsMonitorService、SystemAlertScopeService、SystemAlertService、NotificationService、SystemAlertCleanupTaskService、AccountingMonitorService、BusinessLogSearchService）、`workhub-bootstrap` 迁移脚本。
- 前端改动（workhub-web）：`types/work-item.ts`、`api/project.ts`、`constants/environment.ts`（新增）、`views/project/ProjectListView.vue`、`views/mcp/McpResourceView.vue`、`views/ops/OpsMonitorView.vue`。
- 验证命令：`mvn test -pl workhub-service,workhub-controller -am`（462+59 项通过）；`npm run build`（vue-tsc + vite 构建通过）。
- 追加调整（2026-11-03 用户要求）：预设环境编码改为 DEV/FAT/TEST/UAT/PRE/PROD，展示去掉括号中文标签；已确认库内无 STAGING 存量数据，无需迁移；重新构建并经浏览器实测。
- 剩余风险：Flyway 迁移已在开发库执行成功（20261103.140000）；页面交互已经浏览器实测通过（登录、工作台、业务线、MCP 资源、业务监测）。
