# Design：业务监测 XXL-JOB 手动检测与工具栏调整

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-c83a5661-f41f-43d2-8c5e-24db41bfacfc |
| 需求名称与唯一需求目录 | 业务监测 XXL-JOB 手动检测与工具栏调整；`docs/业务线/BL000012-工作台/需求迭代/REQ-c83a5661-f41f-43d2-8c5e-24db41bfacfc-业务监测XXL-JOB手动检测与工具栏调整/` |
| 涉及仓库与功能域 | workhub-web（运维-业务监测页面 `src/views/ops/OpsMonitorView.vue`） |
| 所属项目或业务线 | 不适用（WorkHub 平台自身功能） |
| 对应 Proposal 版本 | v1 |
| 状态 | v1 已实施（2026-11-12）；页面交互联调待用户登录后走查 |
| 当前事实核对环境、分支、时间 | workhub-web 当前开发分支；2026-11-12 读取工作区源码核对 |

## 1. 现状与证据

- 页面：`OpsMonitorView.vue`，`page-header` 内含说明文案 `page-desc`（“维护本地 XXL-JOB 监测配置，首页不采集远端调度库统计。”）与 `header-buttons`（“刷新配置”→`loadDashboard`、“新增 XXL-JOB 监测”→`openCreate`，后者 `v-if="activeTab === 'XXL_JOB' && canCreate"`）。
- 筛选区：XXL-JOB 页签内 `el-form.filter-form`，字段业务线/环境/仅启用 + “查询”按钮（`@click="loadDashboard"`）——“刷新配置”与“查询”调用同一函数，功能重复。
- 列表列：业务线（编码主行 + `.business-line-name` 中文名副行）、环境、监测名称、监测 Key、XXL-JOB 库、关注执行器、启用、上次状态、上次消息、上次采集时间（`prop="lastCheckedAt"` 原样输出 ISO，含 `T`）、备注、操作（详情/编辑/删除，宽 180）。
- 既有脚本：`canCreate/canUpdate/canDelete` 用 `authStore.hasAnyPermission`；`statusTag(status)` 映射 UP/WARN/ERROR 颜色；`loadDashboard()`→`loadMonitors()` 拉取 `monitorType: 'XXL_JOB'` 列表；详情 `lastCheckWindow()` 依赖 `lastCheckedAt?.split('T')[0]`（不受显示格式化影响）。
- 检测能力：`checkOpsMonitor(id)` 已封装于 `src/api/ops.ts:52`（`POST /ops/monitors/{id}/check`），返回 `OpsMonitorCheckResult { id, monitorType, monitorKey, status, message, checkedAt, detail }`；当前无任何视图调用。后端 `OpsMonitorService.check` 会把结果回写 `ops_monitor.last_status/last_message/last_checked_at`；状态名映射为 UP→正常、WARN→警告、ERROR→异常、DISABLED→已停用、CONFIGURED/空→未采集、其他→未知（`OpsMonitorService.lastStatusName`）。
- 尚未核实的假设：无；实施以文件现状为准。

## 2. 方案（纯前端呈现层改造）

- 总体方案：仅改 `OpsMonitorView.vue` 模板与脚本；复用既有 check 接口与 `checkOpsMonitor` 封装，后端不动。
- 数据流：`checkOpsMonitor(id)` 返回结果 → 就地更新 `monitors` 中对应行字段（`lastStatus/lastStatusName/lastMessage/lastCheckedAt`），不整表刷新，避免闪烁；批量检测结束后不额外刷新（就地更新已一致）。

| 模块 | 负责 | 不负责 |
| --- | --- | --- |
| 筛选区按钮组 | 查询 / 新增（canCreate）/ 全部检测（canCheck，含进度） | 筛选字段逻辑 |
| 页头 | 移除（文案与按钮全部迁走或删除） | — |
| 列表列 | 业务线主副行对调、删两列、时间格式化、操作列加“检测” | 数据结构 |
| 检测状态管理 | checkingIds / checkingAll / 进度计数 | 权限判定（沿用 authStore） |

## 3. 详细设计

### 3.1 工具栏

- 删除 `page-header` 整块（说明文案 + header-buttons）；“刷新配置”直接删除（与“查询”同函数）；`.header-buttons` 样式随之清理。
- 筛选区末尾 `el-form-item` 内并列三个按钮：“查询”（primary，不变）、“新增 XXL-JOB 监测”（primary，`v-if="canCreate"`，`@click="openCreate"`）、“全部检测”（`v-if="canCheck"`，`:loading="checkingAll"`，`:disabled="checkingAll"`）。因筛选区仅在 XXL-JOB 页签内，新增按钮原有的 `activeTab` 条件天然满足。
- “全部检测”文案：空闲时 `全部检测`，进行中 `全部检测（{done}/{total}）`（计算属性 `checkAllLabel`）。

