# Proposal：系统集成可视化管理

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-49bc7dcd-f4ed-4167-8453-41744a9553eb |
| 需求名称 | 系统集成可视化管理（新增系统集成菜单，可视化管理禅道/Jenkins/GitLab 等接入系统与能力目录） |
| 涉及仓库与功能域 | workhub-server：workhub-model、workhub-dao、workhub-service（system/intake/agent 客户端探针）、workhub-controller、workhub-bootstrap（Flyway 迁移）；workhub-web：菜单、路由、api、types、views/system |
| businessLineCode | BL000012 |
| businessLineName | 工作台 |
| 所属项目或业务线 | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-49bc7dcd-f4ed-4167-8453-41744a9553eb-系统集成可视化管理/ |
| 版本/状态 | v1.0，规格已确认（用户 2026-11-06 批准实施计划） |
| 目标系统 | WorkHub 前后端 |
| 原始需求来源及版本 | 用户口头需求（2026-11-06）："1.新增一个系统集成菜单（系统名，集成方式，认证方式，操作（详情，编辑））2.详情点开后查看集成系统的接入能力（比如禅道的换token，匹配版本，创建测试单，创建任务等，可编辑）就是可视化管理目前系统集成东西" |
| 目标环境/分支 | codex/development-20261102 |
| 负责人/评审人 | sh |

## 1. Why：背景与问题

- 当前问题及证据：
  1. 禅道（`ZentaoClient`，sys_config 组 `zentao.base`/`zentao.mapping`）、Jenkins（`AgentJenkinsClient`，sys_config 组 `agent.jenkins`/`policy` 与 `intake.submitTest`）、GitLab（`AgentGitLabClient`/`GitlabRepositoryService`，sys_config 组 `gitlab.global`/`gitlab.{业务线}`/`agent.gitlab`）已真实集成，但配置散落、无统一可视化入口，接入能力（能做什么、哪些已实现、哪些规划中）只存在于代码与文档中。
  2. 管理员无法在页面上了解"平台接了哪些系统、以什么方式、用什么认证、各自提供什么能力"。
- 受影响用户/角色：平台管理员（无法可视化管理集成台账）、新成员（了解平台集成现状成本高）。
- 不解决的后果：集成知识依赖口口相传；后续新系统接入缺少登记模板。

## 2. What：目标与范围

### 本期目标

1. 新增「系统集成」菜单（系统管理下）：列表展示系统名、集成方式、认证方式、启用状态，操作含详情、编辑、测试连接。
2. 详情弹窗展示并编辑该系统的接入能力清单（能力编码/名称/类型/实现状态/描述/启用）与连接配置状态（各组键数、SECRET 是否已配置，不回传值），提供跳转系统配置页定位。
3. 列表页「测试连接」：禅道换 Token 探活、Jenkins 实例级探针、GitLab `/api/v4/user` 探针；凭据未配置或失败时明确报错。

### 本期交付范围

1. Flyway 迁移：`sys_integration_system`、`sys_integration_capability` 两张表 + 预置禅道/Jenkins/GitLab 及能力目录（幂等）+ 菜单权限 `system:integration:view/create/update` 并授予超管。
2. 后端：实体、Mapper、`IntegrationCatalogService`（CRUD/启停）、`IntegrationProbeService`（按 system_code 分派探针）、`IntegrationController`（/api/system/integrations，GET/POST/PUT/probe）及单测。
3. 前端：菜单/路由/`api/integration.ts`/`types/integration.ts`/`IntegrationView.vue`；`SystemConfigView.vue` 支持 `?configGroup=` 预填筛选。

### 明确不在本期范围

- 不改现有客户端调用逻辑、不迁移 sys_config、不动 Agent 策略（台账为登记性质，不与代码行为联动校验）。
- 不在详情页编辑连接配置（地址/账号/Token 仍走系统配置页维护）。
- 不做系统删除（仅启停）、凭据轮换、MCP/Agent 工具面变化。

## 3. 关键方案决策（用户已确认）

| 决策点 | 选择 | 理由 |
| --- | --- | --- |
| 数据来源 | 注册台账表（迁移预置，页面可新增） | 完全可编辑；新系统页面登记即可 |
| 编辑边界 | 仅元数据+配置状态，凭据跳转系统配置页 | 复用已有加密/脱敏能力，凭据编辑单一入口 |
| 连通性测试 | 列表页「测试连接」 | AI 配置页已有 probe 先例；探针全部只读 |

## 4. 验收标准

1. 超管登录可见「系统管理 → 系统集成」菜单；列表含预置的禅道、Jenkins、GitLab 三行，展示集成方式与认证方式。
2. 详情弹窗可见能力清单（禅道含换Token/匹配版本/创建测试单 ACTIVE、创建任务 PLANNED），可新增、编辑、删除能力行并保存；PLANNED 能力有明显标识。
3. 配置状态区按组展示键数与 SECRET 配置状态，点击跳转系统配置页并预填组名筛选；页面不出现任何明文或脱敏后的凭据值。
4. 测试连接：禅道环境可达时返回成功与账号；凭据缺失/错误时返回明确失败原因（不出现堆栈）；Jenkins/GitLab 同理；未内置探针的系统返回"该系统暂不支持测试连接"。
5. 权限：无 `system:integration:view` 不可见菜单与接口；编辑/新增/测试连接分别受 create/update 权限约束。
6. 单测与验证记录见 [tasks.md](tasks.md)。
