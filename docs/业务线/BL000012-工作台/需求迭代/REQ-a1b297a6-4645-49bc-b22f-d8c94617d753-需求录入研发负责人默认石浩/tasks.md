# Tasks：需求录入研发负责人默认石浩

## 1. 元信息

- 需求编码：REQ-a1b297a6-4645-49bc-b22f-d8c94617d753
- 需求名称与唯一需求目录：需求录入研发负责人默认石浩，`docs/业务线/BL000012-工作台/需求迭代/REQ-a1b297a6-4645-49bc-b22f-d8c94617d753-需求录入研发负责人默认石浩/`
- 涉及仓库与功能域：workhub-web 需求录入弹窗
- Proposal 版本：v1
- Design 版本：v1
- 目标环境与分支：web `codex/development-20261103`
- 负责人/评审人：用户

## 2. 任务总览

| 任务 | 交付物 | 前置任务 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | `openCreateDialog()` 默认值修改 + 构建验证 + 迭代日志 | 无 | 设计第 2 节；AC-01~03 | 已完成 |

执行顺序：单任务。

## 3. 任务明细

### T-01：前端默认值与验证

- 描述与目标：`IntakeListView.vue` 的 `openCreateDialog()` 将 `developmentOwnerUserName` 默认置 `'石浩'`，其余表单重置逻辑不变。
- 输入：Design 第 1、2 节；既有 `submitCreate` 校验与提交逻辑。
- 输出：单行代码修改；`npm run build` 结果；迭代日志条目。
- 允许修改范围：`workhub-web/src/views/intake/IntakeListView.vue` 一处默认值；workhub-server 迭代日志与本需求目录。
- 明确不修改范围：后端代码、接口契约、其他表单字段默认值、历史数据。

验收条件：

- [x] AC-01：打开弹窗研发负责人默认“石浩”且可编辑（静态核对 + 构建通过；页面联调待部署后执行）。
- [x] AC-02：清空后提交仍被“请填写研发负责人”拦截（校验代码未改动）。
- [x] AC-03：提交取输入框实际值（`formData.append('developmentOwnerUserName', createForm.developmentOwnerUserName.trim())` 未改动）。

计划验证方式：`npm run build`；核对 `openCreateDialog` 与 `submitCreate` 相关代码段。

状态及真实结果：已完成。2026-11-03 修改 `openCreateDialog()`（原 908 行）默认值为 `'石浩'`；`npm run build` 通过（vue-tsc 类型检查 + vite 构建，见下方验证记录）；`submitCreate` 校验与提交取值代码未改动，AC-02/AC-03 静态核对成立。页面联调待前端部署后由用户执行。

## 4. 覆盖与评审

- Proposal 验收标准 AC-01~03 均映射至 T-01。
- Design 契约/数据/非功能约束：无接口与数据变更，无需额外承接。
- 未覆盖项：无。
- 系统设计更新：不需要（第 7 节不涉及表单默认值），已在迭代日志说明。
- 完整规格统一确认：以用户 2026-11-03 原始需求描述“需求录入功能，研发负责人前端默认石浩”作为实施确认；规格版本 v1，确认人：用户。
