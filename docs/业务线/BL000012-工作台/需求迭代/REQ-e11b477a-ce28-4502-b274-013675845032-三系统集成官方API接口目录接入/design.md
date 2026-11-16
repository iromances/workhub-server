# Design：三系统集成官方API接口目录接入

> SDD 流程的 Design 模板。以本次 Proposal 和已核实的目标环境/分支事实为输入，可基于 Proposal 草案连续起草，随完整规格在实施前统一确认。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-e11b477a-ce28-4502-b274-013675845032 |
| 需求名称与唯一需求目录 | 三系统集成官方API接口目录接入；docs/业务线/BL000012-工作台/需求迭代/REQ-e11b477a-ce28-4502-b274-013675845032-三系统集成官方API接口目录接入/ |
| 涉及仓库与功能域 | workhub-server：dao/system、model/system、service/system、controller/system、workhub-bootstrap 迁移；workhub-web：IntegrationView 及 api/types |
| 所属项目或业务线（适用时） | BL000012-工作台 |
| 对应 Proposal 版本 | v1 |
| 状态 | v1 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | workhub-server@codex/development-20261102；官方文档可达性核查于 2026-11-13（本机 curl 实测，抓取样张存需求目录 `验证证据/`） |

## 1. 现状与证据

- 当前流程、模块和代码位置：
  - 集成台账：`sys_integration_system`（ZENTAO/JENKINS/GITLAB 种子）+ `sys_integration_capability`（平台视角能力，页面可编辑）；`IntegrationCatalogService.detail()` 聚合两者；详情弹框在 `IntegrationView.vue`。
  - 平台已实际调用的官方接口（`platformUsed` 标记的事实来源，按调用点代码枚举）：
    - ZENTAO：`POST /api.php/v1/tokens`、`GET/POST /api.php/v1/projects/{id}/builds`、`POST /api.php/v2/testtasks`、`GET /api.php/v1/stories/{id}`、`GET /api.php/v1/executions/{id}`、`GET /api.php/v1/products/{id}/projects`、`GET /api.php/v1/projects/{id}/executions`、`GET /api.php/v1/{products|executions}/{id}/bugs`、`GET /api.php/v1/bugs/{id}`、`GET /api.php/v1/{stories|tasks}/{id}`（ZentaoClient/IntegrationQueryService）。
    - JENKINS：`GET {instance}/api/json`、`GET/POST job/**/api/json`、`POST job/**/buildWithParameters`、`GET queue/item/{id}/api/json`、`GET job/**/{n}/api/json`、`GET job/**/{n}/logText/progressiveText`、`GET job/**/{n}/artifact/**`、`GET queue/api/json`（AgentJenkinsClient）。
    - GITLAB：`GET /api/v4/projects...`、`GET /api/v4/projects/{id}/repository/branches...`、MR 创建/查询、组项目列表等（GitlabRepositoryService、AgentGitLabClient 等，实施时按调用点精确枚举）。
- 官方文档事实（2026-11-13 本机实测，样张 `/tmp/apidocs` → 归档 `验证证据/`）：
  - GitLab：`https://docs.gitlab.com/ee/api/api_resources.html` 可达，索引含 **203 个**资源组文档链接（格式 `href=/api/{resource}/`；其中 `api_resources`、`rest`、`get_started`、`openapi`、`graphql` 为导航/规范页，实际资源组约 198）。每资源页含方法+路径表格，可脚本解析。
  - Jenkins：`https://www.jenkins.io/doc/book/using/remote-access-api/` 可达（24KB，叙述式），官方未提供统一端点枚举清单；核心端点集按该文档与 Jenkins 核心广泛文档化端点（job/queue/computer/view/api-json/build/stop/enable/disable 等）整理，目录 `sourceNote` 注明口径。
  - 禅道：zentao.net 旧 API 手册链接（`/book/api/about-464.html`）已 301 至新手册其他章节；新手册导航为 JS 渲染，REST API 章节入口未在本次核查中定位。权威源候选（T-01 定位，按优先级）：① zentao.net 新手册 REST API 章节（站点内检索/站点 API）；② zentaopms 开源仓库 REST 路由定义（raw.githubusercontent.com 可达，`module/api/control.php` 已核实 200，REST 路由文件路径待定位）；③ 部署实例（10.10.116.28，21.7.8）实际可用性交叉验证。以能逐条核对的源为准，并在目录中记录来源与版本。
- 尚未核实的假设：GitLab 每资源页的表格结构解析成功率（少数页面可能为异构结构，解析失败的资源组降级为"组名+文档链接"仍入目录，`parseStatus` 标注）；禅道权威源最终落点。

## 2. 系统架构

- 总体方案和设计目标：官方接口目录 = 只读参考数据（文档快照），与平台视角"接入能力"并列。数据经一次性生成脚本从官方文档解析产出种子 SQL 迁移入库；后端提供分页查询；前端只读展示。
- 主要流程、数据流和状态流：
  - 生成流（离线）：脚本抓取官方文档 → 解析资源组/接口条目 → 合并 `platformUsed` 标记（按调用点清单） → 产出种子 SQL + 来源清单（含 URL、抓取时间、解析统计） → 归档需求目录。
  - 查询流：前端详情弹框 → `GET /api/system/integrations/{id}/apis`（分页 + group/method/keyword 过滤） → `IntegrationApiCatalogService` → 新表。
