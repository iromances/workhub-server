# Design：需求提测环境必选与 Jenkins 环境放开

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 需求提测环境必选与 Jenkins 环境放开（REQ-db1f9f08-242d-4ba6-8fce-962f4f64aafa） |
| 涉及仓库与功能域 | workhub-server：需求管理提测确认与测试发布校验；workhub-web：需求管理提测弹窗与测试发布面板 |
| 所属项目或业务线 | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 已确认（随 Proposal 一并确认） |
| 当前事实核对环境、分支、时间 | 2026-11-23；server/web 分支 `codex/development-20261122`；绑定配置生产库 sys_config `intake.testDeploy`/`bindings` 当前仅一条 TEST 绑定（scs-saps，jobName `test-scs-saps`，deployTarget `test-scs-server`） |

## 1. 现状与证据

- 提测确认入口：`POST /intake/{id}/submit-test-review/confirm`（`IntakeController.confirmSubmitTest`）→ `IntakeSubmitTestReviewService.confirmAndSubmit`；环境仅在 `triggerJenkins` 为 true 时必填并强制 `TEST`（168-173、205-210 行），回执环境硬编码 `TEST`（229 行）。
- 测试发布绑定校验：`IntakeTestDeploymentService.validateBinding`（219-224 行）要求 `environmentCode == TEST`、jobName 匹配 `test-[A-Za-z0-9_-]+`、deployTarget 以 `test-` 开头；绑定存于 sys_config JSON，由运维登记。
- 前端：`IntakeListView.vue` 提测弹窗无环境控件，`submitTestEnvironmentCode` 静默置 TEST（1686 行）；校验与请求体仅在 Jenkins 开启时带环境（1815、1821 行）；`IntakeTestDeploymentPanel.vue` 选项 label 硬编码 `TEST`（90 行）。
- 业务线环境（生产库 `pm_business_line.supported_environments`）：多数仅 TEST；智能柜 TEST,DEV,SIT；叉车 TEST,SIT；太平洋保险 DEV,TEST；BL000010 未配置。
- 尚未核实的假设：运维后续按 `{env小写}-{system}` 约定登记非 TEST 绑定（本期不新增数据，仅放开校验）。

## 2. 系统架构

- 总体方案：提测确认环境升级为"始终必选的显式选择"，Jenkins 发布绑定从 TEST 单环境泛化为"非生产环境 + 环境前缀约定"，两层通过"所选环境 = 绑定环境"一致性校验衔接。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| IntakeSubmitTestReviewService（server） | 环境必填、非 PROD、业务线支持环境校验；Jenkins 时与绑定环境一致性校验；回执写入所选环境 | 绑定登记、Job 参数核验 | 入参 environmentCode（必填） | BusinessLineMapper（新增构造依赖） |
| IntakeTestDeploymentService（server） | 绑定环境非 PROD、jobName/deployTarget 匹配 `{env小写}-` 前缀 | 环境与需求匹配（由上层校验） | sys_config 绑定 JSON | 无新增 |
| IntakeListView.vue（web） | 环境必选下拉、提交校验、请求体、环境切换联动、Jenkins 开关禁用态、回执指标卡 | 绑定列表来源 | submitTestEnvironmentCode | IntakeTestDeploymentPanel |
| IntakeTestDeploymentPanel.vue（web） | 按环境过滤绑定、label 显示绑定环境、change 附带绑定环境 | 环境合法性（由父组件/后端校验） | 新增可选 prop environmentCode | 测试绑定 API |

### 关键规则

1. 环境选项：业务线 `supported_environments` 逗号分隔、大写化、去 PROD；为空回退 `[TEST]`；默认选 TEST（存在时）否则首项。
2. 绑定校验泛化：`environmentCode` 非空且 ≠ PROD；`jobName` 匹配 `{env小写}-[A-Za-z0-9_-]+`；`deployTarget` 以 `{env小写}-` 开头；serverAddress/publicBranch 校验不变；报错文案"提测只允许明确登记的非生产测试 Job 和测试目标，禁止生产发布"。
3. 一致性：提测确认开启 Jenkins 时，`validateRequest` 返回绑定的 `environmentCode` 必须等于所选环境，否则报"测试环境与所选测试发布目标不符"。
4. 回执环境：`entity.setJenkinsEnvironmentCode(environment)` 无论是否触发 Jenkins 均写入；DB 列名不变，注释语义调整为"提测确认环境"。

## 3. 契约与数据模型

- 接口契约：`POST /intake/{id}/submit-test-review/confirm` 路径与字段不变；`environmentCode` 语义从"triggerJenkins 时必填"变为"始终必填、不允许 PROD"（对旧调用方为收紧，前端同步改造，无兼容调用方）。
- 数据库：无表结构变更；`pm_intake_submit_test_review.jenkins_environment_code` 复用为提测确认环境。
- 配置：sys_config `intake.testDeploy`/`bindings` 结构不变，登记约定从"TEST 专属"扩展为"{env小写}-{system} Job + {env小写}- 前缀 deployTarget"。

## 4. 非功能约束

- 安全与权限：PROD 永久禁止（前后端双重校验）；绑定仍仅操作已登记 Job，Job 参数 `deploy_test_env` 单选核验保留，防止借环境放开误触生产。
- 性能：业务线环境校验为单条 mapper 查询，无影响。
- 失败处理：环境类校验失败抛 `IllegalArgumentException` 中文提示，不推进阶段、不产生外部副作用（沿用既有顺序：先校验后创建禅道测试单）。
- 日志、监控、告警与审计：不涉及新增。

## 5. 交付与回滚

- 影响系统和发布顺序：server 与 web 一起发布（web 依赖后端必填收紧，先发 server 后发 web 亦可，短暂窗口内旧前端不开构建提测会收到缺环境报错，属预期收紧）。
- 灰度或开关：无。
- 回滚条件、步骤与不可逆部分：还原两侧提交即回滚；无数据迁移、无不可逆部分。
- 生效后需更新的文档：本目录三份规格；需求迭代日志（按所属仓库规范）；`docs/业务线/BL000012-工作台/现状.md` 提测能力描述如涉及 TEST 限制需同步。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1 → §2 前端行；目标 2 → §2 服务行与关键规则 1/3/4；目标 3 → §2 关键规则 2。
- 明确非范围是否被保持：不改表结构、不放开 PROD、不改分支命名与 Job 参数核验、不新增绑定数据。
- 待确认问题及影响：无（用户已确认两项规格决策）。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
