# Design：需求详情类型修改与实时保存

- 需求编码：REQ-0655b868-e6da-48ae-8751-0a21725b820d
- 对应 Proposal：v1，已确认。
- 核对日期：2026-10-29；server 分支 `codex/dev-20260820`，web 分支 `codex/dev-20260707`。

## 1. 现状与证据

- 后端 `IntakeController` 已有 `/business-line`、`/priority`、`/zentao-link`、`/development-branch` 等元数据即时修改接口，权限均为 `intake:metadata:update` 或 `intake:record:update`，返回 `ApiResponse<IntakeDetailResponse>`。
- `IntakeService.updateBusinessLine/updatePriority` 的保存模式：`requireExisting` → 构造当前 `IntakeStructuredData` → 合并新值 → 无变化提前返回 → `applyStructuredData` + `updateFormalFields` + `updateStructuredData` + `replaceStructuredFields` → `recordHistory` → 返回 `detail(id, null, false)`。
- `writeStructuredDataJson` 落库前调用 `normalizeStructuredData`，其中 `normalizeDevelopmentBranchName`（`DevelopmentBranchNameGenerator.normalize`）按类型重算分支名：非“研发需求”返回 null，“研发需求”保留既有规范化分支名、缺失时生成。
- 类型值域事实：`isOperationsRequirement`（数据提取/运维）、`isDevelopmentRequirement`（研发需求）、SQL 草稿仅限数据提取/运维、澄清/研发分析自动化仅限研发需求。
- 前端 `IntakeListView.vue` 详情基本信息中“业务线”带 `canUpdateMetadata` 控制的“编辑”入口，弹框保存后回写 `selectedDetail`、`syncDevelopmentForms` 并刷新列表；“需求类型”当前为只读文本。

## 2. 接口契约

新增 `POST /api/intake-records/{id}/requirement-type`（与既有接口共用 `/api/intake` 别名）。

请求体：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| requirementType | string，必填 | 目标需求类型，仅允许“研发需求”“数据提取/运维” |

成功响应为 `ApiResponse<IntakeDetailResponse>`（最新详情）。权限 `intake:metadata:update` 或 `intake:record:update`；空值/非法值返回参数错误（`IllegalArgumentException` 走既有全局异常处理）。

## 3. 服务实现

`IntakeService.updateRequirementType(id, request, operatorUserName)`，事务方法，流程对齐 `updateBusinessLine`：

1. `requireExisting(id)`；`requireRequirementType` 校验值域（复用 `trimToNull`/`requireValue`）。
2. `currentStructuredData = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(id))`。
3. `mergeRequirementType`：基于 `normalizeStructuredData(currentStructuredData)`（旧类型口径，分支名先按旧类型归一），仅替换 `requirementType`；当前结构化数据为 null 时构造仅含类型的最小结构化数据（对齐 `withBusinessLine` 的空值处理）。
4. 与旧类型相同（`trimToNull` 后比较）时直接返回详情，不落库、不写历史。
5. 手动执行 `normalizeStructuredData(merged)` 得到落库口径，使分支名联动结果可对比、可入历史；再经 `applyStructuredData`、`updateFormalFields`、`updateStructuredData`（内部再次归一，幂等）、`replaceStructuredFields` 持久化。
6. 历史：固定行“需求类型：旧 -> 新”；分支名因联动变化时追加“研发分支名：旧 -> 新”。摘要“修改需求类型”。
7. 返回 `detail(id, null, false)`。

不修改识别逻辑、不新增数据库字段或迁移；类型仍存于 `intake_record.requirement_type`、结构化 JSON 与 `intake_structured_field`。

## 4. 前端实现

`src/api/intake.ts` 新增 `updateIntakeRequirementType(id, request)`，POST `/intake/{id}/requirement-type`，返回 `IntakeDetail`。

`IntakeListView.vue`：

- 详情基本信息“需求类型”值后增加“编辑”链接（`canUpdateMetadata` 控制），与“业务线”入口一致。
- 新增“修改需求类型”弹框：单选（研发需求 / 数据提取/运维）+ 说明信息（切换类型的分支名联动与后续流程差异）。
- 保存成功后回写 `selectedDetail`、`syncDevelopmentForms(detail)`（分支名可能被清空）、刷新列表并提示“需求类型已保存”；失败沿用 `resolveErrorMessage`。

不改动类型识别、筛选、阶段流等其他逻辑。

## 5. 验证与回滚

- 服务测试：值域校验、无变化短路、切运维清空分支、切研发保留/生成分支、历史记录与操作人。
- 控制器测试：接口暴露、参数绑定与权限路径（standalone MockMvc，对齐既有测试）。
- 前端：TypeScript 编译/既有测试方式与 `npm run build`。
- 回滚：均为增量接口与 UI 入口，回退对应提交即可；无数据迁移。已保存的类型修改通过同一接口改回。
