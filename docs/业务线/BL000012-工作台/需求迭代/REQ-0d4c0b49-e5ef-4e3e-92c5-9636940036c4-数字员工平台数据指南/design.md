# Design：数字员工平台数据指南

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-0d4c0b49-e5ef-4e3e-92c5-9636940036c4 |
| 状态 | 已确认（与 Proposal 同时确认） |

## 契约

- 新组件 `AgentPlatformGuide`（workhub-service）：`String guide(AgentToolContext)`——读 `SysConfigService.findPlainValue("agent.runtime", "dataGuide")`；未配置返回固定发现指引。两个执行适配器在组装提示词时注入 `platformGuide` 变量（紧邻 dataWindow）。
- `AiUseCaseDefinitions`：`AGENT_API_STEP_VARIABLES`/`AGENT_CLI_STEP_VARIABLES` 增加 `platformGuide`。
- 迁移 `V20261112_120000__agent_platform_guide.sql`（**新迁移**：V20261110_130000 已在运行库应用， checksum 不可变）：
  1. 两条 Agent 模板 UPDATE 至 v3：新增"平台数据指南：${platformGuide}"段与规则行（职责/任务不携带库表坐标；CLI 渠道指南未覆盖处用 list_mcp_targets/get_business_line_context 发现，API 渠道声明数据来源受限）。
  2. 种子 `sys_config_item`：group `agent.runtime`、key `dataGuide`、TEXT、enabled，内容为当前环境目录（工作台运行库目标及 ops_*/pm_agent_* 表语义、业务实况库目标与 XXL schema、使用规则）。
- `TestPromptTemplates.MIGRATION_FILES` 追加新迁移文件（后迁移覆盖）。

## 数据与回滚

- 无表结构变更；回滚应用即可恢复 v2 模板读取（`ai_use_case_config` 行保留 v3 需手工回退或忽略——变量注入后渲染兼容旧模板缺占位？不兼容：回滚须同时回退模板行）。
- 存量净化（运维 SQL）：巡检助手 `instructions` 与任务 `bl000001-prod-monitor-patrol` 的 `context` 删除目标/表名，保留业务语义；审计 UPDATE_SPEC/UPDATE。

## 验证

- `AgentWorkbenchServiceTest`：注入指南后提示词含"平台数据指南"与配置文本；未配置时含兜底指引。
- `PromptTemplateMigrationConsistencyTest`：v3 占位与白名单一致。
- 端到端：重启后触发一次巡检，验证净化文案 + 指南导航下仍成功取数出报告。

关联：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
