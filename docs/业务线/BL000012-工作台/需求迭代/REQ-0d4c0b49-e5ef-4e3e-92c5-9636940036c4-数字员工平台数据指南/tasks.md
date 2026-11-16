# Tasks：数字员工平台数据指南

## 1. 元信息

- 需求编码：REQ-0d4c0b49-e5ef-4e3e-92c5-9636940036c4；目录同名唯一。
- 涉及仓库与功能域：workhub-server（service/ai/迁移/测试）；运维 SQL（助手与任务文案净化）。
- Proposal/Design 版本：v1；状态：已确认（2026-11-12 用户确认立项）。
- 负责人/评审人：用户。

## 2. 任务总览

| 任务 | 交付物 | 状态 |
| --- | --- | --- |
| T-01 | SDD 建档 | 已完成 |
| T-02 | `AgentPlatformGuide` + 适配器注入 + `AiUseCaseDefinitions` 白名单 | 已完成 |
| T-03 | 迁移 `V20261112_120000`（模板 v3 + sys_config 种子）+ `TestPromptTemplates` 追加 | 已完成 |
| T-04 | 测试：指南注入/兜底断言、模板一致性、全量 mvn test | 已完成（本需求范围 0 失败） |
| T-05 | 部署重启 + 存量文案净化 + 端到端巡检验证 | 已完成（净化后一次性巡检 COMPLETED，见验证记录） |

## 3. 验证记录（2026-11-12）

- `AgentWorkbenchServiceTest` 63 项通过（新增 `platformGuideInjectedIntoBothChannelPrompts`：配置文本注入与未配置兜底）。
- 全量 `mvn test` 本需求范围 0 失败（工作区既有 2 项无关失败不变）。
- 实机：重启后迁移应用（模板 v3、sys_config 种子），净化助手职责与任务文案后触发一次性巡检 COMPLETED——AI 依指南正确定位运行库/表并出具报告。
