# Design：慢 SQL 汇总下线

> 承接 REQ-be099a24 Proposal。纯移除型变更，无新增契约。

## 1. 契约变更

- `GET /api/ops/system-alerts`（SystemAlertController.dashboard）响应体 `SystemAlertDashboardResponse`：
  - 删除字段 `summaries`（`List<SystemAlertSubsystemSummaryResponse>`）。
  - 其余字段（totalCount、page、pageSize、subsystems、events、executionEvidence）语义不变。
  - 便捷构造器同步去掉 summaries 参数（仅 Service 内部使用，无其他调用方）。
- 删除模型：`cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse`（全仓仅 dashboard 链路使用）。

## 2. 后端分层改动

- DAO：`SystemAlertMapper.summarizeEvents`（注解 @Select 聚合查询，GROUP BY 业务线/环境/子系统/服务，LIMIT 50）整体删除。
- Service：`SystemAlertService.dashboard` 删除 `summarizeEvents` 调用与 `summaries` 局部变量，响应构造改为不传 summaries。
- Controller：无代码改动（透传 Service 返回）。

## 3. 前端改动

- `SystemAlertView.vue`：
  - 删除 `activeTab === 'slowSql'` 分支下的"慢 SQL 汇总" section-title + `el-table`（`:data="dashboard.summaries"`）外层 `<template>`。
  - `serviceOptions` 移除 `dashboard.value.summaries` 合并来源，仅保留 subsystems 与 scopes.services。
  - `dashboard` ref 初始值删除 `summaries: []`。
- `types/ops.ts`：删除 `SystemAlertSubsystemSummary` 接口与 `SystemAlertDashboard.summaries` 字段。

## 4. 行为影响与回滚

- 行为影响：每次 dashboard 调用少一次事件表聚合查询；服务下拉不再包含"仅出现在事件表中"的服务；页签其余功能不变。
- 回滚：revert 两个仓库对应提交即可，无数据变更、无接口新增。

## 5. 验证

- 后端：`SystemAlertServiceTest`（fake mapper 去除 summaries）、`SystemAlertControllerTest`（响应断言去除 summaries）调整后通过；模块编译通过。
- 前端：`npm run build`（含 vue-tsc 类型检查）通过。
- 核对 `SystemAlertSubsystemSummary`/`summaries` 在两仓无残留引用。
