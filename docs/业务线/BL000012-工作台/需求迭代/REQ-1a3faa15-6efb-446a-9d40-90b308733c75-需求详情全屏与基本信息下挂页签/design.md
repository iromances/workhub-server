# Design：需求详情全屏与基本信息下挂页签

| 项目 | 内容 |
| --- | --- |
| 需求名称与编码 | 需求详情全屏与基本信息下挂页签（REQ-1a3faa15-6efb-446a-9d40-90b308733c75） |
| 涉及仓库与功能域 | workhub-web：`IntakeListView.vue` 需求详情弹框的模板、脚本与样式，及 `docs/frontend-architecture.md`；workhub-server 仅文档 |
| 所属项目或业务线（适用时） | 工作台（BL000012） |
| 对应 Proposal 版本 | v1.0 |
| 状态 | 已回退（同 Proposal）：§2 的方案曾在工作区实施，2026-11-25 按用户指令整体撤销，未留存于代码；本文保留为历史设计记录 |
| 当前事实核对环境、分支、时间 | 2026-11-25；web 分支 `codex/development-20261122`（HEAD `469053e`，工作区含既有未提交改动）；Element Plus 实装 2.13.6（`package.json` 声明 `^2.11.4`）。§1 的行号为改动前的工作区文件 |

## 1. 现状与证据

- 当前流程、模块和代码位置：详情弹框位于 `src/views/intake/IntakeListView.vue` L4336–L5401，`<el-dialog … width="86vw" top="5vh" class="intake-detail-dialog" destroy-on-close>`；内部 `el-skeleton`（`detailLoading`）后为 `el-tabs v-model="detailActiveTab" class="detail-tabs" @tab-change="handleDetailTabChange"`，六个平级页签：基本信息（L4356–L4582）、需求澄清（L4584–L4794，仅研发需求）、研发任务（L4796–L5169，仅研发需求）、需求待办（L5171–L5293，标题带数量）、识别与附件（L5295–L5376）、修改历史（L5378–L5396）。
- 基本信息页签内容：`detail-basic-layout` 两栏网格——左栏为 `el-descriptions` 属性表、需求描述、交付数据文件（`canGenerateSqlDraft`）、研发协同（研发需求）、SQL 草稿（`canGenerateSqlDraft`）；右栏为 `aside.detail-lifecycle-panel` 阶段时间线（`position: sticky; top: 0`；研发需求 10 项、数据提取/运维 6 项，需求终止关闭时加 1 项，见 `buildDemandStageTimeline` L3251–L3308）；无结构化字段时显示 `el-empty`。
- 当前脚本：`detailActiveTab = ref('basic')`（L99）；`handleDetailTabChange`（L735–L749）切到需求澄清、研发任务时分别调用 `loadClarificationAnalysis`、`loadDevelopmentAnalysis`，其余页签调用 `refreshSelectedDetail`；`openDetail`（L751–L787）与 `resetDetailDialog`（L789–L805）均把页签置为 `'basic'`；`submitRequirementType`（L520–L546）修改类型成功后整体替换 `selectedDetail`。入口为列表行"详情"（L3695）与看板跳转 `/demands/list?intakeId=`（L3444–L3455），均走 `openDetail`。
- 当前样式：`:deep(.intake-detail-dialog) { max-width: 1400px }` 与弹框体 `max-height: calc(90vh - 112px); overflow-y: auto; padding-top: 8px`（L5831–L5839）；`.detail-basic-layout`（L6165–L6170）、`.detail-lifecycle-panel`（L6176–L6184）、`.task-evaluation-scroll--detail { overflow: visible }`（L5915）。
- 事实来源（路径、提交、环境或只读证据）：上述代码；`node_modules/element-plus/theme-chalk/el-dialog.css`、`el-tabs.css`；`es/components/dialog/src/*.mjs`、`es/components/skeleton/src/*.mjs`。
- 目标系统当前实现证据及本次重新核对结果：
  1. `el-dialog` 在 `append-to-body` 为 false（默认）时禁用内部 `el-teleport`，弹框就地渲染，`class` 落在 `.el-dialog` 根元素，现有 `:deep(.intake-detail-dialog …)` 选择器可命中。
  2. `.el-dialog.is-fullscreen` 为 `width: 100%; height: 100%; margin: 0; overflow: auto`，`.el-dialog` 本身不是 flex 容器；`.el-tabs` 为 `display: flex`，`.el-tabs--top` 纵向排列，`.el-tabs__header` 默认 `position: relative; margin: 0 0 15px`，`.el-tabs__content` 默认 `flex-grow: 1; overflow: hidden`。
  3. `el-skeleton` 加载态根元素为单个 `div.el-skeleton`，非加载态直接渲染默认插槽。
  4. 本机 dev（9091）与后端（8080）在运行，dev 进程工作目录与当前工作区为同一目录（HMR 生效）。
