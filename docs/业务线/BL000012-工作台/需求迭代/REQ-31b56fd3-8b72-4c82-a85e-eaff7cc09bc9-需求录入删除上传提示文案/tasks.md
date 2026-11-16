# Tasks：需求录入删除上传提示文案

## 1. 元信息

- 需求编码：REQ-31b56fd3-8b72-4c82-a85e-eaff7cc09bc9
- 需求名称与唯一需求目录：需求录入删除上传提示文案，`docs/业务线/BL000012-工作台/需求迭代/REQ-31b56fd3-8b72-4c82-a85e-eaff7cc09bc9-需求录入删除上传提示文案/`
- 涉及仓库与功能域：workhub-web 需求录入弹窗
- Proposal 版本：v1
- Design 版本：v1
- 目标环境与分支：web `codex/development-20261103`
- 负责人/评审人：用户

## 2. 任务总览

| 任务 | 交付物 | 前置任务 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 删除 `el-alert` 提示块 + 构建验证 + 迭代日志 | 无 | 设计第 2 节；AC-01~03 | 已完成 |

执行顺序：单任务。

## 3. 任务明细

### T-01：删除提示文案并验证

- 描述与目标：删除 `IntakeListView.vue` 需求录入弹窗中的常驻 `el-alert` 提示块（原 3197–3202 行），其余模板与逻辑不变。
- 输入：Design 第 1、2 节；既有上传与提交逻辑。
- 输出：单块模板删除；`npm run build` 结果；迭代日志条目。
- 允许修改范围：`workhub-web/src/views/intake/IntakeListView.vue` 该提示块；workhub-server 迭代日志与本需求目录。
- 明确不修改范围：上传 dropzone、拖拽/粘贴事件、`submitCreate` 校验与提交、后端代码、接口契约、需求清单其他文案。

验收条件：

- [x] AC-01：录入弹窗不再显示该条提示（模板已删除；构建通过；页面联调待部署后执行）。
- [x] AC-02：截图/附件上传区及其点击、拖拽、粘贴逻辑未改动（`handleScreenshotChange` / `handleDropped*` / `handlePastedScreenshots` / `mergeFiles` / `previewLocalFile` 均保留）。
- [x] AC-03：提交链路未改动（`submitCreate` 与 `createUploadIntake` 调用不变，先入库后异步识别契约维持）。

计划验证方式：`npm run build`；grep 确认文案无残留；静态核对弹窗模板。

状态及真实结果：已完成。2026-11-05 删除该 `el-alert` 块；`npm run build` 通过（vue-tsc 类型检查 + vite 构建，4.00s，产物含 `IntakeListView-BUT_BknW.js`）；全仓 grep"会先直接入库"无残留。页面联调待前端部署后由用户执行。

## 4. 覆盖与评审

- Proposal 验收标准 AC-01~03 均映射至 T-01。
- Design 契约/数据/非功能约束：无接口与数据变更，无需额外承接。
- 未覆盖项：无。
- 系统设计更新：不需要（仅删除常驻提示，系统现状无功能变化），已在迭代日志说明。
- 完整规格统一确认：以用户 2026-11-05 原始需求描述作为实施确认；规格版本 v1，确认人：用户。
