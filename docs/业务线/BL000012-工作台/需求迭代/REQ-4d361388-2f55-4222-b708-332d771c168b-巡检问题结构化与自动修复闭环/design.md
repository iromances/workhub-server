# Design：巡检问题结构化与自动修复闭环

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-4d361388-2f55-4222-b708-332d771c168b |
| 状态 | 已确认（两期合并一次实施，2026-11-14 用户确认） |

## 1. 问题身份与跨轮生命周期（核心模型）

- **去重键**：`domain + businessLine + system + signature`。signature 由问题本质生成（慢 SQL=SQL 指纹哈希；系统错误=服务+错误类型+关键单号类；业务监测=监测项+失败摘要指纹；账务=规则+差异指纹）。AI 在结构化产出中给出 `signature` 字段，平台按 `(task_id, signature)` 聚合。
- **跨轮状态**：再次出现→更新 last_seen/occurrence_count/最新证据，不重复深查；FIXED 后再出现→自动重开（REOPENED 标记+关联上次修复）；多轮未出现保持开放并显示"N 轮未复现"，人工可确认关闭。
- **状态机**：`OPEN → ANALYZED → FIXING → PENDING_REVIEW → TESTING → FIXED`；拒绝 `REJECTED → OPEN`；复发 `FIXED → OPEN(REOPENED)`；人工关闭 `CLOSED`。

## 2. 数据模型（迁移一张表）

`pm_agent_issue`：id、task_id、agent_id、business_line_code、environment_code、domain(SYSTEM_ALERT/BUSINESS_MONITOR/ACCOUNTING)、signature(唯一 task+signature)、title、severity(P0/P1/P2)、status、analysis_json（五要素：codeLocation/repro/rootCause/impactVerification/fixDirection + system/repo 定位）、evidence_json（本轮证据引用）、first_run_id、last_run_id、first_seen_at、last_seen_at、occurrence_count、unseen_rounds、fix_json（branch、repos、commit、mr_url、diff_summary、fix_run_id）、review_json（reviewer/decision/note/at）、deploy_json（test/prod 状态与 Jenkins 回执）、reopen_count、created/updated/version。

## 3. 巡检深查（决策③落地）

- 平台在启动巡检 CLI 前，按业务线 GitLab 组**浅 clone**（--depth 1）涉及仓库到 CLI 工作区 `repos/` 子目录（复用 `sync_gitlab_group_repositories` 的组名映射；clone 用现有 GitLab 凭据）。
- 提示词（agent.cli.step v5 或职责引导）：对新出现 P0/P1，在 `repos/` 中定位代码，产出五要素；P2 与复发问题不定位。结果 schema 扩展：`{"type":"RESULT","summary":"...","problems":[{domain,title,severity,signature,system,evidence,analysis{...}}]}`（summary 兼容现有通知/展示；problems 进入平台解析）。
- 结果解析：`AgentRunStore.finish` 完成后由 `AgentIssueService.ingest(run)` 解析 problems、按去重键 upsert 工单（新问题→深查结果入库；复发→聚合；FIXED 复现→重开）。

## 4. 修复执行（去修复）

- 入口：前端勾选工单（单/多）→`POST /api/agent-issues/fix`{issueIds}。前置：所选工单 ANALYZED/OPEN/REJECTED 且分析含 repo 定位；权限 agent:manage。
- 管线（`AgentFixService`）：独立目录 clone 涉及仓库（按分析 repo 列表）→ 建分支 `fix/{issueKey}-{seq}` → 组装修复任务（选中工单五要素+修复方向；CLI 工作区=该目录，职责=修复开发规范）→ 走现有 Agent 运行管线执行 → 完成后校验有提交 → 推送分支 + 建草稿 MR（复用 AgentGitLabClient）→ 工单转 PENDING_REVIEW，fix_json 记录分支/MR/差异摘要 → 站内信通知审核人。
- 修复运行复用 pm_agent_run（specification=修复目标），fix_run_id 关联；失败回 OPEN。

## 5. 工单审核与两段式发版

- `PUT /api/agent-issues/{id}/review`{decision: APPROVE/REJECT, note}（agent:manage）：APPROVE→合并 MR（复用 GitLab 客户端）→ 触发 Jenkins 部署 TEST（复用 AgentJenkinsClient 与发布绑定/守卫）→ TESTING；REJECT→REJECTED（note 必填）→ 人工可重新发起修复。
- `POST /api/agent-issues/{id}/deploy-prod`（人工确认 TEST 通过后点击；agent:manage）：触发 Jenkins PROD → 回执成功→FIXED。
- 状态变化均写审计+站内信。

## 6. 前端

- 运行详情"结果与产物"：文本报告 + 三 Tab（按 domain）问题卡片（级别色标、五要素折叠、证据、工单状态徽标、勾选框；跨 Tab 保持勾选；底部"去修复(N)"）。
- 新路由 `/agent-issues` 工单中心：列表（域/级别/状态/业务线筛选）、详情（五要素、证据、修复 diff 摘要+MR 链接、审核操作、TEST/PROD 发布按钮）。
- API：issues list/detail、fix、review、deploy-prod。

## 7. 安全与边界

- clone/推送/MR/Jenkins 全部走既有凭据与守卫；PROD 仅人工触发；修复不碰本机 IDEAWorkspace；工单操作全审计。

## 8. 验证

- 单测：去重聚合/重开/状态机、ingest 解析、fix 管线（fake git/jenkins）、review 流转。
- 集成：一条业务线真实巡检产出工单→修复到 MR（测试仓库或 dry-run）→审核 TEST。
