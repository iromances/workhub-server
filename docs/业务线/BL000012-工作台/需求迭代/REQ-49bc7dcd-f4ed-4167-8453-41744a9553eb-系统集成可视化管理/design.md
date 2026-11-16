# Design：系统集成可视化管理

## 1. 数据模型

### 1.1 `sys_integration_system`（集成系统台账）

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| id | BIGINT AUTO_INCREMENT | 主键 |
| system_code | VARCHAR(64) | 唯一；大写字母数字下划线；内置 ZENTAO/JENKINS/GITLAB 用于探针分派，创建后不可修改 |
| system_name | VARCHAR(128) | 非空 |
| integration_method | VARCHAR(32) | 枚举：REST_API / REST_AND_LOCAL_GIT |
| auth_type | VARCHAR(32) | 枚举：ACCOUNT_PASSWORD / BASIC_API_TOKEN / PAT |
| config_groups | VARCHAR(512) | CSV，关联 sys_config_item.config_group（支持 `gitlab.` 前缀组，详情按前缀聚合统计） |
| enabled | TINYINT(1) | 默认 1 |
| sort_order | INT | 默认 0 |
| remark | VARCHAR(512) | |
| created_at / updated_at / created_by / updated_by | | 审计字段 |

### 1.2 `sys_integration_capability`（接入能力目录）

| 列 | 类型 | 约束/说明 |
| --- | --- | --- |
| id | BIGINT AUTO_INCREMENT | 主键 |
| system_id | BIGINT | FK → sys_integration_system.id，ON DELETE CASCADE |
| capability_code | VARCHAR(64) | 系统内唯一（uk_system_capability） |
| capability_name | VARCHAR(128) | 非空 |
| capability_type | VARCHAR(16) | 枚举：READ / WRITE |
| impl_status | VARCHAR(16) | 枚举：ACTIVE / PLANNED |
| description | VARCHAR(512) | |
| enabled | TINYINT(1) | 默认 1 |
| sort_order | INT | 默认 0 |
| remark | VARCHAR(512) | |
| created_at / updated_at | | |

能力清单随系统保存整体替换（删旧插新），不做能力级局部更新。

### 1.3 预置数据（幂等 INSERT ... SELECT WHERE NOT EXISTS）

| system_code | 名称 | 集成方式 | 认证方式 | config_groups | 能力 |
| --- | --- | --- | --- | --- | --- |
| ZENTAO | 禅道 | REST_API | ACCOUNT_PASSWORD | zentao.base,zentao.mapping | 换Token（READ/ACTIVE）、匹配版本-查build（READ/ACTIVE）、创建测试单（WRITE/ACTIVE）、创建任务（WRITE/PLANNED） |
| JENKINS | Jenkins | REST_API | BASIC_API_TOKEN | agent.jenkins,intake.submitTest | 只读探针（READ/ACTIVE）、触发构建（WRITE/ACTIVE）、队列/构建状态查询（READ/ACTIVE）、构建回执查询（READ/ACTIVE）、丢失响应查找（READ/ACTIVE） |
| GITLAB | GitLab | REST_AND_LOCAL_GIT | PAT | gitlab.global,gitlab.,agent.gitlab | 列组仓库（READ/ACTIVE）、仓库同步fetch/clone（READ/ACTIVE）、创建候选分支（WRITE/ACTIVE）、创建Draft MR（WRITE/ACTIVE）、查/合并MR（READ/ACTIVE） |

### 1.4 菜单权限（同一迁移）

`sys_permission`：MENU `system:integration:view`（parent=`system:view`，route=`/system/integrations`，sort 56，名称"系统集成"）；BUTTON `system:integration:create`、`system:integration:update`；按 V16 模式授予超管角色（通过 `sys_role_permission` 对已拥有 `system:ai-config:view` 的角色同步授予，与现有系统管理菜单权限保持一致口径）。

## 2. 接口契约（`/api/system/integrations`，包装 ApiResponse）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/system/integrations` | view | 列表（含能力数量），按 sort_order、id 排序 |
| GET | `/api/system/integrations/{id}` | view | 详情：系统 + 能力清单 + configGroups 配置状态 |
| POST | `/api/system/integrations` | create | 新增系统（含初始能力清单） |
| PUT | `/api/system/integrations/{id}` | update | 编辑系统元数据与能力清单（system_code 不可改） |
| POST | `/api/system/integrations/{id}/probe` | update | 测试连接（探针只读） |

请求/响应模型（workhub-model `cn.aslight.workhub.model.system`）：

- `IntegrationSystemResponse`：id、systemCode、systemName、integrationMethod、authType、configGroups（List<String>）、enabled、sortOrder、remark、capabilityCount。
- `IntegrationSystemDetailResponse`：同上 + `capabilities: List<IntegrationCapabilityResponse>`（id、capabilityCode、capabilityName、capabilityType、implStatus、description、enabled、sortOrder、remark）+ `configGroupStatuses: List<ConfigGroupStatus>`（group、itemCount、enabledCount、secretConfigured、prefixGroup）。
- `IntegrationSystemSaveRequest`：systemCode（创建必填、更新忽略）、systemName、integrationMethod、authType、configGroups（List<String>）、enabled、sortOrder、remark、capabilities（List<IntegrationCapabilityItem>：capabilityCode、capabilityName、capabilityType、implStatus、description、enabled、sortOrder、remark）。
- `IntegrationProbeResponse`：ok、message、elapsedMs、probedAt。

