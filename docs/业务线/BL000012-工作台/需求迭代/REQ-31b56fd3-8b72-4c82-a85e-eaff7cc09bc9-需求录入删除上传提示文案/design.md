# Design：需求录入删除上传提示文案

## 1. 现状与改动点

`workhub-web/src/views/intake/IntakeListView.vue` 需求录入弹窗中，"需求录入"文本域与上传区之间有一条常驻 `el-alert`（type=warning，不可关闭），标题文案为"截图和附件会先直接入库，再异步调用 Codex CLI 识别并回填结构化字段。支持点击上传、拖拽上传和直接粘贴截图。"。该提示为全局唯一出现，与两个上传 dropzone 内的"点击选择、直接拖入或粘贴需求截图""点击选择或直接拖入需求附件"操作提示重复。

## 2. 方案

整块删除该 `el-alert` 节点（原 3197–3202 行），其后紧邻的上传区 `<el-row class="upload-panel-row">` 及弹窗其余模板、脚本、样式均不改动。

## 3. 契约、数据与交互影响

- 接口契约：无变化。`submitCreate` 仍按原 FormData 提交原文、截图、附件与元信息。
- 数据：无数据库与迁移变更。
- 交互：点击选择、拖拽、粘贴截图、文件去重、本地预览（`mergeFiles` / `handleDropped*` / `handlePastedScreenshots` / `previewLocalFile`）与提交校验全部保留；提交后仍先保存入库再异步识别（`createUploadIntake` → 后端 `IntakeEnrichmentService`），失败重试入口不变。
- 回滚：恢复该 `el-alert` 块即可，无数据副作用。

## 4. 验证

- 前端 `npm run build`（vue-tsc 类型检查 + vite 构建）通过。
- 全仓 grep 确认"会先直接入库"文案无残留。
- 页面联调（弹窗展示与三种上传方式）待前端部署后执行。
