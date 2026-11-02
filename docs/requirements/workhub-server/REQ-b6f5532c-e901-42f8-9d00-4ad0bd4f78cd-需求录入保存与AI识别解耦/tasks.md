# Tasks：需求录入保存与 AI 识别解耦

## 1. 元信息

- 需求编码：REQ-b6f5532c-e901-42f8-9d00-4ad0bd4f78cd。
- 唯一需求目录：当前目录；[Proposal v1](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-b6f5532c-e901-42f8-9d00-4ad0bd4f78cd-需求录入保存与AI识别解耦/proposal.md>)、[Design v1](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-b6f5532c-e901-42f8-9d00-4ad0bd4f78cd-需求录入保存与AI识别解耦/design.md>)。
- 目标：WorkHub；server codex/dev-20260820，web codex/dev-20260707。
- 状态：用户已统一确认完整规格 v1，实施完成、自动化已验证；运行数据库与页面联调待验证。
- 来源：2026-10-08 本次用户要求需求先保存、AI 失败后可重试。

## 2. 规格确认记录

| 日期 | 范围与版本 | 确认人 | 结论 |
| --- | --- | --- | --- |
| 2026-10-08 | proposal/design/tasks v1；任务提交拒绝隔离、AI 失败保留已保存记录、同 ID 重试、页面反馈及事务验证 | 用户（本次聊天） | 用户回复“执行吧”，统一确认并授权实施 |

## 3. 任务与依赖

| 任务 | 交付物 | 前置 | 对应验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 提交失败隔离、独立 FAILED 状态事务与后台开始阶段失败处理 | 完整规格确认 | AC-01/02/03/06 | 已完成 |
| T-02 | 同记录重试与事务边界自动化测试、既有服务/控制器回归 | T-01 | AC-01/02/03/04/06 | 已完成，自动化通过 |
| T-03 | 上传保存成功与可重试识别的页面提示、前端构建 | 完整规格确认 | AC-05 | 已完成，构建通过；页面联调待验证 |
| T-04 | 验证证据、系统设计与迭代日志更新，用户运行环境联调记录 | T-02/03 | 全部 | 文档已完成，运行联调待验证 |

不使用子代理；共享文件顺序修改。工作区既有修改继续保留。

## 4. 任务明细与验证计划（尚未执行）

### T-01：保证保存与 AI 提交故障独立

- 修改范围：IntakeEnrichmentService 及必要的现有服务依赖注入；保持 IntakeService 保存/重试流程与接口契约。
- 两个执行器提交入口统一捕获任务拒绝；提交拒绝后的失败回写独立事务提交，afterCommit 不传播可选 AI 提交故障。
- 将后台开始阶段纳入已有异常处理；保持失败记录和材料。
- 验收：AI 不在保存提交前执行；拒绝时已有数据保留且数据库可读到 FAILED；保存本身失败不伪装成功。

### T-02：失败与重试自动化验证

- 用模拟 Codex 启动失败、失败结果、异常与拒绝执行器覆盖实际边界，不发起真实 AI 推理。
- 用 Spring 事务设施验证提交、回滚、失败回写使用独立事务；不得仅用 insert 调用顺序证明数据已经提交。
- 确认 AI 故障无 delete、新需求 insert 或正式工作项创建；原文和附件关联不变。
- 重试覆盖同 ID 成功、再次失败、运行中禁止重试及操作历史。
- 计划运行既有 Maven 离线测试，实际命令和结果实施后填写；不得安装依赖，无法运行时报告具体缺项。

### T-03：保存反馈

- 修改范围：workhub-web/src/views/intake/IntakeListView.vue 的录入成功提示；按实际需要调整重试提交反馈。
- 上传成功明确“需求已保存”和识别失败可重试；不承诺 AI 已启动或已成功。
- 保留失败记录在列表、详情中的重试入口与已有权限；列表刷新失败保留独立加载提示。
- 计划执行现有依赖下的前端构建，静态核查提示分支；页面联调保持待验证直到实际完成。

### T-04：交付证据

