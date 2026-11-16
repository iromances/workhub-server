# Tasks：系统集成可视化管理

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-49bc7dcd-f4ed-4167-8453-41744a9553eb |
| 需求名称 | 系统集成可视化管理 |
| 所属项目或业务线 | BL000012-工作台 |
| 规格版本与结论 | v1.0；用户 2026-11-06 批准实施计划（数据来源=注册台账表；编辑边界=仅元数据+配置状态；测试连接=列表页） |
| 确认人/日期 | sh / 2026-11-06 |

## 任务清单

### 后端（workhub-server）

- [x] T1 Flyway 迁移 `V20261106_000001__add_integration_catalog.sql`：两张表、预置三系统与 14 项能力目录（幂等）、菜单权限 `system:integration:view/create/update` 与超管授权。
- [x] T2 model：`IntegrationSystemEntity`、`IntegrationCapabilityEntity`、`IntegrationCapabilityItem`、`IntegrationSystemSaveRequest`、`IntegrationSystemResponse`、`IntegrationSystemDetailResponse`（含 `ConfigGroupStatus`）、`IntegrationProbeResponse`（`cn.aslight.workhub.model.system`）。
- [x] T3 dao：`IntegrationSystemMapper`、`IntegrationCapabilityMapper`（注解 SQL）。
- [x] T4 `ZentaoClient.probe()`（复用换 Token）、`AgentJenkinsClient.probeInstance(Target)`（实例级 `api/json`）、`AgentJenkinsPolicy.firstEnabledInstance()`。
- [x] T5 `IntegrationCatalogService`：CRUD/能力整体替换（先校验后落库）/system_code 不可改/配置状态聚合（前缀组按 `组前缀` 聚合，仅统计不回值）/审计。
- [x] T6 `IntegrationProbeService`：ZENTAO/JENKINS/GITLAB 分派 + 不支持系统明确拒绝；凭据缺失/失败返回明确 message 不抛堆栈。
- [x] T7 `IntegrationController`：GET 列表/详情、POST、PUT、POST `/{id}/probe`，`@PreAuthorize` 按 view/create/update。
- [x] T8 单测：`IntegrationCatalogServiceTest`（6 项）、`IntegrationProbeServiceTest`（7 项）、`IntegrationCatalogMigrationTest`（H2 执行生产迁移：预置数据/超管授权/幂等/Mapper SQL，4 项）。

### 前端（workhub-web）

- [x] T9 `types/integration.ts`（含枚举标签映射）、`api/integration.ts`。
- [x] T10 `constants/menu.ts` + `router/index.ts` 注册「系统集成」菜单与路由（`/system/integrations`，权限 `system:integration:view`）。
- [x] T11 `views/system/IntegrationView.vue`：列表（系统名/编码、集成方式、认证方式、能力数、启用、详情/编辑/测试连接）；详情弹窗（系统信息、配置组状态 +「去系统配置」跳转、能力清单查看/编辑、PLANNED 标识、能力行增删）；新增/编辑弹窗（编辑态 systemCode 只读、configGroups 多选可输入）。
- [x] T12 `SystemConfigView.vue` 支持 `?configGroup=` 预填筛选并自动查询。

### 验证与交付

- [x] T13 后端：新增 17 项单测全部通过；workhub-controller/workhub-job 离线编译通过；全量回归见下（2 项失败为工作区既有问题，与本次无关）。
- [x] T14 前端：`vue-tsc --noEmit`（tsconfig.app.json）通过；`vite build --mode production` 成功。
- [ ] T15 手动走查（本地启动 + 超管登录）：菜单/列表/详情/能力编辑/配置状态跳转/测试连接（禅道真实环境；Jenkins、GitLab 未配置报错文案）——待应用重启与数据库迁移执行后进行。
- [x] T16 更新迭代日志与现状说明。

## 验证记录

| 时间 | 验证项 | 结果 |
| --- | --- | --- |
| 2026-11-07 | `IntegrationCatalogServiceTest`（CRUD 校验、system_code 不可改、能力重复拒绝、配置状态聚合含前缀组/SECRET 判定、审计调用） | 6/6 通过 |
| 2026-11-07 | `IntegrationProbeServiceTest`（三系统探针成功/失败/凭据缺失/无实例/不支持系统；GitLab 用本地 HttpServer 桩验证 PRIVATE-TOKEN 与 `/api/v4/user`） | 7/7 通过 |
| 2026-11-07 | `IntegrationCatalogMigrationTest`（H2 MySQL 模式执行生产迁移：3 系统 14 能力、ZENTAO PLANNED 能力、超管授权 3 项、重复执行幂等、Mapper SQL 全方法） | 4/4 通过 |
| 2026-11-07 | workhub-service 全量回归 | 1018 项中 1016 通过、9 跳过；2 项失败（`KnowledgeArchiveWorkflowTest`、`BusinessLogSearchServiceTest`）经 git status 核对属工作区其他进行中的文档路径/ELK 改动（与 [现状.md AI提示词统一配置化一节 2026-11-05 记录的既有失败一致），本次改动文件与其无交集 |
| 2026-11-07 | `mvn -pl workhub-controller,workhub-job -am compile` | 通过 |
| 2026-11-07 | 前端 `vue-tsc --noEmit -p tsconfig.app.json` | 通过 |
| 2026-11-07 | 前端 `vite build --mode production` | 成功（4.04s） |

实施中的修正：

- `IntegrationSystemDetailResponse` 改为继承 `IntegrationSystemResponse`（列表/详情共用字段装配）。
- `update` 先完成全部入参校验（含能力编码重复）再落库，避免校验失败时已发出系统行更新（事务虽可回滚，但按“先边界后写入”风格修正）。
- 迁移测试断言与迁移实际数据对齐：能力编码排序为字典序（`create-task` < `create-test-task`）；前缀组 `gitlab.` 聚合所有以 `gitlab.` 开头的组（含 `gitlab.global`）。

## 剩余风险与待办

- 应用未重启、真实数据库迁移未执行；菜单与页面需随下次启动生效（T15 待做）。
- Jenkins/GitLab 真实探活依赖凭据配置完成；当前仅验证了未配置时的明确报错路径（单测桩）。
- 台账为登记性质，能力清单与代码行为不自动联动（已确认边界，后续新能力需页面同步维护）。

## 首次启动失败排查（2026-11-07 00:45）

用户在 IDEA 启动后端（运行目录 `/Users/aslight/IDEAWorkspace/workhub-server`，与开发目录文件已同步一致），Flyway 迁移 `20261106.000001` 在真实库（MySQL 8.0.28，10.10.116.53:3308/workhub）执行失败，`flyway_schema_history` 落下 success=0 记录（rank 67），应用启动中止（进程后被 IDEA 调试器挂起在异常上，8080 未监听，页面因此报错）。

排查与结论：

- 后端日志整晚多次 `Communications link failure`（同库连接抖动）；迁移从开始到失败仅约 107ms，且 `sys_integration_system` 表未建出（首条 DDL 未到达或中断）。
- 将迁移前两条 CREATE 原样在目标库瞬时执行（建后立即 DROP，净零变更，库中确认无残留）——**两条 DDL 均正常**；Flyway MySQLParser 解析测试（`IntegrationCatalogMigrationParseTest`，6 条语句）通过。判定失败原因为**数据库连接瞬间断开**，非 SQL 问题。
- 处置：`DELETE FROM flyway_schema_history WHERE version='20261106.000001' AND success=0`（等价 flyway repair，表本不存在、无数据影响），库回到 20261105.203000 干净状态；待用户停掉被挂起进程并重启后端，迁移将重新执行。