- 尚未核实的假设：真实页面尺寸未实测（内置浏览器无登录态）。已核实的是弹框体可视高度等于 `100vh - 72px`（弹框上下内边距各 16px、标题栏 40px、弹框体上内边距 8px），由 Element Plus 样式与 §2 原型测量一致确认。基本信息区自然高度未实测：按时间线项数估算，研发需求约 900px 上下，数据提取/运维需求随 SQL 草稿长度变化，以页面实测为准。开发机屏幕可视高度约 940px（内置屏逻辑分辨率 1440×932、外接屏 1920×1080）。

## 2. 系统架构

- 总体方案和设计目标：弹框全屏；基本信息从页签变为置顶的主体内容，五个页签挂在其下；五个页签的内容与操作原样迁移，不改接口与权限。采用"单列滚动 + 页签栏吸顶"，保证全屏后页签工作区不小于改版前。

```text
┌ 需求详情 ───────────────────────────────────── ✕ ┐
│ 基本信息                                            │
│ ┌ 属性表 ───────────────┐ ┌ 阶段时间线 ─────────┐ │
│ │ 状态/编号/类型/名称…   │ │ 录入 ✓ 澄清 ✓ 评估 ● │ │
│ └───────────────────────┘ └─────────────────────┘ │
│ 需求描述 …                                           │
│ 研发协同（研发分支 / 禅道地址）                       │
│ 需求澄清 │ 研发任务 │ 需求待办 │ 识别与附件 │ 修改历史 │ ← 滚到顶部后吸附
│ [当前页签内容]                                       │
└──────────────────────────────────────────────────┘
```

- 主要流程、数据流和状态流：打开详情的请求流程不变（`openDetail` → `loadDetail` → 澄清/研发任务加载）。变化仅在页签状态：详情加载成功后把 `detailActiveTab` 设为默认页签；需求由研发需求变为非研发需求而当前页签是研发专属页签时回落到"需求待办"。

- 前端页面、交互与后端规则边界：

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| 详情弹框容器（模板、`.intake-detail-dialog` 样式） | `fullscreen`；移除 `width`、`top` 与 `max-width`；弹框改为纵向 flex、`overflow: hidden`，弹框体 `flex: 1; min-height: 0; overflow-y: auto` 作为唯一外层滚动容器 | 页签内布局 | — | `el-dialog` fullscreen |
| 基本信息区 `.detail-basic-pane` | 基本信息内容置顶（原基本信息页签内容原样迁入，首标题"基本信息"）；`max-height: calc(100vh - 200px)`、`padding-bottom: 8px`、底部分隔线、`overflow-y: auto`，仅超长内容时区内滚动 | 内容与操作 | — | 原有样式 |
| 页签区 `.detail-tabs` | 五个 `el-tab-pane` 原样迁入；`min-height: calc(100vh - 72px)`；`el-tabs__header` 改 `position: sticky; top: 0`、白底并以等高实色阴影盖住 15px 下边距；`el-tabs__content` 改 `overflow: visible` | 页签内容与操作 | `detailActiveTab` | `el-tabs` |
| 页签状态脚本 | `openDetail` 加载成功后设默认页签（研发需求 → `clarification`，其余 → `todos`），`resetDetailDialog` 重置为 `todos`；`watch` 需求是否研发需求，由是变否且当前页签为 `clarification`/`evaluation` 时置为 `todos` | 数据加载 | `selectedDetail` | `isDevelopmentRequirement` |

- 滚动与高度取值依据（原型实测，视口 1920×940）：弹框体可视高度为 `100vh - 72px`。基本信息区上限取 `calc(100vh - 200px)`：触顶时页签栏位于视口底部上方约 120px，页签头下方露出约 50px 页签内容，提示下方可滚动；上限取 `calc(100vh - 140px)` 时页签头刚好贴底、内容不可见，故未采用。页签区最小高度取弹框体可视高度：页签栏吸附时页签区底边与弹框体底边对齐，从长页签切到短页签时外层滚动位置回落到最大值，页签头位置不变。页签头吸附于标题栏下方 24px（弹框内边距 16px + 弹框体上内边距 8px）。
- 生命周期时间线：其 `sticky` 以最近的滚动容器为参照。基本信息区未溢出时整区（含时间线）位于首屏内，不需要吸附；溢出时时间线吸附在区内顶部。两种情形下时间线均可见，不丢失原有行为。
- 宽度：全屏后弹框宽为视口宽，`.detail-basic-layout` 两栏按 `2fr / 1fr` 伸展，`max-width: 1180px` 断点仍生效。页签内表格与卡片的列数不调整。
- 模板改动的缩进处理：基本信息内容与五个页签内容的行首缩进保持不变，只调整包裹元素，避免大面积无关差异；基本信息区内的 `<template v-if="selectedDetail.structuredData">` 因此比其父元素多缩进一级。