- 更新需求文档状态、实际测试结果及限制；系统设计仅记录已核实实现。
- 迭代日志按设计、实施、自动化验证、运行联调分别记录实际状态。
- 联调计划：由用户运行 WorkHub，提交一条测试需求，在失效 CLI 配置下检查记录和材料仍在，再恢复有效配置并对同条记录重试；本任务不擅自修改运行配置或重启应用。

## 5. 实际结果与覆盖

- 2026-10-08 已完成只读代码核查及 v1 规格起草：现有录入已保存后异步识别，已有 FAILED 重试；发现 afterCommit 任务拒绝未处理、RUNNING 初始化位于异常处理范围外。
- 未查询运行数据库；未证明此前具体需求未入库，也未假定该次 Codex 错误由执行器拒绝引起。
- 起草规格阶段未实施代码或执行测试；用户统一确认后完成以下实施与验证。全过程未安装软件、未启动或重启 WorkHub。
- 全部验收条件已映射到任务；数据库故障复合场景与进程退出后队列恢复的限制见 Design，不将计划写成已完成。

### 2026-10-08 实施与自动化验证

- T-01：两个任务提交入口共用 submitEnrichment，捕获执行器拒绝后保存 FAILED；失败回写使用 REQUIRES_NEW 事务，数据库回写故障只记录日志，不把已经提交的保存结果改为失败。后台 RUNNING 初始化纳入识别 try 范围，识别异常复用失败回写。
- T-02：新增 IntakeEnrichmentTransactionTest 8 项测试，使用既有 H2、Spring DataSourceTransactionManager 和生产 MyBatis updateEnrichmentState SQL，独立数据库连接验证提交后的失败状态可见。上传与重试调用实际服务方法，由 TransactionTemplate 提供事务；MySQL JSON 展示查询、主记录写入、附件服务和成功识别回写使用精简 JDBC/模拟依赖，不代表运行 MySQL、实际附件存储或完整 Spring 应用装配联调。
- 8 项新增验证覆盖：主记录和材料提交前不启动识别；事务回滚不提交任务；附件保存失败明确报错并回滚；afterCommit 任务拒绝后 FAILED 独立落盘且同 ID 可重试；无调用事务时任务拒绝可回写；模拟 Codex 启动异常、再次超时、成功重试均保留同 ID、原文与图片字节；RUNNING 初始化异常转 FAILED；失败状态回写数据库故障不影响已提交记录。
- 回归命令：`mvn -o -q -pl workhub-controller -am '-Dtest=IntakeEnrichmentTransactionTest,IntakeEnrichmentServiceTest,IntakeServiceTest,CodexCliStructuredExtractorTest,IntakeControllerTest' -Dsurefire.failIfNoSpecifiedTests=false '-DargLine=-javaagent:/Users/aslight/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar' test`，退出码 0。共 69 项：68 通过，0 失败、0 错误、1 跳过（既有 RP/DOCX 样本用例未设置 workhub.rp.sample）；其中新增 8 项全部通过。service 51 项，controller 18 项；报告位于两模块 target/surefire-reports。
- 首轮测试遇到沙箱内 Mockito 动态附加不可用，使用已安装 Mockito 的 javaagent 参数重跑通过；没有安装依赖或修改 Maven 构建配置。
- T-03：前端只修改原有 submitCreate 成功提示为“需求已保存，AI 将在后台识别；识别失败后可重新识别”，保留同文件既有未提交修改。既有列表/详情失败重试入口、加载列表失败独立提示继续使用。
- 前端验证：在 workhub-web 执行 `PATH=/opt/homebrew/opt/node@22/bin:/opt/homebrew/bin:$PATH npm run build`，使用既有 Node 22 和 node_modules，TypeScript 检查与 Vite 构建通过，退出码 0。未运行开发服务器。
- 两仓库 `git diff --check` 通过。未修改数据库结构、运行配置或此前具体需求数据；无提交、发布或应用重启。
- AC-01/02/03/04/06 在上述限定测试环境验证；AC-05 保存提示已改、前端构建通过，页面实际交互待联调。持续数据库故障时失败状态可能无法落盘，已保存的内容保留，错误日志提示核查；进程退出的任务恢复未纳入本期。
- 运行联调计划仍待用户运行环境验证；本次未连接运行数据库，也未发起真实 AI 推理。
