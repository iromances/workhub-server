# Design：业务线编辑弹框排版优化

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-3e6c421b-a6de-4d04-a20a-24f2dc3fb0a1 |
| 需求名称与唯一需求目录 | 业务线编辑弹框排版优化；`docs/业务线/BL000012-工作台/需求迭代/REQ-3e6c421b-a6de-4d04-a20a-24f2dc3fb0a1-业务线编辑弹框排版优化/` |
| 涉及仓库与功能域 | workhub-web（项目管理-业务线弹窗） |
| 所属项目或业务线 | 不适用（WorkHub 平台自身功能） |
| 对应 Proposal 版本 | v1 |
| 状态 | v1 已确认并实施（2026-11-10）；实施调整：容器规则以 :global 落地、列宽收窄防横向溢出 |
| 当前事实核对环境、分支、时间 | web 工作区当前开发分支；2026-11-10 本地 dev（127.0.0.1:9529）实际渲染核对 |

## 1. 现状与证据

- 弹窗：`ProjectListView.vue` 内 `groupDialogVisible` 控制的 `el-dialog`，宽 860px，`label-position="top"` 表单；模板位于该文件“编辑业务线/新增业务线”弹窗段。
- 基础字段：业务线名称、GitLab 组名、说明（textarea rows=3）、支持环境（多选）依次单列全宽。
- 访问地址：`access-config-section` 头部（标题+说明+新增地址按钮）+ `el-empty`（空态）+ `v-for` 渲染 `access-config-row`；每条两行：`access-config-main` 七列 grid（`112px 112px 120px minmax(190px,1fr) minmax(150px,0.8fr) 42px 42px`）+ `access-config-credential` 两列 grid（`240px minmax(190px,1fr)`）。
- 弹窗滚动行为（实测）：`.el-overlay-dialog` 为滚动容器（视口 720px 时 `scrollHeight≈1551`），footer 随内容滚动。
- 既有交互事实：类型切换 `handleEndpointTypeChange` 控制网关前缀显示；密码框 `show-password`、placeholder 按 `loginPasswordConfigured` 区分“留空则不修改”；删除调用 `removeBusinessLineAccessConfig(index)`；新增调用 `addBusinessLineAccessConfig`；支持环境 change 归一化 `normalizeSupportedEnvironmentCodes`。
- 尚未核实的假设：无；实施以文件现状为准。

## 2. 系统架构（呈现层改造）

- 总体方案：同一弹窗内重构模板结构与样式，不改任何提交/回显逻辑。
- 数据流不变：`groupForm` → `submitGroupCreate` → 既有 API。

| 模块 | 负责 | 不负责 |
| --- | --- | --- |
| 弹窗容器 | 宽度 960px、限高、body 内部滚动、footer 固定 | 数据逻辑 |
| 基础字段区 | 名称/GitLab 组名两列并排，说明 2 行 | 校验规则 |
| 访问地址区 | el-table 行内表单（9 列），列头标注字段 | 行内输入的取值语义 |

## 3. 呈现设计

### 3.1 弹窗容器

- `width="960px"`，增加 class `business-line-dialog`（Element Plus 2.11 class 透传到 `.el-dialog` 根元素）。
- 样式：`--el-dialog-margin-top: 5vh`；`max-height: 85vh`；`display: flex; flex-direction: column`；`.el-dialog__body { flex: 1; overflow-y: auto; }`；`.el-dialog__header/.el-dialog__footer { flex-shrink: 0; }`。
- footer 保持取消/保存按钮不变。

### 3.2 基础字段区

- `el-row :gutter="16"`：业务线名称（`el-col :span="12"`）+ GitLab 组名（`el-col :span="12"`）。
- 说明：全宽 textarea `:rows="2"`。
- 支持环境：全宽多选，逻辑不变。

### 3.3 访问地址区（表格式）

- 头部保留：标题“访问地址与网关”、说明文案、右侧“新增地址”按钮。
- 空态：el-table `empty-text="暂未配置访问地址"`，移除 `el-empty`。
- `el-table :data="groupForm.accessConfigs" size="small"`，9 列（行内控件统一 `size="small"`）：

| 列 | 字段 | 控件 | 宽度 | 说明 |
| --- | --- | --- | --- | --- |
| 环境 | environmentCode | el-select | min-width 88 | 选项=groupEnvironmentOptions |
| 类型 | endpointType | el-select | min-width 92 | change 仍调 `handleEndpointTypeChange` |
| 地址名称 | endpointName | el-input | min-width 120 | placeholder“名称” |
| 地址 | endpointUrl | el-input | min-width 220 | placeholder“https://example.com” |
| 网关前缀 | pathPrefix | GATEWAY→el-input，其余→“—” | min-width 110 | placeholder“/asset-api” |
| 登录账号 | loginUsername | el-input clearable | min-width 110 | placeholder“可选” |
| 登录密码 | loginPassword | el-input password show-password | min-width 120 | placeholder 沿用 configured 区分文案 |
| 启用 | enabled | el-switch size=small | width 56，居中 | |
| 操作 | — | 删除 link danger | width 56，居中 | `removeBusinessLineAccessConfig($index)` |

- 密度样式：`.business-line-dialog .access-config-table .el-table__cell { padding: 4px 0; }`、`.access-config-table .cell { padding: 0 6px; }`。

### 3.4 样式清理

- 移除不再使用的类：`access-config-row`、`access-config-main`、`access-config-credential`、`access-prefix-placeholder`（占位样式若仍用于“—”则保留，命名不变）。
- 新增 `business-line-dialog`、`access-config-table` 相关样式；`access-config-section`/`access-config-header` 保留微调。

## 4. 非功能约束

- 无接口、数据与权限变更；不引入新依赖。
- 兼容新增/编辑两种模式（同一弹窗、同一表单结构）。
- 可访问性：列头承担字段语义，行内 placeholder 精简为示例值。

## 5. 交付与回滚

- 发布顺序：随 workhub-web 前端发布，无后端依赖。
- 回滚条件：排版回归或功能异常；回滚步骤 = 还原该文件本次改动。
- 文档更新：本目录 tasks 验证证据、`docs/业务线/BL000012-工作台/迭代日志.md`；不影响系统现状章节（纯前端呈现）。

## 6. 一致性与待确认

- 目标 1（两列并排）→ §3.2；目标 2（表格式）→ §3.3；目标 3（footer 固定）→ §3.1。
- 非范围保持：不改变保存载荷与交互语义（§2 明确不做）。
- 待确认问题：无（关键方案已于 2026-11-10 经用户交互确认）。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
