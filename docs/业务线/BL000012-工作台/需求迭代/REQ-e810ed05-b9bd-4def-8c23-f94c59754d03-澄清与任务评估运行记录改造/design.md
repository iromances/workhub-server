# Design：澄清与任务评估运行记录改造

| 项目 | 内容 |
| --- | --- |
| 需求编码与唯一需求目录 | REQ-e810ed05-b9bd-4def-8c23-f94c59754d03；`docs/业务线/BL000012-工作台/需求迭代/REQ-e810ed05-b9bd-4def-8c23-f94c59754d03-澄清与任务评估运行记录改造/` |
| 涉及仓库与功能域 | 沿用 Proposal：workhub-server（intake 澄清/研发拆解链路）、workhub-web（需求录入列表页） |
| 所属项目或业务线 | BL000012-工作台 |
| 对应 Proposal 版本 | v1（2026-11-12） |
| 状态 | v1 已确认，随实施更新 |
| 当前事实核对环境、分支、时间 | workhub-server `codex/development-20261102`（2026-11-12 本地运行实例 pid 15434 + 运行库 10.10.116.32:3306/workhub 只读核查）；workhub-web 本地工作区 |

## 1. 现状与证据

- 澄清服务：`workhub-service/src/main/java/cn/aslight/workhub/service/intake/IntakeClarificationAnalysisService.java`
  - `analyze()` L84-109：in-progress 且非 stale → 返回旧行；否则 `upsertExecutionState`（L224-249：无行 insert / 有行 update 覆盖）后 `workhubTaskExecutor.execute(...)`，提交无任何异常捕获。
  - `executeAnalysis()` L167-212：先 `requireAnalysisEntity` + `updateExecutionState(RUNNING)`（try 块外，异常即线程死亡且无留痕）；try 内 `catch Exception` → FAILED；无 Error 兜底、无日志。
  - `isStaleExecution()` L339：`PENDING/RUNNING` 且 `updatedAt + 30min < now`。
  - `toResponse()` L350：`IntakeClarificationAnalysisResponse(id, intakeId, status, message, items, createdAt, updatedAt)`。
- 研发拆解服务：`workhub-service/.../intake/DevelopmentAnalysisService.java`
  - `analyze()` L245-277：`existing in-progress → 直接返回旧行`（无 stale 通道，L256）；其余与澄清同构；`executeAnalysis()` L628-683 同构；内部 `NoopIntakeClarificationAnalysisMapper` L165-187 实现 `IntakeClarificationAnalysisMapper` 全部方法。
  - `chat/updateDraft/updateOwners/confirm/retryKnowledgeArchive` 均以 `findLatestByIntakeId` 为最新活跃行。
- Mapper：`workhub-dao/.../intake/IntakeClarificationAnalysisMapper.java`（insert 带 `@Options(useGeneratedKeys)`、`findLatestByIntakeId ORDER BY id DESC LIMIT 1`、`update/updateExecutionState`）；`DevelopmentAnalysisMapper` 同构。
- 前端：`workhub-web/src/views/intake/IntakeListView.vue` `isClarificationInProgress` L1976-1978、`isDevelopmentAnalysisInProgress` L2803-2805（均 `PENDING || RUNNING` 即禁用）；澄清按钮 L4257-4265；类型 `src/types/work-item.ts` L417-425（DevelopmentAnalysisResponse）、L439 起（IntakeClarificationAnalysisResponse）；API `src/api/intake.ts` L137-164。
- 数据证据（2026-11-12 运行库只读查询）：`pm_intake_clarification_analysis` 25 条 PENDING（含 intake 271/275/277 今日 11:57-11:59 创建、超 1 小时无变化）、`pm_intake_development_analysis` 1 条 PENDING；线程 dump 中 `workhub-async` 线程空闲且 3 条任务从未进入 RUNNING。

## 2. 系统架构

总体方案：把"分析运行"从**单行状态槽**改为**追加式运行记录**。每轮运行一行（id 自增即轮次），生命周期 `PENDING → RUNNING → DRAFT/CONFIRMED/FAILED`；新轮启动时显式废弃旧 in-flight 行；所有失败路径兜底落 FAILED + history + 日志；前端重试判定只消费后端下发的 `retryable`。

| 模块/系统 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| IntakeClarificationAnalysisService | 运行记录生命周期、失败兜底、retryable、历史查询 | 澄清项业务语义（reply 规则）、状态机 | HTTP / DB / notes 归档 | Mapper、executor、KnowledgeBaseService、CodexCli generator |
| DevelopmentAnalysisService | 同上（任务评估侧） | 工作项创建规则（confirm 保持现状） | HTTP / DB / notes 归档 | 同上 + WorkItemService |
| IntakeController | 澄清历史端点透出 | — | GET history | 两个 Service |
| Flyway 迁移 | 存量 PENDING/RUNNING 超时行置 FAILED | 表结构 | DB | 无 |
| workhub-web | retryable 判定、历史列表展示 | 后端 stale 口径（不在前端复算） | HTTP | 后端契约 |