- 离线原型验证及其局限：实施前用 Element Plus 2.13.6 样式、应用全局样式和本组件样式，按 Element Plus 的 DOM 结构手写了等价原型（数据为模拟内容），在内置浏览器中测量：
  1. 1920×1080、1920×940、1440×900 三种视口下弹框铺满，弹框体可视高度均为 `100vh - 72px`。
  2. 页签头滚动后吸附于标题栏下方，其下 15px 间距无滚动内容透出。
  3. 页签内容 1305px 时滚至吸附后切到 118px 的短页签，页签头位置不变，页签区底边与弹框体底边对齐。
  4. 基本信息区内容 956px 与 5363px 两种情形均被限制在上限内并出现区内滚动，页签栏完整留在首屏；区内滚动后时间线吸附于区块顶部。
  原型不是真实页面：属性表列宽、时间线样式等视觉细节，以及 Element Plus 组件在滚动容器变化后的实际表现，均需真实页面走查确认。

## 3. 契约与数据模型

- 无新增、修改或废弃的 API、事件与数据库对象；五个页签内的接口调用与权限判断不变。
- 页签切换刷新行为不变：需求澄清、研发任务切换时重新查询各自接口，其余页签刷新详情。
- 无数据迁移，无历史兼容问题。

## 4. 非功能约束

- 安全与权限、授权边界和测试数据处理：不变；页签内操作按钮仍由原有权限计算控制。
- 性能、容量和时效：不新增请求；`destroy-on-close` 保留，关闭后销毁页签内容。
- 失败处理、降级与补偿：不涉及。
- 日志、监控、告警与审计：不涉及。

## 5. 交付与回滚

- 影响系统和前后端/数据库发布顺序：仅前端发布。
- 灰度或开关：无。
- 数据备份与前后校验：不涉及数据。
- 回滚条件、步骤与不可逆部分：按本次差异反向修改 `IntakeListView.vue` 即可，无不可逆部分。该文件在工作区另有既有未提交改动，回滚时不能用 `git checkout` 整文件还原，需按差异还原。
- 生效并核实后需要更新的目标项目系统设计、迭代日志、需求及专项文档内容与证据：`workhub-web/docs/frontend-architecture.md` 补充需求详情布局说明；工作台 `现状.md` 新增 11c 小节；`迭代日志.md` 新增条目；本目录 `tasks.md` 记录验证证据。

## 6. 一致性与待确认

- Proposal 每项目标的设计承接位置：目标 1 → §2 详情弹框容器；目标 2 → §2 基本信息区、页签区与页签状态脚本。
- 明确非范围是否被保持：五个页签内容、列表页、评估弹框与后端均未改动；`developmentRoundDialogVisible` 弹框（带 `append-to-body`，现位于需求待办页签内）随页签原样迁移，未调整位置。
- 实施前向用户征询的两项（均未获答复；下列处理方式已随 2026-11-25 的回退一并作废，此处保留为历史记录）：
  1. **排版方案**：按 §2 主方案实施。备选"上下分屏常驻"未实施，其与主方案的差异仅在滚动模型——内容容器撑满弹框体，基本信息区 `flex: 0 1 auto; max-height` 约 `46vh; overflow-y: auto`，页签区 `flex: 1 1 0; min-height: 0` 且页签内容自行滚动，页签头不吸附。备选让基本信息常驻可见、页签区位置固定；但在可视高度约 940px 的屏幕上两区各约 420px，页签工作区小于改版前（弹框体约 700px），基本信息需要区内滚动。改用备选时只需替换 `.detail-layout`、`.detail-basic-pane`、`.detail-tabs` 与页签头/内容区样式，模板与脚本不变。
  2. **既有瑕疵未纳入**（均在研发任务页签，HEAD 中已存在，非工作区既有未提交改动引入），原样保留：
     - 空状态错位：`<template v-if="developmentAnalysis?.draft">`（L4963–L5090）之后依次为"AI 分支开发"区块（L5092–L5122）、`IntakeTestDeploymentPanel`（L5124）、`<div v-if="submitTestReview">`（L5126–L5161），而 `<el-empty v-else description="当前还没有任务草稿。">`（L5163–L5166）紧跟的是 `submitTestReview` 条件。结果是：没有提测审核记录时（提测前的常态），已有任务拆解的页签底部仍显示"当前还没有任务草稿。"；真正没有草稿但已有提测审核记录时不显示。修法：把该 `el-empty` 移回 L5090 的 `</template>` 之后，其后三个区块的条件不变。
     - 空工具栏卡片：L4910–L4915 的 `task-evaluation-toolbar-card > task-evaluation-toolbar-main > task-evaluation-toolbar` 三层容器内无内容，渲染为一条空白圆角卡片，位于"AI识别需求点"与"评估输出"之间。修法：删除这 6 行模板；同名样式类仍被评估弹框（L3903–L3905）使用，样式保留。
- 契约、ADR 及 Tasks 的关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)；无契约与 ADR。
