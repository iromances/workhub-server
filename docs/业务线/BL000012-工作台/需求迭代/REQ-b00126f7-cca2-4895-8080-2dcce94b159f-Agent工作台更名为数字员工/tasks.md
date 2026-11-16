# Tasks：Agent 工作台更名为数字员工

## 1. 元信息

- 需求编码：REQ-b00126f7-cca2-4895-8080-2dcce94b159f
- 需求名称与唯一需求目录：Agent 工作台更名为数字员工，`docs/业务线/BL000012-工作台/需求迭代/REQ-b00126f7-cca2-4895-8080-2dcce94b159f-Agent工作台更名为数字员工/`
- 涉及仓库与功能域：workhub-web 菜单/路由/页面标题；workhub-server docs
- Proposal 版本：v1
- Design 版本：v1
- 目标环境与分支：web `codex/development-20261103`；server `codex/development-20261102`
- 负责人/评审人：用户

## 2. 任务总览

| 任务 | 交付物 | 前置任务 | 对应设计/验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 前端三处名称替换 + 构建验证 | 无 | 设计第 2 节；AC-01~03 | 已完成 |
| T-02 | 现行文档同步（现状.md、frontend-architecture.md）+ 迭代日志条目 | T-01 | 设计第 2 节；AC-04 | 已完成 |

执行顺序：T-01 → T-02。

## 3. 任务明细

### T-01：前端名称替换与验证

- 描述与目标：`menu.ts` 菜单 label、`router/index.ts` 路由 `meta.title`、`AgentWorkbenchView.vue` 页面 `<h2>` 三处“Agent 工作台”改为“数字员工”。
- 输入：Design 第 1、2 节。
- 输出：三处字符串修改；`npm run build` 结果。
- 允许修改范围：上述三个文件各一处字符串；不触碰同文件其他未提交改动。
- 明确不修改范围：页内实体文案与代码标识、后端代码、接口与权限。

验收条件：

- [x] AC-01：菜单 `/agents` 项显示“数字员工”（`menu.ts` label 已替换，构建产物 `AppLayout` 分片含新名称）。
- [x] AC-02：路由标题显示“数字员工”（`router/index.ts` `meta.title` 已替换，构建产物 `index` 分片含新名称）。
- [x] AC-03：页面 `<h2>` 显示“数字员工”，其余文案不变（`AgentWorkbenchView.vue` 仅标题一处替换，构建产物对应分片含新名称）。

计划验证方式：`npm run build`（vue-tsc + vite 构建）；静态核对三处修改与周边未改动。

状态及真实结果：已完成。2026-11-06 修改三处字符串；`npm run build` 通过（vue-tsc + vite，`✓ built in 3.69s`）；`grep` 确认 workhub-web `src/` 无“Agent 工作台/Agent工作台”残留、`dist/` 三个相关分片含“数字员工”且无旧名称。两文件在改名前已有其他未提交改动，本轮未触碰。页面联调待前端部署后执行。

### T-02：文档同步

- 描述与目标：workhub-web `docs/frontend-architecture.md` 模块章节更名并注明原名；workhub-server `现状.md` 标题/正文同步；`迭代日志.md` 新增本轮条目；历史记录保留原称。
- 输出：三份文档更新。
- 允许修改范围：上述现行文档；明确不修改历史规格、历史日志条目与迁移清单。

验收条件：

- [x] AC-04：现行文档以“数字员工”指称该模块，历史文档未改动。

状态及真实结果：已完成。现状.md 模块标题改为“数字员工与执行桥接（原 Agent 工作台…）”并补充更名说明；frontend-architecture.md 章节改为“数字员工（原 Agent 工作台，v4 扩展）”并注明标识不变；迭代日志新增 2026-11-06 条目。REQ-bf94e06e 历史规格、历史日志条目、迁移清单与 project-charter/implementation-roadmap 等历史章节未改动。另核实并行工作区分支 `codex/agent-workbench-autonomy` 的 `AiUseCaseConfigService.java:180` 与 `DefaultAiGatewayClient.java:330` 两处提示语仍为旧称，未在本轮改动，待该工作区同步（已记入迭代日志）。

## 4. 覆盖与评审

- Proposal 验收标准 AC-01~04 均映射至 T-01/T-02。
- Design 契约/数据/非功能约束：无接口与数据变更，无需额外承接。
- 未覆盖项：页内 Agent 实体称谓与代码标识（明确不做，见 Proposal 边界）。
- 完整规格统一确认：以用户 2026-11-06 原始需求“AGENT工作台名称改为数字员工”作为实施确认；规格版本 v1，确认人：用户。
