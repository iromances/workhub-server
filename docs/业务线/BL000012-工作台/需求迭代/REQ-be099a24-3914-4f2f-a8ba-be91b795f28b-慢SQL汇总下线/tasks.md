# Tasks：慢 SQL 汇总下线

## 1. 元信息
- 需求编码：REQ-be099a24-3914-4f2f-a8ba-be91b795f28b
- 唯一需求目录：docs/业务线/BL000012-工作台/需求迭代/REQ-be099a24-3914-4f2f-a8ba-be91b795f28b-慢SQL汇总下线/
- 目标分支：workhub-server codex/development-20261102；workhub-web codex/development-20261103
- 规格确认：用户确认删除范围为"前后端整体移除"（2026-11-18）；完整规格（Proposal/Design v1）用户确认实施（2026-11-18）；偏差修正见第 5 节（2026-11-18）

## 2. 任务与状态

| 任务 | 交付物 | 状态 | 证据 |
| --- | --- | --- | --- |
| T-01 | 后端：`SystemAlertSubsystemSummaryResponse` 删除、`SystemAlertDashboardResponse` 去 summaries | 已完成 | 文件已删除；record 仅余 totalCount/page/pageSize/subsystems/events/executionEvidence |
| T-02 | 后端：`SystemAlertService.dashboard` 去 `summarizeEvents` 调用 | 已完成 | SystemAlertService.java；另同步调整 SystemAlertVerificationQueryService 透传构造 |
| T-03 | 后端：`SystemAlertMapper.summarizeEvents` 删除 | 已完成 | SystemAlertMapper.java（含 import 清理） |
| T-04 | 后端测试调整 | 已完成 | SystemAlertServiceTest、SystemAlertControllerTest、AgentVerificationServiceTest（构造签名） |
| T-05 | 前端：`SystemAlertView.vue` 汇总区块/serviceOptions/dashboard 初始值/死样式 | 已完成 | 含 `.summary-table` 样式清理 |
| T-06 | 前端：`types/ops.ts` 类型清理 | 已完成 | 删 `SystemAlertSubsystemSummary` 与 `summaries` 字段 |
| T-07 | 后端编译 + 相关单测 | 已完成 | `mvn test-compile` 全模块通过；定向测试 96 项 0 失败（SystemAlertServiceTest 10、SystemAlertVerificationQueryServiceTest 16、AgentVerificationServiceTest 65、SystemAlertControllerTest 5） |
| T-08 | 前端构建 | 已完成 | `npm run build` 通过（5.18s，SystemAlertView 正常产出） |
| T-09 | 残留引用核对 + 文档更新 | 已完成 | 两仓 grep 无 `summaries`/`SystemAlertSubsystemSummary*`/`summarizeEvents` 残留（附件摘要等无关同名除外）；迭代日志已记录 |
| T-10 | 偏差修正：恢复"最近系统异常事件"页签删除功能（见第 5 节） | 已完成 | `SystemAlertView.vue` 三处 v-if 恢复；`npm run build` 通过（3.73s，含 vue-tsc） |

## 3. 计划验证方式与真实结果
- 计划：workhub-service/controller 相关测试通过；`mvn compile` 全链通过；前端 `npm run build` 通过；grep 两仓无 `summaries`/`SystemAlertSubsystemSummary`/`summarizeEvents` 残留。
- 真实结果：全部达成。`mvn test-compile` 全模块通过；定向测试 96/96 通过；前端构建通过；两仓 grep 核对无残留（workhub-server 仅存无关的附件摘要 `IntakeAttachmentSummary` 等同名局部变量）。页签页面行为（AC-01 页面无汇总区块）以代码核对与构建为准，未做浏览器实测。

## 4. 覆盖与评审
- AC-01→T-05/T-08；AC-02→T-01/T-02/T-03/T-04/T-07；AC-03→T-07/T-08。
- 关联：Proposal/Design v1（本目录）。

## 5. 偏差修正记录（2026-11-18）

- 触发：用户报告"最近系统异常事件"页签看不到删除、删除&过滤功能。
- 诊断：T-05 实施时移除嵌套子页签（最近系统异常事件/系统异常汇总）后，三处删除入口的 `v-if` 从 `slowSql || systemError(events)` 误收敛为仅 `slowSql`，行内精确删除列同步被限定，超出 Proposal"明确不做"第 1 条（不删删除/过滤任务等功能）边界。权限判断（`canDelete`/`canDeleteAndFilter`）与后端无问题、无改动。
- 修正（T-10，workhub-web `SystemAlertView.vue`）：工具栏「删除」恢复为 `v-if="canDelete"`、「删除&过滤」恢复为 `v-if="canDeleteAndFilter"`（筛选表单仅在 systemError/slowSql 两页签渲染，语义与修复前一致）；行内精确删除列恢复为 `v-if="canDelete"`。同批次夹带的刷新按钮移除、时间筛选拆分等界面调整未回退（待用户确认归属）。
- 验证：`npm run build` 通过（3.73s，含 vue-tsc 类型检查）。页签可见性以代码核对为准，未做浏览器实测。
