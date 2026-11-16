# Design：业务线支持环境配置与环境编码统一大写

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-95321398-940e-470a-abde-3fc77a348b98 |
| 需求名称与唯一需求目录 | 业务线支持环境配置与环境编码统一大写；`docs/业务线/BL000012-工作台/需求迭代/REQ-95321398-940e-470a-abde-3fc77a348b98-业务线支持环境配置与环境编码统一大写/` |
| 涉及仓库与功能域 | workhub-server（project/mcp/ops/notification/db schema）；workhub-web（业务线编辑、MCP 资源页、运维监测页） |
| 所属项目或业务线 | 不适用（WorkHub 平台自身功能） |
| 对应 Proposal 版本 | v1 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | server `codex/development-20261102` 工作区；web 工作区；2026-11-03 |

## 1. 现状与证据

- 业务线模型：`BusinessLineEntity` / `BusinessLineSaveRequest` / `BusinessLineResponse`（workhub-model `model/project/`）无环境字段；`BusinessLineMapper` 为注解 SQL，列清单不含环境列。
- 访问配置：`pm_business_line_access_config.environment_code`，`ProjectService.normalizeEnvironmentCode` 统一转小写存储，格式正则 `[a-z0-9][a-z0-9_-]{0,63}`；端点类型 `ACCESS_ENDPOINT_TYPES = OPERATIONS/CLIENT/GATEWAY/OTHER`（网关是端点类型，非独立配置）。
- MCP 资源：`mcp_resource_config.environment_code`，`McpResourceService` 保存时仅非空校验，原样存储。
- 运维域：`ops_monitor_config`、`ops_system_alert_subsystem`、`ops_system_alert_scope`、`ops_system_alert_event`、`ops_account_monitor_config`（默认 'prod'）、`sys_notification.environment_code`；写入点 `OpsMonitorService`、`SystemAlertScopeService`、`SystemAlertService`（人工接入）、`NotificationService`、`SystemAlertCleanupTaskService` 均原样/trim 存储。ELK 告警事件环境编码取自范围行（`ElkSystemAlertCollector` 引用 `scope.getEnvironmentCode()`）。
- 消费匹配：`McpRuntimeService.normalizeText` 统一小写后比较（大小写不敏感）；`BusinessLogSearchService`、`SystemAlertScopeService` 按库中值精确等值匹配。
- 前端：`ProjectListView.vue` 硬编码 dev/test/prod；`McpResourceView.vue` 与 `OpsMonitorView.vue` 硬编码 dev/test/staging/prod（小写，精确匹配显示标签）；两页均已通过 `fetchProjectGroups()`（`GET /projects/business-lines`）加载业务线列表。
- 尚未核实的假设：无关键假设；实施时以各文件现状为准。

## 2. 系统架构

- 总体方案：以业务线为环境口径的唯一配置源。业务线保存 `supported_environments`（CSV 大写编码）；所有模块写库前把环境编码归一化为大写；配置类模块（业务线访问配置、MCP 资源）保存时校验环境必须属于所属业务线支持环境；存量数据一次性迁移为大写并回填支持环境。
- 数据流：业务线编辑（web）→ `BusinessLineSaveRequest.supportedEnvironments` → 归一化/校验 → `pm_business_line.supported_environments` → `BusinessLineResponse.supportedEnvironments` → 各页面环境下拉。
- 前端边界：下拉选项、标签映射、表单联动在前端；编码合法性、归属校验、存储在后端。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| workhub-server project | 业务线支持环境存取、大写归一化、访问配置归属校验、迁移 SQL | 其他模块的页面逻辑 | BusinessLineSaveRequest/Response | pm_business_line、pm_business_line_access_config |
| workhub-server mcp | MCP 资源环境大写归一化与归属校验 | 业务线配置维护 | McpResourceSaveRequest | mcp_resource_config、BusinessLineMapper |
| workhub-server ops/notification | 运维/通知各写入点环境大写归一化 | 归属校验（本期不做） | 各保存请求/事件 | BusinessLineMapper（仅清洗） |
| workhub-web | 支持环境编辑器、环境下拉动态取值、大写标签映射 | 后端校验 | /projects/business-lines | 无新增 |

## 3. 契约与数据模型