### 3.2 检测逻辑（脚本新增）

- `const canCheck = computed(() => authStore.hasAnyPermission(['ops:monitor:check', 'ops:monitor:manage']))`。
- `const checkingIds = reactive(new Set<number>())`：进行中单条检测的 id 集合；`const checkingAll = ref(false)`；`const checkProgress = reactive({ done: 0, total: 0 })`。
- `statusName(status: string | null)`：镜像后端映射（UP 正常 / WARN 警告 / ERROR 异常 / DISABLED 已停用 / CONFIGURED 与空 未采集 / 其他 未知）。
- `formatDateTime(value?: string | null)`：空返回 `'—'`；否则 `value.replace('T', ' ')` 截取到秒（`YYYY-MM-DD HH:mm:ss`）。
- `applyCheckResult(row, result)`：`row.lastStatus = result.status`、`row.lastStatusName = statusName(result.status)`、`row.lastMessage = result.message`、`row.lastCheckedAt = result.checkedAt`。
- `checkRow(row)`：守卫 `row.monitorType !== 'XXL_JOB'` 时 warning 返回（本页列表实际全为 XXL-JOB）；加入 `checkingIds`；调用 `checkOpsMonitor(row.id)`；成功按 `result.status` 提示（UP→success“检测完成：正常”，WARN→warning“检测完成：警告”，其余→error“检测异常：{message 截断 80 字}”）并 `applyCheckResult`；异常（请求失败）→ error“检测失败”；`finally` 移出 `checkingIds`。
- `checkAll()`：列表为空时 info“暂无监测配置”返回；`checkingAll = true`、`checkProgress.total = monitors.value.length`、`done = 0`；**顺序**逐条调用 `checkOpsMonitor` 并 `applyCheckResult`（顺序避免并发压远端调度库，且进度直观）；单条 catch 计失败数并继续；结束按失败数提示（全成功 success“全部检测完成：共 N 条”，有失败 warning“全部检测完成：成功 X 条，失败 Y 条”）；`finally` 复位状态。

### 3.3 列表列（模板调整）

| 列 | 调整 |
| --- | --- |
| 业务线 | 主行改为 `businessLineName`，副行小字显示编码（副行类名改为 `.business-line-code`，样式沿用原 `.business-line-name` 规则） |
| 监测名称 / 监测 Key | 整列删除 |
| 环境 / XXL-JOB 库 / 关注执行器 / 启用 | 不变 |
| 上次状态 | 不变（`statusTag(row.lastStatus)`） |
| 上次消息 / 备注 | 不变 |
| 上次采集时间 | 改 template 输出 `formatDateTime(row.lastCheckedAt)` |
| 操作 | 加“检测”link 按钮（`v-if="canCheck"`，`:loading="checkingIds.has(row.id)"`，`:disabled="checkingAll || checkingIds.has(row.id)"`），顺序：详情 / 检测 / 编辑 / 删除；列宽 180→210 |

### 3.4 样式清理

- 删除 `.header-buttons`；`.business-line-name` 改名 `.business-line-code`（规则不变）。其余样式不动。

## 4. 非功能约束

- 无接口、数据、权限点与依赖变更；检测按钮权限与后端 `@PreAuthorize`（`ops:monitor:check`/`ops:monitor:manage`）一致。
- 批量检测顺序执行，避免对远端 XXL-JOB 库并发施压；进行中禁止重复触发（按钮 loading/disabled + checkingIds 去重）。
- 就地更新不整表刷新，保持滚动位置与筛选状态。

## 5. 交付与回滚

- 发布顺序：随 workhub-web 前端发布，无后端依赖。
- 回滚条件：检测功能异常或工具栏回归问题；回滚步骤 = 还原 `OpsMonitorView.vue` 本次改动。
- 文档更新：本目录 tasks 验证证据、`docs/业务线/BL000012-工作台/迭代日志.md`；不影响系统现状章节（纯前端交互，后端能力早已存在）。

## 6. 一致性与待确认

- 目标 1→§3.2/§3.3（行内检测）；目标 2→§3.2（checkAll）；目标 3→§3.1；目标 4→§3.3（列调整与格式化）。
- 非范围保持：不改详情弹窗与日志表时间显示、不改 api 封装签名。
- 待确认问题：无（范围与交互已经 2026-11-12 会话用户明确：单条 + 全部检测、按钮迁移、删列、去 T）。
- 关联链接：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