校验：systemCode 匹配 `[A-Z][A-Z0-9_]{1,63}`；integration_method/auth_type/capability_type/impl_status 枚举校验；能力编码 `[a-z][a-z0-9.-]{1,63}`；能力编码系统内不重复；configGroups 每项长度 ≤128 且格式 `^[a-zA-Z0-9.-]+$`（含尾部点前缀组）。

## 3. 探针设计（`IntegrationProbeService`，按 system_code 分派）

全部只读、单次调用、不落凭据；返回 ok=false + 明确 message 而非异常堆栈：

- **ZENTAO** → `ZentaoClient.probe()`（新增公开方法）：读 `zentao.base` baseUrl/account/password（缺失即报"禅道未配置（zentao.base…）"），POST `/api.php/v1/tokens`，成功返回账号与耗时；复用现有 `exchangeToken` 私有逻辑。
- **JENKINS** → 读 `agent.jenkins`/`policy`（`AgentJenkinsPolicy` 新增 `firstEnabledInstance()`）：无策略或无启用实例时明确报错；有实例则 `AgentJenkinsClient.probeInstance(Target)`（新增公开方法，GET 实例级 `api/json`，jobSegments 用 `["validate"]` 占位仅过构造校验，不请求该 Job），返回实例 version；凭据经 `requireSecretValue` 读取，凭据缺失明确报错。
- **GITLAB** → `IntegrationProbeService` 内轻量探针：读 `gitlab.global` webApiUrl/accessToken，GET `/api/v4/user`（PRIVATE-TOKEN），成功返回用户名。
- **其他 system_code** → ok=false，message="该系统暂不支持测试连接"。

## 4. 服务与分层

- `IntegrationCatalogService`（workhub-service `service/system`）：list/detail/create/update；update 事务内更新系统行 + 能力清单整体替换；detail 组装配置状态（`SysConfigMapper.findAll(group, null)` 按组/前缀统计，`valueType=SECRET` 且 encrypted_value 非空视为已配置）；不回传任何配置值。
- `IntegrationController`（workhub-controller `controller/system`）：照 `SysConfigController` 模式（构造注入、`@PreAuthorize`、ApiResponse 包装），create/update 记录 operator。
- 审计：沿用 `SystemAuditService` 现有模式（如 SysConfigService.create(request, operator, ip) 的用法），在 create/update 落审计日志。

## 5. 前端设计（workhub-web）

- `src/constants/menu.ts`：system 组追加 `{ key: 'system-integrations', label: '系统集成', path: '/system/integrations', permission: 'system:integration:view' }`。
- `src/router/index.ts`：system 子路由 `system/integrations` → `IntegrationView.vue`，meta 照 `system/ai-config`。
- `src/api/integration.ts`：fetchIntegrationSystems/fetchIntegrationSystem/saveIntegrationSystem/createIntegrationSystem/probeIntegration。
- `src/types/integration.ts`：类型定义。
- `src/views/system/IntegrationView.vue`：
  - 列表：系统名/编码、集成方式（标签）、认证方式（标签）、能力数、启用、操作（详情、编辑、测试连接）；测试连接按钮带 loading，结果 ElMessage 成功/失败。
  - 详情弹窗：只读系统信息 + 配置状态表（组名/键数/启用数/SECRET 状态 + 「去系统配置」按钮 → `router.push('/system-config?configGroup=xxx')`）+ 能力表格（行内编辑或弹窗编辑、支持增删，PLANNED 用 warning 标签）。
  - 新增/编辑弹窗：元数据表单（下拉枚举），编辑态 system_code 只读。
  - 权限：`authStore.hasAnyPermission(['system:integration:create','system:integration:manage'])` 等（照 SystemConfigView）。
- `SystemConfigView.vue`：onMounted 读 `route.query.configGroup` 预填 `filters.configGroup` 并触发加载。

## 6. 验证与回滚

- 单测：`IntegrationCatalogServiceTest`（CRUD/校验/配置状态聚合/mock mapper）、`IntegrationProbeServiceTest`（三分派 + 凭据缺失/失败/不支持分支，HttpClient 行为经可注入客户端桩模拟或对真实客户端方法打桩）、控制器权限测试照现有模式。
- 迁移：随 Flyway 在 H2 MySQL 模式测试验证（照现有迁移测试基线）。
- 前端：vue-tsc 类型检查 + test 模式构建。
- 手动：本地启动 + 超管登录走查菜单/列表/详情/编辑/测试连接（禅道真实环境；Jenkins/GitLab 验证未配置报错文案）。
- 回滚：页面与接口为新增，不影响既有链路；迁移仅新增表与权限行，回滚删除菜单权限授权与两张表即可，无数据迁移风险。