- 前端页面、交互与后端规则边界：前端只做过滤与分页展示；数据维护唯一入口是迁移种子（页面与 MCP 均不提供写入）。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| 抓取生成脚本（需求目录归档） | 拉取三系统官方文档、解析接口条目、产出种子 SQL 与来源清单 | 入库执行、定时同步 | 官方文档 HTML → SQL/JSON | 公网可达 |
| `sys_integration_api_endpoint`（新表） | 接口条目结构化存储 | 平台能力语义（capability 不动） | — | Flyway |
| `IntegrationApiCatalogService` + Controller | 分页查询、过滤、平台接入标记输出 | 写入、同步 | REST → 分页响应 | dao/system 新 mapper |
| IntegrationView.vue | 官方接口目录只读区（过滤/分页/标记） | 编辑 | REST | api/types |

## 3. 契约与数据模型

- 新表 `sys_integration_api_endpoint`：
  - `id` PK；`system_code` VARCHAR(64)（ZENTAO/JENKINS/GITLAB，与 `sys_integration_system.system_code` 对齐，逻辑关联不用外键，避免种子对 id 依赖）；`resource_group` VARCHAR(128)（官方资源组/模块名，如 `projects`、`bugs`、`job-build`）；`resource_group_name` VARCHAR(256)（中文/显示名）；`api_name` VARCHAR(512)（官方接口名称/描述短语）；`http_method` VARCHAR(16)（GET/POST/PUT/DELETE/PATCH）；`api_path` VARCHAR(1024)（请求路径模板，含 `{id}` 占位）；`summary` VARCHAR(2048)（官方说明摘录，可空）；`platform_used` TINYINT(1) DEFAULT 0（平台已调用标记）；`source_url` VARCHAR(1024)（来源文档页）；`source_note` VARCHAR(512)（口径备注，如 Jenkins 整理口径、GitLab 解析降级）；`sort_order` INT；`created_at/updated_at`。
  - 索引：`(system_code, resource_group, sort_order)`、`(system_code, http_method)`；不建唯一键（官方文档同一方法+路径可在多资源页出现，唯一性由生成脚本保证不重复）。
- 新接口（页面用，view 权限）：
  - `GET /api/system/integrations/{id}/apis?resourceGroup=&httpMethod=&keyword=&page=1&pageSize=20`（pageSize ≤ 100）→ `{ total, resourceGroups:[{group,name,count,platformUsedCount}], items:[...], sourceSummary:{fetchedAt, sources:[...] } }`；`systemCode` 由 `{id}` 解析，非三系统返回空。
  - 响应不含任何凭据；`api_path` 为官方路径模板。
- 幂等、并发、重试：种子迁移 `INSERT ... WHERE NOT EXISTS` 幂等（键：system_code+http_method+api_path+resource_group）；目录只读无并发写。
- 数据库变更脚本落点：`workhub-bootstrap/.../V20261113_HHMMSS__add_integration_api_catalog.sql`（建表 + 三系统种子；种子由脚本生成后并入该迁移文件）。回滚：DROP 新表（无外部依赖）；能力目录与既有数据不受影响。
- 种子数据量预估：GitLab 约 198 资源组、数千条接口；禅道约 20 组、百余条；Jenkins 约 15-25 组、数十条。迁移文件较大时按系统拆分为三个迁移（GitLab 单独一个）。

## 4. 非功能约束

- 安全与权限：目录为公开文档快照，不含凭据；读取走 `system:integration:view`；无写入口（页面/MCP/REST 均只读）。
- 性能、容量：表预计数千行，分页查询带索引；前端默认 20/页；详情弹框首次加载仅取汇总+首页。
- 失败处理：文档解析失败的单个资源组降级入目录（组名+文档链接，`source_note=解析降级`），不阻塞整体；抓取脚本可断点重跑（按资源组落盘缓存）。
- 日志、监控：无新增（离线脚本输出统计到需求目录产物中）。

## 5. 交付与回滚

- 发布顺序：先迁移（建表+种子）→ 后端 → 前端；前端未发时新接口不影响既有页面。
- 回滚：应用回退即隐藏目录；表保留无害；如需彻底回滚 DROP 新表（种子可再生成）。
- 生效后更新：迭代日志、本需求 tasks 证据；`docs/mcp-server.md` 不涉及。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1 → §2 生成流 + §3 新表/新接口 + 前端展示；目标 2 → `platform_used` 标记与调用点清单（§1）。
- 非范围保持：不开放 MCP 调用、不重构 capability、不做网关（§2 边界）。
- 待确认问题及影响：① 导入范围为全量（用户原话"整套"，规格确认时可改核心集）；② 目录为只读快照（不提供页面编辑）。两项均已在 Proposal/Design 中写明，随规格统一确认。
- 契约、ADR 及 Tasks 的关联链接：契约见 §3；无 ADR；Tasks 同目录 tasks.md。