- 修改的契约（权威文件：workhub-model 对应类 + workhub-web `types/work-item.ts`）：
  - `BusinessLineSaveRequest` 增加 `List<String> supportedEnvironments`（必填非空，元素大写编码）；`BusinessLineResponse` 增加 `supportedEnvironments`（CSV 还原为有序列表）；`BusinessLineEntity` 增加 `supportedEnvironments`（String CSV）。
  - 访问配置 `BusinessLineAccessConfigRequest.environmentCode`：改为大写归一化（正则放宽为大小写不敏感输入，`[A-Za-z0-9][A-Za-z0-9_-]{0,63}`，存储统一大写），且必须 ∈ 该业务线支持环境。
  - `McpResourceSaveRequest.environmentCode`：大写归一化，必须 ∈ 所属业务线支持环境。
  - `OpsMonitorSaveRequest`、`SystemAlertScopeSaveRequest`、`SystemAlertService` 人工接入、`NotificationService`、`SystemAlertCleanupTaskService`、`AccountingMonitorService` 的环境编码：大写归一化，不做归属校验。
- 错误语义：环境编码为空/格式非法/越界（不在支持环境内）→ `IllegalArgumentException` 中文提示，沿用现有全局异常处理。
- 幂等/并发：业务线保存沿用“整体替换访问配置”的事务语义；环境列表整体替换，无新增并发风险。
- 数据库变更（Flyway：`workhub-bootstrap/src/main/resources/db/schema/mysql/V20261103_140000__business_line_supported_environments_and_uppercase.sql`）：
  1. `ALTER TABLE pm_business_line ADD COLUMN supported_environments VARCHAR(512) NULL AFTER description`；
  2. 存量大写迁移：对 `mcp_resource_config`、`pm_business_line_access_config`、`ops_monitor_config`、`ops_system_alert_subsystem`、`ops_system_alert_scope`、`ops_system_alert_event`、`sys_notification`、`ops_account_monitor_config` 执行 `UPDATE ... SET environment_code = UPPER(TRIM(environment_code))`（唯一键冲突风险：迁移语句按业务线分组核对，实施时先跑冲突检查，冲突行人工裁决后继续）；
  3. 回填：各业务线 `supported_environments` = 该业务线在 `pm_business_line_access_config` 与 `mcp_resource_config` 中 distinct 环境编码的大写 CSV（先访问配置，后 MCP 资源补充，`GROUP_CONCAT` 去重排序）。
  - 可重复性：迁移脚本一次性执行（Flyway 版本化）；回滚 = 还原备份或反向 `LOWER` 更新（仅访问配置表曾被小写归一，其余表原本大小写混存，反向不可精确还原，故执行前备份相关表）。
- 兼容：`McpRuntimeService` 匹配走 `normalizeText`（小写化）天然兼容大写存储；`BusinessLogSearchService`/告警范围等值匹配两侧同步迁移后一致。

## 4. 非功能约束

- 安全与权限：沿用现有接口权限（`project:business-line:*`、`mcp:resource:*` 等），无新增端点。
- 性能：支持环境为单行 CSV，最多数十编码，无性能影响；迁移为一次性全表 `UPDATE`，数据量小。
- 失败处理：唯一键冲突时迁移失败可见（Flyway 报错），不静默跳过。
- 审计：MCP 资源保存已有审计链路，不变更。

## 5. 交付与回滚

- 发布顺序：先部署 workhub-server（含迁移 SQL，向后兼容——旧前端提交小写会被归一化为大写），再部署 workhub-web。
- 灰度/开关：无开关；迁移幂等性依赖 Flyway 版本控制。
- 备份：迁移脚本内先创建备份表（`CREATE TABLE ... AS SELECT` 相关表的 business_line_code/environment_code 列）再更新。
- 回滚条件：迁移后环境匹配异常；回滚步骤 = 还原备份表数据 + 回退代码。`supported_environments` 列保留不影响旧代码。
- 文档更新：`docs/业务线/BL000012-工作台/现状.md` 业务线/环境口径章节、`docs/业务线/BL000012-工作台/迭代日志.md`、本目录 tasks 验证证据。

## 6. 一致性与待确认

- 目标 1（业务线支持环境）→ §3 数据模型与 §2 数据流；目标 2（大写统一）→ §3 写入点清单与迁移；目标 3（MCP 适配）→ §3 契约与 §2 模块职责。
- 非范围保持：支付域、动态化运维/支付页面均未纳入改动。
- 待确认问题：存量数据唯一键冲突的裁决规则（同业务线+环境大小写并存的行合并策略）需在实施时按实际冲突数据确认。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
