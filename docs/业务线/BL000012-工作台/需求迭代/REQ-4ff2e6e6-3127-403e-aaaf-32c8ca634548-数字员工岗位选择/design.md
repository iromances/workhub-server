# Design：数字员工岗位选择

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-4ff2e6e6-3127-403e-aaaf-32c8ca634548 |
| 需求名称与唯一需求目录 | 数字员工岗位选择；docs/业务线/BL000012-工作台/需求迭代/REQ-4ff2e6e6-3127-403e-aaaf-32c8ca634548-数字员工岗位选择/ |
| 涉及仓库与功能域 | workhub-server：AgentConfiguration、AgentWorkbenchService、AgentOptionsService、AgentInstructionsDraftService/Request、AiUseCaseDefinitions、迁移；workhub-web：AgentConfigDialog、types |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | 草案 v1 |
| 状态 | 草案，待完整规格统一确认 |
| 当前事实核对环境、分支、时间 | workhub-server codex/development-20261102；workhub-web codex/development-20261103；2026-11-16 |

## 1. 现状与证据

- 字典体系：`dict` 表（V1/V20 定义，`dict_type/enum_code/enum_desc/sort_order`，种子用 `ON DUPLICATE KEY UPDATE` 模式）+ `DictService/DictMapper.query`（按 dictType/enumCodeList 过滤）。
- Agent 配置：`AgentConfiguration` record 序列化存 `pm_agent.configuration_json`，无 DDL；`AgentSaveRequest` 经 controller `@Valid` 校验。
- 选项通道：`AgentOptionsService.options` 组装 providers+scopes 返回 `AgentOptionsResponse`。
- 起草链路：`agent.instructions.draft` 场景变量 agentName/scopeSummary/conversation（REQ-3e8d95c2）；模板升级须新建迁移并登记 `TestPromptTemplates.MIGRATION_FILES`（后文件覆盖前文件）。
- `AgentConfiguration` 构造调用点：main 1 处（AgentWorkbenchService）+ 测试 13 个文件（加 position 参数统一调整）。
- 尚未核实的假设：无。

## 2. 系统架构

- 岗位为 Agent 配置的必填画像字段：存 configuration_json；保存时服务端校验"非空 + 在启用字典内"；下拉数据经 options 接口下发；起草时把岗位中文名传入提示词变量 positionName。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| 迁移 | 字典 agentPosition 种子（IT+全栈+公司常见）；起草模板 v2 | 岗位管理页 | SQL | Flyway |
| AgentConfiguration | position 字段（@NotBlank @Size(64)） | 字典校验 | record | bean validation |
| AgentWorkbenchService | 保存时岗位字典校验 | 运行时注入 | create/update | DictService |
| AgentOptionsService | positions 清单下发 | 维护字典 | options | DictService |
| 起草链路 | positionName 变量与模板段 | 岗位语义理解 | 请求变量 | 网关 |
| AgentConfigDialog | 必填下拉+模糊匹配+起草联动 | 字典维护 | 表单 | options |

## 3. 契约与数据模型

- `AgentConfiguration`：新增 `@NotBlank @Size(max = 64) String position`（置于 name 之后），存岗位字典 `enum_code`。
- `AgentOptionsResponse`：新增 `List<Position> positions`，`Position(String code, String name)`，按 sort_order。
- `AgentInstructionsDraftRequest`：新增 `@Size(max = 128) String positionName`（可空，前端传岗位中文名）；场景变量白名单增 `positionName`；模板 v2 增"数字员工岗位：${positionName}"，缺省渲染"未设置"。
- 字典种子 `agentPosition`（sort 分段）：IT：FULL_STACK 全栈开发/BACKEND 后端开发/FRONTEND 前端开发/MOBILE 移动开发/TEST 测试/OPS 运维/DBA 数据库管理员/SECURITY 安全/DATA 数据-BI/ARCHITECT 架构/PM 项目管理/SUPPORT 技术支持（10–120）；公司常见：PRODUCT 产品经理/OPERATION 运营/FINANCE 财务/RISK 风控/LEGAL 法务/CUSTOMER_SERVICE 客服/HR 人事/ADMIN 行政/SALES 销售/MARKETING 市场（200–290）。
- 校验语义：position 为空 → "请选择岗位"；不在启用字典 → "岗位不存在或已停用，请重新选择"。存量配置读取不校验。
- 幂等/兼容：configuration_json 增字段向后兼容（旧 JSON 无 position 反序列化为 null，仅保存时拦截）；字典种子 ON DUPLICATE KEY 可重复执行。

## 4. 非功能约束

- 安全与权限：岗位校验在 agent:manage 保存路径；字典只读下发。
- 性能：options 增一次字典查询（内存小表，无影响）。

## 5. 交付与回滚

- 发布顺序：后端（含迁移）先发、前端随后；仅前端先发会被必填校验拦（岗位下拉无数据）。
- 回滚：还原两端提交；字典种子与模板 UPDATE 幂等可重放，岗位字段旧版本忽略（Jackson 未知字段容忍由既有配置保证；反之旧 JSON 新代码容忍 null）。
- 生效后更新：迭代日志；现状.md 岗位小节一句。

## 6. 一致性与待确认

- Proposal 目标承接：目标 1 → 字典+options+对话框；目标 2 → 起草变量与模板 v2；目标 3 → 保存/回显。
- 非范围保持：不做岗位管理页、不动执行模板、不改列表展示。
- 待确认问题：无（岗位清单按 §3 种子，后续可经字典 SQL 维护）。