流程：`开始澄清/重新分析` → [防重检查：最新行 in-progress 且未 stale → 返回] → [归档旧 items 到 notes] → [failInFlight 旧行置 FAILED] → [insert 新行 PENDING] → [提交 executor；拒绝→新行 FAILED+history+抛出] → `executeAnalysis`（log 开始 → RUNNING → 生成 → DRAFT/CONFIRMED → 归档+history；`catch Exception`/`catch Throwable` → FAILED+log+history）。

## 3. 契约与数据模型

- **响应契约（修改）**：
  - `IntakeClarificationAnalysisResponse` 增加 `boolean retryable`；`DevelopmentAnalysisResponse` 增加 `boolean retryable`。构造点：两服务 `toResponse`（澄清 1 处、研发拆解多处私有 toResponse 统一收敛）。
  - 语义：无记录 → `true`（可首次分析）；`PENDING/RUNNING` → `isStaleExecution`（updatedAt + 30min < now）；`DRAFT/CONFIRMED/FAILED` → `true`。前端仅在 `retryable === false` 时禁用；字段缺失（旧后端）回退 `status PENDING/RUNNING` 判断。
- **新端点**：`GET /api/intake/{id}/clarification-analysis/history` → `ApiResponse<List<IntakeClarificationAnalysisResponse>>`，按 id DESC 返回全部轮次（含 items）；权限沿用该控制器现有澄清端点配置。
- **新 Mapper 方法**：
  - `IntakeClarificationAnalysisMapper.failInFlightByIntakeId(intakeId, message, updatedBy)`：`UPDATE ... SET analysis_status='FAILED', analysis_message=#{message}, updated_by=#{updatedBy}, updated_at=CURRENT_TIMESTAMP WHERE intake_id=#{intakeId} AND analysis_status IN ('PENDING','RUNNING')`；
  - `IntakeClarificationAnalysisMapper.listByIntakeId(intakeId)`：同 findById 列 + `ORDER BY a.id DESC`（含 business_line 联接与 items JSON 处理，保持一致）；
  - `DevelopmentAnalysisMapper.failInFlightByIntakeId(...)` 同构（不含 items_json 覆盖，按该表实际列）。
- **幂等/并发**：analyze 防重（fresh in-progress 直接返回）覆盖双击并发；两个并发请求同时越过防重窗口时最多多插一轮 PENDING，旧行废弃语句按状态条件幂等，无害。`executeAnalysis` 仅操作自己 `analysisId` 行，互不干扰。
- **迁移（新增，不改表结构）**：`V20261112_120000__fail_stale_intake_analysis_runs.sql`
  ```sql
  UPDATE pm_intake_clarification_analysis SET analysis_status='FAILED',
    analysis_message='后台执行中断（历史遗留），已由数据修复置为失败，可重新分析', updated_by='migration'
  WHERE analysis_status IN ('PENDING','RUNNING') AND updated_at < NOW() - INTERVAL 30 MINUTE;
  -- pm_intake_development_analysis 同构一条
  ```
  可重复执行（条件命中即改，不重复破坏 FAILED 行）；回滚不必要（仅把卡死行置失败，业务无损）。
- **历史兼容**：`findLatestByIntakeId` 语义不变，`DevelopmentAnalysisService` 对澄清行的引用、notes 归档（archiveAfterCommit + analysisId）、`reply()` 的 in-progress 拒绝均保持。

## 4. 非功能约束

- 失败处理：三层兜底——提交拒绝（RejectedExecutionException → 新行 FAILED+history+重抛）、try 内 Exception（现状）、try 内及启动前 Throwable（新增兜底；若 `requireAnalysisEntity` 本身失败无法更新行，则 log.error + history 兜底，不再向上传播到 executor 吞掉）。
- 日志：两服务新增 SLF4J logger，记录 analysisId/intakeId/开始/结束耗时/失败原因，解决"静默丢失不可诊断"。
- 安全/权限：新端点沿用控制器既有认证与权限注解；迁移不触碰业务数据内容。
- 性能：运行记录按轮次追加，单需求轮次量级为个位到十位，`intake_id` 已有普通索引，无需新索引。

## 5. 交付与回滚

- 发布顺序：后端先发（含迁移，重启即应用）→ 前端后发；后端先发期间前端走回退分支（无 retryable 字段时按 status 判断），行为兼容。
- 回滚：代码回滚即可；迁移无回滚需求（FAILED 行可重试，等价改造前的人工修复结果）。
- 生效后更新：`docs/业务线/BL000012-工作台/现状.md`、`迭代日志.md`、本目录 tasks 验证记录、`docs/notes.md` 过程记录。

## 6. 一致性与待确认

- AC-01~07 与 Design 的承接：AC-01/02/03 → §2 流程与 §4 失败处理；AC-04 → §3 响应契约；AC-05 → §3 新端点 + 前端历史列表；AC-06 → §3 迁移；AC-07 → §2 研发拆解同构改造。
- 明确不做项保持：未触碰 IntakeAiDevelopmentService、executor 参数、状态机与 reply 规则。
- 待确认：无（规格已经用户 2026-11-12 确认后实施）。
