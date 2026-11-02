# Tasks：需求详情类型修改与实时保存

## 1. 元信息

- 需求编码：REQ-0655b868-e6da-48ae-8751-0a21725b820d
- 目标系统：WorkHub Server / WorkHub Web
- Proposal / Design：v1，见 [proposal.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-0655b868-e6da-48ae-8751-0a21725b820d-需求详情类型修改与实时保存/proposal.md>)、[design.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-0655b868-e6da-48ae-8751-0a21725b820d-需求详情类型修改与实时保存/design.md>)
- 目标分支：server `codex/dev-20260820`；web `codex/dev-20260707`
- 当前状态：已实施，自动化验证通过；页面联调与 202610280001 类型修正待用户执行

## 2. 确认记录

| 日期 | 范围/版本 | 确认人 | 结论 |
| --- | --- | --- | --- |
| 2026-10-29 | 需求 202610280001 类型应为数据提取/运维；详情页支持修改需求类型、改后实时保存 | 用户 | 原始需求描述即实施指令，按 v1 规格实施 |

## 3. 任务与依赖

| 任务 | 交付物 | 依赖 | 验收映射 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | `IntakeRequirementTypeUpdateRequest` 模型、`IntakeService.updateRequirementType` 及服务测试 | 规格确认 | AC-02、03、04、06 | 已完成 |
| T-02 | `IntakeController` 接口暴露及控制器测试 | T-01 | AC-01 | 已完成 |
| T-03 | 前端 API、详情页类型编辑入口与弹框 | T-02 | AC-05 | 已完成 |
| T-04 | 定向测试、构建、系统文档更新 | T-01～03 | AC-01～06 | 已完成 |
| T-05 | 202610280001 类型修正（数据提取/运维） | 功能部署 | Proposal 背景项 | 待用户执行 |

修改范围：server 的 intake model/service/controller 及测试；web 的 `src/api/intake.ts`、`IntakeListView.vue`；本目录规格与系统文档。其他已有未提交修改保持原状。

## 4. 验证计划

- 服务：合法/空/非法类型；同值短路不落库不写历史；研发→运维清空研发分支名并记录；运维→研发保留既有分支名、缺失时生成；历史含操作人与前后值。
- 控制器：`POST /api/intake/{id}/requirement-type` 暴露、请求体绑定、service 调用与响应。
- 前端：TypeScript 编译与构建通过；编辑入口仅 `canUpdateMetadata` 可见；保存后详情与列表刷新逻辑与业务线编辑一致。
- 数据修正：不直接改库，由用户在部署后通过详情页将 202610280001 改为数据提取/运维（同时验证 AC-05）。

## 5. 实际结果

2026-10-29 完成只读核对与 v1 规格建档：确认识别链路（Codex 结构化提取 + 关键词回退）、类型值域、业务线/优先级既有保存模式与分支名按类型归一的联动事实。未改动代码、未修改数据。

### 2026-10-29 实施与验证

- T-01：新增 `IntakeRequirementTypeUpdateRequest`；`IntakeService.updateRequirementType` 按 Design 第 3 节实现（值域校验、同值短路、`withRequirementType` + 归一化落库、类型/分支名历史）。新增服务测试 4 项：研发→运维清空分支名并写入历史、运维→研发自动生成 `feature/repayment_data_fix_202610280001` 且写入历史、同值短路不落库不写历史、非法/空类型报错且不落库。
- T-02：`IntakeController` 新增 `POST /{id}/requirement-type`，权限 `intake:metadata:update` 或 `intake:record:update`；控制器测试验证接口暴露、请求体绑定与 service 调用。
- T-03：`src/api/intake.ts` 新增 `updateIntakeRequirementType`；`IntakeListView.vue` 详情“需求类型”增加 `canUpdateMetadata` 控制的编辑入口与“修改需求类型”弹框（含分支名联动提示），保存后回写详情、同步研发分支表单、刷新列表。
- 后端验证：`mvn -o -pl workhub-controller -am -Dtest='IntakeServiceTest,IntakeControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test` 通过，IntakeServiceTest 36 项（含新增 4 项）、IntakeControllerTest 19 项（含新增 1 项），0 失败 0 错误，既有回归未破坏。
- 前端验证：workhub-web `npm run build` 通过（3.5s），新增模板与逻辑编译无错误。
- 文档：系统设计新增第 11 节，迭代日志新增本条目；无数据库迁移。
- 待办（T-05）：功能部署后由用户在详情页将 202610280001 改为“数据提取/运维”，该操作同时作为 AC-05 页面联调核对。
