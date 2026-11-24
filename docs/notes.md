# WorkHub 需求分析与排查笔记

## 2026-11-17 开发完成提测被拦截

需求 202611050003 自动开发后进入开发完成，提测服务 startReview/confirmAndSubmit 仍调用仅接受开发中的旧校验，而统一 IntakeDemandStatusRules 已支持开发完成。按用户明确授权修复两入口，统一复用 SUBMIT_TESTING 规则，补齐双状态及其他阶段无副作用回归，30 项全部通过。确认无在途数字员工运行后于 16:59 重启本机服务并核实启动成功。浏览器验证需要登录，未实际提交提测、建测试单或部署；服务成功路径与外部调用使用桩验证。规格归属 REQ-a71851db。

## 2026-11-17 数字员工完整 CLI 与需求 202611050003 交付

按用户确认恢复数字员工 Codex/ZCode 原生能力并追加 WorkHub MCP，修复空授权范围、设计完成触发和同任务重试状态同步。实际自动开发期间发现预算累计耗尽，以及原生 Git 已推送但旧清单不能验收；保留历史运行，补充平台独立 GitLab 分支提交核验。经独立浏览器续跑，15:29 运行 COMPLETED，15:30 需求自动同步开发完成。scs/scs-saps 研发分支 `feature/repay_plan_query_202611050003` 的远端提交为 `e7c66a14289955b26f6e263fefa361e73d1f52fa`，2 项自测通过；TEST 联调和部署未执行。第三次重启前临时暂停新派发并等待在途任务结束，重启后已恢复原 5000ms 调度。详见 [平台验证](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261117-原生CLI完整能力/验证说明.md)。

## 公司项目

### 2026-11-13 漏跑影响账单查询口径更正

- 用户明确要12、13日漏跑的全量候选，含已结清；原按补跑记录查947笔的口径不符。改为主表按创建、宽限和结清时间回溯：叉车60、智能柜900，合计960笔，含13笔已结清。已提供只读校验后的修正版SQL。另84笔已删除记录缺少删除时间，独立附查询供历史核对，不混算为确认影响；当前主表回溯不冒充完整历史快照。已结清不再补算的用户决定不变。

### 2026-11-13 14:50 叉车报表刷新验收及默认范围更正

- 用户执行Job38空参数成功，14:49:25重建1104条，默认融资类应有1104笔全部覆盖，无重复；逾期科目金额逐笔对源无差异，包含本次融资类58笔新增399.76元。更正此前“全范围”表述：空参数默认产品类型FORKLIFT，不覆盖租赁类2笔新增16.02元；这两笔费用此前已核验入账，不建议切换筛选条件重跑清表任务。验收证据已归档，报表刷新待办关闭。

### 2026-11-13 14:46 叉车两日逾期补跑验收

- 用户已补叉车13日，60笔、207.89元，逐笔金额与试算一致，逾期天数较补跑前累计加2；两日覆盖无缺失、无重复，合计415.78元。连同此前智能柜993.36元，两系统合计1409.14元、去重947笔。叉车报表仍为06:00快照，Job38空参数刷新一次仍待处理；仅只读验收，未执行报表任务。

### 2026-11-13 14:44 叉车12日逾期补跑验收

- 用户执行Job10的12日补跑成功。只读核验60笔账单、118条费用明细、207.89元；候选全覆盖，无重复，逐笔金额与此前试算一致，60笔逾期天数均增加1天。叉车13日及补算后Job38报表刷新仍待处理，已结清复核项保持关闭。

### 2026-11-13 用户关闭智能柜已结清账单复核项

- 用户明确“已结清的不管了”：本次13笔已结清账单不再复核或补算，理论6.67元不再列为待处理项。已记录到原生产Job补跑清单；未修改生产数据，智能柜两日实际补算993.36元的验收结论保留。

### 2026-11-13 14:41 智能柜两日逾期补跑验收

- 用户已执行13日补跑，14:41:11–14:41:13 成功，887笔、497.46元；连同12日合计去重887笔账单、1770条费用、993.36元。两日当前候选全覆盖，无重复和逐笔金额差异。助手仅只读验收，已归档证据；已结清复核项与回购/代偿联动核对保留。

### 2026-11-13 14:39 智能柜12日逾期补跑验收

- 用户已执行 Job 43 的12日补跑；只读核验 14:37:38–14:37:40 执行成功，883 笔费用共495.90元，当前候选全部覆盖，缺失、重复及逐笔金额差异均0。13日尚无费用记录，已结清复核项保留；证据已补入原生产Job补跑清单。

### 2026-11-13 14:19 叉车与智能柜漏跑逾期费金额核查

- 只读试算 12、13 日补跑：叉车 60 笔、415.78 元，智能柜 887 笔、993.36 元；去重共 947 笔、1,409.14 元，其中叉车 7 笔不新增费用。另智能柜已结清 13 笔理论 6.67 元单列复核，未执行补跑或修改生产数据。
- 结果与证据补充至[生产 Job 补跑清单](业务线/BL000006-物流平台/需求迭代/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/叉车与智能柜生产Job补跑清单_20261113_1405.md)。

### 2026-11-13 · 叉车和智能柜生产 Job 补跑清单复核（14:05）

只读核对新旧调度库和业务账务：确认此前按启用任务统计的清单遗漏两个子任务——叉车 Job 10、智能柜 Job 43；两者 11 月 12、13 日在新旧中心均无触发，费用明细同样空缺，需各按 executeDate 补两日，共 4 次。代扣批次、叉车对账单逾期费及智能柜核销报表已实际执行，不整体重跑。智能柜另有 13 笔缺口期间已结清账单，默认 Job 会跳过，需单独核对费用；补算后刷新叉车报表并复核回购、代偿触发条件。两天保证金抵扣当前无候选；lease-finance 回调仍异常但已查三类业务对象无新增积压。仅形成清单，未执行补跑或修改生产数据。

[补跑清单与逐笔复核项](业务线/BL000006-物流平台/需求迭代/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/叉车与智能柜生产Job补跑清单_20261113_1405.md)。

### 2026-11-12 · 研发拆解 ZCode 空转根因：仓库工具未注册（REQ-809c8dd3 追加）

277 两轮研发拆解均空转（workItems 空、summary 为计划文本、审计零工具调用）。定位：`workhub.mcp.repository-read-roots` 未配置 → RepositoryFileService 未启用 → 仓库三工具未注册进 MCP runtime → ZCode 模型看不到工具；提示词却要求用它们检索代码。修复：application.yml 配置 read-roots（env 可覆盖）+ isConfigured 校验 list_repository_files 已注册。详见 REQ-809c8dd3 tasks 执行记录。

### 2026-11-12 · 澄清生成提示词收紧（REQ-e810ed05 后续变更③）

用户问"列出的 10 个待确认项 AI 自己无法回答吗"，先提"AI 给建议答案人工采纳"方案被否；定向改为收紧生成准入：只有综合需求材料、结构化字段、知识库和代码查证后仍无法确定唯一答案（不能是推测）且答案实质影响实现行为的问题才允许提问；风险只列有具体位置依据的现实冲突，假设性风险不列；宁缺毋滥、通常不超过 5 项、可返回空数组。落地为迁移 V20261112_161000 更新 `intake.clarification.analyze` 模板（checksum 幂等，变量白名单不变；期间发现与另一在途迁移 V20261112_160000__agent_platform_guide 版本号撞号，改 161000 避开 Flyway 冲突）。PromptTemplateMigrationConsistencyTest 2/2。生成质量待重启迁移后对真实需求实测；若仍偏多，下一步可查生成模型与温度/推理档位配置，或在 normalize 侧加质量过滤（暂不做）。

### 2026-11-12 · ZCode 通道澄清分析"AI 场景未配置或已停用"排查与准入放开（REQ-809c8dd3-d7d6-4bb0-8c03-44a2c78f4c37）"AI 场景未配置或已停用"排查与准入放开（REQ-809c8dd3-d7d6-4bb0-8c03-44a2c78f4c37）

用户把澄清分析切到先行通-GLM CLI（ZCode）通道后执行报"AI 场景未配置或已停用"。只读核查运行库：9 个 AI 场景 8 个已绑 ZCode 且全部启用，配置无缺失；根因是 `DefaultAiGatewayClient.isConfigured` 对 ZCODE+LOCAL_REPOSITORY_READ 无条件 false，而执行链路（executeCli ZCODE 分支经 workhub MCP 只读工具桥接仓库读取、repositoryHint 注入授权目录）早已实现并有测试覆盖——准入检查与执行能力脱节，用户明确"就是要切换通道用"。修复：isConfigured 改按真实前置条件（access-token 已配置 + MCP 运行时工具清单非空），缺失仍 false 且执行期抛明确报错；仓库读取类场景仍仅限 CLI 通道（API 侧 function-calling 桥接另立需求）。网关 18/18 测试通过。部署前提：本地/运行环境配置 WORKHUB_MCP_ACCESS_TOKEN（application.yml 默认空），重启后实测一次 ZCode 澄清分析。同日完成 REQ-e810ed05 澄清/任务评估运行记录改造（见迭代日志），两者联动：解锁按钮后即可用 ZCode 通道重跑 271/275/277。

### 2026-11-12 · 澄清分析静默卡死排查与运行记录改造（REQ-e810ed05-b9bd-4def-8c23-f94c59754d03）

用户反馈 3 个在澄清需求（271/275/277，2026-11-12 11:57-11:59 开始澄清）长时间无澄清项。排查：`pm_intake_clarification_analysis` 三条 PENDING 且 updated_at==created_at、从未 RUNNING/FAILED、无"生成需求澄清项"流水、线程 dump 中 workhub-async 线程空闲且无线程在分析代码、窗口内无 ERROR——后台任务提交后静默丢失；同类僵尸 PENDING 自 2026-05 累计 20+ 条。"重新分析澄清项"按钮灰系前端 `isClarificationInProgress` 只要 PENDING/RUNNING 即禁用，封死后端 30 分钟 stale 重发通道；研发拆解分析更无 stale 通道。经用户确认规格后实施改造：每轮澄清/任务评估独立插入运行记录（旧行显式置 FAILED"已被新分析取代"），executeAnalysis 加 SLF4J 起止日志、catch Throwable 兜底与 executor 拒绝兜底；响应新增 retryable（PENDING/RUNNING 超 30 分钟或终态为 true）供前端判定；新增澄清历史接口与页签折叠列表；迁移 V20261112_120000 修复存量。两服务单测 25 项通过、主代码编译与前端构建通过；workhub-service 3 个 ops 测试编译错误（在途 MQ 改动，验证期间临时移出并字节级原样恢复）、KnowledgeArchiveWorkflowTest 1 项存量 wiki 路径断言失败（在途 ProjectKnowledgeBaseService 改动所致，未修改）、workhub-controller AgentToolControllerTest 编译错误（在途 agent 域改动），全量回归待其在途改动收敛。服务重启应用迁移、271/275/277 重试澄清端到端走查待部署。规格与验证见需求目录 tasks.md。

### 2026-11-04 · 智能柜与叉车整体升级及测试配置核查（15:47）

用户要求注册之外的整体改造与测试 XXL 配置。刷新全部 38 仓库，所选 SHA 与 11:57 一致。自有浏览器只读核查 lease 空间 30 条配置目录和 common.yaml＋11 应用详情：公共中心 .52:28080、Token 非空、AppName 使用 spring.application.name、port=-1、日志 /mnt/xxljob，但 logretentiondays=30；ol-risk 显式覆盖90，ol-order 单独中心127.0.0.1:28080及Token（与公共一致）；其余9应用无独立xxl覆盖。Token原文仅内存比较，未写报告。补查发现ol-risk源码／远程配置未见公共加载声明，不能直接断言其中心地址／Token已继承。智能柜test存在.53／.51目标差异，叉车9仓库test仍本机8848且无启动鉴权；详细边界见报告。切换XXL任务筛选时ERR_EMPTY_RESPONSE，未完成迁移任务逐组对账。代码入口仍59/59与46/71；配置、制品、调度、业务与回退仍待验收，未执行修改。

[整体复核报告](业务线/BL000006-物流平台/需求迭代/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/智能柜与叉车整体升级及测试配置核查_20261104_1547.md)。

### 2026-11-04 · 自有浏览器核对设备应注册未注册项（13:45）

用户登录自有浏览器 XXL-JOB 后，本轮两套 .52 控制台均使用 Codex 内置浏览器读取。XXL 点击搜索并核对两页全部 11 个定义，只有 ol-order／ol-risk／ol-saps 各在线 1；叉车 6 个已建分组均无在线地址，intfcommon 未见分组。Nacos 关闭隐藏空服务，lease 7 条中 6 个智能柜服务健康 1/1，lease-payment 为 0/0；其他命名空间仅 auth 有 xxt-authing。按已核实升级范围列出 Nacos 明确 19 项缺口（ol 2、lease 17），XXL 明确 7 项缺口（全 lease）。lease-authing 映射、lease-risk 的 XXL 适用性、lease-scaffold 范围单列待核。此次未触发任务或修改配置。

[明确缺口清单](业务线/BL000006-物流平台/需求迭代/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/智能柜与叉车应注册未注册清单_20261104_1345.md)。

### 2026-11-04 · 设备新平台注册复核及 .52 环境确认（13:35）

用户明确叉车、智能柜的 Nacos 和 XXL-JOB 都使用 .52，关闭此前 .52/.53 归属待核。用户登录内置浏览器 Nacos 后，关闭隐藏空服务并查询全量 6 个命名空间：lease 7 个健康服务（ol 6、lease-payment 1），auth 另 xxt-authing，其他空间为空。XXL 新标签页未登录，使用 Safari 已有 .52 登录会话刷新两页全部 11 个分组，在线 ol-order／ol-risk／ol-saps／lease-payment 各 1；lease-saps 本轮无在线地址，且 Nacos 未见。lease-opgateway 本轮 Nacos 也未见。只读匿名 API 返回 403 后使用控制台，未进行写操作或触发任务。全量三列矩阵及证据已同步两业务线现状。

[注册复核报告](业务线/BL000006-物流平台/需求迭代/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/智能柜与叉车新平台注册复核_20261104_1335.md)。

### 2026-11-04 · 智能柜与叉车升级进度复查（11:57）

用户要求重查设备，沿用智能柜／叉车测试环境，排除弃用 ol-payment。38 仓库远程分支刷新全部成功，所有选定 SHA 与 10:55 一致；任务文件 blob 与已验证入口清单一致，智能柜 59/59、叉车 46/71，剩余 25（contract 9、intfcommon 11、marketing 4、crm 1）。本轮 .52 Nacos 的 lease 空间有 9 个健康服务：智能柜 6、叉车 3；新增 ol-partner-gateway／lease-opgateway／lease-payment／lease-saps，lease-contract／lease-intfcommon 本次未见。XXL 仍 5 个业务名称在线（智能柜 3、叉车 2）。.53 关系、映射、部署 SHA 和任务执行仍未核实。已同步两业务线现状和三列全量矩阵；本次未编译、安装、部署或触发任务。

[本轮报告](业务线/BL000006-物流平台/需求迭代/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/智能柜与叉车升级进度复核_20261104_1157.md)。

### 2026-11-04 · 物流平台升级进度重新核验（11:51）

刷新全部 54 仓库远程分支及 .110 新平台。saps-back 新增升级分支、父依赖 0.0.2 和 sit 新地址／鉴权；WMS 替换占位地址；运营网关修正应用名；public 原升级分支与 v2 源码树相同。6 个原 feature 分支已删除，明确读取当前统一 release，未覆盖工作区。所有已记录业务任务文件 blob 未变化，仍为 214/256、剩余 42；CRM 3 个兼容问题未修复。Nacos 从 17 增至 22 个健康服务；XXL 从 6 增至 9 个在线名称／10 个地址，新增 CRM、江苏、场景名称。SAPS/checker 和场景多部署归属仍待核。WorkHub MCP 不可用，未取得新的进程／日志／部署 SHA。本轮未触发任务、部署或安装依赖。

[本轮报告](业务线/BL000006-物流平台/需求迭代/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/物流平台升级进度复核_20261104_1151.md)。

### 2026-11-04 · 全量注册清单简化为三列

按用户最新要求，当前全量清单统一为“子系统、新 Nacos、新 XXL-JOB”三列，状态仅用“已注册／未注册／不适用”。在线实例／执行器标已注册；本次未见在线项（含只有分组没有在线地址）标未注册；无需接入及已弃用 ol-payment 标不适用。lease-authing 映射、lease-risk 启动模块和 lease-scaffold 适用性等缺口移至表外说明，避免把未注册误当作必须整改。38 个仓库全量保留，仍使用 10:55～10:57 的 .52 快照，本次未重新查询平台。

### 2026-11-04 · 全量注册清单用语统一

按用户要求，将当前全量清单中无需接入的 Nacos 项统一写“无需注册”，无需执行器／调度的 XXL-JOB 项统一写“无需调度”，依据保留在备注。智能柜／叉车现状及 10:57 复核报告已同步，覆盖 38 个仓库；lease-risk、lease-scaffold 的待核项保留。本次仅调整展示用语，未重新查询运行状态，仍使用 10:55～10:57 的 .52 平台快照。

### 2026-11-04 · 全量子系统注册适用性复核（10:57 快照）

用户要求重查“不适用”口径。本轮重读桌面主方案、lease-public 清单及智能柜／叉车专项方案，刷新全部 38 仓库远程分支；8 仓库有新提交，按固定 SHA 从原库或临时隔离对象库读取，未切换业务工作区。复核 POM 实际依赖（与 dependencyManagement 区分）、任务入口、执行器装配及内部公共组件传递路径。7 前端明确无需独立注册；公共升级构件由宿主注册；15 个后端改为“未见分组；源码无调度接入”；common-order／ol-intf-junxin 按方案仅接口联调。lease-risk 标准 start 存在执行器装配链，start-backend 路径不同，实际部署模块待核。

10:55～10:57 刷新 .52 新平台：Nacos 全部 6 个可见命名空间，业务服务仍为智能柜 5 个、叉车 2 个健康实例；XXL-JOB 全部 11 个分组中，ol-order／ol-risk／ol-saps／lease-payment／lease-saps 各在线 1。lease-contract／lease-crm／lease-finance／lease-marketing 分组无在线，lease-intfcommon／lease-risk 未见分组。与 10:03 相比，lease-payment 和 lease-saps 的 XXL 状态由无在线变为在线；Nacos 本次仍未见二者实例，不假设两侧状态必须相同。

.53 测试登记入口与 .52 的关系仍未核实，WorkHub MCP 本轮不可用，不能把 .52 的“未见”推广到全测试环境。详细矩阵、方案证据和 38 仓库 SHA 见[本轮复核](业务线/BL000006-物流平台/需求迭代/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/全量子系统注册适用性复核_20261104_1057.md)，当前摘要同步至智能柜／叉车现状。本轮未触发任务或修改运行配置，未安装依赖。

### 2026-11-04 · 从全量子系统标记新平台注册状态（10:03 快照）

用户澄清要从全部子系统逐项标记 Nacos／XXL-JOB 注册状态。本轮通过 Safari 刷新 .52 新平台，核对 Nacos 全部 6 个可见命名空间和 XXL-JOB 两页全部 11 条分组；按已核实项目组全量清单覆盖 ol 16 个、lease-saas 22 个仓库。逐项区分健康实例、无在线地址、未见分组、不适用、弃用和源码不足待确认，已写入[智能柜现状](业务线/BL000005-智能柜/现状.md)与[叉车现状](业务线/BL000008-叉车/现状.md)。

核查时段 2026-11-04 10:00～10:03（北京时间）。Nacos 的 lease 空间仍有 7 个健康服务，但相较 09:55 内容有变化：ol-order、ol-risk、ol-saps、ol-gateway 仍在，新增 ol-opgateway；lease-contract、lease-intfcommon 仍在，lease-saps 本轮未见。auth 空间另有 xxt-authing，不能未经映射确认就算作 lease-authing；public、mall、sit、sit-etc 未见非空服务。页面启用了隐藏空服务，负向状态统一写“未见实例”。

XXL-JOB 本轮仅 ol-order、ol-risk、ol-saps 各有 1 个在线地址；lease-saps 已从 09:55 的在线变为当前分组无在线地址，其余叉车已建分组同样无在线地址。lease-intfcommon、lease-risk 未见同名分组，不从分组列表反推原始 registry 中一定不存在未绑定心跳。lease-saps 状态变化原因未核实，不据此声称升级失败或回退。

本次未触发任务、修改配置、执行部署或安装依赖。原始矩阵暂存 `/private/tmp/workhub-upgrade-progress-20261104/registration-matrix-1003.json`，.52 与前次 WorkHub .53 入口的对应关系仍待核实。

### 2026-11-04 · 新 Nacos 与 XXL-JOB 注册情况补查

记录时间：2026-11-04 09:55（北京时间）。用户追问新平台注册情况，范围仍为智能柜和叉车测试环境，排除弃用的 ol-payment。WorkHub MCP 工具仍不可用；本轮通过 Safari 已登录控制台刷新页面，取得新的运行证据。实际读取入口是 **10.10.116.52**：Nacos `:28848/nacos`（页面标识 2.5.4、standalone），XXL-JOB `:28080/xxl-job-admin/jobgroup`（页面标识 2.5.0）。此前 WorkHub 登记的 .53 入口本轮只到达登录页，未取得其注册数据；不将 .52 和 .53 视为已证实的同一实例或地址映射。

Nacos 核对范围为 `lease` 命名空间（页面描述“设备租赁”，ID `e409079b-9077-4ddf-80fe-8c356f96b16f`）、DEFAULT_GROUP，服务名筛选为空。刷新后页面总数 7、共 1 页，以下服务均为 1 个实例、1 个健康实例。XXL-JOB 已读两页全部 11 条执行器分组，4 个分组各有 1 个在线地址。

| 服务 | 新 Nacos 实例／健康实例 | 新 XXL-JOB 执行器 |
| --- | --- | --- |
| ol-order | 1／1 | 在线 1 |
| ol-risk | 1／1 | 在线 1 |
| ol-saps | 1／1 | 在线 1 |
| ol-gateway | 1／1 | 未见对应分组；网关无本地业务 @XxlJob，不据此列为调度缺口 |
| lease-saps | 1／1 | 在线 1 |
| lease-contract | 1／1 | 分组已存在，在线地址为“无” |
| lease-intfcommon | 1／1 | 全部 11 条分组中未见同名分组 |

新 XXL-JOB 的 lease-crm、lease-finance、lease-marketing、lease-payment 分组也均显示在线地址为“无”；lease-risk 未见同名分组。另有已弃用 ol-payment 和示例执行器分组，均无在线地址，不计入本次升级完成数。智能柜其余 ol-intf-risk、ol-marketing、ol-opgateway、ol-partner-gateway 未出现在本次读取的 Nacos 命名空间列表中；不外推到其他命名空间或 .53 实例。

**最新结论：已确认部分应用接入 .52 新平台，Nacos 为智能柜 4 个、叉车 3 个健康服务；XXL-JOB 为智能柜 3 个、叉车 1 个在线执行器。** lease-contract／lease-intfcommon 的 Nacos 注册不能代表 XXL-JOB 接入完成。该运行结果补充了同日早先源码核查时的证据缺口；没有以远端源码未变化推断实际部署未变化。注册健康也不能证明部署包 SHA、实际客户端版本、任务启用、触发／回调或业务执行结果，本轮未核查这些项，也未触发或修改任务。

可复查入口：[Nacos 服务列表](http://10.10.116.52:28848/nacos/#/serviceManagement?dataId=&group=&appName=&namespace=e409079b-9077-4ddf-80fe-8c356f96b16f&pageSize=&pageNo=&namespaceShowName=lease&serviceNameParam=&groupNameParam=)、[XXL-JOB 执行器管理](http://10.10.116.52:28080/xxl-job-admin/jobgroup)。

### 2026-11-04 · 智能柜和叉车升级改造进度复查

沿用用户确认的智能柜 BL000005／ol、叉车 BL000008／lease-saas、测试环境，以及 ol-payment 已弃用并排除升级的范围。本轮没有可用的 WorkHub MCP 工具，不能安全刷新业务线环境映射或读取当前 DB／服务器状态；业务线来源沿用本会话此前核实结果。通过既有 GitLab origin 只读核对 30 个后端／公共／辅助仓库的远程分支，成功读取全部分支清单；lease-scaffold 仍无远程分支。所选提交对象均已存在本地，按远程 SHA 直接读取，没有切换、覆盖或改写业务工作树。

**源码结论：此前跟踪的 25 个应用仓库所选提交均与 11 月 3 日一致，未见新增已推送升级实现。** 升级分支仍为 `release/nacos-2.5.4-xxljob-2.5.0_20261027`；缺少该分支时读取 master 仅用于待改造范围清点。lease-public 升级提交仍为 `86e2721313671b452a85869c8188e71bee2b9dab`。

| 改造点 | 智能柜 | 叉车 |
| --- | --- | --- |
| 后端源码范围与依赖 | 排除 ol-payment 后，8／8 个仓库已有依赖改造 | 本轮补入 lease-bpm，范围由 17 修正为 18；7 个父依赖已改、CRM 仅配置部分改造、10 个未见升级分支 |
| 业务任务入口 | 59／59 个为无参 void；saps 32、order 24、risk 3 | 46／71 个已适配；剩 contract 9、intfcommon 11、marketing 4、crm 1，共 25 个 |
| 测试配置 | ol-order 的源码地址仍为 .51:8848，与昨日登记目标 .53:28848 不同，待核对发布覆盖 | saps、payment 仍为 127.0.0.1:8848 且未声明启动鉴权；CRM 父依赖与显式 schedule 仍旧 |
| 构建与部署 | 未重跑构建或联调；既有相同提交的 ol-saps 编译结论保留 | 未重跑构建或联调；既有相同提交的 saps／payment 编译结论保留 |
| 当前运行状态 | 本轮未核实；11 月 3 日调度快照不能作为今日状态 | 本轮未核实；新平台映射和实际切换仍缺今日证据 |

本轮补正两处前次范围／配置口径：① lease-bpm 的 root POM 引入 Nacos config/discovery，bpm-start 有独立启动类与 bootstrap，不能按纯类库排除；当前 master `56bbad8aeb42` 仍旧，配置仅见 dev／uat／prod，是否参加本次测试发布待确认。② lease-contract 前次记为测试地址的 `.24:8848` 属于 equip-test，叉车源码没有独立 test profile；实际发布使用哪个 profile 尚未核实。这些是核查口径补正，不是今日代码回退或新增改动。

逐仓库完整 SHA、源码状态和验证边界已按现行归档规则写入[智能柜迭代日志](业务线/BL000005-智能柜/迭代日志.md)与[叉车迭代日志](业务线/BL000008-叉车/迭代日志.md)。原始远程引用和脱敏清单在 `/private/tmp/workhub-upgrade-progress-20261104/remote-heads.json`、`inventory.json`。本轮只更新过程与进度记录，未修改业务代码、运行配置、任务状态或安装依赖。

### 2026-11-03 · 智能柜和叉车测试环境升级进度重新核查

范围：用户已确认的 **智能柜 BL000005／ol、叉车 BL000008／lease-saas，测试环境 TEST**。本次通过 WorkHub 重新获取环境上下文并安全刷新两个项目组共 38 个仓库的远程引用；未切换或覆盖业务工作区。数据库运行快照为当日 **17:22～17:27（北京时间）**。参照桌面《消费分期Nacos和XXLJOB升级迁移方案_20261028.md》v1.6 与《lease-public升级改动清单_20261102.md》，目标仍为 Nacos client 2.5.4、官方 XXL-JOB core 2.5.0 及受影响公共构件 0.0.2-SNAPSHOT。

用户随后补充：**ol-payment 已弃用，不需要升级**。据此将该仓库及其 8 个源码任务入口、7 条调度记录移出本次升级范围，不再作为升级缺口。以下范围统计已修正，运行数据仍沿用上述 17:22～17:27 快照，本次范围调整没有重新查询或操作运行环境。原始快照中的 ol-payment 心跳、3 条启用任务及新库遗留配置单列保留，不将用户确认的“已弃用”等同于已经完成运行资源清理。

**结论：智能柜升级范围内的 8 个后端仓库均已有依赖改造，59 个业务任务入口均已适配；配置和任务数据已进入新库，但测试配置、构建及运行验收仍有待核实项，调度尚未切换。叉车仍处于部分仓库改造阶段，旧调度中心仍有实时注册，新的目标平台尚缺受控环境映射，不能认定已完成迁移。** equip-battery-lease-server 不列入这两条业务线的必须升级应用；它对共享接口的影响沿用下节已确认的依赖范围，不混入完成数量。

代码统一读取 `origin/release/nacos-2.5.4-xxljob-2.5.0_20261027`；未找到升级分支的仓库读取 `origin/master`，仅作为待改造基线。实际测试部署分支和运行包 SHA 尚未取得，远程源码状态不等同于已发布。统计有效生产源码的 @XxlJob，排除注释、测试源码及公共 lease-mq 任务；同时计入 public、protected 等可见性的入口。“入口已适配”仅指签名为无参 void，不代表本轮已完成每个任务的参数、失败语义或业务回归验收。

| 改造点 | 智能柜 ol | 叉车 lease-saas |
| --- | --- | --- |
| 公共依赖与应用接入 | 范围内 8／8 个后端仓库已有依赖改造。ol-risk、ol-intf-risk 使用 thctay-dependencies 0.0.2；ol-marketing 独立管理 nacos-client 2.5.4，其余已改仓库使用 lease-dependencies 0.0.2 | 17 个相关后端仓库中 7 个已升父依赖；lease-crm 仅配置部分改造，父依赖和显式 schedule 仍旧；其余 9 个未见升级分支 |
| 业务任务入口 | 范围内 59／59 个无参 void：saps 32、order 24、risk 3；已弃用 payment 的 8 个入口不计入 | 共 71 个，46 个无参 void：saps 27、payment 6、finance 13；contract 9、intfcommon 11、marketing 4、crm 1，共 25 个仍旧 |
| 测试 Nacos 启动配置 | 8 个升级仓库已声明 config/discovery 鉴权；其中 ol-order 仍指向 10.10.116.51:8848，与 WorkHub 登记目标 10.10.116.53:28848 不同 | finance、gateway、intfrisk、opgateway、risk、crm 已指向 10.10.116.52:28848 并声明鉴权；saps、payment 虽已升依赖和任务，test 仍为 127.0.0.1:8848 且未声明鉴权；其余未升级仓库仍需迁移 |
| Nacos 配置迁入 | 10.10.116.53 的 nacos_config_254 中已有范围内 8 条应用配置，另有已弃用 ol-payment 的 1 条；仅确认存在，未宣称配置内容、共享配置及在线服务发现全部验收通过 | 当前受控测试库为 10.10.116.142；源码新地址为 10.10.116.52:28848，但 WorkHub 未登记新平台入口／对应新库，无法核验新平台迁入结果 |
| XXL-JOB 数据准备 | 范围内新 xxl_job_250 已有 40 条任务；与旧库逐条按 id 和 job_group 对齐，handler、Cron、参数差异均为 0，全部停止；另有已弃用 payment 的 7 条不计入升级范围 | 当前旧库共 53 条业务任务；没有已确认的新目标库用于迁移对照 |
| 执行器注册与切换 | 范围内新中心未见执行器注册，旧中心仍有 order、risk、saps 三个注册键实时心跳；范围内旧任务 11 条启用、29 条停止。payment 的旧心跳和 3 条启用记录单列为弃用后的遗留项 | 旧中心仍有 7 个注册键实时心跳；旧任务 2 条启用、51 条停止。不能用智能柜库中的历史 lease 分组代表本业务线已迁入 |
| 构建和验收 | ol-saps 同一 SHA 的既有 Java 8 离线编译日志已复核为 BUILD SUCCESS；其余本轮新增核查仓库未做构建／运行验收 | lease-saps、lease-payment 同一 SHA 的既有 Java 8 离线编译日志已复核为 BUILD SUCCESS；其余本轮新增核查仓库未做构建／运行验收 |

仓库数按本次升级范围内的相关后端源码仓库统计，lease-risk 的两个启动模块不重复计仓库；已弃用的 ol-payment、前端、无相关运行入口的辅助仓库和 lease-public 公共工程不加入应用分母。lease-public 升级远程提交为 `86e2721313671b452a85869c8188e71bee2b9dab`，有效 POM 确认 nacos-client 2.5.4、xxl-job-core 2.5.0；lease-mq 的一个公共入口也已适配，单列而不重复计入业务任务。桌面清单的“未推送”已不代表当前状态；本次未核实 Jenkins 制品发布情况。

**逐仓库源码进度与可复核提交**（`release` 指上述统一升级分支，`master` 指无升级分支时的基线）：

| 智能柜仓库 | 来源／提交 | 任务入口已适配／总数 | 当前源码状态 |
| --- | --- | --- | --- |
| ol-gateway | release／a587b0e69412 | — | lease 父 0.0.2，测试 Nacos 28848 与鉴权已改 |
| ol-intf-risk | release／1ea6d72489dd | — | thctay 父 0.0.2，测试 Nacos 28848 与鉴权已改 |
| ol-marketing | release／12af60a60d87 | — | 独立父工程，显式管理 nacos-client 2.5.4，测试配置已改 |
| ol-opgateway | release／8e00c52d4f0f | — | 切换 lease 父 0.0.2；bootstrap-test.yml 已改；含公共构件体系和 GatewayUtils 调整，尚未构建验收 |
| ol-order | release／5c551b746b8d | 24／24 | lease 父 0.0.2；测试鉴权已声明，地址仍为 10.10.116.51:8848，需核对目标与部署覆盖 |
| ol-partner-gateway | release／0b6bab523a67 | — | lease 父 0.0.2，测试 Nacos 28848 与鉴权已改 |
| ol-payment（已弃用，排除） | master／d9cd90f61461 | 不计入（原 8 个） | 用户确认不需要升级；旧源码仅保留核查证据，不列为改造缺口 |
| ol-risk | release／d13680588c28 | 3／3 | thctay 父 0.0.2，测试 Nacos 28848 与鉴权已改 |
| ol-saps | release／2b2781fd0e46 | 32／32 | lease 父 0.0.2，测试 Nacos 28848 与鉴权已改；同提交既有编译成功 |

| 叉车仓库 | 来源／提交 | 任务入口已适配／总数 | 当前源码状态 |
| --- | --- | --- | --- |
| lease-finance | release／c6b82f001d74 | 13／13 | 父 0.0.2，测试新地址与鉴权已改 |
| lease-gateway | release／2465df5cbcb3 | — | 父 0.0.2，测试新地址与鉴权已改 |
| lease-intfrisk | release／4865a80ea064 | — | 父 0.0.2，测试新地址与鉴权已改 |
| lease-opgateway | release／8d01a4fc6b51 | — | 父 0.0.2，测试新地址与鉴权已改 |
| lease-risk | release／6c6eeaabec83 | — | 父 0.0.2，两个启动模块测试配置已改；清除旧 schedule 显式版本，未发现有效业务 @XxlJob |
| lease-payment | release／a4674a834638 | 6／6 | 父与任务已改；测试 Nacos 地址和鉴权待补／核实部署注入；同提交既有编译成功 |
| lease-saps | release／9d37a9a39b27 | 27／27 | 父与任务已改；测试 Nacos 地址和鉴权待补／核实部署注入；同提交既有编译成功 |
| lease-crm | release／37891f2347ec | 0／1 | 仅升级配置部分；父和 lease-crm-service 的 schedule 显式版本仍为 0.0.1，任务旧签名 |
| lease-authing | master／5c03c78b98be | — | 未见升级分支，旧父与测试配置 |
| lease-contract | master／8b7ceaad06b3 | 0／9 | 未见升级分支，旧父、任务与测试配置 |
| lease-esb | master／32f8c5b107cd | — | 未见升级分支，旧父与测试配置 |
| lease-gps | master／cc24d7a3453d | — | 未见升级分支，旧父与测试配置 |
| lease-intfcommon | master／e2e21f468567 | 0／11 | 未见升级分支，旧父、任务与测试配置；多业务启动配置不重复计任务 |
| lease-intfgps | master／c82ead43ad7a | — | 未见升级分支，thctay 父仍为 0.0.1 |
| lease-marketing | master／b37cacf9ce9a | 0／4 | 未见升级分支，旧父、显式 schedule 版本与任务；4 个 protected 入口计入统计 |
| lease-marketing-back | master／23c470ecb595 | — | 未见升级分支，旧父与测试配置 |
| lease-workflow | master／f2b7932161c2 | — | 未见升级分支，独立依赖管理，测试配置仍旧 |

**受控环境及运行数据证据**：

- 智能柜 WorkHub TEST 明确登记新 Nacos `http://10.10.116.53:28848/nacos`、新 XXL-JOB `http://10.10.116.53:28080/xxl-job-admin`，数据库资源为 `public-test-db-10-10-116-53-3306`。该资源名称虽带 public，本轮返回的实际归属为智能柜，不以名称推断叉车共用。
- 智能柜旧／新库分别为 nacos_config／nacos_config_254、xxl_job／xxl_job_250。原始快照的四个 ol 调度组为 8 payment、9 saps、10 risk、11 order；旧／新任务数分别为 7／7、19／19、2／2、19／19，共 47／47。旧启用数分别为 3、7、0、4；新启用数全部为 0。排除已弃用 payment 后，本次范围为分组 9／10／11，共 40／40 条，旧启用 11 条、停止 29 条，新任务全部停止。按任务 ID 和分组匹配，缺失、handler 差异、job_cron 到 schedule_conf 差异、参数差异均为 0；没有将这个局部一致性检查称为整库迁移验收。
- 智能柜新中心原始快照四个分组 address_list 均为空，registry 未见 ol 注册；旧中心四个注册键的心跳距查询时间为 1～20 秒。其中本次范围内为 order、risk、saps 三个，payment 的旧心跳另作弃用后的遗留项。新中心保持任务停止符合切换准备阶段的做法，当前事实是尚未完成注册和调度切换，不能据此判定迁移失败。
- 智能柜新 Nacos 的 9 条 ol 配置中，8 条属于本次升级范围，1 条为已弃用 ol-payment。该库另有历史 lease 前缀配置和调度分组，多数配置时间为 2026-04-13，且对应分组无地址；不将其作为叉车 .142 源库已向新平台完成迁移的证据。
- 叉车 WorkHub TEST 当前数据库资源为 `bl000008-test-db-10-10-116-142-3306`，accessEndpoints 为空。现有 xxl_job_info 仍含 job_cron，未见 schedule_type／schedule_conf／misfire_strategy；xxl_job_group.address_list 为 varchar(512)，属于旧调度表结构。未发现另命名的新 Nacos／XXL-JOB schema，但仅凭库名不判断 Nacos 服务端实际版本。
- 叉车旧任务分布：payment 5（启用 1）、finance 12、saps 28（启用 1）、marketing 3、crm 1、contract 2、intfcommon 2，共 53，启用 2。实时 registry 为 crm、finance、intfcommon、marketing、payment、risk、saps，查询时心跳距当前时间约 4～20 秒。contract 有分组而本轮未见注册，risk 有注册而旧分组列表无同名分组，后续应核对发布清单与执行器映射。
- 本次范围内源码业务入口数（智能柜 59／叉车 71）与调度库任务记录数（智能柜 40／叉车 53）是不同口径，不相减作为遗漏任务数；任务启用也不代表本轮已验证业务执行成功。原始智能柜全量快照为 67 个入口、47 条任务，其中 payment 的 8 个入口、7 条任务已按用户补充排除。

**仍待收尾的工作**：智能柜核对 ol-order 的测试目标／发布覆盖，补齐范围内应用的构建和运行验收，不再要求升级 ol-payment；其旧执行器、启用任务和新库遗留配置另列弃用资源核对，本次未停用或删除。叉车先完成 lease-crm 的父依赖、旧 schedule 覆盖和任务入口，再覆盖剩余 9 个无升级分支的相关后端，并补齐 saps／payment 的测试接入配置。确认叉车 .52 新平台对应的受控入口与数据库后，再逐项检查配置和调度数据迁移、执行器注册、任务触发／日志／回调、服务发现和配置刷新；按切换前最新启停快照停旧并恢复本次范围内的新调度，原本停止的任务不批量启用，已弃用 ol-payment 不纳入恢复清单。

验证边界：两条业务线 TEST 均未配置启用的 ELK 范围；实际部署 SHA、Nacos 服务端运行版本、发布注入、XXL-JOB token 及全部应用的有效日志留存仍未核实。ol-order 的地址不一致、saps／payment 未声明鉴权都属于源码待核实项，不据此断言运行环境已发生配置故障。三份历史成功编译日志位于 `/private/tmp/workhub-upgrade-recheck-20261103/{ol-saps,lease-saps,lease-payment}-compile.log`，本轮未重新编译，亦未运行环境联调或真实调度。既有 lease-saps 业务单测删除的审查意见保留，不能用 compile 成功代替回归测试。

完整原始源码清点（包括完整 SHA、父依赖、任务签名及脱敏测试配置，保留范围调整前的 ol-payment 证据）保存于 `/private/tmp/workhub-ol-lease-progress-20261103/inventory.json`；计算本次升级进度时须按本节排除 ol-payment。本次只更新过程记录，未修改业务源码或运行配置、安装依赖、写库、部署或触发任务。

### 2026-11-03 · 本轮“设备”范围纠正及换电服务依赖核对

用户明确本轮“设备”指 **智能柜 BL000005／GitLab ol 与叉车 BL000008／GitLab lease-saas**，环境仍为测试。此前助手将其误对应为嘉泰设备租赁 BL000007／th-jiatai-equip，下面的 10 个后端、79 个传统任务入口、70 条旧调度记录等核查事实属于嘉泰设备租赁，不能作为智能柜和叉车的升级进度或改造工作量。相关历史证据保留，但本轮范围以用户此次纠正为准；equip-battery-lease-server 不再仅凭前述错误归属列入本轮必须升级的应用。

用户进一步询问 equip-battery-lease-server 是否依赖智能柜和叉车。本轮核对其全部有效 POM、master/test 的 Feign 客户端及出站调用相关代码，并在智能柜／叉车本地远程分支快照内反查该服务名。只读 git ls-remote 重新确认换电服务 master `8098eb299d464bf1b78dc2fea028282bede0ed45`、test `acbd47e3710054789ff27a0010a539867b94f16c`；叉车共享接口 lease-intfcommon 的 master 为 `e2e21f468567eaae2c6988fe84c417bb992401fc`，均与本次读取的引用一致。

| 依赖维度 | 已确认事实 | 对本轮升级范围的含义 |
| --- | --- | --- |
| Maven／公共组件 | 换电服务直接继承 spring-boot-starter-parent 2.2.9.RELEASE，只引用自身 battery 模块和第三方库；有效 POM 未引用 ol 业务构件、lease 业务构件或 lease-public | 升级智能柜／叉车的 lease-public 不会自动改变换电服务的依赖版本，也不构成必须同步升级它的理由 |
| 智能柜核心应用 | 未发现换电服务对 ol-order、ol-saps、ol-payment 等的直接服务调用；NotifyController:61 提到 ol-order 仅为“处理策略参考”的注释 | 不能把参考实现当作运行依赖 |
| 叉车核心应用 | 未发现换电服务对 lease-saps、lease-payment、lease-finance、lease-crm 等核心服务的直接调用 | 目前无证据将它作为这些应用的直接依赖方 |
| 共享文件接口 | 换电服务的 EquipLeaseIntfcommonUploadClient:15 明确调用 Nacos 服务名 equip-lease-intfcommon，接口为 POST /oss/uploadStreamObject | 这是确认存在的运行服务依赖，接口服务迁移时需评估影响 |
| 文件接口的代码归属与实例 | 叉车仓库 lease-intfcommon 的 resources/equip/bootstrap.yml:5 支持以 equip-lease-intfcommon 部署，且实现上述上传接口；嘉泰设备租赁仓库 equip-intf-common 同样实现该接口并使用该服务名 | 存在共用代码／服务能力关联，但仅凭服务名无法判定换电应用实际使用哪份部署；需结合实例和发布构建记录确认 |
| 合同平台 | 换电服务 ContractPlatformClient:15 明确调用 ca-contract；读取到的叉车 lease-contract、嘉泰设备 equip-contract-esign-v3 分别使用 lease-contract、equip-lease-contract 服务名 | 合同服务依赖存在，但当前没有证据将 ca-contract 等同于智能柜或叉车的合同实例 |

源码证据路径：

- `/Users/aslight/IDEAWorkspace/th-jiatai-equip/equip-battery-lease-server/pom.xml:20`。
- `/Users/aslight/IDEAWorkspace/th-jiatai-equip/equip-battery-lease-server/battery-management/src/main/java/com/thchengtay/modules/battery/external/upload/client/EquipLeaseIntfcommonUploadClient.java:15`。
- `/Users/aslight/IDEAWorkspace/th-jiatai-equip/equip-battery-lease-server/battery-management/src/main/java/com/thchengtay/modules/battery/external/contract/client/ContractPlatformClient.java:15`。
- `/Users/aslight/IDEAWorkspace/lease-saas/lease-intfcommon/lease-intfcommon-start/src/main/resources/equip/bootstrap.yml:5`。
- `/Users/aslight/IDEAWorkspace/lease-saas/lease-intfcommon/lease-intfcommon-controller/src/main/java/com/lease/intfcommon/controller/oss/OssController.java:100`。
- `/Users/aslight/IDEAWorkspace/th-jiatai-equip/equip-intf-common/lease-intfcommon-controller/src/main/java/com/lease/intfcommon/controller/oss/OssController.java:98`。

本轮 WorkHub 上下文接口连续返回 HTTP 502，未能补验当前测试部署映射、远程注入地址或共享运行资源。因此结论为：**未发现换电服务直接依赖智能柜／叉车核心业务应用；已确认依赖 equip-lease-intfcommon 等外部服务，存在共享接口迁移的潜在影响，实际是否由本轮迁移的实例提供仍待核实。** 将其从本轮“必须同步升级应用”中移出，作为共享接口消费者保留影响核对；若最终确认该消费者使用的接口实例或注册中心随本轮迁移，再明确其联调与迁移安排。

### 2026-11-03 · 嘉泰设备租赁测试环境 Nacos／XXL-JOB 升级进度核查

> 范围纠正：本节及其“改造点全貌”核查的是嘉泰设备租赁，源于助手对“设备”的错误映射。用户后续明确本轮范围为智能柜和叉车，本节不代表这两条业务线的升级状态。

核验时间：2026-11-03 15:18（北京时间）。用户确认检查测试环境。WorkHub 核实业务线 BL000007／嘉泰设备租赁、GitLab 项目组 `th-jiatai-equip`，工作区 `/Users/aslight/IDEAWorkspace/th-jiatai-equip`。参照桌面《消费分期Nacos和XXLJOB升级迁移方案_20261028.md》v1.6 与《lease-public升级改动清单_20261102.md》，目标为 Nacos 客户端 2.5.4、官方 XXL-JOB core 2.5.0、受影响 lease 公共构件 0.0.2-SNAPSHOT。

结论：设备测试环境尚未完成升级切换。公共组件已有升级提交并已推送，但设备业务仓库尚未发现命名为 Nacos／XXL-JOB 升级的远程分支；已配置设备测试库仍有旧中心执行器的实时心跳，旧任务仍部分启用；已配置公共新测试库未发现设备配置或执行器分组。

**代码核对口径**：通过 WorkHub 安全刷新该项目组 13 个仓库的远程引用，未切换、重置或覆盖工作区。没有关联需求编号，也没有已确认的测试部署分支／部署 SHA。由于未找到方案指定的 release 升级分支，也未找到包含 nacos／xxl 的其他远程分支，以下直接读取 `origin/master`，仅作为待迁移范围与主线源码现状，不冒充测试环境实际部署版本。

| 仓库 | 核对 origin/master SHA | 有效业务任务入口 | 源码现状 |
| --- | --- | --- | --- |
| equip-contract-esign-v3 | 752609c1ca85fb5e761d8ac597a6e03aab436d45 | 9 | 全为旧签名；lease-dependencies 0.0.1-SNAPSHOT |
| equip-lease-battery | 7b375170ac3508965d0e51c20b23dcd36e08cf94 | 7 | 全为旧签名；lease-dependencies 0.0.1-SNAPSHOT |
| equip-lease-crm | 065cd780975602c7a5e6cbbd6f4f0d5c7975dbc5 | 1 | 旧签名；父依赖与显式 lease-schedule 均为 0.0.1-SNAPSHOT |
| equip-lease-finance | 83a9374340ff1f956129bdc54ad569a42c64e936 | 24 | 全为旧签名；父依赖与显式 lease-schedule 均为 0.0.1-SNAPSHOT |
| equip-lease-payment | 2cd2a886c4341d9345f7f7c2f7972f116bf4bc41 | 9 | 全为旧签名；父依赖与显式 lease-schedule 均为 0.0.1-SNAPSHOT |
| equip-lease-saps | b16db39b478620519830eada185b036a86eae394 | 29 | 全为旧签名；父依赖与 saps-service 显式 lease-schedule 均为 0.0.1-SNAPSHOT |
| equip-intf-common | 187089c80e5dabf362851eecf178e29b4f31ec43 | 0 | 继承旧版 lease-dependencies；需核对 Nacos 迁移 |
| equip-lease-gateway | 1448f209a0fb81175f35e521f10893791b5aef8c | 0 | 继承旧版 lease-dependencies；需核对 Nacos 迁移 |
| equip-lease-opgateway | 8eab7a8c15d4500e17a7156d1d16863a6a0b6a78 | 0 | 继承旧版 lease-dependencies；需核对 Nacos 迁移 |

上述六个有任务的应用合计 79 个有效入口，排除注释后均为接收 String、返回 ReturnT 的旧签名，尚未按方案改为无参 void。已读九个应用 bootstrap 的测试段仍使用原有 8848 地址，未声明 username/password；实际部署注入未能核实，端口本身不用于判断服务端版本。

同组还有实际向设备旧调度库注册的 `equip-battery-lease-server`，不能漏在执行器清单之外：`origin/master` 为 `8098eb299d464bf1b78dc2fea028282bede0ed45`，11 个旧入口；`origin/test` 为 `acbd47e3710054789ff27a0010a539867b94f16c`，8 个旧入口。master 的 battery-common、battery-management 直接指定 xxl-job-core 2.2.0；不继承 lease-dependencies，不能仅升级 lease-public 就认为该应用随之升级。其实际部署分支仍未确认，不将 master 的 11 与 test 的 8 相加。equip-edi-sdk 无有效任务及 bootstrap；两个前端仓库不计入 Java 执行器改造数量。

**公共组件状态纠正**：根目录 lease-public 升级分支当前为 `86e2721313671b452a85869c8188e71bee2b9dab`，源码确认父版本 0.0.2-SNAPSHOT、管理 nacos-client 2.5.4 和 xxl-job-core 2.5.0。只读 `git ls-remote --heads origin release/nacos-2.5.4-xxljob-2.5.0_20261027` 确认远端已存在相同提交。因此桌面 11 月 2 日清单的“未推送”状态已过期；本次未重新构建，不据此认定 Jenkins 构件及设备应用已发布。

**数据库实时证据**：均通过 WorkHub readonly 查询，不读取配置内容、令牌或账号密码。

- 设备测试资源 `bl000007-test-db-10-10-116-24-3306`：`xxl_job_info` 仍有 job_cron，未发现 schedule_type、schedule_conf、misfire_strategy；`xxl_job_group.address_list` 为 varchar(512)，仍为旧版表结构。
- 旧库 registry 中 7 个设备执行器键仍有实时心跳，查询时距数据库 NOW() 为 5～17 秒：equip-lease-saps、equip-lease-payment、equip-lease-finance、equip-lease-crm、equip-lease-battery、equip-battery-lease-server、equip-battery-lease-server-2。
- 旧库已配置设备分组共 70 条任务记录，其中启用 10 条：saps 2、payment 1、finance 6、battery 1；其余 60 条停止。该数量是调度库任务记录数，与源码入口数口径不同；启用不等于本次已验证执行成功。
- 设备旧 `nacos_config.config_info` 查询到 11 条 equip 前缀配置元数据。
- 公共测试资源 `public-test-db-10-10-116-53-3306` 已有 `nacos_config_254`、`xxl_job_250`；其中 equip 前缀 Nacos 配置为 0，XXL-JOB 中 equip 前缀执行器分组为 0。仅覆盖已配置的这套公共新库，不据此排除其他未核实的独立新平台。

**核验缺口及后续顺序**：设备测试环境未配置启用的 ELK 范围；受控 service_status 查询 equip-lease-saps 返回 Unit not found，不能据此认定业务进程未运行。实际部署包 SHA、Nacos 服务端版本、发布注入参数和新平台目标映射尚未取得。建议先落实设备各应用升级分支及上述任务／依赖改造，再迁移设备配置与调度数据，完成新中心注册、触发、日志、回调和跨服务发现验收，最后按方案停旧调度并切换，保持原启停状态，避免两套中心同时触发。

本次只做状态核查和过程记录，未安装软件、依赖或脚本，未修改业务代码或运行配置，未启动服务、触发任务、迁库或执行部署。未运行构建测试：当前没有可供验证的设备升级实现，构建旧源码不能证明目标升级完成。

#### 按改造点整理的全貌（同日补充）

用户要求按改造点整理全貌。本节整理范围、处理要求、当前证据和验收条件，未执行升级。运行心跳与任务启停状态沿用上方 15:18 快照；本轮在相同远程提交上补读全部 POM、任务源码、调用方及执行器配置，并只读补查两套测试库的共享配置摘要和分组 ID 占用情况。既有分支／SHA 口径继续适用。

整体范围为设备项目组内 **10 个后端运行应用**：9 个继承 lease-dependencies 的应用，以及独立管理依赖的 equip-battery-lease-server。前端 equip-lease-admin、equip-battery-lease-admin 和类库 equip-edi-sdk 未发现本次需适配的 Nacos／XXL-JOB 客户端入口，不为它们机械升版。WorkHub 列出的跨业务线共享服务按设备调用关系参加联调，其自身升级另按所属业务线安排。

| 改造点 | 具体工作与完成标准 | 当前状态 |
| --- | --- | --- |
| A. 公共组件 | lease-dependencies、lease-schedule、lease-mq 升为 0.0.2-SNAPSHOT，管理 Nacos 2.5.4／官方 core 2.5.0；删除旧 BaseJobHandler；公共重投入口适配；lease 执行器默认日志留存 90 天。构建环境准备这三个受影响构件，其他公共构件复用既有版本 | 公共源码已完成并推送 86e2721；桌面记录有本地构建结果，Jenkins 制品可用性未核实 |
| B. 业务 Maven 接入 | 9 个应用更新 lease-dependencies 父版本；清理受影响模块的旧版本覆盖；独立换电服务在自身 dependencyManagement 中统一 Nacos／core，清理两个模块的 core 2.2.0。最终依赖树和可执行包均应只含目标版本 | 9 个应用父版本仍旧；独立应用仍显式 core 2.2.0 |
| C. 业务任务入口 | @XxlJob 入口改无参 void，保留 handler 名称；使用原参数的任务通过 XxlJobHelper.getJobParam() 取参；同步适配 IJobHandler 子类、任务日志、原成功／失败及异常语义 | 传统设备 79 个入口均未适配；独立换电服务 master 11 个、test 8 个均为旧入口。若按全部 master 改造，共 90 个，实际发布范围先确认分支 |
| D. 手工调用与特殊任务 | 已定位 12 个 handler 被 Controller 直接调用（含测试 Controller），另有 3 处测试代码调用；保留显式参数业务入口并同步调用方；3 个失败结果分支不能改成正常成功；2 个 RefreshScope 任务需刷新后重测 | 已列出具体调用方及特殊类，尚未改造／验证 |
| E. Nacos 客户端配置 | 10 个应用分别核对 config、discovery 地址与 bootstrap 阶段鉴权；保留或按已确认映射调整服务名、namespace、group、Data ID、共享配置；按实际主端口核对客户端 gRPC 端口及映射 | 已读测试源码仍为原地址且无 username/password 声明；部署注入未核实 |
| F. Nacos 服务端与配置数据 | 先确定设备使用的目标实例、库和命名空间；迁移 11 条设备前缀配置及实际引用的共享配置，核对命名空间、容量、标签和历史数据。新平台按 2.5.4 初始化；迁移后核对配置读取、刷新及服务发现 | 已配置公共新库中设备前缀配置为 0；同 namespace 的 3 条共享配置已存在且与设备旧库摘要不同，不能直接覆盖 |
| G. XXL-JOB 执行器配置 | 核对新中心地址、非空 accessToken、appname、注册地址／端口、日志路径及 90 天留存，验证注册、触发和回调双向可达；独立换电服务核对自身 SapsXxlJobConfig，不依赖 lease 默认值自动生效 | 旧中心仍有 7 个设备执行器键实时心跳；独立换电服务源码留存默认仍为 30 天 |
| H. XXL-JOB 数据迁移 | 以设备源库的 70 条任务为核对基线，迁移相关分组、任务、权限和必要 GLUE 数据；job_cron 映射 schedule_conf，采用新版结构，迁入任务先全部停止；不迁旧 registry／运行日志。共用已有库时先处理主键及关联映射 | 公共新库无设备分组，且源分组 ID 有实际冲突；尚未形成适用于设备目标的迁移脚本 |
| I. 构建与测试验收 | Java 8 构建；确认官方 core、目标 Nacos 与实际公共构件版本；编译生产及测试源码；隔离验证入口、参数、失败反馈、手工调用；测试环境验收服务发现、刷新、重连、触发、日志、回调、超时和业务回归 | 本轮未运行；设备尚无升级实现及对应部署证据 |
| J. 切换与回退 | 切换前重新保存任务启停快照，停旧触发并等待在途任务和回调完成；发布已适配应用，验证新平台后按快照恢复；回退成套恢复应用包、公共依赖、Nacos 与调度配置 | 未切换；15:18 旧库 10 条启用、60 条停止，实际切换前须重新快照 |

**逐应用覆盖矩阵**（传统设备均需父依赖和 Nacos 客户端配置接入）：

| 应用 | 业务任务入口 | 已确认的额外改造／验证点 |
| --- | --- | --- |
| equip-lease-saps | 29 | saps-service/pom.xml 的 lease-schedule 显式 0.0.1；4 个 handler 有 Controller 调用，另有 3 处测试调用；3 个失败反馈任务；1 个 IJobHandler 子类 |
| equip-lease-finance | 24 | 根 POM 的 lease-schedule 显式 0.0.1；6 个 IJobHandler 子类；MsgRetrySchedule 带 RefreshScope |
| equip-lease-payment | 9 | 根 POM 的 lease-schedule 显式 0.0.1；7 个 handler 有 Controller 调用；RemitStatusSchedule 带 RefreshScope |
| equip-contract-esign-v3 | 9 | 1 个 IJobHandler 子类；根 POM 的 lease-mq 为注释；核对实际执行器装配来源和本轮是否部署（旧中心分组存在，但前轮未见其注册心跳） |
| equip-lease-battery | 7 | 1 个 IJobHandler 子类；billMarkSchedule 被业务接口调用，调用方接收 ReturnT 返回值，需同步适配 |
| equip-lease-crm | 1 | lease-crm-service/pom.xml 的 lease-schedule 显式 0.0.1；旧中心有注册心跳但分组清单无同名 appname，需核对任务映射 |
| equip-intf-common | 0 | 更新父依赖、配置与服务发现并联调；无现存业务 @XxlJob 入口需要新增或迁移 |
| equip-lease-gateway | 0 | 更新父依赖及 Nacos；验收路由发现和下游调用 |
| equip-lease-opgateway | 0 | 更新父依赖及 Nacos；验收路由发现和下游调用 |
| equip-battery-lease-server | master 11／test 8 | 独立 Spring Boot 父工程；battery-common、battery-management 的 core 2.2.0；自身 Nacos 版本管理、XxlJobLogger 和 SapsXxlJobConfig；同时核对 server 与 server-2 两个运行名称 |

九个传统应用中，有效 POM 解析未发现直接引入 lease-mq。桌面 lease-public 清单把 equip-contract-esign-v3 与 equip-lease-payment 列为直接依赖，但两处实际位于 XML 注释内。应以有效 POM、依赖树和最终包为准：先核实是否存在传递 MQ 依赖；确实带入公共 messageSenderRetrySchedule 才纳入该宿主验收，不为凑齐公共组件范围而新增 MQ 依赖，也不将公共 handler 重复计入上述 79 个业务入口。

**任务代码容易漏改的具体点**：

- 9 个直接继承 IJobHandler 的类：合同 ProviderCallbackRecordRetryConsumer；电池 ApplicationContinueSchedule；金融 ReFactoringGenerateBillSchedule、StatisticMonthOverdueRateSchedule、StatisticSchedule、ApplicationAuditRetryJob、MessageNoticeRetryJob、SyncCustomerMerchantInfoJob；清结算 StatementContractGenerateSchedule。按新版契约实现无参 void execute()，保留各自 @XxlJob 名称及原业务异常处理。
- 12 个存在 Controller 直接调用的 handler：电池 billMarkSchedule；支付 batchStartAction、payCheckSchedule、payPushCheck、channelQuerySchedule、payResultNotifySchedule、remitNotifySchedule、remitStatusSchedule；清结算 loanOrderStatusNotifySchedule、funderLoanSettleNotifySchedule、funderBillReportSchedule、reportOrderMonthSchedule。显式参数入口不能改成依赖调度上下文；尤其 BillInfoApiController:54 仍将执行结果赋给 ReturnT，签名和调用方要一并处理；FunderBillReportManageController:80 是实际报表刷新接口。
- 3 处既有测试调用：StatementNoticeScheduleTest 对 statementNoticeSchedule、loanOrderStatusNotifySchedule 的调用，以及 RefundStatementGenerateScheduleTest 对 refundStatementGenerateSchedule 的调用。须适配测试调用方式，不以删除原业务测试代替兼容处理。
- 3 个清结算任务有明确失败结果：reFactoringStatementGenerateJob 缺失 chargeType 时 ReturnT.FAIL；funderBillReportSchedule 清理报表失败时 ReturnT.FAIL；StatementContractGenerateSchedule 参数解析异常时返回 FAIL_CODE 和异常文字。调度入口需映射成 XxlJobHelper.handleFail 或保留相应抛异常语义，不能简单替换为 return;。兼有手工入口的任务需保证手工行为也符合原契约。
- 刷新作用域任务为金融 MsgRetrySchedule（msgRetrySchedule）与支付 RemitStatusSchedule（remitStatusSchedule）。迁移后需验证配置刷新前后均能调度。
- 独立换电服务使用 SapsXxlJobConfig，已有 accessToken 绑定和 enabled 判断，留存默认值为 30；应核对远程有效值或按目标将默认值调整为 90。它不继承 lease-schedule，升级 lease-public 不会自动修改该配置类。

**环境迁移前置项：当前证据不支持直接复用桌面全库脚本**。

1. 设备旧库在受控目标 `bl000007-test-db-10-10-116-24-3306`；已配置公共新库在 `public-test-db-10-10-116-53-3306`。桌面 INSERT ... SELECT 为同一 MySQL 实例的跨 schema 全表复制，不能直接作为这两个目标之间的设备选择性迁移脚本。
2. 两侧存在相同 namespace ID `e409079b-9077-4ddf-80fe-8c356f96b16f`；COMMON_GROUP 下 common.yaml、redis.yaml、rabbitmq.yaml 三项 Data ID 相同，但返回的 md5 摘要全部不同。源 namespace 还有非 equip 前缀的其他业务配置；只统计 11 条 equip 配置不足以覆盖共享配置，也不能整 namespace 覆盖公共新库。若采用该公共实例，应先确定设备独立命名空间及配置映射；若使用独立设备新实例／新库，则另行核实其受控目标。
3. 设备旧分组 ID 5／6／7／10／11 分别为 equip-lease-saps／equip-lease-payment／equip-lease-finance／equip-lease-battery／equip-lease-contract；公共新库相同 ID 已分别属于 lease-marketing／lease-crm／lease-contract／ol-risk／ol-order。采用独立空库才能按方案直接保留这些 ID；若共用公共调度库，必须另行设计分组与任务 ID 映射，连同 job_group、child_jobid、用户分组权限和 GLUE 关联校验，不能只改库名执行全表 INSERT。源组 ID 12 本轮查询未见目标同 ID；任务 ID 是否冲突尚未逐项核对。
4. 设备 CRM、equip-battery-lease-server-2 有注册心跳，却未在旧分组清单中看到同名 appname；合同则有组但未见注册心跳。发布清单应将实际运行实例、执行器 appname、分组和 handler 对齐，决定哪些随本轮部署，不按注册键数或任务数直接推算服务数量。

建议实施顺序为：**确认发布分支与目标平台隔离方式 → 应用依赖／任务改造与配置／迁移脚本准备 → Java 8 构建和隔离验证 → 新平台联调验收 → 停旧、切换、按快照恢复 → 留观及回退验证**。依赖接入与任务接口必须成套构建发布；共用目标平台的命名空间和 ID 冲突应在准备迁移数据前解决。当前不宜用单个完成百分比表示进度，按上表分别追踪源码、构建、数据准备、部署与验收。

本轮仅补充过程记录及只读证据，源码清单保存于 `/private/tmp/workhub-equip-upgrade-overview-20261103/source-inventory.json`；所有业务源码、运行参数和数据库数据均未修改。

### 2026-11-03 · 物流平台七个仓库的 Nacos／XXL-JOB 升级状态核查

按用户继续检查的范围，核对物流平台 BL000006／GitLab xxt-plateform 下的 thctay-saps、thctay-account、thctay-eas、thctay-payment、saps-back、saps-account-checker、thctay-saps-core-common。WorkHub 已确认归属并安全刷新项目组远程引用；读取 `/Users/aslight/IDEAWorkspace/xxt-plateform/{仓库}`，未切换、覆盖或清理工作区。沿用桌面统一方案 v1.6 的公共依赖、任务入口及 Nacos 鉴权规则；这次为源码状态检查，未关联需求编号或假定生产已部署。

本组实际升级分支名是 `feature/nacos-2.5.4-xxljob-2.5.0_20261102`，与前面消费分期／租赁服务的 release 分支不同。下表六个 feature 分支均与各自 origin/master 完全相同，merge-base 为本身，三点 diff 为空、无新增提交。因此当前没有可评审的升级实现，不判为已经适配正确。

| 仓库 | 远程 feature 分支及 master 相同 SHA | 当前任务／依赖状态 |
| --- | --- | --- |
| thctay-saps | 4f4a228aba8190c99ca78d4e55e9d66f1e80d244 | 59 个有效 @XxlJob 入口全为旧签名；父 thctay-dependencies 0.0.1-SNAPSHOT |
| thctay-account | 54d001b2527df6058de8485de927651a1b39edb7 | 7 个有效入口全为旧签名；父 thctay-dependencies 0.0.1-SNAPSHOT；HotAccountJobHandler 留有 BaseJobHandler import |
| thctay-eas | bf81ca73cd968bceceb9c32d461e25198ebc13e3 | 16 个有效入口全为旧签名；父 0.0.1-SNAPSHOT，thctay-schedule 仍显式 0.0.1-SNAPSHOT |
| thctay-payment | ee34164c6651ec2e0d9832c4d4e388647e6d549b | 21 个有效入口全为旧签名；父 0.0.1-SNAPSHOT，mq、schedule 仍显式 0.0.1-SNAPSHOT |
| saps-account-checker | 5f976e9a937f52d5727898a33eda8741230d60af | 4 个有效入口全为旧签名；父为 thctay-saps 0.0.1-SNAPSHOT，需要随其实际父依赖同步核对 |
| thctay-saps-core-common | 75736cfc7d4dbb0f65bb49629c195cef2f591ba9 | 公共类库，版本 1.0-SNAPSHOT；只直接依赖 thctay-exception，无启动入口及 XXL-JOB 任务，无需机械升为新公共父版本 |

saps-back 未找到上述 feature 或 release 升级分支。其 origin/master 为 `aa1f91664d8b16d0858fce087961a254f76fa764`；另外存在 `import_261103`（`2f10150c0079b80288e48f0203738b2a7b84845e`），内容是月度服务费导出，不当作升级分支。为判断待迁移范围而补读 master：没有有效 @XxlJob 入口，父 thctay-dependencies 仍为 0.0.1-SNAPSHOT，使用 Nacos config/discovery，需迁移 Nacos，但不需要新增或改造不存在的 XXL-JOB 业务入口。master 的状态不冒充升级分支实现。

五个含任务的应用合计 107 个旧入口（59＋7＋16＋21＋4），均仍接收 String、返回 ReturnT；需按方案改为无参 void、保留 handler 名称，按需读取 XxlJobHelper 参数并保留手工调用及成功／失败反馈。上表显式旧 schedule／mq 版本需在宿主升父时同步清理；account 未使用的 BaseJobHandler import 也需随新版公共基类删除而清理。

六个运行应用（含 saps-back）的已读 bootstrap 均仍使用原地址／8848，未声明 Nacos 启动鉴权，仓库非 Markdown 内容未检索到 Nacos 凭据注入键。这里仅报告源码现状；端口 8848 本身不能用于判断服务端版本，实际发布注入、服务端迁移、XXL-JOB 地址／令牌仍需按目标环境核实。未读取物流生产 Nacos，未进行应用启动或调度。

公共类库 thctay-saps-core-common 不直接引用 Nacos／XXL-JOB；已补查本机缓存 thctay-exception → thctay-common 的 POM，也未见这两类运行依赖。按方案“未受影响公共构件复用旧版本”，可以保留其旧构件；不能把未修改该库与五个应用仍保留旧任务入口混为同类缺口。

本次未执行升级编译：六个升级分支没有源码变化，saps-back 缺升级分支，构建现有旧版本不能证明目标升级完成。

### 2026-11-03 · 三个服务按 Nacos／XXL-JOB 方案复核

范围：智能柜 BL000005／ol、叉车 BL000008／lease-saas；工作区及 remote 再经 WorkHub 和本地 Git 核实，安全刷新远程引用。审查分支仍为 `release/nacos-2.5.4-xxljob-2.5.0_20261027`，采用与 master 的三点 diff；未关联需求编号。本次为源码与 Java 8 离线构建复核，未启动服务或触发真实任务。

| 仓库 | 最新审查 SHA | origin/master 及 merge-base | 相对上次变化 |
| --- | --- | --- | --- |
| ol-saps | 2b2781fd0e46cfb575011efdad2089024d6a8355 | 1452001667b4f0568f3f5b077f9282704f81935c | 只新增 bootstrap.yml 的 dev、test 地址及鉴权配置；任务源码与上次一致 |
| lease-saps | 9d37a9a39b27980a8a3725600f384bb3747dea7c | 51add5ccfb04cd717a481d6079ef064b13d08c5d | 无变化 |
| lease-payment | a4674a834638c56639564a959e1a563285b8b83d | b77618ab722f02d5da8c2035417a191c1f1a7fa9 | 无变化 |

结论：三个服务的依赖接入和业务任务入口改法符合桌面统一方案与 lease-public 清单的源码要求，未发现已确认的升级源码缺陷；不能因此认定三个服务的所有环境均完成迁移。

- 实际依赖树均确认 `nacos-client:2.5.4`、官方 `xxl-job-core:2.5.0`、`lease-mq:0.0.2-SNAPSHOT`、`lease-schedule:0.0.2-SNAPSHOT`。本地 core JAR SHA-256 为 `66d05bcea7254d102b77e476cc79f431bc4992ccf016ce74eef900794330833f`，与桌面方案记录一致。
- 有效业务 handler 共 65 项（32／27／6）；相对 master 无新增、删除或重名，均为无参 void。参数读取、显式参数手工调用、成功／失败反馈沿用上次逐方法审查结果；本次确认任务文件无后续变化，并重新核对所有入口。未发现有效代码引用旧 BaseJobHandler 或 XxlJobLogger。
- ol-saps 的 dev、test：配置中心和服务发现均改为 28848 端口，且在 bootstrap 阶段提供非空 username/password；保留原 namespace、group 和共享配置声明。证据为审查提交 `lease-saps-start/src/main/resources/bootstrap.yml:39、46、82、89`。本次早期进度文字误称 test、sit，已更正为 dev、test。
- ol-saps 的 sit、prod，以及 lease-saps、lease-payment 的各环境仍沿用原 Nacos 地址，bootstrap 未声明鉴权。若这些环境纳入本次开启鉴权的新 Nacos 迁移，应按方案补齐或核实发布阶段提供的启动配置。原地址未变不单独证明运行中的服务端版本未升级；本次未核查发布参数。
- XXL-JOB 新中心地址、非空 accessToken、日志留存及注册／回调需结合对应环境的远程配置验证；本次未读取智能柜／叉车目标环境的 Nacos，未将待核验项认定为已经发生的配置故障。

本次三个服务均完成 Java 8 离线 compile：lease-payment 直接编译全部 9 个模块；两个 saps 使用临时聚合构建，将已有 `lease-saas/lease-public` 的 master `5b032cfe76bc06776f33281e2df10efcb02efde4` 中 `lease-permission:0.0.1-SNAPSHOT` 源码纳入同一构建，未改业务源码或依赖版本、未安装构件或下载依赖。两个 saps 的 9 个应用模块也均通过。因此上次“两个 saps 尚未完成编译”的验证限制已补齐；普通独立构建环境仍需预先准备相同公共构件。

验证源码及日志位于 `/private/tmp/workhub-upgrade-recheck-20261103/`：三个 `{仓库}-compile.log` 均为 BUILD SUCCESS；依赖核查日志为 `ol-saps-resolved-dependencies.log`、`lease-saps-resolved-dependencies.log`、`lease-payment-dependencies.log`。临时聚合 POM 默认 dependency 插件离线解析失败后，显式选择本机已缓存的 3.1.2 完成依赖核查，未安装插件或修改仓库配置。三个 diff --check 均通过；未运行环境联调及真实调度。

### 2026-11-02 · ol-saps、lease-saps、lease-payment 升级分支审查

用户随后明确要求按桌面 Nacos／XXL-JOB 升级方案判断正确性。补充结论：三个服务的依赖管理升级及 65 个业务任务入口适配符合方案的源码改造规则；但不能认定完整接入已完成。三个审查提交的 bootstrap.yml 均与 master 相同，仍使用原有 Nacos 地址，config/discovery 均未提供 username/password，仓库非 Markdown 内容也未检索到 Nacos 凭据注入配置。方案要求凭据在 bootstrap 阶段可用，须由对应环境的发布配置补齐并验证；原地址未变本身不证明服务端仍是旧版本。XXL-JOB 新中心地址、非空 accessToken 及日志留存等实际远程配置本次未读取，属于待核验，不据源码没有 token 就断言运行环境缺失。完整迁移还需构建及目标环境验收，前述测试删除和本地实体删除不作为“任务适配改法错误”的依据。

审查范围：用户要求检查三个服务的改动；结合上文 Nacos／XXL-JOB 升级上下文，安全刷新远程引用后，审查各仓库 `origin/release/nacos-2.5.4-xxljob-2.5.0_20261027` 相对 `origin/master` 的三点 diff。WorkHub 确认智能柜 BL000005 → ol、叉车 BL000008 → lease-saas。未指定需求编号，本次为静态审查与离线构建，不作为生产部署验证。未切换工作区或修改业务代码。

| 仓库／工作区 | 审查提交 | 基线及 merge-base |
| --- | --- | --- |
| `/Users/aslight/IDEAWorkspace/ol/ol-saps` | `5f18272419f2fbf7554c3965d36f4c10cf63495a` | `1452001667b4f0568f3f5b077f9282704f81935c` |
| `/Users/aslight/IDEAWorkspace/lease-saas/lease-saps` | `9d37a9a39b27980a8a3725600f384bb3747dea7c` | `51add5ccfb04cd717a481d6079ef064b13d08c5d` |
| `/Users/aslight/IDEAWorkspace/lease-saas/lease-payment` | `a4674a834638c56639564a959e1a563285b8b83d` | `b77618ab722f02d5da8c2035417a191c1f1a7fa9` |

审查意见：`lease-saps` 将 `lease-saps-business/src/test/java/com/lease/saps/business/service/customerbill/impl/CustomerBillWithholdBizImplTest.java` 整文件删除，移除了 17 项代扣业务回归测试，覆盖通联／宝付 appId、绑卡渠道过滤、支付流水查询、无可用卡告警及订单号冲突重试。这些是 Mockito 隔离业务测试，未使用旧 `ReturnT` 或 XXL-JOB 调度方法签名；本次未发现替代测试。建议保留，测试不兼容时做有针对性的适配。该意见为测试覆盖退化（P2），不表示已证明代扣运行逻辑出错。

其余已读差异中未发现已确认的新增业务逻辑缺陷。检查有效 @XxlJob handler：ol-saps 32、lease-saps 27、lease-payment 6，共 65 项；相对 master 无名称新增或删除，无重复，全部为无参 void 签名。现有 Controller 的手工调用已沿调用链检查；参数从调度入口取出再传入原业务方法，在线程池中使用的参数未改为在线程池内部读取。报表失败反馈的变更已与原成功／失败分支对照。三个 POM 引用新版 lease-dependencies，未发现这三个宿主另行覆盖旧 nacos-client、xxl-job-core、lease-schedule 或 lease-mq 版本。

验证采用远程提交的临时源码快照，目录 `/private/tmp/workhub-upgrade-review-20261102/{仓库}`，使用本机现有 Java 8、Maven 执行离线 compile，跳过测试及应用启动，未安装依赖。

| 验证 | 结果 |
| --- | --- |
| lease-payment 全 reactor 编译 | 通过，9 个模块 SUCCESS；日志 `/private/tmp/workhub-upgrade-review-20261102/lease-payment-compile.log` |
| ol-saps 全 reactor 编译 | 本地缺少 `com.lease:lease-permission:0.0.1-SNAPSHOT`，在 model 模块依赖解析阶段阻塞，不能认定完整编译通过；日志同目录 `ol-saps-compile.log` |
| lease-saps 全 reactor 编译 | 同样缺少 lease-permission，在 common 模块依赖解析阶段阻塞；日志同目录 `lease-saps-compile.log` |
| 三个升级分支 diff --check | 均通过；不代替编译或运行验证 |
| 环境联调、真实调度、支付／短信／签约、Nacos 配置修改 | 未执行 |

另发现与升级提交无关的本地工作树问题：`ol-saps` 当前在 master，未提交地删除了 `lease-saps-model/src/main/java/com/lease/saps/model/entity/CustomerOrderEntity.java`，但 CustomerOrderManageRes 等大量源码仍导入、继承或使用该类型。若保持该删除且无替代类型，干净构建会出现缺失类型错误；升级分支自身仍保留该文件，不混入升级分支缺陷。lease-saps、lease-payment 当前 master 工作树干净。

### 2026-11-02 · 消费分期升级前的 intfcommon 与合同服务依赖核查

核验时间：2026-11-02 14:22（Asia/Shanghai）。业务线：消费分期 BL000004；GitLab 项目组：`asset-plateform`；环境：生产；需求标识：用户指定的桌面 Nacos／XXL-JOB 升级方案核查，无需求编号。代码口径为通过 WorkHub 安全刷新后的 `origin/master`，工作区为 `/Users/aslight/IDEAWorkspace/asset-plateform/{仓库}`，未切换或覆盖业务工作树。对照文件为桌面 `nacos和xxljob升级/消费分期Nacos和XXLJOB升级迁移方案_20261028.md`（v1.6）。

核查结论：消费分期自己的公共接口服务与跨业务线合同服务并存。`amp-intf-common` 是本组源码仓库，应用和 Nacos 注册名均为 `amp-intfcommon`；资方、订单、支付、账单直接通过该服务名获取文件或发送短信。签约主要依赖叉车组 `lease-saas/lease-contract`，订单实名认证依赖物流组 `xxt-plateform/thctay-contract`，均由生产配置中的 HTTP 地址访问。不能把所有合同相关能力归为消费分期组内应用，也不能把 `amp-intfcommon` 当作外部 `thctay-intfcommon`。

#### 逐系统核查

“未见直接调用”指已核查的 master 源码及当前 Nacos 配置未发现直接依赖，不代表所有间接业务链均与合同无关。表中健康实例数为本次生产 Nacos prod／DEFAULT_GROUP 服务列表的读取快照。

| 后端仓库 | 注册名／健康实例 | intfcommon 使用情况 | 合同相关直接依赖 |
| --- | --- | --- | --- |
| amp-intf-common | amp-intfcommon／3 | 本组公共接口提供方，当前对外入口主要为 OSS、短信及配置管理 | 留有合同枚举、异常类及 E 签宝工具；未发现消费者所用的合同签约 Controller，不据遗留名称认定其为实际合同服务 |
| amp-capital | amp-capital／4 | 本组 amp-intfcommon：文件及部分意愿短信；另有外部 assets-intfcommon：意愿短信 | LeaseContractClient → commonParam.contractSignUrl → 叉车 lease-contract；LoanAttachmentServiceImpl 实际调用创建签章流程 |
| amp-order | amp-order／4 | 本组 amp-intfcommon：文件上传与查询 | ContractFeignClient → `$contract$` → 拦截器替换为 api.contract → 物流 thctay-contract；用于个人实名认证，不是订单自身实现合同签约 |
| amp-payment | amp-payment／3 | 本组 amp-intfcommon：文件上传与查询 | ContractApi → agree.sign.contractSignUrl → 叉车 lease-contract；AgreementPayThctServiceImpl 实际调用创建签章流程 |
| amp-saps | amp-saps／3 | 本组 amp-intfcommon：导出等文件上传；外部 thctay-intfcommon：短信验证码 | 未见直接合同签约调用 |
| amp-risk | amp-risk／2 | 声明旧 thctay-intfcommon Feign 接口；未检索到业务代码注入／调用该接口 | 未见直接合同客户端；另核查生产 bpm_datasource 的 21 条配置，未发现 contract／intfcommon 引用 |
| amp-risk-backend | amp-risk-backend／1 | 未见直接固定客户端；存在通用动态调用能力 | 未见直接合同客户端；同库 bpm_datasource 核查结果同上，不外推到所有历史模型副本 |
| amp-intf-risk | amp-intfrisk／1 | 未见本次目标公共接口服务的直接调用 | 未见直接合同服务调用 |
| amp-intf-risk-jt | amp-intf-risk-jt／1 | 未见本次目标公共接口服务的直接调用 | 未见直接合同服务调用 |
| amp-auth | amp-auth／1 | 未见直接调用 | 未见直接合同服务调用 |
| amp-op-gateway | amp-opgateway／1 | 根据发现的服务名动态生成路由，可转发本组 amp-intfcommon | 未见独立合同客户端；生产配置未发现显式外部合同路由 |
| amp-gateway | amp-gateway／4 | 未见直接调用 | 未见直接合同服务调用；存在通用业务接口执行能力，不据此断言所有间接上游无合同业务 |
| amp-ops-reporting | amp-ops-reporting／1 | 未见直接调用 | 未见直接合同服务调用 |
| amp-wolease | amp-wolease／当前列表未见 | 未见直接调用 | 未见直接合同服务调用；仅“未见注册”不足以认定该应用已下线 |

补查同组其余三个仓库：`amp-admin` 是前端，`.env.production` 将 VUE_APP_INTFCOMMON_API 指向管理网关下 `/amp-intfcommon`；`amp-unionpay-h5` 未见有效的直接合同服务地址，出现的旧 thctay-intfcommon URL 为注释；`amp-doc` 为文档仓库，不作为后端运行服务计数。

#### 生产地址与代码证据

1. 本组公共接口：`amp-intf-common/intfcommon-start/src/main/resources/bootstrap.yml:3` 定义 `spring.application.name=amp-intfcommon`；POM artifactId 同为 `amp-intfcommon`。`META-INF/app.properties` 中残留的 `app.name=thctay-contract` 不能代替 Spring 应用名及生产注册信息。生产当前有 3 个健康实例。
2. 资方合同：`amp-capital/capital-api/src/main/java/com/amp/capital/api/LeaseContractClient.java:22` 读取 `commonParam.contractSignUrl`；`capital-service/.../loan/impl/LoanAttachmentServiceImpl.java:1061` 实际调用 `createSignContract`。生产 `amp-capital.yml` 的值为 `https://lease-opgateway.thchengtay.com/lease-contract`。
3. 支付合同：`amp-payment/payment-api/src/main/java/com/amp/payment/api/service/ContractApi.java:16` 读取 `agree.sign.contractSignUrl`；`payment-business/.../service/impl/AgreementPayThctServiceImpl.java:359` 实际调用 `createSignContract`。生产 `amp-payment` 同样指向 `https://lease-opgateway.thchengtay.com/lease-contract`。相邻旧 thctay-contract 注解已被注释。
4. 订单认证：`amp-order/amp-order-api/src/main/java/com/thchengtay/order/api/ContractFeignClient.java:17` 的 URL 为 `$contract$`；`amp-order-start/.../interceptor/OkHttpLoggingInterceptor.java:45-51` 根据 `api.contract` 替换目标。生产 `amp-order.yml` 的值为 `https://opgateway.lianrongbao.cn/thctay-contract`；业务调用见 ApplicationLoanServiceImpl:539、ApplicationPreliminaryServiceImpl:93。
5. 资方外部短信：`amp-capital/capital-api/.../AssetsIntfCommonClient.java:19` 读取 `assets.intfcommon.url`；IntentionAuthService:174 实际调用；生产值为 `https://assets-opgateway.thchengtay.com/assets-intfcommon`。确认是跨域 HTTP 依赖；未凭域名将其强行映射到某个 GitLab 仓库。
6. 账单外部短信：`amp-saps/saps-api/src/main/java/com/amp/saps/api/service/ThctayIntfCommonApi.java:16` 读取 `thcty.domain`，缺省值为 `https://opgateway.lianrongbao.cn/thctay-intfcommon`；VerificationCodeService:61 实际调用。已读 Nacos `amp-saps` 中未见 thcty.domain 覆盖；未读取进程环境或 JVM 启动参数，故此处为 master 与 Nacos 口径下的目标地址。
7. WorkHub 业务线配置确认 `lease-contract` 归叉车／`lease-saas`，`thctay-contract`、`intf-common` 归物流／`xxt-plateform`；对应本地 Git remote 已核对。外部仓库只核验归属及本地远程分支中的应用名，本次未刷新其全部业务线代码。

生产 Nacos 的 prod 命名空间共 16 条配置，本次全部通过 Safari 的“详情”只读查看：`amp-intfcommon.yml`、`amp-order.yml`、`amp-auth`、`amp-gateway`、`amp-risk.yml`、`amp-intfrisk.yml`、`amp-capital.yml`、`amp-opgateway`、`thctay-contract.yml`、`amp-saps`、`amp-payment`、`amp-risk-scene-backend.yml`、`amp-risk-backend.yml`、`amp-ops-reporting`、`amp-wolease`、`amp-intf-risk-jt.yml`。

其中 `thctay-contract.yml` 是配置条目；生产当前两页服务列表共见 13 个注册服务，未见 `thctay-contract`、`lease-contract` 或 `thctay-intfcommon`。这与已确认的外部 HTTP 调用方式一致，但不能单凭未注册判断配置无用或删除它。该配置还留有指向消费分期 `/amp-intfcommon/oss/uploadBase64StringObject` 的 uploadPath，是否有外部实例读取此条配置仍需额外核实。

风控补查使用 WorkHub `amp-db-prod`／`readonly`：先查询 `amp_risk.bpm_datasource` 的主键范围 1～28，再按该范围读取 21 条数据源的名称、状态、请求 URL 及合同／intfcommon 关键字命中标志；结果未截断，21 条命中标志均为 0。未读取业务请求模板内容、客户资料或密钥，未执行数据更新。该结果仅覆盖当前基础数据源表，未证明所有已发布模型副本或运行中的缓存配置均一致。

#### 对桌面升级方案的影响

- 保留 `amp-intf-common` 在本次迁移范围中，明确仓库名与注册名的差异；消费端、网关和目标 Nacos 均保留注册名 `amp-intfcommon`。
- 外部依赖清单除原有 thctay-contract 等系统外，补充 `lease-contract`、外部 `assets-intfcommon`、物流 `thctay-intfcommon`。外部指跨业务线应用，不表示调用方直接对接 E 签宝 SaaS。
- 迁移时保留并逐项验证 `commonParam.contractSignUrl`、`agree.sign.contractSignUrl`、`api.contract`、`assets.intfcommon.url`，同时核对 `thcty.domain` 的实际生效值。不将这些 HTTP 目标误改为新 Nacos 中的本组服务名。
- 外部合同应用本身不因消费端迁移而自动纳入本次升级；需在目标环境联调合同生成、签署结果回调、认证、短信及文件上传。此处只完成依赖核查，未发起真实签约、认证、短信或支付请求，未完成迁移联调。
- `amp-wolease` 保持“运行必要性待业务确认”；`thctay-contract.yml` 保持现状，不能因无同名注册实例就删除或认定可忽略。

#### 核查提交基线

下表均为消费分期组本次刷新后的 `origin/master`。生产运行包的构建 SHA 未与这组代码提交比对，结论属于代码＋当前配置＋注册状态核查，不等同于生产包溯源认证。

| 仓库 | SHA |
| --- | --- |
| amp-admin | 507989b0cba45a3f4a53d78dac6cc0b4124dddfb |
| amp-auth | 16661164ad8157fef2ba73c42832d0649f2d810d |
| amp-capital | 742871aefc308d23d1ecc9aa757fa518632441d3 |
| amp-doc | 6d67b2ee34612b22347c930917d86d3176c88b4c |
| amp-gateway | 218a0a5643b54755ee9a7df64c7c7025c592764a |
| amp-intf-common | ec1e4df4867b6a8c26f6fb2a6665cd03a6ef0779 |
| amp-intf-risk | b54773cd9e25083a7ba80040a400c6b6fd6c4357 |
| amp-intf-risk-jt | f1a87f39cb03107105bacbd6686805a3a15820ff |
| amp-op-gateway | cff6a6fd09d991d3d2e43715f07e14ecae881e17 |
| amp-ops-reporting | a6a7c8d9b83527b45eb7c80dbbcfc3aa521a9e3f |
| amp-order | f696b160af8081f9a2090aca35ced64c2d5005af |
| amp-payment | 0c83df979d2db10ba38ea382ca95a2d01ddf0ca2 |
| amp-risk | 545bdaee66216dbc3f024573d9b098316f669209 |
| amp-risk-backend | 74f1df26d41894ca2ec25cd2f8014b4f0e824e2f |
| amp-saps | 3662fa385df99e968a4ff3686265015c41aaa8b6 |
| amp-unionpay-h5 | 34bb33b997ecaff13adad7413fa65deca3901361 |
| amp-wolease | 13c029e58e4d7b356b1e14fe9dea4260a8082f54 |

外部归属核对的本地远程分支快照：`lease-saas/lease-contract` 为 `8b7ceaad06b3f4c7a7d72eb7d1805117c996def1`；`xxt-plateform/thctay-contract` 为 `3182bff5291ec7e1ef9a8f14f80fa46ba0c64bbc`；`xxt-plateform/intf-common` 为 `13f4fad4b0d3d1f68bf11f744f9d331a18abfeee`。

### 2026-10-30 · intf-paychannel 生产预警分析

- [分类、根因证据与处理建议](requirements/共享支付系统/20261019-代码审查与预警归类/2026-10-30-intf-paychannel生产预警分析.md)
- [原始错误与堆栈摘录](requirements/共享支付系统/20261019-代码审查与预警归类/2026-10-30-intf-paychannel生产预警原始信息.md)：补充 21 条代表性日志，覆盖原分析各类问题，保留时间、商户/订单、原文及事件 ID；敏感信息脱敏。
- 状态：只读诊断已归档；修复建议待实施。生产全部业务线，本文保留 179 条历史 ERROR 对应的待处理事项；已排除用户确认恢复的渠道故障，核验截至 2026-10-30 12:07 左右。


### WorkHub 临时分析 · 生成需求澄清项 · 需求 246

<!-- workhub-analysis:3dfad9332600a5fb362d592a1bf033da23b18a214f56aa8d3b005d6daa254fad -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：26
- 记录时间：2026-10-20T11:07:05.511155
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/保费分期/聚保融保费分期项目/2026/202610120004/历史版本/f2870855-e88d-4a22-93cf-a698cb06a658/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "确认存量自营项目的默认进件模式",
  "description" : "上线时，现有自营项目应初始化为“仅挂靠公司线上签约”，还是同时支持线上、线下两种模式？",
  "evidence" : "需求规定项目配置“进件模式”为必填多选，但只明确了存量订单统一标记为线上，未说明存量项目配置的初始值；现有 ProjectControl 尚无该字段。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "确认存量订单回填范围",
  "description" : "请明确需要统一标记为“挂靠公司线上签约”的自营项目编号、产品编号及订单状态范围。",
  "evidence" : "需求仅表述“存量自营项目订单”，未提供可执行的项目/产品清单；此前聚保融需求脚本仅按产品 PRD-JBR-A001 回填，不能代表本次自营范围。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "QUESTION",
  "title" : "确认企业与渠道建立关联的触发时点",
  "description" : "非实名认证企业应在点击“保存”后还是点击“提交”成功后与渠道建立关联？",
  "evidence" : "需求 2.2.3 写明完成【提交】后建立关联；现有 AuthEnterpriseServiceImpl.save 在暂存企业信息后即调用 generateChannelEnterpriseRelation，渠道端同时存在“保存”和“提交”按钮。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "QUESTION",
  "title" : "确认未认证企业的跨渠道复用规则",
  "description" : "同一统一社会信用代码已被一个渠道以“非实名认证”方式创建时，其他渠道应直接关联该企业、禁止新增，还是允许各自创建记录？",
  "evidence" : "需求未说明跨渠道规则；现有企业数据按统一社会信用代码全局判重，已认证企业可自动绑定渠道，但未认证企业的复用口径不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "QUESTION",
  "title" : "确认已被订单引用的未认证企业是否仍可编辑",
  "description" : "未认证企业被订单选用后，是否仍允许修改企业名称、统一社会信用代码、法人、经办人及证件附件？",
  "evidence" : "需求只说明未认证企业后续可编辑并发起认证；现有渠道端对 authStatus=NEW 的企业开放编辑，未限定是否已被订单引用。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "QUESTION",
  "title" : "确认订单中的企业信息取值口径",
  "description" : "企业主数据后续发生修改时，既有订单应继续使用选中时的企业信息快照，还是使用企业主数据的最新值？",
  "evidence" : "现有订单仅保存 companyCode/companyName，查询单位信息和营业执照时会根据 companyCode重新读取 crm_enterprise，存在历史订单随主数据变化的可能；需求未说明。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 6,
  "itemType" : "QUESTION",
  "title" : "确认提交预审后进件模式是否锁定",
  "description" : "订单提交预审成功后，是否一律禁止修改进件模式？",
  "evidence" : "需求说明提交预审后记录订单进件模式，并由其决定合同套系、签章和通知邮件，但未明确后续退回、编辑等场景能否改选。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 7,
  "itemType" : "QUESTION",
  "title" : "确认项目配置变更对草稿订单的处理",
  "description" : "草稿订单已选择某进件模式后，如果项目配置取消该模式，提交预审时应阻止提交还是继续沿用草稿中的原选择？",
  "evidence" : "需求说明可选项由当前项目配置决定，但未定义配置变更与既有草稿之间的生效规则。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 8,
  "itemType" : "QUESTION",
  "title" : "确认线上模式提交时的企业状态校验时点",
  "description" : "线上模式提交预审时，是否必须以后端当时的实时状态再次确认企业同时为“已认证、已授权”？",
  "evidence" : "需求定义线上模式只能选择已认证且已授权企业；现有前端仅在加载下拉框时按 SUCCESS 过滤，现有提交预审接口也会校验企业状态，但需求未明确是否以提交时状态为最终口径。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 9,
  "itemType" : "QUESTION",
  "title" : "提供线上签约合同套系清单",
  "description" : "请提供线上模式适用的合同模板清单，包括模板标识/版本及各合同签署方配置。",
  "evidence" : "需求 2.5 仅写“合同套系另行附件提供”，当前材料只有产品方案 PDF，未包含线上合同套系附件；现有签约流程按项目、产品和场景选模板，尚无进件模式维度。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 10,
  "itemType" : "QUESTION",
  "title" : "提供线下签约合同套系清单",
  "description" : "请提供线下模式适用的合同模板清单，包括模板标识/版本及除挂靠公司外的各签署方配置。",
  "evidence" : "需求只明确线下套系不调用挂靠公司签章，未提供具体合同清单和模板配置；所述“另行附件”不在本次材料中。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 11,
  "itemType" : "QUESTION",
  "title" : "确认声明书是否提供标准模板",
  "description" : "《授权委托暨业务合作声明书》是否有统一模板供渠道下载；如有，请提供模板版本及下载入口要求。",
  "evidence" : "需求只要求在线下模式上传该声明书，未提供文件模板或说明由渠道自行准备。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 12,
  "itemType" : "QUESTION",
  "title" : "确认声明书上传规格",
  "description" : "请明确《授权委托暨业务合作声明书》允许的文件格式、单次数量及大小限制。",
  "evidence" : "需求仅说明“只校验必传”，未说明上传规格；代码仓库中不同业务附件存在 PDF、图片等不同限制，无法据此唯一确定。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 13,
  "itemType" : "QUESTION",
  "title" : "确认通知邮件收件字段映射",
  "description" : "诚泰保理放款后，“客户邮箱”和“挂靠公司邮箱”分别以订单中的哪个字段为准？",
  "evidence" : "需求只描述收件对象；此前聚保融实现使用 ApplicationCustomer.email 和 companyEmail，而自营渠道端企业资料另有 enterpriseEmail 字段，字段映射未明确。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 14,
  "itemType" : "QUESTION",
  "title" : "确认运营端页面缓存覆盖范围",
  "description" : "“全局页面”是否指运营端全部带查询列表的页面；如有不缓存页面，请给出排除范围。",
  "evidence" : "需求只给出订单管理示例，未列出页面清单；insurance_admin 包含订单、融资、账单、对账、报表等大量查询页面。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 15,
  "itemType" : "QUESTION",
  "title" : "确认触发保留查询结果的返回方式",
  "description" : "缓存要求适用于列表进入详情后返回、浏览器后退、顶部标签切换和左侧菜单切换中的哪些场景？",
  "evidence" : "需求表述为“页面跳转后”和“切换回原页面”，未界定具体导航方式；现有运营端同时存在路由跳转和顶部标签页机制。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 16,
  "itemType" : "QUESTION",
  "title" : "确认需要保留的页面状态",
  "description" : "除查询条件和当前列表数据外，是否还需保留页码、每页条数、排序、当前页签及滚动位置？",
  "evidence" : "需求仅写“原检索查询结果不变”；现有订单列表包含分页、排序和查询条件，具体缓存边界不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 17,
  "itemType" : "QUESTION",
  "title" : "确认数据变更后的列表刷新规则",
  "description" : "在详情页完成审核、更新合同等会改变列表数据的操作后返回列表，应继续显示旧缓存，还是自动刷新一次？",
  "evidence" : "需求要求返回时“不刷新”，但现有部分操作成功后会主动执行 toQuery；若一律保留缓存，列表可能展示变更前状态。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "未实名认证企业的身份真实性风险",
  "description" : "线下模式允许未完成企业实名认证和静默签授权的企业进件，系统无法证明企业、法人及经办人信息真实有效，需确认接受由此产生的冒用和合规风险。",
  "evidence" : "需求 2.2、2.3.2 明确允许“未认证”或“未授权”企业创建线下模式订单。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "线下声明书仅校验存在的法律效力风险",
  "description" : "系统只校验声明书是否上传，不识别内容、签字、盖章及版本，错误文件、空白文件或无效签署也可能通过，需确认接受。",
  "evidence" : "需求 2.3 明确注明该附件“只校验必传”。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "企业主数据变化导致历史订单信息漂移风险",
  "description" : "如未锁定企业资料或未保存订单快照，企业后续编辑可能使历史订单展示、合同取值、附件和邮件地址发生变化，需确认接受相应追溯风险。",
  "evidence" : "现有 ApplicationProprietaryController.getCompany 和 SelfOperatedOrderService.addCompanyAttachments 会按 companyCode读取当前企业资料；未认证企业仍处于可编辑状态。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "RISK",
  "title" : "存量订单批量回填误标风险",
  "description" : "将全部存量自营订单统一标记为线上签约，可能误标历史例外数据，并影响后续合同、邮件、查询和统计分支，需确认接受批量口径。",
  "evidence" : "需求明确要求统一回填线上模式，但未提供逐单核验依据或项目、状态边界。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "RISK",
  "title" : "页面缓存造成陈旧数据风险",
  "description" : "运营端返回列表后不重新查询，可能继续展示已被其他用户或后台流程更新的旧状态，操作人员可能据此作出错误判断，需确认接受或明确强制刷新场景。",
  "evidence" : "需求要求页面跳转返回后保留原查询结果且不刷新；订单、融资等数据会被审核、签约、放款及异步流程持续更新。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "RISK",
  "title" : "全局页面缓存的浏览器资源风险",
  "description" : "若全部运营页面长期保活，页面组件、表格数据和监听器会持续占用浏览器内存，打开页面较多时可能出现性能下降，需确认接受缓存范围带来的资源开销。",
  "evidence" : "insurance_admin 已使用 keep-alive 和 cachedViews，并在标签关闭时才移除缓存；“全局页面”会显著扩大缓存数量。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 6,
  "itemType" : "RISK",
  "title" : "多系统版本不一致导致错误路由风险",
  "description" : "订单服务、渠道端、运营端、数据库字段及合同配置若未同时生效，可能出现进件模式为空、选项与后端校验不一致、选错合同套系或邮件收件人错误，需确认接受发布兼容风险。",
  "evidence" : "本需求同时涉及 scf-order、scf_channel_admin、insurance_admin、数据库回填及合同模板配置；此前聚保融代码对缺失进件模式存在按线上逻辑处理的路径。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### WorkHub 临时分析 · 生成需求澄清项 · 需求 248

<!-- workhub-analysis:cdc6ab9513ea1ec509e526135852e097bbca8e53e91f48138167ec3e2c6a2a60 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：27
- 记录时间：2026-10-20T11:11:15.946996
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130003/历史版本/794e1ef7-e09c-4137-8d12-1c011a13a118/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "确认新增接口字段名",
  "description" : "请确认新入账渠道字段的接口参数名；该字段不能直接复用现有 channel，需给出独立字段名。",
  "evidence" : "代码 CustomerPayApiRequest.channel 已表示“支付通道”且必填，支付校验及订单落库均在使用；需求材料仅称新增“渠道”字段，未提供技术字段名。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "确认入账渠道传值契约",
  "description" : "请明确新字段“合作方”“通汇”的实际传值，以及 null、空字符串是否均按“合作方/原逻辑”处理。",
  "evidence" : "结构化字段给出枚举“合作方、通汇”，附件又描述“合作方渠道传空、通汇传‘通汇’”，两处对合作方的传值表述不一致。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "QUESTION",
  "title" : "确认渠道与收款主体的对应关系",
  "description" : "请确认“合作方”和“通汇”分别对应哪个实际收款主体。",
  "evidence" : "需求背景称两个收款主体为“中恒、融易租”，渠道枚举却为“合作方、通汇”，材料未说明两组名称之间的映射。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "QUESTION",
  "title" : "确认空渠道的页面展示值",
  "description" : "请确认渠道为空的对账单在列表显示“合作方”还是留空。",
  "evidence" : "需求规定渠道传空按原逻辑生成，同时又要求列表展示“渠道”；未规定空值展示规则。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "QUESTION",
  "title" : "确认功能隔离范围",
  "description" : "请确认渠道拆单和新增展示字段是否仅对中恒项目 PRJ-ZH-001 生效。",
  "evidence" : "需求项目及合作方均指向中恒，但 /api/pay/apply 和 StatementGenerator 是多项目共用代码，当前生成逻辑未按中恒单独分支。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "QUESTION",
  "title" : "确认 T+1 的日期口径",
  "description" : "请确认“T+1生成”指下一自然日还是下一工作日。",
  "evidence" : "附件写明 T+1；代码同时支持 next_day（下一自然日）和 T+N（结合节假日配置计算），中恒现有线下正常还款及提前结清出账规则配置为 next_day。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 6,
  "itemType" : "QUESTION",
  "title" : "确认列表“客户实际还款”的计算口径",
  "description" : "请确认列表金额应取对账单所含支付订单交易金额去重汇总、对账单金额，还是其他业务口径。",
  "evidence" : "需求只给出字段名称，未定义公式；代码中支付订单有 transactionAmount，对账单另有 totalAmount、settlementAmount 等金额，口径不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 7,
  "itemType" : "QUESTION",
  "title" : "确认详情“客户实际还款”的分摊口径",
  "description" : "请确认详情每个资产和期次应展示该账单实际冲销金额，还是重复展示整笔支付订单金额。",
  "evidence" : "当前详情按资产和期次聚合 statement_resource；一笔提前结清支付可覆盖多个期次，需求未说明实际还款如何分摊到详情行。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 8,
  "itemType" : "QUESTION",
  "title" : "确认导出文件是否同步新增字段",
  "description" : "请确认列表导出和详情导出是否也必须包含“渠道”“客户实际还款”。",
  "evidence" : "两个页面均提供独立导出接口和 Excel 模板；需求仅写“列表/详情增加字段”，未明确导出范围。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 9,
  "itemType" : "QUESTION",
  "title" : "确认存量及过渡期数据处理范围",
  "description" : "请确认已生成对账单及上线前已受理但尚未生成对账单的数据是否需要补渠道和客户实际还款。",
  "evidence" : "现有 customer_order、statement_original_resource、statement_resource、statement 均没有本需求的入账渠道字段，历史请求也没有该值；需求未说明生效时间或补数范围。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 10,
  "itemType" : "QUESTION",
  "title" : "确认错误渠道的更正方式",
  "description" : "请确认合作方误传渠道后采用何种业务更正方式。",
  "evidence" : "当前支付申请按 paySn 做重复校验，已受理流水再次提交会报“支付流水重复”，代码没有渠道更正入口；需求材料未说明纠错流程。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "现有 channel 同名冲突风险",
  "description" : "若新入账渠道仍使用 channel 参数，将与既有支付通道发生覆盖或解析冲突，可能造成支付申请失败或支付通道路由错误，需确认不接受直接复用该字段。",
  "evidence" : "CustomerPayApiRequest.channel 当前必填；业务代码会将其解析为支付 ChannelEnum，线下场景也会把它保存为 channelName。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "空值无法区分漏传与合法合作方渠道",
  "description" : "若继续以空值代表合作方渠道，系统无法识别合作方漏传的通汇数据，漏传记录会按原逻辑并入合作方对账单。",
  "evidence" : "需求明确“渠道传空时按原逻辑生成”；没有其他字段可以判断该笔实际进入中恒还是通汇对应主体。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "上下游不同步上线会产生错单风险",
  "description" : "后端上线后若合作方尚未准确传送新渠道，通汇回款仍可能进入原对账单；该错分会直接影响运营核销拆分。",
  "evidence" : "拆单完全依赖支付申请新增字段，且空值被定义为兼容原逻辑；需求未提供双方切换时间及灰度方案。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "RISK",
  "title" : "存量数据无法可靠追溯渠道",
  "description" : "没有外部入账流水补充依据时，历史及过渡期数据只能按空渠道处理，无法可靠重建合作方与通汇渠道，需确认是否接受。",
  "evidence" : "当前订单、对账原始资源、对账资源和对账单实体均未保存该业务渠道；知识库与附件也未提供历史映射规则。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "RISK",
  "title" : "客户实际还款可能重复汇总",
  "description" : "若详情各期次重复展示整笔订单金额，或列表直接累加详情金额，提前结清等多期场景会重复计算客户实际还款，需在口径确定后确认该风险已消除或被接受。",
  "evidence" : "当前详情按资产及期次聚合，而对账资源通过 resourceSerialNo 关联同一支付订单；一笔订单可生成多个科目和多个期次明细。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "RISK",
  "title" : "代码核对基线不完整",
  "description" : "本次代码结论可能遗漏尚未同步到当前仓库的改动，需确认以可用基线继续评审，或补充目标分支后重新核对。",
  "evidence" : "结构化字段推荐 feature/reconciliation_opt_202610130003，但本地及现有远端引用中未找到该分支；assets-saps 当前位于无关的 feature/split_ratio_adjust_202609160002，前端位于 master。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### WorkHub 临时分析 · 生成研发评估 · 需求 248

<!-- workhub-analysis:14c3cb092f2d39360fe70e0be4f26f123d0f9870ed1cd9d7f4b72ea8857a16e2 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：96
- 记录时间：2026-10-20T12:50:06.652699
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130003/历史版本/242f8400-e261-4fd5-9659-7139f3bbb1c6/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : "ca-assets (24 repositories)",
  "requirementRawPath" : "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130003/历史版本/242f8400-e261-4fd5-9659-7139f3bbb1c6/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求涉及 assets-saps 与 assets-lease-admin，建议拆为 4 个可排期任务，预估合计 40h。核心链路为“支付申请采集入账渠道→支付订单及对账资源留痕→T+1 按渠道分组生成并列对账单→列表/详情展示渠道与客户实际还款”。现有代码已定位，但字段契约、金额口径、历史数据及导出范围尚未明确，整体置信度为 MEDIUM；本次仅完成只读评估，未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "支付申请接口新增独立的业务入账渠道字段，适用于中恒线下正常还款和线下提前结清；空值兼容原有处理，通汇渠道需明确传值。", "中恒同一合作方的线下正常还款、提前结清对账资源按入账渠道隔离，T+1 分别生成合作方渠道与通汇渠道的并列对账单。", "线下正常还款、提前结清对账单列表新增“渠道”和“客户实际还款”字段。", "线下正常还款、提前结清对账单详情新增“客户实际还款”字段。" ],
  "impactedModules" : [ "assets-saps / POST /api/pay/apply 支付申请、参数校验与 customer_order 落库", "assets-saps / 线下正常还款及提前结清对账原始资源生成", "assets-saps / statementGenerateSchedule 与对账单分组生成", "assets-saps / statement、statement_original_resource、statement_resource 数据模型及DDL", "assets-saps / 对账单列表、详情及Excel导出接口", "assets-lease-admin / 线下正常还款对账单页面", "assets-lease-admin / 线下提前结清对账单页面" ],
  "risks" : [ "CustomerPayApiRequest 已存在必填字段 channel，含义是支付通道；直接复用会与本需求的入账渠道发生同名及语义冲突。", "当前对账单生成SQL只按项目、类型、结算方、收款方和科目聚合，未纳入入账渠道；仅在页面查询时拼接渠道无法阻止不同收款主体混单。", "支付申请、customer_order、statement_original_resource、statement_resource、statement 均缺少独立的业务入账渠道留痕，渠道必须贯穿完整链路，否则无法稳定拆单或追溯。", "客户实际还款若直接连接 statement_resource 与 customer_order/customer_transaction 汇总，可能因同一订单拆成多个科目和多个期次而重复计算。", "公共支付申请接口和 StatementGenerator 被多个项目复用；若缺少 PRD-ZH-001/PRJ-ZH-001 及指定线下场景隔离，可能改变其他业务线的对账单数量。", "空渠道被定义为合法兼容值，会同时承载“合作方渠道”和“上游漏传”两种情况，存在错单且难以追溯的风险。", "现有 paySn 重复校验不允许已受理流水重新提交，若入账渠道传错，当前链路没有可复用的更正入口。", "上下游未同步切换时，新旧请求会并存；由于空值继续按原逻辑处理，可能在过渡期继续产生混合对账单。", "需求未明确历史订单及待生成资源的补数规则，存量数据无法从现有字段可靠反推出入账渠道。", "列表和详情均已有独立导出链路；若只修改页面和查询返回，Excel数据会与页面字段不一致。", "推荐分支 feature/reconciliation_opt_202610130003 在当前本地及远端引用中未发现；assets-saps 当前基线为 feature/split_ratio_adjust_202609160002，需先确认实际开发基线。" ],
  "questions" : [ "请确认新增接口字段的技术名称和取值契约：现有 channel 已表示支付通道，新的入账渠道是否采用独立字段；“合作方”应显式传值还是继续传空；“通汇”的接口编码是什么？", "请确认“合作方、通汇”与实际收款主体“中恒、融易租”的一一对应关系，以及空渠道在列表中的展示文案。", "功能是否仅对中恒产品 PRD-ZH-001、项目 PRJ-ZH-001 的线下正常还款和线下提前结清生效？", "附件中的 T+1 是下一自然日还是下一工作日？中恒当前两个场景配置为 next_day，即下一自然日。", "请确认“客户实际还款”口径：列表是否按对账单关联支付订单的 transaction_amount 去重汇总；详情是否按资产和期次取 customer_transaction.transaction_amount，提前结清跨多期时如何分摊？", "列表及详情Excel导出是否需要同步新增“渠道”“客户实际还款”字段？", "请确认生效日期及存量范围：仅处理上线后的新支付申请，还是需要为未出账资源、已生成对账单或历史订单补渠道；错误渠道是否需要提供更正或重建机制？" ],
  "developerPool" : [ "admin", "cairui", "hanmeng", "lvliuhao", "shihao", "xuewentao", "xujieni", "yinjun", "yufanghua", "zhiyuntao", "尹军", "曹彬", "江敏", "王文龙", "石浩" ],
  "workItems" : [ {
    "title" : "assets-saps 新增中恒支付申请入账渠道采集能力",
    "description" : "在不复用现有支付通道 channel 的前提下，为中恒线下正常还款和线下提前结清采集独立入账渠道，并保存到支付订单供对账资源生成使用。",
    "requirementChangePoint" : "支付申请接口新增独立的业务入账渠道字段，适用于中恒线下正常还款和线下提前结清。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/pay/apply", "CustomerPayApiRequest", "customer_order表", "PRD-ZH-001 中恒支付配置" ],
    "changePoints" : [ "1. 接口契约：在 CustomerPayApiRequest 增加独立入账渠道字段及说明，避免与现有必填支付通道 channel 冲突。", "2. 参数验证：仅在 PRD-ZH-001 的线下正常还款、线下提前结清场景识别和校验渠道；兼容空值，并拒绝未定义值。", "3. 配置/SQL：为 customer_order 增加入账渠道字段及索引评估，追加DDL并同步实体、映射和查询模型。", "4. 接口/服务：支付订单落库时保存原始渠道值，保证后续支付结果处理和对账资源生成仍可读取。", "5. 幂等/验证：保持 paySn 重复校验语义，补充空渠道、合作方渠道、通汇渠道、非法值及非中恒项目回归用例。", "6. 联调验证：形成上游字段名、编码、上线顺序及异常渠道更正约束。" ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130003/历史版本/242f8400-e261-4fd5-9659-7139f3bbb1c6/source.md", "附件/451-中恒-正常还款_提前结清对账单优化.docx", "assets-saps-model/src/main/java/com/lease/saps/model/api/pay/CustomerPayApiRequest.java:34", "assets-saps-controller/src/main/java/com/lease/saps/controller/api/CustomerPayApiController.java:39", "assets-saps-business/src/main/java/com/lease/saps/business/api/impl/CustomerPayApiBizServiceImpl.java:128", "assets-saps-business/src/main/java/com/lease/saps/business/strategy/apiPay/AbsPaymentApiStrategy.java:254", "assets-saps-model/src/main/java/com/lease/saps/model/entity/CustomerOrderEntity.java:108" ],
    "confidence" : "MEDIUM",
    "moduleName" : "支付申请与订单留痕",
    "relatedFiles" : [ "assets-saps-model/src/main/java/com/lease/saps/model/api/pay/CustomerPayApiRequest.java", "assets-saps-controller/src/main/java/com/lease/saps/controller/api/CustomerPayApiController.java", "assets-saps-business/src/main/java/com/lease/saps/business/api/impl/CustomerPayApiBizServiceImpl.java", "assets-saps-business/src/main/java/com/lease/saps/business/strategy/apiPay/AbsPaymentApiStrategy.java", "assets-saps-model/src/main/java/com/lease/saps/model/entity/CustomerOrderEntity.java", "assets-saps/dbscript/DDL" ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "shihao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖业务确认新增字段名、渠道编码及中恒场景隔离条件。",
    "risk" : "若直接复用 channel 或上游未同步上线，将导致支付通道解析异常或入账渠道无法区分。"
  }, {
    "title" : "assets-saps 支持中恒线下还款对账单按渠道并列生成",
    "description" : "将入账渠道从支付订单传递到对账原始资源和正式对账单，并把渠道加入中恒线下正常还款、提前结清的分组键，复用现有次日调度生成并列对账单。",
    "requirementChangePoint" : "中恒同一合作方的线下正常还款、提前结清对账资源按入账渠道隔离，T+1 分别生成并列对账单。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "statement_original_resource表", "statement_resource表", "statement表", "StatementGenerator", "statementGenerateSchedule", "PRJ-ZH-001 对账场景配置" ],
    "changePoints" : [ "1. 配置/SQL：为 statement_original_resource、statement_resource、statement 增加入账渠道字段，追加DDL并同步实体、DTO及复制转换逻辑。", "2. 接口/服务：线下正常还款和提前结清生成原始资源时，根据 resourceSerialNo/orderNo 读取 customer_order 入账渠道并留痕。", "3. 生成查询：waitGenerateStatement 查询结果增加渠道，SQL聚合键和 Java 分组键同步增加渠道维度。", "4. 对账单生成：仅对 PRJ-ZH-001 的指定线下场景按渠道生成并列对账单，并将渠道写入主单及正式明细。", "5. 任务调度：复用 statementGenerateSchedule，不新增任务；按确认后的 T+1 口径核对或调整中恒 statement_scene_rule 的 bill_date_rule。", "6. 幂等/验证：验证同项目、同合作方、同日、不同渠道生成两张对账单；同渠道仍合并；任务重跑不重复出单；其他项目分组行为保持不变。" ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ "附件/451-中恒-正常还款_提前结清对账单优化.docx", "assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java:242", "assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java:669", "assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java:740", "assets-saps-dao/src/main/resources/mapper/StatementOriginalResourceMapper.xml:447", "assets-saps-schedule/src/main/java/com/lease/saps/schedule/statement/StatementGenerateSchedule.java:27", "assets-saps-business/src/main/java/com/lease/saps/business/statement/resourceGen/RepaymentInterestPayOfflineResourceGen.java:52", "assets-saps-business/src/main/java/com/lease/saps/business/statement/resourceGen/AdvanceSettlementOfflineResourceGen.java:52", "assets-saps/dbscript/DML/V2_lease_phase_two_1210.sql:66" ],
    "confidence" : "MEDIUM",
    "moduleName" : "对账资源与渠道拆单",
    "relatedFiles" : [ "assets-saps-model/src/main/java/com/lease/saps/model/entity/statement/StatementOriginalResourceEntity.java", "assets-saps-model/src/main/java/com/lease/saps/model/entity/statement/StatementResourceEntity.java", "assets-saps-model/src/main/java/com/lease/saps/model/entity/statement/StatementEntity.java", "assets-saps-model/src/main/java/com/lease/saps/model/dto/statement/WaitStatementResourceDto.java", "assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java", "assets-saps-dao/src/main/resources/mapper/StatementOriginalResourceMapper.xml", "assets-saps-schedule/src/main/java/com/lease/saps/schedule/statement/StatementGenerateSchedule.java", "assets-saps/dbscript/DDL", "assets-saps/dbscript/DML/V2_lease_phase_two_1210.sql" ],
    "estimatedEffort" : "16h",
    "ownerUserName" : "lvliuhao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖“支付申请入账渠道采集能力”完成，并需先确认项目隔离、渠道映射及T+1口径。",
    "risk" : "StatementGenerator 和待生成资源SQL为多项目共用，分组维度修改不完整会继续混单，隔离不足则会影响其他项目。"
  }, {
    "title" : "assets-saps 补充中恒对账单渠道与客户实际还款查询能力",
    "description" : "为线下正常还款、提前结清对账单列表和详情返回渠道及客户实际还款，并按确认范围同步导出模型，确保金额聚合不因科目或期次展开而重复。",
    "requirementChangePoint" : "对账单列表新增渠道、客户实际还款，详情新增客户实际还款。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "GET /saps/statement/queryStatementPageV2", "GET /saps/statement/queryStatementDetailPageV2", "对账单列表导出", "对账单详情导出", "StatementResponseVoV2", "StatementDetailResponseVo" ],
    "changePoints" : [ "1. 金额口径：按确认规则建立对账单级和资产期次级客户实际还款取数逻辑，关联支付订单或客户交易时先按业务订单去重。", "2. 查询模型：StatementResponseVoV2 增加渠道、客户实际还款；StatementDetailResponseVo 增加客户实际还款。", "3. 列表接口：queryStatementPageV2 查询返回对账单渠道及对账单级客户实际还款。", "4. 详情接口：queryStatementDetailPageV2 按现有资产和期次分组返回客户实际还款，避免同一支付订单因多个科目重复汇总。", "5. 导出/核销：若产品确认包含导出，同步列表和详情Excel模板或导出VO；确认新增展示字段不改变现有核销金额及状态流转。", "6. 验证：覆盖正常还款单期、多期、提前结清跨期、同渠道多订单、两渠道并列单及空渠道兼容数据。" ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ "附件/451-中恒-正常还款_提前结清对账单优化.docx", "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java:214", "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java:237", "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java:942", "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java:998", "assets-saps-dao/src/main/resources/mapper/StatementMapper.xml:171", "assets-saps-dao/src/main/resources/mapper/StatementResourceMapper.xml:348", "assets-saps-service/src/main/java/com/lease/saps/service/statement/StatementResourceService.java:223", "assets-saps-model/src/main/java/com/lease/saps/model/entity/CustomerOrderEntity.java:118" ],
    "confidence" : "LOW",
    "moduleName" : "对账单查询与导出",
    "relatedFiles" : [ "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java", "assets-saps-service/src/main/java/com/lease/saps/service/statement/StatementService.java", "assets-saps-service/src/main/java/com/lease/saps/service/statement/StatementResourceService.java", "assets-saps-dao/src/main/resources/mapper/StatementMapper.xml", "assets-saps-dao/src/main/resources/mapper/StatementResourceMapper.xml", "assets-saps-model/src/main/java/com/lease/saps/model/vo/statement/StatementResponseVoV2.java", "assets-saps-model/src/main/java/com/lease/saps/model/vo/statement/StatementDetailResponseVo.java", "assets-saps-model/src/main/java/com/lease/saps/model/vo/statement/StatementDetailLineOffRepaymentExportVo.java" ],
    "estimatedEffort" : "12h",
    "ownerUserName" : "曹彬",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖渠道拆单数据落库；客户实际还款公式、详情分摊及导出范围确认后方可完成验收口径。",
    "risk" : "客户实际还款定义尚未给出，错误关联会在一个订单拆成多个科目或期次时造成重复汇总。"
  }, {
    "title" : "assets-lease-admin 展示中恒对账单渠道与客户实际还款字段",
    "description" : "在线下正常还款和线下提前结清对账单列表、详情弹窗中展示后端新增字段，并保持现有权限、分页、核销和导出入口不变。",
    "requirementChangePoint" : "对账单管理页面的线下正常还款、提前结清列表新增渠道和客户实际还款，详情新增客户实际还款。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "线下正常还款对账单页面", "线下提前结清对账单页面", "对账单详情弹窗" ],
    "changePoints" : [ "1. 页面/权限：两个列表的 columnConfigData 增加“渠道”“客户实际还款”列，不新增菜单或按钮权限。", "2. 页面/详情：两个页面的 tableColumns 增加“客户实际还款”列，复用 queryStatementDetail 组件。", "3. 展示规则：金额沿用现有元金额格式化方法；渠道空值按确认文案展示，未知枚举提供安全兜底。", "4. 导出/核销：回归列表及详情导出按钮、分页、查看明细和核销入口，确保新增列不参与核销提交。", "5. 验证：完成类型检查、变更文件ESLint及再次类型检查，并联调两类对账单的空渠道和通汇渠道数据。" ],
    "systemTags" : [ "assets-lease-admin" ],
    "evidenceRefs" : [ "附件/451-中恒-正常还款_提前结清对账单优化.docx", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineNormalRepaymentStatement.vue:228", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineNormalRepaymentStatement.vue:267", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineNormalRepaymentStatement.vue:410", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineEarlySettlementStatement.vue:224", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineEarlySettlementStatement.vue:263", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineEarlySettlementStatement.vue:406", "assets-lease-admin/src/views/usedCarLeaseback/statement/components/queryStatementDetail.vue:87", "assets-lease-admin/src/api/api_list.js:88" ],
    "confidence" : "MEDIUM",
    "moduleName" : "对账单管理前端",
    "relatedFiles" : [ "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineNormalRepaymentStatement.vue", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineEarlySettlementStatement.vue", "assets-lease-admin/src/views/usedCarLeaseback/statement/components/queryStatementDetail.vue", "assets-lease-admin/src/api/statement/statement.js", "assets-lease-admin/src/api/api_list.js" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "江敏",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖后端列表、详情字段名和空渠道展示规则确定。",
    "risk" : "若后端金额字段或渠道枚举在联调阶段变化，两个页面及导出验收字段需同步调整。"
  } ],
  "totalEstimatedEffort" : "40h",
  "developmentEstimatedEffort" : "40h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-20T12:50:06.602460",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 生成研发评估 · 需求 251

<!-- workhub-analysis:4cb0dd0fc550ea3f6b4d860872649b4fefda27acf90900c436030ba355b5b324 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：98
- 记录时间：2026-10-20T15:11:44.068780
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130002/历史版本/85ab2fba-a619-43ab-9435-a6cf88b1159d/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : "ca-assets (24 repositories)",
  "requirementRawPath" : "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130002/历史版本/85ab2fba-a619-43ab-9435-a6cf88b1159d/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求涉及 assets-saps 与 assets-lease-admin 两个系统，可复用现有二维码交易子方式和聚合扫码支付链路，无需改造 assets-payment 支付流程、数据库表或定时任务。建议拆为“还款台账线上二维码识别”和“资方账单二维码备注”两个可排期任务，合计约 16h；最终工时受二维码映射口径、多笔支付明细展示规则及导出范围确认影响。",
  "requirementChangePoints" : [ "在“创新保理 > 报表管理 > 还款台账”的还款方式筛选、列表结果中新增“线上-二维码”；该标识用于合作方调用业务系统聚合支付接口并由 C 端用户扫码完成支付的场景。", "还款台账的“线上-二维码”应同步覆盖分页查询和导出结果，并与现有“线上”“线下”“线下-二维码”保持可区分。", "在“创新保理 > 车辆租赁保理 > 资方账单”保留现有“还款方式”列，另新增“备注”列；二维码场景分别展示“线上-二维码”或“线下-二维码”，普通还款和未还款记录不误标。" ],
  "impactedModules" : [ "assets-saps", "assets-lease-admin" ],
  "risks" : [ "assets-saps 当前工作区位于 feature/split_ratio_adjust_202609160002，而聚合扫码支付能力已在 origin/master 的 a96b3a530 等提交中落地；实施时若未基于最新主干，可能遗漏 transaction_sub_mode=16 的支付链路并产生合并冲突。", "现有还款台账筛选存在编码语义不一致：前端和 SQL 使用 transaction_sub_mode=14 表示“线下-二维码”，但 ActualRepaymentPageQueryVO 注释仍写 34，扩展“线上-二维码”时需统一查询参数约定，避免与 payment_mode 混用。", "资方账单一行可能关联多条 repayment_plan_pay_detail；当前 AssetCapitalBillServiceImpl 直接取无明确排序的第一条明细，若出现普通还款与二维码还款混合，备注结果可能不稳定。", "资方账单导出使用二进制 Excel 模板 asset_capital_bill.xlsx；若需求要求导出备注，需同步模板占位符，否则页面与导出字段不一致。", "若将 transaction_sub_mode=12、13、16 全部标记为“线上-二维码”，可能改变历史扫码或微信支付数据的展示口径；若仅识别 16，则需业务确认其覆盖全部目标聚合支付场景。" ],
  "questions" : [ "“线上-二维码”是否严格对应 transaction_sub_mode=16（聚合扫码）？现有 12（扫码）和 13（微信支付）是否继续显示为普通“线上”？", "同一资方账单存在多笔支付明细且混合普通支付、线上二维码或线下二维码时，备注应按最近一笔、二维码优先，还是同时展示多个标识？", "资方账单新增“备注”是否需要同步进入导出文件？原始截图仅明确标注列表新增列，但现有资方账单提供导出能力。" ],
  "developerPool" : [ "admin", "cairui", "hanmeng", "lvliuhao", "shihao", "xuewentao", "xujieni", "yinjun", "yufanghua", "zhiyuntao", "尹军", "曹彬", "江敏", "王文龙", "石浩" ],
  "workItems" : [ {
    "title" : "assets-saps + assets-lease-admin 增加还款台账线上二维码识别与筛选能力",
    "description" : "复用现有聚合扫码交易子方式及还款台账查询链路，为目标扫码支付场景提供“线上-二维码”筛选、列表展示和导出展示；保持现有线上、线下及线下二维码口径不变。不新增表、定时任务、菜单或权限。",
    "requirementChangePoint" : "在还款台账新增“线上-二维码”还款方式，并覆盖查询、列表和导出。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "assets-saps", "assets-lease-admin" ],
    "changePoints" : [ "1. 统一还款方式参数口径，明确 transaction_sub_mode=14 的“线下-二维码”和目标聚合扫码子方式的“线上-二维码”映射，修正 QueryVO 中 34/14 不一致的注释或契约。", "2. 扩展还款台账分页查询条件和结果组装，使“线上-二维码”按交易子方式准确筛选、展示，同时避免普通 payment_mode=线上数据被整体改名。", "3. 扩展还款台账返回模型的中文展示规则，确保列表与 downloadPage 复用同一转换结果。", "4. 在还款台账页面的还款方式下拉框增加“线上-二维码”选项，并使用与后端契约一致的值。", "5. 回归导出中的“还款方式”列，验证线上二维码、线下二维码、普通线上和普通线下结果与列表一致。", "6. 补充场景识别与筛选测试；该需求仅查询展示，不涉及核销写入、幂等、任务调度、权限或数据库 DDL/DML。" ],
    "systemTags" : [ "assets-saps", "assets-lease-admin" ],
    "evidenceRefs" : [ "需求材料：raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130002/历史版本/85ab2fba-a619-43ab-9435-a6cf88b1159d/source.md", "原始附件：先行通-中恒二维码支付标识.docx；截图1明确在还款方式下拉框新增“线上-二维码”", "assets-lease-admin/src/views/usedCarLeaseback/reportForm/realPayment.vue：现有还款方式筛选、paymentModeCn 列及“线下-二维码”选项", "assets-lease-admin/src/api/api_list.js：还款台账分页和导出接口分别为 /manage/report/repayment/page、/page/download", "assets-saps/assets-saps-dao/src/main/resources/mapper/RepaymentPlanPaySubjectDetailMapper.xml：查询已关联 customer_order.transaction_sub_mode，现有特殊筛选仅处理值14", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/RepaymentReportService.java：分页结果对 QRCODE 做线下二维码转换，导出复用 page() 结果", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/enums/TransactionSubModeEnum.java：现有 QRCODE=14、UNION_SCAN_CODE=16", "assets-saps Git 提交 a96b3a530：聚合支付接口已支持 transaction_sub_mode=16 并透传二维码信息", "assets-payment Git 提交 60a3349：中金聚合扫码支付模式及路由已实现，无需在本需求重复改造支付链路" ],
    "confidence" : "MEDIUM",
    "moduleName" : "还款台账",
    "relatedFiles" : [ "assets-lease-admin/src/views/usedCarLeaseback/reportForm/realPayment.vue", "assets-lease-admin/src/api/api_list.js", "assets-saps/assets-saps-controller/src/main/java/com/lease/saps/controller/manage/report/RepaymentReportController.java", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/RepaymentReportService.java", "assets-saps/assets-saps-dao/src/main/resources/mapper/RepaymentPlanPaySubjectDetailMapper.xml", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/dto/manage/report/ActualRepaymentPageDataVO.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/dto/manage/report/ActualRepaymentPageQueryVO.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/enums/TransactionSubModeEnum.java", "assets-saps/assets-saps-model/src/test/java/com/lease/saps/model/report/ActualRepaymentPageDataVOTest.java" ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "yinjun",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "实施前确认“线上-二维码”对应的 transaction_sub_mode 范围，并以包含聚合扫码支付提交的最新主干为基线。",
    "risk" : "若二维码子方式范围未确认，可能误标历史扫码/微信支付数据；筛选参数继续混用 payment_mode 与 transaction_sub_mode 也会导致查询结果偏差。"
  }, {
    "title" : "assets-saps + assets-lease-admin 增加资方账单二维码来源备注能力",
    "description" : "在资方账单现有“还款方式”之外新增只读“备注”，根据实际还款明细的交易子方式展示“线上-二维码”或“线下-二维码”。复用 repayment_plan_pay_detail.transaction_sub_mode，不改变支付、核销、清分、账单状态及历史数据。",
    "requirementChangePoint" : "在资方账单新增备注列，二维码还款分别标识为“线上-二维码”或“线下-二维码”。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "assets-saps", "assets-lease-admin" ],
    "changePoints" : [ "1. 在资方账单返回模型增加二维码备注字段，并明确无二维码支付明细时返回空值。", "2. 调整资方账单查询结果组装，基于 repayment_plan_pay_detail.transaction_sub_mode 生成线上/线下二维码备注，不覆盖现有 paymentModeCn。", "3. 对同一账单多笔支付明细建立确定性的归并规则，禁止沿用当前无排序取第一条明细的方式生成备注。", "4. 在资方账单列表的“还款方式”列后新增“备注”列，展示后端返回的二维码标识，不新增菜单和按钮权限。", "5. 若确认导出需要同步，在 asset_capital_bill.xlsx 增加“备注”表头及字段占位符，保证列表与导出口径一致。", "6. 补充无还款、普通还款、线上二维码、线下二维码及多明细混合场景测试；该字段为只读派生结果，不新增数据库字段、核销动作、幂等写入或定时任务。" ],
    "systemTags" : [ "assets-saps", "assets-lease-admin" ],
    "evidenceRefs" : [ "需求材料：raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130002/历史版本/85ab2fba-a619-43ab-9435-a6cf88b1159d/source.md", "原始附件：先行通-中恒二维码支付标识.docx；截图2明确在现有“还款方式”右侧新增“备注”列，并展示“线下-二维码”", "assets-lease-admin/src/views/managementBill/index.vue：资方账单页面已有 paymentModeCn“还款方式”列，尚无备注列", "assets-lease-admin/src/api/api_list.js：资方账单分页和导出接口为 /asset/capital/bill/page、/page/download", "assets-saps/assets-saps-controller/src/main/java/com/lease/saps/controller/manage/bill/asset/AssetCapitalBillController.java：列表和导出共用 AssetCapitalBillService.pageQuery", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/asset/impl/AssetCapitalBillServiceImpl.java：已批量获取账单支付明细，但当前仅取每个账单第一条明细的 paymentMode", "assets-saps/assets-saps-dao/src/main/resources/mapper/RepaymentPlanPayDetailMapper.xml：listByBillNos 返回支付明细及 transaction_sub_mode，未定义排序", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/entity/RepaymentPlanPayDetailEntity.java：已有 transactionSubMode 字段，无需新增表字段", "assets-saps/assets-saps-start/src/main/resources/excel/asset_capital_bill.xlsx：现有导出模板最后一列为“还款方式”，尚无“备注”占位符" ],
    "confidence" : "MEDIUM",
    "moduleName" : "资方账单",
    "relatedFiles" : [ "assets-lease-admin/src/views/managementBill/index.vue", "assets-lease-admin/src/api/api_list.js", "assets-saps/assets-saps-controller/src/main/java/com/lease/saps/controller/manage/bill/asset/AssetCapitalBillController.java", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/asset/impl/AssetCapitalBillServiceImpl.java", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/impl/RepaymentPlanPayDetailServiceImpl.java", "assets-saps/assets-saps-dao/src/main/resources/mapper/RepaymentPlanPayDetailMapper.xml", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/vo/bill/CapitalBillPageRespDataVO.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/entity/RepaymentPlanPayDetailEntity.java", "assets-saps/assets-saps-start/src/main/resources/excel/asset_capital_bill.xlsx" ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "yinjun",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖统一二维码子方式口径及多笔支付明细的备注归并规则；可与还款台账任务并行开发。",
    "risk" : "当前资方账单按账单号聚合展示而支付明细可能为一对多，若未先明确混合支付规则，页面和导出可能出现不稳定或不完整的备注。"
  } ],
  "totalEstimatedEffort" : "16h",
  "developmentEstimatedEffort" : "16h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-20T15:11:44.006053",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 251

<!-- workhub-analysis:027ccb1be2b4c5337cde2221d646cbd70c24226b9bfa920dae1d34998e888c32 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：98
- 记录时间：2026-10-20T15:37:42.394500
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130002/历史版本/85ab2fba-a619-43ab-9435-a6cf88b1159d/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : "ca-assets (24 repositories)",
  "requirementRawPath" : "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130002/历史版本/85ab2fba-a619-43ab-9435-a6cf88b1159d/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求涉及 assets-saps 与 assets-lease-admin 两个系统，可复用现有二维码交易子方式和聚合扫码支付链路，无需改造 assets-payment 支付流程、数据库表或定时任务。建议拆为“还款台账线上二维码识别”和“资方账单二维码备注”两个可排期任务，合计约 16h；最终工时受二维码映射口径、多笔支付明细展示规则及导出范围确认影响。",
  "requirementChangePoints" : [ "在“创新保理 > 报表管理 > 还款台账”的还款方式筛选、列表结果中新增“线上-二维码”；该标识用于合作方调用业务系统聚合支付接口并由 C 端用户扫码完成支付的场景。", "还款台账的“线上-二维码”应同步覆盖分页查询和导出结果，并与现有“线上”“线下”“线下-二维码”保持可区分。", "在“创新保理 > 车辆租赁保理 > 资方账单”保留现有“还款方式”列，另新增“备注”列；二维码场景分别展示“线上-二维码”或“线下-二维码”，普通还款和未还款记录不误标。", "在还款台账新增“线上-二维码”还款方式，并覆盖查询、列表和导出。" ],
  "impactedModules" : [ "assets-saps", "assets-lease-admin" ],
  "risks" : [ "assets-saps 当前工作区位于 feature/split_ratio_adjust_202609160002，而聚合扫码支付能力已在 origin/master 的 a96b3a530 等提交中落地；实施时若未基于最新主干，可能遗漏 transaction_sub_mode=16 的支付链路并产生合并冲突。", "现有还款台账筛选存在编码语义不一致：前端和 SQL 使用 transaction_sub_mode=14 表示“线下-二维码”，但 ActualRepaymentPageQueryVO 注释仍写 34，扩展“线上-二维码”时需统一查询参数约定，避免与 payment_mode 混用。", "资方账单一行可能关联多条 repayment_plan_pay_detail；当前 AssetCapitalBillServiceImpl 直接取无明确排序的第一条明细，若出现普通还款与二维码还款混合，备注结果可能不稳定。", "资方账单导出使用二进制 Excel 模板 asset_capital_bill.xlsx；若需求要求导出备注，需同步模板占位符，否则页面与导出字段不一致。", "若将 transaction_sub_mode=12、13、16 全部标记为“线上-二维码”，可能改变历史扫码或微信支付数据的展示口径；若仅识别 16，则需业务确认其覆盖全部目标聚合支付场景。" ],
  "questions" : [ "“线上-二维码”是否严格对应 transaction_sub_mode=16（聚合扫码）？现有 12（扫码）和 13（微信支付）是否继续显示为普通“线上”？", "同一资方账单存在多笔支付明细且混合普通支付、线上二维码或线下二维码时，备注应按最近一笔、二维码优先，还是同时展示多个标识？", "资方账单新增“备注”是否需要同步进入导出文件？原始截图仅明确标注列表新增列，但现有资方账单提供导出能力。" ],
  "developerPool" : [ "admin", "cairui", "hanmeng", "lvliuhao", "shihao", "xuewentao", "xujieni", "yinjun", "yufanghua", "zhiyuntao", "尹军", "曹彬", "江敏", "王文龙", "石浩" ],
  "workItems" : [ {
    "title" : "assets-saps + assets-lease-admin 增加还款台账线上二维码识别与筛选能力和增加资方账单二维码来源备注能力",
    "description" : "复用现有聚合扫码交易子方式及还款台账查询链路，为目标扫码支付场景提供“线上-二维码”筛选、列表展示和导出展示；保持现有线上、线下及线下二维码口径不变。不新增表、定时任务、菜单或权限。",
    "requirementChangePoint" : "在还款台账新增“线上-二维码”还款方式，并覆盖查询、列表和导出。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "assets-saps", "assets-lease-admin" ],
    "changePoints" : [ "1. 统一还款方式参数口径，明确 transaction_sub_mode=14 的“线下-二维码”和目标聚合扫码子方式的“线上-二维码”映射，修正 QueryVO 中 34/14 不一致的注释或契约。", "2. 扩展还款台账分页查询条件和结果组装，使“线上-二维码”按交易子方式准确筛选、展示，同时避免普通 payment_mode=线上数据被整体改名。", "3. 扩展还款台账返回模型的中文展示规则，确保列表与 downloadPage 复用同一转换结果。", "4. 在还款台账页面的还款方式下拉框增加“线上-二维码”选项，并使用与后端契约一致的值。", "5. 回归导出中的“还款方式”列，验证线上二维码、线下二维码、普通线上和普通线下结果与列表一致。", "6. 补充场景识别与筛选测试；该需求仅查询展示，不涉及核销写入、幂等、任务调度、权限或数据库 DDL/DML。" ],
    "systemTags" : [ "assets-saps", "assets-lease-admin" ],
    "evidenceRefs" : [ "需求材料：raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130002/历史版本/85ab2fba-a619-43ab-9435-a6cf88b1159d/source.md", "原始附件：先行通-中恒二维码支付标识.docx；截图1明确在还款方式下拉框新增“线上-二维码”", "assets-lease-admin/src/views/usedCarLeaseback/reportForm/realPayment.vue：现有还款方式筛选、paymentModeCn 列及“线下-二维码”选项", "assets-lease-admin/src/api/api_list.js：还款台账分页和导出接口分别为 /manage/report/repayment/page、/page/download", "assets-saps/assets-saps-dao/src/main/resources/mapper/RepaymentPlanPaySubjectDetailMapper.xml：查询已关联 customer_order.transaction_sub_mode，现有特殊筛选仅处理值14", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/RepaymentReportService.java：分页结果对 QRCODE 做线下二维码转换，导出复用 page() 结果", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/enums/TransactionSubModeEnum.java：现有 QRCODE=14、UNION_SCAN_CODE=16", "assets-saps Git 提交 a96b3a530：聚合支付接口已支持 transaction_sub_mode=16 并透传二维码信息", "assets-payment Git 提交 60a3349：中金聚合扫码支付模式及路由已实现，无需在本需求重复改造支付链路" ],
    "confidence" : "MEDIUM",
    "moduleName" : "还款台账",
    "relatedFiles" : [ "assets-lease-admin/src/views/usedCarLeaseback/reportForm/realPayment.vue", "assets-lease-admin/src/api/api_list.js", "assets-saps/assets-saps-controller/src/main/java/com/lease/saps/controller/manage/report/RepaymentReportController.java", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/RepaymentReportService.java", "assets-saps/assets-saps-dao/src/main/resources/mapper/RepaymentPlanPaySubjectDetailMapper.xml", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/dto/manage/report/ActualRepaymentPageDataVO.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/dto/manage/report/ActualRepaymentPageQueryVO.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/enums/TransactionSubModeEnum.java", "assets-saps/assets-saps-model/src/test/java/com/lease/saps/model/report/ActualRepaymentPageDataVOTest.java" ],
    "estimatedEffort" : "12h",
    "ownerUserName" : "cairui",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "实施前确认“线上-二维码”对应的 transaction_sub_mode 范围，并以包含聚合扫码支付提交的最新主干为基线。",
    "risk" : "若二维码子方式范围未确认，可能误标历史扫码/微信支付数据；筛选参数继续混用 payment_mode 与 transaction_sub_mode 也会导致查询结果偏差。"
  } ],
  "totalEstimatedEffort" : "20h",
  "developmentEstimatedEffort" : "12h",
  "testingEstimatedEffort" : "8h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-20T15:11:44.006053",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 248

<!-- workhub-analysis:d85aa44255bc0944a30873de66668c52d63f71689ad93c1267bc7f78fa991f44 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：96
- 记录时间：2026-10-20T15:40:28.178122
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130003/历史版本/242f8400-e261-4fd5-9659-7139f3bbb1c6/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : "ca-assets (24 repositories)",
  "requirementRawPath" : "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130003/历史版本/242f8400-e261-4fd5-9659-7139f3bbb1c6/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求涉及 assets-saps 与 assets-lease-admin，建议拆为 4 个可排期任务，预估合计 40h。核心链路为“支付申请采集入账渠道→支付订单及对账资源留痕→T+1 按渠道分组生成并列对账单→列表/详情展示渠道与客户实际还款”。现有代码已定位，但字段契约、金额口径、历史数据及导出范围尚未明确，整体置信度为 MEDIUM；本次仅完成只读评估，未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "支付申请接口新增独立的业务入账渠道字段，适用于中恒线下正常还款和线下提前结清；空值兼容原有处理，通汇渠道需明确传值。", "中恒同一合作方的线下正常还款、提前结清对账资源按入账渠道隔离，T+1 分别生成合作方渠道与通汇渠道的并列对账单。", "线下正常还款、提前结清对账单列表新增“渠道”和“客户实际还款”字段。", "线下正常还款、提前结清对账单详情新增“客户实际还款”字段。", "支付申请接口新增独立的业务入账渠道字段，适用于中恒线下正常还款和线下提前结清。", "中恒同一合作方的线下正常还款、提前结清对账资源按入账渠道隔离，T+1 分别生成并列对账单。", "对账单列表新增渠道、客户实际还款，详情新增客户实际还款。", "对账单管理页面的线下正常还款、提前结清列表新增渠道和客户实际还款，详情新增客户实际还款。" ],
  "impactedModules" : [ "assets-saps / POST /api/pay/apply 支付申请、参数校验与 customer_order 落库", "assets-saps / 线下正常还款及提前结清对账原始资源生成", "assets-saps / statementGenerateSchedule 与对账单分组生成", "assets-saps / statement、statement_original_resource、statement_resource 数据模型及DDL", "assets-saps / 对账单列表、详情及Excel导出接口", "assets-lease-admin / 线下正常还款对账单页面", "assets-lease-admin / 线下提前结清对账单页面" ],
  "risks" : [ "CustomerPayApiRequest 已存在必填字段 channel，含义是支付通道；直接复用会与本需求的入账渠道发生同名及语义冲突。", "当前对账单生成SQL只按项目、类型、结算方、收款方和科目聚合，未纳入入账渠道；仅在页面查询时拼接渠道无法阻止不同收款主体混单。", "支付申请、customer_order、statement_original_resource、statement_resource、statement 均缺少独立的业务入账渠道留痕，渠道必须贯穿完整链路，否则无法稳定拆单或追溯。", "客户实际还款若直接连接 statement_resource 与 customer_order/customer_transaction 汇总，可能因同一订单拆成多个科目和多个期次而重复计算。", "公共支付申请接口和 StatementGenerator 被多个项目复用；若缺少 PRD-ZH-001/PRJ-ZH-001 及指定线下场景隔离，可能改变其他业务线的对账单数量。", "空渠道被定义为合法兼容值，会同时承载“合作方渠道”和“上游漏传”两种情况，存在错单且难以追溯的风险。", "现有 paySn 重复校验不允许已受理流水重新提交，若入账渠道传错，当前链路没有可复用的更正入口。", "上下游未同步切换时，新旧请求会并存；由于空值继续按原逻辑处理，可能在过渡期继续产生混合对账单。", "需求未明确历史订单及待生成资源的补数规则，存量数据无法从现有字段可靠反推出入账渠道。", "列表和详情均已有独立导出链路；若只修改页面和查询返回，Excel数据会与页面字段不一致。", "推荐分支 feature/reconciliation_opt_202610130003 在当前本地及远端引用中未发现；assets-saps 当前基线为 feature/split_ratio_adjust_202609160002，需先确认实际开发基线。" ],
  "questions" : [ "请确认新增接口字段的技术名称和取值契约：现有 channel 已表示支付通道，新的入账渠道是否采用独立字段；“合作方”应显式传值还是继续传空；“通汇”的接口编码是什么？", "请确认“合作方、通汇”与实际收款主体“中恒、融易租”的一一对应关系，以及空渠道在列表中的展示文案。", "功能是否仅对中恒产品 PRD-ZH-001、项目 PRJ-ZH-001 的线下正常还款和线下提前结清生效？", "附件中的 T+1 是下一自然日还是下一工作日？中恒当前两个场景配置为 next_day，即下一自然日。", "请确认“客户实际还款”口径：列表是否按对账单关联支付订单的 transaction_amount 去重汇总；详情是否按资产和期次取 customer_transaction.transaction_amount，提前结清跨多期时如何分摊？", "列表及详情Excel导出是否需要同步新增“渠道”“客户实际还款”字段？", "请确认生效日期及存量范围：仅处理上线后的新支付申请，还是需要为未出账资源、已生成对账单或历史订单补渠道；错误渠道是否需要提供更正或重建机制？" ],
  "developerPool" : [ "admin", "cairui", "hanmeng", "lvliuhao", "shihao", "xuewentao", "xujieni", "yinjun", "yufanghua", "zhiyuntao", "尹军", "曹彬", "江敏", "王文龙", "石浩" ],
  "workItems" : [ {
    "title" : "assets-saps 新增中恒支付申请入账渠道采集能力",
    "description" : "在不复用现有支付通道 channel 的前提下，为中恒线下正常还款和线下提前结清采集独立入账渠道，并保存到支付订单供对账资源生成使用。",
    "requirementChangePoint" : "支付申请接口新增独立的业务入账渠道字段，适用于中恒线下正常还款和线下提前结清。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/pay/apply", "CustomerPayApiRequest", "customer_order表", "PRD-ZH-001 中恒支付配置" ],
    "changePoints" : [ "1. 接口契约：在 CustomerPayApiRequest 增加独立入账渠道字段及说明，避免与现有必填支付通道 channel 冲突。", "2. 参数验证：仅在 PRD-ZH-001 的线下正常还款、线下提前结清场景识别和校验渠道；兼容空值，并拒绝未定义值。", "3. 配置/SQL：为 customer_order 增加入账渠道字段及索引评估，追加DDL并同步实体、映射和查询模型。", "4. 接口/服务：支付订单落库时保存原始渠道值，保证后续支付结果处理和对账资源生成仍可读取。", "5. 幂等/验证：保持 paySn 重复校验语义，补充空渠道、合作方渠道、通汇渠道、非法值及非中恒项目回归用例。", "6. 联调验证：形成上游字段名、编码、上线顺序及异常渠道更正约束。" ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610130003/历史版本/242f8400-e261-4fd5-9659-7139f3bbb1c6/source.md", "附件/451-中恒-正常还款_提前结清对账单优化.docx", "assets-saps-model/src/main/java/com/lease/saps/model/api/pay/CustomerPayApiRequest.java:34", "assets-saps-controller/src/main/java/com/lease/saps/controller/api/CustomerPayApiController.java:39", "assets-saps-business/src/main/java/com/lease/saps/business/api/impl/CustomerPayApiBizServiceImpl.java:128", "assets-saps-business/src/main/java/com/lease/saps/business/strategy/apiPay/AbsPaymentApiStrategy.java:254", "assets-saps-model/src/main/java/com/lease/saps/model/entity/CustomerOrderEntity.java:108" ],
    "confidence" : "MEDIUM",
    "moduleName" : "支付申请与订单留痕",
    "relatedFiles" : [ "assets-saps-model/src/main/java/com/lease/saps/model/api/pay/CustomerPayApiRequest.java", "assets-saps-controller/src/main/java/com/lease/saps/controller/api/CustomerPayApiController.java", "assets-saps-business/src/main/java/com/lease/saps/business/api/impl/CustomerPayApiBizServiceImpl.java", "assets-saps-business/src/main/java/com/lease/saps/business/strategy/apiPay/AbsPaymentApiStrategy.java", "assets-saps-model/src/main/java/com/lease/saps/model/entity/CustomerOrderEntity.java", "assets-saps/dbscript/DDL" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "cairui",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖业务确认新增字段名、渠道编码及中恒场景隔离条件。",
    "risk" : "若直接复用 channel 或上游未同步上线，将导致支付通道解析异常或入账渠道无法区分。"
  }, {
    "title" : "assets-saps 支持中恒线下还款对账单按渠道并列生成",
    "description" : "将入账渠道从支付订单传递到对账原始资源和正式对账单，并把渠道加入中恒线下正常还款、提前结清的分组键，复用现有次日调度生成并列对账单。",
    "requirementChangePoint" : "中恒同一合作方的线下正常还款、提前结清对账资源按入账渠道隔离，T+1 分别生成并列对账单。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "statement_original_resource表", "statement_resource表", "statement表", "StatementGenerator", "statementGenerateSchedule", "PRJ-ZH-001 对账场景配置" ],
    "changePoints" : [ "1. 配置/SQL：为 statement_original_resource、statement_resource、statement 增加入账渠道字段，追加DDL并同步实体、DTO及复制转换逻辑。", "2. 接口/服务：线下正常还款和提前结清生成原始资源时，根据 resourceSerialNo/orderNo 读取 customer_order 入账渠道并留痕。", "3. 生成查询：waitGenerateStatement 查询结果增加渠道，SQL聚合键和 Java 分组键同步增加渠道维度。", "4. 对账单生成：仅对 PRJ-ZH-001 的指定线下场景按渠道生成并列对账单，并将渠道写入主单及正式明细。", "5. 任务调度：复用 statementGenerateSchedule，不新增任务；按确认后的 T+1 口径核对或调整中恒 statement_scene_rule 的 bill_date_rule。", "6. 幂等/验证：验证同项目、同合作方、同日、不同渠道生成两张对账单；同渠道仍合并；任务重跑不重复出单；其他项目分组行为保持不变。" ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ "附件/451-中恒-正常还款_提前结清对账单优化.docx", "assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java:242", "assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java:669", "assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java:740", "assets-saps-dao/src/main/resources/mapper/StatementOriginalResourceMapper.xml:447", "assets-saps-schedule/src/main/java/com/lease/saps/schedule/statement/StatementGenerateSchedule.java:27", "assets-saps-business/src/main/java/com/lease/saps/business/statement/resourceGen/RepaymentInterestPayOfflineResourceGen.java:52", "assets-saps-business/src/main/java/com/lease/saps/business/statement/resourceGen/AdvanceSettlementOfflineResourceGen.java:52", "assets-saps/dbscript/DML/V2_lease_phase_two_1210.sql:66" ],
    "confidence" : "MEDIUM",
    "moduleName" : "对账资源与渠道拆单",
    "relatedFiles" : [ "assets-saps-model/src/main/java/com/lease/saps/model/entity/statement/StatementOriginalResourceEntity.java", "assets-saps-model/src/main/java/com/lease/saps/model/entity/statement/StatementResourceEntity.java", "assets-saps-model/src/main/java/com/lease/saps/model/entity/statement/StatementEntity.java", "assets-saps-model/src/main/java/com/lease/saps/model/dto/statement/WaitStatementResourceDto.java", "assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java", "assets-saps-dao/src/main/resources/mapper/StatementOriginalResourceMapper.xml", "assets-saps-schedule/src/main/java/com/lease/saps/schedule/statement/StatementGenerateSchedule.java", "assets-saps/dbscript/DDL", "assets-saps/dbscript/DML/V2_lease_phase_two_1210.sql" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "cairui",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖“支付申请入账渠道采集能力”完成，并需先确认项目隔离、渠道映射及T+1口径。",
    "risk" : "StatementGenerator 和待生成资源SQL为多项目共用，分组维度修改不完整会继续混单，隔离不足则会影响其他项目。"
  }, {
    "title" : "assets-saps 补充中恒对账单渠道与客户实际还款查询能力",
    "description" : "为线下正常还款、提前结清对账单列表和详情返回渠道及客户实际还款，并按确认范围同步导出模型，确保金额聚合不因科目或期次展开而重复。",
    "requirementChangePoint" : "对账单列表新增渠道、客户实际还款，详情新增客户实际还款。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "GET /saps/statement/queryStatementPageV2", "GET /saps/statement/queryStatementDetailPageV2", "对账单列表导出", "对账单详情导出", "StatementResponseVoV2", "StatementDetailResponseVo" ],
    "changePoints" : [ "1. 金额口径：按确认规则建立对账单级和资产期次级客户实际还款取数逻辑，关联支付订单或客户交易时先按业务订单去重。", "2. 查询模型：StatementResponseVoV2 增加渠道、客户实际还款；StatementDetailResponseVo 增加客户实际还款。", "3. 列表接口：queryStatementPageV2 查询返回对账单渠道及对账单级客户实际还款。", "4. 详情接口：queryStatementDetailPageV2 按现有资产和期次分组返回客户实际还款，避免同一支付订单因多个科目重复汇总。", "5. 导出/核销：若产品确认包含导出，同步列表和详情Excel模板或导出VO；确认新增展示字段不改变现有核销金额及状态流转。", "6. 验证：覆盖正常还款单期、多期、提前结清跨期、同渠道多订单、两渠道并列单及空渠道兼容数据。" ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ "附件/451-中恒-正常还款_提前结清对账单优化.docx", "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java:214", "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java:237", "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java:942", "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java:998", "assets-saps-dao/src/main/resources/mapper/StatementMapper.xml:171", "assets-saps-dao/src/main/resources/mapper/StatementResourceMapper.xml:348", "assets-saps-service/src/main/java/com/lease/saps/service/statement/StatementResourceService.java:223", "assets-saps-model/src/main/java/com/lease/saps/model/entity/CustomerOrderEntity.java:118" ],
    "confidence" : "LOW",
    "moduleName" : "对账单查询与导出",
    "relatedFiles" : [ "assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java", "assets-saps-service/src/main/java/com/lease/saps/service/statement/StatementService.java", "assets-saps-service/src/main/java/com/lease/saps/service/statement/StatementResourceService.java", "assets-saps-dao/src/main/resources/mapper/StatementMapper.xml", "assets-saps-dao/src/main/resources/mapper/StatementResourceMapper.xml", "assets-saps-model/src/main/java/com/lease/saps/model/vo/statement/StatementResponseVoV2.java", "assets-saps-model/src/main/java/com/lease/saps/model/vo/statement/StatementDetailResponseVo.java", "assets-saps-model/src/main/java/com/lease/saps/model/vo/statement/StatementDetailLineOffRepaymentExportVo.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "cairui",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖渠道拆单数据落库；客户实际还款公式、详情分摊及导出范围确认后方可完成验收口径。",
    "risk" : "客户实际还款定义尚未给出，错误关联会在一个订单拆成多个科目或期次时造成重复汇总。"
  }, {
    "title" : "assets-lease-admin 展示中恒对账单渠道与客户实际还款字段",
    "description" : "在线下正常还款和线下提前结清对账单列表、详情弹窗中展示后端新增字段，并保持现有权限、分页、核销和导出入口不变。",
    "requirementChangePoint" : "对账单管理页面的线下正常还款、提前结清列表新增渠道和客户实际还款，详情新增客户实际还款。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "线下正常还款对账单页面", "线下提前结清对账单页面", "对账单详情弹窗" ],
    "changePoints" : [ "1. 页面/权限：两个列表的 columnConfigData 增加“渠道”“客户实际还款”列，不新增菜单或按钮权限。", "2. 页面/详情：两个页面的 tableColumns 增加“客户实际还款”列，复用 queryStatementDetail 组件。", "3. 展示规则：金额沿用现有元金额格式化方法；渠道空值按确认文案展示，未知枚举提供安全兜底。", "4. 导出/核销：回归列表及详情导出按钮、分页、查看明细和核销入口，确保新增列不参与核销提交。", "5. 验证：完成类型检查、变更文件ESLint及再次类型检查，并联调两类对账单的空渠道和通汇渠道数据。" ],
    "systemTags" : [ "assets-lease-admin" ],
    "evidenceRefs" : [ "附件/451-中恒-正常还款_提前结清对账单优化.docx", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineNormalRepaymentStatement.vue:228", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineNormalRepaymentStatement.vue:267", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineNormalRepaymentStatement.vue:410", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineEarlySettlementStatement.vue:224", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineEarlySettlementStatement.vue:263", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineEarlySettlementStatement.vue:406", "assets-lease-admin/src/views/usedCarLeaseback/statement/components/queryStatementDetail.vue:87", "assets-lease-admin/src/api/api_list.js:88" ],
    "confidence" : "MEDIUM",
    "moduleName" : "对账单管理前端",
    "relatedFiles" : [ "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineNormalRepaymentStatement.vue", "assets-lease-admin/src/views/usedCarLeaseback/statement/OfflineEarlySettlementStatement.vue", "assets-lease-admin/src/views/usedCarLeaseback/statement/components/queryStatementDetail.vue", "assets-lease-admin/src/api/statement/statement.js", "assets-lease-admin/src/api/api_list.js" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "cairui",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖后端列表、详情字段名和空渠道展示规则确定。",
    "risk" : "若后端金额字段或渠道枚举在联调阶段变化，两个页面及导出验收字段需同步调整。"
  } ],
  "totalEstimatedEffort" : "24h",
  "developmentEstimatedEffort" : "16h",
  "testingEstimatedEffort" : "8h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-20T12:50:06.602460",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 246

<!-- workhub-analysis:a7f7abfa5526d2f8439de22dd20acaed2228ec6607c5776a2a99a966173142ea -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：97
- 记录时间：2026-10-20T15:40:55.854909
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/保费分期/聚保融保费分期项目/2026/202610120004/历史版本/3f6e4c17-fd98-4cdd-a6c6-aec65918ce41/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "保费分期",
  "projectId" : 21,
  "projectName" : "聚保融保费分期项目",
  "repositoryUrl" : null,
  "requirementRawPath" : "raw/公司项目/requirements/保费分期/聚保融保费分期项目/2026/202610120004/历史版本/3f6e4c17-fd98-4cdd-a6c6-aec65918ce41/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "自营项目新增挂靠公司线上、线下签约进件模式，涉及项目配置、企业管理、订单创建、合同套系、附件校验、通知邮件及后台标记；同时优化运营端全局页面，页面跳转后保留原检索结果且不刷新。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "hanmeng" ],
  "workItems" : [ {
    "title" : "需求开发",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "16h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  } ],
  "totalEstimatedEffort" : "32h",
  "developmentEstimatedEffort" : "16h",
  "testingEstimatedEffort" : "16h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-20T15:40:55.228024",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 生成需求澄清项 · 需求 238

<!-- workhub-analysis:f0cf1faaee72cb3515dfd25abf6c865ffceaf98a2e2257b9ca71a2c71bc68fb2 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：29
- 记录时间：2026-10-21T09:10:56.396547
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000005/BL000005/2026/202608250007/历史版本/b79ce9e1-e5bc-43df-8e61-90a363913001/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "确认报表项目范围",
  "description" : "请确认本报表是否仅统计优利卡货车驻车电池项目 PRJ000088。",
  "evidence" : "需求材料仅写“优利卡项目”；代码 ol-saps 中将 PRJ000088 标识为优利卡货车驻车电池，未在需求中明确项目编码。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "确认月报统计时间边界",
  "description" : "请确认次月1日生成的是上一个自然月、按北京时间月初00:00:00至次月月初00:00:00前的数据。",
  "evidence" : "需求材料仅写“次月1日凌晨”“按业务发生日期筛选”，未明确时区、起止边界及边界值归属。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "QUESTION",
  "title" : "确认历史报表生成范围",
  "description" : "请确认上线后是否补生成历史月份报表；如需补生成，请给出最早月份。",
  "evidence" : "需求示例为2026-08，但未说明首份报表月份及是否回补历史数据，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "QUESTION",
  "title" : "确认首份报表期初余额",
  "description" : "请确认首份月报没有上月报表时，期初应付余额取0还是由财务提供初始化值。",
  "evidence" : "字段5依赖上月报表，但需求未定义首月初始化规则。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "QUESTION",
  "title" : "确认期初余额引用字段",
  "description" : "请确认字段5“期初应付余额”应取上月报表的字段16“期末应付余额”。",
  "evidence" : "需求写“上月报表的字段17”，但报表实际只列到字段16，不存在字段17。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "QUESTION",
  "title" : "确认期末应付余额公式",
  "description" : "请确认字段16是否需要计入字段5期初应付余额；若不计入，请确认字段5仅用于展示。",
  "evidence" : "需求定义字段5为期初余额，但字段16公式为8-10+15，未引用字段5，存在口径冲突。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 6,
  "itemType" : "QUESTION",
  "title" : "确认报表行纳入范围",
  "description" : "请确认每月哪些经销商或代理人需要生成一行，例如仅当月有发生额、截至月末有余额，还是全部历史收益人。",
  "evidence" : "需求只定义行字段，未定义主体纳入和零金额行规则，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 7,
  "itemType" : "QUESTION",
  "title" : "确认同一订单的主体归属",
  "description" : "请确认同一订单同时关联经销商和代理人时，现金实收、订单数及厂家款项应归属哪一方，是否允许双方同时计入。",
  "evidence" : "ol-marketing 的 PerfOrder 同时保存经销商和代理人；ol-saps 的 PaymentSlipEntity、CustomerBillEntity主要保存经销商信息。直接分别汇总可能重复计算同一笔资金。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 8,
  "itemType" : "QUESTION",
  "title" : "确认生效订单数口径",
  "description" : "请确认字段4统计当月生效订单数，还是截至月末仍生效的累计去重订单数。",
  "evidence" : "字段名称为“名下生效订单数”，但需求未说明统计期间、去重键及取消订单是否剔除。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 9,
  "itemType" : "QUESTION",
  "title" : "确认首笔款金额组成",
  "description" : "请确认字段6统计整张首笔款缴费单的实付金额，还是仅统计其中的代收付科目金额。",
  "evidence" : "需求说明为“所有已支付的首笔款”，但字段名称带“代收付”；代码与测试表明首笔款还可能包含一次性服务费，并非全部属于代收付。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 10,
  "itemType" : "QUESTION",
  "title" : "确认首笔款部分支付处理",
  "description" : "请确认缴费单部分支付时按每笔已到账金额当月计入，还是待缴费单全部支付成功后一次性计入。",
  "evidence" : "PaymentSlipEntity存在PART_PAID和SUCCESS状态，PaymentSlipPayDetailEntity保存逐笔payAmount、payTime，需求只写“已支付”，未覆盖部分支付。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 11,
  "itemType" : "QUESTION",
  "title" : "确认前三期代收科目范围",
  "description" : "请确认字段7应累计哪些账单科目，是否为“代收付服务费（经销商）”和“代收付服务费（厂家）”之和。",
  "evidence" : "ol-saps配置中存在20101“代收付服务费（经销商）”和20102“代收付服务费（厂家）”两个科目；需求仅写“代收部分”，未明确科目范围。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 12,
  "itemType" : "QUESTION",
  "title" : "确认账单部分还款计入日期",
  "description" : "请确认字段7在账单分次还款时按每笔到账时间计入，还是在整期账单结清月份计入全部实还金额。",
  "evidence" : "需求指定“客户实际结清时间”；代码同时存在CustomerBillEntity.settlementTime和CustomerBillPayDetailEntity.accountTime、settlementTime，部分还款会产生不同月份归属。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 13,
  "itemType" : "QUESTION",
  "title" : "确认可提现总金额口径",
  "description" : "请确认字段9是否直接按现有“收益管理”展示口径汇总incomeAmount，并包含EFFECTIVE与已确认的CONFIRMED收益。",
  "evidence" : "现有PerformanceIncomeManagementServiceImpl基于收益记录汇总，且对全款经销商记录存在合并逻辑；需求仅写“已生效订单的收益总和”，未明确是否复用该展示口径及状态集合。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 14,
  "itemType" : "QUESTION",
  "title" : "确认累计已提现金额字段",
  "description" : "请确认字段10累计提现申请金额applyAmount，还是扣除手续费后的实际到账金额actualAmount。",
  "evidence" : "AcctWithdrawApply同时保存applyAmount、feeAmount、actualAmount；需求只写“成功提现总额”。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 15,
  "itemType" : "QUESTION",
  "title" : "确认提现成功日期字段",
  "description" : "请确认字段10按提现单payTime归属月份，而不是现有提现列表使用的applyTime。",
  "evidence" : "需求明确按“提现成功时间”；AcctWithdrawApply存在payTime，但现有AcctWithdrawApplyMapper提现列表按applyTime筛选并展示。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 16,
  "itemType" : "QUESTION",
  "title" : "确认字段11引用金额",
  "description" : "请确认字段11中的“实收客户首笔款”是否就是字段6；若不是，请给出对应金额口径。",
  "evidence" : "字段11公式写“可提现总金额-实收客户首笔款”，报表字段中没有同名字段，最接近的是字段6。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 17,
  "itemType" : "QUESTION",
  "title" : "确认厂家实际到账数据源",
  "description" : "请指定字段12“厂家实际打款到账金额”对应的系统、单据、成功状态、金额字段和到账时间字段。",
  "evidence" : "需求材料未给出字段12的数据来源；在当前相关代码中也未定位到可唯一对应“厂家向公司打款到账”的业务记录，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 18,
  "itemType" : "QUESTION",
  "title" : "确认已还厂家金额数据源",
  "description" : "请指定字段14“实际退还给厂家金额”对应的系统、单据、成功状态、金额字段和付款时间字段。",
  "evidence" : "需求材料未给出字段14的数据来源；现有代付、提现及账单记录不能直接证明哪一类交易属于“退还厂家”，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 19,
  "itemType" : "QUESTION",
  "title" : "确认退款与冲正处理",
  "description" : "请确认月报生成前发生的退款、红冲或撤销是否冲减原金额，以及月报固定后发生的冲正应调整原月还是记入后续月份。",
  "evidence" : "PaymentSlipEntity包含REFUND、REFUNDING状态，缴费单及客户账单支付明细均有isReverse字段；需求未定义异常交易处理。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 20,
  "itemType" : "QUESTION",
  "title" : "确认负数展示规则",
  "description" : "请确认各余额和厂家代付金额允许展示负数，还是需要按0截断或转列展示。",
  "evidence" : "字段公式可能产生负值；ol-order现有货车驻车电池测试明确允许“厂家代收付服务费”为负数。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 21,
  "itemType" : "QUESTION",
  "title" : "确认报表导出能力",
  "description" : "请确认代收代付报表是否必须支持按当前筛选条件导出Excel。",
  "evidence" : "需求只描述报表字段，未说明导出；现有ol-admin财务报表及收益、提现页面普遍提供导出功能。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "固定月报与后续源数据不一致风险",
  "description" : "请确认接受月报固定后，后续补录、退款、红冲或状态修正可能导致历史月报与实时业务数据不一致；如不接受，需先明确重算或调整机制。",
  "evidence" : "需求要求“固定生成”；现有缴费单和账单支持退款、冲正及人工实际付款时间，源数据可能在月末后变化。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "跨系统月末截点不一致风险",
  "description" : "请确认接受各系统数据在月末存在同步延迟时可能造成现金实收、收益和提现未处于同一业务截点的风险。",
  "evidence" : "缴费单和客户账单位于ol-saps，收益及提现位于ol-marketing，报表需跨系统关联；需求未定义延迟数据等待窗口或月末对账机制。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "历史数据关联不完整风险",
  "description" : "若要求补生成历史报表，请确认接受历史订单、收益人或主体映射缺失时部分记录可能无法归属经销商/代理人的风险。",
  "evidence" : "现有收益管理接口文档明确部分历史字段允许为空；SAPS资金记录与Marketing收益主体需跨系统按订单或客户编码关联。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "RISK",
  "title" : "财务数据越权风险",
  "description" : "请确认接受在未明确角色权限前不能向普通运营或渠道用户开放该报表。",
  "evidence" : "报表包含主体级实收、提现及垫付余额等敏感财务数据；需求未定义可见角色，现有收益、提现页面使用独立权限标识控制查询和导出。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```


### 保费分期 scf-saps 系统预警根因核查 · 2026-10-21

- 范围：WorkHub 配置 BL000001，GitLab 组 scf，生产环境；代码工作区 `/Users/aslight/IDEAWorkspace/scf/scf-saps`，对照 `origin/master` 提交 `f4985f3af89e51935b01bac9d8d336e96a2b8b85`。仅只读诊断，未调整生产数据或告警规则。
- 预警来源：当前本地 WorkHub 页面 `/ops/system-alerts`，筛选保费分期／生产／scf-saps／ERROR，当前保留 19 条系统异常：14 条代扣、2 条清分、3 条通用异常。受控远端工作台数据库的记录滞后至 10 月 15 日，未将该数据库作为当前页面的完整清单；实况已通过页面和实时 ELK 交叉核实。列表数量不代表全部历史异常次数。
- 代扣：14 条列表告警对应 7 次请求，每次被内外两层 catch 重复记 ERROR。生产 `scf_self_saps.customer_order` 的 `id=94795`、`order_no=A260903162734000001` 是 9 月 3 日创建的人工核销单（transaction_mode=40），仍为未支付 status=1、未取消；对应交易未结算。同一原账单 `FA260405145001000101` 已由另一核销单 `A260903172219000001` 成功结算 6916.98。`CustomerOrderServiceImpl.checkBillPaying` 按申请单 `APA260405102530000001` 查 status=1/2，导致后续账单 `FA260405145001000106` 也被拦；发生在请求 Payment 之前。10 月 15 日至 21 日 09:00 的代扣任务表有 19 次失败，实扣均为 0，原因均为“订单正在处理中,请取消线下申请或查单”。
- 代扣修复判断：应优先核实并处置遗留核销单；SAPS 建议补人工核销失败的回滚或失败状态收尾，保留真实在途订单的防重保护。当前代码先保存未支付订单，再由独立 `@Transactional paymentHandle` 执行结算，`processWriteOff` 异常仅重新抛出而未关闭已创建订单，存在遗留未支付记录的路径。这与现存数据一致，但未取得 9 月 3 日第一次失败原始日志，不能断言当次具体触发异常。告警可避免业务拦截重复记两次 ERROR。未执行作废、补偿或代码修复。
- 清分：10 月 19 日 15:55:36、15:55:50 对同一退保单 `IW026100811459020` 重复手工清分，`InsuranceAction.handleCustomerBill` 报保司批复金额不足。日志显示分配至后续客户账单时仅余 640.02，下一笔需 1421.32；这不是查询无流水空指针。保司批复总额 12266.34，不能把 640.02 误解为批复总额。10 月 20 日 16:08:33 已再次处理成功，退保表 settlement_status=1；入金 `PTA26100913450001` status=1、剩余清分金额=0。成功时运营应收 9949.36、结余 2316.98。当前无待处理阻塞，不建议因旧告警去掉金额校验。两日之间何种业务调整消除了差额尚未核对，不能据此认定一个具体算法缺陷。
- 通用异常：页面 3 条 `[20440]系统异常`（10 月 19 日 15:54:02、16:30:26，20 日 15:55:36）均为 `ClientAbortException: IOException: Broken pipe`，在 FastJson 写 HTTP 响应时对端连接已断开。`AmpGlobalExceptionHandler` 统一归入系统异常并尝试再次写响应，形成额外 handler 警告。可低优先级细分日志处理；现有证据不能区分用户取消、代理超时或网络断开，不应据此判定账务执行失败。
- 另：实时 ELK 还有 JWT 无效日志，但未出现在本次页面 19 条清单中，未混入上述统计。
- 当前修复边界：用户要求 Payment 不改，本次 Payment 源码修改与新增测试已精确撤回，工作区干净。SAPS 空流水修复在 10:20:53 的版本 6 项测试通过，但 10:21:54 源码又被外部改回空响应／data 抛异常；已向用户核实意图，未覆盖该外部改动，也不将之前测试作为当前源码验证结论。
- 后续确认：用户已回复“保留当前抛异常的修改”。保留 SAPS 生产实现并同步 7 项回归，2026-10-21T10:28:31+08:00 全部通过，源码哈希一致。Payment 无改动。现网零流水 data=null 仍会以业务异常失败，未声称已消除调度失败。


### 保费分期 scf-payment 错误归类核查 · 2026-10-21

- 业务线/环境：WorkHub 核实保费分期 BL000001、GitLab 组 scf、生产环境。代码口径为 `/Users/aslight/IDEAWorkspace/scf/scf-payment` 的 origin/master `35468f0a24ea4d4b8fb856958dd75470d9b01658`；当前 fix 分支与该基线一致、工作区干净。该 SHA 不代表已经核验部署制品版本。
- 范围：2026-10-21 10:53 左右，工作台系统预警页筛选保费分期／生产／scf-payment／ERROR／系统异常，共 22 条保留记录，发生在 10 月 15、16、20 日。已核对两页清单，并按事件时间通过实时 ELK 查询全部相关异常堆栈。数量仅代表当时页面保留记录，不能当作全部历史交易失败数。用户只要求原因归类，未改代码、配置、数据库或过滤规则。

| 根因分类 | 当前页面日志数 | 触发与证据 | 判断与建议 |
| --- | --- | --- | --- |
| 交行回单压缩包缺失 | 17 | 页面均为“全局未知异常：{}”；10 月 15 日 2 条、16 日 14 条、20 日 1 条，堆栈全部为 `SftpException: No such file`，来自 `PdfDownloadService.downloadAndExtract:155` 的 `channelSftp.get`，调用入口 `/api/bcm/downloadBankSlipFile`。连接已建立，读取目标文件时失败。 | 优先核对银行回单文件同步、账号目录与查询日期，影响回单下载。当前不能归因为扣款/代付失败。代码可将缺文件细分为明确业务结果并补 SFTP 失败路径的资源释放。 |
| 重复绑卡拦截 | 2 | 10 月 15 日 15:55:54、15:55:58，堆栈均为 `BizException: 您的银行卡已签约`，`OldCardBindingThctServiceImpl.checkExistsCard:711`。代码校验商户、银行卡、渠道、VALID 状态，并在同申请单匹配时拒绝。 | 属于重复操作业务校验，保持防重复保护；可优化前端复用已有签约状态和业务异常日志分类。 |
| 宝付扣款账户余额查询地址配置不合法 | 2（同一次请求） | 10 月 15 日 17:01:09，底层“渠道接口调用失败”及外层 `100001:接口调用异常`；入口 `AccountController.queryDeductionAccount`，queryType=2。生产 `scf_self_payment.channel_pay_config.id=44`（`BAOFUPAY-HZF000144-PAY`）的 `resv_one` 为字符串 `1`。`BaofooStrategy.queryAcctInfo:803,833` 读取该字段并拼接 `T-1001-006-03/transReq.do`。Forest 日志确认实际请求 `http://localhost/1T-1001-006-03/transReq.do`，连接本机 80 端口失败。 | 已确认配置值与代码期望的基础 URL 不符，需修正该项余额查询配置并增加 URL 校验。影响此商户扣款账户余额查询，当前证据不表明发生实际扣款失败；两条日志不要计作两笔交易。配置当前仍为 1，未修改。 |
| 重复上传代扣协议拦截 | 1 | 10 月 15 日 19:28:11，`600004:代扣协议已上传`，堆栈 `AgreementPayThctServiceImpl.handleWithholdElectronicsSign:164`。代码检测同申请单已有 agreement 记录后拒绝重复上传。 | 属于业务重复操作保护；不应只因 ERROR 级别判定渠道故障。可优化重复上传时的状态展示和错误分类。此检查证明已有记录，不单独证明其远端文件状态。 |

- 回单根因边界：实现按配置账号与请求日期拼接远程 ZIP 路径；现有日志不能还原这些请求的具体日期参数，也没有在银行 SFTP 侧核对远程目录。`BCMApiController.downloadBankSlipFile` 没有 `@WebLog` 注解，而 `LogAspect` 只拦截该注解；在受控数据库按时间和入口检索 api_logger 也未找到对应记录。因此不能把“远程目标不存在”进一步包装成“银行未生成”或“日期一定传错”的已确认结论。未直连 SFTP，也未发真实回单接口请求。
- 回单资源释放：当前 get 异常会跳过后续 `channelSftp.disconnect()` 与 `session.disconnect()`；这些释放不在 finally。属于可修的异常路径资源收尾问题，本轮仅分析。
- 另核对实时原始 ERROR：10 月 21 日 10:37:32 至 10:52:32 共 52 条，39 条 Druid 空闲连接丢弃（`discard long time none received connection`）、9 条 JWT 无效、4 条慢 SQL（约 1.03～1.10 秒）。这些不属于上述预警页 22 条当前保留系统异常，未混入该统计。空闲连接丢弃仅证明连接池作出淘汰决定，未据此断言数据库不可用；JWT 无效尚未核对调用方身份；慢 SQL 可另按执行计划与索引排查。
- 优先顺序：先核对回单缺文件链路和宝付余额查询基础地址；重复绑卡与协议上传保留业务保护，优化交互/日志分类即可。本轮仅只读诊断，Payment 工作区无修改。


### 保费分期 scf-order 错误归类核查 · 2026-10-21

- 核查范围：WorkHub 确认保费分期 BL000001、GitLab 组 scf、生产环境。代码工作区 `/Users/aslight/IDEAWorkspace/scf/scf-order`；已安全 fetch origin master，对照 `origin/master=bdf1eec97427696ad54302da9946819109d23cb0`。工作区当前分支 `fix/fix-project-margin-zero` 干净，未切换、重置或修改源码。远程提交不等同于已核验生产部署制品。辅助查看已配置的 scf-intf-zhonghua 实时日志及代码，其本地 origin/master 为 `21ce0ff9787e823d8a8ab964c9a6388daa06d93f`，该辅助代码未据此认定当前部署版本。
- 当前清单：2026-10-21 13:29 左右在工作台系统预警页选择保费分期／生产／scf-order／ERROR／系统异常，无额外时间或消息过滤，共 105 条保留记录，最早 10 月 15 日 11:46:20。完整读取列表后分为 89 条状态同步、4 条融资补件、2 条附件校验、10 条异步参数校验。该清单数量不代表全部历史请求次数或实际资金失败笔数。

| 分类 | 当前保留数 | 已核实原因 | 影响与处理建议 |
| --- | --- | --- | --- |
| 放款状态同步失败 | 89 | `ApplicationServiceImpl.syncStatus` 检查申请单号 Redis key 后报“订单正在处理中”。`LoanOrderServiceImpl.loanApply` 先由 `checkIsLock` 对同一申请单号 setIfAbsent 加锁，在该锁尚未释放时，`dealLoanApply → notifyCrmResult → applicationService.syncStatus` 同步调用又检查同一个 key，因此被自身外层锁拦截。 | 属于订单服务的锁复用/调用时序问题，应修正锁作用域或内部同步时机并保留并发保护；不等同于 SAPS 遗留核销单阻塞。不要直接通过删线上锁处理。 |
| 融资补件接口验签失败 | 4 | 10 月 21 日的四笔融资申请调用中化 `/xxt/supplementaryMaterials`，下游 HTTP 200 但业务码 `000602`、message“签名验证失败”、success=false；order 随后记 supplement finance apply failed，materials=2。 | 四笔共 8 份本方已签章合同未完成本次补推，需优先核对资方接口报文签名的规则、字段/序列化和配置，再按幂等机制补推；当前不能断言一定是密钥配置错误，也不能把接口报文验签失败混同于本方合同签章失败。 |
| 附件保存失败 | 2 | 10 月 19 日 18:25:17、18:25:23，同一异常为“委托代理服务合同中的车架号与车辆信息车架号不符”，对应 VIN 尾号 819491。`ApplicationContractService.saveLoanContract` 对合同类型 220 调用 PDFUtil.contains，未检索到该 VIN 后抛异常。 | 优先核对合同与车辆对应关系及 PDF 可提取文本；当前尚未查看实际合同，不能断言必然上传错文件。PDFUtil 使用 PDFBox 文本提取、无 OCR，扫描件/字体或解析失败也可能导致不匹配，IO 异常会记录后返回 false。 |
| CompletableFuture Exception | 10 | 10 月 15 日 15:45:53～15:58:55。外层为“申请异常!参数拦截”，向下追到验证失败 INFO 堆栈，全部为 `PROJECT_PROPERTIES_ERROR:保证金不足`。 | 现场可用保证金 832.23 时，失败申请需占用 1112.58；之后可用仅 24.95，失败申请需占用 683.26 或 1112.58。属于真实额度不足拦截，核对保证金余额与占用/释放，不应简单去掉校验。可改善外层保留具体错误信息及日志分类。 |

#### 状态同步的证据与当前状态

- 代码链：LoanOrderServiceImpl:190～211 → LoanOrderServiceTransational.checkIsLock:449（setIfAbsent，key 为申请单号，60 分钟）→ dealLoanApply:267 → notifyCrmResult:889 → ApplicationServiceImpl:739～740。同步方法捕获该异常，记录失败操作日志，未向上终止放款主流程。
- 最近代表单 `JBR026102111264417`：12:49:50 操作日志失败、remark“订单正在处理中”；12:49:52 和 12:55:01 已有状态同步成功记录，当前申请表和放款表均为 351（放款成功）。另外核对当日三笔同类单也均为 351。
- 受控 SQL 对 10 月 15 日至 21 日 13:34，application_log.action=12000、result=0、remark=“订单正在处理中”的失败记录汇总：241 次失败记录，涉及 143 笔未删除申请，当前申请表 status/loan_status 均为 351。此统计范围比页面保留的 89 条日志大，不能互相替换计数；仅证明这些申请的系统当前状态，未做实际银行到账全量对账。
- ELK 按日查询发现的此类堆栈均为订单正在处理中，但部分日查询受服务端结果字节限制而 truncated，未将其条数当作全量。分类数来自完整页面列表，根因由实时堆栈、源码持锁调用链及数据库操作日志交叉核实。

#### 融资补件的现状

- 四笔：`FA026101511279335`、`FA026101511279337`、`FA026101915422649`、`FA026101601438698`。对应失败时间为 10:44:38、10:38:24、10:40:37、12:18:45，实时日志逐笔确认资方返回 000602。
- 生产 finance_application 四笔当前 status=325、loan_status=330；finance_contract 每笔 800/242 两份合同，sign_status=1（本方签章成功），funder_sign_status=0（待资方签章）。四笔共 8 份，不表示 8 次独立资金交易。
- FinanceContractService 仅在补件返回 success 后将资方签章状态置为 SIGNING；失败只记录 ERROR，状态仍为待处理。现有 `pushFunderContractJob` 可查询这些待推合同，但本轮未核对其生产调度启用状态，也未触发补推。
- 已确认问题发生在资方补件报文验签环节；具体签名算法/密钥/字段差异尚需继续对接核验，未使用密钥进行试签或调用资方接口。

- 最新原始日志补充：13:13:39～13:28:39 scf-order ERROR 共 18 条，均为 JWT 无效；未混入上述页面 105 条保留系统异常。
- 处理顺序：先排查资方补件验签并核对待推合同；修正订单状态同步自锁及补偿边界；保证金不足按余额与占用处理，合同附件按实际文件核验。此次只读分析，未修改代码、生产配置、业务数据或预警规则。



### WorkHub 临时分析 · 调整研发任务 · 需求 238

<!-- workhub-analysis:e6121bd2e7f9474e53472a28443e488b807a084711ec65e30191fbca0e47fbf1 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：99
- 记录时间：2026-10-22T15:11:21.333821
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000005/BL000005/2026/202608250007/历史版本/89dca1d3-fb65-4f83-b15a-2813927acce2/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000005",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "raw/公司项目/requirements/BL000005/BL000005/2026/202608250007/历史版本/89dca1d3-fb65-4f83-b15a-2813927acce2/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "新增代收代付报表，关联“现金实收”“额度释放”“实际提现”数据，直观展示公司垫付与代收资金情况；所有金额按对应业务发生日期筛选，系统于次月1日凌晨自动汇总并固定生成带月份标签的月度报表。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "xujieni" ],
  "workItems" : [ {
    "title" : "报表开发",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "ol-saps", "ol-admin" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "16h",
    "ownerUserName" : "xujieni",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  } ],
  "totalEstimatedEffort" : "24h",
  "developmentEstimatedEffort" : "16h",
  "testingEstimatedEffort" : "8h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-22T15:11:20.90804",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 230

<!-- workhub-analysis:57fa1699224d12c9abb8e5c1f41517055f33897f063836fc893ca427e1356231 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：100
- 记录时间：2026-10-22T15:12:54.749908
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/202609110002/历史版本/8348d809-2105-440e-89a6-f43039bcd0e3/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000008",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "raw/公司项目/requirements/BL000008/BL000008/2026/202609110002/历史版本/8348d809-2105-440e-89a6-f43039bcd0e3/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "优化叉车租售及租赁业务的账单、开票记录与金额展示：叉车租售开票金额按客户实际还款额展示；叉车租赁客户账单新增保证金抵扣及抵扣后应还、已付、未付金额字段；叉车租赁开票记录新增两家承租人的记录及实际还款额、保证金抵扣金额、抵扣后实际还款额字段。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "yinjun" ],
  "workItems" : [ {
    "title" : "开票记录改造",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps", "lease-crm" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "yinjun",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "客户账单改造",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-admin", "lease-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "yinjun",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  } ],
  "totalEstimatedEffort" : "24h",
  "developmentEstimatedEffort" : "16h",
  "testingEstimatedEffort" : "8h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-22T15:12:54.477701",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 生成需求澄清项 · 需求 255

<!-- workhub-analysis:f3692ee05d457b49464db4c8c84cbb873e973490850f73ac3a1a1ce5568f2f5b -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：32
- 记录时间：2026-10-26T11:39:40.436062
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610230006/历史版本/c9dec90f-c664-4e2a-8282-561dcb41c0c7/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "确认商户切换覆盖的交易方式",
  "description" : "请确认本次仅切换聚合扫码（交易子方式16），还是同时切换中金快捷支付（10）和协议支付（11）。",
  "evidence" : "需求背景描述“生成二维码”，但支付通道调整写的是全部“扣款”。代码 ChannelTransModelEnum 将中金快捷、协议、聚合扫码定义为不同交易方式；ChannelPayConfigServiceImpl#getConfig 当前仅按应用、环境、配置类型和渠道取商户配置，不区分交易方式。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "确认切换后的新增绑卡商户",
  "description" : "请确认切换后新增绑卡、换卡及重新签约仍使用“中恒中金商户”，还是改用嘉泰保理商户010583。",
  "evidence" : "需求仅明确“存量用户绑卡保持不变”，未说明切换后新发生的绑卡和重新签约规则。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "QUESTION",
  "title" : "确认存量协议号可跨商户扣款",
  "description" : "请确认中金支付已验证：原中恒商户生成的存量绑卡协议号，可以由嘉泰保理商户010583直接发起扣款。",
  "evidence" : "代码 CardBindingEntity 只保存应用、渠道和 agreeId，不保存协议所属商户；XxtpayPayStrategyImpl 会把原 agreeId 与当前支付配置中的 merchantNo、appId组合上送。需求材料没有提供中金侧的跨商户协议复用证明。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "QUESTION",
  "title" : "确认010583对应的下游应用标识",
  "description" : "请确认嘉泰中金商户号010583对应的 xxt-pay/中金应用ID（终端号）具体取值。",
  "evidence" : "需求只给出商户号010583和私钥；XxtpayPayStrategyImpl 发起支付时同时必传 merchantNo 与 appId，后者来自 channel_pay_config.terminal_id，现有材料未给出。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "QUESTION",
  "title" : "确认各环境商户配置已就绪",
  "description" : "请确认测试环境和生产环境是否均已准备010583对应的应用标识、密钥及有效支付配置。",
  "evidence" : "结构化字段仅称私钥已通过邮件发送，未说明适用环境及配置完成状态；相关中金请求实际由 assets-payment 转发至 xxt-pay，仓库无法核验邮件和下游运行配置。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "QUESTION",
  "title" : "确认100%清分的金额口径",
  "description" : "请确认“100%清分至嘉泰保理”是按扣款原始金额全额清分，还是按扣除渠道手续费后的净额清分。",
  "evidence" : "需求未说明手续费承担方式；代码分账请求同时存在 amount、arriveAmount、fee字段，二者会形成不同到账金额。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 6,
  "itemType" : "QUESTION",
  "title" : "确认嘉泰清分收款标识",
  "description" : "请确认中金渠道向嘉泰保理清分时使用的收款商户/结算标识是否为历史资料中的0002。",
  "evidence" : "历史需求知识库记录嘉泰中金商户010583、结算标识0002；当前需求只写010583。代码将扣款主商户merchantNo与分账接收方channelMerchantNo分别上送，不能视为同一字段。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 7,
  "itemType" : "QUESTION",
  "title" : "确认清分时点",
  "description" : "请确认切换后仍采用实时清分，还是采用对账单清分。",
  "evidence" : "当前需求只写“扣款完成后100%清分”；历史需求202608250006写明“实时资金清分”。代码 ShareAccountInfoParser 按渠道读取clearingMode，支持实时清分与对账单清分两种模式。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 8,
  "itemType" : "QUESTION",
  "title" : "确认正式切换时间",
  "description" : "请确认生产环境按哪个具体日期和时间点开始由010583发起新扣款。",
  "evidence" : "需求材料没有上线日期或切换时点；商户配置切换会立即影响后续中金支付请求。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 9,
  "itemType" : "QUESTION",
  "title" : "确认切换时处理中订单的归属规则",
  "description" : "请确认切换时尚未终态的旧商户订单，应继续在旧商户查询直至终态，还是允许切换后统一走新商户查询。",
  "evidence" : "需求未定义在途订单规则；XxtpayOrderQueryStrategyImpl 查单时读取当前有效支付配置，OrderChannelEntity没有保存下单时的merchantNo或下游appId。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "存量绑卡可能无法直接复用",
  "description" : "若中金协议号与签约商户绑定，旧协议号配合010583扣款会批量失败；在取得支付机构验证前，需要人工确认是否接受该风险。",
  "evidence" : "需求仅声明绑卡不变；代码会把历史agreeId与新商户配置组合上送，仓库及知识库均没有跨商户复用验证结果。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "在途旧商户订单可能无法正确查单",
  "description" : "直接替换当前支付配置后，旧商户处理中订单可能被送到新商户上下文查询，出现长期处理中或被判失败的风险。",
  "evidence" : "XxtpayOrderQueryStrategyImpl根据订单的应用和渠道重新查询当前channel_pay_config；OrderChannelEntity未留存下单商户号。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "商户切换可能扩大到全部中金交易方式",
  "description" : "若直接替换中金渠道支付配置，快捷、协议及聚合扫码可能同时使用新商户，影响范围可能超过需求中的二维码场景。",
  "evidence" : "ChannelPayConfigServiceImpl#getConfig不以transactionSubMode作为查询条件，而中金三种交易方式共用ZJPAY渠道配置。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "RISK",
  "title" : "100%清分依赖运行环境配置",
  "description" : "代码能力并不能证明目标环境已实际生效；若中金渠道的分账账户或清分模式缺失、错误或被环境级配置覆盖，可能导致扣款失败或资金未按100%进入嘉泰。",
  "evidence" : "SAPS通过PaymentConfig.shareAccountInfo和clearingMode决定分账账户及模式；仓库只有配置脚本和默认规则，无法核验生产数据库、缓存及xxt-pay实际配置。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "RISK",
  "title" : "密钥与下游配置无法从材料核验",
  "description" : "私钥仅通过邮件传递，仓库中也不包含目标商户完整配置，因此存在密钥版本、商户号和应用ID不匹配导致上线失败的风险。",
  "evidence" : "结构化字段仅说明“私钥信息已通过邮件发送”；需求附件摘要未列出应用ID、公钥、密钥版本或环境对应关系。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "RISK",
  "title" : "审批中可能发生范围变更",
  "description" : "需求尚未审批完成，当前确认结论可能因审批意见或支付方反馈发生变化，需要人工确认是否接受提前按现有版本推进澄清的风险。",
  "evidence" : "结构化识别字段显示审批状态为“审批中”。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### WorkHub 临时分析 · 调整研发任务 · 需求 255

<!-- workhub-analysis:d2da8c6b55248aea89c49eb78905dc1f3fea5a600228ff3339cc7151629c832e -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：101
- 记录时间：2026-10-27T15:49:09.237022
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610230006/历史版本/8a970181-17e9-4603-a6c6-2db39052b58e/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : null,
  "requirementRawPath" : "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610230006/历史版本/8a970181-17e9-4603-a6c6-2db39052b58e/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "将中恒项目扣款支付通道由“中恒中金商户”切换为“嘉泰保理中金商户”，使用嘉泰中金商户号010583发起扣款；存量用户绑卡保持不变，扣款完成后仍按100%比例清分至嘉泰保理账户。涉及创新保理资产管理系统，合作方为中恒。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "cairui", "xujieni" ],
  "workItems" : [ {
    "title" : "支付商户号从中恒切换为嘉泰",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "xxt-pay" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "cairui",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "聚合扫码支付测试联调",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "intf-paychannel" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "xujieni",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  } ],
  "totalEstimatedEffort" : "24h",
  "developmentEstimatedEffort" : "16h",
  "testingEstimatedEffort" : "8h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-27T15:49:08.923353",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 生成研发评估 · 需求 253

<!-- workhub-analysis:78a641ac5d99ed5827698ca815290f9741d6f7497e98bb7f281134f809e91ac9 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：102
- 记录时间：2026-10-27T16:04:16.275094
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610220003/历史版本/0cd418eb-61b2-4865-9d74-63be8112b416/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : "ca-assets (24 repositories)",
  "requirementRawPath" : "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610220003/历史版本/0cd418eb-61b2-4865-9d74-63be8112b416/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求涉及 assets-lease、assets-saps、assets-lease-admin、assets-partner-gateway、assets-lease-crm，建议拆为5个可排期任务，预估合计100h。现有系统已具备资产及影像查询、合作方支付API、还款台账罚息科目、客户/资方账单罚息字段、月初管理服务费对账单及核销框架，可复用后改造成国胜交接链路。当前中恒罚息算法仍按原规则执行，与“宽限5个自然日、千分之五/日并从第1日追溯、实收罚息按资方2/3和合作方1/3分配”不一致。用户扣款方案明确待确认，本评估未计入线上扣款改造；确认后可能新增 assets-payment 改造和工时。国胜交接远端分支目前与主干同一基线，尚无本需求实现提交。本次仅完成只读评估，未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "按国胜导入模板交付中恒存量资产清单、还款计划表和实际还款记录，并提供资产影像资料URL；国胜侧人工导入。", "为国胜配置外部访问、验签、IP白名单及通知路由，接收国胜推送的线下还款信息，并将回购等对账单通知切换至国胜。", "用户扣款由国胜下发指令，候选方案分别使用“国胜中金商户号”或“通汇嘉泰中金商户号”发起扣款并100%清分至通汇商户；具体方案待确认。", "中恒客户账单还款宽限期调整为应还日后5个自然日；第6日起按逾期金额千分之五每日计收，并追溯计算自逾期第1日起的实际逾期天数。", "国胜推送客户实际罚息金额，系统按实收罚息计算实还资方罚息2/3、实还管理方或合作方罚息1/3，并贯通支付结算及科目明细。", "还款台账增加或规范“实还资方罚息”还款类型，列表、筛选及导出口径保持一致。", "客户账单展示应还罚息和国胜推送的实还罚息，支持列表及导出口径一致。", "资方账单展示实还资方罚息和实还管理方罚息，并保持列表、详情及导出一致。", "每月1日自动汇总上月线上、线下已实际结算账单，生成合作方服务费对账单，包含合作方服务费、合作方罚息及需求所述逾期对账单扣减项。", "合作方服务费对账单提供列表、明细、导出和核销能力，记录核销日期、操作人、备注及银行凭证；合作方名称本期保持不变。" ],
  "impactedModules" : [ "assets-lease", "assets-saps", "assets-lease-admin", "assets-partner-gateway", "assets-lease-crm" ],
  "risks" : [ "资金口径风险：现有中恒 T1 罚息逻辑采用原费率和资方年化费率算法，直接修改可能影响存量已逾期账单、减免、回购、提前结清及历史对账单，必须明确规则生效日和历史数据处理方式。", "金额分摊风险：2/3与1/3在分级计算时可能产生分币差，且国胜推送实还罚息与系统应还罚息可能不一致，当前材料没有给出舍入及差额处理规则。", "接口风险：材料未提供国胜接口文档、签名规则、错误码、重试规范及接入参数；国胜专属代码证据不存在，因此接口任务置信度为LOW。", "路由风险：现有对账单通知按合作方编码查询外部接入配置；若在合作方名称暂不修改的同时复用原中恒编码，可能覆盖历史中恒通知路由。", "数据交接风险：未在可访问材料中定位到“导入模板_v2.xlsx”，也未明确交接截止时点、存量资产范围及影像URL的外网访问期限，无法最终确定字段映射和导出条数。", "对账口径风险：“逾期对账单金额”的来源、状态范围和扣减期间未定义；现有 MANAGE_SERVICE_FEE 能力虽可复用，但可能与目标“合作方服务费对账单”口径不同。", "迭代依赖风险：202610130003的已确认评估涉及还款渠道及对账资源改造，但当前远端研发分支相对主干仅有接口注释变化；若该迭代尚未实现，本需求需要合并排期并处理同一支付、对账链路的代码冲突。", "扣款范围风险：用户扣款方案尚未确认，本次100h预估不含线上扣款通道和商户号切换；确认后可能增加 assets-payment、支付配置及联调任务。" ],
  "questions" : [ "用户扣款最终选择“国胜中金商户号”还是“通汇嘉泰中金商户号”？国胜需要传入哪些扣款要素、由哪个系统调用支付通道、切换生效日是什么？", "国胜是否使用新的合作方编码和外部接入账号？若页面继续展示原合作方名称，应如何区分新国胜路由与历史中恒路由，避免历史通知被覆盖？", "请补充国胜还款、回购对账单通知的接口文档，以及商户ID、公钥、IP白名单、通知地址、错误码、超时重试和幂等约定。", "请提供“导入模板_v2.xlsx”，并确认交接截止时间、资产范围是全部历史资产还是仅在贷资产，以及影像URL所需访问权限和有效期。", "实收罚息按2/3与1/3分配时采用何种分币舍入规则？国胜推送金额大于或小于系统应还罚息时，是拒绝、按推送金额入账，还是生成差额待处理？", "合作方服务费具体对应现有哪个科目，是否复用10206管理服务费和10302管理方罚息？公式中的“逾期对账单金额”具体指哪些对账单、哪些状态及哪个月份的金额？", "本需求上线时，202610130003“正常还款/提前结清对账单优化”是否已作为前置版本发布？若未发布，是否允许合并为同一迭代交付？" ],
  "developerPool" : [ "admin", "cairui", "hanmeng", "lvliuhao", "shihao", "xuewentao", "xujieni", "yinjun", "yufanghua", "zhiyuntao", "尹军", "曹彬", "江敏", "王文龙", "石浩" ],
  "workItems" : [ {
    "title" : "资产租赁与账单系统导出中恒存量资产交接数据",
    "description" : "按照国胜导入模板一次性交付资产清单、还款计划、实际还款记录和影像URL，不建设国胜导入功能；交付前完成跨库关联、数量及金额核验。",
    "requirementChangePoint" : "中恒存量资产资料交接",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "中恒项目资产主数据", "客户及资方还款计划", "实际还款流水及科目明细", "资产影像URL清单", "国胜导入模板及交付核验清单" ],
    "changePoints" : [ "1. 配置/SQL：确认导入模板字段、交接截止时点和中恒项目过滤条件，编制 assets-lease 与 assets-saps 只读导出SQL。", "2. 数据服务：从资产、客户、融资申请及附件数据组装资产清单和影像URL，保证资产编号、合同号等关联键唯一。", "3. 账单数据：从客户账单、资方还款计划、支付明细和支付科目明细生成还款计划表及实际还款记录。", "4. 导出交付：按模板生成分文件或工作簿，处理金额单位、日期格式、枚举翻译和敏感字段保护。", "5. 幂等/验证：校验资产数、期数、实还总额、未还余额、URL可访问性及重复记录，形成交付批次和可追溯校验结果。" ],
    "systemTags" : [ "assets-lease", "assets-saps" ],
    "evidenceRefs" : [ "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610220003/历史版本/0cd418eb-61b2-4865-9d74-63be8112b416/source.md", "中恒-保理资产交接国胜需求.docx：2.1资产数据提供、2.1.1资产数据导出与导入", "assets-lease/assets-lease-controller/src/main/java/com/assets/lease/finance/controller/asset/AssetRecordController.java", "assets-lease/assets-lease-service/src/main/java/com/assets/lease/finance/service/asset/impl/AssetRecordPageService.java", "assets-lease/assets-lease-dao/src/main/resources/com/assets/lease/finance/mapper/asset/AssetAttachmentMapper.xml", "assets-saps/assets-saps-dao/src/main/resources/mapper/RepaymentPlanPaySubjectDetailMapper.xml" ],
    "confidence" : "MEDIUM",
    "moduleName" : "存量资产数据交接",
    "relatedFiles" : [ "assets-lease/assets-lease-model/src/main/java/com/assets/lease/finance/model/asset/entity/AssetAttachment.java", "assets-lease/assets-lease-dao/src/main/resources/com/assets/lease/finance/mapper/asset/AssetAttachmentMapper.xml", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/entity/RepaymentPlanEntity.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/entity/RepaymentPlanPayDetailEntity.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/entity/RepaymentPlanPaySubjectDetailEntity.java" ],
    "estimatedEffort" : "16h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖国胜导入模板、交接截止时点、资产范围和安全交付方式确认。",
    "risk" : "模板缺失且数据横跨资产库与账单库；影像URL可能为内网地址或带时效签名，存在国胜无法下载风险。"
  }, {
    "title" : "合作方网关接入国胜还款上报与对账通知能力",
    "description" : "复用现有合作方网关和支付API，完成国胜外部访问配置、线下还款上报、回购及相关对账单通知联调；线上扣款指令不在本任务确定范围内。",
    "requirementChangePoint" : "国胜接管线下还款信息同步及资产回购等后续资管操作",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "国胜外部接入账号及安全配置", "/api/trial/calc", "/api/pay/apply", "/api/query/payment/result", "/partner/business/notify", "回购及还款对账单通知" ],
    "changePoints" : [ "1. 配置/SQL：为国胜建立或调整 org_external_access 接入信息、租户映射、IP白名单、公钥及业务通知地址，并隔离历史中恒路由。", "2. 接口/服务：按国胜协议适配试算、线下还款上报和结果查询，传递账单、科目、实际罚息、实际收款时间及凭证。", "3. 通知服务：将回购、线下还款及合作方服务费对账单通知路由至国胜，适配国胜响应码并保留通知日志。", "4. 任务补偿：复用或补充通知失败重试与人工补发能力，明确超时、重试次数和失败告警。", "5. 幂等/验证：以合作方支付流水和对账单编号防重，校验资产归属、账单状态、金额合计、重复通知及回购附件URL。" ],
    "systemTags" : [ "assets-partner-gateway", "assets-lease-crm", "assets-saps" ],
    "evidenceRefs" : [ "中恒-保理资产交接国胜需求.docx：1.1需求背景、客户线下还款、回购机制", "assets-partner-gateway/src/main/java/com/assets/gateway/fileterFactory/RequestCheckGatewayFilterFactory.java", "assets-partner-gateway/src/main/java/com/assets/gateway/controller/NotifyPartnerController.java", "assets-lease-crm/assets-lease-crm-controller/src/main/java/com/lease/crm/controller/OrgExternalAccessController.java", "assets-saps/assets-saps-controller/src/main/java/com/lease/saps/controller/api/CustomerPayApiController.java", "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementManage.java", "Git：origin/feature/guosheng_handover_202610220003 当前与 origin/master 同指向805cfddc7，未发现国胜专属实现" ],
    "confidence" : "LOW",
    "moduleName" : "国胜合作方接口接入",
    "relatedFiles" : [ "assets-partner-gateway/src/main/java/com/assets/gateway/services/ConfigManageService.java", "assets-partner-gateway/src/main/java/com/assets/gateway/services/notifyService/NormalNotifyService.java", "assets-lease-crm/assets-lease-crm-model/src/main/java/com/lease/crm/model/organization/entity/OrgExternalAccess.java", "assets-lease-crm/assets-lease-crm-dao/src/main/java/com/lease/crm/mapper/organization/OrgExternalAccessMapper.xml", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/api/pay/CustomerPayApiRequest.java", "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/api/impl/CustomerPayApiBizServiceImpl.java" ],
    "estimatedEffort" : "20h",
    "ownerUserName" : "shihao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖国胜接口协议、安全接入参数、合作方编码路由和202610130003前置迭代状态；不含待确认的线上扣款方案。",
    "risk" : "缺少国胜协议，现有通知成功判断包含中恒返回格式假设；若国胜字段、签名或响应结构不同，需追加网关适配和联调工时。"
  }, {
    "title" : "账单系统调整中恒客户罚息计算与实收分摊规则",
    "description" : "将中恒客户罚息调整为5天宽限、千分之五每日并在第6天追溯自第1天计算；接收实际罚息后按资方2/3、合作方1/3写入账单及支付科目。",
    "requirementChangePoint" : "客户应还罚息规则及实还罚息2/3、1/3分配",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "BillConfig中恒罚息配置", "客户逾期计算任务", "客户账单罚息科目103", "资方罚息科目10301", "管理方罚息科目10302", "支付结算及科目实还明细" ],
    "changePoints" : [ "1. 配置/SQL：调整PRD-ZH-001宽限期、日罚息率、追溯计息模式和罚息科目配置，明确生效日及存量账单处理脚本。", "2. 接口/服务：接收并校验国胜推送的客户实际罚息金额，贯通支付申请、清分计算和支付结果结算。", "3. 罚息计算：第6个自然日首次计息时补算第1至第6日，后续按日累加，并正确处理部分还款、提前结清、回购和减免。", "4. 金额分摊：以实收罚息为基数计算资方2/3和合作方1/3，统一分币舍入及尾差归属，更新资方账单科目和支付科目实还额。", "5. 幂等/验证：按账单和计息日期防止重复计息，按合作方流水防止重复入账；覆盖宽限期第5/6日、跨月、部分还款、差额罚息和冲正场景。" ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ "中恒-保理资产交接国胜需求.docx：2.2还款台账、2.3客户账单、2.4资方账单", "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/strategy/overdue/T1CustomerBillOverdueStrategy.java", "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/strategy/clearing/T1SettleClearingStrategy.java", "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/strategy/apiPay/T4ApiPaymentStrategy.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/enums/BillSubjectNoEnum.java", "assets-saps/dbscript/DML/V1_lease_20241128.sql" ],
    "confidence" : "MEDIUM",
    "moduleName" : "客户罚息与结算分摊",
    "relatedFiles" : [ "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/strategy/overdue/T1CustomerBillOverdueStrategy.java", "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/api/impl/CustomerPayApiBizServiceImpl.java", "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/strategy/clearing/T1SettleClearingStrategy.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/profile/config/BillConfig.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/entity/RepaymentPlanFeeDetailEntity.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/entity/RepaymentPlanPaySubjectDetailEntity.java" ],
    "estimatedEffort" : "24h",
    "ownerUserName" : "shihao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖实际罚息接口字段、差额处理、分币舍入和规则生效日确认。",
    "risk" : "属于核心资金计算改造；错误可能导致客户应还、资方应收和合作方应付三方金额不一致，并污染后续回购及对账单。"
  }, {
    "title" : "账单后台增强还款台账与客户资方账单罚息展示",
    "description" : "复用现有10301、10302科目及账单字段，将实还资方罚息、实还管理方罚息贯通列表、筛选、详情和导出，并统一业务名称。",
    "requirementChangePoint" : "还款台账、客户账单和资方账单罚息字段改造",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "还款台账列表及导出", "客户账单列表及导出", "资方账单列表及导出", "还款类型下拉项", "页面数据权限" ],
    "changePoints" : [ "1. 配置/SQL：核对103、10301、10302科目名称及历史数据兼容方式；原则上复用现有表和科目，不新增金额字段。", "2. 接口/服务：还款台账返回“实还资方罚息”，客户账单返回应还/实还罚息，资方账单返回实还资方/管理方罚息，并统一金额单位。", "3. 页面/权限：调整还款类型下拉和三个页面列名、排序、筛选、租户权限及空值展示。", "4. 导出：同步三个页面导出表头与查询口径，确保分页列表和全量导出金额一致。", "5. 幂等/验证：用线上、线下、部分还款及无罚息样例核对10301+10302等于实收罚息，回归资产标签和租户过滤。" ],
    "systemTags" : [ "assets-saps", "assets-lease-admin" ],
    "evidenceRefs" : [ "中恒-保理资产交接国胜需求.docx：2.2至2.4及附件页面截图", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/RepaymentReportService.java", "assets-saps/assets-saps-dao/src/main/resources/mapper/RepaymentPlanPaySubjectDetailMapper.xml", "assets-saps/assets-saps-dao/src/main/java/com/lease/saps/mapper/asset/AssetCapitalBillMapper.xml", "assets-saps/assets-saps-dao/src/main/java/com/lease/saps/mapper/asset/AssetCustomerBillMapper.xml", "assets-lease-admin/src/views/usedCarLeaseback/reportForm/realPayment.vue", "assets-lease-admin/src/views/managementBill/index.vue" ],
    "confidence" : "HIGH",
    "moduleName" : "罚息台账与账单查询",
    "relatedFiles" : [ "assets-saps/assets-saps-controller/src/main/java/com/lease/saps/controller/manage/report/RepaymentReportController.java", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/RepaymentReportService.java", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/vo/bill/CapitalBillPageRespDataVO.java", "assets-saps/assets-saps-dao/src/main/java/com/lease/saps/mapper/asset/AssetCapitalBillMapper.xml", "assets-saps/assets-saps-dao/src/main/java/com/lease/saps/mapper/asset/AssetCustomerBillMapper.xml", "assets-lease-admin/src/views/usedCarLeaseback/reportForm/realPayment.vue", "assets-lease-admin/src/views/customerBill/index.vue", "assets-lease-admin/src/views/managementBill/index.vue" ],
    "estimatedEffort" : "12h",
    "ownerUserName" : "yinjun",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖罚息分摊任务先明确并落地10301、10302实还金额。",
    "risk" : "现有页面和接口已部分具备目标字段，实际工作量取决于历史数据是否已按正确科目落库；若需回填历史实还金额需追加数据任务。"
  }, {
    "title" : "对账系统新增合作方服务费月结与核销能力",
    "description" : "优先复用现有MANAGE_SERVICE_FEE对账单、月初生成规则和核销组件，为中恒生成上月已实际结算账单对应的合作方服务费及1/3罚息对账单。",
    "requirementChangePoint" : "每月1日生成合作方服务费对账单并支持列表、明细、导出和核销",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "statement_scene及statement_scene_rule配置", "statement_original_resource待出账资源", "statement对账单主表及费用明细", "statementGenerateSchedule月初任务", "合作方服务费列表、详情、导出和核销页面" ],
    "changePoints" : [ "1. 配置/SQL：为PRJ-ZH-001配置合作方服务费对账场景、10206服务费和10302合作方罚息科目、月初账期及付款期限；本期保持页面合作方名称不变。", "2. 接口/服务：仅采集上月线上、线下已实际结算账单，汇总合作方服务费、合作方罚息及确认后的逾期对账单扣减金额。", "3. 任务调度：每月1日由statementGenerateSchedule生成上月对账单，支持指定账期补跑、失败重试和任务告警。", "4. 页面/权限：基于ManagementServiceFeeStatement复用列表、明细和核销组件，按附件原图补齐总笔数、应付款项、服务费、合作方罚息、核销人及备注。", "5. 导出/核销：列表和明细支持一致口径导出；核销记录日期、操作人、付款日期、银行流水备注和凭证，并校验已核销单不可重复操作。", "6. 幂等/验证：按项目、合作方、账期和对账类型防重，校验明细汇总等于主单、10302为实收罚息1/3、补跑不重复占用原始资源。" ],
    "systemTags" : [ "assets-saps", "assets-lease-admin" ],
    "evidenceRefs" : [ "中恒-保理资产交接国胜需求.docx：资管服务费对账单、2.5合作方服务费对账单及列表/详情/核销截图", "assets-saps/assets-saps-model/src/main/java/com/lease/saps/model/enums/statement/StatementTypeEnum.java", "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java", "assets-saps/assets-saps-schedule/src/main/java/com/lease/saps/schedule/statement/StatementGenerateSchedule.java", "assets-saps/dbscript/DML/V2_lease_phase_two_1210.sql", "assets-lease-admin/src/views/usedCarLeaseback/statement/ManagementServiceFeeStatement.vue", "assets-lease-admin/src/views/usedCarLeaseback/statement/components/verification.vue" ],
    "confidence" : "MEDIUM",
    "moduleName" : "合作方服务费对账",
    "relatedFiles" : [ "assets-saps/assets-saps-business/src/main/java/com/lease/saps/business/statement/StatementGenerator.java", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/statement/StatementOriginalResourceService.java", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/statement/StatementResourceService.java", "assets-saps/assets-saps-service/src/main/java/com/lease/saps/service/statement/StatementService.java", "assets-saps/assets-saps-controller/src/main/java/com/lease/saps/controller/manage/statement/StatementManageController.java", "assets-saps/assets-saps-schedule/src/main/java/com/lease/saps/schedule/statement/StatementGenerateSchedule.java", "assets-saps/dbscript/DML/V2_lease_phase_two_1210.sql", "assets-lease-admin/src/views/usedCarLeaseback/statement/ManagementServiceFeeStatement.vue", "assets-lease-admin/src/views/usedCarLeaseback/statement/components/queryStatementDetail.vue", "assets-lease-admin/src/views/usedCarLeaseback/statement/components/verification.vue" ],
    "estimatedEffort" : "28h",
    "ownerUserName" : "lvliuhao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖合作方服务费科目、逾期对账单扣减口径、账期边界和202610130003资源改造状态确认。",
    "risk" : "如果业务要求新增独立对账单类型而非复用MANAGE_SERVICE_FEE，或要求对已生成的逾期对账单进行跨期冲抵，需增加枚举、接口、权限、历史兼容和冲抵状态机工时。"
  } ],
  "totalEstimatedEffort" : "100h",
  "developmentEstimatedEffort" : "100h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-27T16:04:16.216546",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 253

<!-- workhub-analysis:5550ea6150dc62e437fe1583d03f0e3ea7a4d137255d74c4d62f28f262c658a1 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：102
- 记录时间：2026-10-27T16:06:11.070745
- 操作者：admin
- 来源：[source.md](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610220003/历史版本/0cd418eb-61b2-4865-9d74-63be8112b416/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : "ca-assets (24 repositories)",
  "requirementRawPath" : "raw/公司项目/requirements/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610220003/历史版本/0cd418eb-61b2-4865-9d74-63be8112b416/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求涉及 assets-lease、assets-saps、assets-lease-admin、assets-partner-gateway、assets-lease-crm，建议拆为5个可排期任务，预估合计100h。现有系统已具备资产及影像查询、合作方支付API、还款台账罚息科目、客户/资方账单罚息字段、月初管理服务费对账单及核销框架，可复用后改造成国胜交接链路。当前中恒罚息算法仍按原规则执行，与“宽限5个自然日、千分之五/日并从第1日追溯、实收罚息按资方2/3和合作方1/3分配”不一致。用户扣款方案明确待确认，本评估未计入线上扣款改造；确认后可能新增 assets-payment 改造和工时。国胜交接远端分支目前与主干同一基线，尚无本需求实现提交。本次仅完成只读评估，未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "按国胜导入模板交付中恒存量资产清单、还款计划表和实际还款记录，并提供资产影像资料URL；国胜侧人工导入。", "为国胜配置外部访问、验签、IP白名单及通知路由，接收国胜推送的线下还款信息，并将回购等对账单通知切换至国胜。", "用户扣款由国胜下发指令，候选方案分别使用“国胜中金商户号”或“通汇嘉泰中金商户号”发起扣款并100%清分至通汇商户；具体方案待确认。", "中恒客户账单还款宽限期调整为应还日后5个自然日；第6日起按逾期金额千分之五每日计收，并追溯计算自逾期第1日起的实际逾期天数。", "国胜推送客户实际罚息金额，系统按实收罚息计算实还资方罚息2/3、实还管理方或合作方罚息1/3，并贯通支付结算及科目明细。", "还款台账增加或规范“实还资方罚息”还款类型，列表、筛选及导出口径保持一致。", "客户账单展示应还罚息和国胜推送的实还罚息，支持列表及导出口径一致。", "资方账单展示实还资方罚息和实还管理方罚息，并保持列表、详情及导出一致。", "每月1日自动汇总上月线上、线下已实际结算账单，生成合作方服务费对账单，包含合作方服务费、合作方罚息及需求所述逾期对账单扣减项。", "合作方服务费对账单提供列表、明细、导出和核销能力，记录核销日期、操作人、备注及银行凭证；合作方名称本期保持不变。" ],
  "impactedModules" : [ "assets-lease", "assets-saps", "assets-lease-admin", "assets-partner-gateway", "assets-lease-crm" ],
  "risks" : [ "资金口径风险：现有中恒 T1 罚息逻辑采用原费率和资方年化费率算法，直接修改可能影响存量已逾期账单、减免、回购、提前结清及历史对账单，必须明确规则生效日和历史数据处理方式。", "金额分摊风险：2/3与1/3在分级计算时可能产生分币差，且国胜推送实还罚息与系统应还罚息可能不一致，当前材料没有给出舍入及差额处理规则。", "接口风险：材料未提供国胜接口文档、签名规则、错误码、重试规范及接入参数；国胜专属代码证据不存在，因此接口任务置信度为LOW。", "路由风险：现有对账单通知按合作方编码查询外部接入配置；若在合作方名称暂不修改的同时复用原中恒编码，可能覆盖历史中恒通知路由。", "数据交接风险：未在可访问材料中定位到“导入模板_v2.xlsx”，也未明确交接截止时点、存量资产范围及影像URL的外网访问期限，无法最终确定字段映射和导出条数。", "对账口径风险：“逾期对账单金额”的来源、状态范围和扣减期间未定义；现有 MANAGE_SERVICE_FEE 能力虽可复用，但可能与目标“合作方服务费对账单”口径不同。", "迭代依赖风险：202610130003的已确认评估涉及还款渠道及对账资源改造，但当前远端研发分支相对主干仅有接口注释变化；若该迭代尚未实现，本需求需要合并排期并处理同一支付、对账链路的代码冲突。", "扣款范围风险：用户扣款方案尚未确认，本次100h预估不含线上扣款通道和商户号切换；确认后可能增加 assets-payment、支付配置及联调任务。" ],
  "questions" : [ "用户扣款最终选择“国胜中金商户号”还是“通汇嘉泰中金商户号”？国胜需要传入哪些扣款要素、由哪个系统调用支付通道、切换生效日是什么？", "国胜是否使用新的合作方编码和外部接入账号？若页面继续展示原合作方名称，应如何区分新国胜路由与历史中恒路由，避免历史通知被覆盖？", "请补充国胜还款、回购对账单通知的接口文档，以及商户ID、公钥、IP白名单、通知地址、错误码、超时重试和幂等约定。", "请提供“导入模板_v2.xlsx”，并确认交接截止时间、资产范围是全部历史资产还是仅在贷资产，以及影像URL所需访问权限和有效期。", "实收罚息按2/3与1/3分配时采用何种分币舍入规则？国胜推送金额大于或小于系统应还罚息时，是拒绝、按推送金额入账，还是生成差额待处理？", "合作方服务费具体对应现有哪个科目，是否复用10206管理服务费和10302管理方罚息？公式中的“逾期对账单金额”具体指哪些对账单、哪些状态及哪个月份的金额？", "本需求上线时，202610130003“正常还款/提前结清对账单优化”是否已作为前置版本发布？若未发布，是否允许合并为同一迭代交付？" ],
  "developerPool" : [ "admin", "cairui", "hanmeng", "lvliuhao", "shihao", "xuewentao", "xujieni", "yinjun", "yufanghua", "zhiyuntao", "尹军", "曹彬", "江敏", "王文龙", "石浩" ],
  "workItems" : [ {
    "title" : "交接资料准备：资产清单、资产影像资料、还款计划表、实际还款记录",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-saps", "assets-lease" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "shihao",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "保证金比例改为5%",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-lease" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "1h",
    "ownerUserName" : "shihao",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "国胜接口对接联调",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-lease" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "24h",
    "ownerUserName" : "shihao",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "客户账单逾期&页面字段",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-lease-admin", "assets-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "yinjun",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "资方账单新合作方罚息",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-lease-admin", "assets-lease" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "yinjun",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "还款罚息清分&收款方修改，嘉泰全部收款",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "24h",
    "ownerUserName" : "cairui",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "合作方对账单页面，出账， 罚息， 核销",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-lease-admin", "assets-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "16h",
    "ownerUserName" : "xujieni",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "回购对账单规则调整",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "xujieni",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  }, {
    "title" : "还款台账新增资方罚息",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-lease-admin", "assets-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "yinjun",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  } ],
  "totalEstimatedEffort" : "125h",
  "developmentEstimatedEffort" : "101h",
  "testingEstimatedEffort" : "24h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-10-27T16:04:16.216546",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null
}
```




### WorkHub 临时分析 · 生成研发评估 · 需求 256

<!-- workhub-analysis:0543ec6acf87cca2e02cb8ad3ee255bd609789b688cdcda7b93d5d406d6ea336 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：106
- 记录时间：2026-11-02T09:46:58.342824
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000005/BL000005/2026/202610230007/历史版本/4f6cde27-7507-4179-8946-26c6e1d86199/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000005",
  "projectId" : null,
  "projectName" : "BL000005",
  "repositoryUrl" : "ol (16 repositories)",
  "requirementRawPath" : "requirement-sources/BL000005/BL000005/2026/202610230007/历史版本/4f6cde27-7507-4179-8946-26c6e1d86199/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：建议拆分为 8 个可独立排期的业务任务，预估合计 224h。现有代码可复用支付通道策略、XXL-Job、缴费单/客户账单/对账单核销、付款单逐笔代付、提现冻结与轮询框架；交通银行接口实现、收款台账和备用通道路由尚无代码证据。未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "RCP-01：周期调用交通银行回单及账户查询接口，获取并保存先行通对公账户收款记录。", "RCP-02：按附言码/对账单号、付款户名和金额三要素匹配缴费单、客户账单或设备厂家对账单，自动调用原核销链并登记自动入账明细。", "RCP-03：新增对公收款管理，支持多条件查询、状态及挂账原因展示、金额汇总、入账明细和导出。", "RCP-04：未入账或已驳回收款可发起手工入账，经审核通过后核销业务单；支持驳回、重新发起和完整审计记录。", "RCP-05：银行转账收银台随机生成、展示、复制并持久化附言码，作为缴费单和客户账单自动匹配条件。", "RCP-06：代理提现增加交通银行对私跨行支付备用通道，单笔上限 100000 元，仅在现有代付通道全部停用时启用。", "RCP-07：付款单批量审核通过后可经交通银行 210201 接口按付款单逐笔对公转账，并执行每日 50 笔、每日 13000000 元、每年 50000000 元限额。" ],
  "impactedModules" : [ "ol-payment", "ol-saps", "ol-h5", "ol-admin", "ol-marketing", "ol-order" ],
  "risks" : [ "材料未提供交通银行回单、账户查询及 210201 接口规范、签名加密方式、证书部署方式、错误码和联调环境，银行接入任务置信度为 LOW。", "代码中同时存在 xxt-pay 与 xxt-payment 两套服务名及不同代付契约，必须确认它们与 ol-payment 的实际部署映射，否则代理提现改造系统可能判断错误。", "收款记录重复拉取、定时任务重试、审核重复点击及银行响应超时均可能造成重复核销或重复付款，必须使用银行流水唯一键、业务幂等键和分布式锁。", "相同附言码、户名和金额可能匹配多张业务单；未定义冲突规则时不得自动核销，应转为挂账待人工处理。", "原型允许一笔收款拆分给多张业务单并部分入账，但现有缴费单核销代码要求付款金额等于未付总额，规则冲突会显著影响改造范围。", "对公日/年额度在多实例并发付款下需原子占额；若失败、处理中、退票的额度释放口径不明确，可能错误拦截或超限。", "ol-saps 当前工作区中 CustomerOrderEntity.java 处于删除状态，本次依据其调用方及其他可读代码评估，排期前应确认正式开发基线。" ],
  "questions" : [ "请提供交通银行回单、账户查询和 210201 接口文档，以及测试地址、测试账号、证书/签名方案、生产账户清单和银行错误码说明。", "“每周期”具体频率是多少，首次上线是否需要补拉历史收款；若需要，补拉起始日期和单次查询窗口如何确定？", "xxt-pay、xxt-payment 与候选系统 ol-payment 的实际关系是什么；“现有支付通道全部停用”由哪个配置判定，是自动切换还是运营手工启用，原通道恢复后是否自动切回？", "对公日/年限额按自然日/自然年和北京时间统计吗；处理中、失败、撤销、退票及重试交易分别是否占用或释放额度？", "当三要素匹配出多张业务单时，是否统一挂账禁止自动核销；是否允许按项目、业务单时间或其他优先级自动选择？", "请确认以原型为准允许一笔收款拆分至多张业务单并部分入账，还是缴费单、账单和对账单均必须全额核销。", "手工入账发起人与审核人是否必须岗位分离；哪些角色拥有查询、导出、发起、审核通过和驳回权限？", "材料提到叉车系统与 OL 未合并时分别展示，但当前候选系统未包含独立叉车仓库；本次是否仅评估现有 OL 仓库，还是还需补充独立叉车系统代码范围？" ],
  "developerPool" : [ "admin", "cairui", "hanmeng", "lvliuhao", "shihao", "xuewentao", "xujieni", "yinjun", "yufanghua", "zhiyuntao", "尹军", "曹彬", "江敏", "王文龙", "石浩" ],
  "workItems" : [ {
    "title" : "ol-payment 对接交通银行对公收款查询能力",
    "description" : "封装交通银行回单及账户交易查询，为业务系统提供标准化、可增量拉取的对公入账流水。",
    "requirementChangePoint" : "RCP-01：周期获取交通银行对公收款记录。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "交通银行回单接口", "交通银行账户查询接口", "支付渠道配置库" ],
    "changePoints" : [ "1. 扩展渠道枚举及渠道配置，登记交行接口地址、账户、证书引用、启停状态和查询参数，敏感信息仅由运行配置注入。", "2. 新增回单/交易查询请求响应模型及渠道客户端，支持时间窗口、分页、断点续查和银行错误码转换。", "3. 统一输出银行流水号、付款/收款账号及户名、金额、附言、交易时间和交易方向等字段。", "4. 增加超时重试、失败告警、脱敏日志及接口调用审计。", "5. 以银行流水号、收款账号等组合键保证重复查询幂等，并补充签名、分页、重试和重复流水测试。" ],
    "systemTags" : [ "ol-payment" ],
    "evidenceRefs" : [ "requirement-sources/BL000005/BL000005/2026/202610230007/历史版本/4f6cde27-7507-4179-8946-26c6e1d86199/附件/465-先行通设备科技银企直连应用方案.docx", "ol-payment/lease-payment-common/src/main/java/com/lease/payment/common/enums/ChannelEnum.java", "ol-payment/lease-payment-model/src/main/java/com/lease/payment/model/entity/RemitChannelConfigEntity.java", "ol-payment/lease-payment-service/src/main/java/com/lease/payment/service/impl/RemitChannelConfigServiceImpl.java" ],
    "confidence" : "LOW",
    "moduleName" : "支付渠道接入",
    "relatedFiles" : [ "ol-payment/lease-payment-common/src/main/java/com/lease/payment/common/enums/ChannelEnum.java", "ol-payment/lease-payment-model/src/main/java/com/lease/payment/model/entity/RemitChannelConfigEntity.java", "ol-payment/lease-payment-service/src/main/java/com/lease/payment/service/impl/RemitChannelConfigServiceImpl.java", "ol-payment/dbscript/DDL/" ],
    "estimatedEffort" : "32h",
    "ownerUserName" : "yufanghua",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖交通银行正式接口文档、证书方案和联调环境。",
    "risk" : "当前仓库只有通联等渠道实现，未发现交通银行交易查询或回单接口代码。"
  }, {
    "title" : "ol-saps/ol-h5 生成展示银行转账附言码规则",
    "description" : "恢复现有附言码预留链路，在银行转账前生成可匹配的随机码并展示、复制和持久化。",
    "requirementChangePoint" : "RCP-05：银行转账随机生成并展示附言码。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "银行转账收银台", "线下收款账户接口", "客户支付订单" ],
    "changePoints" : [ "1. 服务端生成随机附言码，定义唯一性、有效期、重复进入收银台及重新支付时的复用规则。", "2. 在线下收款账户接口返回附言码，并通过短期缓存绑定租户、客户和待支付业务单。", "3. 支付提交时校验附言码绑定关系，并写入客户支付订单的 remarkCode 字段。", "4. ol-h5 银行转账页展示附言码及复制入口，沿用现有 payApply 请求提交 remarkCode。", "5. 增加碰撞、过期、重复提交和篡改附言码验证。" ],
    "systemTags" : [ "ol-saps", "ol-h5" ],
    "evidenceRefs" : [ "附件 DOCX image7：银行转账页面附言码原型", "ol-h5/src/views/CashierDesk/BankTransfer.vue", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/app/CustomerPayAppController.java", "ol-saps/lease-saps-business/src/main/java/com/lease/saps/business/strategy/pay/OfflinePaymentStrategy.java", "ol-saps/lease-saps-business/src/main/java/com/lease/saps/business/service/order/impl/CustomerPayOrderBizImpl.java" ],
    "confidence" : "HIGH",
    "moduleName" : "银行转账收银台",
    "relatedFiles" : [ "ol-h5/src/views/CashierDesk/BankTransfer.vue", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/app/CustomerPayAppController.java", "ol-saps/lease-saps-business/src/main/java/com/lease/saps/business/impl/CustomerPayBizServiceImpl.java", "ol-saps/lease-saps-business/src/main/java/com/lease/saps/business/service/order/impl/CustomerPayOrderBizImpl.java" ],
    "estimatedEffort" : "12h",
    "ownerUserName" : "江敏",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : "现有接口固定返回空白附言码，Redis 校验逻辑和 H5 展示均被注释，恢复时需明确有效期与重复支付规则。"
  }, {
    "title" : "ol-saps 新增对公收款周期同步与自动核销能力",
    "description" : "保存银行收款台账，按三要素匹配业务单并复用现有核销服务完成自动入账。",
    "requirementChangePoint" : "RCP-01、RCP-02、RCP-03：同步收款、自动匹配核销并提供查询导出。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "对公收款台账", "XXL-Job", "缴费单", "客户账单", "设备厂家对账单" ],
    "changePoints" : [ "1. 新建对公收款、入账明细及同步游标表，配置银行流水唯一索引、状态、挂账原因、项目归属和审计字段。", "2. 扩展 PaymentApiService 调用标准化收款查询接口。", "3. 新增 XXL-Job 周期拉取任务，支持时间游标、分页、补拉、失败重试和告警。", "4. 缴费单/客户账单按附言码完全相等、付款户名相等、金额相等匹配；设备厂家对账单按附言包含对账单号、厂家名称相等、金额相等匹配。", "5. 唯一匹配后复用现有缴费单、客户账单和对账单核销能力，记录自动入账、操作员 admin 及关联业务单明细。", "6. 无匹配、多匹配或字段不一致时生成明确挂账原因，不执行自动核销。", "7. 通过唯一键、分布式锁和事务状态校验避免重复拉取、重复核销及任务并发。", "8. 提供分页筛选、金额汇总、入账明细及按筛选条件导出接口。" ],
    "systemTags" : [ "ol-saps" ],
    "evidenceRefs" : [ "先行通设备科技银企直连后台原型图.html：对公收款列表、挂账提示、汇总及导出", "ol-saps/lease-saps-api/src/main/java/com/lease/saps/api/PaymentApiService.java", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/PaymentSlipManageController.java", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/CustomerBillManageController.java", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/StatementManageV2Controller.java", "ol-saps/lease-saps-business/src/main/java/com/lease/saps/business/schedule/PaymentSlipSchedule.java" ],
    "confidence" : "MEDIUM",
    "moduleName" : "对公收款自动入账",
    "relatedFiles" : [ "ol-saps/lease-saps-api/src/main/java/com/lease/saps/api/PaymentApiService.java", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/PaymentSlipManageController.java", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/CustomerBillManageController.java", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/StatementManageV2Controller.java", "ol-saps/lease-saps-business/src/main/java/com/lease/saps/business/schedule/", "ol-saps/dbscript/DDL/" ],
    "estimatedEffort" : "40h",
    "ownerUserName" : "lvliuhao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖交通银行收款查询能力及附言码任务。",
    "risk" : "跨三类业务单调用不同核销入口；名称、金额精度或重复候选数据异常时必须挂账，不能猜测核销。"
  }, {
    "title" : "ol-saps 新增对公收款手工入账审核与重提能力",
    "description" : "为未自动匹配的收款提供业务单查询、入账申请、审核、驳回和重新发起流程。",
    "requirementChangePoint" : "RCP-04：手工入账、审核、驳回及重新发起。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "手工入账申请", "入账审核记录", "原核销服务" ],
    "changePoints" : [ "1. 扩展收款及入账明细结构，保存申请人、申请时间、审核人、审核时间、审核意见和状态流转记录。", "2. 提供缴费单、客户账单和设备厂家对账单候选查询，过滤已结清、已取消、退款中等不可核销记录。", "3. 校验勾选业务单、本次入账金额、未还金额、收款可入账余额及拆分合计，禁止超额入账。", "4. 发起后将收款置为待审核；驳回必须填写意见并允许重新发起。", "5. 审核通过时在事务内复用原核销规则，写入入账明细并更新为已入账或部分入账。", "6. 增加发起、审核、驳回权限及岗位分离校验，并记录操作审计。", "7. 对重复审核、并发发起和业务单状态变化执行幂等及二次校验。" ],
    "systemTags" : [ "ol-saps" ],
    "evidenceRefs" : [ "先行通设备科技银企直连后台原型图.html：手工入账、待审核、已驳回和部分入账交互", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/PaymentSlipManageController.java", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/CustomerBillManageController.java", "ol-saps/lease-saps-business/src/main/java/com/lease/saps/business/impl/SettlementBizServiceImpl.java" ],
    "confidence" : "MEDIUM",
    "moduleName" : "对公收款手工入账",
    "relatedFiles" : [ "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/PaymentSlipManageController.java", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/CustomerBillManageController.java", "ol-saps/lease-saps-controller/src/main/java/com/lease/saps/controller/manage/StatementManageV2Controller.java", "ol-saps/lease-saps-business/src/main/java/com/lease/saps/business/impl/SettlementBizServiceImpl.java", "ol-saps/dbscript/DDL/" ],
    "estimatedEffort" : "32h",
    "ownerUserName" : "lvliuhao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖对公收款台账及自动核销任务的数据模型。",
    "risk" : "现有缴费单入口只允许全额核销，与原型的拆分及部分入账能力冲突，需产品先确认口径。"
  }, {
    "title" : "ol-admin 新增对公收款管理与入账审核页面",
    "description" : "按附件原型实现收款列表、明细、手工入账、审核和导出操作。",
    "requirementChangePoint" : "RCP-03、RCP-04：对公收款后台管理及审核页面。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "对公收款管理菜单", "入账明细弹窗", "手工入账弹窗", "入账审核弹窗" ],
    "changePoints" : [ "1. 新增对公收款管理页面及接口封装，接入菜单资源和路由。", "2. 实现交易流水、账户、项目、状态、时间、金额、附言、入账方式、操作员和挂账原因筛选。", "3. 展示收款列表、状态、挂账原因、筛选金额合计和入账明细。", "4. 实现未入账/已驳回记录的手工入账弹窗、候选业务单查询、勾选及金额校验。", "5. 实现待审核数量、待审核/已审核页签、通过、驳回及重新发起交互。", "6. 接入按筛选条件导出，并处理账号脱敏及大数据量导出反馈。", "7. 配置查询、导出、发起入账和审核按钮权限，避免仅靠前端状态控制。" ],
    "systemTags" : [ "ol-admin" ],
    "evidenceRefs" : [ "先行通设备科技银企直连后台原型图.html", "附件 DOCX image2-image6：列表、明细、手工入账及审核原型", "ol-admin/src/views/saps/customerBill/index.vue", "ol-admin/src/views/saps/paymentSlip/index.vue", "ol-admin/src/views/saps/customerBill/components/WriteOffDialog.vue" ],
    "confidence" : "HIGH",
    "moduleName" : "对公收款管理页面",
    "relatedFiles" : [ "ol-admin/src/api/api_list.js", "ol-admin/src/views/saps/customerBill/index.vue", "ol-admin/src/views/saps/paymentSlip/index.vue", "ol-admin/src/views/saps/customerBill/components/WriteOffDialog.vue", "ol-admin/src/views/" ],
    "estimatedEffort" : "32h",
    "ownerUserName" : "王文龙",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖对公收款查询、导出及手工入账审核接口。",
    "risk" : "菜单资源和角色授权数据不在当前前端仓库内，需同步配置权限平台或菜单数据库。"
  }, {
    "title" : "ol-payment 新增交通银行对私对公备用代付与额度控制能力",
    "description" : "在现有代付策略框架中实现交通银行 210201 等交易能力，并作为现有通道全部停用后的备用路由。",
    "requirementChangePoint" : "RCP-06、RCP-07：对私及对公交通银行备用跨行支付。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "交通银行 210201 接口", "代付通道路由", "支付额度台账" ],
    "changePoints" : [ "1. 扩展交通银行渠道、商户/账户、接口地址、证书引用、启停状态及额度配置 SQL。", "2. 实现对私和对公转账申请、结果查询及银行回执解析策略，统一映射处理中、成功和失败状态。", "3. 改造当前硬编码默认通道，只有现存代付通道全部停用时才路由至交通银行，并记录路由决策。", "4. 根据 accountProp 和业务场景区分对私与对公请求，对公使用 210201 并按业务单逐笔提交。", "5. 对私校验单笔 100000 元；对公校验每日 50 笔、每日 13000000 元、每年 50000000 元。", "6. 建立原子额度占用台账，处理并发、处理中、失败、重试、撤销和退票的占用或释放。", "7. 以业务订单号和银行请求流水保证重复提交幂等；超时结果未知时保持处理中并主动查询，禁止直接重付。", "8. 保持 xxt-pay/xxt-payment 调用方所需响应字段兼容，并增加通道切换、额度及异常结果测试。" ],
    "systemTags" : [ "ol-payment" ],
    "evidenceRefs" : [ "先行通设备科技银企直连应用方案.docx：对私备用支付、对公 210201 及限额", "ol-payment/lease-payment-business/src/main/java/com/lease/payment/business/service/impl/RemitBizServiceImpl.java", "ol-payment/lease-payment-business/src/main/java/com/lease/payment/business/channel/RemitChannelStrategyBiz.java", "ol-payment/lease-payment-business/src/main/java/com/lease/payment/business/channel/impl/remit/AllinPayRemitStrategyImpl.java", "ol-payment/lease-payment-service/src/main/java/com/lease/payment/service/impl/RemitChannelConfigServiceImpl.java" ],
    "confidence" : "LOW",
    "moduleName" : "备用代付通道",
    "relatedFiles" : [ "ol-payment/lease-payment-common/src/main/java/com/lease/payment/common/enums/ChannelEnum.java", "ol-payment/lease-payment-business/src/main/java/com/lease/payment/business/service/impl/RemitBizServiceImpl.java", "ol-payment/lease-payment-business/src/main/java/com/lease/payment/business/channel/RemitChannelStrategyBiz.java", "ol-payment/lease-payment-model/src/main/java/com/lease/payment/model/remit/RemitReq.java", "ol-payment/dbscript/DDL/" ],
    "estimatedEffort" : "40h",
    "ownerUserName" : "yufanghua",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖交通银行 210201/查询接口文档及备用通道切换口径。",
    "risk" : "当前代付代码默认固定选择 ALLINPAY，且没有需求所述累计限额台账；银行异步结果与退票规则未知。"
  }, {
    "title" : "ol-marketing 适配代理提现交通银行备用代付规则",
    "description" : "保持现有代理提现冻结、支付及轮询框架，适配备用通道路由和 10 万元对私限额。",
    "requirementChangePoint" : "RCP-06：代理立即提现使用交通银行对私备用通道。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "代理提现流程", "xxt-payment 代付契约", "提现结果轮询" ],
    "changePoints" : [ "1. 明确代理提现请求的业务场景、PERSONAL 账户属性和备用通道路由识别字段。", "2. 增加对私单笔 100000 元校验及可识别的前端错误返回。", "3. 兼容交通银行返回的渠道编码、流水号及处理中/成功/失败状态。", "4. 复用现有 merchantSn、资金冻结、结果轮询和长时间未决告警，避免未知结果时提前解冻或重复打款。", "5. 增加正常通道启用、全部停用后切换交行、超限、超时及重复提交测试。" ],
    "systemTags" : [ "ol-marketing" ],
    "evidenceRefs" : [ "附件 DOCX image8：代理提现原型", "ol-marketing/marketing-management/src/main/java/com/thchengtay/modules/account/service/impl/XxtPaymentWithdrawServiceImpl.java", "ol-marketing/marketing-management/src/main/java/com/thchengtay/modules/account/domain/dto/H5WithdrawApplyRequest.java", "ol-marketing/marketing-api/src/main/java/com/thchengtay/api/payment/client/PaymentRemitClient.java", "ol-marketing/marketing-management/src/main/resources/bpmn/WithdrawProcess.bpmn" ],
    "confidence" : "LOW",
    "moduleName" : "代理提现",
    "relatedFiles" : [ "ol-marketing/marketing-management/src/main/java/com/thchengtay/modules/account/service/impl/XxtPaymentWithdrawServiceImpl.java", "ol-marketing/marketing-management/src/main/java/com/thchengtay/modules/account/domain/dto/H5WithdrawApplyRequest.java", "ol-marketing/marketing-api/src/main/java/com/thchengtay/api/payment/client/PaymentRemitClient.java", "ol-marketing/marketing-management/src/main/resources/bpmn/WithdrawProcess.bpmn" ],
    "estimatedEffort" : "12h",
    "ownerUserName" : "xuewentao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖交通银行备用代付能力及 xxt-payment 与 ol-payment 部署关系确认。",
    "risk" : "当前营销系统调用 xxt-payment 契约，与 ol-payment 当前 RemitReq/响应包装不完全一致，可能需要额外支付网关仓库改造。"
  }, {
    "title" : "ol-order/ol-admin 接入付款单交通银行逐笔备用付款规则",
    "description" : "沿用批量发起和批量审核流程，在审核通过后按付款单逐笔调用备用通道并可靠回写结果。",
    "requirementChangePoint" : "RCP-07：付款单通过交通银行 210201 逐笔对公付款。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "付款单批量审核", "付款单代付请求", "支付结果回调", "付款确认提示" ],
    "changePoints" : [ "1. 付款单审核通过时传递 CORPORATE 账户属性、采购付款场景、采购方主体和厂家对公账户信息。", "2. 每张付款单生成稳定且可重试的幂等业务流水，按单逐笔调用支付系统，不使用可能碰撞的时间片流水。", "3. 调用前获取对公额度预校验结果并向操作人展示超笔数、超日限额或超年限额原因。", "4. 对银行同步终态、异步处理中、超时未知和明确失败分别处理，处理中由查询/回调推进，禁止直接重复付款。", "5. 回写付款单、批次记录及支付明细，保持成功置已付款、失败恢复可重发的现有规则。", "6. ol-admin 保留批量发起和批量审核交互，根据实际路由展示第三方支付或交通银行付款提示。", "7. 增加多采购方、逐笔部分成功、回调重复、超时查询和额度并发测试。" ],
    "systemTags" : [ "ol-order", "ol-admin" ],
    "evidenceRefs" : [ "先行通设备科技银企直连应用方案.docx：付款单批量审核后按单逐笔调用 210201", "ol-order/order-controller/src/main/java/com/ol/order/controller/paymentorder/manage/PaymentOrderManageController.java", "ol-order/order-service/src/main/java/com/ol/order/service/paymentorder/PaymentOrderSingleAuditService.java", "ol-order/order-api/src/main/java/com/ol/order/api/PaymentClient.java", "ol-admin/src/views/paymentOrder/index.vue" ],
    "confidence" : "MEDIUM",
    "moduleName" : "付款单线上付款",
    "relatedFiles" : [ "ol-order/order-controller/src/main/java/com/ol/order/controller/paymentorder/manage/PaymentOrderManageController.java", "ol-order/order-service/src/main/java/com/ol/order/service/paymentorder/PaymentOrderSingleAuditService.java", "ol-order/order-service/src/main/java/com/ol/order/service/paymentorder/PaymentOrderBatchAuditService.java", "ol-order/order-api/src/main/java/com/ol/order/api/PaymentClient.java", "ol-admin/src/views/paymentOrder/index.vue", "ol-admin/src/api/api_list.js" ],
    "estimatedEffort" : "24h",
    "ownerUserName" : "lvliuhao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖交通银行备用代付及对公额度控制能力。",
    "risk" : "现有付款调用异常时返回 null 并记录 ERROR，未区分银行未受理与结果未知；直接重试可能造成重复付款。"
  } ],
  "totalEstimatedEffort" : "224h",
  "developmentEstimatedEffort" : "224h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-02T09:46:58.256638",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 生成需求澄清项 · 需求 269

<!-- workhub-analysis:6b846c45f3b5252beb0b951a45025c88fedcc7709ccc176045754e636269f4f8 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：38
- 记录时间：2026-11-03T09:46:46.987541
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/fce84ec9-c1dc-4819-8564-86efe151e810/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "确认“已结清”的判定口径",
  "description" : "请确认“已结清账单”应以用户实际还款状态为“已结算”、账单结算状态为“已结算”，还是未还总金额为 0 判定。",
  "evidence" : "需求材料仅使用“已结清”表述；代码同时存在 customerSettlementStatus、settlementStatus、unpaidAmount，且 BillStatusEnum 中没有“已结清”状态。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "确认功能适用的项目范围",
  "description" : "请确认支付方式列、筛选项和导出字段仅适用于申城医美诚泰保理项目，还是适用于【用户账单】页面中的全部项目。",
  "evidence" : "需求名称和项目信息指向申城项目；代码中的【用户账单】页面和查询接口为公共入口，申城项目代码为 XXT-PRJ-SC-A，产品为 PRD-SC-A001、PRD-SC-A002。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "QUESTION",
  "title" : "确认“最后一次还款行为”的排序依据",
  "description" : "出现多次还款时，请确认按结算时间、到账时间还是操作时间确定最后一次还款行为。",
  "evidence" : "需求只规定按“最后一次还款行为”判定；RepaymentPlanPayDetailEntity 同时保存 settlementTime、accountTime、operateTime，现有支付详情查询未明确排序。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "QUESTION",
  "title" : "确认冲正或撤销记录是否参与判定",
  "description" : "请确认已冲正、已撤销、已删除或已取消的支付明细是否应排除，只按最终有效的结清记录判定支付方式。",
  "evidence" : "支付明细模型存在 isReverse，并继承删除、取消标记；现有支付详情接口仅按 billNo 查询，需求材料未说明异常记录的处理规则。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "QUESTION",
  "title" : "确认历史账单的回溯范围",
  "description" : "请确认功能上线前已经结清的账单是否也必须按相同规则补齐并展示支付方式。",
  "evidence" : "需求未限定生效日期；代码中的 repayment_plan.paymentMode 已标记为 Deprecated，需求所述判定依据更接近 repayment_plan_pay_detail，材料未提供历史数据补齐要求。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "QUESTION",
  "title" : "确认已结清但缺少有效支付明细时的展示值",
  "description" : "请确认已结清账单查不到有效支付明细时，应展示为空、展示“未知”，还是允许回退使用账单表原有支付方式。",
  "evidence" : "需求仅明确非已结清账单为空，没有规定已结清但支付明细缺失的处理方式；代码中同时存在已废弃的账单支付方式字段和支付明细支付方式字段。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "历史账单支付方式可能无法完整回溯",
  "description" : "如果历史支付明细存在缺失、无支付方式或冲正状态不完整，历史已结清账单可能显示为空或分类错误，进而导致线上、线下筛选及导出对账结果漏数；需确认是否接受该风险。",
  "evidence" : "代码能证明账单汇总字段与支付明细字段并存，且汇总字段已标记 Deprecated；仓库和需求材料均未提供生产历史数据完整性核验结果，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### WorkHub 临时分析 · 调整研发任务 · 需求 265

<!-- workhub-analysis:23bd72b60df280e109037778fdb2378530102663da669d0fc383e4baba121782 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：104
- 记录时间：2026-11-03T09:52:16.256425
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202610260004/历史版本/4c55bf73-9ded-4476-a9e3-07c2b22a46db/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202610260004/历史版本/4c55bf73-9ded-4476-a9e3-07c2b22a46db/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "申城资管平台新增按产品维度的放款金额校验：PRD-SC-A001付息产品和PRD-SC-A002贴息产品的放款申请接口打款金额（transMoney）必须等于客户申请贷款本金（applyAmt）；不相等时拒绝放款申请，并返回“打款金额不符合要求”。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "xujieni" ],
  "workItems" : [ {
    "title" : "产品维度增加放款金额校验",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "scs-capital", "scs-admin" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "xujieni",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  } ],
  "totalEstimatedEffort" : "8h",
  "developmentEstimatedEffort" : "4h",
  "testingEstimatedEffort" : "4h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-03T09:52:15.946211",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 258

<!-- workhub-analysis:8d3b028f086b2ce29fbc0ad2481c1c723318e803e0b800dd0600f0f49d7f3b32 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：105
- 记录时间：2026-11-03T09:54:07.364490
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/保费分期/聚保融保费分期项目/2026/202610230002/历史版本/a3378e76-4554-4e25-82f4-13bdae24bbd4/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "保费分期",
  "projectId" : 21,
  "projectName" : "聚保融保费分期项目",
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/保费分期/聚保融保费分期项目/2026/202610230002/历史版本/a3378e76-4554-4e25-82f4-13bdae24bbd4/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "挂靠公司线下签约模式订单向中化保理申请融资进件时，在进件申请接口 contractlist 中新增推送《授权委托暨业务合作声明书》附件；附件通过文件上传接口获取 fileKey，并按 fileType=400（其他附件）推送。该附件非必传，仅线下签约模式订单推送，线上签约模式订单无需推送。同时，线下签约模式下《电子签名授权委托书（企业）》和《应收账款转让通知书（企业）》非必传，线上签约模式仍需传输。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "hanmeng" ],
  "workItems" : [ {
    "title" : "中化线上化需求-声明书传输",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "scf-intf-zhonghua" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  } ],
  "totalEstimatedEffort" : "16h",
  "developmentEstimatedEffort" : "8h",
  "testingEstimatedEffort" : "8h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-03T09:54:07.020938",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 生成研发评估 · 需求 269

<!-- workhub-analysis:134d0fa0f61cb0a210300c7c56ba3baa5d18a0de06e28fbf7c6274217778f93f -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：108
- 记录时间：2026-11-05T11:07:30.437374
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：改造 scs-saps 与 scs-admin，共拆分 2 个研发任务，预估 20h。现有支付方式枚举、查询字段和前端字典可复用，但账单表 paymentMode 已废弃，需以支付明细表的最后一次有效结清行为统一支撑列表、筛选、汇总及导出。本次未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "【用户账单】列表新增“支付方式”列，仅已结清账单展示，其他状态为空。", "支付方式按账单最后一次有效结清行为判定：线上扣款清分显示“线上”，线下还款、退费、代偿、回购等显示“线下”。", "【用户账单】查询条件新增“支付方式”，选项为线上、线下，并保证列表、总数和金额汇总采用一致口径。", "【用户账单】-【导出明细】新增“支付方式”字段，字段值及筛选结果与页面一致。" ],
  "impactedModules" : [ "scs-admin：用户账单列表、查询条件及导出入口", "scs-saps：用户账单查询接口、支付方式判定、分页计数与金额汇总", "scs-saps：用户账单异步导出数据及 Excel 模板" ],
  "risks" : [ "“已结清”可能对应 customerSettlementStatus、settlementStatus 或 unpaidAmount=0，不同口径会直接改变展示和筛选结果。", "repayment_plan.payment_mode 已标记废弃，现有列表和筛选仍读取该字段；若未统一改为支付明细口径，会造成页面、总数、汇总和导出不一致。", "支付明细同时存在 settlementTime、accountTime、operateTime、冲正及取消标记；最后一次有效行为的排序和排除规则尚未确认。", "【用户账单】为公共页面和公共接口；若不增加申城项目范围控制，改动可能影响其他项目。", "历史账单可能缺少完整支付明细；若要求全量回溯，可能需要数据核验或补数，当前仓库没有生产数据完整性证据。", "按最后一次支付明细进行分页筛选和最高 50 万行异步导出可能增加 SQL 开销；仓库未提供 repayment_plan_pay_detail 原表索引 DDL，需要在实施时核验索引和执行计划。" ],
  "questions" : [ "“已结清”的技术判定应采用 customerSettlementStatus=SETTLED、settlementStatus=SETTLED，还是 unpaidAmount=0？", "功能只对申城项目 XXT-PRJ-SC-A（产品 PRD-SC-A001、PRD-SC-A002）生效，还是公共【用户账单】中的所有项目都生效？", "“最后一次还款行为”按 settlementTime、accountTime 还是 operateTime 排序；时间相同时用哪个字段作为最终顺序？", "冲正、撤销、已取消或逻辑删除的支付明细是否全部排除；冲正后是否回退到上一条有效支付明细？", "需求是否要求回溯全部历史账单，还是仅对上线后结清的账单生效？", "已结清但找不到有效支付明细时，支付方式应保持空白，还是展示异常标识并进入数据修复？" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scs-saps 增加用户账单支付方式判定筛选及导出能力",
    "description" : "复用现有 PaymentModeEnum、RepaymentPlanQueryDTO.paymentMode 和 RepaymentPlanVo.paymentMode/paymentModeCn，不再依赖已废弃的 repayment_plan.payment_mode；从 repayment_plan_pay_detail 选取最后一次有效结清行为，并统一应用于列表、筛选、计数、金额汇总和异步导出。当前判断不需要新增定时任务；是否需要补数或索引 DDL 取决于历史范围及数据库核验结果。",
    "requirementChangePoint" : "支付方式判定、线上/线下筛选及导出字段",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "用户账单查询接口", "用户账单分页计数与金额汇总", "支付明细查询规则", "用户账单异步导出", "Excel 导出模板及回归测试" ],
    "changePoints" : [ "1. 明确并实现“已结清”和“最后一次有效支付明细”规则，统一过滤逻辑删除、取消及冲正记录，并按确认后的时间字段和稳定次序选取唯一支付方式。", "2. 调整用户账单查询 SQL，以 repayment_plan_pay_detail.payment_mode 返回支付方式；非已结清账单强制返回空值，并按确认结果限制申城项目或公共项目范围。", "3. 将 paymentMode 筛选条件同步应用于分页列表、queryListCount 及 BillFeeDetailMapper 金额汇总查询，避免列表、总数和合计数据口径不一致。", "4. 保持现有查询与导出接口契约，复用 10=线上、20=线下枚举；补充空支付明细、非法枚举值等防御性处理。", "5. 在异步导出数据中生成 paymentModeCn，并更新 customer_bill_user.xlsx 的“支付方式”表头和占位符；同步扩展模板回归测试。", "6. 验证线上扣款、线下还款、退费、代偿、回购、非结清、多次还款、冲正/撤销及明细缺失场景；核对页面结果、总数、汇总和导出一致性，并检查大数据量查询执行计划。" ],
    "systemTags" : [ "scs-saps" ],
    "evidenceRefs" : [ "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/附件/487-申城资管平台—用户账单增加“支付方式”字段20261102.pdf", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/RepaymentPlanEntity.java:53-59", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/RepaymentPlanPayDetailEntity.java:57-103", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/PaymentModeEnum.java:10-39", "scs-saps/saps-model/src/main/java/com/amp/saps/model/dto/management/repaymentplan/RepaymentPlanQueryDTO.java:65-73", "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/RepaymentPlanVo.java:228-250", "scs-saps/saps-dao/src/main/resources/mapper/RepaymentPlanMapper.xml:47-48", "scs-saps/saps-dao/src/main/resources/mapper/RepaymentPlanMapper.xml:713-724", "scs-saps/saps-dao/src/main/resources/mapper/RepaymentPlanMapper.xml:865-870", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/manage/bill/RepaymentPlanManageController.java:148-195", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/manage/bill/RepaymentPlanManageController.java:287-342", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/manage/bill/RepaymentPlanPayDetailController.java:48-94", "scs-saps/saps-common/src/main/java/com/amp/saps/common/constants/ScCtFactoringConstants.java:15-28", "scs-saps/saps-controller/src/main/resources/excel/customer_bill_user.xlsx", "scs-saps/saps-controller/src/test/java/com/amp/saps/controller/manage/bill/RepaymentPlanExportTemplateTest.java:32-82" ],
    "confidence" : "MEDIUM",
    "moduleName" : "用户账单查询与导出",
    "relatedFiles" : [ "scs-saps/saps-model/src/main/java/com/amp/saps/model/dto/management/repaymentplan/RepaymentPlanQueryDTO.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/RepaymentPlanVo.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/RepaymentPlanEntity.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/RepaymentPlanPayDetailEntity.java", "scs-saps/saps-dao/src/main/resources/mapper/RepaymentPlanMapper.xml", "scs-saps/saps-dao/src/main/resources/mapper/BillFeeDetailMapper.xml", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/manage/bill/RepaymentPlanManageController.java", "scs-saps/saps-controller/src/main/resources/excel/customer_bill_user.xlsx", "scs-saps/saps-controller/src/test/java/com/amp/saps/controller/manage/bill/RepaymentPlanExportTemplateTest.java" ],
    "estimatedEffort" : "16h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "实施前需确认已结清口径、项目范围、最后行为排序、异常明细处理及历史回溯范围。",
    "risk" : "核心代码位置明确，但六项业务口径尚未确认；若要求历史补数或新增索引，工时需另行评估。"
  }, {
    "title" : "scs-admin 增加用户账单支付方式展示与筛选能力",
    "description" : "在现有【用户账单】公共页面启用已预留的支付方式筛选和列表列，复用 userbills_payMode 字典及现有查询、导出权限，不新增页面或权限点。",
    "requirementChangePoint" : "用户账单列表展示支付方式并支持线上、线下筛选",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "用户账单查询表单", "用户账单数据列表", "导出条件传递", "页面验收" ],
    "changePoints" : [ "1. 启用【用户账单】查询表单中已预留的“支付方式”下拉框，复用 userbills_payMode 字典的线上、线下选项。", "2. 在账单列表增加“支付方式”列，绑定 paymentMode 字段；后端返回空值时保持空白，不自行推断状态。", "3. 确认查询、重置、分页和批量查询场景正确传递或清理 paymentMode 条件。", "4. 复用现有 userBills:export 权限和 downloadUser 接口，将当前 paymentMode 筛选条件原样传入异步导出，不新增菜单或权限配置。", "5. 联调验证非结清为空、线上/线下显示与筛选、无结果提示以及导出记录跳转。" ],
    "systemTags" : [ "scs-admin" ],
    "evidenceRefs" : [ "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/附件/487-申城资管平台—用户账单增加“支付方式”字段20261102.pdf", "scs-admin/src/views/userBills/index.vue:88-97", "scs-admin/src/views/userBills/index.vue:351-360", "scs-admin/src/views/userBills/index.vue:482-483", "scs-admin/src/views/userBills/index.vue:561-588", "scs-admin/src/api/api_list.js:136-144" ],
    "confidence" : "MEDIUM",
    "moduleName" : "用户账单页面",
    "relatedFiles" : [ "scs-admin/src/views/userBills/index.vue", "scs-admin/src/api/userBills/index.js", "scs-admin/src/api/api_list.js" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scs-saps 确认并输出统一的 paymentMode 字段语义；页面适用范围需与后端保持一致。",
    "risk" : "页面本身已有预留代码，改造较小；主要风险来自公共页面适用范围及后端业务口径未确认。"
  } ],
  "totalEstimatedEffort" : "20h",
  "developmentEstimatedEffort" : "20h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-05T11:07:30.376690",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 生成研发评估 · 需求 270

<!-- workhub-analysis:dab8d3fe956be056653c2400f96995816740bce14ed253c34f810f60acf07f36 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：107
- 记录时间：2026-11-05T11:09:20.011131
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/历史版本/0da047d3-430c-4dcd-afb0-c06910f4d372/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "保费分期",
  "projectId" : 21,
  "projectName" : "聚保融保费分期项目",
  "repositoryUrl" : "scf (13 repositories)",
  "requirementRawPath" : "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/历史版本/0da047d3-430c-4dcd-afb0-c06910f4d372/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求可复用现有保费补缴、线下入金识别、备付金账户和交行代付链路，预计涉及 scf-order、scf-saps、scf-gateway、scf-intf-common、insurance_admin，合计约 54h。未发现 scf-intf-zhonghua 承载聚保融补缴接口的代码证据，故不纳入改造范围。",
  "requirementChangePoints" : [ "备付金余额明细查询响应新增“订单号”：充值、对账单扣款返回空；首笔款扣款、首笔款退款及保费补缴返回客户订单号。", "新增聚保融保费补缴申请接口，接收渠道申请单号、客户订单号、补缴金额（分）、原因、打款保司摘要、补费方式和申请文件 fileKey。", "补缴申请必须先校验客户订单状态，再按补费方式处理；订单非放款成功时拒绝，备付金不足时拒绝并记录明确原因。", "支持“备付金补费”和“另行付款”两种入账方式：前者即时扣减渠道备付金，后者按补费业务单号和等额金额识别或人工确认入账。", "补缴款入账后自动调用交行银企直联向原订单保险公司账户打款，维护入账、打款状态、打款时间和摘要，且不得修改原订单金额、合同、账单及融资数据。", "新增保费补缴结果查询和异步结果通知，返回申请单号、补费业务单号、入账状态、打款状态、打款时间及摘要，并具备幂等和失败补偿能力。", "保费补缴记录增加审核结果、拒绝原因、补费方式和聚保融申请单号；自营存量及新增记录默认审核通过、补费方式为另行付款。", "后台保费补缴页面及导出增加审核结果、补费方式字段，并增加对应下拉检索项。", "文件上传能力增加“保费补缴申请文件”类型，并在申请接口中校验 fileKey 的存在性及文件类型。" ],
  "impactedModules" : [ "scf-order", "scf-saps", "scf-gateway", "scf-intf-common", "insurance_admin" ],
  "risks" : [ "备付金扣减、补缴记录创建和入账回调跨系统执行，若缺少业务单号唯一约束、账户行锁或补偿机制，可能发生重复扣款、重复入账或账实不一致。", "现有保费补缴打款通过 CompletableFuture 异步触发，缺少可靠消息或明确的持久化调度证据；进程重启和调用超时可能导致已入账但未向保司打款。", "现有 application_premium_supplement 表没有渠道申请单号、审核结果、拒绝原因和补费方式，新增字段需兼容并回填自营存量记录。", "现有备付金交易枚举仅覆盖充值、对账单扣款、首笔款扣款和首笔款退款，补缴交易类型及客户订单号映射需同步所有查询、导出和对账使用方。", "结果通知地址、应答协议和补偿策略未在材料中给出；若仅依赖网关同步通知，可能出现打款成功但聚保融长期未获知结果。", "“放款成功”若严格限定 ApplicationStatus.LOAN_SUCCESS，订单进入 INSURANCE_NORMAL 等后续状态后将不能补费，需在开发前确认业务状态口径。", "文件类型编码及文件规格未明确，无法直接确定 oss.support.file-types 的配置值和验收数据。" ],
  "questions" : [ "审核拒绝后重新申请时，聚保融是否会复用原“申请单号”？若复用，接口幂等规则应允许拒绝记录生成新补费业务单；若不复用，则应强制申请单号全局唯一。", "“客户订单状态=放款成功”是仅允许 ApplicationStatus.LOAN_SUCCESS，还是也允许已放款后的 INSURANCE_NORMAL 等状态？", "请明确结果通知地址配置键、请求签名方式、成功应答格式、超时重试次数和最终失败后的人工补偿入口。", "“保费补缴申请文件”的 serviceType 编码、允许的文件后缀、大小及数量限制是什么？", "向保司打款失败、超时或退票时，是持续自动重试、转人工处理，还是需要向聚保融通知失败结果？当前需求返回字段只有已打款/未打款。" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scf-order 新增聚保融保费补缴申请与结果查询能力",
    "description" : "扩展现有保费补缴业务单，承接聚保融申请、审核结果落库和主动结果查询；复用原订单渠道、运营商、保险公司和收款账户信息，但不更新原订单金额、合同、账单或融资数据。",
    "requirementChangePoint" : "新增聚保融保费补缴申请、审核和结果查询接口。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "application_premium_supplement", "聚保融保费补缴申请接口", "聚保融保费补缴结果查询接口", "保费补缴查询及导出模型" ],
    "changePoints" : [ "1. 追加 DDL/DML：增加渠道申请单号、审核结果、拒绝原因、补费方式、申请来源等字段及必要唯一索引；将自营存量记录回填为审核通过、另行付款。", "2. 新增聚保融申请和查询契约，完成金额分转元、枚举、必填项、fileKey、渠道归属及客户订单存在性校验。", "3. 按规定顺序校验订单状态和补费方式；无论通过或拒绝均生成可追溯记录并返回补费业务单号、审核结果及拒绝原因。", "4. 根据客户订单补全渠道、申请时间、运营商、保险公司和打款摘要；聚保融传入摘要优先，未传时保持为空。", "5. 以渠道机构与申请单号建立幂等边界，防止重复请求生成多笔有效补缴单，同时落实审核拒绝后重新申请规则。", "6. 增加结果查询权限隔离，只允许调用机构查询自身补缴记录，并返回入账、打款状态及打款信息。", "7. 增加接口、状态校验、金额转换、重复申请及“不修改原订单”回归测试。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/po/PremiumSupplement.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/enums/ApplicationStatus.java", "scf-order/dbscript/DDL/V11__202510_scf_selfoperation_20251016.ddl.sql", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order",
    "relatedFiles" : [ "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/po/PremiumSupplement.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementDTO.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/dbscript/DDL/V11__202510_scf_selfoperation_20251016.ddl.sql" ],
    "estimatedEffort" : "16h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖“scf-intf-common 增加保费补缴申请文件上传类型”明确文件类型配置。",
    "risk" : "拒绝后重新申请的申请单号规则和可补费订单状态范围尚未确认，可能影响唯一索引与审核逻辑。"
  }, {
    "title" : "scf-saps 扩展保费补缴双方式入账与回调规则",
    "description" : "为补缴业务提供备付金即时扣款和另行付款识别入账能力，入账成功后可靠通知 scf-order 继续向保司打款。",
    "requirementChangePoint" : "支持备付金补费、另行付款两种入账方式及入账状态更新。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "备付金扣款接口", "account", "account_balance_record", "customer_order", "pend_transaction", "保费补缴入账回调" ],
    "changePoints" : [ "1. 新增保费补缴备付金扣款服务契约，以补费业务单号作为幂等键，并校验渠道账户归属、启用状态和可用余额。", "2. 在同一事务内锁定备付金账户、扣减余额并写入补缴交易流水；余额不足时不扣款，返回可映射为“备付金余额不足”的业务结果。", "3. 为另行付款复用现有 PREMIUM 线下订单及挂账匹配逻辑，严格按补费业务单号、渠道和等额金额匹配，避免仅按金额误匹配。", "4. 兼容系统识别入账和人工手动入账，两条路径均统一推进补缴入账状态，且重复清分不重复结算。", "5. 入账事务提交后调用 scf-order 的 positing/completed 能力；失败时落持久化补偿记录并由稳定调度重试。", "6. 覆盖并发扣款、余额不足、重复请求、重复银行流水、人工与自动入账竞争及回调失败补偿测试。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountService.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendAutoClearBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendClearingBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/strategy/payment/result/PremiumPaymentResultProcesser.java", "scf-saps/saps-api/src/main/java/com/amp/saps/api/service/OrderApiService.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-saps",
    "relatedFiles" : [ "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountService.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendAutoClearBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendClearingBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/strategy/payment/result/PremiumPaymentResultProcesser.java", "scf-saps/saps-api/src/main/java/com/amp/saps/api/service/OrderApiService.java" ],
    "estimatedEffort" : "16h",
    "ownerUserName" : "hanmeng",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scf-order 先生成补费业务单并提供幂等入账回调接口。",
    "risk" : "资金扣减与跨系统回调无法形成单库事务，必须通过业务幂等和持久化补偿保证最终一致。"
  }, {
    "title" : "scf-order/scf-gateway 串联保费补缴打款保司与结果通知能力",
    "description" : "补缴款入账后复用现有交行代付链路向原订单保险公司打款，并在成功后通过统一网关主动通知聚保融。",
    "requirementChangePoint" : "补缴入账后自动向保司打款并异步通知聚保融结果。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "交行单笔代付", "保费补缴打款状态回调", "聚保融结果通知", "api_notify", "org_external_access.notify_url" ],
    "changePoints" : [ "1. 入账成功后幂等触发现有 generateToUnderwriterPaymentSlip，按原订单保司收款账户、补缴金额和渠道摘要生成独立代付单。", "2. 以补费业务单号控制打款幂等，持久化代付请求号、未打款/已打款状态、完成时间和摘要，禁止写回原订单金额科目。", "3. 处理同步异常、异步成功、失败、超时和退票状态；用持久化任务替代仅依赖进程内异步执行，并提供人工重试边界。", "4. 新增补缴结果通知业务类型及聚保融通知地址配置，通知载荷与主动查询返回字段保持一致。", "5. 复用 api_notify 或新增等价通知记录，记录通知中、成功、失败和重试次数；聚保融重复接收时以补费业务单号幂等。", "6. 验证入账成功仅打款一次、回调重复不重复通知、通知失败补偿及打款失败/退票处置。" ],
    "systemTags" : [ "scf-order", "scf-gateway" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/GatewayFeignClient.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/loan/impl/ApiNotifyServiceImpl.java", "scf-gateway/src/main/java/com/amp/gateway/controller/NotifyPartnerController.java", "scf-gateway/src/main/java/com/amp/gateway/enums/NotifyBusinessType.java" ],
    "confidence" : "MEDIUM",
    "moduleName" : "scf-order/scf-gateway",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/GatewayFeignClient.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/loan/ApiNotify.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/loan/impl/ApiNotifyServiceImpl.java", "scf-gateway/src/main/java/com/amp/gateway/controller/NotifyPartnerController.java", "scf-gateway/src/main/java/com/amp/gateway/enums/NotifyBusinessType.java" ],
    "estimatedEffort" : "10h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖补缴申请记录和两种入账方式完成；联调前需取得聚保融通知协议及地址配置。",
    "risk" : "通知协议、重试规则和打款失败/退票终态未明确，当前可靠性方案只能按现有网关及 api_notify 能力估算。"
  }, {
    "title" : "scf-saps 补充备付金明细订单号与补缴交易类型",
    "description" : "扩展既有备付金余额明细接口，按交易类型返回客户订单号，并将保费补缴纳入明细查询。",
    "requirementChangePoint" : "备付金余额明细接口新增订单号返回字段及保费补缴交易类型。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/account/balance/record/query", "AccountTransactionTypeEnum", "account_balance_record.order_no" ],
    "changePoints" : [ "1. 扩展 AccountTransactionTypeEnum 和接口字段说明，增加保费补缴交易类型。", "2. 响应模型增加 orderNo，并在查询组装中读取 account_balance_record.order_no。", "3. 充值、对账单扣款返回空订单号；首笔款扣款、首笔款退款和保费补缴写入并返回客户订单号。", "4. 保持 sourceOrderNo 现有语义：补缴场景返回补费业务单号，其他交易类型保持原关联业务单号。", "5. 同步管理端查询/导出模型中受交易类型枚举影响的展示，并验证历史数据空值兼容。", "6. 增加五类交易明细映射、分页过滤和金额单位转换测试。" ],
    "systemTags" : [ "scf-saps" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/AccountApiController.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountApiService.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiResponse.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/entity/AccountBalanceRecordEntity.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/enums/AccountTransactionTypeEnum.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-saps",
    "relatedFiles" : [ "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountApiService.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiRequest.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiResponse.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/entity/AccountBalanceRecordEntity.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/enums/AccountTransactionTypeEnum.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "保费补缴交易流水由“双方式入账与回调规则”任务写入。",
    "risk" : "首笔款退款现有流水是否均已保存客户订单号需做数据抽样；历史记录可能只能返回空值。"
  }, {
    "title" : "insurance_admin 增加保费补缴审核结果与补费方式检索展示",
    "description" : "在现有运营后台保费补缴页面和导出中展示审核结果、补费方式，并提供对应筛选项。",
    "requirementChangePoint" : "后台保费补缴记录增加审核结果、补费方式表头及检索项。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "保费补缴列表页", "保费补缴查询参数", "保费补缴导出模板", "审核结果与补费方式字典" ],
    "changePoints" : [ "1. 在检索区增加审核结果和补费方式下拉框，枚举分别为通过/拒绝、备付金补费/另行付款。", "2. 在表格增加审核结果、补费方式列，并兼容存量记录默认值。", "3. 后端分页查询增加 auditResult、paymentMethod 条件并同步 Mapper SQL。", "4. 更新 operator、channel、funder 三套保费补缴导出模板及填充模型，确保筛选条件与列表一致。", "5. 复核现有页面、导出和下载权限；本需求无新增操作按钮时不扩展写权限。", "6. 验证聚保融通过/拒绝记录、自营默认记录、组合筛选和导出字段。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "insurance_admin/src/views/order/premiumBackPayment/index.vue", "insurance_admin/src/api/api_list.js", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_operator.xlsx" ],
    "confidence" : "HIGH",
    "moduleName" : "insurance_admin",
    "relatedFiles" : [ "insurance_admin/src/views/order/premiumBackPayment/index.vue", "insurance_admin/src/api/api_list.js", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_operator.xlsx", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_channel.xlsx", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_funder.xlsx" ],
    "estimatedEffort" : "6h",
    "ownerUserName" : "cairui",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scf-order 完成审核结果、补费方式字段及查询条件。",
    "risk" : "三套导出模板均需同步，遗漏任一角色模板会导致页面有字段而导出缺列。"
  }, {
    "title" : "scf-intf-common 增加保费补缴申请文件上传类型",
    "description" : "为聚保融通过既有文件上传接口取得补缴申请文件 fileKey 提供类型白名单，并支持 scf-order 回查文件信息。",
    "requirementChangePoint" : "文件类型新增“保费补缴申请文件”。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/file/fileUpload", "oss.support.file-types", "文件详情查询接口" ],
    "changePoints" : [ "1. 在各目标环境 oss.support.file-types 中新增保费补缴申请文件 serviceType 映射。", "2. 明确并配置允许的后缀、大小和数量规则，保持上传返回 fileKey 的既有契约。", "3. 在 scf-order 申请处理时通过文件详情接口校验 fileKey 存在、归属和文件类型，并解析后台下载所需 URL。", "4. 验证未知类型拒绝、合法文件上传、伪造/失效 fileKey 拒绝及后台下载。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/oss/OutwardController.java", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/config/FileTypeProperties.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java", "scf_channel_admin/src/views/order/components/PremiumSupplement.vue" ],
    "confidence" : "MEDIUM",
    "moduleName" : "scf-intf-common",
    "relatedFiles" : [ "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/oss/OutwardController.java", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/config/FileTypeProperties.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "需产品或接口方先明确 serviceType 编码和文件规格。",
    "risk" : "类型白名单位于外部配置中心，仓库内无当前环境配置值，发布时可能漏配某一环境。"
  } ],
  "totalEstimatedEffort" : "54h",
  "developmentEstimatedEffort" : "54h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-05T11:09:19.945294",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 269

<!-- workhub-analysis:b09168cbe1e4d79740c7c34b857a1cc8a7f6af5442a910340878098eb708d377 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：108
- 记录时间：2026-11-06T13:55:13.612653
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：改造 scs-saps 与 scs-admin，共拆分 2 个研发任务，预估 20h。现有支付方式枚举、查询字段和前端字典可复用，但账单表 paymentMode 已废弃，需以支付明细表的最后一次有效结清行为统一支撑列表、筛选、汇总及导出。本次未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "【用户账单】列表新增“支付方式”列，仅已结清账单展示，其他状态为空。", "支付方式按账单最后一次有效结清行为判定：线上扣款清分显示“线上”，线下还款、退费、代偿、回购等显示“线下”。", "【用户账单】查询条件新增“支付方式”，选项为线上、线下，并保证列表、总数和金额汇总采用一致口径。", "【用户账单】-【导出明细】新增“支付方式”字段，字段值及筛选结果与页面一致。", "用户账单列表展示支付方式并支持线上、线下筛选" ],
  "impactedModules" : [ "scs-admin：用户账单列表、查询条件及导出入口", "scs-saps：用户账单查询接口、支付方式判定、分页计数与金额汇总", "scs-saps：用户账单异步导出数据及 Excel 模板" ],
  "risks" : [ "“已结清”可能对应 customerSettlementStatus、settlementStatus 或 unpaidAmount=0，不同口径会直接改变展示和筛选结果。", "repayment_plan.payment_mode 已标记废弃，现有列表和筛选仍读取该字段；若未统一改为支付明细口径，会造成页面、总数、汇总和导出不一致。", "支付明细同时存在 settlementTime、accountTime、operateTime、冲正及取消标记；最后一次有效行为的排序和排除规则尚未确认。", "【用户账单】为公共页面和公共接口；若不增加申城项目范围控制，改动可能影响其他项目。", "历史账单可能缺少完整支付明细；若要求全量回溯，可能需要数据核验或补数，当前仓库没有生产数据完整性证据。", "按最后一次支付明细进行分页筛选和最高 50 万行异步导出可能增加 SQL 开销；仓库未提供 repayment_plan_pay_detail 原表索引 DDL，需要在实施时核验索引和执行计划。" ],
  "questions" : [ "“已结清”的技术判定应采用 customerSettlementStatus=SETTLED、settlementStatus=SETTLED，还是 unpaidAmount=0？", "功能只对申城项目 XXT-PRJ-SC-A（产品 PRD-SC-A001、PRD-SC-A002）生效，还是公共【用户账单】中的所有项目都生效？", "“最后一次还款行为”按 settlementTime、accountTime 还是 operateTime 排序；时间相同时用哪个字段作为最终顺序？", "冲正、撤销、已取消或逻辑删除的支付明细是否全部排除；冲正后是否回退到上一条有效支付明细？", "需求是否要求回溯全部历史账单，还是仅对上线后结清的账单生效？", "已结清但找不到有效支付明细时，支付方式应保持空白，还是展示异常标识并进入数据修复？" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scs-saps 增加用户账单支付方式判定筛选及导出能力",
    "description" : "在现有【用户账单】公共页面启用已预留的支付方式筛选和列表列，复用 userbills_payMode 字典及现有查询、导出权限，不新增页面或权限点。",
    "requirementChangePoint" : "用户账单列表展示支付方式并支持线上、线下筛选",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "用户账单查询表单", "用户账单数据列表", "导出条件传递", "页面验收" ],
    "changePoints" : [ "1. 启用【用户账单】查询表单中已预留的“支付方式”下拉框，复用 userbills_payMode 字典的线上、线下选项。", "2. 在账单列表增加“支付方式”列，绑定 paymentMode 字段；后端返回空值时保持空白，不自行推断状态。", "3. 确认查询、重置、分页和批量查询场景正确传递或清理 paymentMode 条件。", "4. 复用现有 userBills:export 权限和 downloadUser 接口，将当前 paymentMode 筛选条件原样传入异步导出，不新增菜单或权限配置。", "5. 联调验证非结清为空、线上/线下显示与筛选、无结果提示以及导出记录跳转。" ],
    "systemTags" : [ "scs-admin" ],
    "evidenceRefs" : [ "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/附件/487-申城资管平台—用户账单增加“支付方式”字段20261102.pdf", "scs-admin/src/views/userBills/index.vue:88-97", "scs-admin/src/views/userBills/index.vue:351-360", "scs-admin/src/views/userBills/index.vue:482-483", "scs-admin/src/views/userBills/index.vue:561-588", "scs-admin/src/api/api_list.js:136-144" ],
    "confidence" : "MEDIUM",
    "moduleName" : "用户账单页面",
    "relatedFiles" : [ "scs-admin/src/views/userBills/index.vue", "scs-admin/src/api/userBills/index.js", "scs-admin/src/api/api_list.js" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scs-saps 确认并输出统一的 paymentMode 字段语义；页面适用范围需与后端保持一致。",
    "risk" : "页面本身已有预留代码，改造较小；主要风险来自公共页面适用范围及后端业务口径未确认。"
  } ],
  "totalEstimatedEffort" : "8h",
  "developmentEstimatedEffort" : "4h",
  "testingEstimatedEffort" : "4h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-05T11:07:30.376690",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 269

<!-- workhub-analysis:59b4788a443d0dbfd211a2018c84cf5f3de822239ec013e826868b073d35afee -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：108
- 记录时间：2026-11-10T13:21:55.161660
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：改造 scs-saps 与 scs-admin，共拆分 2 个研发任务，预估 20h。现有支付方式枚举、查询字段和前端字典可复用，但账单表 paymentMode 已废弃，需以支付明细表的最后一次有效结清行为统一支撑列表、筛选、汇总及导出。本次未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "【用户账单】列表新增“支付方式”列，仅已结清账单展示，其他状态为空。", "支付方式按账单最后一次有效结清行为判定：线上扣款清分显示“线上”，线下还款、退费、代偿、回购等显示“线下”。", "【用户账单】查询条件新增“支付方式”，选项为线上、线下，并保证列表、总数和金额汇总采用一致口径。", "【用户账单】-【导出明细】新增“支付方式”字段，字段值及筛选结果与页面一致。", "用户账单列表展示支付方式并支持线上、线下筛选" ],
  "impactedModules" : [ "scs-admin：用户账单列表、查询条件及导出入口", "scs-saps：用户账单查询接口、支付方式判定、分页计数与金额汇总", "scs-saps：用户账单异步导出数据及 Excel 模板" ],
  "risks" : [ "“已结清”可能对应 customerSettlementStatus、settlementStatus 或 unpaidAmount=0，不同口径会直接改变展示和筛选结果。", "repayment_plan.payment_mode 已标记废弃，现有列表和筛选仍读取该字段；若未统一改为支付明细口径，会造成页面、总数、汇总和导出不一致。", "支付明细同时存在 settlementTime、accountTime、operateTime、冲正及取消标记；最后一次有效行为的排序和排除规则尚未确认。", "【用户账单】为公共页面和公共接口；若不增加申城项目范围控制，改动可能影响其他项目。", "历史账单可能缺少完整支付明细；若要求全量回溯，可能需要数据核验或补数，当前仓库没有生产数据完整性证据。", "按最后一次支付明细进行分页筛选和最高 50 万行异步导出可能增加 SQL 开销；仓库未提供 repayment_plan_pay_detail 原表索引 DDL，需要在实施时核验索引和执行计划。" ],
  "questions" : [ "“已结清”的技术判定应采用 customerSettlementStatus=SETTLED、settlementStatus=SETTLED，还是 unpaidAmount=0？", "功能只对申城项目 XXT-PRJ-SC-A（产品 PRD-SC-A001、PRD-SC-A002）生效，还是公共【用户账单】中的所有项目都生效？", "“最后一次还款行为”按 settlementTime、accountTime 还是 operateTime 排序；时间相同时用哪个字段作为最终顺序？", "冲正、撤销、已取消或逻辑删除的支付明细是否全部排除；冲正后是否回退到上一条有效支付明细？", "需求是否要求回溯全部历史账单，还是仅对上线后结清的账单生效？", "已结清但找不到有效支付明细时，支付方式应保持空白，还是展示异常标识并进入数据修复？" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scs-saps 增加用户账单支付方式判定筛选及导出能力",
    "description" : "在现有【用户账单】公共页面启用已预留的支付方式筛选和列表列，复用 userbills_payMode 字典及现有查询、导出权限，不新增页面或权限点。",
    "requirementChangePoint" : "用户账单列表展示支付方式并支持线上、线下筛选",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "用户账单查询表单", "用户账单数据列表", "导出条件传递", "页面验收" ],
    "changePoints" : [ "1. 启用【用户账单】查询表单中已预留的“支付方式”下拉框，复用 userbills_payMode 字典的线上、线下选项。", "2. 在账单列表增加“支付方式”列，绑定 paymentMode 字段；后端返回空值时保持空白，不自行推断状态。", "3. 确认查询、重置、分页和批量查询场景正确传递或清理 paymentMode 条件。", "4. 复用现有 userBills:export 权限和 downloadUser 接口，将当前 paymentMode 筛选条件原样传入异步导出，不新增菜单或权限配置。", "5. 联调验证非结清为空、线上/线下显示与筛选、无结果提示以及导出记录跳转。" ],
    "systemTags" : [ "scs-admin", "scs-saps" ],
    "evidenceRefs" : [ "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611020006/历史版本/4fddc489-17cc-4e58-9ad6-e4dac11b7bdc/附件/487-申城资管平台—用户账单增加“支付方式”字段20261102.pdf", "scs-admin/src/views/userBills/index.vue:88-97", "scs-admin/src/views/userBills/index.vue:351-360", "scs-admin/src/views/userBills/index.vue:482-483", "scs-admin/src/views/userBills/index.vue:561-588", "scs-admin/src/api/api_list.js:136-144" ],
    "confidence" : "MEDIUM",
    "moduleName" : "用户账单页面",
    "relatedFiles" : [ "scs-admin/src/views/userBills/index.vue", "scs-admin/src/api/userBills/index.js", "scs-admin/src/api/api_list.js" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scs-saps 确认并输出统一的 paymentMode 字段语义；页面适用范围需与后端保持一致。",
    "risk" : "页面本身已有预留代码，改造较小；主要风险来自公共页面适用范围及后端业务口径未确认。"
  } ],
  "totalEstimatedEffort" : "8h",
  "developmentEstimatedEffort" : "4h",
  "testingEstimatedEffort" : "4h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-05T11:07:30.376690",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 270

<!-- workhub-analysis:770cfe7935f756813ae3ebb4a79045de433c88080a7e9e4f3bc333a63073252b -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：107
- 记录时间：2026-11-10T13:23:57.717509
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/历史版本/0da047d3-430c-4dcd-afb0-c06910f4d372/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "保费分期",
  "projectId" : 21,
  "projectName" : "聚保融保费分期项目",
  "repositoryUrl" : "scf (13 repositories)",
  "requirementRawPath" : "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/历史版本/0da047d3-430c-4dcd-afb0-c06910f4d372/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求可复用现有保费补缴、线下入金识别、备付金账户和交行代付链路，预计涉及 scf-order、scf-saps、scf-gateway、scf-intf-common、insurance_admin，合计约 54h。未发现 scf-intf-zhonghua 承载聚保融补缴接口的代码证据，故不纳入改造范围。",
  "requirementChangePoints" : [ "备付金余额明细查询响应新增“订单号”：充值、对账单扣款返回空；首笔款扣款、首笔款退款及保费补缴返回客户订单号。", "新增聚保融保费补缴申请接口，接收渠道申请单号、客户订单号、补缴金额（分）、原因、打款保司摘要、补费方式和申请文件 fileKey。", "补缴申请必须先校验客户订单状态，再按补费方式处理；订单非放款成功时拒绝，备付金不足时拒绝并记录明确原因。", "支持“备付金补费”和“另行付款”两种入账方式：前者即时扣减渠道备付金，后者按补费业务单号和等额金额识别或人工确认入账。", "补缴款入账后自动调用交行银企直联向原订单保险公司账户打款，维护入账、打款状态、打款时间和摘要，且不得修改原订单金额、合同、账单及融资数据。", "新增保费补缴结果查询和异步结果通知，返回申请单号、补费业务单号、入账状态、打款状态、打款时间及摘要，并具备幂等和失败补偿能力。", "保费补缴记录增加审核结果、拒绝原因、补费方式和聚保融申请单号；自营存量及新增记录默认审核通过、补费方式为另行付款。", "后台保费补缴页面及导出增加审核结果、补费方式字段，并增加对应下拉检索项。", "文件上传能力增加“保费补缴申请文件”类型，并在申请接口中校验 fileKey 的存在性及文件类型。", "新增聚保融保费补缴申请、审核和结果查询接口。", "支持备付金补费、另行付款两种入账方式及入账状态更新。", "补缴入账后自动向保司打款并异步通知聚保融结果。", "备付金余额明细接口新增订单号返回字段及保费补缴交易类型。", "后台保费补缴记录增加审核结果、补费方式表头及检索项。", "文件类型新增“保费补缴申请文件”。" ],
  "impactedModules" : [ "scf-order", "scf-saps", "scf-gateway", "scf-intf-common", "insurance_admin" ],
  "risks" : [ "备付金扣减、补缴记录创建和入账回调跨系统执行，若缺少业务单号唯一约束、账户行锁或补偿机制，可能发生重复扣款、重复入账或账实不一致。", "现有保费补缴打款通过 CompletableFuture 异步触发，缺少可靠消息或明确的持久化调度证据；进程重启和调用超时可能导致已入账但未向保司打款。", "现有 application_premium_supplement 表没有渠道申请单号、审核结果、拒绝原因和补费方式，新增字段需兼容并回填自营存量记录。", "现有备付金交易枚举仅覆盖充值、对账单扣款、首笔款扣款和首笔款退款，补缴交易类型及客户订单号映射需同步所有查询、导出和对账使用方。", "结果通知地址、应答协议和补偿策略未在材料中给出；若仅依赖网关同步通知，可能出现打款成功但聚保融长期未获知结果。", "“放款成功”若严格限定 ApplicationStatus.LOAN_SUCCESS，订单进入 INSURANCE_NORMAL 等后续状态后将不能补费，需在开发前确认业务状态口径。", "文件类型编码及文件规格未明确，无法直接确定 oss.support.file-types 的配置值和验收数据。" ],
  "questions" : [ "审核拒绝后重新申请时，聚保融是否会复用原“申请单号”？若复用，接口幂等规则应允许拒绝记录生成新补费业务单；若不复用，则应强制申请单号全局唯一。", "“客户订单状态=放款成功”是仅允许 ApplicationStatus.LOAN_SUCCESS，还是也允许已放款后的 INSURANCE_NORMAL 等状态？", "请明确结果通知地址配置键、请求签名方式、成功应答格式、超时重试次数和最终失败后的人工补偿入口。", "“保费补缴申请文件”的 serviceType 编码、允许的文件后缀、大小及数量限制是什么？", "向保司打款失败、超时或退票时，是持续自动重试、转人工处理，还是需要向聚保融通知失败结果？当前需求返回字段只有已打款/未打款。" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scf-order 新增聚保融保费补缴申请与结果查询能力",
    "description" : "扩展现有保费补缴业务单，承接聚保融申请、审核结果落库和主动结果查询；复用原订单渠道、运营商、保险公司和收款账户信息，但不更新原订单金额、合同、账单或融资数据。",
    "requirementChangePoint" : "新增聚保融保费补缴申请、审核和结果查询接口。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "application_premium_supplement", "聚保融保费补缴申请接口", "聚保融保费补缴结果查询接口", "保费补缴查询及导出模型" ],
    "changePoints" : [ "1. 追加 DDL/DML：增加渠道申请单号、审核结果、拒绝原因、补费方式、申请来源等字段及必要唯一索引；将自营存量记录回填为审核通过、另行付款。", "2. 新增聚保融申请和查询契约，完成金额分转元、枚举、必填项、fileKey、渠道归属及客户订单存在性校验。", "3. 按规定顺序校验订单状态和补费方式；无论通过或拒绝均生成可追溯记录并返回补费业务单号、审核结果及拒绝原因。", "4. 根据客户订单补全渠道、申请时间、运营商、保险公司和打款摘要；聚保融传入摘要优先，未传时保持为空。", "5. 以渠道机构与申请单号建立幂等边界，防止重复请求生成多笔有效补缴单，同时落实审核拒绝后重新申请规则。", "6. 增加结果查询权限隔离，只允许调用机构查询自身补缴记录，并返回入账、打款状态及打款信息。", "7. 增加接口、状态校验、金额转换、重复申请及“不修改原订单”回归测试。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/po/PremiumSupplement.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/enums/ApplicationStatus.java", "scf-order/dbscript/DDL/V11__202510_scf_selfoperation_20251016.ddl.sql", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order",
    "relatedFiles" : [ "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/po/PremiumSupplement.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementDTO.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/dbscript/DDL/V11__202510_scf_selfoperation_20251016.ddl.sql" ],
    "estimatedEffort" : "16h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖“scf-intf-common 增加保费补缴申请文件上传类型”明确文件类型配置。",
    "risk" : "拒绝后重新申请的申请单号规则和可补费订单状态范围尚未确认，可能影响唯一索引与审核逻辑。"
  }, {
    "title" : "scf-saps 扩展保费补缴双方式入账与回调规则",
    "description" : "为补缴业务提供备付金即时扣款和另行付款识别入账能力，入账成功后可靠通知 scf-order 继续向保司打款。",
    "requirementChangePoint" : "支持备付金补费、另行付款两种入账方式及入账状态更新。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "备付金扣款接口", "account", "account_balance_record", "customer_order", "pend_transaction", "保费补缴入账回调" ],
    "changePoints" : [ "1. 新增保费补缴备付金扣款服务契约，以补费业务单号作为幂等键，并校验渠道账户归属、启用状态和可用余额。", "2. 在同一事务内锁定备付金账户、扣减余额并写入补缴交易流水；余额不足时不扣款，返回可映射为“备付金余额不足”的业务结果。", "3. 为另行付款复用现有 PREMIUM 线下订单及挂账匹配逻辑，严格按补费业务单号、渠道和等额金额匹配，避免仅按金额误匹配。", "4. 兼容系统识别入账和人工手动入账，两条路径均统一推进补缴入账状态，且重复清分不重复结算。", "5. 入账事务提交后调用 scf-order 的 positing/completed 能力；失败时落持久化补偿记录并由稳定调度重试。", "6. 覆盖并发扣款、余额不足、重复请求、重复银行流水、人工与自动入账竞争及回调失败补偿测试。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountService.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendAutoClearBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendClearingBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/strategy/payment/result/PremiumPaymentResultProcesser.java", "scf-saps/saps-api/src/main/java/com/amp/saps/api/service/OrderApiService.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-saps",
    "relatedFiles" : [ "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountService.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendAutoClearBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendClearingBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/strategy/payment/result/PremiumPaymentResultProcesser.java", "scf-saps/saps-api/src/main/java/com/amp/saps/api/service/OrderApiService.java" ],
    "estimatedEffort" : "16h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scf-order 先生成补费业务单并提供幂等入账回调接口。",
    "risk" : "资金扣减与跨系统回调无法形成单库事务，必须通过业务幂等和持久化补偿保证最终一致。"
  }, {
    "title" : "scf-order/scf-gateway 串联保费补缴打款保司与结果通知能力",
    "description" : "补缴款入账后复用现有交行代付链路向原订单保险公司打款，并在成功后通过统一网关主动通知聚保融。",
    "requirementChangePoint" : "补缴入账后自动向保司打款并异步通知聚保融结果。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "交行单笔代付", "保费补缴打款状态回调", "聚保融结果通知", "api_notify", "org_external_access.notify_url" ],
    "changePoints" : [ "1. 入账成功后幂等触发现有 generateToUnderwriterPaymentSlip，按原订单保司收款账户、补缴金额和渠道摘要生成独立代付单。", "2. 以补费业务单号控制打款幂等，持久化代付请求号、未打款/已打款状态、完成时间和摘要，禁止写回原订单金额科目。", "3. 处理同步异常、异步成功、失败、超时和退票状态；用持久化任务替代仅依赖进程内异步执行，并提供人工重试边界。", "4. 新增补缴结果通知业务类型及聚保融通知地址配置，通知载荷与主动查询返回字段保持一致。", "5. 复用 api_notify 或新增等价通知记录，记录通知中、成功、失败和重试次数；聚保融重复接收时以补费业务单号幂等。", "6. 验证入账成功仅打款一次、回调重复不重复通知、通知失败补偿及打款失败/退票处置。" ],
    "systemTags" : [ "scf-order", "scf-gateway" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/GatewayFeignClient.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/loan/impl/ApiNotifyServiceImpl.java", "scf-gateway/src/main/java/com/amp/gateway/controller/NotifyPartnerController.java", "scf-gateway/src/main/java/com/amp/gateway/enums/NotifyBusinessType.java" ],
    "confidence" : "MEDIUM",
    "moduleName" : "scf-order/scf-gateway",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/GatewayFeignClient.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/loan/ApiNotify.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/loan/impl/ApiNotifyServiceImpl.java", "scf-gateway/src/main/java/com/amp/gateway/controller/NotifyPartnerController.java", "scf-gateway/src/main/java/com/amp/gateway/enums/NotifyBusinessType.java" ],
    "estimatedEffort" : "10h",
    "ownerUserName" : "yanggang",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖补缴申请记录和两种入账方式完成；联调前需取得聚保融通知协议及地址配置。",
    "risk" : "通知协议、重试规则和打款失败/退票终态未明确，当前可靠性方案只能按现有网关及 api_notify 能力估算。"
  }, {
    "title" : "scf-saps 补充备付金明细订单号与补缴交易类型",
    "description" : "扩展既有备付金余额明细接口，按交易类型返回客户订单号，并将保费补缴纳入明细查询。",
    "requirementChangePoint" : "备付金余额明细接口新增订单号返回字段及保费补缴交易类型。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/account/balance/record/query", "AccountTransactionTypeEnum", "account_balance_record.order_no" ],
    "changePoints" : [ "1. 扩展 AccountTransactionTypeEnum 和接口字段说明，增加保费补缴交易类型。", "2. 响应模型增加 orderNo，并在查询组装中读取 account_balance_record.order_no。", "3. 充值、对账单扣款返回空订单号；首笔款扣款、首笔款退款和保费补缴写入并返回客户订单号。", "4. 保持 sourceOrderNo 现有语义：补缴场景返回补费业务单号，其他交易类型保持原关联业务单号。", "5. 同步管理端查询/导出模型中受交易类型枚举影响的展示，并验证历史数据空值兼容。", "6. 增加五类交易明细映射、分页过滤和金额单位转换测试。" ],
    "systemTags" : [ "scf-saps" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/AccountApiController.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountApiService.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiResponse.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/entity/AccountBalanceRecordEntity.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/enums/AccountTransactionTypeEnum.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-saps",
    "relatedFiles" : [ "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountApiService.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiRequest.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiResponse.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/entity/AccountBalanceRecordEntity.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/enums/AccountTransactionTypeEnum.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "保费补缴交易流水由“双方式入账与回调规则”任务写入。",
    "risk" : "首笔款退款现有流水是否均已保存客户订单号需做数据抽样；历史记录可能只能返回空值。"
  }, {
    "title" : "insurance_admin 增加保费补缴审核结果与补费方式检索展示",
    "description" : "在现有运营后台保费补缴页面和导出中展示审核结果、补费方式，并提供对应筛选项。",
    "requirementChangePoint" : "后台保费补缴记录增加审核结果、补费方式表头及检索项。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "保费补缴列表页", "保费补缴查询参数", "保费补缴导出模板", "审核结果与补费方式字典" ],
    "changePoints" : [ "1. 在检索区增加审核结果和补费方式下拉框，枚举分别为通过/拒绝、备付金补费/另行付款。", "2. 在表格增加审核结果、补费方式列，并兼容存量记录默认值。", "3. 后端分页查询增加 auditResult、paymentMethod 条件并同步 Mapper SQL。", "4. 更新 operator、channel、funder 三套保费补缴导出模板及填充模型，确保筛选条件与列表一致。", "5. 复核现有页面、导出和下载权限；本需求无新增操作按钮时不扩展写权限。", "6. 验证聚保融通过/拒绝记录、自营默认记录、组合筛选和导出字段。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "insurance_admin/src/views/order/premiumBackPayment/index.vue", "insurance_admin/src/api/api_list.js", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_operator.xlsx" ],
    "confidence" : "HIGH",
    "moduleName" : "insurance_admin",
    "relatedFiles" : [ "insurance_admin/src/views/order/premiumBackPayment/index.vue", "insurance_admin/src/api/api_list.js", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_operator.xlsx", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_channel.xlsx", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_funder.xlsx" ],
    "estimatedEffort" : "6h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scf-order 完成审核结果、补费方式字段及查询条件。",
    "risk" : "三套导出模板均需同步，遗漏任一角色模板会导致页面有字段而导出缺列。"
  }, {
    "title" : "scf-intf-common 增加保费补缴申请文件上传类型",
    "description" : "为聚保融通过既有文件上传接口取得补缴申请文件 fileKey 提供类型白名单，并支持 scf-order 回查文件信息。",
    "requirementChangePoint" : "文件类型新增“保费补缴申请文件”。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/file/fileUpload", "oss.support.file-types", "文件详情查询接口" ],
    "changePoints" : [ "1. 在各目标环境 oss.support.file-types 中新增保费补缴申请文件 serviceType 映射。", "2. 明确并配置允许的后缀、大小和数量规则，保持上传返回 fileKey 的既有契约。", "3. 在 scf-order 申请处理时通过文件详情接口校验 fileKey 存在、归属和文件类型，并解析后台下载所需 URL。", "4. 验证未知类型拒绝、合法文件上传、伪造/失效 fileKey 拒绝及后台下载。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/oss/OutwardController.java", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/config/FileTypeProperties.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java", "scf_channel_admin/src/views/order/components/PremiumSupplement.vue" ],
    "confidence" : "MEDIUM",
    "moduleName" : "scf-intf-common",
    "relatedFiles" : [ "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/oss/OutwardController.java", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/config/FileTypeProperties.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "需产品或接口方先明确 serviceType 编码和文件规格。",
    "risk" : "类型白名单位于外部配置中心，仓库内无当前环境配置值，发布时可能漏配某一环境。"
  } ],
  "totalEstimatedEffort" : "54h",
  "developmentEstimatedEffort" : "54h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-05T11:09:19.945294",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 270

<!-- workhub-analysis:379cad4430f98c9ab96e402b306e22fedc590b808ca0fc5f4630ff8257f1994a -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：107
- 记录时间：2026-11-10T13:24:16.448226
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/历史版本/0da047d3-430c-4dcd-afb0-c06910f4d372/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "保费分期",
  "projectId" : 21,
  "projectName" : "聚保融保费分期项目",
  "repositoryUrl" : "scf (13 repositories)",
  "requirementRawPath" : "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/历史版本/0da047d3-430c-4dcd-afb0-c06910f4d372/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求可复用现有保费补缴、线下入金识别、备付金账户和交行代付链路，预计涉及 scf-order、scf-saps、scf-gateway、scf-intf-common、insurance_admin，合计约 54h。未发现 scf-intf-zhonghua 承载聚保融补缴接口的代码证据，故不纳入改造范围。",
  "requirementChangePoints" : [ "备付金余额明细查询响应新增“订单号”：充值、对账单扣款返回空；首笔款扣款、首笔款退款及保费补缴返回客户订单号。", "新增聚保融保费补缴申请接口，接收渠道申请单号、客户订单号、补缴金额（分）、原因、打款保司摘要、补费方式和申请文件 fileKey。", "补缴申请必须先校验客户订单状态，再按补费方式处理；订单非放款成功时拒绝，备付金不足时拒绝并记录明确原因。", "支持“备付金补费”和“另行付款”两种入账方式：前者即时扣减渠道备付金，后者按补费业务单号和等额金额识别或人工确认入账。", "补缴款入账后自动调用交行银企直联向原订单保险公司账户打款，维护入账、打款状态、打款时间和摘要，且不得修改原订单金额、合同、账单及融资数据。", "新增保费补缴结果查询和异步结果通知，返回申请单号、补费业务单号、入账状态、打款状态、打款时间及摘要，并具备幂等和失败补偿能力。", "保费补缴记录增加审核结果、拒绝原因、补费方式和聚保融申请单号；自营存量及新增记录默认审核通过、补费方式为另行付款。", "后台保费补缴页面及导出增加审核结果、补费方式字段，并增加对应下拉检索项。", "文件上传能力增加“保费补缴申请文件”类型，并在申请接口中校验 fileKey 的存在性及文件类型。", "新增聚保融保费补缴申请、审核和结果查询接口。", "支持备付金补费、另行付款两种入账方式及入账状态更新。", "补缴入账后自动向保司打款并异步通知聚保融结果。", "备付金余额明细接口新增订单号返回字段及保费补缴交易类型。", "后台保费补缴记录增加审核结果、补费方式表头及检索项。", "文件类型新增“保费补缴申请文件”。" ],
  "impactedModules" : [ "scf-order", "scf-saps", "scf-gateway", "scf-intf-common", "insurance_admin" ],
  "risks" : [ "备付金扣减、补缴记录创建和入账回调跨系统执行，若缺少业务单号唯一约束、账户行锁或补偿机制，可能发生重复扣款、重复入账或账实不一致。", "现有保费补缴打款通过 CompletableFuture 异步触发，缺少可靠消息或明确的持久化调度证据；进程重启和调用超时可能导致已入账但未向保司打款。", "现有 application_premium_supplement 表没有渠道申请单号、审核结果、拒绝原因和补费方式，新增字段需兼容并回填自营存量记录。", "现有备付金交易枚举仅覆盖充值、对账单扣款、首笔款扣款和首笔款退款，补缴交易类型及客户订单号映射需同步所有查询、导出和对账使用方。", "结果通知地址、应答协议和补偿策略未在材料中给出；若仅依赖网关同步通知，可能出现打款成功但聚保融长期未获知结果。", "“放款成功”若严格限定 ApplicationStatus.LOAN_SUCCESS，订单进入 INSURANCE_NORMAL 等后续状态后将不能补费，需在开发前确认业务状态口径。", "文件类型编码及文件规格未明确，无法直接确定 oss.support.file-types 的配置值和验收数据。" ],
  "questions" : [ "审核拒绝后重新申请时，聚保融是否会复用原“申请单号”？若复用，接口幂等规则应允许拒绝记录生成新补费业务单；若不复用，则应强制申请单号全局唯一。", "“客户订单状态=放款成功”是仅允许 ApplicationStatus.LOAN_SUCCESS，还是也允许已放款后的 INSURANCE_NORMAL 等状态？", "请明确结果通知地址配置键、请求签名方式、成功应答格式、超时重试次数和最终失败后的人工补偿入口。", "“保费补缴申请文件”的 serviceType 编码、允许的文件后缀、大小及数量限制是什么？", "向保司打款失败、超时或退票时，是持续自动重试、转人工处理，还是需要向聚保融通知失败结果？当前需求返回字段只有已打款/未打款。" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scf-order 新增聚保融保费补缴申请与结果查询能力",
    "description" : "扩展现有保费补缴业务单，承接聚保融申请、审核结果落库和主动结果查询；复用原订单渠道、运营商、保险公司和收款账户信息，但不更新原订单金额、合同、账单或融资数据。",
    "requirementChangePoint" : "新增聚保融保费补缴申请、审核和结果查询接口。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "application_premium_supplement", "聚保融保费补缴申请接口", "聚保融保费补缴结果查询接口", "保费补缴查询及导出模型" ],
    "changePoints" : [ "1. 追加 DDL/DML：增加渠道申请单号、审核结果、拒绝原因、补费方式、申请来源等字段及必要唯一索引；将自营存量记录回填为审核通过、另行付款。", "2. 新增聚保融申请和查询契约，完成金额分转元、枚举、必填项、fileKey、渠道归属及客户订单存在性校验。", "3. 按规定顺序校验订单状态和补费方式；无论通过或拒绝均生成可追溯记录并返回补费业务单号、审核结果及拒绝原因。", "4. 根据客户订单补全渠道、申请时间、运营商、保险公司和打款摘要；聚保融传入摘要优先，未传时保持为空。", "5. 以渠道机构与申请单号建立幂等边界，防止重复请求生成多笔有效补缴单，同时落实审核拒绝后重新申请规则。", "6. 增加结果查询权限隔离，只允许调用机构查询自身补缴记录，并返回入账、打款状态及打款信息。", "7. 增加接口、状态校验、金额转换、重复申请及“不修改原订单”回归测试。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/po/PremiumSupplement.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/enums/ApplicationStatus.java", "scf-order/dbscript/DDL/V11__202510_scf_selfoperation_20251016.ddl.sql", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order",
    "relatedFiles" : [ "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/po/PremiumSupplement.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementDTO.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/dbscript/DDL/V11__202510_scf_selfoperation_20251016.ddl.sql" ],
    "estimatedEffort" : "16h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖“scf-intf-common 增加保费补缴申请文件上传类型”明确文件类型配置。",
    "risk" : "拒绝后重新申请的申请单号规则和可补费订单状态范围尚未确认，可能影响唯一索引与审核逻辑。"
  }, {
    "title" : "scf-saps 扩展保费补缴双方式入账与回调规则",
    "description" : "为补缴业务提供备付金即时扣款和另行付款识别入账能力，入账成功后可靠通知 scf-order 继续向保司打款。",
    "requirementChangePoint" : "支持备付金补费、另行付款两种入账方式及入账状态更新。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "备付金扣款接口", "account", "account_balance_record", "customer_order", "pend_transaction", "保费补缴入账回调" ],
    "changePoints" : [ "1. 新增保费补缴备付金扣款服务契约，以补费业务单号作为幂等键，并校验渠道账户归属、启用状态和可用余额。", "2. 在同一事务内锁定备付金账户、扣减余额并写入补缴交易流水；余额不足时不扣款，返回可映射为“备付金余额不足”的业务结果。", "3. 为另行付款复用现有 PREMIUM 线下订单及挂账匹配逻辑，严格按补费业务单号、渠道和等额金额匹配，避免仅按金额误匹配。", "4. 兼容系统识别入账和人工手动入账，两条路径均统一推进补缴入账状态，且重复清分不重复结算。", "5. 入账事务提交后调用 scf-order 的 positing/completed 能力；失败时落持久化补偿记录并由稳定调度重试。", "6. 覆盖并发扣款、余额不足、重复请求、重复银行流水、人工与自动入账竞争及回调失败补偿测试。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountService.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendAutoClearBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendClearingBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/strategy/payment/result/PremiumPaymentResultProcesser.java", "scf-saps/saps-api/src/main/java/com/amp/saps/api/service/OrderApiService.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-saps",
    "relatedFiles" : [ "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountService.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendAutoClearBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendClearingBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/strategy/payment/result/PremiumPaymentResultProcesser.java", "scf-saps/saps-api/src/main/java/com/amp/saps/api/service/OrderApiService.java" ],
    "estimatedEffort" : "16h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scf-order 先生成补费业务单并提供幂等入账回调接口。",
    "risk" : "资金扣减与跨系统回调无法形成单库事务，必须通过业务幂等和持久化补偿保证最终一致。"
  }, {
    "title" : "scf-order/scf-gateway 串联保费补缴打款保司与结果通知能力",
    "description" : "补缴款入账后复用现有交行代付链路向原订单保险公司打款，并在成功后通过统一网关主动通知聚保融。",
    "requirementChangePoint" : "补缴入账后自动向保司打款并异步通知聚保融结果。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "交行单笔代付", "保费补缴打款状态回调", "聚保融结果通知", "api_notify", "org_external_access.notify_url" ],
    "changePoints" : [ "1. 入账成功后幂等触发现有 generateToUnderwriterPaymentSlip，按原订单保司收款账户、补缴金额和渠道摘要生成独立代付单。", "2. 以补费业务单号控制打款幂等，持久化代付请求号、未打款/已打款状态、完成时间和摘要，禁止写回原订单金额科目。", "3. 处理同步异常、异步成功、失败、超时和退票状态；用持久化任务替代仅依赖进程内异步执行，并提供人工重试边界。", "4. 新增补缴结果通知业务类型及聚保融通知地址配置，通知载荷与主动查询返回字段保持一致。", "5. 复用 api_notify 或新增等价通知记录，记录通知中、成功、失败和重试次数；聚保融重复接收时以补费业务单号幂等。", "6. 验证入账成功仅打款一次、回调重复不重复通知、通知失败补偿及打款失败/退票处置。" ],
    "systemTags" : [ "scf-order", "scf-gateway" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/GatewayFeignClient.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/loan/impl/ApiNotifyServiceImpl.java", "scf-gateway/src/main/java/com/amp/gateway/controller/NotifyPartnerController.java", "scf-gateway/src/main/java/com/amp/gateway/enums/NotifyBusinessType.java" ],
    "confidence" : "MEDIUM",
    "moduleName" : "scf-order/scf-gateway",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/GatewayFeignClient.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/loan/ApiNotify.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/loan/impl/ApiNotifyServiceImpl.java", "scf-gateway/src/main/java/com/amp/gateway/controller/NotifyPartnerController.java", "scf-gateway/src/main/java/com/amp/gateway/enums/NotifyBusinessType.java" ],
    "estimatedEffort" : "10h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖补缴申请记录和两种入账方式完成；联调前需取得聚保融通知协议及地址配置。",
    "risk" : "通知协议、重试规则和打款失败/退票终态未明确，当前可靠性方案只能按现有网关及 api_notify 能力估算。"
  }, {
    "title" : "scf-saps 补充备付金明细订单号与补缴交易类型",
    "description" : "扩展既有备付金余额明细接口，按交易类型返回客户订单号，并将保费补缴纳入明细查询。",
    "requirementChangePoint" : "备付金余额明细接口新增订单号返回字段及保费补缴交易类型。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/account/balance/record/query", "AccountTransactionTypeEnum", "account_balance_record.order_no" ],
    "changePoints" : [ "1. 扩展 AccountTransactionTypeEnum 和接口字段说明，增加保费补缴交易类型。", "2. 响应模型增加 orderNo，并在查询组装中读取 account_balance_record.order_no。", "3. 充值、对账单扣款返回空订单号；首笔款扣款、首笔款退款和保费补缴写入并返回客户订单号。", "4. 保持 sourceOrderNo 现有语义：补缴场景返回补费业务单号，其他交易类型保持原关联业务单号。", "5. 同步管理端查询/导出模型中受交易类型枚举影响的展示，并验证历史数据空值兼容。", "6. 增加五类交易明细映射、分页过滤和金额单位转换测试。" ],
    "systemTags" : [ "scf-saps" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/AccountApiController.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountApiService.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiResponse.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/entity/AccountBalanceRecordEntity.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/enums/AccountTransactionTypeEnum.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-saps",
    "relatedFiles" : [ "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountApiService.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiRequest.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiResponse.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/entity/AccountBalanceRecordEntity.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/enums/AccountTransactionTypeEnum.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "保费补缴交易流水由“双方式入账与回调规则”任务写入。",
    "risk" : "首笔款退款现有流水是否均已保存客户订单号需做数据抽样；历史记录可能只能返回空值。"
  }, {
    "title" : "insurance_admin 增加保费补缴审核结果与补费方式检索展示",
    "description" : "在现有运营后台保费补缴页面和导出中展示审核结果、补费方式，并提供对应筛选项。",
    "requirementChangePoint" : "后台保费补缴记录增加审核结果、补费方式表头及检索项。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "保费补缴列表页", "保费补缴查询参数", "保费补缴导出模板", "审核结果与补费方式字典" ],
    "changePoints" : [ "1. 在检索区增加审核结果和补费方式下拉框，枚举分别为通过/拒绝、备付金补费/另行付款。", "2. 在表格增加审核结果、补费方式列，并兼容存量记录默认值。", "3. 后端分页查询增加 auditResult、paymentMethod 条件并同步 Mapper SQL。", "4. 更新 operator、channel、funder 三套保费补缴导出模板及填充模型，确保筛选条件与列表一致。", "5. 复核现有页面、导出和下载权限；本需求无新增操作按钮时不扩展写权限。", "6. 验证聚保融通过/拒绝记录、自营默认记录、组合筛选和导出字段。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "insurance_admin/src/views/order/premiumBackPayment/index.vue", "insurance_admin/src/api/api_list.js", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_operator.xlsx" ],
    "confidence" : "HIGH",
    "moduleName" : "insurance_admin",
    "relatedFiles" : [ "insurance_admin/src/views/order/premiumBackPayment/index.vue", "insurance_admin/src/api/api_list.js", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_operator.xlsx", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_channel.xlsx", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_funder.xlsx" ],
    "estimatedEffort" : "6h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scf-order 完成审核结果、补费方式字段及查询条件。",
    "risk" : "三套导出模板均需同步，遗漏任一角色模板会导致页面有字段而导出缺列。"
  }, {
    "title" : "scf-intf-common 增加保费补缴申请文件上传类型",
    "description" : "为聚保融通过既有文件上传接口取得补缴申请文件 fileKey 提供类型白名单，并支持 scf-order 回查文件信息。",
    "requirementChangePoint" : "文件类型新增“保费补缴申请文件”。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/file/fileUpload", "oss.support.file-types", "文件详情查询接口" ],
    "changePoints" : [ "1. 在各目标环境 oss.support.file-types 中新增保费补缴申请文件 serviceType 映射。", "2. 明确并配置允许的后缀、大小和数量规则，保持上传返回 fileKey 的既有契约。", "3. 在 scf-order 申请处理时通过文件详情接口校验 fileKey 存在、归属和文件类型，并解析后台下载所需 URL。", "4. 验证未知类型拒绝、合法文件上传、伪造/失效 fileKey 拒绝及后台下载。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/oss/OutwardController.java", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/config/FileTypeProperties.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java", "scf_channel_admin/src/views/order/components/PremiumSupplement.vue" ],
    "confidence" : "MEDIUM",
    "moduleName" : "scf-intf-common",
    "relatedFiles" : [ "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/oss/OutwardController.java", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/config/FileTypeProperties.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "需产品或接口方先明确 serviceType 编码和文件规格。",
    "risk" : "类型白名单位于外部配置中心，仓库内无当前环境配置值，发布时可能漏配某一环境。"
  } ],
  "totalEstimatedEffort" : "54h",
  "developmentEstimatedEffort" : "54h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-05T11:09:19.945294",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 272

<!-- workhub-analysis:7854fb4062484a4774d97c5c8384b850d7271e247bdb9e29c389338a96cc70f4 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：109
- 记录时间：2026-11-10T16:06:02.641752
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202611030001/历史版本/498a542e-6916-4418-ae5e-c3bb9b2b0b59/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202611030001/历史版本/498a542e-6916-4418-ae5e-c3bb9b2b0b59/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "租赁资产保理服务现行规则为客户当期应还款项连续逾期达到70个自然日时触发回购。根据最新业务合同，2026年11月1日至11月30日期间达到该条件的逾期资产暂不触发收购；自2026年12月1日起恢复执行，并纳入11月期间已达到70个自然日、应收购待收购的资产。存在债务人已向原债权人清偿、车辆担保权利灭失或债权权利瑕疵的资产，不计入承接标的资产且不予收购。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "shihao" ],
  "workItems" : [ {
    "title" : "回购暂时取消，直到12-01",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "shihao",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  } ],
  "totalEstimatedEffort" : "8h",
  "developmentEstimatedEffort" : "4h",
  "testingEstimatedEffort" : "4h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-10T16:06:01.633271",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 生成需求澄清项 · 需求 277

<!-- workhub-analysis:45cd6ff9d7c93450b10926c2f1dd18bf192de734955d450e88f60f46c4ff7877 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：44
- 记录时间：2026-11-12T15:44:33.468061
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/b823051e-2655-4980-9d6d-08b84caf8b87/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "罚息与提前结清手续费是否需要补充“未还”字段",
  "description" : "需求描述要求返回各科目“应还、已还、未还”三个数值，但附件字段清单中罚息、提前结清手续费仅有“应还、已还”两列，未列“未还”。请确认这两个科目是否需要增加“未还罚息”“未还提前结清手续费”，还是以字段清单为准不提供。",
  "evidence" : "需求描述：“返回账单各科目（本金、服务费-资金方/合作方/技术服务方/业务指定方、资产管理费、罚息、提前结清手续费）的应还、已还、未还实时数值”；附件《申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf》字段清单中罚息、提前结清手续费仅列“应还、已还”，两处不一致。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "通汇账单明细数据的同步方式与频率",
  "description" : "需求核心是“同步通汇的账单明细数据”，但方案仅描述在【还款计划查询】接口新增字段。请确认通汇→申城的数据获取方式（通汇推送接口、申城定时拉取、或数据已在本地库）、同步频率及增量/全量口径。",
  "evidence" : "需求描述：“同步通汇的账单明细数据（每期各科目的应收和最终实收）”；附件方案仅涉及接口加字段，未提供通汇侧接口文档或同步机制说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "QUESTION",
  "title" : "同步范围是否包含历史存量账单",
  "description" : "请确认本次同步的账单范围：仅上线后的新增账单，还是包含历史存量账单；若含历史账单，各科目应收/实收的历史数据由通汇提供还是申城侧回补。",
  "evidence" : "需求描述“每期各科目的应收和最终实收”未限定账单时间范围；需求材料未说明存量数据处理方式，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "QUESTION",
  "title" : "四类服务费与账单科目的映射关系",
  "description" : "请确认现有账单/科目体系是否已按“资金方、合作方、技术服务方、业务指定方”四类分别记录服务费的应收与实收；若通汇侧为单一或不同粒度的服务费科目，请提供四类服务费与通汇科目的映射或拆分规则。",
  "evidence" : "结构化识别字段“新增字段清单”要求分别返回四类服务费的应还/已还/未还数值；材料未提供科目映射或拆分规则，且本次未能通过只读工具确认代码中的科目模型，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "QUESTION",
  "title" : "“未还”数值的计算口径",
  "description" : "请确认“未还”取“应还−已还”的实时计算值，还是账单中单独记录的字段；两者在存在部分冲抵、精度舍入时结果可能不同。",
  "evidence" : "附件方案仅要求返回“应还、已还、未还数值”，未定义“未还”的计算规则，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "QUESTION",
  "title" : "罚息“实时”数值的取值时点",
  "description" : "罚息通常按日计提。请确认查询返回的罚息是通汇同步至申城的最新计提值，还是查询时点按规则实时计算；若两者存在计息尾差，请明确以哪侧数值为准。",
  "evidence" : "附件方案：“合作方调用该查询接口查询时对应账单实时各科目应还、已还、未还数值返回结果”；罚息计提与取数口径材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 6,
  "itemType" : "QUESTION",
  "title" : "还款金额在各科目间的冲抵/分摊顺序",
  "description" : "一笔还款可能同时覆盖多个科目，各科目的“已还”数值取决于冲抵顺序。请提供冲抵/分摊规则（如先罚息、再费用、后本金的顺序），并明确分摊结果由通汇侧计算后同步，还是申城侧按规则计算。",
  "evidence" : "附件方案要求按科目返回“已还”数值，但材料未说明分摊/冲抵规则及其归属方，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 7,
  "itemType" : "QUESTION",
  "title" : "未出账/未到期期次的返回规则",
  "description" : "请确认当某期账单尚未生成或未到还款日时，该期在【还款计划查询】结果中如何返回：返回全部为0的记录、返回应还值（已还为0）、还是不返回该期。",
  "evidence" : "附件方案：“根据具体订单产品规则，部分科目没有数值的则传0”，仅覆盖科目级取0场景，未覆盖整期未出账场景，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 8,
  "itemType" : "QUESTION",
  "title" : "新增字段的报文字段名与释义",
  "description" : "附件PDF字段表的“释义”列全部为空。请提供新增字段的接口英文字段名（报文key）、释义及示例值；或确认由研发按接口现有命名规范拟定后回发业务/合作方确认。",
  "evidence" : "附件《申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf》字段表“释义”列为空，材料未提供接口报文字段定义，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 9,
  "itemType" : "QUESTION",
  "title" : "新增字段的生效范围（全部调用方或仅申城业务）",
  "description" : "【还款计划查询】为存量合作方在用接口。请确认新增字段是对所有调用该接口的合作方统一返回，还是仅申城医美业务/指定合作方返回，其他调用方报文保持不变。",
  "evidence" : "附件方案：“上述新增字段根据合作方调用该查询接口查询时…返回结果”，未区分调用方范围；证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "通汇侧外部依赖风险",
  "description" : "各科目应收/实收数据来源为通汇，若通汇侧无现成接口或数据粒度不满足（如未按四类服务方拆分服务费），需通汇侧配合改造，跨系统排期与联调存在外部依赖，可能影响整体交付节奏。",
  "evidence" : "需求描述：“同步通汇的账单明细数据”；材料未附通汇侧接口文档或数据结构，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "账单科目粒度改造与存量数据回补风险",
  "description" : "若现有账单/还款模型未按四类服务方分别记录应收与实收，实际改造将不限于接口加字段，还需扩展数据模型并核对/回补存量数据，范围与风险显著上升，需业务知悉并接受可能的范围调整。",
  "evidence" : "附件方案表述为“拟在现有【还款计划查询】接口中新增字段”；现有模型是否已支持四类服务费科目未能通过代码确认（本次仅依据需求材料），证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "需求描述与字段清单不一致的返工风险",
  "description" : "需求描述要求全部科目返回“应还、已还、未还”，而字段清单中罚息、提前结清手续费缺少“未还”，若业务与研发各自按不同版本理解，存在联调或验收阶段返工风险。",
  "evidence" : "需求描述与附件PDF字段清单对比：罚息、提前结清手续费仅列“应还、已还”两列。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "RISK",
  "title" : "存量合作方报文兼容风险",
  "description" : "在存量【还款计划查询】接口上新增一批必填字段会改变返回报文结构，存量调用方若对报文做严格校验或全量解析，可能出现兼容问题，需在上线前确认影响面并通知合作方。",
  "evidence" : "附件方案：“拟在现有【还款计划查询】接口中新增字段”且全部“必填”；存量调用方范围及解析方式材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "RISK",
  "title" : "实时数值与对账口径偏差风险",
  "description" : "接口承诺返回“实时”数值，若数据依赖通汇同步，则查询结果与通汇侧时点值可能存在同步延迟或计息尾差；需求中“最终实收”与“实时数值”两种表述并存，合作方对账时可能产生差异，需接受明确的对账口径。",
  "evidence" : "需求描述：“每期各科目的应收和最终实收”与附件方案“实时各科目应还、已还、未还数值”并存；同步时效与对账规则材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### WorkHub 临时分析 · 回复需求澄清项 · 需求 277

<!-- workhub-analysis:6d21b976abbf12824a7b8e964afc9a3253f88baf6c8d7a405eab8619691049c3 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：44
- 记录时间：2026-11-12T16:01:54.306461
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/b823051e-2655-4980-9d6d-08b84caf8b87/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "罚息与提前结清手续费是否需要补充“未还”字段",
  "description" : "需求描述要求返回各科目“应还、已还、未还”三个数值，但附件字段清单中罚息、提前结清手续费仅有“应还、已还”两列，未列“未还”。请确认这两个科目是否需要增加“未还罚息”“未还提前结清手续费”，还是以字段清单为准不提供。",
  "evidence" : "需求描述：“返回账单各科目（本金、服务费-资金方/合作方/技术服务方/业务指定方、资产管理费、罚息、提前结清手续费）的应还、已还、未还实时数值”；附件《申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf》字段清单中罚息、提前结清手续费仅列“应还、已还”，两处不一致。",
  "status" : "RESPONDED",
  "responseText" : "不需要",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-12T16:01:51.424092"
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "通汇账单明细数据的同步方式与频率",
  "description" : "需求核心是“同步通汇的账单明细数据”，但方案仅描述在【还款计划查询】接口新增字段。请确认通汇→申城的数据获取方式（通汇推送接口、申城定时拉取、或数据已在本地库）、同步频率及增量/全量口径。",
  "evidence" : "需求描述：“同步通汇的账单明细数据（每期各科目的应收和最终实收）”；附件方案仅涉及接口加字段，未提供通汇侧接口文档或同步机制说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "QUESTION",
  "title" : "同步范围是否包含历史存量账单",
  "description" : "请确认本次同步的账单范围：仅上线后的新增账单，还是包含历史存量账单；若含历史账单，各科目应收/实收的历史数据由通汇提供还是申城侧回补。",
  "evidence" : "需求描述“每期各科目的应收和最终实收”未限定账单时间范围；需求材料未说明存量数据处理方式，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "QUESTION",
  "title" : "四类服务费与账单科目的映射关系",
  "description" : "请确认现有账单/科目体系是否已按“资金方、合作方、技术服务方、业务指定方”四类分别记录服务费的应收与实收；若通汇侧为单一或不同粒度的服务费科目，请提供四类服务费与通汇科目的映射或拆分规则。",
  "evidence" : "结构化识别字段“新增字段清单”要求分别返回四类服务费的应还/已还/未还数值；材料未提供科目映射或拆分规则，且本次未能通过只读工具确认代码中的科目模型，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "QUESTION",
  "title" : "“未还”数值的计算口径",
  "description" : "请确认“未还”取“应还−已还”的实时计算值，还是账单中单独记录的字段；两者在存在部分冲抵、精度舍入时结果可能不同。",
  "evidence" : "附件方案仅要求返回“应还、已还、未还数值”，未定义“未还”的计算规则，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "QUESTION",
  "title" : "罚息“实时”数值的取值时点",
  "description" : "罚息通常按日计提。请确认查询返回的罚息是通汇同步至申城的最新计提值，还是查询时点按规则实时计算；若两者存在计息尾差，请明确以哪侧数值为准。",
  "evidence" : "附件方案：“合作方调用该查询接口查询时对应账单实时各科目应还、已还、未还数值返回结果”；罚息计提与取数口径材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 6,
  "itemType" : "QUESTION",
  "title" : "还款金额在各科目间的冲抵/分摊顺序",
  "description" : "一笔还款可能同时覆盖多个科目，各科目的“已还”数值取决于冲抵顺序。请提供冲抵/分摊规则（如先罚息、再费用、后本金的顺序），并明确分摊结果由通汇侧计算后同步，还是申城侧按规则计算。",
  "evidence" : "附件方案要求按科目返回“已还”数值，但材料未说明分摊/冲抵规则及其归属方，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 7,
  "itemType" : "QUESTION",
  "title" : "未出账/未到期期次的返回规则",
  "description" : "请确认当某期账单尚未生成或未到还款日时，该期在【还款计划查询】结果中如何返回：返回全部为0的记录、返回应还值（已还为0）、还是不返回该期。",
  "evidence" : "附件方案：“根据具体订单产品规则，部分科目没有数值的则传0”，仅覆盖科目级取0场景，未覆盖整期未出账场景，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 8,
  "itemType" : "QUESTION",
  "title" : "新增字段的报文字段名与释义",
  "description" : "附件PDF字段表的“释义”列全部为空。请提供新增字段的接口英文字段名（报文key）、释义及示例值；或确认由研发按接口现有命名规范拟定后回发业务/合作方确认。",
  "evidence" : "附件《申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf》字段表“释义”列为空，材料未提供接口报文字段定义，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 9,
  "itemType" : "QUESTION",
  "title" : "新增字段的生效范围（全部调用方或仅申城业务）",
  "description" : "【还款计划查询】为存量合作方在用接口。请确认新增字段是对所有调用该接口的合作方统一返回，还是仅申城医美业务/指定合作方返回，其他调用方报文保持不变。",
  "evidence" : "附件方案：“上述新增字段根据合作方调用该查询接口查询时…返回结果”，未区分调用方范围；证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "通汇侧外部依赖风险",
  "description" : "各科目应收/实收数据来源为通汇，若通汇侧无现成接口或数据粒度不满足（如未按四类服务方拆分服务费），需通汇侧配合改造，跨系统排期与联调存在外部依赖，可能影响整体交付节奏。",
  "evidence" : "需求描述：“同步通汇的账单明细数据”；材料未附通汇侧接口文档或数据结构，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "账单科目粒度改造与存量数据回补风险",
  "description" : "若现有账单/还款模型未按四类服务方分别记录应收与实收，实际改造将不限于接口加字段，还需扩展数据模型并核对/回补存量数据，范围与风险显著上升，需业务知悉并接受可能的范围调整。",
  "evidence" : "附件方案表述为“拟在现有【还款计划查询】接口中新增字段”；现有模型是否已支持四类服务费科目未能通过代码确认（本次仅依据需求材料），证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "需求描述与字段清单不一致的返工风险",
  "description" : "需求描述要求全部科目返回“应还、已还、未还”，而字段清单中罚息、提前结清手续费缺少“未还”，若业务与研发各自按不同版本理解，存在联调或验收阶段返工风险。",
  "evidence" : "需求描述与附件PDF字段清单对比：罚息、提前结清手续费仅列“应还、已还”两列。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "RISK",
  "title" : "存量合作方报文兼容风险",
  "description" : "在存量【还款计划查询】接口上新增一批必填字段会改变返回报文结构，存量调用方若对报文做严格校验或全量解析，可能出现兼容问题，需在上线前确认影响面并通知合作方。",
  "evidence" : "附件方案：“拟在现有【还款计划查询】接口中新增字段”且全部“必填”；存量调用方范围及解析方式材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "RISK",
  "title" : "实时数值与对账口径偏差风险",
  "description" : "接口承诺返回“实时”数值，若数据依赖通汇同步，则查询结果与通汇侧时点值可能存在同步延迟或计息尾差；需求中“最终实收”与“实时数值”两种表述并存，合作方对账时可能产生差异，需接受明确的对账口径。",
  "evidence" : "需求描述：“每期各科目的应收和最终实收”与附件方案“实时各科目应还、已还、未还数值”并存；同步时效与对账规则材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### WorkHub 临时分析 · 回复需求澄清项 · 需求 277

<!-- workhub-analysis:f6bf097a420733cc7ac8dda56ee4aaccf0455d4122b737757836264a57e9329b -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：44
- 记录时间：2026-11-12T16:02:47.560666
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/b823051e-2655-4980-9d6d-08b84caf8b87/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "罚息与提前结清手续费是否需要补充“未还”字段",
  "description" : "需求描述要求返回各科目“应还、已还、未还”三个数值，但附件字段清单中罚息、提前结清手续费仅有“应还、已还”两列，未列“未还”。请确认这两个科目是否需要增加“未还罚息”“未还提前结清手续费”，还是以字段清单为准不提供。",
  "evidence" : "需求描述：“返回账单各科目（本金、服务费-资金方/合作方/技术服务方/业务指定方、资产管理费、罚息、提前结清手续费）的应还、已还、未还实时数值”；附件《申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf》字段清单中罚息、提前结清手续费仅列“应还、已还”，两处不一致。",
  "status" : "RESPONDED",
  "responseText" : "不需要",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-12T16:01:51.424092"
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "通汇账单明细数据的同步方式与频率",
  "description" : "需求核心是“同步通汇的账单明细数据”，但方案仅描述在【还款计划查询】接口新增字段。请确认通汇→申城的数据获取方式（通汇推送接口、申城定时拉取、或数据已在本地库）、同步频率及增量/全量口径。",
  "evidence" : "需求描述：“同步通汇的账单明细数据（每期各科目的应收和最终实收）”；附件方案仅涉及接口加字段，未提供通汇侧接口文档或同步机制说明，证据不足。",
  "status" : "RESPONDED",
  "responseText" : "无需",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-12T16:02:47.435957"
}, {
  "index" : 2,
  "itemType" : "QUESTION",
  "title" : "同步范围是否包含历史存量账单",
  "description" : "请确认本次同步的账单范围：仅上线后的新增账单，还是包含历史存量账单；若含历史账单，各科目应收/实收的历史数据由通汇提供还是申城侧回补。",
  "evidence" : "需求描述“每期各科目的应收和最终实收”未限定账单时间范围；需求材料未说明存量数据处理方式，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "QUESTION",
  "title" : "四类服务费与账单科目的映射关系",
  "description" : "请确认现有账单/科目体系是否已按“资金方、合作方、技术服务方、业务指定方”四类分别记录服务费的应收与实收；若通汇侧为单一或不同粒度的服务费科目，请提供四类服务费与通汇科目的映射或拆分规则。",
  "evidence" : "结构化识别字段“新增字段清单”要求分别返回四类服务费的应还/已还/未还数值；材料未提供科目映射或拆分规则，且本次未能通过只读工具确认代码中的科目模型，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "QUESTION",
  "title" : "“未还”数值的计算口径",
  "description" : "请确认“未还”取“应还−已还”的实时计算值，还是账单中单独记录的字段；两者在存在部分冲抵、精度舍入时结果可能不同。",
  "evidence" : "附件方案仅要求返回“应还、已还、未还数值”，未定义“未还”的计算规则，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "QUESTION",
  "title" : "罚息“实时”数值的取值时点",
  "description" : "罚息通常按日计提。请确认查询返回的罚息是通汇同步至申城的最新计提值，还是查询时点按规则实时计算；若两者存在计息尾差，请明确以哪侧数值为准。",
  "evidence" : "附件方案：“合作方调用该查询接口查询时对应账单实时各科目应还、已还、未还数值返回结果”；罚息计提与取数口径材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 6,
  "itemType" : "QUESTION",
  "title" : "还款金额在各科目间的冲抵/分摊顺序",
  "description" : "一笔还款可能同时覆盖多个科目，各科目的“已还”数值取决于冲抵顺序。请提供冲抵/分摊规则（如先罚息、再费用、后本金的顺序），并明确分摊结果由通汇侧计算后同步，还是申城侧按规则计算。",
  "evidence" : "附件方案要求按科目返回“已还”数值，但材料未说明分摊/冲抵规则及其归属方，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 7,
  "itemType" : "QUESTION",
  "title" : "未出账/未到期期次的返回规则",
  "description" : "请确认当某期账单尚未生成或未到还款日时，该期在【还款计划查询】结果中如何返回：返回全部为0的记录、返回应还值（已还为0）、还是不返回该期。",
  "evidence" : "附件方案：“根据具体订单产品规则，部分科目没有数值的则传0”，仅覆盖科目级取0场景，未覆盖整期未出账场景，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 8,
  "itemType" : "QUESTION",
  "title" : "新增字段的报文字段名与释义",
  "description" : "附件PDF字段表的“释义”列全部为空。请提供新增字段的接口英文字段名（报文key）、释义及示例值；或确认由研发按接口现有命名规范拟定后回发业务/合作方确认。",
  "evidence" : "附件《申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf》字段表“释义”列为空，材料未提供接口报文字段定义，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 9,
  "itemType" : "QUESTION",
  "title" : "新增字段的生效范围（全部调用方或仅申城业务）",
  "description" : "【还款计划查询】为存量合作方在用接口。请确认新增字段是对所有调用该接口的合作方统一返回，还是仅申城医美业务/指定合作方返回，其他调用方报文保持不变。",
  "evidence" : "附件方案：“上述新增字段根据合作方调用该查询接口查询时…返回结果”，未区分调用方范围；证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "通汇侧外部依赖风险",
  "description" : "各科目应收/实收数据来源为通汇，若通汇侧无现成接口或数据粒度不满足（如未按四类服务方拆分服务费），需通汇侧配合改造，跨系统排期与联调存在外部依赖，可能影响整体交付节奏。",
  "evidence" : "需求描述：“同步通汇的账单明细数据”；材料未附通汇侧接口文档或数据结构，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "账单科目粒度改造与存量数据回补风险",
  "description" : "若现有账单/还款模型未按四类服务方分别记录应收与实收，实际改造将不限于接口加字段，还需扩展数据模型并核对/回补存量数据，范围与风险显著上升，需业务知悉并接受可能的范围调整。",
  "evidence" : "附件方案表述为“拟在现有【还款计划查询】接口中新增字段”；现有模型是否已支持四类服务费科目未能通过代码确认（本次仅依据需求材料），证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "需求描述与字段清单不一致的返工风险",
  "description" : "需求描述要求全部科目返回“应还、已还、未还”，而字段清单中罚息、提前结清手续费缺少“未还”，若业务与研发各自按不同版本理解，存在联调或验收阶段返工风险。",
  "evidence" : "需求描述与附件PDF字段清单对比：罚息、提前结清手续费仅列“应还、已还”两列。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "RISK",
  "title" : "存量合作方报文兼容风险",
  "description" : "在存量【还款计划查询】接口上新增一批必填字段会改变返回报文结构，存量调用方若对报文做严格校验或全量解析，可能出现兼容问题，需在上线前确认影响面并通知合作方。",
  "evidence" : "附件方案：“拟在现有【还款计划查询】接口中新增字段”且全部“必填”；存量调用方范围及解析方式材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "RISK",
  "title" : "实时数值与对账口径偏差风险",
  "description" : "接口承诺返回“实时”数值，若数据依赖通汇同步，则查询结果与通汇侧时点值可能存在同步延迟或计息尾差；需求中“最终实收”与“实时数值”两种表述并存，合作方对账时可能产生差异，需接受明确的对账口径。",
  "evidence" : "需求描述：“每期各科目的应收和最终实收”与附件方案“实时各科目应还、已还、未还数值”并存；同步时效与对账规则材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### WorkHub 临时分析 · 回复需求澄清项 · 需求 277

<!-- workhub-analysis:fe2437fb01c0616162c6d5b54d14580ad88f644e4f5f6eb97eb3f615ab7f2785 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：44
- 记录时间：2026-11-12T16:03:04.503727
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/b823051e-2655-4980-9d6d-08b84caf8b87/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "罚息与提前结清手续费是否需要补充“未还”字段",
  "description" : "需求描述要求返回各科目“应还、已还、未还”三个数值，但附件字段清单中罚息、提前结清手续费仅有“应还、已还”两列，未列“未还”。请确认这两个科目是否需要增加“未还罚息”“未还提前结清手续费”，还是以字段清单为准不提供。",
  "evidence" : "需求描述：“返回账单各科目（本金、服务费-资金方/合作方/技术服务方/业务指定方、资产管理费、罚息、提前结清手续费）的应还、已还、未还实时数值”；附件《申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf》字段清单中罚息、提前结清手续费仅列“应还、已还”，两处不一致。",
  "status" : "RESPONDED",
  "responseText" : "不需要",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-12T16:01:51.424092"
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "通汇账单明细数据的同步方式与频率",
  "description" : "需求核心是“同步通汇的账单明细数据”，但方案仅描述在【还款计划查询】接口新增字段。请确认通汇→申城的数据获取方式（通汇推送接口、申城定时拉取、或数据已在本地库）、同步频率及增量/全量口径。",
  "evidence" : "需求描述：“同步通汇的账单明细数据（每期各科目的应收和最终实收）”；附件方案仅涉及接口加字段，未提供通汇侧接口文档或同步机制说明，证据不足。",
  "status" : "RESPONDED",
  "responseText" : "无需",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-12T16:02:47.435957"
}, {
  "index" : 2,
  "itemType" : "QUESTION",
  "title" : "同步范围是否包含历史存量账单",
  "description" : "请确认本次同步的账单范围：仅上线后的新增账单，还是包含历史存量账单；若含历史账单，各科目应收/实收的历史数据由通汇提供还是申城侧回补。",
  "evidence" : "需求描述“每期各科目的应收和最终实收”未限定账单时间范围；需求材料未说明存量数据处理方式，证据不足。",
  "status" : "RESPONDED",
  "responseText" : "包含",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-12T16:03:04.375551"
}, {
  "index" : 3,
  "itemType" : "QUESTION",
  "title" : "四类服务费与账单科目的映射关系",
  "description" : "请确认现有账单/科目体系是否已按“资金方、合作方、技术服务方、业务指定方”四类分别记录服务费的应收与实收；若通汇侧为单一或不同粒度的服务费科目，请提供四类服务费与通汇科目的映射或拆分规则。",
  "evidence" : "结构化识别字段“新增字段清单”要求分别返回四类服务费的应还/已还/未还数值；材料未提供科目映射或拆分规则，且本次未能通过只读工具确认代码中的科目模型，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "QUESTION",
  "title" : "“未还”数值的计算口径",
  "description" : "请确认“未还”取“应还−已还”的实时计算值，还是账单中单独记录的字段；两者在存在部分冲抵、精度舍入时结果可能不同。",
  "evidence" : "附件方案仅要求返回“应还、已还、未还数值”，未定义“未还”的计算规则，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "QUESTION",
  "title" : "罚息“实时”数值的取值时点",
  "description" : "罚息通常按日计提。请确认查询返回的罚息是通汇同步至申城的最新计提值，还是查询时点按规则实时计算；若两者存在计息尾差，请明确以哪侧数值为准。",
  "evidence" : "附件方案：“合作方调用该查询接口查询时对应账单实时各科目应还、已还、未还数值返回结果”；罚息计提与取数口径材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 6,
  "itemType" : "QUESTION",
  "title" : "还款金额在各科目间的冲抵/分摊顺序",
  "description" : "一笔还款可能同时覆盖多个科目，各科目的“已还”数值取决于冲抵顺序。请提供冲抵/分摊规则（如先罚息、再费用、后本金的顺序），并明确分摊结果由通汇侧计算后同步，还是申城侧按规则计算。",
  "evidence" : "附件方案要求按科目返回“已还”数值，但材料未说明分摊/冲抵规则及其归属方，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 7,
  "itemType" : "QUESTION",
  "title" : "未出账/未到期期次的返回规则",
  "description" : "请确认当某期账单尚未生成或未到还款日时，该期在【还款计划查询】结果中如何返回：返回全部为0的记录、返回应还值（已还为0）、还是不返回该期。",
  "evidence" : "附件方案：“根据具体订单产品规则，部分科目没有数值的则传0”，仅覆盖科目级取0场景，未覆盖整期未出账场景，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 8,
  "itemType" : "QUESTION",
  "title" : "新增字段的报文字段名与释义",
  "description" : "附件PDF字段表的“释义”列全部为空。请提供新增字段的接口英文字段名（报文key）、释义及示例值；或确认由研发按接口现有命名规范拟定后回发业务/合作方确认。",
  "evidence" : "附件《申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf》字段表“释义”列为空，材料未提供接口报文字段定义，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 9,
  "itemType" : "QUESTION",
  "title" : "新增字段的生效范围（全部调用方或仅申城业务）",
  "description" : "【还款计划查询】为存量合作方在用接口。请确认新增字段是对所有调用该接口的合作方统一返回，还是仅申城医美业务/指定合作方返回，其他调用方报文保持不变。",
  "evidence" : "附件方案：“上述新增字段根据合作方调用该查询接口查询时…返回结果”，未区分调用方范围；证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "通汇侧外部依赖风险",
  "description" : "各科目应收/实收数据来源为通汇，若通汇侧无现成接口或数据粒度不满足（如未按四类服务方拆分服务费），需通汇侧配合改造，跨系统排期与联调存在外部依赖，可能影响整体交付节奏。",
  "evidence" : "需求描述：“同步通汇的账单明细数据”；材料未附通汇侧接口文档或数据结构，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "账单科目粒度改造与存量数据回补风险",
  "description" : "若现有账单/还款模型未按四类服务方分别记录应收与实收，实际改造将不限于接口加字段，还需扩展数据模型并核对/回补存量数据，范围与风险显著上升，需业务知悉并接受可能的范围调整。",
  "evidence" : "附件方案表述为“拟在现有【还款计划查询】接口中新增字段”；现有模型是否已支持四类服务费科目未能通过代码确认（本次仅依据需求材料），证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "需求描述与字段清单不一致的返工风险",
  "description" : "需求描述要求全部科目返回“应还、已还、未还”，而字段清单中罚息、提前结清手续费缺少“未还”，若业务与研发各自按不同版本理解，存在联调或验收阶段返工风险。",
  "evidence" : "需求描述与附件PDF字段清单对比：罚息、提前结清手续费仅列“应还、已还”两列。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "RISK",
  "title" : "存量合作方报文兼容风险",
  "description" : "在存量【还款计划查询】接口上新增一批必填字段会改变返回报文结构，存量调用方若对报文做严格校验或全量解析，可能出现兼容问题，需在上线前确认影响面并通知合作方。",
  "evidence" : "附件方案：“拟在现有【还款计划查询】接口中新增字段”且全部“必填”；存量调用方范围及解析方式材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "RISK",
  "title" : "实时数值与对账口径偏差风险",
  "description" : "接口承诺返回“实时”数值，若数据依赖通汇同步，则查询结果与通汇侧时点值可能存在同步延迟或计息尾差；需求中“最终实收”与“实时数值”两种表述并存，合作方对账时可能产生差异，需接受明确的对账口径。",
  "evidence" : "需求描述：“每期各科目的应收和最终实收”与附件方案“实时各科目应还、已还、未还数值”并存；同步时效与对账规则材料未说明，证据不足。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### WorkHub 临时分析 · 生成需求澄清项 · 需求 277

<!-- workhub-analysis:9e0c1d83ccd7ee770af444bc3262f689f2a19785390f687b1a71dd389b82f6ea -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：45
- 记录时间：2026-11-12T16:45:44.256712
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/09d49d21-0ef2-47bf-86ea-85b44b9b03a0/source.md>)

```json
[ ]
```



### WorkHub 临时分析 · 生成研发评估 · 需求 277

<!-- workhub-analysis:feb04488ac598912448bb28469342ff8f1fc1392ca0cdd46fe500b301124141b -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：110
- 记录时间：2026-11-12T16:54:21.259004
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/9fd6380d-19fd-4767-bcd5-63258a279ff3/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/9fd6380d-19fd-4767-bcd5-63258a279ff3/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "我先查阅需求材料已提供的内容，接下来需要在业务线代码中定位【还款计划查询】接口及相关科目（本金/服务费/资产管理费/罚息/提前结清手续费）的现有实现。先并行搜索关键代码。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ ],
  "totalEstimatedEffort" : null,
  "developmentEstimatedEffort" : null,
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-12T16:54:21.170574",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 生成研发评估 · 需求 277

<!-- workhub-analysis:bc9977eabc75823ad642401d7887b07d656e106ef5da9fe8bd0f5a23def381be -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：111
- 记录时间：2026-11-12T18:08:23.553994
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/23b8bfcc-cce6-4a21-9c3e-c01d6eeb3ae1/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/23b8bfcc-cce6-4a21-9c3e-c01d6eeb3ae1/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "我先查看需求材料已提供的信息，再通过只读工具检索业务线代码，确认【还款计划查询】接口的现有实现。先并行搜索几个关键仓库。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ ],
  "totalEstimatedEffort" : null,
  "developmentEstimatedEffort" : null,
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-12T18:08:23.429055",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 生成研发评估 · 需求 277

<!-- workhub-analysis:ef722321c02af3270312dc59867c9bbbe1da51f903f7ad57acfdfc71f215063e -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：112
- 记录时间：2026-11-12T18:45:47.533159
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/e7e90c40-9c54-4c36-860c-fcef0f585841/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/e7e90c40-9c54-4c36-860c-fcef0f585841/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "我先查看需求材料中提到的【还款计划查询】接口在代码中的位置，确认现有系统和可复用逻辑。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ ],
  "totalEstimatedEffort" : null,
  "developmentEstimatedEffort" : null,
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-12T18:45:47.452021",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 生成研发评估 · 需求 277

<!-- workhub-analysis:abbf3e1772c672b4337897f50d72c0c0546ee90efbc0162aa13f5cd51c5efb68 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：113
- 记录时间：2026-11-12T18:50:56.690797
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/64b8d679-d468-4d24-99d8-3e9a64c0afbe/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/64b8d679-d468-4d24-99d8-3e9a64c0afbe/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "原答复为未完成的评估过程记录：计划按“先分析需求材料，再查代码确认现有系统，最后输出评估草稿”的工作流执行。第一步已完成，提炼出需求核心：在申城资管平台现有【还款计划查询】接口中新增 21 个科目字段（本金、四类服务费、资产管理费、罚息、提前结清手续费的应还/已还/未还实时数值），供合作方查询通汇账单明细数据。第二步（查代码确认现有系统）仅开始搜索【还款计划查询】接口的位置，之后内容中断（出现异常文本“olanışıória后续标记不变”），代码确认与最终评估草稿均未完成。",
  "requirementChangePoints" : [ "在申城资管平台现有【还款计划查询】接口中新增 21 个科目字段（本金、四类服务费、资产管理费、罚息、提前结清手续费的应还/已还/未还实时数值），供合作方查询通汇账单明细数据。" ],
  "impactedModules" : [ "还款计划查询接口（申城资管平台）——代码位置确认未完成" ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ ],
  "totalEstimatedEffort" : null,
  "developmentEstimatedEffort" : null,
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-12T18:50:56.635978",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 生成研发评估 · 需求 277

<!-- workhub-analysis:41fee43641506f8999716cf8302fe46a15f32fe61a35bf4cf3920f821b2fc017 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：117
- 记录时间：2026-11-13T11:41:50.530029
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/256656d8-ffad-4529-a858-6bf4414691ad/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/256656d8-ffad-4529-a858-6bf4414691ad/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "我先梳理需求材料，再按约束检索代码，最后输出评估 JSON。\n\n需求要点（来自 source.md 与附件 PDF 摘要）：\n- 在现有【还款计划查询】接口新增字段，返回账单各科目（本金、四类服务费、资产管理费、罚息、提前结清手续费）的应还/已还/未还实时值，无数值科目传 0。\n- 调用方是合作方（申城侧通过通汇同步账单明细）。\n\n下面开始代码检索，先定位【还款计划查询】接口。",
  "requirementChangePoints" : [ "在现有【还款计划查询】接口新增字段，返回账单各科目（本金、四类服务费、资产管理费、罚息、提前结清手续费）的应还/已还/未还实时值，无数值科目传 0", "调用方是合作方（申城侧通过通汇同步账单明细）" ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ ],
  "totalEstimatedEffort" : null,
  "developmentEstimatedEffort" : null,
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-13T11:41:50.402783",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### WorkHub 临时分析 · 调整研发任务 · 需求 278

<!-- workhub-analysis:54abb72377009a2c6d94632f0e82ad9812893649c11f8bba7ade1370b69bd6d9 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：118
- 记录时间：2026-11-13T13:37:55.685571
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/消费分期/即科医美通汇诚泰保理/2026/202611130002/历史版本/8416516b-cbec-465f-9415-15894ecec0ed/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "消费分期",
  "projectId" : 4,
  "projectName" : "即科医美通汇诚泰保理",
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/消费分期/即科医美通汇诚泰保理/2026/202611130002/历史版本/8416516b-cbec-465f-9415-15894ecec0ed/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "针对目前极石商贸商品分期保理项目（项目编号：JT_XXT-PRJ-ZMY-A，涉及两个产品：JT_XXT-PRJ-ZMY-A001、JT_XXT-PRJ-ZMY-A002），易宝支付渠道，扣款成功后，全部清分给嘉泰保理：正常还款、提前还款当期、提前结清：原清分给担保方的金额，全部清分给嘉泰保理。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "cairui" ],
  "workItems" : [ {
    "title" : "线上扣款全部清分给嘉泰",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "amp-payment" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "cairui",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null
  } ],
  "totalEstimatedEffort" : "4h",
  "developmentEstimatedEffort" : "2h",
  "testingEstimatedEffort" : "2h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-13T13:37:55.343399",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### 临时分析 · 生成研发评估 · 需求 277

<!-- analysis-record:49964c5939abf703cf59bb755ec223502148ba8a315e1e2af3ac2559a4dd315c -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：121
- 记录时间：2026-11-14T21:40:36.206880
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/4112d861-a2f6-4960-8fc5-bd6d74cdc3aa/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/4112d861-a2f6-4960-8fc5-bd6d74cdc3aa/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：仅改造 scs-saps，在现有【还款计划查询】接口（/api/repayment/plan/query，RepayPlanApiController→RepaymentPlanServiceImpl.queryRepaymentPlanListApi→RepaymentPlanApiVo）响应中新增每期科目级应还/已还/未还字段（本金、资金方/合作方/技术服务方/业务指定方服务费、资产管理费、罚息、提前结清手续费，共22个必填字段，无金额科目传0），数据源复用 bill_fee_detail 的 total_amount/paid_amount/unpaid_amount 与管理端成熟的科目汇总口径（含红冲、costAmount、申城医美产品特殊逻辑）。共拆分 2 个研发任务（接口字段开发 8h、文档联调与存量调用方回归 4h）。金额单位与精度、罚息科目口径两处需与申城/通汇对齐；本次未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "【还款计划查询】接口（/api/repayment/plan/query）响应新增每期科目级实时数值字段：应还/已还/未还本金、应还/已还/未还服务费（资金方、合作方、技术服务方、业务指定方）、应还/已还/未还资产管理费。", "同接口新增罚息、提前结清手续费的应还/已还实时数值字段（PDF字段清单未列未还，需求描述文字含未还，口径待确认）。", "所有新增字段必填，按订单产品规则无金额科目一律传0（如本项目技术服务费费率为0）。", "数值为合作方查询时点账单实时值，需覆盖正常还款、部分还款、提前结清、代偿/回购、退费等结算场景后的科目状态。", "满足通汇同步账单明细数据（每期各科目应收与最终实收）的数据获取需要，不新增同步任务，仅扩展现有查询接口。" ],
  "impactedModules" : [ "scs-saps/saps-controller/APIs/RepayPlanApiController（/api/repayment/plan/query）", "scs-saps/saps-model/vo/APIS/RepaymentPlanApiVo", "scs-saps/saps-service/RepaymentPlanServiceImpl#queryRepaymentPlanListApi", "scs-saps bill_fee_detail 科目金额数据口径", "scs-saps/saps-model/enums/BillSubjectNoEnum 科目映射", "合作方接口文档与申城/通汇联调" ],
  "risks" : [ "RepaymentPlanApiVo 现有金额字段为 Integer，而 bill_fee_detail 为 BigDecimal：新增字段的类型、金额单位（元/分）与小数位必须与申城/通汇文档对齐，否则对账口径错误。", "申城代偿/回购线上还款不落 repayment_plan_pay_subject_detail（管理端回退到 statement_charge_resource 展示），科目'已还'依赖 bill_fee_detail.paid_amount 的更新链路，该场景数值需专项验证。", "该接口被 FunderBillSyncScheduleBizServiceImpl 定时同步及 scs-payment(SapsApi)、scs-capital/scs-order(SapsClient) 内部客户端消费，新增字段需保证 JSON 兼容、不破坏存量反序列化与定时任务。", "管理端账单列表存在申城医美产品特殊口径（isThPrdZmycA002、AssetManagementFeeAmount、资方服务费 costAmount 占用、红冲 reverse 明细），接口字段若不复用同一口径会出现接口与页面数值不一致。", "罚息/提前结清手续费是否输出'未还'在 PDF 与需求描述间不一致，字段清单未定稿前存在返工风险。" ],
  "questions" : [ "罚息与提前结清手续费是否需要返回'未还'字段？PDF 字段清单仅列应还/已还两项，需求描述文字为'应还、已还、未还'，两处不一致，直接影响字段清单与验收。", "'罚息'口径是否仅指对客罚息（P0007）？系统还存在对资罚息（P0009，代偿场景计收），是否需要一并返回。", "新增字段的英文命名、金额单位（元/分）与精度需与申城/通汇接口文档对齐定稿（现有 VO 金额为 Integer，科目数据为 BigDecimal）。" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scs-saps 还款计划查询接口新增账单各科目应还已还未还字段",
    "description" : "在现有【还款计划查询】接口 /api/repayment/plan/query 的响应对象 RepaymentPlanApiVo 中新增22个科目级必填字段：应还/已还/未还×（本金、资金方服务费、合作方服务费、技术服务方服务费、业务指定方服务费、资产管理费）+ 罚息应还/已还 + 提前结清手续费应还/已还；在 RepaymentPlanServiceImpl.queryRepaymentPlanListApi 中按账单号批量查询 bill_fee_detail，按 BillSubjectNoEnum 科目映射（P0001本金、P0003资金方、P0004技术服务、P0005合作方、P0006提前结清手续费、P0007罚息、P0008业务指定方、P0012资产管理费）汇总 total_amount/paid_amount/unpaid_amount 实时填充，无金额科目置0；红冲、costAmount 及申城医美产品特殊口径与管理端 queryList 保持一致；补齐各还款场景单测。",
    "requirementChangePoint" : "【还款计划查询】接口响应新增每期科目级实时数值字段：应还/已还/未还本金、四类服务费、资产管理费，及罚息、提前结清手续费应还/已还；无金额科目传0，覆盖各结算场景。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/APIS/RepaymentPlanApiVo.java", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java", "scs-saps/saps-service/src/main/java/com/amp/saps/service/RepaymentPlanService.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/BillSubjectNoEnum.java" ],
    "changePoints" : [ "saps-model/vo/APIS/RepaymentPlanApiVo.java：新增22个科目字段（BigDecimal，命名与单位以定稿文档为准），保留存量字段不动。", "saps-service/RepaymentPlanServiceImpl#queryRepaymentPlanListApi（约1932行）：在现有还款计划返回组装处，按 billNos 批量查询 bill_fee_detail（is_delete=0、is_cancel=0）。", "按 BillSubjectNoEnum 科目映射（P0001/P0003/P0004/P0005/P0006/P0007/P0008/P0012）分组汇总 total_amount/paid_amount/unpaid_amount 填充 VO，缺失科目统一补0。", "科目口径对齐管理端 queryList/AssetManagementFeeAmount：红冲 reverse 明细、资方服务费 costAmount 占用、申城医美产品 isThPrdZmycA002 特殊处理保持一致。", "单测/自测：正常还款、部分还款、提前结清、代偿/回购（科目已还走 bill_fee_detail 更新链路）、退费场景数值正确性与0值兜底。" ],
    "systemTags" : [ "scs-saps" ],
    "evidenceRefs" : [ "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/RepayPlanApiController.java:43（/api/repayment/plan/query 接口入口，orgCode 鉴权）", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java:1932（queryRepaymentPlanListApi 现有实现，经 mapper.queryRepaymentPlanList 查询）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/APIS/RepaymentPlanApiVo.java（现有响应VO仅有总应还/已还/未还，无科目字段）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/BillSubjectNoEnum.java（P0001本金/P0003资方/P0004平台即技术服务费/P0005合作方/P0006提前结清/P0007罚息/P0008业务指定方/P0012资产管理费）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/BillFeeDetailEntity.java（bill_fee_detail 含 subjectNo/totalAmount/paidAmount/unpaidAmount/redAmount/reverse）", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java:1069（管理端 queryList 科目汇总可复用口径）及 2774（AssetManagementFeeAmount）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/RepaymentPlanVo.java（管理端已具备全部科目级应还/已还/未还字段，字段命名可参照）", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/manage/bill/RepaymentPlanPaySubjectDetailController.java（申城代偿/回购回退 statement_charge_resource 展示逻辑，验证科目已还口径）", "附件：申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf（22个新增字段清单）", "scs-saps/docs/raw/feature/am_fee_recon_202608260001/deal/260830_申城医美_智融诚泰再保理需求整理.md（科目与主体口径：技术服务费0、资产管理费月结不入客户应还）" ],
    "confidence" : "MEDIUM",
    "moduleName" : "还款计划查询接口",
    "relatedFiles" : [ "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/APIS/RepaymentPlanApiVo.java", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/RepayPlanApiController.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/BillFeeDetailEntity.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/BillSubjectNoEnum.java" ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "shihao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : "金额单位/精度与罚息科目口径未定稿即开发，可能返工；代偿/回购场景科目已还数值需专项验证；需回归存量内部调用方。"
  }, {
    "title" : "scs-saps 还款计划查询接口科目字段文档定稿与联调回归",
    "description" : "更新合作方《还款计划查询》接口文档，明确22个新增字段的英文名、必填、金额单位与精度、无金额科目传0规则；在测试环境与申城/通汇联调，验证每期各科目应还/已还/未还实时数值与账单实际结算一致；回归存量调用方（FunderBillSyncSchedule 定时同步、scs-payment SapsApi、scs-capital/scs-order SapsClient）确认新增字段不影响反序列化与既有流程。",
    "requirementChangePoint" : "满足通汇同步账单明细数据（每期各科目应收与最终实收）的获取；新增字段必填、无数值科目传0；数值覆盖各结算场景。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "合作方接口文档（还款计划查询）", "scs-saps/saps-business/src/main/java/com/amp/saps/business/impl/FunderBillSyncScheduleBizServiceImpl.java", "scs-payment/scs-payment-api/src/main/java/com/scs/payment/api/service/SapsApi.java", "scs-capital/scs-capital-api/src/main/java/com/scs/capital/api/SapsClient.java", "scs-order/scs-order-api/src/main/java/com/thchengtay/order/api/SapsClient.java" ],
    "changePoints" : [ "接口文档：新增字段清单（字段名/释义/必填/单位/精度/零值规则）定稿并同步申城/通汇。", "测试环境联调：按申请单号查询验证每期科目实时数值，覆盖正常、部分还款、提前结清、代偿/回购、退费场景及0值科目（如技术服务费）。", "存量调用方回归：FunderBillSyncScheduleBizServiceImpl 定时同步（/saps/api/repayment/plan/query 自调用）及 scs-payment/scs-capital/scs-order 客户端反序列化兼容性验证。" ],
    "systemTags" : [ "scs-saps" ],
    "evidenceRefs" : [ "scs-saps/saps-business/src/main/java/com/amp/saps/business/impl/FunderBillSyncScheduleBizServiceImpl.java:71（存量自调用 /saps/api/repayment/plan/query 消费 RepaymentPlanApiVo）", "scs-payment/scs-payment-api/src/main/java/com/scs/payment/api/service/SapsApi.java:28（内部消费方）", "scs-capital/scs-capital-api/src/main/java/com/scs/capital/api/SapsClient.java:34、scs-order/scs-order-api/src/main/java/com/thchengtay/order/api/SapsClient.java:32（内部消费方）", "附件：申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf（联调验收字段依据）" ],
    "confidence" : "MEDIUM",
    "moduleName" : "接口联调与文档",
    "relatedFiles" : [ "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/RepayPlanApiController.java", "scs-saps/saps-business/src/main/java/com/amp/saps/business/impl/FunderBillSyncScheduleBizServiceImpl.java", "scs-payment/scs-payment-api/src/main/java/com/scs/payment/api/service/SapsApi.java", "scs-capital/scs-capital-api/src/main/java/com/scs/capital/api/SapsClient.java", "scs-order/scs-order-api/src/main/java/com/thchengtay/order/api/SapsClient.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "scs-saps 还款计划查询接口新增账单各科目应还已还未还字段",
    "risk" : "联调依赖申城/通汇配合与外部环境；罚息/提前结清手续费是否输出未还未定稿会影响文档与验收用例。"
  } ],
  "totalEstimatedEffort" : "12h",
  "developmentEstimatedEffort" : "12h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-14T21:40:35.984556",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### 临时分析 · 调整研发任务 · 需求 277

<!-- analysis-record:d1a189a5882215ae7274cf1dec4a7d9728a99a3684d673d70aaaf2afc23330ae -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：121
- 记录时间：2026-11-14T22:50:30.272163
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/4112d861-a2f6-4960-8fc5-bd6d74cdc3aa/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/4112d861-a2f6-4960-8fc5-bd6d74cdc3aa/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：仅改造 scs-saps，在现有【还款计划查询】接口（/api/repayment/plan/query，RepayPlanApiController→RepaymentPlanServiceImpl.queryRepaymentPlanListApi→RepaymentPlanApiVo）响应中新增每期科目级应还/已还/未还字段（本金、资金方/合作方/技术服务方/业务指定方服务费、资产管理费、罚息、提前结清手续费，共22个必填字段，无金额科目传0），数据源复用 bill_fee_detail 的 total_amount/paid_amount/unpaid_amount 与管理端成熟的科目汇总口径（含红冲、costAmount、申城医美产品特殊逻辑）。共拆分 2 个研发任务（接口字段开发 8h、文档联调与存量调用方回归 4h）。金额单位与精度、罚息科目口径两处需与申城/通汇对齐；本次未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "【还款计划查询】接口（/api/repayment/plan/query）响应新增每期科目级实时数值字段：应还/已还/未还本金、应还/已还/未还服务费（资金方、合作方、技术服务方、业务指定方）、应还/已还/未还资产管理费。", "同接口新增罚息、提前结清手续费的应还/已还实时数值字段（PDF字段清单未列未还，需求描述文字含未还，口径待确认）。", "所有新增字段必填，按订单产品规则无金额科目一律传0（如本项目技术服务费费率为0）。", "数值为合作方查询时点账单实时值，需覆盖正常还款、部分还款、提前结清、代偿/回购、退费等结算场景后的科目状态。", "满足通汇同步账单明细数据（每期各科目应收与最终实收）的数据获取需要，不新增同步任务，仅扩展现有查询接口。", "【还款计划查询】接口响应新增每期科目级实时数值字段：应还/已还/未还本金、四类服务费、资产管理费，及罚息、提前结清手续费应还/已还；无金额科目传0，覆盖各结算场景。", "满足通汇同步账单明细数据（每期各科目应收与最终实收）的获取；新增字段必填、无数值科目传0；数值覆盖各结算场景。" ],
  "impactedModules" : [ "scs-saps/saps-controller/APIs/RepayPlanApiController（/api/repayment/plan/query）", "scs-saps/saps-model/vo/APIS/RepaymentPlanApiVo", "scs-saps/saps-service/RepaymentPlanServiceImpl#queryRepaymentPlanListApi", "scs-saps bill_fee_detail 科目金额数据口径", "scs-saps/saps-model/enums/BillSubjectNoEnum 科目映射", "合作方接口文档与申城/通汇联调" ],
  "risks" : [ "RepaymentPlanApiVo 现有金额字段为 Integer，而 bill_fee_detail 为 BigDecimal：新增字段的类型、金额单位（元/分）与小数位必须与申城/通汇文档对齐，否则对账口径错误。", "申城代偿/回购线上还款不落 repayment_plan_pay_subject_detail（管理端回退到 statement_charge_resource 展示），科目'已还'依赖 bill_fee_detail.paid_amount 的更新链路，该场景数值需专项验证。", "该接口被 FunderBillSyncScheduleBizServiceImpl 定时同步及 scs-payment(SapsApi)、scs-capital/scs-order(SapsClient) 内部客户端消费，新增字段需保证 JSON 兼容、不破坏存量反序列化与定时任务。", "管理端账单列表存在申城医美产品特殊口径（isThPrdZmycA002、AssetManagementFeeAmount、资方服务费 costAmount 占用、红冲 reverse 明细），接口字段若不复用同一口径会出现接口与页面数值不一致。", "罚息/提前结清手续费是否输出'未还'在 PDF 与需求描述间不一致，字段清单未定稿前存在返工风险。" ],
  "questions" : [ "罚息与提前结清手续费是否需要返回'未还'字段？PDF 字段清单仅列应还/已还两项，需求描述文字为'应还、已还、未还'，两处不一致，直接影响字段清单与验收。", "'罚息'口径是否仅指对客罚息（P0007）？系统还存在对资罚息（P0009，代偿场景计收），是否需要一并返回。", "新增字段的英文命名、金额单位（元/分）与精度需与申城/通汇接口文档对齐定稿（现有 VO 金额为 Integer，科目数据为 BigDecimal）。" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scs-saps 还款计划查询接口新增账单各科目应还已还未还字段",
    "description" : "在现有【还款计划查询】接口 /api/repayment/plan/query 的响应对象 RepaymentPlanApiVo 中新增22个科目级必填字段：应还/已还/未还×（本金、资金方服务费、合作方服务费、技术服务方服务费、业务指定方服务费、资产管理费）+ 罚息应还/已还 + 提前结清手续费应还/已还；在 RepaymentPlanServiceImpl.queryRepaymentPlanListApi 中按账单号批量查询 bill_fee_detail，按 BillSubjectNoEnum 科目映射（P0001本金、P0003资金方、P0004技术服务、P0005合作方、P0006提前结清手续费、P0007罚息、P0008业务指定方、P0012资产管理费）汇总 total_amount/paid_amount/unpaid_amount 实时填充，无金额科目置0；红冲、costAmount 及申城医美产品特殊口径与管理端 queryList 保持一致；补齐各还款场景单测。",
    "requirementChangePoint" : "【还款计划查询】接口响应新增每期科目级实时数值字段：应还/已还/未还本金、四类服务费、资产管理费，及罚息、提前结清手续费应还/已还；无金额科目传0，覆盖各结算场景。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/APIS/RepaymentPlanApiVo.java", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java", "scs-saps/saps-service/src/main/java/com/amp/saps/service/RepaymentPlanService.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/BillSubjectNoEnum.java" ],
    "changePoints" : [ "saps-model/vo/APIS/RepaymentPlanApiVo.java：新增22个科目字段（BigDecimal，命名与单位以定稿文档为准），保留存量字段不动。", "saps-service/RepaymentPlanServiceImpl#queryRepaymentPlanListApi（约1932行）：在现有还款计划返回组装处，按 billNos 批量查询 bill_fee_detail（is_delete=0、is_cancel=0）。", "按 BillSubjectNoEnum 科目映射（P0001/P0003/P0004/P0005/P0006/P0007/P0008/P0012）分组汇总 total_amount/paid_amount/unpaid_amount 填充 VO，缺失科目统一补0。", "科目口径对齐管理端 queryList/AssetManagementFeeAmount：红冲 reverse 明细、资方服务费 costAmount 占用、申城医美产品 isThPrdZmycA002 特殊处理保持一致。", "单测/自测：正常还款、部分还款、提前结清、代偿/回购（科目已还走 bill_fee_detail 更新链路）、退费场景数值正确性与0值兜底。" ],
    "systemTags" : [ "scs-saps" ],
    "evidenceRefs" : [ "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/RepayPlanApiController.java:43（/api/repayment/plan/query 接口入口，orgCode 鉴权）", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java:1932（queryRepaymentPlanListApi 现有实现，经 mapper.queryRepaymentPlanList 查询）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/APIS/RepaymentPlanApiVo.java（现有响应VO仅有总应还/已还/未还，无科目字段）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/BillSubjectNoEnum.java（P0001本金/P0003资方/P0004平台即技术服务费/P0005合作方/P0006提前结清/P0007罚息/P0008业务指定方/P0012资产管理费）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/BillFeeDetailEntity.java（bill_fee_detail 含 subjectNo/totalAmount/paidAmount/unpaidAmount/redAmount/reverse）", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java:1069（管理端 queryList 科目汇总可复用口径）及 2774（AssetManagementFeeAmount）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/RepaymentPlanVo.java（管理端已具备全部科目级应还/已还/未还字段，字段命名可参照）", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/manage/bill/RepaymentPlanPaySubjectDetailController.java（申城代偿/回购回退 statement_charge_resource 展示逻辑，验证科目已还口径）", "附件：申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf（22个新增字段清单）", "scs-saps/docs/raw/feature/am_fee_recon_202608260001/deal/260830_申城医美_智融诚泰再保理需求整理.md（科目与主体口径：技术服务费0、资产管理费月结不入客户应还）" ],
    "confidence" : "MEDIUM",
    "moduleName" : "还款计划查询接口",
    "relatedFiles" : [ "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/APIS/RepaymentPlanApiVo.java", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/RepayPlanApiController.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/BillFeeDetailEntity.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/BillSubjectNoEnum.java" ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "shihao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : "金额单位/精度与罚息科目口径未定稿即开发，可能返工；代偿/回购场景科目已还数值需专项验证；需回归存量内部调用方。"
  }, {
    "title" : "scs-saps 还款计划查询接口科目字段文档定稿与联调回归",
    "description" : "更新合作方《还款计划查询》接口文档，明确22个新增字段的英文名、必填、金额单位与精度、无金额科目传0规则；在测试环境与申城/通汇联调，验证每期各科目应还/已还/未还实时数值与账单实际结算一致；回归存量调用方（FunderBillSyncSchedule 定时同步、scs-payment SapsApi、scs-capital/scs-order SapsClient）确认新增字段不影响反序列化与既有流程。",
    "requirementChangePoint" : "满足通汇同步账单明细数据（每期各科目应收与最终实收）的获取；新增字段必填、无数值科目传0；数值覆盖各结算场景。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "合作方接口文档（还款计划查询）", "scs-saps/saps-business/src/main/java/com/amp/saps/business/impl/FunderBillSyncScheduleBizServiceImpl.java", "scs-payment/scs-payment-api/src/main/java/com/scs/payment/api/service/SapsApi.java", "scs-capital/scs-capital-api/src/main/java/com/scs/capital/api/SapsClient.java", "scs-order/scs-order-api/src/main/java/com/thchengtay/order/api/SapsClient.java" ],
    "changePoints" : [ "接口文档：新增字段清单（字段名/释义/必填/单位/精度/零值规则）定稿并同步申城/通汇。", "测试环境联调：按申请单号查询验证每期科目实时数值，覆盖正常、部分还款、提前结清、代偿/回购、退费场景及0值科目（如技术服务费）。", "存量调用方回归：FunderBillSyncScheduleBizServiceImpl 定时同步（/saps/api/repayment/plan/query 自调用）及 scs-payment/scs-capital/scs-order 客户端反序列化兼容性验证。" ],
    "systemTags" : [ "scs-saps" ],
    "evidenceRefs" : [ "scs-saps/saps-business/src/main/java/com/amp/saps/business/impl/FunderBillSyncScheduleBizServiceImpl.java:71（存量自调用 /saps/api/repayment/plan/query 消费 RepaymentPlanApiVo）", "scs-payment/scs-payment-api/src/main/java/com/scs/payment/api/service/SapsApi.java:28（内部消费方）", "scs-capital/scs-capital-api/src/main/java/com/scs/capital/api/SapsClient.java:34、scs-order/scs-order-api/src/main/java/com/thchengtay/order/api/SapsClient.java:32（内部消费方）", "附件：申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf（联调验收字段依据）" ],
    "confidence" : "MEDIUM",
    "moduleName" : "接口联调与文档",
    "relatedFiles" : [ "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/RepayPlanApiController.java", "scs-saps/saps-business/src/main/java/com/amp/saps/business/impl/FunderBillSyncScheduleBizServiceImpl.java", "scs-payment/scs-payment-api/src/main/java/com/scs/payment/api/service/SapsApi.java", "scs-capital/scs-capital-api/src/main/java/com/scs/capital/api/SapsClient.java", "scs-order/scs-order-api/src/main/java/com/thchengtay/order/api/SapsClient.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "shihao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "scs-saps 还款计划查询接口新增账单各科目应还已还未还字段",
    "risk" : "联调依赖申城/通汇配合与外部环境；罚息/提前结清手续费是否输出未还未定稿会影响文档与验收用例。"
  } ],
  "totalEstimatedEffort" : "12h",
  "developmentEstimatedEffort" : "12h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-14T21:40:35.984556",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### 临时分析 · 调整研发任务 · 需求 277

<!-- analysis-record:de90929fb46518e80b59069ad54069aed87ddaf8ad3b1681402e77c129ee9c50 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：121
- 记录时间：2026-11-14T22:50:45.630257
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/4112d861-a2f6-4960-8fc5-bd6d74cdc3aa/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "新消费分期",
  "projectId" : 46,
  "projectName" : "申城-医美-诚泰保理",
  "repositoryUrl" : "scs (11 repositories)",
  "requirementRawPath" : "requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611050003/历史版本/4112d861-a2f6-4960-8fc5-bd6d74cdc3aa/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：仅改造 scs-saps，在现有【还款计划查询】接口（/api/repayment/plan/query，RepayPlanApiController→RepaymentPlanServiceImpl.queryRepaymentPlanListApi→RepaymentPlanApiVo）响应中新增每期科目级应还/已还/未还字段（本金、资金方/合作方/技术服务方/业务指定方服务费、资产管理费、罚息、提前结清手续费，共22个必填字段，无金额科目传0），数据源复用 bill_fee_detail 的 total_amount/paid_amount/unpaid_amount 与管理端成熟的科目汇总口径（含红冲、costAmount、申城医美产品特殊逻辑）。共拆分 2 个研发任务（接口字段开发 8h、文档联调与存量调用方回归 4h）。金额单位与精度、罚息科目口径两处需与申城/通汇对齐；本次未修改代码、未创建工作项。",
  "requirementChangePoints" : [ "【还款计划查询】接口（/api/repayment/plan/query）响应新增每期科目级实时数值字段：应还/已还/未还本金、应还/已还/未还服务费（资金方、合作方、技术服务方、业务指定方）、应还/已还/未还资产管理费。", "同接口新增罚息、提前结清手续费的应还/已还实时数值字段（PDF字段清单未列未还，需求描述文字含未还，口径待确认）。", "所有新增字段必填，按订单产品规则无金额科目一律传0（如本项目技术服务费费率为0）。", "数值为合作方查询时点账单实时值，需覆盖正常还款、部分还款、提前结清、代偿/回购、退费等结算场景后的科目状态。", "满足通汇同步账单明细数据（每期各科目应收与最终实收）的数据获取需要，不新增同步任务，仅扩展现有查询接口。", "【还款计划查询】接口响应新增每期科目级实时数值字段：应还/已还/未还本金、四类服务费、资产管理费，及罚息、提前结清手续费应还/已还；无金额科目传0，覆盖各结算场景。", "满足通汇同步账单明细数据（每期各科目应收与最终实收）的获取；新增字段必填、无数值科目传0；数值覆盖各结算场景。" ],
  "impactedModules" : [ "scs-saps/saps-controller/APIs/RepayPlanApiController（/api/repayment/plan/query）", "scs-saps/saps-model/vo/APIS/RepaymentPlanApiVo", "scs-saps/saps-service/RepaymentPlanServiceImpl#queryRepaymentPlanListApi", "scs-saps bill_fee_detail 科目金额数据口径", "scs-saps/saps-model/enums/BillSubjectNoEnum 科目映射", "合作方接口文档与申城/通汇联调" ],
  "risks" : [ "RepaymentPlanApiVo 现有金额字段为 Integer，而 bill_fee_detail 为 BigDecimal：新增字段的类型、金额单位（元/分）与小数位必须与申城/通汇文档对齐，否则对账口径错误。", "申城代偿/回购线上还款不落 repayment_plan_pay_subject_detail（管理端回退到 statement_charge_resource 展示），科目'已还'依赖 bill_fee_detail.paid_amount 的更新链路，该场景数值需专项验证。", "该接口被 FunderBillSyncScheduleBizServiceImpl 定时同步及 scs-payment(SapsApi)、scs-capital/scs-order(SapsClient) 内部客户端消费，新增字段需保证 JSON 兼容、不破坏存量反序列化与定时任务。", "管理端账单列表存在申城医美产品特殊口径（isThPrdZmycA002、AssetManagementFeeAmount、资方服务费 costAmount 占用、红冲 reverse 明细），接口字段若不复用同一口径会出现接口与页面数值不一致。", "罚息/提前结清手续费是否输出'未还'在 PDF 与需求描述间不一致，字段清单未定稿前存在返工风险。" ],
  "questions" : [ "罚息与提前结清手续费是否需要返回'未还'字段？PDF 字段清单仅列应还/已还两项，需求描述文字为'应还、已还、未还'，两处不一致，直接影响字段清单与验收。", "'罚息'口径是否仅指对客罚息（P0007）？系统还存在对资罚息（P0009，代偿场景计收），是否需要一并返回。", "新增字段的英文命名、金额单位（元/分）与精度需与申城/通汇接口文档对齐定稿（现有 VO 金额为 Integer，科目数据为 BigDecimal）。" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scs-saps 还款计划查询接口新增账单各科目应还已还未还字段",
    "description" : "在现有【还款计划查询】接口 /api/repayment/plan/query 的响应对象 RepaymentPlanApiVo 中新增22个科目级必填字段：应还/已还/未还×（本金、资金方服务费、合作方服务费、技术服务方服务费、业务指定方服务费、资产管理费）+ 罚息应还/已还 + 提前结清手续费应还/已还；在 RepaymentPlanServiceImpl.queryRepaymentPlanListApi 中按账单号批量查询 bill_fee_detail，按 BillSubjectNoEnum 科目映射（P0001本金、P0003资金方、P0004技术服务、P0005合作方、P0006提前结清手续费、P0007罚息、P0008业务指定方、P0012资产管理费）汇总 total_amount/paid_amount/unpaid_amount 实时填充，无金额科目置0；红冲、costAmount 及申城医美产品特殊口径与管理端 queryList 保持一致；补齐各还款场景单测。",
    "requirementChangePoint" : "【还款计划查询】接口响应新增每期科目级实时数值字段：应还/已还/未还本金、四类服务费、资产管理费，及罚息、提前结清手续费应还/已还；无金额科目传0，覆盖各结算场景。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/APIS/RepaymentPlanApiVo.java", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java", "scs-saps/saps-service/src/main/java/com/amp/saps/service/RepaymentPlanService.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/BillSubjectNoEnum.java" ],
    "changePoints" : [ "saps-model/vo/APIS/RepaymentPlanApiVo.java：新增22个科目字段（BigDecimal，命名与单位以定稿文档为准），保留存量字段不动。", "saps-service/RepaymentPlanServiceImpl#queryRepaymentPlanListApi（约1932行）：在现有还款计划返回组装处，按 billNos 批量查询 bill_fee_detail（is_delete=0、is_cancel=0）。", "按 BillSubjectNoEnum 科目映射（P0001/P0003/P0004/P0005/P0006/P0007/P0008/P0012）分组汇总 total_amount/paid_amount/unpaid_amount 填充 VO，缺失科目统一补0。", "科目口径对齐管理端 queryList/AssetManagementFeeAmount：红冲 reverse 明细、资方服务费 costAmount 占用、申城医美产品 isThPrdZmycA002 特殊处理保持一致。", "单测/自测：正常还款、部分还款、提前结清、代偿/回购（科目已还走 bill_fee_detail 更新链路）、退费场景数值正确性与0值兜底。" ],
    "systemTags" : [ "scs-saps" ],
    "evidenceRefs" : [ "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/RepayPlanApiController.java:43（/api/repayment/plan/query 接口入口，orgCode 鉴权）", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java:1932（queryRepaymentPlanListApi 现有实现，经 mapper.queryRepaymentPlanList 查询）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/APIS/RepaymentPlanApiVo.java（现有响应VO仅有总应还/已还/未还，无科目字段）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/BillSubjectNoEnum.java（P0001本金/P0003资方/P0004平台即技术服务费/P0005合作方/P0006提前结清/P0007罚息/P0008业务指定方/P0012资产管理费）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/BillFeeDetailEntity.java（bill_fee_detail 含 subjectNo/totalAmount/paidAmount/unpaidAmount/redAmount/reverse）", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java:1069（管理端 queryList 科目汇总可复用口径）及 2774（AssetManagementFeeAmount）", "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/RepaymentPlanVo.java（管理端已具备全部科目级应还/已还/未还字段，字段命名可参照）", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/manage/bill/RepaymentPlanPaySubjectDetailController.java（申城代偿/回购回退 statement_charge_resource 展示逻辑，验证科目已还口径）", "附件：申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf（22个新增字段清单）", "scs-saps/docs/raw/feature/am_fee_recon_202608260001/deal/260830_申城医美_智融诚泰再保理需求整理.md（科目与主体口径：技术服务费0、资产管理费月结不入客户应还）" ],
    "confidence" : "MEDIUM",
    "moduleName" : "还款计划查询接口",
    "relatedFiles" : [ "scs-saps/saps-model/src/main/java/com/amp/saps/model/vo/APIS/RepaymentPlanApiVo.java", "scs-saps/saps-service/src/main/java/com/amp/saps/service/impl/RepaymentPlanServiceImpl.java", "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/RepayPlanApiController.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/entity/BillFeeDetailEntity.java", "scs-saps/saps-model/src/main/java/com/amp/saps/model/enums/BillSubjectNoEnum.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "shihao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : "金额单位/精度与罚息科目口径未定稿即开发，可能返工；代偿/回购场景科目已还数值需专项验证；需回归存量内部调用方。"
  }, {
    "title" : "scs-saps 还款计划查询接口科目字段文档定稿与联调回归",
    "description" : "更新合作方《还款计划查询》接口文档，明确22个新增字段的英文名、必填、金额单位与精度、无金额科目传0规则；在测试环境与申城/通汇联调，验证每期各科目应还/已还/未还实时数值与账单实际结算一致；回归存量调用方（FunderBillSyncSchedule 定时同步、scs-payment SapsApi、scs-capital/scs-order SapsClient）确认新增字段不影响反序列化与既有流程。",
    "requirementChangePoint" : "满足通汇同步账单明细数据（每期各科目应收与最终实收）的获取；新增字段必填、无数值科目传0；数值覆盖各结算场景。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "合作方接口文档（还款计划查询）", "scs-saps/saps-business/src/main/java/com/amp/saps/business/impl/FunderBillSyncScheduleBizServiceImpl.java", "scs-payment/scs-payment-api/src/main/java/com/scs/payment/api/service/SapsApi.java", "scs-capital/scs-capital-api/src/main/java/com/scs/capital/api/SapsClient.java", "scs-order/scs-order-api/src/main/java/com/thchengtay/order/api/SapsClient.java" ],
    "changePoints" : [ "接口文档：新增字段清单（字段名/释义/必填/单位/精度/零值规则）定稿并同步申城/通汇。", "测试环境联调：按申请单号查询验证每期科目实时数值，覆盖正常、部分还款、提前结清、代偿/回购、退费场景及0值科目（如技术服务费）。", "存量调用方回归：FunderBillSyncScheduleBizServiceImpl 定时同步（/saps/api/repayment/plan/query 自调用）及 scs-payment/scs-capital/scs-order 客户端反序列化兼容性验证。" ],
    "systemTags" : [ "scs-saps" ],
    "evidenceRefs" : [ "scs-saps/saps-business/src/main/java/com/amp/saps/business/impl/FunderBillSyncScheduleBizServiceImpl.java:71（存量自调用 /saps/api/repayment/plan/query 消费 RepaymentPlanApiVo）", "scs-payment/scs-payment-api/src/main/java/com/scs/payment/api/service/SapsApi.java:28（内部消费方）", "scs-capital/scs-capital-api/src/main/java/com/scs/capital/api/SapsClient.java:34、scs-order/scs-order-api/src/main/java/com/thchengtay/order/api/SapsClient.java:32（内部消费方）", "附件：申城资管平台—【还款计划查询】接口增加科目字段20261106.pdf（联调验收字段依据）" ],
    "confidence" : "MEDIUM",
    "moduleName" : "接口联调与文档",
    "relatedFiles" : [ "scs-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/RepayPlanApiController.java", "scs-saps/saps-business/src/main/java/com/amp/saps/business/impl/FunderBillSyncScheduleBizServiceImpl.java", "scs-payment/scs-payment-api/src/main/java/com/scs/payment/api/service/SapsApi.java", "scs-capital/scs-capital-api/src/main/java/com/scs/capital/api/SapsClient.java", "scs-order/scs-order-api/src/main/java/com/thchengtay/order/api/SapsClient.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "shihao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "scs-saps 还款计划查询接口新增账单各科目应还已还未还字段",
    "risk" : "联调依赖申城/通汇配合与外部环境；罚息/提前结清手续费是否输出未还未定稿会影响文档与验收用例。"
  } ],
  "totalEstimatedEffort" : "12h",
  "developmentEstimatedEffort" : "8h",
  "testingEstimatedEffort" : "4h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-14T21:40:35.984556",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null
}
```



### 临时分析 · 生成需求澄清项 · 需求 279

<!-- analysis-record:e6acfb33022c7572ba5d3922f6bd88e3ee39a08b9be2ccd2fa30e30b05b7b803 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：49
- 记录时间：2026-11-17T14:06:14.003153
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/新消费分期/申城-医美-诚泰保理/2026/202611120004/历史版本/ed5b3477-c664-4d5c-bd82-caa93ab9551b/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "华美项目是否保留名义融担方（陕西瑞恒永欣/DBF000082）",
  "description" : "需求PDF正文称除『替换直接资金方、诚泰向华美收款账户放款、合同套系调整』三点外，交互流程、产品规则、费用科目、费率均与现有项目保持不变；但附件要素表又写明『融担公司：无』『本项目没有融担公司，没有担保费』。而现有【申城-医美-诚泰再保理】项目实际配置了名义融担方（仅形式担保），且多处实现依赖该主体：放款校验硬性要求融担编码 DBF000082、试算一致性校验『名义担保费=技术服务费+合作方服务费』、贴息第二笔放款金额构成=资金方服务费+担保费、进件合同清单含221委托担保合同/242连带责任保证担保确认函。请明确一个口径：华美项目沿用名义融担方（与现有项目一致，仅资金方换成华美），还是真正去掉融担方（后者需同步调整放款校验、名义担保费口径与合同清单）。",
  "evidence" : "已查证：①需求PDF概述章节（仅列3处变化、其余不变）与要素表『融担公司：无/没有担保费』两处口径冲突；②现有项目口径：scs-order/docs/assets/database/DML/20260902_sc_reuse_old_project_product.dml.sql 及 issue 文档 reuse_old_sc_project_product_v0.1.md（『融担仍为 DBF000082』）、scs-capital/docs/申城-诚泰保理项目需求方案20260723.md（融担=陕西瑞恒永欣，仅形式担保）、docs/申城-诚泰保理项目-付息、贴息产品要素及测算表-IRR-Markdown表格版.md:18（列瑞恒永欣）；③代码依赖：scs-capital/scs-capital-service/.../algorithm/shencheng/ShenChengLoanRules.java:26-31（GUARANTOR_CODE=DBF000011 同级硬编码 GUARANTOR_CODE=DBF000082，validate() 编码不匹配即拒单）、ShenChengCalculationApplicationService.validateScheduleCommon（名义担保费=repayXxtFee+repayGeexFee）、http/loan-confirm-payment-sc-a001.http（合同221/242为必传附件）。综合后仍无法确定唯一答案。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "『单一用户额度上限10万元』是否跨智融/华美两个项目合并计算",
  "description" : "新增华美项目后，申城医美资产将同时存在两个平行项目（现有项目直接资金方=智融、新项目=华美），客群同为申城医美用户。需求正文与要素表均只写『单一用户额度上限10万元、允许同一用户同时多笔在贷订单（额度范围内即可）』，未说明用户在两个项目间的在贷余额是否合并占用10万额度。该口径直接决定进件额度校验行为（例如用户在智融项目已有10万在贷时，华美项目能否再放款），且业务明确未来还会按此模式继续克隆新资金方项目，口径影响会持续放大。请确认：两项目各自独立10万，还是按证件号跨两项目合并共10万。",
  "evidence" : "已查证：①需求PDF、要素表、结构化字段均无跨项目额度表述（仅『单一用户额度上限10万元』）；②代码侧：scs-order ApplicationLoanMapper.xml getLoanData（customerLimitAmount=prj.person_loan_limit，项目级配置），同文件 getOnAuditAmount/getInTransitOrderAmount 的 projectId 均为可选过滤条件（两口径均可实现）；③实际聚合范围由 ApplicationLoanServiceImpl.java:1164-1475（validationCredit/getOnAuditAmount 调用处）决定，该文件超出只读工具20KB读取上限无法核实具体入参；④scs-order wiki（放款处理阶段.md）记载资金系统统计含『跨项目在贷余额』，平台具备跨项目聚合能力。现有证据无法推出唯一口径。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "申城放款链路多处硬编码旧项目/产品/参与方编码，新项目无法纯配置克隆，须逐一扩展代码",
  "description" : "需求按『新增项目+产品、规则不变』设计，但申城逻辑在代码中按固定编码分发：ShenChengLoanRules 硬编码 PROJECT_CODE=XXT-PRJ-SC-A、GUARANTOR_CODE=DBF000082、FUNDER_CODE=ZJF000011，validate() 对项目/合作方/融担/资金方编码不匹配直接拒单『申城项目或参与方编码不匹配』；ShenChengCalculationApplicationService 仅认 PRD-SC-A001/A002，其余产品编码抛『申城试算不支持产品』；SAPS 费用映射（Hzf000144SapsRepaymentPlanMappingStrategy、LoanOrderEquipService.pushRepaymentPlanEntitys）按产品编码分支，未命中的新编码会落入公共默认口径（贴息 refund*/资管费映射被盖错）。新项目/新产品编码确定后，上述位置必须同步改码，否则放款申请被拒或 SAPS 科目口径错误。",
  "evidence" : "scs-capital/scs-capital-service/.../algorithm/shencheng/ShenChengLoanRules.java:26-31 及 validate()（编码不匹配 reject）；.../shencheng/ShenChengCalculationApplicationService.java（PAID_INTEREST_PRODUCT/REBATE_PRODUCT 白名单，else 抛『申城试算不支持产品』）；.../saps/Hzf000144SapsRepaymentPlanMappingStrategy.java（仅 A001/A002 分支，其余静默返回）；docs/summary/申城-诚泰保理项目-放款代码评审报告20260802.md §3.3（pushRepaymentPlanEntitys 需按合作方+产品编码双重判断，否则公共 refund*=repay* 覆盖贴息口径）及 LoanOrderEquipServiceSapsMappingTest。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "本地仓库代码与 TEST 实测行为存在版本差，资金方编码与收款账户规则需先对齐基线",
  "description" : "当前工作区 ShenChengLoanRules.FUNDER_CODE 硬编码为 ZJF000011（诚泰旧直接资金方编码），而文档记录现行项目（智融再保理化后）申请单 funder_code=ZJF000016 且 2026-11-10 TEST 黑盒实测放款流程可走通——说明本地代码落后或领先于 TEST 部署版本。同时仓库内并存两套收款账户规则：贴息『商户+合作方双账户拆分』校验（validateRebate）与『收款人必须是直接资方』校验（LoanRecaccountServiceImpl，按 remitType 分支），黑盒实测口径为『打款合计=申请本金』。设计华美改造前必须先确认 TEST/生产实际生效版本与账户规则分支，避免基于过期代码出方案。",
  "evidence" : "ShenChengLoanRules.java:31（FUNDER_CODE=ZJF000011）；scs-order/docs/assets/project-summary/issue/loan_data_funder_fields_v0.1.md（测试库申城单 funder_code=ZJF000016）、reuse_old_sc_project_product_v0.1.md（资金方绑定智融 ZJF000016）；知识库 202610260004 黑盒测试.md（2026-11-10 TEST 实例等额校验通过，且明确『分支状态不能推断 TEST 部署版本』）；ShenChengLoanRules.validateRebate（贴息双账户）与 LoanRecaccountServiceImpl.loanRecaccountCheck（『收款人非直接资方』，remitType=20 时跳过）。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "scs-saps 结算/账单/退费/凭证按申城项目常量硬编码路由，新项目账单不会命中现有申城规则",
  "description" : "资管平台的申城专属逻辑（账单生成与科目归属、代偿回购后收款方=资管公司、结算与全额校验、退费、EAS 凭证推送等）全部以 ScCtFactoringConstants 的 PROJECT_CODE=XXT-PRJ-SC-A、PRODUCT_CODES=[PRD-SC-A001,PRD-SC-A002] 及固定主体编码/商户号分发，StatementMapper.xml 还直接写死『通汇诚泰』主体名。华美项目若只建主数据不扩展这些常量与判断，其账单将按存量通用规则结算，科目归属、资金方服务费/资产管理费月结对账单、分账与凭证口径都会偏离『与现有一致』的需求要求。",
  "evidence" : "scs-saps/saps-common/.../constants/ScCtFactoringConstants.java（项目/产品/主体/商户号全量常量）；使用点：saps-business/.../CustomerBillGenerateBizServiceImpl.java:633/650/1037/1135、settleV2/PaySettleCheckBizImpl.java:195、settleV2/PaySettleHelpBizImpl.java:434/477、repayment/SettleApplyCheckBizImpl.java、refund/RefundBizImpl.java、saps-service/.../BillFeeDetailServiceImpl.java:168、RepaymentPlanServiceImpl.java:1518/2778、EasVoucherPushBizImpl.java:135、CustomerOrderAsinfoImpl.java:148；saps-dao/.../mapper/StatementMapper.xml:560-722（写死通汇诚泰）；openspec sc-ct-factoring-202607240001/specs/sc-settlement-subject-routing/spec.md（按产品编号启用申城专属结算规则、存量产品行为不变）。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 3,
  "itemType" : "RISK",
  "title" : "scs-order 开户回调硬编码『通汇诚泰』账户名，华美项目主数据将被写错",
  "description" : "已知缺口：AmpProjectServiceImpl 开户回调把账户 account_name 写死为『通汇诚泰』，此前智融替换时仅手工改库内展示名、未修代码。华美项目直接资金方为华美商业保理（深圳）有限公司（收款账户为中信银行广州分行营业部 8110901012101292393），若不修复该回调或明确规避方案，新项目收款账户名会被错误写为通汇诚泰，破坏项目主数据与放款收款主体的一致性展示。",
  "evidence" : "scs-order/scs-order-service/.../impl/AmpProjectServiceImpl.java:504（『通汇诚泰』硬编码）；scs-order/docs/assets/project-summary/issue/reuse_old_sc_project_product_v0.1.md §3.3『开户回调仍把 account_name 写死为通汇诚泰（AmpProjectServiceImpl），属放款/主数据缺口，本次只改库内展示名』；本需求要素表华美收款账户信息。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 4,
  "itemType" : "RISK",
  "title" : "历史编码 XXT-PRJ-SC-B/PRD-SC-B001 已逻辑删除且部署脚本带防复活守卫，新项目编码须避开",
  "description" : "2026-09 申城再保理曾新建 XXT-PRJ-SC-B/PRD-SC-B001 后决策废弃（逻辑删除保留），现行 DML 幂等脚本对已存在编码（含已逻辑删除）一律跳过以防复活。新项目/新产品若复用 B 系编码，部署脚本会静默跳过 INSERT 造成配置缺失且不易察觉；须使用全新编码，并在脚本中对历史逻辑删除行做显式处理。",
  "evidence" : "scs-order/docs/assets/database/DML/20260901_sc_refactoring_project_party_v1.dml.sql（头注『重跑不得复活 XXT-PRJ-SC-B/PRD-SC-B001』）；20260902_sc_reuse_old_project_product.dml.sql（INSERT 改为『no/code 已存在即跳过（含已逻辑删除）』）；reuse_old_sc_project_product_v0.1.md §3.1/§4（B 项目/产品 is_delete=1、『禁止再为资管费新建 B 项目/产品』、幂等必须以业务键存在为准）。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 5,
  "itemType" : "RISK",
  "title" : "新项目为『配置克隆+代码扩展』混合改造，启用开关分散多库多表，漏配即整链快速失败",
  "description" : "除代码硬编码外，申城产品启用依赖大量分散配置：scs-order 产品配置（repaymentPlanVerification=HZF000144 缺失或不等直接拒单、repaymentPlanCalculation、plateformFeeRate=0、fpFeeRate 按期数、不得配 repayDateRule、绑卡校验、附件清单、进件属性、assetFeeRate 等）与资金方主数据/项目绑定（org_funding_party 新增华美、amp_project_funding direct=1 华美 + direct=0 诚泰间接）；scs-capital remit_config 按项目+产品+合作方三键配置；scs-saps 渠道/商户/科目配置（首信易商户号、P0003/P0004/P0005/P0012 归属）。任一漏配都会在进件或放款环节直接拒单，需求排期与联调需按完整清单推进。",
  "evidence" : "scs-capital/docs/summary/申城-诚泰保理项目-放款代码评审报告20260802.md §3.5『产品配置检查清单（amp-order 侧，是申城逻辑的启用开关）：repaymentPlanVerification=HZF000144 缺失或不等直接拒单』等；scs-capital/dbscript/DML/202609_remit_config_init.sql（remit_config 按 project_code+product_code+cooperation_code）；scs-order 20260901/20260902 DML（资金方主数据 org_funding_party/org_funding_party_indirect 与 amp_project_funding 绑定结构）；scs-saps/docs/backup/db/am_fee_recon_202608260001/DML/2026/V13_sc_ct_factoring_channel_config_dml.sql 及 dbscript/DML/2026/V1_interest_product_202609210003.sql（渠道/科目按项目落库）。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 调整研发任务 · 需求 257

<!-- analysis-record:762004c49602b49a18259ac44663a0351a29b58126a721912d026f5996bc7b94 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：103
- 记录时间：2026-11-17T14:55:53.495371
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610230003/历史版本/3cc48e7c-9e84-4ffa-83e9-23854e168b02/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202610230003/历史版本/3cc48e7c-9e84-4ffa-83e9-23854e168b02/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "恒昌项目新增二维码扫码还款功能，业务系统通过“恒昌-中金商户号”生成还款二维码并推送客户完成还款，扣款成功后按固定比例清分；还款台账和资方账单页面需区分并展示二维码还款类型。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "cairui" ],
  "workItems" : [ {
    "title" : "研发",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "cairui",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "16h",
  "developmentEstimatedEffort" : "8h",
  "testingEstimatedEffort" : "8h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-17T14:55:53.067452",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 283

<!-- analysis-record:e55028d3f182fa743541771e63af86cea6da195fc0f6895f6812dc1bb41348af -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：123
- 记录时间：2026-11-18T10:55:09.303422
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000005/BL000005/2026/202608310002/历史版本/b5c3c6f3-fbce-43c1-94d0-69060a190b53/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000005",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/BL000005/BL000005/2026/202608310002/历史版本/b5c3c6f3-fbce-43c1-94d0-69060a190b53/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "先行通设备科技平台新增客户端小程序“小猪先行”（一期），设计主色调为先行通品牌色（黄色）。覆盖先行通平台现有及未来所有项目，叉车租售、叉车融资租赁项目除外。主要内容包括：1）首页按是否扫展业/营销码及历史归属渠道数量展示对应渠道商品页或宣传页；2）注册/登录改造，新增微信手机号快捷登录，协议文本待法务提供；3）【订单】【账单】【我的】等功能按现有H5实现，优利卡项目对客合同不在【我的合同】展示；4）【购物车】支持多项目/多渠道设备展示；5）“先行通设备科技”公众号菜单调整；6）后台新增【业务需求】菜单，支持查询、需求处理及新增业务需求。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ ],
  "workItems" : [ ],
  "totalEstimatedEffort" : null,
  "developmentEstimatedEffort" : null,
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T10:55:08.623781",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 270

<!-- analysis-record:087d3906e9cffb2f1fe31cfb912e08d5285546a34c2bac9ce3d56d242e0696cf -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：107
- 记录时间：2026-11-18T11:25:33.021268
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/历史版本/0da047d3-430c-4dcd-afb0-c06910f4d372/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "保费分期",
  "projectId" : 21,
  "projectName" : "聚保融保费分期项目",
  "repositoryUrl" : "scf (13 repositories)",
  "requirementRawPath" : "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/历史版本/0da047d3-430c-4dcd-afb0-c06910f4d372/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "评估草稿：需求可复用现有保费补缴、线下入金识别、备付金账户和交行代付链路，预计涉及 scf-order、scf-saps、scf-gateway、scf-intf-common、insurance_admin，合计约 54h。未发现 scf-intf-zhonghua 承载聚保融补缴接口的代码证据，故不纳入改造范围。",
  "requirementChangePoints" : [ "备付金余额明细查询响应新增“订单号”：充值、对账单扣款返回空；首笔款扣款、首笔款退款及保费补缴返回客户订单号。", "新增聚保融保费补缴申请接口，接收渠道申请单号、客户订单号、补缴金额（分）、原因、打款保司摘要、补费方式和申请文件 fileKey。", "补缴申请必须先校验客户订单状态，再按补费方式处理；订单非放款成功时拒绝，备付金不足时拒绝并记录明确原因。", "支持“备付金补费”和“另行付款”两种入账方式：前者即时扣减渠道备付金，后者按补费业务单号和等额金额识别或人工确认入账。", "补缴款入账后自动调用交行银企直联向原订单保险公司账户打款，维护入账、打款状态、打款时间和摘要，且不得修改原订单金额、合同、账单及融资数据。", "新增保费补缴结果查询和异步结果通知，返回申请单号、补费业务单号、入账状态、打款状态、打款时间及摘要，并具备幂等和失败补偿能力。", "保费补缴记录增加审核结果、拒绝原因、补费方式和聚保融申请单号；自营存量及新增记录默认审核通过、补费方式为另行付款。", "后台保费补缴页面及导出增加审核结果、补费方式字段，并增加对应下拉检索项。", "文件上传能力增加“保费补缴申请文件”类型，并在申请接口中校验 fileKey 的存在性及文件类型。", "新增聚保融保费补缴申请、审核和结果查询接口。", "支持备付金补费、另行付款两种入账方式及入账状态更新。", "补缴入账后自动向保司打款并异步通知聚保融结果。", "备付金余额明细接口新增订单号返回字段及保费补缴交易类型。", "后台保费补缴记录增加审核结果、补费方式表头及检索项。", "文件类型新增“保费补缴申请文件”。" ],
  "impactedModules" : [ "scf-order", "scf-saps", "scf-gateway", "scf-intf-common", "insurance_admin" ],
  "risks" : [ "备付金扣减、补缴记录创建和入账回调跨系统执行，若缺少业务单号唯一约束、账户行锁或补偿机制，可能发生重复扣款、重复入账或账实不一致。", "现有保费补缴打款通过 CompletableFuture 异步触发，缺少可靠消息或明确的持久化调度证据；进程重启和调用超时可能导致已入账但未向保司打款。", "现有 application_premium_supplement 表没有渠道申请单号、审核结果、拒绝原因和补费方式，新增字段需兼容并回填自营存量记录。", "现有备付金交易枚举仅覆盖充值、对账单扣款、首笔款扣款和首笔款退款，补缴交易类型及客户订单号映射需同步所有查询、导出和对账使用方。", "结果通知地址、应答协议和补偿策略未在材料中给出；若仅依赖网关同步通知，可能出现打款成功但聚保融长期未获知结果。", "“放款成功”若严格限定 ApplicationStatus.LOAN_SUCCESS，订单进入 INSURANCE_NORMAL 等后续状态后将不能补费，需在开发前确认业务状态口径。", "文件类型编码及文件规格未明确，无法直接确定 oss.support.file-types 的配置值和验收数据。" ],
  "questions" : [ "审核拒绝后重新申请时，聚保融是否会复用原“申请单号”？若复用，接口幂等规则应允许拒绝记录生成新补费业务单；若不复用，则应强制申请单号全局唯一。", "“客户订单状态=放款成功”是仅允许 ApplicationStatus.LOAN_SUCCESS，还是也允许已放款后的 INSURANCE_NORMAL 等状态？", "请明确结果通知地址配置键、请求签名方式、成功应答格式、超时重试次数和最终失败后的人工补偿入口。", "“保费补缴申请文件”的 serviceType 编码、允许的文件后缀、大小及数量限制是什么？", "向保司打款失败、超时或退票时，是持续自动重试、转人工处理，还是需要向聚保融通知失败结果？当前需求返回字段只有已打款/未打款。" ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scf-order 新增聚保融保费补缴申请与结果查询能力",
    "description" : "扩展现有保费补缴业务单，承接聚保融申请、审核结果落库和主动结果查询；复用原订单渠道、运营商、保险公司和收款账户信息，但不更新原订单金额、合同、账单或融资数据。",
    "requirementChangePoint" : "新增聚保融保费补缴申请、审核和结果查询接口。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "application_premium_supplement", "聚保融保费补缴申请接口", "聚保融保费补缴结果查询接口", "保费补缴查询及导出模型" ],
    "changePoints" : [ "1. 追加 DDL/DML：增加渠道申请单号、审核结果、拒绝原因、补费方式、申请来源等字段及必要唯一索引；将自营存量记录回填为审核通过、另行付款。", "2. 新增聚保融申请和查询契约，完成金额分转元、枚举、必填项、fileKey、渠道归属及客户订单存在性校验。", "3. 按规定顺序校验订单状态和补费方式；无论通过或拒绝均生成可追溯记录并返回补费业务单号、审核结果及拒绝原因。", "4. 根据客户订单补全渠道、申请时间、运营商、保险公司和打款摘要；聚保融传入摘要优先，未传时保持为空。", "5. 以渠道机构与申请单号建立幂等边界，防止重复请求生成多笔有效补缴单，同时落实审核拒绝后重新申请规则。", "6. 增加结果查询权限隔离，只允许调用机构查询自身补缴记录，并返回入账、打款状态及打款信息。", "7. 增加接口、状态校验、金额转换、重复申请及“不修改原订单”回归测试。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/po/PremiumSupplement.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/enums/ApplicationStatus.java", "scf-order/dbscript/DDL/V11__202510_scf_selfoperation_20251016.ddl.sql", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order",
    "relatedFiles" : [ "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/po/PremiumSupplement.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementDTO.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/dbscript/DDL/V11__202510_scf_selfoperation_20251016.ddl.sql" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖“scf-intf-common 增加保费补缴申请文件上传类型”明确文件类型配置。",
    "risk" : "拒绝后重新申请的申请单号规则和可补费订单状态范围尚未确认，可能影响唯一索引与审核逻辑。",
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-saps 扩展保费补缴双方式入账与回调规则",
    "description" : "为补缴业务提供备付金即时扣款和另行付款识别入账能力，入账成功后可靠通知 scf-order 继续向保司打款。",
    "requirementChangePoint" : "支持备付金补费、另行付款两种入账方式及入账状态更新。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "备付金扣款接口", "account", "account_balance_record", "customer_order", "pend_transaction", "保费补缴入账回调" ],
    "changePoints" : [ "1. 新增保费补缴备付金扣款服务契约，以补费业务单号作为幂等键，并校验渠道账户归属、启用状态和可用余额。", "2. 在同一事务内锁定备付金账户、扣减余额并写入补缴交易流水；余额不足时不扣款，返回可映射为“备付金余额不足”的业务结果。", "3. 为另行付款复用现有 PREMIUM 线下订单及挂账匹配逻辑，严格按补费业务单号、渠道和等额金额匹配，避免仅按金额误匹配。", "4. 兼容系统识别入账和人工手动入账，两条路径均统一推进补缴入账状态，且重复清分不重复结算。", "5. 入账事务提交后调用 scf-order 的 positing/completed 能力；失败时落持久化补偿记录并由稳定调度重试。", "6. 覆盖并发扣款、余额不足、重复请求、重复银行流水、人工与自动入账竞争及回调失败补偿测试。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountService.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendAutoClearBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendClearingBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/strategy/payment/result/PremiumPaymentResultProcesser.java", "scf-saps/saps-api/src/main/java/com/amp/saps/api/service/OrderApiService.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-saps",
    "relatedFiles" : [ "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountService.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendAutoClearBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/pend/impl/PendClearingBizImpl.java", "scf-saps/saps-business/src/main/java/com/amp/saps/business/strategy/payment/result/PremiumPaymentResultProcesser.java", "scf-saps/saps-api/src/main/java/com/amp/saps/api/service/OrderApiService.java" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scf-order 先生成补费业务单并提供幂等入账回调接口。",
    "risk" : "资金扣减与跨系统回调无法形成单库事务，必须通过业务幂等和持久化补偿保证最终一致。",
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-order/scf-gateway 串联保费补缴打款保司与结果通知能力",
    "description" : "补缴款入账后复用现有交行代付链路向原订单保险公司打款，并在成功后通过统一网关主动通知聚保融。",
    "requirementChangePoint" : "补缴入账后自动向保司打款并异步通知聚保融结果。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "交行单笔代付", "保费补缴打款状态回调", "聚保融结果通知", "api_notify", "org_external_access.notify_url" ],
    "changePoints" : [ "1. 入账成功后幂等触发现有 generateToUnderwriterPaymentSlip，按原订单保司收款账户、补缴金额和渠道摘要生成独立代付单。", "2. 以补费业务单号控制打款幂等，持久化代付请求号、未打款/已打款状态、完成时间和摘要，禁止写回原订单金额科目。", "3. 处理同步异常、异步成功、失败、超时和退票状态；用持久化任务替代仅依赖进程内异步执行，并提供人工重试边界。", "4. 新增补缴结果通知业务类型及聚保融通知地址配置，通知载荷与主动查询返回字段保持一致。", "5. 复用 api_notify 或新增等价通知记录，记录通知中、成功、失败和重试次数；聚保融重复接收时以补费业务单号幂等。", "6. 验证入账成功仅打款一次、回调重复不重复通知、通知失败补偿及打款失败/退票处置。" ],
    "systemTags" : [ "scf-order", "scf-gateway" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/GatewayFeignClient.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/loan/impl/ApiNotifyServiceImpl.java", "scf-gateway/src/main/java/com/amp/gateway/controller/NotifyPartnerController.java", "scf-gateway/src/main/java/com/amp/gateway/enums/NotifyBusinessType.java" ],
    "confidence" : "MEDIUM",
    "moduleName" : "scf-order/scf-gateway",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/application/v2/PremiumSupplementService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/application/PremiumSupplementController.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/GatewayFeignClient.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/loan/ApiNotify.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/loan/impl/ApiNotifyServiceImpl.java", "scf-gateway/src/main/java/com/amp/gateway/controller/NotifyPartnerController.java", "scf-gateway/src/main/java/com/amp/gateway/enums/NotifyBusinessType.java" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖补缴申请记录和两种入账方式完成；联调前需取得聚保融通知协议及地址配置。",
    "risk" : "通知协议、重试规则和打款失败/退票终态未明确，当前可靠性方案只能按现有网关及 api_notify 能力估算。",
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-saps 补充备付金明细订单号与补缴交易类型",
    "description" : "扩展既有备付金余额明细接口，按交易类型返回客户订单号，并将保费补缴纳入明细查询。",
    "requirementChangePoint" : "备付金余额明细接口新增订单号返回字段及保费补缴交易类型。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/account/balance/record/query", "AccountTransactionTypeEnum", "account_balance_record.order_no" ],
    "changePoints" : [ "1. 扩展 AccountTransactionTypeEnum 和接口字段说明，增加保费补缴交易类型。", "2. 响应模型增加 orderNo，并在查询组装中读取 account_balance_record.order_no。", "3. 充值、对账单扣款返回空订单号；首笔款扣款、首笔款退款和保费补缴写入并返回客户订单号。", "4. 保持 sourceOrderNo 现有语义：补缴场景返回补费业务单号，其他交易类型保持原关联业务单号。", "5. 同步管理端查询/导出模型中受交易类型枚举影响的展示，并验证历史数据空值兼容。", "6. 增加五类交易明细映射、分页过滤和金额单位转换测试。" ],
    "systemTags" : [ "scf-saps" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-saps/saps-controller/src/main/java/com/amp/saps/controller/APIs/AccountApiController.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountApiService.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiResponse.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/entity/AccountBalanceRecordEntity.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/enums/AccountTransactionTypeEnum.java" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-saps",
    "relatedFiles" : [ "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountApiService.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiRequest.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/APIs/AccountBalanceRecordQueryApiResponse.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/entity/AccountBalanceRecordEntity.java", "scf-saps/saps-model/src/main/java/com/amp/saps/model/enums/AccountTransactionTypeEnum.java", "scf-saps/saps-service/src/main/java/com/amp/saps/service/impl/AccountBalanceRecordService.java" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "保费补缴交易流水由“双方式入账与回调规则”任务写入。",
    "risk" : "首笔款退款现有流水是否均已保存客户订单号需做数据抽样；历史记录可能只能返回空值。",
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "insurance_admin 增加保费补缴审核结果与补费方式检索展示",
    "description" : "在现有运营后台保费补缴页面和导出中展示审核结果、补费方式，并提供对应筛选项。",
    "requirementChangePoint" : "后台保费补缴记录增加审核结果、补费方式表头及检索项。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "保费补缴列表页", "保费补缴查询参数", "保费补缴导出模板", "审核结果与补费方式字典" ],
    "changePoints" : [ "1. 在检索区增加审核结果和补费方式下拉框，枚举分别为通过/拒绝、备付金补费/另行付款。", "2. 在表格增加审核结果、补费方式列，并兼容存量记录默认值。", "3. 后端分页查询增加 auditResult、paymentMethod 条件并同步 Mapper SQL。", "4. 更新 operator、channel、funder 三套保费补缴导出模板及填充模型，确保筛选条件与列表一致。", "5. 复核现有页面、导出和下载权限；本需求无新增操作按钮时不扩展写权限。", "6. 验证聚保融通过/拒绝记录、自营默认记录、组合筛选和导出字段。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "insurance_admin/src/views/order/premiumBackPayment/index.vue", "insurance_admin/src/api/api_list.js", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_operator.xlsx" ],
    "confidence" : "HIGH",
    "moduleName" : "insurance_admin",
    "relatedFiles" : [ "insurance_admin/src/views/order/premiumBackPayment/index.vue", "insurance_admin/src/api/api_list.js", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/application/dto/apply/PremiumSupplementPage.java", "scf-order/scf-order-dao/src/main/resources/com/thchengtay/order/mapper/application/PremiumSupplementMapper.xml", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_operator.xlsx", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_channel.xlsx", "scf-order/scf-order-start/src/main/resources/export/premium_supplement_funder.xlsx" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "依赖 scf-order 完成审核结果、补费方式字段及查询条件。",
    "risk" : "三套导出模板均需同步，遗漏任一角色模板会导致页面有字段而导出缺列。",
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-intf-common 增加保费补缴申请文件上传类型",
    "description" : "为聚保融通过既有文件上传接口取得补缴申请文件 fileKey 提供类型白名单，并支持 scf-order 回查文件信息。",
    "requirementChangePoint" : "文件类型新增“保费补缴申请文件”。",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "POST /api/file/fileUpload", "oss.support.file-types", "文件详情查询接口" ],
    "changePoints" : [ "1. 在各目标环境 oss.support.file-types 中新增保费补缴申请文件 serviceType 映射。", "2. 明确并配置允许的后缀、大小和数量规则，保持上传返回 fileKey 的既有契约。", "3. 在 scf-order 申请处理时通过文件详情接口校验 fileKey 存在、归属和文件类型，并解析后台下载所需 URL。", "4. 验证未知类型拒绝、合法文件上传、伪造/失效 fileKey 拒绝及后台下载。" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "requirement-sources/保费分期/聚保融保费分期项目/2026/202610190007/附件/489-保费分期-聚保融新增接口（保费补缴）、备付金余额明细查询接口新增反馈字段20261019.pdf", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/oss/OutwardController.java", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/config/FileTypeProperties.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java", "scf_channel_admin/src/views/order/components/PremiumSupplement.vue" ],
    "confidence" : "MEDIUM",
    "moduleName" : "scf-intf-common",
    "relatedFiles" : [ "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/oss/OutwardController.java", "scf-intf-common/intfcommon-controller/src/main/java/com/amp/intfcommon/controller/config/FileTypeProperties.java", "scf-order/scf-order-api/src/main/java/com/thchengtay/order/api/AmpIntfCommonClient.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "xujieni",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "需产品或接口方先明确 serviceType 编码和文件规格。",
    "risk" : "类型白名单位于外部配置中心，仓库内无当前环境配置值，发布时可能漏配某一环境。",
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "32h",
  "developmentEstimatedEffort" : "16h",
  "testingEstimatedEffort" : "16h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-05T11:09:19.945294",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 283

<!-- analysis-record:905aff14acab3616579372c1bd2642b84feb16e1245ffcb7bc086f9ee484b621 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：123
- 记录时间：2026-11-18T14:14:42.212589
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000005/BL000005/2026/202608310002/历史版本/b5c3c6f3-fbce-43c1-94d0-69060a190b53/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "智能柜",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/BL000005/BL000005/2026/202608310002/历史版本/b5c3c6f3-fbce-43c1-94d0-69060a190b53/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "先行通设备科技平台新增客户端小程序“小猪先行”（一期），设计主色调为先行通品牌色（黄色）。覆盖先行通平台现有及未来所有项目，叉车租售、叉车融资租赁项目除外。主要内容包括：1）首页按是否扫展业/营销码及历史归属渠道数量展示对应渠道商品页或宣传页；2）注册/登录改造，新增微信手机号快捷登录，协议文本待法务提供；3）【订单】【账单】【我的】等功能按现有H5实现，优利卡项目对客合同不在【我的合同】展示；4）【购物车】支持多项目/多渠道设备展示；5）“先行通设备科技”公众号菜单调整；6）后台新增【业务需求】菜单，支持查询、需求处理及新增业务需求。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "hanmeng" ],
  "workItems" : [ {
    "title" : "支付支持小程序终端类型",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "ol-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "支付结果通知发货通知",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "ol-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "24h",
  "developmentEstimatedEffort" : "16h",
  "testingEstimatedEffort" : "8h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T10:55:08.623781",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 生成研发评估 · 需求 271

<!-- analysis-record:b7817dbb226cf02372c49255e922ee432a4ab004d82b43d6fd07ddc1a4801f05 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：124
- 记录时间：2026-11-18T14:29:49.121093
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/保费分期/聚保融保费分期项目/2026/202611040003/历史版本/40af1c06-19e6-4335-847e-78dd0da92dc0/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "保费分期",
  "projectId" : 21,
  "projectName" : "聚保融保费分期项目",
  "repositoryUrl" : "scf (13 repositories)",
  "requirementRawPath" : "requirement-sources/保费分期/聚保融保费分期项目/2026/202611040003/历史版本/40af1c06-19e6-4335-847e-78dd0da92dc0/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "核心结论：系统已内建“资方服务费结算方式”能力（org_funding_party.funding_service_fee_settlement_method，1=差额放款/2=放款后支付，运营端资金方配置页可维护），生产库中化(ZJF000015)当前为'2'、测试库已为'1'。本需求主体是：生产将中化切换为差额放款后，融资单创建时loanAmount即=资方融资金额-资方服务费（FinanceApplicationService.financeHandle既有分支），放款到账校验（liquidationAmount vs loanAmount）随口径自动生效；中化放款结果通知/查询paymentAmt新值经scf-intf-zhonghua透传，DTO无需结构性改动；打款链路需全面下线（zhFinanceOverHandle与financeConfirm自动触发、PaymentFactoringFeeSchedule两个XXL-JOB、/api/adjustment手动接口）并按结算方式加防护；手续费不一致人工确认改为仅复写资方服务费；insurance_admin确认弹窗文案需去掉“并向资方支付”。合同推送/电签/回传链路无需改动（模板由中化调整）。需人工复核的新问题：1)上线时点存量“审批通过待放款/放款中”中化融资单loanAmount已按全额生成，切换后到账校验必然异常，需存量修正或人工确认兜底方案；2)在途finance_adjustment_plan调账打款需保留notifyPayResult回调以闭环；3)放款金额口径变化对报表与融资导出的影响未深入核查，建议复核；4)测试库中化配置已为'1'与生产不一致，需确认是否为预置变更；5)XXL-JOB停用需运维侧同步下线调度。",
  "requirementChangePoints" : [ "中化保理放款金额计算规则变更：放款金额=资方融资金额-资方服务费，资方融资金额（商业险垫资金额）与资方服务费（保理手续费）计算公式不变，通过将中化资金方服务费结算方式切换为差额放款实现", "中化接口传值调整：放款结果通知、放款结果查询接口中paymentAmt放款金额值=商业险垫资金额-保理手续费（中化侧传值变化，先行通侧透传解析与金额校验口径同步）", "先行通到账校验规则变更：应收到账金额=资方应放款金额=资方融资金额-资方服务费；中化推送保理手续费与系统按产品规则计算的资方服务费的一致性校验保持不变", "保理手续费不一致人工确认规则调整：标记异常与备注、人工弹窗输入确认值流程不变，确认后系统仅以人工填写数值复写资方服务费并重建资方还款计划，不再向中化保理打款", "取消先行通收款后向中化保理支付资方服务费的流程：放款成功自动触发、中化线下融资确认触发、XXL-JOB定时打款/轮询、手动补偿接口全面下线并对中化封禁", "合同调整：中化保理负责调整保理合同模板及合同金额内容，合同附件推送、先行通电签、回传流程保持不变（无系统改造，仅回归验证）" ],
  "impactedModules" : [ "scf-order/融资管理（融资单生成、放款结果通知、到账校验、人工确认）", "scf-order/保理手续费支付（调整计划AdjustmentPlan、XXL-JOB任务、手动补偿接口）", "scf-order/资金方配置（org_funding_party资方服务费结算方式）", "scf-intf-zhonghua/中化接口（放款结果通知与查询paymentAmt透传口径，无结构性改动）", "insurance_admin/融资管理页面（确认保理手续费弹窗文案、列表与导出口径）" ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scf-order 切换中化资方服务费结算方式为差额放款并适配放款金额计算",
    "description" : "将生产环境org_funding_party中中化(ZJF000015)的资方服务费结算方式由'2'放款后支付切换为'1'差额放款，复用financeHandle既有差额放款分支使融资单放款金额=资方融资金额-资方服务费，并制定存量进行中单据处理方案。生产库现值'2'、测试库已为'1'。",
    "requirementChangePoint" : "中化保理放款金额计算规则变更：放款金额=资方融资金额-资方服务费，资方融资金额（商业险垫资金额）与资方服务费（保理手续费）计算公式不变，通过将中化资金方服务费结算方式切换为差额放款实现",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "org_funding_party.funding_service_fee_settlement_method（ZJF000015）", "FinanceApplicationService.financeHandle/buildFinanceApplication", "dbscript DML脚本" ],
    "changePoints" : [ "配置/SQL：新增生产DML脚本将ZJF000015的funding_service_fee_settlement_method由'2'改为'1'（差额放款），与中化放款规则切换时点对齐", "接口/服务：复核financeHandle差额放款分支（loanAmount=financeAmount-serviceFee）在融资单创建、中化线下确认路径下的执行与落库时序", "幂等/验证：梳理上线时点存量'审批通过待放款/放款中'中化融资单（loanAmount已按全额生成）的修正或人工确认兜底方案" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceApplicationService.java:484-489（if(\"1\".equals(getFundingServiceFeeSettlementMethod()))差额放款赋值loanAmount）", "scf-order/dbscript/DDL/zh_payment_20250912.sql:26-28（org_funding_party含funding_service_fee_settlement_method列）", "insurance_admin/src/views/counterparty/Funding/components/configDia.vue:120-134（资方服务费结算方式：差额放款/放款后支付配置UI）", "只读DB查询：PROD scf_self_order.org_funding_party ZJF000015 funding_service_fee_settlement_method='2'；TEST同表='1'" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order/资金方配置与融资单生成",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceApplicationService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/organization/po/OrgFundingParty.java", "scf-order/dbscript/DDL/zh_payment_20250912.sql" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "lvliuhao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-order 调整中化放款到账校验规则为应收差额并同步接口金额校验",
    "description" : "放款结果通知与主动查询链路中，到账校验口径切换为应收到账金额=资方融资金额-资方服务费（loanAmount随差额放款配置自动切换），并核对中化paymentAmt新值（商业险垫资金额-保理手续费）的校验与落库口径。",
    "requirementChangePoint" : "中化接口传值调整：放款结果通知、放款结果查询接口中paymentAmt放款金额值=商业险垫资金额-保理手续费（中化侧传值变化，先行通侧透传解析与金额校验口径同步）",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "FinanceBusinessService.loanResultNotify/compareAmountAndPush2Saps", "FinanceApplicationCallbackApiController（/api/finance/loan/result、/api/finance/loan/query）", "FinanceLoanPaymentService.saveCapitalLoanPayment", "scf-intf-zhonghua LoanDetail（paymentAmt/serviceFee透传）" ],
    "changePoints" : [ "接口/服务：compareAmountAndPush2Saps到账校验按差额口径复核（liquidationAmount vs loanAmount），LOAN_AMOUNT_NOT_MATCH与SERVICE_FEE_NOT_MATCH及组合异常标记逻辑回归", "接口/服务：放款结果通知入口checkLoanDetail与confirmReceived主动查询路径，按新口径校验中化paymentAmt，必要时增加通知放款金额与应收差额的一致性校验", "幂等/验证：saveCapitalLoanPayment落库paymentAmt/realPaymentAmt与银行到账liquidationAmount金额口径一致性回归，确认分布式锁与重复通知幂等不受影响" ],
    "systemTags" : [ "scf-order", "scf-intf-zhonghua" ],
    "evidenceRefs" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java:105-210（loanResultNotify到账查询、compareAmountAndPush2Saps金额与手续费比对）", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/api/FinanceApplicationCallbackApiController.java:75-140（放款结果通知/查询入口及paymentAmt、serviceFee>0校验）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceLoanPaymentService.java:122-143（saveCapitalLoanPayment按中化通知金额落库）", "scf-intf-zhonghua/intfCapital-model/src/main/java/com/scf/intfCapital/dto/capital/zhonghua/resp/loan/LoanDetail.java（paymentAmt/serviceFee字段透传，无结构性改动）" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order/融资管理-放款结果处理",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/api/FinanceApplicationCallbackApiController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceLoanPaymentService.java", "scf-intf-zhonghua/intfCapital-service/src/main/java/com/scf/intfCapital/service/capital/zhonghua/LoanResultNotifyServiceImpl.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "lvliuhao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI1",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-order 调整保理手续费人工确认仅复写资方服务费不再打款",
    "description" : "保理手续费不一致人工确认（确认保理手续费、确认保理手续费并确认已融资收款）后，仅以人工填写值复写资方服务费并重建资方还款计划，不再触发向中化保理打款；异常标记与弹窗操作流程保持不变。",
    "requirementChangePoint" : "保理手续费不一致人工确认规则调整：标记异常与备注、人工弹窗输入确认值流程不变，确认后系统仅以人工填写数值复写资方服务费并重建资方还款计划，不再向中化保理打款",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "FinanceBusinessService.confirmServiceFee/confirmReceivedAndFee/zhFinanceOverHandle", "FinanceApplicationController（/manage/finance/confirm/serviceFee、/confirm/receivedAndFee）", "FinanceFundingRepaymentPlanService还款计划重建" ],
    "changePoints" : [ "接口/服务：confirmServiceFee/confirmReceivedAndFee确认后复写serviceFee、清异常、重建资方还款计划，按资金方服务费结算方式跳过adjustmentPlanService.executeAdjustmentPlan打款调用", "接口/服务：zhFinanceOverHandle中'触发一次保理手续费打款'调用条件化，保留SAPS动账结算、loanSuccess、合同下载与放款成功邮件", "幂等/验证：确认接口幂等（LOAN_SUCCESS直接返回）与复写后资方还款计划、推送SAPS数据一致性回归；FinanceAbnormalTypeEnum五种异常类型入口校验不变" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java:283-330（confirmServiceFee/confirmReceivedAndFee复写服务费后走zhFinanceOverHandle）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java:365-391（zhFinanceOverHandle内adjustmentPlanService.executeAdjustmentPlan触发打款）", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/finance/FinanceApplicationController.java:180-247（确认保理服务费/确认融资收款和服务费接口及异常类型白名单）", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/finance/enums/FinanceAbnormalTypeEnum.java（fee_not_match/two_abnormal等异常枚举）" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order/融资管理-人工确认",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/finance/FinanceApplicationController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/calculation/funding/ZhongHuaFundingCalculationStrategy.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "lvliuhao",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI1",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-order 下线中化资方服务费支付流程并停用保理手续费打款任务",
    "description" : "取消先行通收款后向中化支付资方服务费的全部触发链路：放款成功自动触发、中化线下融资确认触发、XXL-JOB定时打款与轮询、手动补偿接口，调整计划生成加结算方式防护，保留支付回调以闭环在途打款。",
    "requirementChangePoint" : "取消先行通收款后向中化保理支付资方服务费的流程：放款成功自动触发、中化线下融资确认触发、XXL-JOB定时打款/轮询、手动补偿接口全面下线并对中化封禁",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "FinanceApplicationService.financeConfirm", "FinanceBusinessService.zhFinanceOverHandle", "PaymentFactoringFeeSchedule（queryAdjustmentPlanProcessingJob/executeAdjustmentPlanJob）", "AdjustmentPlanService（executeAdjustmentPlan/paymentFactoringFee/notifyPayResult）", "AdjustmentPlanController（/api/adjustment/*）", "finance_adjustment_plan表" ],
    "changePoints" : [ "接口/服务：FinanceApplicationService.financeConfirm中化线下确认后的'支付保理手续费'调用移除/按结算方式条件化", "任务调度：PaymentFactoringFeeSchedule两个XXL-JOB停止调度并在代码标注下线，queryAdjustmentPlanProcessing轮询查单同步退役（运维侧需同步下线xxl-job调度配置）", "接口/服务：AdjustmentPlanService.executeAdjustmentPlan/paymentFactoringFee按资金方结算方式防护，差额放款模式下不再生成finance_adjustment_plan调账单；/api/adjustment/executeAdjustmentPlan、/api/adjustment/adjustmentPaymentAgain手动接口对中化封禁；notifyPayResult回调保留处理存量在途打款" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/xxljob/PaymentFactoringFeeSchedule.java（JOB任务-支付保理手续费，两个XxlJob入口）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/AdjustmentPlanService.java:56-135（仅ZJF000015且factoringPaymentResult=3时生成调账单并sapsClient.singleRemit打款给中化账户）", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/AdjustmentPlanController.java:56-77（手动executeAdjustmentPlan/adjustmentPaymentAgain接口）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceApplicationService.java:292-296（financeConfirm确认后支付保理手续费）", "scf-order/dbscript/DDL/fix_payment_20260112.sql（finance_adjustment_plan融资调账计划表）" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order/保理手续费支付",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/xxljob/PaymentFactoringFeeSchedule.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/AdjustmentPlanService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/AdjustmentPlanController.java", "scf-order/dbscript/DDL/fix_payment_20260112.sql" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "zhanghuajun",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI1",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "insurance_admin 调整融资管理保理手续费确认弹窗文案与金额展示口径",
    "description" : "融资管理页面'确认保理手续费'、'确认保理手续费并确认已融资收款'弹窗提示语去掉'并向资方支付'表述，改为仅复写资方服务费口径；列表、详情与导出中放款金额、资方服务费等字段按差额放款口径核对。",
    "requirementChangePoint" : "保理手续费不一致人工确认规则调整：标记异常与备注、人工弹窗输入确认值流程不变，确认后系统仅以人工填写数值复写资方服务费并重建资方还款计划，不再向中化保理打款",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "insurance_admin/src/views/financialManage/financing/index.vue（确认弹窗、按钮、列表列）", "insurance_admin/src/views/financialManage/financing/components/detail.vue", "/manage/finance/page/query/download导出" ],
    "changePoints" : [ "页面/文案：confirmFactoringServiceFee、confirmFactoringServiceFeeAndFinancingReceipt两个弹窗提示语去掉'并向资方支付'，改为'仅更新资方服务费'口径；确认按钮权限保持不变", "页面/展示：融资管理列表与详情中放款金额、资方服务费、资方计算保理手续费字段按差额放款口径核对展示", "导出：/manage/finance/page/query/download导出模板字段口径核对（资方放款金额=差额口径）" ],
    "systemTags" : [ "insurance_admin" ],
    "evidenceRefs" : [ "insurance_admin/src/views/financialManage/financing/index.vue:763-812（两个确认弹窗文案含'继续后续流程并向资方支付'）", "insurance_admin/src/views/financialManage/financing/index.vue:298-320（三个确认操作按钮及权限标识）", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/finance/vo/FinanceApplicationRespVo.java:200-205（serviceFee/capitalServiceFee/factoringPaymentResult导出字段）", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/finance/FinanceApplicationController.java:88-113（融资管理导出）" ],
    "confidence" : "HIGH",
    "moduleName" : "insurance_admin/融资管理页面",
    "relatedFiles" : [ "insurance_admin/src/views/financialManage/financing/index.vue", "insurance_admin/src/views/financialManage/financing/components/detail.vue", "insurance_admin/src/api/api_list.js" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI3",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-order/insurance_admin 中化差额放款与不打款全链路联调回归",
    "description" : "与中化保理联调放款结果通知/查询新paymentAmt值，回归到账校验、手续费一致性异常、人工确认复写不打款、打款流程下线与在途调账闭环、合同推送电签回传不变等全场景，并完成生产配置切换演练。",
    "requirementChangePoint" : "合同调整：中化保理负责调整保理合同模板及合同金额内容，合同附件推送、先行通电签、回传流程保持不变（无系统改造，仅回归验证）",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "测试环境全链路（scf-order、scf-intf-zhonghua、insurance_admin、SAPS动账）", "xxl-job调度配置", "org_funding_party生产配置切换窗口" ],
    "changePoints" : [ "幂等/验证：与中化联调放款结果通知/放款结果查询paymentAmt新值（商业险垫资金额-保理手续费）场景，验证到账校验、手续费不一致标记与人工确认仅复写不打款", "幂等/验证：打款流程下线回归——确认不再新增finance_adjustment_plan调账单、XXL-JOB停用生效、在途打款notifyPayResult回调可正常闭环", "幂等/验证：合同附件推送、电签、回传流程回归（应不受影响）；存量全额口径单据修正/人工兜底方案演练与生产配置切换演练" ],
    "systemTags" : [ "scf-order", "scf-intf-zhonghua", "insurance_admin" ],
    "evidenceRefs" : [ "测试库scf_self_order.org_funding_party ZJF000015已为'1'（差额放款），可先行联调", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java:105-210（放款结果处理全链路入口）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceContractService（queryNeedFunderContract合同下载链路保持不变）" ],
    "confidence" : "HIGH",
    "moduleName" : "保费分期/中化融资全链路",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java", "scf-intf-zhonghua/intfCapital-service/src/main/java/com/scf/intfCapital/service/capital/zhonghua/LoanQueryServiceImpl.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/AdjustmentPlanService.java" ],
    "estimatedEffort" : "8h",
    "ownerUserName" : null,
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI1、WI2、WI3、WI4、WI5",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "26h",
  "developmentEstimatedEffort" : "26h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T14:29:48.936316",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 282

<!-- analysis-record:fe777d5238ba3cadcd28073fda4d77c360d81e3137c208ecddfb318639627651 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：125
- 记录时间：2026-11-18T15:55:31.751875
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000008",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "1.前端交互页面和逻辑优化；2.经销商入网流程优化、合作协议电子签约、付款结算周期优化；3.订单申请流程优化",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "hanmeng" ],
  "workItems" : [ {
    "title" : "账单增加线下还款转账凭证上传权限",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps", "lease-admin" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "4h",
  "developmentEstimatedEffort" : "4h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T15:55:30.494658",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 282

<!-- analysis-record:f696bcf9339462b6ff48b68a8aeeb2e4858285b1854573bef1ac044167ce664d -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：125
- 记录时间：2026-11-18T15:55:49.282935
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000008",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "1.前端交互页面和逻辑优化；2.经销商入网流程优化、合作协议电子签约、付款结算周期优化；3.订单申请流程优化",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "hanmeng" ],
  "workItems" : [ {
    "title" : "客户账单账单增加线下还款转账凭证上传权限",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps", "lease-admin" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "4h",
  "developmentEstimatedEffort" : "4h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T15:55:30.494658",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 282

<!-- analysis-record:b8b8af2301992bc68b85290a4d38c8f3ce6ada62708d191d6e84f60f4ba4e781 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：125
- 记录时间：2026-11-18T15:56:02.917046
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000008",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "1.前端交互页面和逻辑优化；2.经销商入网流程优化、合作协议电子签约、付款结算周期优化；3.订单申请流程优化",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "hanmeng" ],
  "workItems" : [ {
    "title" : "客户账单账单增加线下还款转账凭证上传权限",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps", "lease-admin" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "缴费单和账单的上传凭证按钮都需要给到合作商权限",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "8h",
  "developmentEstimatedEffort" : "8h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T15:55:30.494658",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 282

<!-- analysis-record:bb8d7301395e55e589a801bac284173e1ed731605803e2f68b78ae5dc3de2bab -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：125
- 记录时间：2026-11-18T15:56:40.768820
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000008",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "1.前端交互页面和逻辑优化；2.经销商入网流程优化、合作协议电子签约、付款结算周期优化；3.订单申请流程优化",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "hanmeng" ],
  "workItems" : [ {
    "title" : "客户账单账单增加线下还款转账凭证上传权限",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps", "lease-admin" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "缴费单和账单的上传凭证按钮都需要给到合作商权限",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "上传后邮件通知运营",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "12h",
  "developmentEstimatedEffort" : "12h",
  "testingEstimatedEffort" : null,
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T15:55:30.494658",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 282

<!-- analysis-record:3479a5463c26abbf517cb0abb0f30ced59748dc013d0cf004bb6adfc7b559417 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：125
- 记录时间：2026-11-18T16:01:53.256510
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000008",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/BL000008/BL000008/2026/202609070003/历史版本/71f99110-d117-477d-938f-4624658bfce4/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "1.前端交互页面和逻辑优化；2.经销商入网流程优化、合作协议电子签约、付款结算周期优化；3.订单申请流程优化",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "hanmeng" ],
  "workItems" : [ {
    "title" : "客户账单账单增加线下还款转账凭证上传权限",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps", "lease-admin" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "缴费单和账单的上传凭证按钮都需要给到合作商权限",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "上传后邮件通知运营",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "10 经销商付款结算规则新增枚举值",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-admin" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "hanmeng",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "36h",
  "developmentEstimatedEffort" : "20h",
  "testingEstimatedEffort" : "16h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T15:55:30.494658",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 271

<!-- analysis-record:f349a0ec893c68cc02433feaf938940f335d96c34a3782202470d96ca7349133 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：124
- 记录时间：2026-11-18T17:35:06.103653
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/保费分期/聚保融保费分期项目/2026/202611040003/历史版本/40af1c06-19e6-4335-847e-78dd0da92dc0/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "保费分期",
  "projectId" : 21,
  "projectName" : "聚保融保费分期项目",
  "repositoryUrl" : "scf (13 repositories)",
  "requirementRawPath" : "requirement-sources/保费分期/聚保融保费分期项目/2026/202611040003/历史版本/40af1c06-19e6-4335-847e-78dd0da92dc0/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "核心结论：系统已内建“资方服务费结算方式”能力（org_funding_party.funding_service_fee_settlement_method，1=差额放款/2=放款后支付，运营端资金方配置页可维护），生产库中化(ZJF000015)当前为'2'、测试库已为'1'。本需求主体是：生产将中化切换为差额放款后，融资单创建时loanAmount即=资方融资金额-资方服务费（FinanceApplicationService.financeHandle既有分支），放款到账校验（liquidationAmount vs loanAmount）随口径自动生效；中化放款结果通知/查询paymentAmt新值经scf-intf-zhonghua透传，DTO无需结构性改动；打款链路需全面下线（zhFinanceOverHandle与financeConfirm自动触发、PaymentFactoringFeeSchedule两个XXL-JOB、/api/adjustment手动接口）并按结算方式加防护；手续费不一致人工确认改为仅复写资方服务费；insurance_admin确认弹窗文案需去掉“并向资方支付”。合同推送/电签/回传链路无需改动（模板由中化调整）。需人工复核的新问题：1)上线时点存量“审批通过待放款/放款中”中化融资单loanAmount已按全额生成，切换后到账校验必然异常，需存量修正或人工确认兜底方案；2)在途finance_adjustment_plan调账打款需保留notifyPayResult回调以闭环；3)放款金额口径变化对报表与融资导出的影响未深入核查，建议复核；4)测试库中化配置已为'1'与生产不一致，需确认是否为预置变更；5)XXL-JOB停用需运维侧同步下线调度。",
  "requirementChangePoints" : [ "中化保理放款金额计算规则变更：放款金额=资方融资金额-资方服务费，资方融资金额（商业险垫资金额）与资方服务费（保理手续费）计算公式不变，通过将中化资金方服务费结算方式切换为差额放款实现", "中化接口传值调整：放款结果通知、放款结果查询接口中paymentAmt放款金额值=商业险垫资金额-保理手续费（中化侧传值变化，先行通侧透传解析与金额校验口径同步）", "先行通到账校验规则变更：应收到账金额=资方应放款金额=资方融资金额-资方服务费；中化推送保理手续费与系统按产品规则计算的资方服务费的一致性校验保持不变", "保理手续费不一致人工确认规则调整：标记异常与备注、人工弹窗输入确认值流程不变，确认后系统仅以人工填写数值复写资方服务费并重建资方还款计划，不再向中化保理打款", "取消先行通收款后向中化保理支付资方服务费的流程：放款成功自动触发、中化线下融资确认触发、XXL-JOB定时打款/轮询、手动补偿接口全面下线并对中化封禁", "合同调整：中化保理负责调整保理合同模板及合同金额内容，合同附件推送、先行通电签、回传流程保持不变（无系统改造，仅回归验证）" ],
  "impactedModules" : [ "scf-order/融资管理（融资单生成、放款结果通知、到账校验、人工确认）", "scf-order/保理手续费支付（调整计划AdjustmentPlan、XXL-JOB任务、手动补偿接口）", "scf-order/资金方配置（org_funding_party资方服务费结算方式）", "scf-intf-zhonghua/中化接口（放款结果通知与查询paymentAmt透传口径，无结构性改动）", "insurance_admin/融资管理页面（确认保理手续费弹窗文案、列表与导出口径）" ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "admin", "cairui", "caobin", "caofeng", "dujunchen", "hanmeng", "jiangmin", "lvliuhao", "shihao", "wangwenlong", "xuewentao", "xujieni", "yanggang", "yinjun", "yufanghua", "zhanghongjun", "zhanghuajun", "zhiyuntao" ],
  "workItems" : [ {
    "title" : "scf-order 切换中化资方服务费结算方式为差额放款并适配放款金额计算",
    "description" : "将生产环境org_funding_party中中化(ZJF000015)的资方服务费结算方式由'2'放款后支付切换为'1'差额放款，复用financeHandle既有差额放款分支使融资单放款金额=资方融资金额-资方服务费，并制定存量进行中单据处理方案。生产库现值'2'、测试库已为'1'。",
    "requirementChangePoint" : "中化保理放款金额计算规则变更：放款金额=资方融资金额-资方服务费，资方融资金额（商业险垫资金额）与资方服务费（保理手续费）计算公式不变，通过将中化资金方服务费结算方式切换为差额放款实现",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "org_funding_party.funding_service_fee_settlement_method（ZJF000015）", "FinanceApplicationService.financeHandle/buildFinanceApplication", "dbscript DML脚本" ],
    "changePoints" : [ "配置/SQL：新增生产DML脚本将ZJF000015的funding_service_fee_settlement_method由'2'改为'1'（差额放款），与中化放款规则切换时点对齐", "接口/服务：复核financeHandle差额放款分支（loanAmount=financeAmount-serviceFee）在融资单创建、中化线下确认路径下的执行与落库时序", "幂等/验证：梳理上线时点存量'审批通过待放款/放款中'中化融资单（loanAmount已按全额生成）的修正或人工确认兜底方案" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceApplicationService.java:484-489（if(\"1\".equals(getFundingServiceFeeSettlementMethod()))差额放款赋值loanAmount）", "scf-order/dbscript/DDL/zh_payment_20250912.sql:26-28（org_funding_party含funding_service_fee_settlement_method列）", "insurance_admin/src/views/counterparty/Funding/components/configDia.vue:120-134（资方服务费结算方式：差额放款/放款后支付配置UI）", "只读DB查询：PROD scf_self_order.org_funding_party ZJF000015 funding_service_fee_settlement_method='2'；TEST同表='1'" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order/资金方配置与融资单生成",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceApplicationService.java", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/organization/po/OrgFundingParty.java", "scf-order/dbscript/DDL/zh_payment_20250912.sql" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-order 调整中化放款到账校验规则为应收差额并同步接口金额校验",
    "description" : "放款结果通知与主动查询链路中，到账校验口径切换为应收到账金额=资方融资金额-资方服务费（loanAmount随差额放款配置自动切换），并核对中化paymentAmt新值（商业险垫资金额-保理手续费）的校验与落库口径。",
    "requirementChangePoint" : "中化接口传值调整：放款结果通知、放款结果查询接口中paymentAmt放款金额值=商业险垫资金额-保理手续费（中化侧传值变化，先行通侧透传解析与金额校验口径同步）",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "FinanceBusinessService.loanResultNotify/compareAmountAndPush2Saps", "FinanceApplicationCallbackApiController（/api/finance/loan/result、/api/finance/loan/query）", "FinanceLoanPaymentService.saveCapitalLoanPayment", "scf-intf-zhonghua LoanDetail（paymentAmt/serviceFee透传）" ],
    "changePoints" : [ "接口/服务：compareAmountAndPush2Saps到账校验按差额口径复核（liquidationAmount vs loanAmount），LOAN_AMOUNT_NOT_MATCH与SERVICE_FEE_NOT_MATCH及组合异常标记逻辑回归", "接口/服务：放款结果通知入口checkLoanDetail与confirmReceived主动查询路径，按新口径校验中化paymentAmt，必要时增加通知放款金额与应收差额的一致性校验", "幂等/验证：saveCapitalLoanPayment落库paymentAmt/realPaymentAmt与银行到账liquidationAmount金额口径一致性回归，确认分布式锁与重复通知幂等不受影响" ],
    "systemTags" : [ "scf-order", "scf-intf-zhonghua" ],
    "evidenceRefs" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java:105-210（loanResultNotify到账查询、compareAmountAndPush2Saps金额与手续费比对）", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/api/FinanceApplicationCallbackApiController.java:75-140（放款结果通知/查询入口及paymentAmt、serviceFee>0校验）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceLoanPaymentService.java:122-143（saveCapitalLoanPayment按中化通知金额落库）", "scf-intf-zhonghua/intfCapital-model/src/main/java/com/scf/intfCapital/dto/capital/zhonghua/resp/loan/LoanDetail.java（paymentAmt/serviceFee字段透传，无结构性改动）" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order/融资管理-放款结果处理",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/api/FinanceApplicationCallbackApiController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceLoanPaymentService.java", "scf-intf-zhonghua/intfCapital-service/src/main/java/com/scf/intfCapital/service/capital/zhonghua/LoanResultNotifyServiceImpl.java" ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "hanmeng",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI1",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-order 调整保理手续费人工确认仅复写资方服务费不再打款",
    "description" : "保理手续费不一致人工确认（确认保理手续费、确认保理手续费并确认已融资收款）后，仅以人工填写值复写资方服务费并重建资方还款计划，不再触发向中化保理打款；异常标记与弹窗操作流程保持不变。",
    "requirementChangePoint" : "保理手续费不一致人工确认规则调整：标记异常与备注、人工弹窗输入确认值流程不变，确认后系统仅以人工填写数值复写资方服务费并重建资方还款计划，不再向中化保理打款",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "FinanceBusinessService.confirmServiceFee/confirmReceivedAndFee/zhFinanceOverHandle", "FinanceApplicationController（/manage/finance/confirm/serviceFee、/confirm/receivedAndFee）", "FinanceFundingRepaymentPlanService还款计划重建" ],
    "changePoints" : [ "接口/服务：confirmServiceFee/confirmReceivedAndFee确认后复写serviceFee、清异常、重建资方还款计划，按资金方服务费结算方式跳过adjustmentPlanService.executeAdjustmentPlan打款调用", "接口/服务：zhFinanceOverHandle中'触发一次保理手续费打款'调用条件化，保留SAPS动账结算、loanSuccess、合同下载与放款成功邮件", "幂等/验证：确认接口幂等（LOAN_SUCCESS直接返回）与复写后资方还款计划、推送SAPS数据一致性回归；FinanceAbnormalTypeEnum五种异常类型入口校验不变" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java:283-330（confirmServiceFee/confirmReceivedAndFee复写服务费后走zhFinanceOverHandle）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java:365-391（zhFinanceOverHandle内adjustmentPlanService.executeAdjustmentPlan触发打款）", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/finance/FinanceApplicationController.java:180-247（确认保理服务费/确认融资收款和服务费接口及异常类型白名单）", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/finance/enums/FinanceAbnormalTypeEnum.java（fee_not_match/two_abnormal等异常枚举）" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order/融资管理-人工确认",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/finance/FinanceApplicationController.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/calculation/funding/ZhongHuaFundingCalculationStrategy.java" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "hanmeng",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI1",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-order 下线中化资方服务费支付流程并停用保理手续费打款任务",
    "description" : "取消先行通收款后向中化支付资方服务费的全部触发链路：放款成功自动触发、中化线下融资确认触发、XXL-JOB定时打款与轮询、手动补偿接口，调整计划生成加结算方式防护，保留支付回调以闭环在途打款。",
    "requirementChangePoint" : "取消先行通收款后向中化保理支付资方服务费的流程：放款成功自动触发、中化线下融资确认触发、XXL-JOB定时打款/轮询、手动补偿接口全面下线并对中化封禁",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "FinanceApplicationService.financeConfirm", "FinanceBusinessService.zhFinanceOverHandle", "PaymentFactoringFeeSchedule（queryAdjustmentPlanProcessingJob/executeAdjustmentPlanJob）", "AdjustmentPlanService（executeAdjustmentPlan/paymentFactoringFee/notifyPayResult）", "AdjustmentPlanController（/api/adjustment/*）", "finance_adjustment_plan表" ],
    "changePoints" : [ "接口/服务：FinanceApplicationService.financeConfirm中化线下确认后的'支付保理手续费'调用移除/按结算方式条件化", "任务调度：PaymentFactoringFeeSchedule两个XXL-JOB停止调度并在代码标注下线，queryAdjustmentPlanProcessing轮询查单同步退役（运维侧需同步下线xxl-job调度配置）", "接口/服务：AdjustmentPlanService.executeAdjustmentPlan/paymentFactoringFee按资金方结算方式防护，差额放款模式下不再生成finance_adjustment_plan调账单；/api/adjustment/executeAdjustmentPlan、/api/adjustment/adjustmentPaymentAgain手动接口对中化封禁；notifyPayResult回调保留处理存量在途打款" ],
    "systemTags" : [ "scf-order" ],
    "evidenceRefs" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/xxljob/PaymentFactoringFeeSchedule.java（JOB任务-支付保理手续费，两个XxlJob入口）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/AdjustmentPlanService.java:56-135（仅ZJF000015且factoringPaymentResult=3时生成调账单并sapsClient.singleRemit打款给中化账户）", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/AdjustmentPlanController.java:56-77（手动executeAdjustmentPlan/adjustmentPaymentAgain接口）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceApplicationService.java:292-296（financeConfirm确认后支付保理手续费）", "scf-order/dbscript/DDL/fix_payment_20260112.sql（finance_adjustment_plan融资调账计划表）" ],
    "confidence" : "HIGH",
    "moduleName" : "scf-order/保理手续费支付",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/xxljob/PaymentFactoringFeeSchedule.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/AdjustmentPlanService.java", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/AdjustmentPlanController.java", "scf-order/dbscript/DDL/fix_payment_20260112.sql" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "hanmeng",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI1",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "insurance_admin 调整融资管理保理手续费确认弹窗文案与金额展示口径",
    "description" : "融资管理页面'确认保理手续费'、'确认保理手续费并确认已融资收款'弹窗提示语去掉'并向资方支付'表述，改为仅复写资方服务费口径；列表、详情与导出中放款金额、资方服务费等字段按差额放款口径核对。",
    "requirementChangePoint" : "保理手续费不一致人工确认规则调整：标记异常与备注、人工弹窗输入确认值流程不变，确认后系统仅以人工填写数值复写资方服务费并重建资方还款计划，不再向中化保理打款",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "insurance_admin/src/views/financialManage/financing/index.vue（确认弹窗、按钮、列表列）", "insurance_admin/src/views/financialManage/financing/components/detail.vue", "/manage/finance/page/query/download导出" ],
    "changePoints" : [ "页面/文案：confirmFactoringServiceFee、confirmFactoringServiceFeeAndFinancingReceipt两个弹窗提示语去掉'并向资方支付'，改为'仅更新资方服务费'口径；确认按钮权限保持不变", "页面/展示：融资管理列表与详情中放款金额、资方服务费、资方计算保理手续费字段按差额放款口径核对展示", "导出：/manage/finance/page/query/download导出模板字段口径核对（资方放款金额=差额口径）" ],
    "systemTags" : [ "insurance_admin" ],
    "evidenceRefs" : [ "insurance_admin/src/views/financialManage/financing/index.vue:763-812（两个确认弹窗文案含'继续后续流程并向资方支付'）", "insurance_admin/src/views/financialManage/financing/index.vue:298-320（三个确认操作按钮及权限标识）", "scf-order/scf-order-model/src/main/java/com/thchengtay/order/model/finance/vo/FinanceApplicationRespVo.java:200-205（serviceFee/capitalServiceFee/factoringPaymentResult导出字段）", "scf-order/scf-order-controller/src/main/java/com/thchengtay/order/controller/finance/FinanceApplicationController.java:88-113（融资管理导出）" ],
    "confidence" : "HIGH",
    "moduleName" : "insurance_admin/融资管理页面",
    "relatedFiles" : [ "insurance_admin/src/views/financialManage/financing/index.vue", "insurance_admin/src/views/financialManage/financing/components/detail.vue", "insurance_admin/src/api/api_list.js" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "hanmeng",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI3",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  }, {
    "title" : "scf-order/insurance_admin 中化差额放款与不打款全链路联调回归",
    "description" : "与中化保理联调放款结果通知/查询新paymentAmt值，回归到账校验、手续费一致性异常、人工确认复写不打款、打款流程下线与在途调账闭环、合同推送电签回传不变等全场景，并完成生产配置切换演练。",
    "requirementChangePoint" : "合同调整：中化保理负责调整保理合同模板及合同金额内容，合同附件推送、先行通电签、回传流程保持不变（无系统改造，仅回归验证）",
    "taskType" : "UNKNOWN",
    "targetResources" : [ "测试环境全链路（scf-order、scf-intf-zhonghua、insurance_admin、SAPS动账）", "xxl-job调度配置", "org_funding_party生产配置切换窗口" ],
    "changePoints" : [ "幂等/验证：与中化联调放款结果通知/放款结果查询paymentAmt新值（商业险垫资金额-保理手续费）场景，验证到账校验、手续费不一致标记与人工确认仅复写不打款", "幂等/验证：打款流程下线回归——确认不再新增finance_adjustment_plan调账单、XXL-JOB停用生效、在途打款notifyPayResult回调可正常闭环", "幂等/验证：合同附件推送、电签、回传流程回归（应不受影响）；存量全额口径单据修正/人工兜底方案演练与生产配置切换演练" ],
    "systemTags" : [ "scf-order", "scf-intf-zhonghua", "insurance_admin" ],
    "evidenceRefs" : [ "测试库scf_self_order.org_funding_party ZJF000015已为'1'（差额放款），可先行联调", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java:105-210（放款结果处理全链路入口）", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceContractService（queryNeedFunderContract合同下载链路保持不变）" ],
    "confidence" : "HIGH",
    "moduleName" : "保费分期/中化融资全链路",
    "relatedFiles" : [ "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/FinanceBusinessService.java", "scf-intf-zhonghua/intfCapital-service/src/main/java/com/scf/intfCapital/service/capital/zhonghua/LoanQueryServiceImpl.java", "scf-order/scf-order-service/src/main/java/com/thchengtay/order/service/finance/AdjustmentPlanService.java" ],
    "estimatedEffort" : "2h",
    "ownerUserName" : "hanmeng",
    "priority" : "3",
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : "WI1、WI2、WI3、WI4、WI5",
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "32h",
  "developmentEstimatedEffort" : "16h",
  "testingEstimatedEffort" : "16h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T14:29:48.936316",
  "generator" : "Codex CLI",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 276

<!-- analysis-record:cc069da646a78507250eae232663c963816a036ca361eb2f0e33870ea6a6aa21 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：126
- 记录时间：2026-11-18T17:36:56.236286
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/2026110600003/历史版本/a8dbcf8d-d3c9-4b83-be36-d4004d667f5f/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "嘉泰租赁资产保理",
  "projectId" : 42,
  "projectName" : "中恒信合乘用车保理项目",
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/2026110600003/历史版本/a8dbcf8d-d3c9-4b83-be36-d4004d667f5f/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "国胜项目系统迭代，包含5项变更：1) 线上还款二维码失效规则由生成后5分钟有效调整为当日24:00失效，跨日自动失效；2) 客户提前结清推送增加T+1个工作日宽限期，允许合作方在T+1个工作日内推送客户实际结清时间且不多计利息/罚息；3) 还款台账搜索栏及列表新增“客户实还时间”字段，结算方式为提前结清/代偿/回购且线下还款的结算时间由账单生成日改为按账单核销收款时间；4) 为国胜合作方开通4个外部查询账号（邓艳艳、李星野、秦轶斐、何洋）并配置资产管理及中恒信合资产查询权限（搜索、查询列表、查看详情）；5) 客户账单“客户实还时间”搜索框改为日期控件并缩短。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "xujieni" ],
  "workItems" : [ {
    "title" : "资产管理权限",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "assets-lease", "assets-lease-admin" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "xujieni",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "12h",
  "developmentEstimatedEffort" : "4h",
  "testingEstimatedEffort" : "8h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-18T17:36:55.401245",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 生成需求澄清项 · 需求 274

<!-- analysis-record:46bc390dd09917eaa86141ea7f9d3acdf02cbd294aad748afe1d586e889248c4 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：53
- 记录时间：2026-11-19T09:50:50.022384
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000005/BL000005/2026/202610280004/历史版本/e0d82b5b-6967-4c42-aa17-789efd462ca4/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "付款流程口径：全自动系统代付，还是\"人工发起付款申请+财务复核\"工作流",
  "description" : "需确认最终流程：A. 按产品方案全自动（扣款成功自动生成记录并系统代付，失败自动重试，无人工环节）；B. 按需求描述构建\"人工发起付款申请→财务复核→付款\"流程（需新增申请/复核状态机、操作人与时间字段、权限与操作留痕）。二者数据结构、交互与实现工作量差异巨大，需产品给定唯一口径。",
  "evidence" : "已查证：①受理单需求描述与需求简介均为\"线上代收对账单新增【系统内人工发起付款申请及财务复核完成付款】功能\"；②产品方案《新增【线上代收付明细自动付款】功能.docx》V1.0（2026/11/09 范怡）全文描述全自动：扣款成功自动生成记录、\"由先行通设备科技的宝付代付商户号，向对应的设备厂家打款相同金额\"、代付方式仅\"系统自动/线下打款\"、代付结果仅\"付款中/付款成功\"、\"代付失败系统重新调用代付接口直至成功\"，字段清单与筛选项中无申请人/申请时间/复核人/复核时间/待复核状态等任何字段；③代码现状：ol-saps StatementManageV2Controller（/saps/statement/online/collection/* 4个查询导出接口+核销复用/saps/statement/v2/check）与 ol-admin factoryBill 模块（onlineCollectionBill.vue、OnlineCollectionDetail.vue、FactoryStatementWriteOff.vue）均无付款申请/复核功能。两份需求材料口径相反，无法从证据推断唯一答案。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "强制回购（待强制回购/已强制回购）后的线上扣款账单是否纳入生成与代付范围",
  "description" : "需确认新【线上代收付明细】的账单状态过滤口径：A. 仅代偿/回购（500/501/600/601），排除强制回购；B. 沿用现有线上代收数据源（500/501/600/601/700/701），强制回购账单线上扣款成功同样生成记录并系统代付，此时\"代偿/回购对账单编号\"字段及打款备注是否取强制回购对账单（FB前缀）编号也需一并明确。该口径直接决定取数范围与打款边界。",
  "evidence" : "已查证：①需求描述与docx均仅写\"代偿、回购后…扣款成功的账单\"，字段与打款备注规则均只定义\"代偿/回购对账单编号\"；②代码现实：ol-saps CustomerBillPayDetailMapper.xml listOnlineCollectionPayDetails（第75-76行）过滤条件为 cb.bill_status IN (500,501,600,601,700,701)，明确包含待/已强制回购，StatementTypeEnum 存在独立的 FORCE_BUYBACK(2010,\"强制回购\",\"FB\")；③生产库(PRJ000088)：已强制回购(701)账单2458条，远多于已代偿(501)20条、已回购(601)20条，强制回购账单当前回款全部走线下(channel=8,sub_mode 32/40，46笔)，线上代收对账单明细(30条)目前仅含已代偿账单——即需求文本与被替换功能的既有数据源口径不一致，且一旦强制回购账单发生线上扣款，按字面口径将不生成记录、不代付。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "宝付代付渠道能力缺失：可查证的支付系统代付链路仅支持通联，宝付仅有代收实现，代付商户1283248无配置与生产执行先例",
  "description" : "需求核心依赖\"宝付代付商户号1283248系统代付\"，但当前可查证的支付系统代码中代付通道硬编码通联且无宝付代付策略、请求接口不携带商户号、生产库无宝付代付商户配置、宝付代付无任何生产执行记录。落地需在支付侧新增宝付代付渠道对接（接口适配、渠道路由、商户/密钥配置、回调与对账文件），并依赖宝付侧接口开通与联调，属资金出账通道，无法在ol组仓库内复用现成实现。",
  "evidence" : "已查证：①ol-payment lease-payment-business/.../service/impl/RemitBizServiceImpl.java singleRemit 中\"// 通道选择(路由),默认通联 ChannelEnum channel = ChannelEnum.ALLINPAY;\"，无宝付路由分支；②ol-payment channel/impl/remit/ 目录仅有 AllinPayRemitStrategyImpl.java，宝付目录(baofu/)仅有 BaofuAgreePayStrategyImpl(代收)/BaofuCardBindStrategyImpl/BaofuPayQueryServiceImpl/BaofuPayResultNotifyStrategy，无代付策略；③生产库 ol_payment.remit_channel_config 仅1条宝付配置：BAOFUPAY+SIGN_PAY(协议代收)+商户1273947，无代付商户1283248；④ol-saps 退款单虽有宝付代付设计（RefundOrderProperties.baofuRemitMerchantNo=1283248、RefundInterfaceTypeEnum.BANK_TRANSFER\"银行卡代付\"），但生产库 ol_saps.refund_payment_flow 仅有 ORIGINAL_REFUND 记录(14成功/1失败)，BANK_TRANSFER 为0条，宝付代付无生产执行先例；⑤代付调用链 ol-saps PaymentApiService→xxt-pay /pay/remit/singleRemit 的 RemitReq（lease-saps-api/.../RemitReq.java）无商户号字段，无法指定1283248。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "\"代付失败重新调用直至成功\"与现有代付幂等及异步状态机冲突：同一订单号重复提交会被拒绝，处理中/未知态下重发存在重复出款风险",
  "description" : "需求要求代付失败即重新调用代付接口直至成功，且代付结果无失败终态（仅付款中/付款成功）。而现有代付框架按\"租户+业务订单号\"做幂等，同单重发直接报 DUPLICATE_ORDER，每次重试必须更换业务订单号；同时代付存在 PROCESSING 中间态、依赖异步回调或主动查询推进终态。若重试逻辑不严格区分\"确定失败\"与\"处理中/结果未知\"即重新发起，存在对同一笔款项重复打款的资金风险；若仅凭同步返回失败重发，异步失败场景又会长期滞留\"付款中\"。重试机制需明确逐次换号、终态核实后再重发的口径并配套查询兜底。",
  "evidence" : "已查证：①ol-payment RemitBizServiceImpl.singleRemit 入口 isDuplicateRequest 按 RemitOrderEntity.tenantCode+BizSerialNo 查重，存在即抛 BizException(DUPLICATE_ORDER)——同一业务订单号无法二次发起；②同文件代付订单初始状态即 PROCESSING，终态依赖 remitNotify 异步回调（通联回调模型）或 queryRemit 渠道同步查询推进，存在\"提交成功但结果未知\"窗口；③产品方案代付结果枚举仅\"付款中/付款成功\"且要求\"重新调用代付接口进行代付，直至成功为止\"，未定义重试频率、次数与结果未知时的处理规则；④ol-saps 侧既有先例为退款单逐次请求换号（refund_payment_flow.requestNo/thirdTradeNo/queryCount/nextQueryTime 字段），新功能需按同等模式设计而非简单重发。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 生成需求澄清项 · 需求 285

<!-- analysis-record:a93105e0c60fc4a9c117317ea976eab0d759c571c3f1b57f8b081b1d4a0aaa97 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：56
- 记录时间：2026-11-19T11:26:16.095149
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/消费分期/即科医美通汇诚泰保理/2026/202611190001/历史版本/7fa66c11-25c6-438e-8b16-bd749cbbcd2c/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "按项目号补跑回购出账任务会绕过 removeBuyBack 排除配置，指定 projectNo 重跑仍会生成回购对账单",
  "description" : "排除逻辑只作用于日批未传 projectNo 的分支；若运维通过 xxl-job 以 projectNo=XXT-PRJ-JK-A/C/D 参数补跑 buyBackStatementGenerateSchedule，回购对账单仍会照常生成，与需求“系统不再生成回购对账单”的口径存在缺口。需在方案中明确二选一：补跑操作纪律约束，或在代码层（else 分支）增加同一排除校验。",
  "evidence" : "已查证：amp-saps/saps-schedule/src/main/java/com/amp/saps/schedule/statement/BuyBackStatementGenerateSchedule.java:86-96，removeBuyBack 排除仅位于 projectNo 为空的分支；同文件 else 分支（117-146 行）按传入 projectNo 直接执行 statementGenerateBizService.execute(BUYBACK)，不读取排除字典。仓库内本需求草案 dbscript/DML/2026/V5_cancel_buyback_202611190001_dml.sql:16 注释亦自认“指定 projectNo 补跑不读取该排除项”。生产库 amp_saps.business_config 现有 removeBuyBack 仅 XXT-PRJ-DP-A/B 两值，三项目尚未排除。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "强制回购分析跑批链路不校验 removeBuyBack，CRM 侧一旦重新开启强制回购配置，回购对账单将恢复生成",
  "description" : "强制回购有三条入口：日批（校验排除）、/api/mandatory/buyback 手动接口（校验排除）、mandatoryBuyBackAnalyzeSchedule 跑批内部直调 statementBizService.mandatoryBuyBack（无任何排除校验）。当前三项目 forced_buyback_flag=0 不触发，但该配置由 CRM 推送可随时变更，若开启即绕过本次排除配置。草案 DML 注释“强制回购接口也使用该配置”仅对手动 API 成立，对跑批内部链路不成立；方案需明确该链路的处理（如同步下线/锁定三项目的强制回购配置，或在 analyze 调用前增加排除校验）。",
  "evidence" : "已查证：全仓 removeBuyBack 仅出现在 BuyBackStatementGenerateSchedule.java:86 与 CapitalProviderController.java:81 两处；amp-saps/saps-business/.../MandatoryBuybackBizServiceImpl.java:90 在 analyze 中直接调用 statementBizService.mandatoryBuyBack，StatementBizServiceImpl.java:1637 的 mandatoryBuyBack 实现无排除校验；MandatoryBuyBackConfigServiceImpl.saveByPush/updateByPush 接受 CRM 推送变更配置。生产库 amp_saps.mandatory_buyback_config：XXT-PRJ-JK-A/C/D 三行 forced_buyback_flag 均为 0（JK-A 另有 compensation_rule='<='），即当前关闭、但未加锁。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 285

<!-- analysis-record:fc514cf3ef22b7db128f7a2084b72b4d7ed169f6ae648a7bbedfd73767339911 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：56
- 记录时间：2026-11-19T11:43:49.597616
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/消费分期/即科医美通汇诚泰保理/2026/202611190001/历史版本/7fa66c11-25c6-438e-8b16-bd749cbbcd2c/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "按项目号补跑回购出账任务会绕过 removeBuyBack 排除配置，指定 projectNo 重跑仍会生成回购对账单",
  "description" : "排除逻辑只作用于日批未传 projectNo 的分支；若运维通过 xxl-job 以 projectNo=XXT-PRJ-JK-A/C/D 参数补跑 buyBackStatementGenerateSchedule，回购对账单仍会照常生成，与需求“系统不再生成回购对账单”的口径存在缺口。需在方案中明确二选一：补跑操作纪律约束，或在代码层（else 分支）增加同一排除校验。",
  "evidence" : "已查证：amp-saps/saps-schedule/src/main/java/com/amp/saps/schedule/statement/BuyBackStatementGenerateSchedule.java:86-96，removeBuyBack 排除仅位于 projectNo 为空的分支；同文件 else 分支（117-146 行）按传入 projectNo 直接执行 statementGenerateBizService.execute(BUYBACK)，不读取排除字典。仓库内本需求草案 dbscript/DML/2026/V5_cancel_buyback_202611190001_dml.sql:16 注释亦自认“指定 projectNo 补跑不读取该排除项”。生产库 amp_saps.business_config 现有 removeBuyBack 仅 XXT-PRJ-DP-A/B 两值，三项目尚未排除。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "允许，采用removeBuyBack的方案",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-19T11:43:49.515374"
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "强制回购分析跑批链路不校验 removeBuyBack，CRM 侧一旦重新开启强制回购配置，回购对账单将恢复生成",
  "description" : "强制回购有三条入口：日批（校验排除）、/api/mandatory/buyback 手动接口（校验排除）、mandatoryBuyBackAnalyzeSchedule 跑批内部直调 statementBizService.mandatoryBuyBack（无任何排除校验）。当前三项目 forced_buyback_flag=0 不触发，但该配置由 CRM 推送可随时变更，若开启即绕过本次排除配置。草案 DML 注释“强制回购接口也使用该配置”仅对手动 API 成立，对跑批内部链路不成立；方案需明确该链路的处理（如同步下线/锁定三项目的强制回购配置，或在 analyze 调用前增加排除校验）。",
  "evidence" : "已查证：全仓 removeBuyBack 仅出现在 BuyBackStatementGenerateSchedule.java:86 与 CapitalProviderController.java:81 两处；amp-saps/saps-business/.../MandatoryBuybackBizServiceImpl.java:90 在 analyze 中直接调用 statementBizService.mandatoryBuyBack，StatementBizServiceImpl.java:1637 的 mandatoryBuyBack 实现无排除校验；MandatoryBuyBackConfigServiceImpl.saveByPush/updateByPush 接受 CRM 推送变更配置。生产库 amp_saps.mandatory_buyback_config：XXT-PRJ-JK-A/C/D 三行 forced_buyback_flag 均为 0（JK-A 另有 compensation_rule='<='），即当前关闭、但未加锁。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 285

<!-- analysis-record:f7f4022ae1cefb1f81cfc52a3f975e4bee6fa9d59e0e5065cc3058112d6fcd5b -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：56
- 记录时间：2026-11-19T11:44:00.676613
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/消费分期/即科医美通汇诚泰保理/2026/202611190001/历史版本/7fa66c11-25c6-438e-8b16-bd749cbbcd2c/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "按项目号补跑回购出账任务会绕过 removeBuyBack 排除配置，指定 projectNo 重跑仍会生成回购对账单",
  "description" : "排除逻辑只作用于日批未传 projectNo 的分支；若运维通过 xxl-job 以 projectNo=XXT-PRJ-JK-A/C/D 参数补跑 buyBackStatementGenerateSchedule，回购对账单仍会照常生成，与需求“系统不再生成回购对账单”的口径存在缺口。需在方案中明确二选一：补跑操作纪律约束，或在代码层（else 分支）增加同一排除校验。",
  "evidence" : "已查证：amp-saps/saps-schedule/src/main/java/com/amp/saps/schedule/statement/BuyBackStatementGenerateSchedule.java:86-96，removeBuyBack 排除仅位于 projectNo 为空的分支；同文件 else 分支（117-146 行）按传入 projectNo 直接执行 statementGenerateBizService.execute(BUYBACK)，不读取排除字典。仓库内本需求草案 dbscript/DML/2026/V5_cancel_buyback_202611190001_dml.sql:16 注释亦自认“指定 projectNo 补跑不读取该排除项”。生产库 amp_saps.business_config 现有 removeBuyBack 仅 XXT-PRJ-DP-A/B 两值，三项目尚未排除。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "允许，采用removeBuyBack的方案",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-19T11:43:49.515374"
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "强制回购分析跑批链路不校验 removeBuyBack，CRM 侧一旦重新开启强制回购配置，回购对账单将恢复生成",
  "description" : "强制回购有三条入口：日批（校验排除）、/api/mandatory/buyback 手动接口（校验排除）、mandatoryBuyBackAnalyzeSchedule 跑批内部直调 statementBizService.mandatoryBuyBack（无任何排除校验）。当前三项目 forced_buyback_flag=0 不触发，但该配置由 CRM 推送可随时变更，若开启即绕过本次排除配置。草案 DML 注释“强制回购接口也使用该配置”仅对手动 API 成立，对跑批内部链路不成立；方案需明确该链路的处理（如同步下线/锁定三项目的强制回购配置，或在 analyze 调用前增加排除校验）。",
  "evidence" : "已查证：全仓 removeBuyBack 仅出现在 BuyBackStatementGenerateSchedule.java:86 与 CapitalProviderController.java:81 两处；amp-saps/saps-business/.../MandatoryBuybackBizServiceImpl.java:90 在 analyze 中直接调用 statementBizService.mandatoryBuyBack，StatementBizServiceImpl.java:1637 的 mandatoryBuyBack 实现无排除校验；MandatoryBuyBackConfigServiceImpl.saveByPush/updateByPush 接受 CRM 推送变更配置。生产库 amp_saps.mandatory_buyback_config：XXT-PRJ-JK-A/C/D 三行 forced_buyback_flag 均为 0（JK-A 另有 compensation_rule='<='），即当前关闭、但未加锁。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "没问题",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-19T11:44:00.573673"
} ]
```



### 临时分析 · 生成需求澄清项 · 需求 275

<!-- analysis-record:87f6cbcf7b41f0a51029bf16cb4500c327f1bb4bb33b1fec5e18a7357c95a50e -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：58
- 记录时间：2026-11-19T18:37:19.711523
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/2026110600006/历史版本/30ccafec-3ab6-46c2-8ec3-9bdd52ede180/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "“服务费”字段的适用范围与判定口径：仅嘉泰还是全部可转让资方，按资方编码还是按转让模式判定",
  "description" : "需求简介写“嘉泰模式转让界面新增服务费字段”，但产品方案 docx 正文为“弹窗界面新增字段‘服务费’，人工填写值（保留两位小数）”，未限定嘉泰。请明确一个口径：(a) 弹窗对所有可转让资方无条件显示；(b) 仅转让模式=3（直租-资金方）时显示；(c) 仅实际转让资金方为 ZJF000027（通汇嘉泰融资租赁（天津））时显示。口径决定弹窗显隐/必填校验、列表与详情字段展示范围以及后续科目下发的适用边界。",
  "evidence" : "已查证：1) intake 275 结构化“需求简介”=“现申请嘉泰模式转让界面新增服务费字段”，而附件《资金方平台新增服务费字段.docx》正文（附件499全文提取）无嘉泰限定，两处口径不一致；2) 前端弹窗 lease-admin/src/views/assetTransfer/assetOrder/components/transferPop.vue 对所有可转让资方统一渲染，无按资方/模式分支的先例；3) 后端转让模式由资金方交易模式决定（AssetTransferOrderServiceImpl.resolveTransferMode，AssetTransferModelEnum：1售后回租/2直租-先行通/3直租-资金方）；4) 生产库 lease_crm.org_funding_party（2026-11 只读核对）：名含“嘉泰”的主体有3家（ZJF000014 通汇嘉泰、ZJF000023 通汇嘉泰融资租赁(天津)、ZJF000027 通汇嘉泰融资租赁(天津)），仅 ZJF000027 配置 transaction_mode='3' 且生效，ZJF000014/ZJF000023 交易模式为空，另 ZJF000026 浙银为'2'——“嘉泰”与“模式3”并非一一对应，无法从现有证据唯一确定判定口径。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "“同时新增服务费科目”的具体口径：科目落点、编码与金额结算方式",
  "description" : "需求仅有一句“同时新增服务费科目”（docx 中仅为标题行“资金方平台新增服务费科目”，无正文），未说明科目编码、挂在哪个系统（对资金方账单费用明细/付款单科目/清结算台账）以及服务费金额如何结算（建单即生成、确认放款时并入资方账单首期、生成单独付款单，还是仅在资产转让单上记录展示）。请给出明确口径，例如：新增对资方账单科目“服务费”（新编码，如1020x），金额=弹窗填写值，在确认放款推送资方账单时随首期/一次性下发；或复用既有10201等。",
  "evidence" : "已查证：1) 附件499 docx全文提取中“资金方平台新增服务费科目”仅为标题行，其后仅描述弹窗/单据/详情三处字段，无科目任何细节；需求描述（source.md/requirementSummary）同样仅尾句“同时新增服务费科目”；需求截图 image.png 为图片仅可取元数据，无法补充信息；2) 既有科目枚举均为对客/付款方向：BillSubjectEnum（10201服务费（资金方）、1020201服务费（先行通）、1020202服务费（业务合作商））、PaymentSlipSubjectEnum（203服务费（一次性收取）、106首期服务费及10201/1020201/1020202首期变体）、RentSaleCustomerBillSubjectNoEnum，均与本需求“对资方收取的服务费”方向不同，无现成可套用口径；3) 对资金方账单推送链路（AssetTransferOrderServiceImpl.syncFundingRepaymentPlan → FunderBillGenerateDTO.funderBillList[].feeDetailList，实现文件885-886行使用 PRINCIPAL/INTEREST 组装）目前科目仅有本金/利息，无服务费科目；lease-saps 侧 FeeDetailDTO 注释列明的测算科目（10001/1020201/1020202）亦不含该服务费。综上无法从需求材料、知识库与代码中确定科目编码、落点与结算方式。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "lease-admin 存在新旧两套资产转让页面树，菜单实际指向未确认，前端改动可能落错页面导致上线后字段不显示",
  "description" : "服务费字段需改动弹窗（transferPop）、资产转让单列表（transferOrder/index.vue）、查看详情（transferOrderDetail.vue）。lease-admin 同时存在 src/views/assetTransfer/**（二期改造树）与 src/views/deviceRentAndSale/assetTransfer/**（旧树，含独立 transferPop.vue，同样调用 POST /assetTransfer/order/transfer）。若生产菜单 component 仍指向旧树而改动只落在新树（或反之），上线后弹窗/列表/详情均不出现服务费字段。实施前需先确认目标环境菜单绑定，并明确是否两套同改。",
  "evidence" : "1) 两套页面并存：lease-admin/src/views/assetTransfer/assetOrder/components/transferPop.vue 与 lease-admin/src/views/deviceRentAndSale/assetTransfer/assetOrder/components/transferPop.vue（375行调用同一 /assetTransfer/order/transfer 接口）；2) 资产转让二期发布手册（lease-admin/docs/assets/prd/2026/asset_transfer_ui_phase2/release-handbook.md §10.6）明确记录已知问题：“lease-admin 两套同名页面树：本分支只改 src/views/assetTransfer/**；若生产菜单 component 指向 src/views/deviceRentAndSale/assetTransfer/**，需单独评估”，且 §0 状态为“生产未发布”；3) 权限中心库 xxt_authing.resource（生产只读）中未检索到叉车租售资产转让菜单行（仅消费金融/设备金融等其他平台的“资产转让单”菜单），菜单实际 component 指向在现有可查证据内无法确认。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "资产转让二期代码尚未上生产，本需求开发基线若不含二期改动，弹窗改造将落在旧实现上并产生合并冲突与口径漂移",
  "description" : "本需求独立分支为 feature/funder_service_fee_2026110600006，而弹窗 transferPop 在二期分支（价款人工改写锁定、可转让资方口径后端收口、切换资方重算等）已被大幅重构且仅部署测试环境。若新分支基于未合入二期的 master 开发，服务费字段的添加位置与交互会基于旧弹窗，后续与二期合并存在冲突及交互口径漂移；上线顺序上本需求需晚于或随二期同窗发布。",
  "evidence" : "1) intake 275 结构化字段 developmentBranchName=feature/funder_service_fee_2026110600006（本需求独立分支）；2) 二期发布手册 §0：分支 feature/asset_transfer_ui_phase2_20260930 涉及 lease-finance/lease-admin 等六仓，“代码仅部署测试环境，生产未发布”，§5 要求按序合入发布；3) 当前 transferPop.vue（src/views/assetTransfer 树）内含二期改造注释（“转让资产价款已被人工改写…切换资金方时解除锁定”“可转让资金方：…口径由后端收口，含嘉泰等跨业务编码资方”），证明弹窗现状即二期版本，开发基线必须包含该批次变更。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 调整研发任务 · 需求 285

<!-- analysis-record:1b5fc7f41b2841278224250c94959ca2fc643b236b0a1c54c1b574709a317fa9 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：129
- 记录时间：2026-11-20T09:13:59.457462
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/消费分期/即科医美通汇诚泰保理/2026/202611190001/历史版本/26a0b815-508f-416e-904d-41f63a65ac93/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "消费分期",
  "projectId" : 4,
  "projectName" : "即科医美通汇诚泰保理",
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/消费分期/即科医美通汇诚泰保理/2026/202611190001/历史版本/26a0b815-508f-416e-904d-41f63a65ac93/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "内部管理要求，针对即科项目回购功能调整。合作方：上海即科智能技术集团有限公司，针对资方是通汇诚泰保理的项目取消回购逻辑，涉及项目：1、XXT-PRJ-JK-A（即科医美通汇诚泰保理）；2、XXT-PRJ-JK-C（即科医美通汇诚泰保理（担保公司））；3、XXT-PRJ-JK-D（即科商品分期通汇诚泰保理）。取消原有\"连4累6\"或\"连3累5\"触发回购的逻辑，系统不再生成回购对账单；后续客户逾期满足代偿规则后触发代偿，生成代偿对账单即可。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "shihao" ],
  "workItems" : [ {
    "title" : "取消回购",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "amp-saps" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "8h",
    "ownerUserName" : "shihao",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "12h",
  "developmentEstimatedEffort" : "8h",
  "testingEstimatedEffort" : "4h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-20T09:13:58.645408",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 生成需求澄清项 · 需求 286

<!-- analysis-record:49af28cd0c3c687a6aaa138f7460e2e1b586b4409611b0e96cc043d061ec423f -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：59
- 记录时间：2026-11-20T10:02:10.065705
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202611170005/历史版本/684f9c63-e8f0-4774-8810-3e787cbbf557/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "恒昌存量宝付（channel 15）扣款通道与存量绑卡的处置口径",
  "description" : "需求只写了\"原恒昌中金商户切换为嘉泰保理中金商户（010583/0006）\"并要求扣款资金100%清分至嘉泰保理，但已查证恒昌生产线上扣款当前跑在宝付渠道且为转付对账单清分（非实时清分），中金渠道在恒昌应用下目前仅配了二维码（subMode=16）费率：若口径是\"全部扣款切到中金010583\"，需新增 21+10/11 费率与绑卡配置，且存量客户宝付协议绑卡在中金商户下不可用（绑卡与支付必须同渠道同商户，协议号按渠道隔离），需求未说明宝付是否停用、存量客户如何重新签约；若口径是\"仅中金通道（二维码及其扣款）切换、宝付照旧\"，则\"扣款100%清分至嘉泰保理\"对宝付扣款不成立。请回复一个明确口径：宝付扣款是否退役，存量绑卡迁移/双通道并行方案。",
  "evidence" : "已查需求原文与附件摘要（仅提中金商户切换、无宝付及绑卡迁移表述）；assets-payment dbscript/DML/V3_hengchang_250521_dml.sql（恒昌 9800005 生产 bind/pay/remit 全套配置在宝付 channel 15）；assets-saps dbscript/DML/V18_payment_share_type_hc_20260515.sql 与 dbscript/DML/2026/V1_jd_pay_switch_202606290003_dml.sql §2.3（恒昌 shareAccountInfo 仅 channel 15、clearingMode=30，注释\"恒昌 9800005 PRD-HC-001 宝付线上目前对账单清分\"）；assets-saps docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md F1（channel_fee_config 9800005 仅 subMode=16 一条中金费率）、F4（\"bind/pay 指向同一商户，避免协议支付跨商户\"）；assets-payment 全部 DML/docs/sql 无恒昌中金扣款（21+10/11）配置脚本。上述证据仍无法唯一确定宝付通道及存量绑卡的处置口径。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "与在途需求 202610230003（恒昌二维码扫码还款）的商户切换目标与清分基线冲突",
  "description" : "202610230003 的上线阻塞项 A7/F4 原计划\"上线前切换并回归\"到恒昌专属中金商户 M1000305（当前测试环境临时走 010583 嘉泰商户），其已通过的测试基线为三方分账（ZJF000029/HZF000055/GLF000003 按固定比例、clearingMode=10）。本需求把商户正式定为嘉泰保理中金商户 010583 并将清分改为 100% 归嘉泰保理，直接推翻 A7 的切换目标并使 A2/B2/F3 的清分测试基线失效。两个需求改动同一批配置（channel_pay_config 商户、shareAccountInfo[\"21\"]、商户 sharingEnabled），若 202610230003 按原方案先上线再执行本需求，会产生二次切换、二次回归；需两个需求统一口径、合并安排上线与回归。",
  "evidence" : "assets-saps/docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md：A7（\"核对 M1000305 pay 商户为恒昌专属商户…上线阻塞项\"）、§七风险移交（\"恒昌专属商户（A7/F4）：当前测试走 010583 嘉泰商户，上线前切换并回归\"）、A2/B2（三方分账固定比例、SplitType=30）、F3（shareAccountInfo[\"21\"] 含三分账方 clearingMode=10）、F4/F5。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "嘉泰保理中金商户 010583 及分账能力依赖 ca-assets 之外的系统，超出需求声明的涉及系统范围",
  "description" : "商户号 010583、结算标识 0006 与聚合分账门禁（商户 sharingEnabled、交易方式配置）实际落点在 xxt-pay 与 intf-paychannel（业务线全局系统，本次授权代码范围之外），assets-payment 侧只经 channel_pay_config 指向 xxt-pay 内部地址上送 merchantNo/asinfo。测试环境已用 010583 验证出码与分账，但生产上线要求 xxt-pay 生产侧 010583 商户、结算标识 0006、恒昌应用交易方式与分账配置同步就绪且 intf-paychannel 部署版本满足门禁；需求\"涉及系统\"仅写了创新保理资产管理系统，该跨系统依赖未纳入交付范围，缺失会导致上线后报\"渠道配置信息加载异常\"或分账指令被门禁拦截。",
  "evidence" : "业务线上下文 involvedSystems/globalSystems（全局系统含 xxt-pay、xxt-pay-gateway、intf-paychannel）；assets-payment docs/sql/V8_cpc_merchant_setup_202606020007_dml.sql（中金 channel_url 指向 xxt-pay-internal.thchengtay.com，member_id/terminal_id 为 xxt-pay 侧商户编码）；qr_repayment 261120-测试用例.md F2/F5 与 §七（\"聚合分账门禁版本：测试环境 intf-paychannel 版本旧于仓库，上线版本需按门禁逻辑配 sharingEnabled\"、A6 \"resv_three 为空报 303002 渠道配置信息加载异常\"）。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "恒昌 statement_project_config 中合作方（HZF000055）收款账户为空，新增合作方服务费对账单将无收款账号",
  "description" : "本需求要复用中恒已落地的合作方服务费对账单（StatementTypeEnum.PARTNER_SERVICE_FEE，资方→合作方方向、明细取 statement_project_config 中 PARTNER 交易方收款账户）。恒昌项目 PRJ-HC-001 的 statement_project_config 中 PARTNER（HZF000055 恒昌众鼎融资租赁有限公司）银行账户字段在初始脚本中全部为空，后续脚本未补；中恒同功能上线时该问题被明确列为\"上线前人工补充，否则合作方服务费对账单明细没有收款账号\"。恒昌若不补充账户，新增的对账单生成后无法结算/打款。",
  "evidence" : "assets-saps dbscript/DML/V8_hengchang_20250526.sql 末段 statement_project_config（PRJ-HC-001，HZF000055 的 accountBankChCn/accountNameCh/accountNumCh/accountBankColCn 等均为空串）；对照 assets-saps dbscript/DML/2026/V7_zh_penalty_split_202610270001_dml.sql 第4节（PRJ-ZH-001 PARTNER 账户为空需上线前人工补充，否则\"合作方服务费对账单明细将没有收款账号\"）；dbscript 全量检索确认无后续脚本更新 PRJ-HC-001 的 statement_project_config。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 286

<!-- analysis-record:716ca924ad889756e0dab3c8c3472bc7811a4ac7fa424e26808691d6b1bd49f4 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：59
- 记录时间：2026-11-20T14:44:43.728248
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202611170005/历史版本/684f9c63-e8f0-4774-8810-3e787cbbf557/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "恒昌存量宝付（channel 15）扣款通道与存量绑卡的处置口径",
  "description" : "需求只写了\"原恒昌中金商户切换为嘉泰保理中金商户（010583/0006）\"并要求扣款资金100%清分至嘉泰保理，但已查证恒昌生产线上扣款当前跑在宝付渠道且为转付对账单清分（非实时清分），中金渠道在恒昌应用下目前仅配了二维码（subMode=16）费率：若口径是\"全部扣款切到中金010583\"，需新增 21+10/11 费率与绑卡配置，且存量客户宝付协议绑卡在中金商户下不可用（绑卡与支付必须同渠道同商户，协议号按渠道隔离），需求未说明宝付是否停用、存量客户如何重新签约；若口径是\"仅中金通道（二维码及其扣款）切换、宝付照旧\"，则\"扣款100%清分至嘉泰保理\"对宝付扣款不成立。请回复一个明确口径：宝付扣款是否退役，存量绑卡迁移/双通道并行方案。",
  "evidence" : "已查需求原文与附件摘要（仅提中金商户切换、无宝付及绑卡迁移表述）；assets-payment dbscript/DML/V3_hengchang_250521_dml.sql（恒昌 9800005 生产 bind/pay/remit 全套配置在宝付 channel 15）；assets-saps dbscript/DML/V18_payment_share_type_hc_20260515.sql 与 dbscript/DML/2026/V1_jd_pay_switch_202606290003_dml.sql §2.3（恒昌 shareAccountInfo 仅 channel 15、clearingMode=30，注释\"恒昌 9800005 PRD-HC-001 宝付线上目前对账单清分\"）；assets-saps docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md F1（channel_fee_config 9800005 仅 subMode=16 一条中金费率）、F4（\"bind/pay 指向同一商户，避免协议支付跨商户\"）；assets-payment 全部 DML/docs/sql 无恒昌中金扣款（21+10/11）配置脚本。上述证据仍无法唯一确定宝付通道及存量绑卡的处置口径。",
  "status" : "RESPONDED",
  "responseText" : "宝付渠道已经不能用了，仅中金渠道需要处理",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-20T14:44:43.544818"
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "与在途需求 202610230003（恒昌二维码扫码还款）的商户切换目标与清分基线冲突",
  "description" : "202610230003 的上线阻塞项 A7/F4 原计划\"上线前切换并回归\"到恒昌专属中金商户 M1000305（当前测试环境临时走 010583 嘉泰商户），其已通过的测试基线为三方分账（ZJF000029/HZF000055/GLF000003 按固定比例、clearingMode=10）。本需求把商户正式定为嘉泰保理中金商户 010583 并将清分改为 100% 归嘉泰保理，直接推翻 A7 的切换目标并使 A2/B2/F3 的清分测试基线失效。两个需求改动同一批配置（channel_pay_config 商户、shareAccountInfo[\"21\"]、商户 sharingEnabled），若 202610230003 按原方案先上线再执行本需求，会产生二次切换、二次回归；需两个需求统一口径、合并安排上线与回归。",
  "evidence" : "assets-saps/docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md：A7（\"核对 M1000305 pay 商户为恒昌专属商户…上线阻塞项\"）、§七风险移交（\"恒昌专属商户（A7/F4）：当前测试走 010583 嘉泰商户，上线前切换并回归\"）、A2/B2（三方分账固定比例、SplitType=30）、F3（shareAccountInfo[\"21\"] 含三分账方 clearingMode=10）、F4/F5。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "嘉泰保理中金商户 010583 及分账能力依赖 ca-assets 之外的系统，超出需求声明的涉及系统范围",
  "description" : "商户号 010583、结算标识 0006 与聚合分账门禁（商户 sharingEnabled、交易方式配置）实际落点在 xxt-pay 与 intf-paychannel（业务线全局系统，本次授权代码范围之外），assets-payment 侧只经 channel_pay_config 指向 xxt-pay 内部地址上送 merchantNo/asinfo。测试环境已用 010583 验证出码与分账，但生产上线要求 xxt-pay 生产侧 010583 商户、结算标识 0006、恒昌应用交易方式与分账配置同步就绪且 intf-paychannel 部署版本满足门禁；需求\"涉及系统\"仅写了创新保理资产管理系统，该跨系统依赖未纳入交付范围，缺失会导致上线后报\"渠道配置信息加载异常\"或分账指令被门禁拦截。",
  "evidence" : "业务线上下文 involvedSystems/globalSystems（全局系统含 xxt-pay、xxt-pay-gateway、intf-paychannel）；assets-payment docs/sql/V8_cpc_merchant_setup_202606020007_dml.sql（中金 channel_url 指向 xxt-pay-internal.thchengtay.com，member_id/terminal_id 为 xxt-pay 侧商户编码）；qr_repayment 261120-测试用例.md F2/F5 与 §七（\"聚合分账门禁版本：测试环境 intf-paychannel 版本旧于仓库，上线版本需按门禁逻辑配 sharingEnabled\"、A6 \"resv_three 为空报 303002 渠道配置信息加载异常\"）。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "恒昌 statement_project_config 中合作方（HZF000055）收款账户为空，新增合作方服务费对账单将无收款账号",
  "description" : "本需求要复用中恒已落地的合作方服务费对账单（StatementTypeEnum.PARTNER_SERVICE_FEE，资方→合作方方向、明细取 statement_project_config 中 PARTNER 交易方收款账户）。恒昌项目 PRJ-HC-001 的 statement_project_config 中 PARTNER（HZF000055 恒昌众鼎融资租赁有限公司）银行账户字段在初始脚本中全部为空，后续脚本未补；中恒同功能上线时该问题被明确列为\"上线前人工补充，否则合作方服务费对账单明细没有收款账号\"。恒昌若不补充账户，新增的对账单生成后无法结算/打款。",
  "evidence" : "assets-saps dbscript/DML/V8_hengchang_20250526.sql 末段 statement_project_config（PRJ-HC-001，HZF000055 的 accountBankChCn/accountNameCh/accountNumCh/accountBankColCn 等均为空串）；对照 assets-saps dbscript/DML/2026/V7_zh_penalty_split_202610270001_dml.sql 第4节（PRJ-ZH-001 PARTNER 账户为空需上线前人工补充，否则\"合作方服务费对账单明细将没有收款账号\"）；dbscript 全量检索确认无后续脚本更新 PRJ-HC-001 的 statement_project_config。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 275

<!-- analysis-record:78e96984418aa64c7972024b776b63db115e48f6749948f85d0a765f40a4e786 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：58
- 记录时间：2026-11-20T15:11:07.227508
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/2026110600006/历史版本/30ccafec-3ab6-46c2-8ec3-9bdd52ede180/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "“服务费”字段的适用范围与判定口径：仅嘉泰还是全部可转让资方，按资方编码还是按转让模式判定",
  "description" : "需求简介写“嘉泰模式转让界面新增服务费字段”，但产品方案 docx 正文为“弹窗界面新增字段‘服务费’，人工填写值（保留两位小数）”，未限定嘉泰。请明确一个口径：(a) 弹窗对所有可转让资方无条件显示；(b) 仅转让模式=3（直租-资金方）时显示；(c) 仅实际转让资金方为 ZJF000027（通汇嘉泰融资租赁（天津））时显示。口径决定弹窗显隐/必填校验、列表与详情字段展示范围以及后续科目下发的适用边界。",
  "evidence" : "已查证：1) intake 275 结构化“需求简介”=“现申请嘉泰模式转让界面新增服务费字段”，而附件《资金方平台新增服务费字段.docx》正文（附件499全文提取）无嘉泰限定，两处口径不一致；2) 前端弹窗 lease-admin/src/views/assetTransfer/assetOrder/components/transferPop.vue 对所有可转让资方统一渲染，无按资方/模式分支的先例；3) 后端转让模式由资金方交易模式决定（AssetTransferOrderServiceImpl.resolveTransferMode，AssetTransferModelEnum：1售后回租/2直租-先行通/3直租-资金方）；4) 生产库 lease_crm.org_funding_party（2026-11 只读核对）：名含“嘉泰”的主体有3家（ZJF000014 通汇嘉泰、ZJF000023 通汇嘉泰融资租赁(天津)、ZJF000027 通汇嘉泰融资租赁(天津)），仅 ZJF000027 配置 transaction_mode='3' 且生效，ZJF000014/ZJF000023 交易模式为空，另 ZJF000026 浙银为'2'——“嘉泰”与“模式3”并非一一对应，无法从现有证据唯一确定判定口径。",
  "status" : "RESPONDED",
  "responseText" : "全部",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-20T15:11:07.013776"
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "“同时新增服务费科目”的具体口径：科目落点、编码与金额结算方式",
  "description" : "需求仅有一句“同时新增服务费科目”（docx 中仅为标题行“资金方平台新增服务费科目”，无正文），未说明科目编码、挂在哪个系统（对资金方账单费用明细/付款单科目/清结算台账）以及服务费金额如何结算（建单即生成、确认放款时并入资方账单首期、生成单独付款单，还是仅在资产转让单上记录展示）。请给出明确口径，例如：新增对资方账单科目“服务费”（新编码，如1020x），金额=弹窗填写值，在确认放款推送资方账单时随首期/一次性下发；或复用既有10201等。",
  "evidence" : "已查证：1) 附件499 docx全文提取中“资金方平台新增服务费科目”仅为标题行，其后仅描述弹窗/单据/详情三处字段，无科目任何细节；需求描述（source.md/requirementSummary）同样仅尾句“同时新增服务费科目”；需求截图 image.png 为图片仅可取元数据，无法补充信息；2) 既有科目枚举均为对客/付款方向：BillSubjectEnum（10201服务费（资金方）、1020201服务费（先行通）、1020202服务费（业务合作商））、PaymentSlipSubjectEnum（203服务费（一次性收取）、106首期服务费及10201/1020201/1020202首期变体）、RentSaleCustomerBillSubjectNoEnum，均与本需求“对资方收取的服务费”方向不同，无现成可套用口径；3) 对资金方账单推送链路（AssetTransferOrderServiceImpl.syncFundingRepaymentPlan → FunderBillGenerateDTO.funderBillList[].feeDetailList，实现文件885-886行使用 PRINCIPAL/INTEREST 组装）目前科目仅有本金/利息，无服务费科目；lease-saps 侧 FeeDetailDTO 注释列明的测算科目（10001/1020201/1020202）亦不含该服务费。综上无法从需求材料、知识库与代码中确定科目编码、落点与结算方式。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "lease-admin 存在新旧两套资产转让页面树，菜单实际指向未确认，前端改动可能落错页面导致上线后字段不显示",
  "description" : "服务费字段需改动弹窗（transferPop）、资产转让单列表（transferOrder/index.vue）、查看详情（transferOrderDetail.vue）。lease-admin 同时存在 src/views/assetTransfer/**（二期改造树）与 src/views/deviceRentAndSale/assetTransfer/**（旧树，含独立 transferPop.vue，同样调用 POST /assetTransfer/order/transfer）。若生产菜单 component 仍指向旧树而改动只落在新树（或反之），上线后弹窗/列表/详情均不出现服务费字段。实施前需先确认目标环境菜单绑定，并明确是否两套同改。",
  "evidence" : "1) 两套页面并存：lease-admin/src/views/assetTransfer/assetOrder/components/transferPop.vue 与 lease-admin/src/views/deviceRentAndSale/assetTransfer/assetOrder/components/transferPop.vue（375行调用同一 /assetTransfer/order/transfer 接口）；2) 资产转让二期发布手册（lease-admin/docs/assets/prd/2026/asset_transfer_ui_phase2/release-handbook.md §10.6）明确记录已知问题：“lease-admin 两套同名页面树：本分支只改 src/views/assetTransfer/**；若生产菜单 component 指向 src/views/deviceRentAndSale/assetTransfer/**，需单独评估”，且 §0 状态为“生产未发布”；3) 权限中心库 xxt_authing.resource（生产只读）中未检索到叉车租售资产转让菜单行（仅消费金融/设备金融等其他平台的“资产转让单”菜单），菜单实际 component 指向在现有可查证据内无法确认。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "资产转让二期代码尚未上生产，本需求开发基线若不含二期改动，弹窗改造将落在旧实现上并产生合并冲突与口径漂移",
  "description" : "本需求独立分支为 feature/funder_service_fee_2026110600006，而弹窗 transferPop 在二期分支（价款人工改写锁定、可转让资方口径后端收口、切换资方重算等）已被大幅重构且仅部署测试环境。若新分支基于未合入二期的 master 开发，服务费字段的添加位置与交互会基于旧弹窗，后续与二期合并存在冲突及交互口径漂移；上线顺序上本需求需晚于或随二期同窗发布。",
  "evidence" : "1) intake 275 结构化字段 developmentBranchName=feature/funder_service_fee_2026110600006（本需求独立分支）；2) 二期发布手册 §0：分支 feature/asset_transfer_ui_phase2_20260930 涉及 lease-finance/lease-admin 等六仓，“代码仅部署测试环境，生产未发布”，§5 要求按序合入发布；3) 当前 transferPop.vue（src/views/assetTransfer 树）内含二期改造注释（“转让资产价款已被人工改写…切换资金方时解除锁定”“可转让资金方：…口径由后端收口，含嘉泰等跨业务编码资方”），证明弹窗现状即二期版本，开发基线必须包含该批次变更。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 275

<!-- analysis-record:64f0dc0f7bec8ae160f7da2ec7578ec752ca515519557c558196503bace60f0a -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：58
- 记录时间：2026-11-23T10:42:36.647003
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/2026110600006/历史版本/30ccafec-3ab6-46c2-8ec3-9bdd52ede180/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "“服务费”字段的适用范围与判定口径：仅嘉泰还是全部可转让资方，按资方编码还是按转让模式判定",
  "description" : "需求简介写“嘉泰模式转让界面新增服务费字段”，但产品方案 docx 正文为“弹窗界面新增字段‘服务费’，人工填写值（保留两位小数）”，未限定嘉泰。请明确一个口径：(a) 弹窗对所有可转让资方无条件显示；(b) 仅转让模式=3（直租-资金方）时显示；(c) 仅实际转让资金方为 ZJF000027（通汇嘉泰融资租赁（天津））时显示。口径决定弹窗显隐/必填校验、列表与详情字段展示范围以及后续科目下发的适用边界。",
  "evidence" : "已查证：1) intake 275 结构化“需求简介”=“现申请嘉泰模式转让界面新增服务费字段”，而附件《资金方平台新增服务费字段.docx》正文（附件499全文提取）无嘉泰限定，两处口径不一致；2) 前端弹窗 lease-admin/src/views/assetTransfer/assetOrder/components/transferPop.vue 对所有可转让资方统一渲染，无按资方/模式分支的先例；3) 后端转让模式由资金方交易模式决定（AssetTransferOrderServiceImpl.resolveTransferMode，AssetTransferModelEnum：1售后回租/2直租-先行通/3直租-资金方）；4) 生产库 lease_crm.org_funding_party（2026-11 只读核对）：名含“嘉泰”的主体有3家（ZJF000014 通汇嘉泰、ZJF000023 通汇嘉泰融资租赁(天津)、ZJF000027 通汇嘉泰融资租赁(天津)），仅 ZJF000027 配置 transaction_mode='3' 且生效，ZJF000014/ZJF000023 交易模式为空，另 ZJF000026 浙银为'2'——“嘉泰”与“模式3”并非一一对应，无法从现有证据唯一确定判定口径。",
  "status" : "RESPONDED",
  "responseText" : "全部",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-20T15:11:07.013776"
}, {
  "index" : 1,
  "itemType" : "QUESTION",
  "title" : "“同时新增服务费科目”的具体口径：科目落点、编码与金额结算方式",
  "description" : "需求仅有一句“同时新增服务费科目”（docx 中仅为标题行“资金方平台新增服务费科目”，无正文），未说明科目编码、挂在哪个系统（对资金方账单费用明细/付款单科目/清结算台账）以及服务费金额如何结算（建单即生成、确认放款时并入资方账单首期、生成单独付款单，还是仅在资产转让单上记录展示）。请给出明确口径，例如：新增对资方账单科目“服务费”（新编码，如1020x），金额=弹窗填写值，在确认放款推送资方账单时随首期/一次性下发；或复用既有10201等。",
  "evidence" : "已查证：1) 附件499 docx全文提取中“资金方平台新增服务费科目”仅为标题行，其后仅描述弹窗/单据/详情三处字段，无科目任何细节；需求描述（source.md/requirementSummary）同样仅尾句“同时新增服务费科目”；需求截图 image.png 为图片仅可取元数据，无法补充信息；2) 既有科目枚举均为对客/付款方向：BillSubjectEnum（10201服务费（资金方）、1020201服务费（先行通）、1020202服务费（业务合作商））、PaymentSlipSubjectEnum（203服务费（一次性收取）、106首期服务费及10201/1020201/1020202首期变体）、RentSaleCustomerBillSubjectNoEnum，均与本需求“对资方收取的服务费”方向不同，无现成可套用口径；3) 对资金方账单推送链路（AssetTransferOrderServiceImpl.syncFundingRepaymentPlan → FunderBillGenerateDTO.funderBillList[].feeDetailList，实现文件885-886行使用 PRINCIPAL/INTEREST 组装）目前科目仅有本金/利息，无服务费科目；lease-saps 侧 FeeDetailDTO 注释列明的测算科目（10001/1020201/1020202）亦不含该服务费。综上无法从需求材料、知识库与代码中确定科目编码、落点与结算方式。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "lease-admin 存在新旧两套资产转让页面树，菜单实际指向未确认，前端改动可能落错页面导致上线后字段不显示",
  "description" : "服务费字段需改动弹窗（transferPop）、资产转让单列表（transferOrder/index.vue）、查看详情（transferOrderDetail.vue）。lease-admin 同时存在 src/views/assetTransfer/**（二期改造树）与 src/views/deviceRentAndSale/assetTransfer/**（旧树，含独立 transferPop.vue，同样调用 POST /assetTransfer/order/transfer）。若生产菜单 component 仍指向旧树而改动只落在新树（或反之），上线后弹窗/列表/详情均不出现服务费字段。实施前需先确认目标环境菜单绑定，并明确是否两套同改。",
  "evidence" : "1) 两套页面并存：lease-admin/src/views/assetTransfer/assetOrder/components/transferPop.vue 与 lease-admin/src/views/deviceRentAndSale/assetTransfer/assetOrder/components/transferPop.vue（375行调用同一 /assetTransfer/order/transfer 接口）；2) 资产转让二期发布手册（lease-admin/docs/assets/prd/2026/asset_transfer_ui_phase2/release-handbook.md §10.6）明确记录已知问题：“lease-admin 两套同名页面树：本分支只改 src/views/assetTransfer/**；若生产菜单 component 指向 src/views/deviceRentAndSale/assetTransfer/**，需单独评估”，且 §0 状态为“生产未发布”；3) 权限中心库 xxt_authing.resource（生产只读）中未检索到叉车租售资产转让菜单行（仅消费金融/设备金融等其他平台的“资产转让单”菜单），菜单实际 component 指向在现有可查证据内无法确认。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "资产转让二期代码尚未上生产，本需求开发基线若不含二期改动，弹窗改造将落在旧实现上并产生合并冲突与口径漂移",
  "description" : "本需求独立分支为 feature/funder_service_fee_2026110600006，而弹窗 transferPop 在二期分支（价款人工改写锁定、可转让资方口径后端收口、切换资方重算等）已被大幅重构且仅部署测试环境。若新分支基于未合入二期的 master 开发，服务费字段的添加位置与交互会基于旧弹窗，后续与二期合并存在冲突及交互口径漂移；上线顺序上本需求需晚于或随二期同窗发布。",
  "evidence" : "1) intake 275 结构化字段 developmentBranchName=feature/funder_service_fee_2026110600006（本需求独立分支）；2) 二期发布手册 §0：分支 feature/asset_transfer_ui_phase2_20260930 涉及 lease-finance/lease-admin 等六仓，“代码仅部署测试环境，生产未发布”，§5 要求按序合入发布；3) 当前 transferPop.vue（src/views/assetTransfer 树）内含二期改造注释（“转让资产价款已被人工改写…切换资金方时解除锁定”“可转让资金方：…口径由后端收口，含嘉泰等跨业务编码资方”），证明弹窗现状即二期版本，开发基线必须包含该批次变更。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "独立分支",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-23T10:42:36.562231"
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 286

<!-- analysis-record:9231b9c7ab2dd8cff40a02bd204b87367127c734c479c7f364d6be8f4abbd9f4 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：59
- 记录时间：2026-11-23T13:21:56.928887
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202611170005/历史版本/684f9c63-e8f0-4774-8810-3e787cbbf557/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "恒昌存量宝付（channel 15）扣款通道与存量绑卡的处置口径",
  "description" : "需求只写了\"原恒昌中金商户切换为嘉泰保理中金商户（010583/0006）\"并要求扣款资金100%清分至嘉泰保理，但已查证恒昌生产线上扣款当前跑在宝付渠道且为转付对账单清分（非实时清分），中金渠道在恒昌应用下目前仅配了二维码（subMode=16）费率：若口径是\"全部扣款切到中金010583\"，需新增 21+10/11 费率与绑卡配置，且存量客户宝付协议绑卡在中金商户下不可用（绑卡与支付必须同渠道同商户，协议号按渠道隔离），需求未说明宝付是否停用、存量客户如何重新签约；若口径是\"仅中金通道（二维码及其扣款）切换、宝付照旧\"，则\"扣款100%清分至嘉泰保理\"对宝付扣款不成立。请回复一个明确口径：宝付扣款是否退役，存量绑卡迁移/双通道并行方案。",
  "evidence" : "已查需求原文与附件摘要（仅提中金商户切换、无宝付及绑卡迁移表述）；assets-payment dbscript/DML/V3_hengchang_250521_dml.sql（恒昌 9800005 生产 bind/pay/remit 全套配置在宝付 channel 15）；assets-saps dbscript/DML/V18_payment_share_type_hc_20260515.sql 与 dbscript/DML/2026/V1_jd_pay_switch_202606290003_dml.sql §2.3（恒昌 shareAccountInfo 仅 channel 15、clearingMode=30，注释\"恒昌 9800005 PRD-HC-001 宝付线上目前对账单清分\"）；assets-saps docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md F1（channel_fee_config 9800005 仅 subMode=16 一条中金费率）、F4（\"bind/pay 指向同一商户，避免协议支付跨商户\"）；assets-payment 全部 DML/docs/sql 无恒昌中金扣款（21+10/11）配置脚本。上述证据仍无法唯一确定宝付通道及存量绑卡的处置口径。",
  "status" : "RESPONDED",
  "responseText" : "宝付渠道已经不能用了，仅中金渠道需要处理",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-20T14:44:43.544818"
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "与在途需求 202610230003（恒昌二维码扫码还款）的商户切换目标与清分基线冲突",
  "description" : "202610230003 的上线阻塞项 A7/F4 原计划\"上线前切换并回归\"到恒昌专属中金商户 M1000305（当前测试环境临时走 010583 嘉泰商户），其已通过的测试基线为三方分账（ZJF000029/HZF000055/GLF000003 按固定比例、clearingMode=10）。本需求把商户正式定为嘉泰保理中金商户 010583 并将清分改为 100% 归嘉泰保理，直接推翻 A7 的切换目标并使 A2/B2/F3 的清分测试基线失效。两个需求改动同一批配置（channel_pay_config 商户、shareAccountInfo[\"21\"]、商户 sharingEnabled），若 202610230003 按原方案先上线再执行本需求，会产生二次切换、二次回归；需两个需求统一口径、合并安排上线与回归。",
  "evidence" : "assets-saps/docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md：A7（\"核对 M1000305 pay 商户为恒昌专属商户…上线阻塞项\"）、§七风险移交（\"恒昌专属商户（A7/F4）：当前测试走 010583 嘉泰商户，上线前切换并回归\"）、A2/B2（三方分账固定比例、SplitType=30）、F3（shareAccountInfo[\"21\"] 含三分账方 clearingMode=10）、F4/F5。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "先处理该需求，后续在处理其他的",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-23T13:21:56.775558"
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "嘉泰保理中金商户 010583 及分账能力依赖 ca-assets 之外的系统，超出需求声明的涉及系统范围",
  "description" : "商户号 010583、结算标识 0006 与聚合分账门禁（商户 sharingEnabled、交易方式配置）实际落点在 xxt-pay 与 intf-paychannel（业务线全局系统，本次授权代码范围之外），assets-payment 侧只经 channel_pay_config 指向 xxt-pay 内部地址上送 merchantNo/asinfo。测试环境已用 010583 验证出码与分账，但生产上线要求 xxt-pay 生产侧 010583 商户、结算标识 0006、恒昌应用交易方式与分账配置同步就绪且 intf-paychannel 部署版本满足门禁；需求\"涉及系统\"仅写了创新保理资产管理系统，该跨系统依赖未纳入交付范围，缺失会导致上线后报\"渠道配置信息加载异常\"或分账指令被门禁拦截。",
  "evidence" : "业务线上下文 involvedSystems/globalSystems（全局系统含 xxt-pay、xxt-pay-gateway、intf-paychannel）；assets-payment docs/sql/V8_cpc_merchant_setup_202606020007_dml.sql（中金 channel_url 指向 xxt-pay-internal.thchengtay.com，member_id/terminal_id 为 xxt-pay 侧商户编码）；qr_repayment 261120-测试用例.md F2/F5 与 §七（\"聚合分账门禁版本：测试环境 intf-paychannel 版本旧于仓库，上线版本需按门禁逻辑配 sharingEnabled\"、A6 \"resv_three 为空报 303002 渠道配置信息加载异常\"）。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "恒昌 statement_project_config 中合作方（HZF000055）收款账户为空，新增合作方服务费对账单将无收款账号",
  "description" : "本需求要复用中恒已落地的合作方服务费对账单（StatementTypeEnum.PARTNER_SERVICE_FEE，资方→合作方方向、明细取 statement_project_config 中 PARTNER 交易方收款账户）。恒昌项目 PRJ-HC-001 的 statement_project_config 中 PARTNER（HZF000055 恒昌众鼎融资租赁有限公司）银行账户字段在初始脚本中全部为空，后续脚本未补；中恒同功能上线时该问题被明确列为\"上线前人工补充，否则合作方服务费对账单明细没有收款账号\"。恒昌若不补充账户，新增的对账单生成后无法结算/打款。",
  "evidence" : "assets-saps dbscript/DML/V8_hengchang_20250526.sql 末段 statement_project_config（PRJ-HC-001，HZF000055 的 accountBankChCn/accountNameCh/accountNumCh/accountBankColCn 等均为空串）；对照 assets-saps dbscript/DML/2026/V7_zh_penalty_split_202610270001_dml.sql 第4节（PRJ-ZH-001 PARTNER 账户为空需上线前人工补充，否则\"合作方服务费对账单明细将没有收款账号\"）；dbscript 全量检索确认无后续脚本更新 PRJ-HC-001 的 statement_project_config。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 286

<!-- analysis-record:0f71ef9d3d70aae445ca05afb12ae2af3bad87655215e30fe15127b0e0f69aed -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：59
- 记录时间：2026-11-23T13:22:06.159937
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202611170005/历史版本/684f9c63-e8f0-4774-8810-3e787cbbf557/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "恒昌存量宝付（channel 15）扣款通道与存量绑卡的处置口径",
  "description" : "需求只写了\"原恒昌中金商户切换为嘉泰保理中金商户（010583/0006）\"并要求扣款资金100%清分至嘉泰保理，但已查证恒昌生产线上扣款当前跑在宝付渠道且为转付对账单清分（非实时清分），中金渠道在恒昌应用下目前仅配了二维码（subMode=16）费率：若口径是\"全部扣款切到中金010583\"，需新增 21+10/11 费率与绑卡配置，且存量客户宝付协议绑卡在中金商户下不可用（绑卡与支付必须同渠道同商户，协议号按渠道隔离），需求未说明宝付是否停用、存量客户如何重新签约；若口径是\"仅中金通道（二维码及其扣款）切换、宝付照旧\"，则\"扣款100%清分至嘉泰保理\"对宝付扣款不成立。请回复一个明确口径：宝付扣款是否退役，存量绑卡迁移/双通道并行方案。",
  "evidence" : "已查需求原文与附件摘要（仅提中金商户切换、无宝付及绑卡迁移表述）；assets-payment dbscript/DML/V3_hengchang_250521_dml.sql（恒昌 9800005 生产 bind/pay/remit 全套配置在宝付 channel 15）；assets-saps dbscript/DML/V18_payment_share_type_hc_20260515.sql 与 dbscript/DML/2026/V1_jd_pay_switch_202606290003_dml.sql §2.3（恒昌 shareAccountInfo 仅 channel 15、clearingMode=30，注释\"恒昌 9800005 PRD-HC-001 宝付线上目前对账单清分\"）；assets-saps docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md F1（channel_fee_config 9800005 仅 subMode=16 一条中金费率）、F4（\"bind/pay 指向同一商户，避免协议支付跨商户\"）；assets-payment 全部 DML/docs/sql 无恒昌中金扣款（21+10/11）配置脚本。上述证据仍无法唯一确定宝付通道及存量绑卡的处置口径。",
  "status" : "RESPONDED",
  "responseText" : "宝付渠道已经不能用了，仅中金渠道需要处理",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-20T14:44:43.544818"
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "与在途需求 202610230003（恒昌二维码扫码还款）的商户切换目标与清分基线冲突",
  "description" : "202610230003 的上线阻塞项 A7/F4 原计划\"上线前切换并回归\"到恒昌专属中金商户 M1000305（当前测试环境临时走 010583 嘉泰商户），其已通过的测试基线为三方分账（ZJF000029/HZF000055/GLF000003 按固定比例、clearingMode=10）。本需求把商户正式定为嘉泰保理中金商户 010583 并将清分改为 100% 归嘉泰保理，直接推翻 A7 的切换目标并使 A2/B2/F3 的清分测试基线失效。两个需求改动同一批配置（channel_pay_config 商户、shareAccountInfo[\"21\"]、商户 sharingEnabled），若 202610230003 按原方案先上线再执行本需求，会产生二次切换、二次回归；需两个需求统一口径、合并安排上线与回归。",
  "evidence" : "assets-saps/docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md：A7（\"核对 M1000305 pay 商户为恒昌专属商户…上线阻塞项\"）、§七风险移交（\"恒昌专属商户（A7/F4）：当前测试走 010583 嘉泰商户，上线前切换并回归\"）、A2/B2（三方分账固定比例、SplitType=30）、F3（shareAccountInfo[\"21\"] 含三分账方 clearingMode=10）、F4/F5。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "先处理该需求，后续在处理其他续期",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-23T13:22:05.988612"
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "嘉泰保理中金商户 010583 及分账能力依赖 ca-assets 之外的系统，超出需求声明的涉及系统范围",
  "description" : "商户号 010583、结算标识 0006 与聚合分账门禁（商户 sharingEnabled、交易方式配置）实际落点在 xxt-pay 与 intf-paychannel（业务线全局系统，本次授权代码范围之外），assets-payment 侧只经 channel_pay_config 指向 xxt-pay 内部地址上送 merchantNo/asinfo。测试环境已用 010583 验证出码与分账，但生产上线要求 xxt-pay 生产侧 010583 商户、结算标识 0006、恒昌应用交易方式与分账配置同步就绪且 intf-paychannel 部署版本满足门禁；需求\"涉及系统\"仅写了创新保理资产管理系统，该跨系统依赖未纳入交付范围，缺失会导致上线后报\"渠道配置信息加载异常\"或分账指令被门禁拦截。",
  "evidence" : "业务线上下文 involvedSystems/globalSystems（全局系统含 xxt-pay、xxt-pay-gateway、intf-paychannel）；assets-payment docs/sql/V8_cpc_merchant_setup_202606020007_dml.sql（中金 channel_url 指向 xxt-pay-internal.thchengtay.com，member_id/terminal_id 为 xxt-pay 侧商户编码）；qr_repayment 261120-测试用例.md F2/F5 与 §七（\"聚合分账门禁版本：测试环境 intf-paychannel 版本旧于仓库，上线版本需按门禁逻辑配 sharingEnabled\"、A6 \"resv_three 为空报 303002 渠道配置信息加载异常\"）。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "恒昌 statement_project_config 中合作方（HZF000055）收款账户为空，新增合作方服务费对账单将无收款账号",
  "description" : "本需求要复用中恒已落地的合作方服务费对账单（StatementTypeEnum.PARTNER_SERVICE_FEE，资方→合作方方向、明细取 statement_project_config 中 PARTNER 交易方收款账户）。恒昌项目 PRJ-HC-001 的 statement_project_config 中 PARTNER（HZF000055 恒昌众鼎融资租赁有限公司）银行账户字段在初始脚本中全部为空，后续脚本未补；中恒同功能上线时该问题被明确列为\"上线前人工补充，否则合作方服务费对账单明细没有收款账号\"。恒昌若不补充账户，新增的对账单生成后无法结算/打款。",
  "evidence" : "assets-saps dbscript/DML/V8_hengchang_20250526.sql 末段 statement_project_config（PRJ-HC-001，HZF000055 的 accountBankChCn/accountNameCh/accountNumCh/accountBankColCn 等均为空串）；对照 assets-saps dbscript/DML/2026/V7_zh_penalty_split_202610270001_dml.sql 第4节（PRJ-ZH-001 PARTNER 账户为空需上线前人工补充，否则\"合作方服务费对账单明细将没有收款账号\"）；dbscript 全量检索确认无后续脚本更新 PRJ-HC-001 的 statement_project_config。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 286

<!-- analysis-record:f2f7e8d169daf4524956741d975a2ceb70f3385dfe38f7c6e80c9e0e917b9405 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：59
- 记录时间：2026-11-23T13:22:09.950615
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202611170005/历史版本/684f9c63-e8f0-4774-8810-3e787cbbf557/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "恒昌存量宝付（channel 15）扣款通道与存量绑卡的处置口径",
  "description" : "需求只写了\"原恒昌中金商户切换为嘉泰保理中金商户（010583/0006）\"并要求扣款资金100%清分至嘉泰保理，但已查证恒昌生产线上扣款当前跑在宝付渠道且为转付对账单清分（非实时清分），中金渠道在恒昌应用下目前仅配了二维码（subMode=16）费率：若口径是\"全部扣款切到中金010583\"，需新增 21+10/11 费率与绑卡配置，且存量客户宝付协议绑卡在中金商户下不可用（绑卡与支付必须同渠道同商户，协议号按渠道隔离），需求未说明宝付是否停用、存量客户如何重新签约；若口径是\"仅中金通道（二维码及其扣款）切换、宝付照旧\"，则\"扣款100%清分至嘉泰保理\"对宝付扣款不成立。请回复一个明确口径：宝付扣款是否退役，存量绑卡迁移/双通道并行方案。",
  "evidence" : "已查需求原文与附件摘要（仅提中金商户切换、无宝付及绑卡迁移表述）；assets-payment dbscript/DML/V3_hengchang_250521_dml.sql（恒昌 9800005 生产 bind/pay/remit 全套配置在宝付 channel 15）；assets-saps dbscript/DML/V18_payment_share_type_hc_20260515.sql 与 dbscript/DML/2026/V1_jd_pay_switch_202606290003_dml.sql §2.3（恒昌 shareAccountInfo 仅 channel 15、clearingMode=30，注释\"恒昌 9800005 PRD-HC-001 宝付线上目前对账单清分\"）；assets-saps docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md F1（channel_fee_config 9800005 仅 subMode=16 一条中金费率）、F4（\"bind/pay 指向同一商户，避免协议支付跨商户\"）；assets-payment 全部 DML/docs/sql 无恒昌中金扣款（21+10/11）配置脚本。上述证据仍无法唯一确定宝付通道及存量绑卡的处置口径。",
  "status" : "RESPONDED",
  "responseText" : "宝付渠道已经不能用了，仅中金渠道需要处理",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-20T14:44:43.544818"
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "与在途需求 202610230003（恒昌二维码扫码还款）的商户切换目标与清分基线冲突",
  "description" : "202610230003 的上线阻塞项 A7/F4 原计划\"上线前切换并回归\"到恒昌专属中金商户 M1000305（当前测试环境临时走 010583 嘉泰商户），其已通过的测试基线为三方分账（ZJF000029/HZF000055/GLF000003 按固定比例、clearingMode=10）。本需求把商户正式定为嘉泰保理中金商户 010583 并将清分改为 100% 归嘉泰保理，直接推翻 A7 的切换目标并使 A2/B2/F3 的清分测试基线失效。两个需求改动同一批配置（channel_pay_config 商户、shareAccountInfo[\"21\"]、商户 sharingEnabled），若 202610230003 按原方案先上线再执行本需求，会产生二次切换、二次回归；需两个需求统一口径、合并安排上线与回归。",
  "evidence" : "assets-saps/docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md：A7（\"核对 M1000305 pay 商户为恒昌专属商户…上线阻塞项\"）、§七风险移交（\"恒昌专属商户（A7/F4）：当前测试走 010583 嘉泰商户，上线前切换并回归\"）、A2/B2（三方分账固定比例、SplitType=30）、F3（shareAccountInfo[\"21\"] 含三分账方 clearingMode=10）、F4/F5。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "先处理该需求，后续再处理其他续期",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-23T13:22:09.879509"
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "嘉泰保理中金商户 010583 及分账能力依赖 ca-assets 之外的系统，超出需求声明的涉及系统范围",
  "description" : "商户号 010583、结算标识 0006 与聚合分账门禁（商户 sharingEnabled、交易方式配置）实际落点在 xxt-pay 与 intf-paychannel（业务线全局系统，本次授权代码范围之外），assets-payment 侧只经 channel_pay_config 指向 xxt-pay 内部地址上送 merchantNo/asinfo。测试环境已用 010583 验证出码与分账，但生产上线要求 xxt-pay 生产侧 010583 商户、结算标识 0006、恒昌应用交易方式与分账配置同步就绪且 intf-paychannel 部署版本满足门禁；需求\"涉及系统\"仅写了创新保理资产管理系统，该跨系统依赖未纳入交付范围，缺失会导致上线后报\"渠道配置信息加载异常\"或分账指令被门禁拦截。",
  "evidence" : "业务线上下文 involvedSystems/globalSystems（全局系统含 xxt-pay、xxt-pay-gateway、intf-paychannel）；assets-payment docs/sql/V8_cpc_merchant_setup_202606020007_dml.sql（中金 channel_url 指向 xxt-pay-internal.thchengtay.com，member_id/terminal_id 为 xxt-pay 侧商户编码）；qr_repayment 261120-测试用例.md F2/F5 与 §七（\"聚合分账门禁版本：测试环境 intf-paychannel 版本旧于仓库，上线版本需按门禁逻辑配 sharingEnabled\"、A6 \"resv_three 为空报 303002 渠道配置信息加载异常\"）。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "恒昌 statement_project_config 中合作方（HZF000055）收款账户为空，新增合作方服务费对账单将无收款账号",
  "description" : "本需求要复用中恒已落地的合作方服务费对账单（StatementTypeEnum.PARTNER_SERVICE_FEE，资方→合作方方向、明细取 statement_project_config 中 PARTNER 交易方收款账户）。恒昌项目 PRJ-HC-001 的 statement_project_config 中 PARTNER（HZF000055 恒昌众鼎融资租赁有限公司）银行账户字段在初始脚本中全部为空，后续脚本未补；中恒同功能上线时该问题被明确列为\"上线前人工补充，否则合作方服务费对账单明细没有收款账号\"。恒昌若不补充账户，新增的对账单生成后无法结算/打款。",
  "evidence" : "assets-saps dbscript/DML/V8_hengchang_20250526.sql 末段 statement_project_config（PRJ-HC-001，HZF000055 的 accountBankChCn/accountNameCh/accountNumCh/accountBankColCn 等均为空串）；对照 assets-saps dbscript/DML/2026/V7_zh_penalty_split_202610270001_dml.sql 第4节（PRJ-ZH-001 PARTNER 账户为空需上线前人工补充，否则\"合作方服务费对账单明细将没有收款账号\"）；dbscript 全量检索确认无后续脚本更新 PRJ-HC-001 的 statement_project_config。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 286

<!-- analysis-record:81aff90b3f44ba15e88f6ff2d90946fba1256b483e90a7c9708d1851fca1ad5f -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：59
- 记录时间：2026-11-23T13:22:34.996863
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202611170005/历史版本/684f9c63-e8f0-4774-8810-3e787cbbf557/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "恒昌存量宝付（channel 15）扣款通道与存量绑卡的处置口径",
  "description" : "需求只写了\"原恒昌中金商户切换为嘉泰保理中金商户（010583/0006）\"并要求扣款资金100%清分至嘉泰保理，但已查证恒昌生产线上扣款当前跑在宝付渠道且为转付对账单清分（非实时清分），中金渠道在恒昌应用下目前仅配了二维码（subMode=16）费率：若口径是\"全部扣款切到中金010583\"，需新增 21+10/11 费率与绑卡配置，且存量客户宝付协议绑卡在中金商户下不可用（绑卡与支付必须同渠道同商户，协议号按渠道隔离），需求未说明宝付是否停用、存量客户如何重新签约；若口径是\"仅中金通道（二维码及其扣款）切换、宝付照旧\"，则\"扣款100%清分至嘉泰保理\"对宝付扣款不成立。请回复一个明确口径：宝付扣款是否退役，存量绑卡迁移/双通道并行方案。",
  "evidence" : "已查需求原文与附件摘要（仅提中金商户切换、无宝付及绑卡迁移表述）；assets-payment dbscript/DML/V3_hengchang_250521_dml.sql（恒昌 9800005 生产 bind/pay/remit 全套配置在宝付 channel 15）；assets-saps dbscript/DML/V18_payment_share_type_hc_20260515.sql 与 dbscript/DML/2026/V1_jd_pay_switch_202606290003_dml.sql §2.3（恒昌 shareAccountInfo 仅 channel 15、clearingMode=30，注释\"恒昌 9800005 PRD-HC-001 宝付线上目前对账单清分\"）；assets-saps docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md F1（channel_fee_config 9800005 仅 subMode=16 一条中金费率）、F4（\"bind/pay 指向同一商户，避免协议支付跨商户\"）；assets-payment 全部 DML/docs/sql 无恒昌中金扣款（21+10/11）配置脚本。上述证据仍无法唯一确定宝付通道及存量绑卡的处置口径。",
  "status" : "RESPONDED",
  "responseText" : "宝付渠道已经不能用了，仅中金渠道需要处理",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-20T14:44:43.544818"
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "与在途需求 202610230003（恒昌二维码扫码还款）的商户切换目标与清分基线冲突",
  "description" : "202610230003 的上线阻塞项 A7/F4 原计划\"上线前切换并回归\"到恒昌专属中金商户 M1000305（当前测试环境临时走 010583 嘉泰商户），其已通过的测试基线为三方分账（ZJF000029/HZF000055/GLF000003 按固定比例、clearingMode=10）。本需求把商户正式定为嘉泰保理中金商户 010583 并将清分改为 100% 归嘉泰保理，直接推翻 A7 的切换目标并使 A2/B2/F3 的清分测试基线失效。两个需求改动同一批配置（channel_pay_config 商户、shareAccountInfo[\"21\"]、商户 sharingEnabled），若 202610230003 按原方案先上线再执行本需求，会产生二次切换、二次回归；需两个需求统一口径、合并安排上线与回归。",
  "evidence" : "assets-saps/docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md：A7（\"核对 M1000305 pay 商户为恒昌专属商户…上线阻塞项\"）、§七风险移交（\"恒昌专属商户（A7/F4）：当前测试走 010583 嘉泰商户，上线前切换并回归\"）、A2/B2（三方分账固定比例、SplitType=30）、F3（shareAccountInfo[\"21\"] 含三分账方 clearingMode=10）、F4/F5。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "先处理该需求，后续再处理其他续期",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-23T13:22:09.879509"
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "嘉泰保理中金商户 010583 及分账能力依赖 ca-assets 之外的系统，超出需求声明的涉及系统范围",
  "description" : "商户号 010583、结算标识 0006 与聚合分账门禁（商户 sharingEnabled、交易方式配置）实际落点在 xxt-pay 与 intf-paychannel（业务线全局系统，本次授权代码范围之外），assets-payment 侧只经 channel_pay_config 指向 xxt-pay 内部地址上送 merchantNo/asinfo。测试环境已用 010583 验证出码与分账，但生产上线要求 xxt-pay 生产侧 010583 商户、结算标识 0006、恒昌应用交易方式与分账配置同步就绪且 intf-paychannel 部署版本满足门禁；需求\"涉及系统\"仅写了创新保理资产管理系统，该跨系统依赖未纳入交付范围，缺失会导致上线后报\"渠道配置信息加载异常\"或分账指令被门禁拦截。",
  "evidence" : "业务线上下文 involvedSystems/globalSystems（全局系统含 xxt-pay、xxt-pay-gateway、intf-paychannel）；assets-payment docs/sql/V8_cpc_merchant_setup_202606020007_dml.sql（中金 channel_url 指向 xxt-pay-internal.thchengtay.com，member_id/terminal_id 为 xxt-pay 侧商户编码）；qr_repayment 261120-测试用例.md F2/F5 与 §七（\"聚合分账门禁版本：测试环境 intf-paychannel 版本旧于仓库，上线版本需按门禁逻辑配 sharingEnabled\"、A6 \"resv_three 为空报 303002 渠道配置信息加载异常\"）。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "会一起改动",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-23T13:22:34.930212"
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "恒昌 statement_project_config 中合作方（HZF000055）收款账户为空，新增合作方服务费对账单将无收款账号",
  "description" : "本需求要复用中恒已落地的合作方服务费对账单（StatementTypeEnum.PARTNER_SERVICE_FEE，资方→合作方方向、明细取 statement_project_config 中 PARTNER 交易方收款账户）。恒昌项目 PRJ-HC-001 的 statement_project_config 中 PARTNER（HZF000055 恒昌众鼎融资租赁有限公司）银行账户字段在初始脚本中全部为空，后续脚本未补；中恒同功能上线时该问题被明确列为\"上线前人工补充，否则合作方服务费对账单明细没有收款账号\"。恒昌若不补充账户，新增的对账单生成后无法结算/打款。",
  "evidence" : "assets-saps dbscript/DML/V8_hengchang_20250526.sql 末段 statement_project_config（PRJ-HC-001，HZF000055 的 accountBankChCn/accountNameCh/accountNumCh/accountBankColCn 等均为空串）；对照 assets-saps dbscript/DML/2026/V7_zh_penalty_split_202610270001_dml.sql 第4节（PRJ-ZH-001 PARTNER 账户为空需上线前人工补充，否则\"合作方服务费对账单明细将没有收款账号\"）；dbscript 全量检索确认无后续脚本更新 PRJ-HC-001 的 statement_project_config。",
  "status" : "PENDING",
  "responseText" : null,
  "respondedBy" : null,
  "respondedAt" : null
} ]
```



### 临时分析 · 回复需求澄清项 · 需求 286

<!-- analysis-record:bbcceadb15d34a0e6d00bf0983b921397aa87d08bfd684c74abbd6838153bd68 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：59
- 记录时间：2026-11-23T13:22:51.411941
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/嘉泰租赁资产保理/中恒信合乘用车保理项目/2026/202611170005/历史版本/684f9c63-e8f0-4774-8810-3e787cbbf557/source.md>)

```json
[ {
  "index" : 0,
  "itemType" : "QUESTION",
  "title" : "恒昌存量宝付（channel 15）扣款通道与存量绑卡的处置口径",
  "description" : "需求只写了\"原恒昌中金商户切换为嘉泰保理中金商户（010583/0006）\"并要求扣款资金100%清分至嘉泰保理，但已查证恒昌生产线上扣款当前跑在宝付渠道且为转付对账单清分（非实时清分），中金渠道在恒昌应用下目前仅配了二维码（subMode=16）费率：若口径是\"全部扣款切到中金010583\"，需新增 21+10/11 费率与绑卡配置，且存量客户宝付协议绑卡在中金商户下不可用（绑卡与支付必须同渠道同商户，协议号按渠道隔离），需求未说明宝付是否停用、存量客户如何重新签约；若口径是\"仅中金通道（二维码及其扣款）切换、宝付照旧\"，则\"扣款100%清分至嘉泰保理\"对宝付扣款不成立。请回复一个明确口径：宝付扣款是否退役，存量绑卡迁移/双通道并行方案。",
  "evidence" : "已查需求原文与附件摘要（仅提中金商户切换、无宝付及绑卡迁移表述）；assets-payment dbscript/DML/V3_hengchang_250521_dml.sql（恒昌 9800005 生产 bind/pay/remit 全套配置在宝付 channel 15）；assets-saps dbscript/DML/V18_payment_share_type_hc_20260515.sql 与 dbscript/DML/2026/V1_jd_pay_switch_202606290003_dml.sql §2.3（恒昌 shareAccountInfo 仅 channel 15、clearingMode=30，注释\"恒昌 9800005 PRD-HC-001 宝付线上目前对账单清分\"）；assets-saps docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md F1（channel_fee_config 9800005 仅 subMode=16 一条中金费率）、F4（\"bind/pay 指向同一商户，避免协议支付跨商户\"）；assets-payment 全部 DML/docs/sql 无恒昌中金扣款（21+10/11）配置脚本。上述证据仍无法唯一确定宝付通道及存量绑卡的处置口径。",
  "status" : "RESPONDED",
  "responseText" : "宝付渠道已经不能用了，仅中金渠道需要处理",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-20T14:44:43.544818"
}, {
  "index" : 0,
  "itemType" : "RISK",
  "title" : "与在途需求 202610230003（恒昌二维码扫码还款）的商户切换目标与清分基线冲突",
  "description" : "202610230003 的上线阻塞项 A7/F4 原计划\"上线前切换并回归\"到恒昌专属中金商户 M1000305（当前测试环境临时走 010583 嘉泰商户），其已通过的测试基线为三方分账（ZJF000029/HZF000055/GLF000003 按固定比例、clearingMode=10）。本需求把商户正式定为嘉泰保理中金商户 010583 并将清分改为 100% 归嘉泰保理，直接推翻 A7 的切换目标并使 A2/B2/F3 的清分测试基线失效。两个需求改动同一批配置（channel_pay_config 商户、shareAccountInfo[\"21\"]、商户 sharingEnabled），若 202610230003 按原方案先上线再执行本需求，会产生二次切换、二次回归；需两个需求统一口径、合并安排上线与回归。",
  "evidence" : "assets-saps/docs/plan/feature/qr_repayment_202610230003/261120-测试用例.md：A7（\"核对 M1000305 pay 商户为恒昌专属商户…上线阻塞项\"）、§七风险移交（\"恒昌专属商户（A7/F4）：当前测试走 010583 嘉泰商户，上线前切换并回归\"）、A2/B2（三方分账固定比例、SplitType=30）、F3（shareAccountInfo[\"21\"] 含三分账方 clearingMode=10）、F4/F5。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "先处理该需求，后续再处理其他续期",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-23T13:22:09.879509"
}, {
  "index" : 1,
  "itemType" : "RISK",
  "title" : "嘉泰保理中金商户 010583 及分账能力依赖 ca-assets 之外的系统，超出需求声明的涉及系统范围",
  "description" : "商户号 010583、结算标识 0006 与聚合分账门禁（商户 sharingEnabled、交易方式配置）实际落点在 xxt-pay 与 intf-paychannel（业务线全局系统，本次授权代码范围之外），assets-payment 侧只经 channel_pay_config 指向 xxt-pay 内部地址上送 merchantNo/asinfo。测试环境已用 010583 验证出码与分账，但生产上线要求 xxt-pay 生产侧 010583 商户、结算标识 0006、恒昌应用交易方式与分账配置同步就绪且 intf-paychannel 部署版本满足门禁；需求\"涉及系统\"仅写了创新保理资产管理系统，该跨系统依赖未纳入交付范围，缺失会导致上线后报\"渠道配置信息加载异常\"或分账指令被门禁拦截。",
  "evidence" : "业务线上下文 involvedSystems/globalSystems（全局系统含 xxt-pay、xxt-pay-gateway、intf-paychannel）；assets-payment docs/sql/V8_cpc_merchant_setup_202606020007_dml.sql（中金 channel_url 指向 xxt-pay-internal.thchengtay.com，member_id/terminal_id 为 xxt-pay 侧商户编码）；qr_repayment 261120-测试用例.md F2/F5 与 §七（\"聚合分账门禁版本：测试环境 intf-paychannel 版本旧于仓库，上线版本需按门禁逻辑配 sharingEnabled\"、A6 \"resv_three 为空报 303002 渠道配置信息加载异常\"）。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "会一起改动",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-23T13:22:34.930212"
}, {
  "index" : 2,
  "itemType" : "RISK",
  "title" : "恒昌 statement_project_config 中合作方（HZF000055）收款账户为空，新增合作方服务费对账单将无收款账号",
  "description" : "本需求要复用中恒已落地的合作方服务费对账单（StatementTypeEnum.PARTNER_SERVICE_FEE，资方→合作方方向、明细取 statement_project_config 中 PARTNER 交易方收款账户）。恒昌项目 PRJ-HC-001 的 statement_project_config 中 PARTNER（HZF000055 恒昌众鼎融资租赁有限公司）银行账户字段在初始脚本中全部为空，后续脚本未补；中恒同功能上线时该问题被明确列为\"上线前人工补充，否则合作方服务费对账单明细没有收款账号\"。恒昌若不补充账户，新增的对账单生成后无法结算/打款。",
  "evidence" : "assets-saps dbscript/DML/V8_hengchang_20250526.sql 末段 statement_project_config（PRJ-HC-001，HZF000055 的 accountBankChCn/accountNameCh/accountNumCh/accountBankColCn 等均为空串）；对照 assets-saps dbscript/DML/2026/V7_zh_penalty_split_202610270001_dml.sql 第4节（PRJ-ZH-001 PARTNER 账户为空需上线前人工补充，否则\"合作方服务费对账单明细将没有收款账号\"）；dbscript 全量检索确认无后续脚本更新 PRJ-HC-001 的 statement_project_config。",
  "status" : "ACKNOWLEDGED",
  "responseText" : "人工核销，无需配置线上收款账户",
  "respondedBy" : "admin",
  "respondedAt" : "2026-11-23T13:22:51.350278"
} ]
```



### 临时分析 · 调整研发任务 · 需求 275

<!-- analysis-record:f4b553e11b607c213b6763180f7f766919097a82af700d7ac021b0b64a009357 -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：134
- 记录时间：2026-11-24T14:20:25.907576
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/2026110600006/历史版本/36fa40da-f3ab-41fc-aa4c-5dc869bfc4b5/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000008",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/BL000008/BL000008/2026/2026110600006/历史版本/36fa40da-f3ab-41fc-aa4c-5dc869bfc4b5/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "因目前嘉泰直租模式下新增服务费，申请在资金方平台【资产转让单】点击【资产转让】按钮的弹窗界面新增字段“服务费”，由人工填写（保留两位小数）；点击【确定】生成资产转让单后，单据及【查看详情】页面均新增“服务费”字段，取弹窗填写值。同时新增服务费科目。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "shihao" ],
  "workItems" : [ {
    "title" : "资产转让单新增服务费",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-admin", "lease-finance" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "shihao",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "8h",
  "developmentEstimatedEffort" : "4h",
  "testingEstimatedEffort" : "4h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "RESERVED",
  "zentaoSyncMessage" : "禅道同步接口已预留，当前不会调用禅道。",
  "generatedAt" : "2026-11-24T14:20:25.177571",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```



### 临时分析 · 调整研发任务 · 需求 275

<!-- analysis-record:7969088f9bd4033c9cfef8fe307493b781d0e34b829413c299d7e243ead9544e -->

- 状态：待确认／过程记录，不代表正式审批
- 分析 ID：134
- 记录时间：2026-11-24T14:20:32.939156
- 操作者：admin
- 来源：[来源版本](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirement-sources/BL000008/BL000008/2026/2026110600006/历史版本/36fa40da-f3ab-41fc-aa4c-5dc869bfc4b5/source.md>)

```json
{
  "status" : "DRAFT",
  "businessLine" : "BL000008",
  "projectId" : null,
  "projectName" : null,
  "repositoryUrl" : null,
  "requirementRawPath" : "requirement-sources/BL000008/BL000008/2026/2026110600006/历史版本/36fa40da-f3ab-41fc-aa4c-5dc869bfc4b5/source.md",
  "requirementWikiPath" : null,
  "requirementWikiUrl" : null,
  "summary" : "因目前嘉泰直租模式下新增服务费，申请在资金方平台【资产转让单】点击【资产转让】按钮的弹窗界面新增字段“服务费”，由人工填写（保留两位小数）；点击【确定】生成资产转让单后，单据及【查看详情】页面均新增“服务费”字段，取弹窗填写值。同时新增服务费科目。",
  "requirementChangePoints" : [ ],
  "impactedModules" : [ ],
  "risks" : [ ],
  "questions" : [ ],
  "developerPool" : [ "shihao" ],
  "workItems" : [ {
    "title" : "资产转让单新增服务费",
    "description" : null,
    "requirementChangePoint" : null,
    "taskType" : "CODE_CHANGE",
    "targetResources" : [ ],
    "changePoints" : [ ],
    "systemTags" : [ "lease-admin", "lease-finance" ],
    "evidenceRefs" : [ ],
    "confidence" : "MEDIUM",
    "moduleName" : null,
    "relatedFiles" : [ ],
    "estimatedEffort" : "4h",
    "ownerUserName" : "shihao",
    "priority" : null,
    "plannedStartDate" : null,
    "plannedEndDate" : null,
    "dependency" : null,
    "risk" : null,
    "zentaoTaskId" : null,
    "zentaoTaskUrl" : null
  } ],
  "totalEstimatedEffort" : "8h",
  "developmentEstimatedEffort" : "4h",
  "testingEstimatedEffort" : "4h",
  "plannedDueDate" : null,
  "plannedTestingStartDate" : null,
  "plannedTestingEndDate" : null,
  "plannedReleaseDate" : null,
  "zentaoSyncStatus" : "FAILED",
  "zentaoSyncMessage" : "禅道任务同步：0 新建、0 更新、0 取消、1 失败（迭代 880）。失败明细：资产转让单新增服务费：任务评估失败，请查看后端日志",
  "generatedAt" : "2026-11-24T14:20:25.177571",
  "generator" : "manual",
  "plannedDevelopmentStartDate" : null,
  "sddDocuments" : null,
  "zentaoSyncedTaskIds" : null
}
```


## 2026-10-30 物流平台叉车智能柜中间件升级方案整理

- 根据用户两份消费分期参考文件，核对 WorkHub 业务线映射与三个 GitLab 组的远程 master，整理[三条业务线升级方案](requirements/业务线/BL000006-物流平台/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/README.md)，需求编码 REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba。
- 源码入口数：物流 256（共享合同/业财 21 单列）、叉车 71、智能柜 67，另两套公共 MQ 各 1；不等于线上启用任务数。
- 特别处理 lease MQ 参数、手工任务返回值、独立 BOM、中间父 POM 与共享注册域；当前 thctay public 远程升级源码日志默认 90 天，已注明与参考清单 60 天的差异。
- 本轮只整理文档和静态证据，未安装依赖、修改业务源码/配置、构建、触发任务或部署；实际环境和发布计划见各方案待落实项。

## 2026-11-03 需求管理菜单拆分与需求看板

- 一级菜单"需求管理"下拆出"需求清单"（原需求管理页，路由 /demands/list，旧地址 /demands 与 /intake 重定向保留）与"需求看板"（/demands/board）。
- 后端新增 GET /api/intake-records/board（IntakeController.board，权限 intake:record:view，参数 includeClosed 默认 false 排除已完成/终止关闭），IntakeService.board 按研发负责人聚合：任务数取最新研发拆解草稿 workItems 数量，工时沿用 IntakeProcessInfoSupport.estimates 口径（人工覆盖优先），开始时间优先实际开发开始日期回退计划开发日期，截止时间优先预计完成日期回退预估上线日期；负责人昵称经 UserMapper.findActiveUsers 映射，无负责人需求归入"未指定负责人"列。
- 前端新增 views/intake/IntakeBoardView.vue（按人分列的看板，支持关键词过滤与含终态开关，逾期截止日红色高亮），类型与接口封装在 types/work-item.ts、api/intake.ts。
- 验证：mvn 编译通过；workhub-service Intake*/DevelopmentAnalysis* 测试 456 例全通过（含新增 IntakeServiceBoardTest 3 例）；workhub-controller 测试通过；workhub-web npm run build（vue-tsc + vite）通过。

## 2026-11-03 需求看板增加任务负责人维度

- 看板接口 GET /api/intake-records/board 新增可选参数 groupBy：demandOwner（默认，按需求研发负责人，该人对整条需求负责）或 taskOwner（按需求内任务项负责人聚合，同一需求按任务拆分到多列，任务数/预估工时展示该人名下数值并附需求合计）。
- 口径：任务项工时按各任务 estimatedEffort 归一化求和（如 1d 记 8h）；无主任务归入"未指定负责人"列；整条需求尚无任何指派任务时以需求研发负责人兜底，避免评估未完成的需求从看板消失。
- 前端看板工具栏新增"按需求负责人 / 按任务负责人"切换（el-radio-group），卡片在两维度数值不一致时显示 x/共N 项、y/共Z 提示行。
- 验证：IntakeServiceBoardTest 扩到 6 例（任务维度聚合、无主任务归列、非法维度拒绝、需求维度回归）全部通过；Intake*/DevelopmentAnalysis* 回归 134 例 0 失败；workhub-web npm run build 通过。

## 2026-11-03 需求看板任务维度取消兜底

- 按用户要求移除任务负责人维度的需求负责人兜底：有任务项的需求严格按任务项负责人归列，未指派任务归入"未指定负责人"列；没有任务项（无草稿或任务项为空）的需求不进入任务维度看板。需求负责人维度行为不变。
- IntakeServiceBoardTest 扩到 7 例（新增"无任务项需求在任务维度不出现、在需求维度正常出现"用例），全部通过；workhub-controller 编译通过。
- 提醒：本地 IDEA 启动的后端进程（10:23）早于任务维度代码编译，需重启后端后切换维度才生效。

## 2026-11-03 人员身份统一收敛到账号管理（sys_user）

- 背景：「团队」(pm_developer_resource) 与「账号管理」(sys_user) 两套人员数据漂移，录入表单负责人为自由文本、任务评估保存会把任意名字 upsert 进团队表，导致同一人出现两个身份（石浩/shihao、尹军/yinjun），需求负责人看板裂成两列。
- 代码：UserMapper.findActiveUsers 改查 sys_user（status=ACTIVE 且未删除），全系统人员候选唯一来源；删除任务评估时自动登记人员（upsertActiveUser）；任务负责人、需求录入负责人、工作项指派、团队名单新增/修改均校验必须是活跃账号，否则报"请先在账号管理中创建"；业务线成员展示名改为 JOIN sys_user（flyway V10 删除 member_display_name 冗余列）；DeveloperResourceService.listOptions 同步切 sys_user，前端 /system/developer-options 自动变为账号候选。
- 前端：需求录入的"研发负责人"从自由文本改为活跃账号下拉（去掉硬编码默认"石浩"），任务评估两处负责人下拉去掉 allow-create，选项统一显示"昵称（账号）"。
- 数据（test 库 10.10.116.53:3308）：补建 6 个账号 caobin(曹彬)/wangwenlong(王文龙)/jiangmin(江敏)/yufanghua/yanggang(杨刚)/dujunchen(杜俊辰)，初始密码 Workhub@2026 且 must_change_password=1，角色 DEV；需求负责人 石浩50+王文龙1、工作项 石浩/尹军/王文龙/徐杰妮、待办 杨刚/杜俊辰、任务项草稿 吕刘浩/尹军/张华君/徐杰妮/曹彬/江敏/王文龙/石浩 全部改写为账号名并留历史；团队表删除中文名键行、补齐 caofeng/zhanghuajun，15 行全部对齐账号。
- 历史保留：8 条已完成需求负责人为多人合并文本（如"蔡锐、尹军、 韩猛、张华君"）或历史人名（耿庆阳、林成名/林成铭），属档案数据不改写；"WorkHub 管理员"为系统待办虚拟处理人，保留。
- 待办：prod 库（aslight.cn:30364）存在同类问题（石浩 25 条等），代码部署后需在 prod 重复数据清洗；本地后端需重启生效（flyway 会执行 V10）。

## 2026-11-03 需求看板默认任务维度 + 卡片跳转需求清单

- 看板默认维度改为"按任务负责人"（页面默认值，接口默认仍为 demandOwner）。
- 卡片需求名称改为可点击，跳转 /demands/list?intakeId=xx；需求清单监听路由 query 自动打开对应需求详情，打开后清除 query 避免重复触发。前端构建通过。

## 2026-11-03 业务线文档统一归档到 docs/业务线

- 按用户要求建立 `docs/业务线/{业务线编码}-{业务线名称}/` 统一归档：`现状.md`、`迭代日志.md`、`需求迭代/`（过往历史需求）；覆盖全部 12 条业务线（编码与名称取自 WorkHub 业务线配置），无资料的业务线（账单管理、嘉泰设备租赁、服务费分期保理、工作台）仅建骨架；共享支付系统（xxt-pay、intf-paychannel 等公共系统，无业务线编码）按同样结构归入 `docs/业务线/共享支付系统/`。
- 迁移：`docs/system/业务线/{编码}-{名称}/系统设计.md` 改名迁为各业务线 `现状.md`（BL000004 的迭代日志随迁）；`docs/requirements/{业务线}/需求迭代/` 的研发评估与 `docs/requirements/业务线/{编码}-{名称}/` 的 SDD、验证证据全部迁入对应 `需求迭代/`；`docs/requirements/index.md`（已确认自动评估索引）迁为 `docs/业务线/index.md`。迁移清单 121 项见 `docs/migrations/2026-11-03.json`。
- 仅修正现行指引与迁移文件中指向旧路径的链接（根/requirements/system AGENTS、docs/README.md、company/projects/保费分期 三份、各迁移文件内部互链）；`历史版本/`、原始快照与历史日志原文不改写。
- 代码同步适配研发评估自动归档路径：`ProjectKnowledgeBaseService.buildWikiRequirementPath` 改为 `docs/业务线/{业务线编码}-{业务线名称}/需求迭代/...`（无编码或编码与名称相同时退回名称），已确认评估索引改写 `docs/业务线/index.md`；`McpResourceService` 的 `requirementWikiPathPattern` 与 `wikiPublishRule`、`McpRuntimeService.businessLineWikiRoot` 同步更新；`RepositoryDocumentPaths.migrateRelativePath` 的知识库历史 wiki 前缀改映射到 `业务线/`。
- 验证：workhub-service 定向测试 6 类 47 项通过（KnowledgeArchiveIntegrationTest 12、DevelopmentPlanFolderServiceTest 新增编码前缀目录用例、McpRuntimeServiceTest、RepositoryDocumentPathsTest、DevelopmentAnalysisServiceTest、CodexCliDevelopmentAnalysisGeneratorTest）；应用未重启，运行生效待部署后验证；本轮未提交。

## 2026-11-03 WorkHub 平台文档并入 BL000012-工作台，删除 docs/requirements

- 按用户要求，WorkHub 平台即业务线 `BL000012-工作台`，平台文档并入业务线目录：`docs/requirements/workhub-server/` 的 9 个平台需求 SDD 迁入 `docs/业务线/BL000012-工作台/需求迭代/`；`docs/system/系统设计.md`、`docs/system/迭代日志.md` 迁为 `BL000012-工作台/现状.md`、`迭代日志.md`（替换原骨架桩）；`docs/requirements/AGENTS.md` 迁为 `docs/业务线/AGENTS.md`（需求归档指引，内容改写为业务线统一目录规则）；`docs/requirements/` 目录删除。迁移清单增至 148 项（`docs/migrations/2026-11-03.json`）。
- 链接与指引同步：根 AGENTS.md 项目边界改为"各业务线现状、迭代日志与需求产出统一在 docs/业务线/（WorkHub 平台即 BL000012-工作台）"；docs/system/AGENTS.md 文档归属、docs/README.md、docs/company/AGENTS.md、docs/requirement-sources/AGENTS.md、平台现状第 9/10 节的归档路径与互链全部更新；迁移的 REQ 文档内部自引用链接一并修正，`历史版本/` 快照与 REQ-898b9c28 proposal 中的历史决策原文不改写。
- MCP `wikiPublishRule` 同步改为统一发布到 `docs/业务线/{业务线编码}-{业务线名称}/需求迭代`；研发评估落盘代码无需再改（BL000012 需求经 intake 流程自动落 `BL000012-工作台/需求迭代/`）。
- 验证：workhub-service 定向测试通过（见下方测试记录）；现行指引与迁移文件的本地链接存在性校验通过；本轮未提交，应用未重启。

## 2026-11-03 MCP 新增 get_intake_attachment 数据库附件空中读取

- 背景：用户确认原始需求原件以数据库为权威存储（intake_record.rawContent + attachment.fileContent），要求 AI 具备直接读库取附件内容的能力，全程内存"空中读取"、不落本地缓存；docs/requirement-sources 快照档案的去留（评估溯源用途）保留给用户后续单独决策。
- 实现：AttachmentTextExtractionService 新增公开 `extractTextInMemory`（单附件内存提取：图片不提取文本并返回说明、超过 8000 字符截断标记、类型不支持/无正文返回说明）；McpRuntimeService 注入 AttachmentService 与提取服务（新增 7 参主构造，旧 3/4/5 参构造保留兼容、缺省时跳过注册），注册 MCP 工具 `get_intake_attachment`（attachmentId 必填、intakeId 可选做需求归属校验）；处理器复用 loadAsResource 的"数据库优先、历史路径回退、SHA-256 完整性校验"取字节，全部在内存中完成，无本地文件写入。`get_intake_detail` 原本已支持空中读取 rawContent 与 structuredData（含附件摘要）。
- 验证：McpRuntimeServiceTest 新增 3 例（文本提取、附件不属于当前需求拒绝、图片返回说明），McpRuntimeServiceTest 19 项、AttachmentTextExtractionServiceTest 4 项、McpResourceServiceTest 13 项全部通过；应用未重启，工具待部署后生效。


## 2026-11-04｜共享支付系统更名为支付中台

- 按用户要求将"共享支付系统"更名为"支付中台"：归档目录 `docs/业务线/共享支付系统/` 整体迁为 `docs/业务线/支付中台/`；归档指引、README 索引与 BL000005-智能柜 现状等现行文档中的名称和路径同步更新。需求迭代现行报告正文保留原称与核验时点，仅修正其中指向旧目录的链接；`历史版本/` 快照、历史日志与既存报告原文按原字节保留。
- 迁移明细见 [2026-11-04 支付中台更名](migrations/2026-11-04.json)。本轮仅文档更名，未提交，应用未重启。

## 2026-11-04 删除 requirement-sources、company、migrations 目录

- 用户在确认"需求原件以数据库为权威存储、AI 经 MCP 空中读取"（get_intake_detail / get_intake_attachment）后决定删除三个资料目录：`docs/requirement-sources`（182 个文件：原始需求材料副本、逐轮不可变快照与来源绑定）、`docs/company`（公司业务知识与项目资料）、`docs/migrations`（迁移清单，含未提交的 2026-11-03.json、2026-11-04.json）。
- 不可恢复的未跟踪内容已确认一并删除：3 份来源绑定（258-105-development、265-104-development、269-38-clarification）、3 个新审批编号的材料副本（202610230002、202610260004、202611020006，其原件在 `attachment` 表仍有权威版本）、2 份迁移清单；已跟踪的 264 个文件可按 git 历史找回。
- 指引同步：根 AGENTS 项目边界与文档归属改为"需求原件以数据库为权威存储，AI 经 MCP 空中读取，仓库不做原始材料文件归档"；README 移除公司资料/原始材料/迁移清单入口与原始材料章节；业务线归档指引与 BL000012 现状/迭代日志的迁移清单死链改为按 git 历史定位。历史正文（研发评估的"来源版本"链接、迭代日志历史条目）按原时点保留、不改写。
- 已知影响，待单独立需求处理：① 归档管道未改，下次开发分析/澄清分析会在 `docs/requirement-sources` 重建目录并继续写快照——评估输入快照与哈希清单入库的溯源改造需另行立项；② 知识检索与 `sourceLink` 校验逻辑不变，对历史需求重评时来源快照已删除，会按既有校验报"来源版本文件不存在"；③ 支付中台更名与业务线目录重组的迁移映射记录随清单删除，可按 git 历史定位。
- 本轮仅删除目录与指引同步，未改应用代码，未提交。

## 2026-11-04｜REQ-bf94e06e｜自主迭代一期规格核查与 v2 草案

- 状态：待完整规格统一确认，未实施。
- 持续目标：让 WorkHub 及所属业务线自主迭代；一期先验证已确认需求的实施与 MR 交付，后续逐条业务线推广。是否扩展到主动提出改进，已询问用户，尚未答复。
- 复用既有 REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e；原 v1 保留历史快照，当前规格补足隔离、快照、队列、实际验收与失败恢复。
- 在线 WorkHub MCP 的上下文/需求查询均因数据库连接失败，继续本地只读核查与规格起草；未修改应用代码、配置或运行资源。
- [唯一需求与核查证据](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/核查记录-20261103.md)。


## 2026-11-04｜自主迭代目标纠正：GLM 草案不代表用户意图

- 用户明确长期目标包括主动发现问题并提出需求，同时指出既有草案由 GLM 生成、没有理解其意图。
- 撤回基于旧草案的 v2 实施确认，保留历史并停止将其中的内置 Agent、固定确认点与 MR 交付范围视为用户已确认要求。
- 已询问持续推进主体及一个理想业务场景；当前继续目标澄清，未修改应用代码或配置。
- [当前目标澄清](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/目标澄清.md)。


## 2026-11-04｜自主迭代目标明确：监测驱动、多 AI 与 Jenkins 审核发布

- 用户给出明确场景：WorkHub 自身日志错误/Bug/巡检，以及业务线业务监测、财务（现页面名账务）监测、系统预警发现的问题，自动分析、形成解决方案和代码分支，经人审核后发布测试与上线。
- 用户明确发布由 WorkHub 调现有流程执行、全部使用 Jenkins；整个过程支持系统配置的 AI，不绑定 Codex。
- 按新答复重写 Proposal/Design/Tasks v3，覆盖主动发现、统一 API/CLI 工具循环、分支与真实测试、版本绑定审核、Jenkins 发布恢复、测试/上线效果复查和全部目标业务线接入。旧 v1/v2 仍撤回并保留历史。
- 本次只读核查与文档工作；v3 完整规格待统一确认，未改应用代码、运行配置，未安装依赖或触发真实发布。
- [当前 v3 规格](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/proposal.md)。


## 2026-11-04｜Agent 工作台目标补充：多个 Agent 与通用任务

- 用户明确 Agent 是上层主体，主动巡检 WorkHub 提供的监测能力；工作台可配置多个 Agent，分派巡检或其他任务。
- Proposal/Design/Tasks 修订为 v4：Agent 配置、任务/周期、独立运行与产物为核心，Agent 自主规划和选择工具；巡检修复为应用模板，保留多 AI、分支实测、人工审核及 WorkHub 调 Jenkins 发布。通用研发任务也可使用变更/审核/发布能力，无需伪造告警问题。
- 补入无告警主动巡检、非巡检任务、多 Agent 隔离与并发冲突验收；v3 三份原文保存历史，旧确认请求由本版替代，v4 尚待统一确认。
- 仅修改规格与记录，未实施功能、调用真实 AI/Jenkins、安装依赖或修改运行配置。
- [Agent 工作台规格](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/proposal.md)。

## 2026-11-04｜Agent 工作台进入实施：现有 AI API 与原生 CLI

- 用户已批准 v4，并明确 WorkHub 为企业与 Agent 的桥梁、工作台 Agent 为配置形式，复用现有 AI API/CLI。按此继续实现，未再请求同一范围的确认。
- 后端已实现 Agent/任务/运行基础、主动监测工具、API 工具循环、CLI 原生任务与独立 MCP、调度/租约/幂等/补充/停止及报告留痕；前端配置/任务/运行页面已完成类型检查和构建。相关 47 项后端测试通过，包含真实本地子进程和 loopback 工具通信；均非实际 AI/Jenkins 联调。
- 当前仍缺修复工作区/分支实测、领域问题闭环、准确版本审核、Jenkins 发布/回归及各业务线真实验收。在线受控查询仍报数据库连接失败。未安装依赖、执行数据库迁移或部署。
- 详细实现事实与剩余工作见该需求的 [Tasks](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/tasks.md)，不将基础代码完成当作自主迭代目标完成。

## 2026-11-04｜Agent 代码工作区与 Jenkins 客户端验证

- 完成工作台本地桩交互，修正 AI 空值、工具提示与重试可用状态；新增安全重试测试，旧运行尚有未知外部执行时不能重复启动。
- 新增10项受控代码工具，使用管理员绑定的固定 SHA 与独立裸对象库，不进入用户未提交工作树。实现分页读取、路径/软链接限制、原 blob 与版本校验、幂等候选修订、唯一文本替换和失租约隔离；原文含脱敏信息时禁止整文件覆盖。
- 新增 Jenkins HTTP 客户端，验证固定目标、API Token、单次触发、队列过期/回执丢失查找、重复构建和参数错配，以及实际回执读取；未连接真实 Jenkins 或审批出站流程。
- 后端相关72项测试通过，controller/job离线编译通过；前端构建及本地桩页面验证通过。受宿主沙箱限制的进程树/loopback测试在自动审批后于同一隔离范围完成。没有安装依赖、迁移真实库或发布应用。
- 在线 WorkHub MCP 再次报 `CannotGetJdbcConnectionException`；向用户询问已有构建测试执行环境，继续本地独立实施。真实测试/分支交付、问题聚合、审批发布与效果闭环仍未完成。

补充：已实现 Jenkins 实例/Job/环境/仓库参数绑定、加密 Token 引用与两个只读准备工具。跨范围预检在取密钥/网络前拒绝，配置变化使旧绑定摘要失效；预检不代替发布审核。相关测试累计83项通过，controller/job离线编译通过。当前共19项Agent工具，尚未配置真实Jenkins绑定或开放AI直接发布。

## 2026-11-04｜巡检事实与问题分析链路

- 将实际监测/日志结果保存为运行级 Observation，新增问题登记/查询/领取/分析5项工具；来源分类与稳定身份从受控记录提取，不接受模型改写业务线或来源。重复轮询不重复建问题，方案历史不可变；同问题活动运行与权限撤销均有服务端控制。
- 前端接入问题、监测证据、分析历史与关联运行，完成独立分页、错误、空范围和切换Agent交互核查。验证截图已保存到需求目录；页面仅使用明确标注的本地样本，临时服务已停止。
- 相关102项后端测试通过，controller/job离线编译及前端构建通过。新增迁移未在实际库执行，未调用真实AI/GitLab/Jenkins或改运行配置。受控WorkHub查询仍返回数据库连接失败；实际测试执行环境等既有澄清尚待答复，继续保留实测/交付/审批/发布/观察缺口。
- 进度与完整范围见 [Tasks](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/tasks.md)。

## 2026-11-04｜多仓库候选清单与版本查看

- 平台从实际Git对象生成本次运行的完整仓库清单，冻结提交、差异、依赖、验收/回退建议及关联问题方案。短事务核对运行租约和全量版本；重复请求幂等，新代码或方案使旧清单失效。
- 工作台运行新增候选变更页签，显示版本、所有仓库提交/差异和实测/远端交付缺项。浏览器用本地内存样本验证版本、分页、失败清理、运行切换和空态；截图归档，临时服务已停止。
- 相关116项后端测试通过，controller/job离线编译和前端类型检查/构建通过；新增第四份迁移未在真实库执行。未安装依赖或触发外部服务。审批、实际测试/交付、Jenkins发布及效果闭环仍在实施。


## 2026-11-04｜GitLab 候选分支、MR 与交付状态

- 已实现固定项目的候选分支推送、Draft MR、实际提交核验和逐仓库持久化交付。未知写入核对原请求，新清单不能绕过旧请求重发；代码交付仍不代表实际测试或发布批准。当前共29项Agent工具。
- 工作台展示每仓库实际分支、MR链接、核验时间与未知/冲突状态。本地浏览器验证部分完成、未登记、503清理、迟到响应隔离；截图归档，临时页面和服务已关闭。
- 相关143项后端测试全部通过，含真实临时Git HTTP推送与模拟GitLab API、H2/Mapper/事务/并发；controller/job离线编译和前端类型检查/构建通过。第五份迁移仅在H2验证，未写真实库、配置或外部系统。
- 实际测试执行器、产物验收、统一恢复、人工审核、Jenkins发布/复查和真实业务线接入仍在实施。在线WorkHub查询仍无法连接数据库。


## 2026-11-04｜Jenkins 发布记录与中断恢复模块

- 单 Job 发布授权/出站/资源占用已持久化，发送前复核准确材料并提交意图，使用固定请求查询原 queue/build；未知结果不重发，取消 Agent 不冒充远端已停止。构建成功须再核对实际部署提交回执，部署与业务验证分开。
- 本轮47项相关测试全部通过（发布模块27、Jenkins策略8、客户端12），含H2/生产Mapper/事务及真实本机HTTP链路；审核与实测状态是测试夹具。修复了JSON数值节点比较误判和测试桩嵌套准备。controller/job及依赖离线编译通过。
- 第六份迁移未执行实际数据库；人工审核写入口、组合发布协调、自动调度、实际测试/业务验证和真实接入仍待完成。未安装软件、修改运行配置或触发业务Jenkins。WorkHub受控查询仍报数据库连接失败。


## 2026-11-04｜整组发布人工审核与 Job 依赖执行

- 新增冻结计划/步骤/依赖/审核记录，AI可准备材料和查询结果，人单独批准测试/生产。批准与全部出站请求/资源占用同事务，材料变化需要新计划；驳回写回原任务。
- 自动调度仅消费已批准请求，实际执行入口校验依赖及租约；失败/取消阻止尚未发送步骤，未知结果继续占用并核对原请求。整组部署产物不等于业务复查通过。
- 发布相关73项测试通过，controller/job离线编译与前端1771模块构建通过；隔离浏览器验证审核门禁/取消/幂等重试/驳回/过期/失败/旧响应隔离，截图归档。第七份迁移未执行真实数据库。
- 真实测试执行器、平台测试/业务验证结果、统一人工对账和业务线接入仍未完成；真实候选保持不可批准。未安装依赖、修改运行配置或调用真实AI/GitLab/Jenkins。WorkHub受控查询仍无法获取数据库连接。


## 2026-11-04｜ZCode 原生接入与任务恢复

- 新增 ZCode CLI 适配及配置字段，支持已有 Node/CJS 和 Provider 配置；认证与设备状态仅复制到私有临时目录，不回写原配置。前端可区分同厂商多个 CLI，场景按 Provider ID 选择。
- 迟到回执由原 owner/调用键校验后追加，调度器核对并恢复；CLI 最终结果消费和完成同事务，已保存结果不重跑。追加指令阻止旧行动，运行中的 CLI 先停止再重新规划，活动企业操作保留原回执。
- 247 项本地测试全部通过（0 跳过），controller/job 及依赖离线编译通过；5 项实际启动已安装 ZCode，模型和监测数据均为本地模拟。前序前端 test 构建通过。ZCode 配置迁移未执行真实 MySQL；未调用真实 AI、GitLab、Jenkins 或部署。
- 用户因网络不可用要求 Jenkins 暂缓，后续再接；保留仅允许测试环境构建、禁止生产构建测试的边界。完整目标中的来源扩展、产物验收、业务复查和真实接入尚未完成。


## 2026-11-04｜巡检历史读取与已登记缺陷线索

- 用户确认包括已登记缺陷和运行时发现的问题。新增业务线内缺陷列表/详情，来源权限和当前范围每次核对；环境未知明确待定位，不修改人工工作项。
- 修正业务失败详情误用任务汇总，接入实际逐次日志；增加历史账务运行、规则续页、状态分页和固定预警时间范围，最新成功不再遮住此前失败。同一失败运行跨入口保持相同事实身份。
- 工具目录现35项，Agent/任务清单容量64项。262项相关回归及最后41项读取复查通过，controller/job编译通过。首次新增夹具缺少分页字段的错误已修正；验证使用实际DDL、Mapper、H2及本机模拟，无真实环境调用。
- 持久消费游标、迟到补扫、缺陷环境关联和完整产物/效果验收仍待完成；Jenkins按用户要求暂缓。本轮无迁移、配置写入或部署。


## 2026-11-04｜跨运行巡检进度

- 新增3个巡检进度工具，系统预警、已结束账务运行、已登记缺陷按任务/范围保存快照及有限扫描位置，重扫发现迟到和更新；原记录未确认不跳过，明确终止后可接续。
- 确认只记AI分析，异常须对应实际问题信号，账务差异需同一运行规则证据；环境未明的缺陷保持线索。待补充可分页重领并保留历史，来源权限每次复核。快照/游标和确认/回执均保持事务一致。
- 新增14项集成测试，276项相关回归及最后55项巡检复查通过（0跳过），controller/job编译通过；含既有5项ZCode本机模拟。测试夹具问题已修正。新增迁移仅在H2验证，无真实库变更、安装、配置写入或部署。
- 外部来源持续游标、缺陷环境关联、未知执行回执自动对账及完整验收仍待完成；Jenkins继续按用户要求暂缓。证据见 [跨运行巡检验证](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261104-跨运行巡检进度/验证说明.md)。


## 2026-11-04｜巡检回执恢复

- 已提交的巡检扫描/确认结果在执行器回执丢失后可由运行恢复按原调用回读，补入标准工具回执；API不重做业务操作，CLI仍需进程停止证明。取消保留已提交事实，无持久结果或意图不符不误释放。
- 人工新输入与旧工具已执行事实共同进入后续规划，不错误声明原操作未发出。新增6项集成测试；282项相关回归全部通过，controller/job编译通过。
- 本轮无新增迁移、安装、运行配置修改或真实业务调用。Jenkins继续暂缓；缺陷环境关联、其他未知执行对账和完整交付/效果验收仍待完成。见 [恢复验证](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261104-巡检回执恢复/验证说明.md)。

## 2026-11-04｜Agent复查职责确认与独立分支实施

用户选择“Agent 判定，WorkHub 核对证据约束”。沿用已确认v4，在 codex/agent-workbench-autonomy 独立后端工作树实现复查记录、实际证据/窗口检查、等待恢复与持久凭据。175项本地测试通过、1项真实ZCode用例未配置跳过。没有触发真实构建、发布或迁移；来源覆盖、页面和问题关闭仍待完成。见[唯一需求验证](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261104-Agent判定与证据约束/验证说明.md)。


## 2026-11-04｜业务监测真实来源接入复查

独立分支补齐XXL-JOB实时查询执行凭据，修正空/缺字段统计可能被当零失败的问题；查询前检查实际环境。统计、明细和活动时间使用同一SQL，复查不接受重复旧活动或没有活动的空结果。130项本地测试通过，无真实配置/数据库/服务操作；Jenkins暂缓。证据见[需求验证](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261104-业务监测执行凭据/验证说明.md)。


## 2026-11-04｜日志查询接入效果复查

独立分支新增同PIT日志/服务活动凭据，纠正超时/分片失败和无法解析记录可能被当作空结果的问题，明确截断与查询区间空档。旧采样格式保持可读，164项本地测试通过；测试夹具类型错误已修正。未修改实际服务、配置或原工作树，Jenkins暂缓。系统预警持久采集区间与补漏为下一项，见[需求验证](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261104-日志查询与活动证据/验证说明.md)。


## 2026-11-04｜预警采集区间与续读

独立分支修正“达到单次上限后记录成功并跳过旧日志”的进度模型，固定区间和分页位置，事务保存事件/通知/证据。重扫去重、旧所有者、停用范围和累计缺项均有本地验证；130项通过。新DDL只在H2执行，未触发真实任务或Jenkins。Agent复查查询联通和更早历史补漏继续，见[需求证据](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261104-预警采集持久续读/验证说明.md)。


## 2026-11-04｜预警回执接入Agent复查

独立分支贯通持久采集→事件查询→脱敏Observation→Agent判定/平台约束。验证删除/改写、过滤、缺页、旧活动、规则变化及异常活动查询；修正同时间窗口误选与摘要误脱敏。157项本地测试全部通过，无新增迁移或真实服务操作。复查页面和问题闭环继续，Jenkins暂缓。见[唯一需求证据](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261104-预警采集复查联通/验证说明.md)。


## 2026-11-04｜工作台复查结果与采样历史

独立分支补齐复查结论、稳定分页采样和原始证据查看，当前授权/范围及引用归属持续核对；历史结论不被当前新鲜度覆盖。117项后端测试全部通过，前端类型检查、test构建与本机组件验证通过，前端提交133ff6a。临时服务/依赖链接已清理，无真实环境操作。原问题关闭/复发及最终验收继续，Jenkins暂缓。[需求证据](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261104-复查结果与采样历史/验证说明.md)。


## 2026-11-04｜问题关闭凭据与复发

独立分支完成关闭、不可变历史和复发联通；核对原环境/对象、版本、实际产物与观察起点。146项后端测试通过，前端test构建/本机组件验证通过。窗口起点边界已修正，临时服务与依赖链接已清理，新迁移仅在H2执行。用户确认任务完成目标环境需明确选择，下一阶段接入；真实测试回执与最终验收继续，Jenkins暂缓。


## 2026-11-04｜明确任务完成环境

已接入明确目标、任务/运行规格冻结、幂等及旧任务缺目标阻止；前端验证TEST单目标和TEST/PROD双目标，改变授权范围不会自动增加生产目标。98项本地测试通过、1项真实ZCode未配置跳过；前端test构建通过。未访问真实环境，临时资源已清理。发布执行和最终验收与目标的实际联通继续，Jenkins暂缓。


## 2026-11-10｜物理删除两个资料整理助手（运维操作）

用户要求物理删除数字员工"资料整理助手（ZCode）"（`cdc282d8-0bf0-4707-8cac-cf5d8356585f`）与"资料整理助手（Codex）"（`87fc31b5-1543-4b70-907f-3b2c6796ea46`）。经只读核查：两助手仅报告类数据（6 任务、10 运行、117 事件、7 产物，全部终态，无周期任务），无观测/问题/候选/构建/发布/验证等自治数据。在 `10.10.116.32:3306/workhub` 单事务删除 definition/task/run/event/artifact 并先写入 2 条 `pm_agent_audit`（action=DELETE，记录名称与删除行数）保留审计痕迹；删除后目标定义/任务/运行/事件均为 0，其余两个数字员工（保费分期监测巡检助手、WorkHub 代码迭代助手）不受影响。直接 SQL 操作，无代码与迁移变更。


## 2026-11-10｜保费分期巡检助手数据链路修复（运维操作）

用户要求解决巡检助手三项监测的数据可达问题，直接 SQL/MCP 资源操作（均留痕）：

1. **MCP 数据库目标纠偏**：新增目标 `bl000012-prod-db-10-10-116-32-3306`（BL000012/PROD，库 workhub，root 凭据按 McpCryptoService 方案 AES-GCM 加密，master-key 与实例现有密文核验一致）；停用旧目标 `bl000012-prod-db-aslight-cn-30364`（指向旧库，预警停在 2026-10-15）。新目标经 MCP 实测可读到当日最新预警（23:30:14）。
2. **助手职责改写**：`保费分期监测巡检助手`（26067ea5）职责更新为职责驱动架构口径（经平台 MCP run_readonly_query 巡检 ops_system_alert_event / ops_monitor_config / ops_account_reconcile_run+result，业务监测实况走 db-prod 的 scf_self_xxl/xxl_job），version 5→6，审计 UPDATE。
3. **创建周期巡检任务** `bl000001-prod-monitor-patrol`：CRON 每 30 分钟、数据范围=增量分析、产物 REPORT、范围 BL000001/PROD；`schedule_enabled=0` 暂停（避免旧版运行实例以旧工具架构空跑），审计 CREATE。
4. **待用户操作**：部署新版后端并配置 `workhub.mcp.access-token`；开启 `WORKHUB_ACCOUNTING_SCHEDULE_ENABLED=true` 使账务核对每日 06:30 落库（当前 reconcile_run 0 条即因该开关默认 false）；部署后在页面启用上述任务的周期。


## 2026-11-10｜新版后端本机部署与巡检任务启用（运维操作，接上节）

用户要求代办部署。运行方式核实：WorkHub 后端为本机 IDEA 调试进程（workhub-server 各模块 target/classes 启动），前端为 workhub-web vite dev 热更新（无需部署）；23:08 启动的进程已应用 V20261110_130000 迁移并载入当前代码，MCP Token 原已配置。

- 重启进程（停 67445，起 98218）：沿用原 classpath 与 `WORKHUB_MCP_ACCESS_TOKEN`，新增 `WORKHUB_ACCOUNTING_SCHEDULE_ENABLED=true`（账务核对每日 06:30 落库）。Flyway 校验 71 个迁移、schema 已是 20261110.130000；启动 3.6s 完成。
- 验证：`/api/mcp/runtime` 存活（无 Token 401）；MCP 会话经新目标读到 23:38:07 最新预警。
- 启用巡检任务 `bl000001-prod-monitor-patrol`（审计 SCHEDULE）：schedule_enabled=1，next_run_at=2026-11-10 16:00 UTC（00:00 CST 首轮）。
- 注意：98218 为命令行 nohup 进程（logs/server-console.log），用户下次在 IDEA 点运行即可回到调试方式（记得在运行配置加 WORKHUB_ACCOUNTING_SCHEDULE_ENABLED）。


## 2026-11-11｜巡检任务六次触发终成功（接 2026-11-10 部署节）

- 第4/5次失败定位（依赖新加的错误明细补丁）：ZCodeCliClient 协议流 4MB 总量护栏被巡检的密集 MCP 查询回显撑破；上限提至 64MB（相关测试 86 项通过），随重启生效。
- 第6次运行（46906001）COMPLETED：263.6 秒、1 步、REPORT 产物。ZCode 经平台 MCP 巡检三域，正确执行增量窗口（首轮建基线）与"无数据≠健康"纪律。
- 报告要点（细节见 pm_agent_artifact）：①307 条系统预警（293 慢 SQL 两类模式 + 14 条 SYSTEM_ERROR：放款状态同步失败/代扣失败/卡签约失败）；②【最重要发现】BL000001 预警采集自 11-09 17:36 起"ELK返回日志超出采集区间"持续 ERROR，30 小时零预警系采集故障（其他业务线同期正常佐证）；③业务监测 db-prod 堡垒机认证失败，XXL 调度无法验证（数据缺口）；④账务监测未配置规则（ops_account_rule_config 0 行）且 daily_enabled=false，无差异≠健康。
- 遗留（用户决策/操作）：堡垒机认证修复；BL000001 预警采集修复与补采；账务规则配置；巡检任务周期当前保持停用（用户此前仅要求执行一次，如需恢复每 30 分钟巡检可在页面启用）。
- 服务现为 nohup 进程 36235（logs/server-console.log）；IDEA 运行配置需补 WORKHUB_ACCOUNTING_SCHEDULE_ENABLED 与 MCP Token 环境变量。


## 2026-11-12｜清除巡检助手的两条旧测试任务（运维操作，用户确认）

用户确认按连带清除方案删除 11-05 旧工具架构的两条测试任务（6335cc88…、79741273…）：事务内先写 3 条审计（2 TASK DELETE + 1 CASE DELETE），随后清除问题记录 85784e53（含 1 方案、1 信号、case_run 关联）、2 条巡检观测、2 条运行及其事件/产物，最后删除任务。验证：助手下仅剩巡检任务 bl000001-prod-monitor-patrol（7 次运行保留），观测/问题清零。


## 2026-11-12｜平台数据指南上线与监测通知切换（REQ-0d4c0b49 + REQ-172f8541）

- 平台数据指南：迁移 V20261112_160000（120000 版本号与并行会话撞车后改名；期间清理 target 残留副本解决 Flyway 重复版本启动失败）——模板 v4 增加 platformGuide 段，sys_config `agent.runtime/dataGuide` 种子目录；巡检助手职责与任务文案净化为纯业务语义。净化后一次性巡检（449.9s）成功：AI 纯靠指南导航取数出报告，增量窗口精确接续上轮完成时间。
- 监测通知切换：删除 XXL 失败/MQ 失败/ELK 采集故障三处站内信（扫描与状态落库保留供巡检读取）；新增 AgentRunNotifier（COMPLETED 且含 P0/P1 时通知任务范围业务线成员，dedupe 按运行 ID）；相关测试 37 项全绿。
- 部署：服务重启（PID 36362），巡检任务恢复每 30 分钟周期（17:00 首轮，此后含 P0/P1 的轮次自动发 AGENT_PATROL 站内信）。
- 期间并行会话在途改动两度阻塞编译（ZCode 签名、intake 澄清域），均等待其稳定后通过；CodexCliPromptConsistencyTest 失败属其在途工作。


## 2026-11-12｜巡检通知链路端到端打通（接 REQ-172f8541）

通知未触发的排查链：格式缺 P0/P1 标记（补规范）→ 静默失败（加 NOTIFIED/NOTIFY_FAILED 审计）→ 心跳调度线程被异常静默杀死致运行失联（coordinator 心跳加 Throwable 兜底）→ 审计定位真因：巡检结束时连接池被 CLI 密集查询瞬时占满（CannotGetJdbcConnection），AgentRunNotifier 加 3 次退避重试。最终验证（run 3d996fdc，258 秒 COMPLETED）：报告含【P1】→ NOTIFIED 审计 1 收件人 → admin 收到 AGENT_PATROL 站内信——巡检→分级→通知全链路打通。


## 2026-11-13｜批量配置全业务线巡检助手（运维操作）

用户要求为其余业务线配置巡检。更新平台数据指南（多业务线目标映射：db-prod 覆盖多数线、BL000003 用 jiatai-amp-db-prod、BL000004/011 用 amp-db-prod、XXL 库名从 ops_monitor_config.xxl_job_database_name 自读），随后批量创建 12 个"XX监测巡检助手（ZCode）"（BL000002~BL000013，含工作台自身与太平洋保险）及每小时增量巡检任务（cron 错峰 :02/:06/:10…:46，避开 :00；职责/验收标准与保费分期同模板，纯业务语义）。全部审计留痕。首 run 按各错峰时间自动触发；通知发业务线成员（无成员回退 admin）。

## 2026-11-13｜保费分期中化反馈"已还款未收到对账单"排查（含生产复核结论）

以下首段为早期未接通生产时的候选分析，不代表最终结论。

业务线 BL000001、生产环境；代码口径 scf 组：scf-saps origin/master `febbb07f`（2026-10-21）、scf-intf-zhonghua origin/master `21ce0ff9`。中化反馈 512 个车辆账单编号（22 位 = FA+yyMMddHHmmss+4流水+2期数+2序号，账单创建时间跨 2026-03~10、月中集中，推测为运营商车辆子账单号历史格式）。链路梳理：还款结算写 `receivable_accounts_resource`（bill_date=还款日+1）→ XXL-Job 按 `bill_date=executeDate` 精确拉取生成 `receivable_accounts` 对账单 → 中化资方触发 `sys_api_request`（biz_type=2）→ StmtNotifyHandler 组装明细推 scf-intf-zhonghua → statement_notify_record → 中化。候选根因：①任务按日精确匹配漏生成（资源行 generate_bill_flag=0 滞留）；②通知组装明细抛异常（未匹配运营商账单/车架号）卡 INIT 静默失败——重试计数不涨、永不告警；③重试 5 次耗尽（应已有企业微信告警）；④还款结算未落资源行。本会话无 MCP 只读通道，已产出分批只读 SQL（docs/业务线/BL000001-保费分期/20261113-中化反馈已还款未收到对账单核查.sql），待用户受控渠道执行后定位。

### 2026-11-13 14:16（北京时间）生产复核

- 范围：WorkHub `list_mcp_targets` 当前确认 BL000001／PROD／scf；通过 `db-prod/readonly` 核查 `scf_self_saps` 与 `scf_self_intfcapital`，通过 `search_business_logs` 读取生产日志。未指定研发需求。两个仓库远端 `master` 已用 `ls-remote` 核实，分别为 `febbb07fd05453cc5bf21c20294dc00a5cf1adb5` 与 `21ce0ff9787e823d8a8ab964c9a6388daa06d93f`；相关本地源文件与该提交一致。未取得运行制品 SHA。
- 附件 `Sheet1!A2:A513` 共 512 个唯一车辆账单号，生产全部命中，对应 283 个运营商主账单和 283 个资方账单，全部关联代偿对账单 `ARA2611110400040001`（主键 688，2026-11-11 04:00:04 生成，金额 731,414.48 元）。分 7 批逐项比对成功报文，511 个存在，唯一缺失为 `FA26081516350100010301`（附件 A171）；所有相关资源均已生成对账单。
- 补偿日志：2026-11-13 10:00:01.117，`apiRequestRetryJobHandler` 开始处理 1 条记录；10:00:02.295，接口接收 `S0261113100001`／上述对账单；10:00:04.431，中化返回 `capitalRetCode=000000`、`capitalRetMsg=成功`。`statement_notify_record.id=158` 状态 `1002`，完整 `statement_details` 数量为 511。`sys_api_request.id=3237`／`SR0261111040001_05` 于 10:00:05 更新为 `status=4`（成功）。接口成功回执不等同已核验中化内部账务页面。
- 单车遗漏原因：目标运营商车辆账单 `repayment_plan_sub.id=43578` 当前 `refund_insurance_status=30`（退保），最后更新时间 2026-11-11 17:30:01。对应资方账单 `FBA2608171720010003`／主键 26574 已结清 1,240.06 元；资源主键 15843、15844 分别为本金 1,211.15 元及资方服务费 28.91 元，均已挂入该代偿对账单。`StmtNotifyHandler.java:187-191` 的代偿分支按当前运营商车辆账单 `refund_insurance_status=NORMAL(10)` 过滤，补偿重组明细时排除了这辆车，形成金额仍在对账单、车辆明细缺失。此前看到的运营商未还 1,862.96 元不能用来否认资方账单已结清。
- 整批此前异常的已确认日志：2026-11-12 14:30 至 19:00 多次 `SR0261111040001:sendRequest error`，异常为 `Data too long for column 'request_params' at row 1`，发生在 SAPS 更新通知记录处。当前该列为 `longtext`，成功请求长度 76,441 字符。日志证明历史报文落库超长异常；本轮未取得下游首次失败的完整原因及字段扩容操作记录，不能据此声称恢复动作由本轮完成。
- 处理建议（未实施）：代偿通知明细应与已生成对账单的实际资方资源、车辆和期次一致，不能因后续退保状态变化丢失历史应通知车辆。需同时核对金额与车辆集合，保留真正未参与代偿车辆的排除规则。当前通知已成功，普通补偿任务和发送处理器均会跳过成功记录；即便强制按原逻辑重组也仍会漏车。应先确认中化对成功对账单的更正／补明细规则，再定向修正这一张，不能直接重复发送整批或修改真实退保状态绕过筛选。
- 核查边界：长请求日志有字段截断，车辆缺失结论来自数据库完整报文的逐项匹配与 `JSON_LENGTH`，不是从截断日志推断。服务器原始文件补充查询因远端 Python／SSH 文件读取失败未取得，ELK 已提供上述日志。早期 SQL 草稿存在字段、引号和账单关联假设问题，本轮未执行该文件，不作为生产执行依据。
- 留痕：[核查证据](业务线/BL000001-保费分期/证据/20261113-中化对账单补偿复核/核查证据.json)、[512 笔逐项核对](业务线/BL000001-保费分期/证据/20261113-中化对账单补偿复核/512笔逐项核对.json)。本轮仅只读诊断和本地证据归档，未安装软件、修改业务代码／生产配置／数据或触发补偿、支付和重推。


### 2026-11-13 14:49（北京时间）再次补偿核验

用户反馈已重新补偿。本轮经同一生产只读通道确认：对账单 ARA2611110400040001 新增通知记录 id=159，流水 S0261113140001，2026-11-13 14:46:38 中化返回 000000／成功，状态1002；SAPS 通知 id=3237、流水 SR0261111040001_06 同时更新为状态4。成功报文共512条、512个唯一车辆账单，与原附件逐项对比无遗漏、无额外记录、无重复，FA26081516350100010301 恰好出现1次。总金额仍为731,414.48元。ELK成功回执时间为14:46:38.254，与数据库一致。已确认本次接口推送完整且成功；未核查中化内部账务页面，也未据此宣称永久代码修复已部署。本轮未修改生产或触发重推。证据：[再次补偿核验](业务线/BL000001-保费分期/证据/20261113-中化对账单补偿复核/1446再次补偿核验.json)。


## 2026-11-14｜前端下线"巡检问题"面板（运维操作，用户确认）

旧工具架构遗留的「巡检问题」面板（AgentCasesPanel 及其缺陷关联/关闭凭据子面板）从数字员工页删除：AI 不再登记问题，面板永远为空。后端 pm_autonomy_case 数据与自治服务保留（历史记录可查），仅前端下线。构建通过。


## 2026-11-14｜物理删除 8 个已停用的巡检数字员工（运维操作，用户确认）

用户经页面停用并要求物理删除的助手：嘉泰消费分期/嘉泰租赁资产保理/嘉泰设备租赁/太平洋保险/工作台/服务费分期保理/物流平台/账单管理（各含 1 个巡检任务，4 个有历史运行共 82 轮，无过程数据）。事务内先写 AGENT/TASK DELETE 审计（含删除行数），随后清除运行/事件/产物、任务、助手。保留启用中的 6 个：保费分期、消费分期、智能柜、叉车、新消费分期巡检 + WorkHub 代码迭代助手。


## 2026-11-14｜修复 ZCode CLI 0.17 仓库工具不可见导致研发评估空拆解（研发修复，已验证）

现象：`intake.development.analyze` 连续多日返回"AI 未产出任务拆解"；库中 intake 277 的 5 份历史 DRAFT 实际 workItems 全为空（此前无守卫被当成功保存）。协议探针实证双重根因：① ZCode.app 11-12 18:26 升级到 0.17.1 后，`session/create` 的 `mcpServers` 参数只登记不连接，必须显式 `mcp/list {mode:"connect"}` 才会把 workhub_task MCP 工具暴露给模型（对照实验：调前模型自述无该工具，调后 connected toolCount=29 并真实调用 `mcp__workhub_task__list_repository_files` 返回 scs 仓库目录）；② `toolAllowlist` 用裸名，会话内实际暴露名为 `mcp__workhub_task__<name>`，裸名全量过滤。修复：`ZCodeTaskConversation` 订阅后新增 MCP_CONNECT 阶段（连接失败快速失败且不下发提示词）、工具名统一幂等补前缀；纠偏续跑与 analyze 提示词同步标注真实工具名。验证：`ZCodeTaskConversationTest` 17/17、真机 `ZCodeCliLocalProtocolTest` 5/5（真 CLI + 夹具，模型请求工具面断言通过）；全量 1149 测试中仅 3 处失败均为前几轮未提交 intake/提示词重构与 ops ELK 既有问题，与本轮无关。待用户重启应用后重跑 202611050003 评估闭环确认。


## 2026-11-14｜研发任务评估移除风险/待确认项，统一由需求澄清阶段产出（产品对齐，用户确认）

用户决策：评估阶段不再输出 risks/questions（与需求澄清职责重叠），业务+技术待确认/风险统一在澄清阶段产出（澄清场景本就是 LOCAL_REPOSITORY_READ，提示词已按"系统架构师查需求+知识库+代码"设计，MCP 修复后可真实查证）。实施：① 生成器 OUTPUT_SCHEMA/RawDevelopmentAnalysis 去掉两字段，草稿构建置空（model 字段保留兼容存量 draft_json）；② 新增迁移 V20261114_210000 更新 analyze 提示词（删 risks/questions 指引，代码调查新发现写入 summary），TestPromptTemplates 登记；③ 前端删除评估 tab/评估弹窗/轮次弹窗三处"风险与待确认"区块（澄清与 SQL 草稿区块保留）；④ 同步修正 CodexCliPromptConsistencyTest 的澄清 fixture 至 V20261112_161000 收紧版（前几轮遗留红，已修绿）。回归：全量 1152 测试，本轮相关全绿；剩 KnowledgeArchiveWorkflowTest（路径期望）与 BusinessLogSearchServiceTest（ELK）两处红为前几轮未提交遗留，与本轮零关联。待决策：生产库 clarification 模板已被手工改为"系统架构师"版，与迁移文件（"研发需求澄清助手"版）漂移，需确认手改版后固化为新迁移。

### 2026-11-14 补充｜澄清提示词以手改版为准固化（用户确认）

用户确认以页面手改版（"系统架构师"版，prompt_version=7）为准。字节精确校验 SHA256=f84d195c…4cc 与线上 checksum 一致后，固化为迁移 V20261114_213000__freeze_clarification_prompt.sql（本库 checksum 相同自动跳过，新环境按此初始化），并登记 TestPromptTemplates、同步 CodexCliPromptConsistencyTest fixture。漂移消除，相关测试全绿。


## 2026-11-14｜REQ-4d361388 巡检问题结构化动工（进行中）

已完成：pm_agent_issue 表迁移（V20261114_200000）、工单实体/Mapper/摄取服务（去重聚合+复发重开+未复现计数）、工单查询 API、CLI 结果 schema 扩展 problems 字段（含五要素 analysis 结构）、运行完成钩子自动摄取、5 个巡检任务口径更新（要求输出结构化 problems，P0/P1 新问题深查五要素）。73 项相关测试通过。待做：修复管线（clone→分支→修复→MR）、工单审核与两段式发版 API、前端三 Tab 与工单中心。


## 2026-11-14｜REQ-4d361388 全链路完成（工单闭环）

后端（编译通过，73 项相关测试绿）：
- 工单域：pm_agent_issue 表（V20261114_200000）、AgentIssueService（摄取/去重聚合/复发重开/未复现计数）、查询 API。
- 修复管线：AgentFixService——勾选工单→核验 GitLab 绑定→状态推 FIXING→组装五要素修复任务（CHANGE_SET 期望产物、独立职责/验收）走既有运行管线→站内信。
- 审核与发版：AgentIssueReviewService——PENDING_REVIEW 审核（APPROVE→TESTING 记录部署状态；REJECT→REJECTED）；TESTING 人工确认（PASS→FIXED；FAIL→REJECTED）。Controller 暴露 /fix /{id}/review /{id}/deploy。
- CLI 结果 schema 扩展 problems 数组（含 analysis 五要素结构）；运行完成钩子自动摄取；5 个巡检任务口径更新。

前端（构建通过）：运行详情三 Tab 问题面板（域分组/级别/五要素/勾选/复制摘要/去修复二次确认）；工单中心新路由 /agent-issues（筛选列表+审核+TEST→PROD 操作）。

待部署重启生效；修复任务的独立 clone/推送/MR 接线在 AgentFixService 中预留（fix_json 结构就绪），Jenkins TEST/PROD 触发复用既有链路待接线——这两处需要 GitLab 策略与 Jenkins 绑定的真实环境联调。


## 2026-11-14｜新增“任务同步禅道”独立按钮：研发任务推送/更新/取消禅道（研发功能，用户确认规格）

规格（用户确认）：评估 tab“评估完成”左侧新增独立按钮，全评估阶段可用；重复同步时已映射任务更新、新任务新建、拆解中已删除的禅道任务取消（canceled）。实施：① ZentaoClient 新增 createDevTask（POST executions/{id}/tasks，type=dev）/updateDevTask/cancelDevTask（PUT tasks/{id}）与 resolveTaskMapping（需求关联禅道地址优先，回退业务线映射），复用既有 token 认证；② DevelopmentAnalysisService.syncZentaoTasks 逐项 best-effort（单项失败不中断），回写工作项 zentaoTaskId/Url、草稿 zentaoSyncedTaskIds 快照（支持删除检测）与 SYNCED/PARTIAL/FAILED 状态，记历史；③ 手工保存草稿（updateDraft 按标题合并）与调整负责人（updateOwners）均保留禅道映射，withSystemTags 透传；④ 新端点 POST /intake/{id}/development-analysis/zentao-sync（intake:ai:operate）；⑤ 前端“任务同步禅道”按钮（先保存草稿再同步）、工作项表新增禅道任务链接列、API 封装。Model 层工作项加 zentaoTaskId/Url（兼容构造器保旧调用点），草稿加 zentaoSyncedTaskIds（存量 JSON 兼容 null）。验证：ZentaoClientTaskTest 6/6（HTTP 桩：创建/更新/取消/失败分支/映射回退）、全量 1158 测试仅剩两处与本轮无关既有红、vue-tsc 0 错误。注意：负责人直接作为禅道 assignedTo 透传，非禅道账号时该项失败并在汇总明细提示；重新分析生成的新轮次不带旧映射（语义：新任务集）。


## 2026-11-15｜叉车巡检派发失败修复（运维操作）

WARN "Agent scheduled task was not enqueued. taskId=12bd560d, errorType=IllegalArgumentException" 根因：叉车（BL000008）业务线 supported_environments 仅 TEST,SIT，不含 PROD——批量配置时助手范围设了 PROD，validateScopes 每次拒绝。修复：助手配置与任务规格范围改 TEST（审计留痕），next_run_at 拉到当前触发即时执行。其余 4 个巡检助手范围与环境匹配确认无误（保费分期/智能柜/消费分期/新消费分期均 PROD）。


## 2026-11-15｜删除 WorkHub 代码迭代助手及其数据（运维操作，用户确认）

用户要求删除"WorkHub 代码迭代助手（Codex·测试）"（0c54333f）及其全部数据。事务内先写 AGENT DELETE 审计，随后清除：构建测试调用/记录、代码操作/工作区、候选变更集、巡检观测、运行事件/产物、运行、任务、助手本体。剩余 5 个巡检助手及 128 轮历史运行不受影响。

## 2026-11-15｜REQ-4d361388 链路两个缺陷修复：问题摄取接线 + 派发并发计数（研发修复，已验证，待重启与运维 SQL）

用户反馈"运行产出结果与产物还是一整个、没有分类、无法选中修复"。排查结论：前后端功能代码 11-14 已完成并部署（前端 dist 06:55 / 后端 06:59），CLI 也已按新口径输出结构化 problems（叉车 11-15 06:52 完成运行的 CLI_RESULT 事件里有 3 条带五要素的问题），但两个缺陷导致全链路不通：

1. 摄取接线错误（主因）：`AgentRunStore.finishSavedNative` 只把结果 JSON 的 summary 落入 result_text，problems 留在 CLI_RESULT 事件里；而 `AgentIssueService.ingest` 把 result_text 整体当 JSON 解析 problems → 永远解析失败，pm_agent_issue 0 条，面板永远空。修复：ingest 在 result_text 无 problems 时回退读取最近 CLI_RESULT 事件的 result.problems（多轮重规划取最后一条含 problems 的）。
2. 派发死锁（次因，巡检停摆 28 小时）：11-14 晚服务重启期间 3 个巡检 Agent 的在途运行经 recoverExpired 恢复为 WAITING_RESOURCE 且保留 owner_token + 未决 CLI 句柄（租约已过期）；`countOwnedRuns` 只按 owner_token 非空计数，单 Agent 并发=1 被永久占满 → 三个 Agent 自 11-14 23:15 起 25 轮巡检全部卡 QUEUED。修复：countOwnedRuns 增加 `lease_until > now` 条件，只统计存活租约；失联残留不再占用新轮次额度。

顺带把 AgentIssueMapper.list 的 `ORDER BY FIELD(severity,...)` 换成 CASE（H2 可测，MySQL 语义不变）。测试：新增 AgentIssueServiceTest（CLI_RESULT 事件摄取/summary 落库/复发聚合/坏行跳过/嵌入 JSON 兼容，H2 + 真实迁移）；AgentWorkbenchServiceTest 新增"过期恢复不阻塞同 Agent 新轮次"用例，6 处旧口径断言更新（恢复后残留不再计数），67 项全绿；全量 1168 项仅剩 KnowledgeArchiveWorkflowTest 与 BusinessLogSearchServiceTest 两处前几轮遗留红（与本轮零关联），controller 模块 67 项全绿。

待办：①用户重启 IntelliJ 运行的服务生效；②执行运维 SQL（docs/业务线/BL000012-工作台/20261115-巡检派发死锁解卡.sql：终结 3 条 WAITING_RESOURCE 为 FAILED、每任务仅保留最新 1 条 QUEUED 其余 22 条取消，含审计与事件留痕）；③首轮巡检完成后问题面板/工单中心出数。历史 2 条已完成运行（11-14 22:38/22:52 的 b5a831a8、2abffa5f）如需回填工单需另行触发。

### 2026-11-15 补充｜解卡 SQL 已由本会话直接执行（用户委托）

经应用自身数据源配置 + 本机 mysql 客户端直连 workhub 库执行（脚本见 docs/业务线/BL000012-工作台/20261115-巡检派发死锁解卡.sql，含第 4 节补充收尾）：①3 条 11-14 晚的 WAITING_RESOURCE 终结为 FAILED；②22 条积压 QUEUED 取消，每任务保留最新 1 条；③发现用户 08:38-08:41 曾短暂重启又停止，产生 3 条孤儿 RUNNING（37fb8b5b/a6605da3/d1f11f4a）与 1 条恢复出的 WAITING_RESOURCE（c35b6629，新消费分期），执行器 JVM 已死、CLI 结果无人接收，一并按失败关闭。全部写审计与事件。复核：owner_token 残留=0、QUEUED 每任务=1。未杀任何进程（无法区分巡检 CLI 子进程与本会话进程，孤儿 CLI 自行退出即可）。待用户启动服务后验证：3 个 Agent 应在数秒内认领保留轮次，完成后问题面板/工单中心出数。

### 2026-11-15 补充｜「结果与产物」内嵌子 Tab 改版 + 全链路出数验证

前端：运行详情「结果与产物」Tab 改为内层子 Tab——[结果报告] [系统预警（n）] [业务监测（n）] [账务监测（n）]，AgentRunIssuesPanel 去掉自带 tabs 壳只输出三个域 tab-pane（勾选/复制摘要/去修复工具条保留在子 Tab 内容下方）；vue-tsc 0 错误、dist 重建（10:20）。验证：服务重启后巡检恢复（01:00 起多轮 COMPLETED），摄取修复生效——pm_agent_issue 已入库 40 条（系统预警 20/业务监测 10/账务监测 10，P1 共 13 条），问题面板与工单中心出数可勾选。02:00/02:10 两轮失败为 ZCode 模型请求瞬时拒绝（-32605 workspace/generateText），下轮自动重试。

### 2026-11-15 补充｜摄取瞬时事务失败加退避重试（用户反馈"报告有、Tab 没有"）

用户反馈部分问题只在结果报告、不在域 Tab。排查：设计上报告为本轮散文全文、域 Tab 为跨轮聚合工单，本不一一对应；但确实存在真缺陷——02:32/03:04/03:42 三轮摄取失败（DataAccessResourceFailure/TransactionSystemException，与 11-12 通知失败同源：巡检结束瞬间 CLI 密集查询占满连接池），整轮回滚未入库。修复：AgentExecutionCoordinator 摄取调用点加 3 次退避重试（2s*attempt，照 AgentRunNotifier 模式）；agent 全量测试绿。漏摄的 3 轮无需手工回填：问题均为持续状态，下一轮巡检复现时聚合更新（次数/级别刷新）。待用户下次重启生效。

### 2026-11-15 补充｜数字员工弹框统一支持点击空白关闭（用户确认）

AgentWorkbenchView 两处 el-dialog（任务列表、任务与运行）原显式 `:close-on-click-modal="false"` 已移除，回归 Element Plus 默认（true）；三个 el-drawer（Agent 配置、分派任务、调整周期）默认本就为 true。vue-tsc 0 错误、dist 已重建。弹框关闭不销毁内容，未发送的补充指令等输入保留。

### 2026-11-16 智能柜30笔未结清账单补齐SQL交付

用户确认仅30笔未结清账单同时补齐对客逾期天数和罚息，已结清不动，要求一键SQL。生产只读复核每笔无还款、日费率0.001，合计2630个缺失计费日、696.29元（17笔151.90元、13笔544.39元）。已准备事务保护、原值与版本校验、整批幂等的一键补齐和回滚SQL，9项本地MySQL 8.4.10隔离验证通过；生产MySQL 8.0.28，生产尚未执行。脚本限定2026-11-16快照，跨日需重新核查。

[脚本与验证](业务线/BL000005-智能柜/需求迭代/REQ-3f92412a-828c-4774-8277-606dbb632cae-30笔未结清账单逾期天数及罚息补齐/tasks.md)。

### 2026-11-16 智能柜补齐SQL按用户要求简化

用户明确只要普通UPDATE和INSERT，已生成同需求目录“简单补齐.sql”，移除存储过程和DDL。30笔/2630条/696.29元不变；原值漂移逐账单跳过，客户端遇SQL错误须停止并ROLLBACK，完整重复执行不重复收费。7项本地MySQL验证通过，生产未执行。

### 2026-11-16 智能柜补齐SQL改为每账单独立一组

用户要求一个账单一组、全部单表UPDATE/INSERT。交付“按账单分组补齐.sql”：30组，每组2条直接UPDATE+1条INSERT VALUES，无跨表条件。金额696.29元、2630条记录不变。本地逐组验证通过；默认不自动提交，须核对每组1/1/N后COMMIT，异常ROLLBACK，不可重复提交。生产未执行。

### 2026-11-16 12:31 · 智能柜30笔逾期天数及罚息补齐验收通过

用户已执行SQL，助手只读核验30笔账单全部通过：新增2630条逐日计费记录、696.29元；天数、科目、主表金额逐笔一致，缺失、重复、金额差异均0。样本BFX2409191504350007恢复607天，罚息余额297.43元，账单余额783.54元。验证范围为这30笔，无生产写入操作。


## 2026-11-16 智能柜已结清漏计补齐与减免方案准备

用户要求已结清按未结清形式补数据，并以减免结算消除新增应收。核实14笔有效已结清账单缺15个计费日，生产日费率0.001、基数101/20101/20102，补计及全额减免11.09元。已准备每账单一组7条单表DML、核验和回退草案；本地验证通过，生产交付前45项只读检查通过。完整规格待用户统一确认，生产未执行。

目录：docs/业务线/BL000005-智能柜/需求迭代/REQ-9ac32f0f-ac9c-4bd1-9d7e-fd3741aac1fe-14笔已结清账单补计罚息并减免结算。


## 2026-11-16 智能柜已结清减免补录业务时间v2

按用户要求，新减免订单的业务时间和完成时间、交易业务日期对齐各账单原结清时间；结算/账务时间原已对齐。创建/修改/操作时间保留实际补录时间及补录标记，逐日计费日期仍为缺失日。范围14笔、15日、补计并减免11.09元不变，SQL单表结构不变。本地隔离时间和金额验证通过；生产未执行。原方案v1已保留。


## 2026-11-16 已结清补计减免生产验收受阻

用户报告已执行v2补齐SQL。WorkHub只读通道连续查询在自身资源配置库McpResourceMapper.findAll阶段无法获取JDBC连接，未读到账单结果，暂不能确认生产修复成功或异常。已记录证据，停止重复请求；待通道恢复核验7表、14账单/15日/11.09元和时间。未执行生产写入。


## 2026-11-16 已结清补计减免生产验收通过

2026-11-16 14:58连接恢复后重新读取生产7表，14笔对客天数、罚息、减免流水及原结清时间全部通过；新增15条计费11.09元，四层减免各14条11.09元；未付均0，原现金付款不变，无漏写或重复。本次只读，无生产修改。前次连接故障已解除。

证据目录：docs/业务线/BL000005-智能柜/需求迭代/REQ-9ac32f0f-ac9c-4bd1-9d7e-fd3741aac1fe-14笔已结清账单补计罚息并减免结算。


## 2026-11-16 三笔特殊账单调查及修复草案

三笔特殊情况已查明：BFX2407261209050001按实际入账计费口径保留0天0元；BFX2409191504350003保留222天/108.78元（实收51.04、减免57.74），按未计费日期将展期12修正11；BFX2505081709160010历史18天75.57元已归零转租金，拟补记18条计费和等额减免，现金4582.50及未付0不变。已准备两组单表SQL，本地验证和10项生产前置只读检查通过，生产未执行。

目录：docs/业务线/BL000005-智能柜/需求迭代/REQ-6f9b0b1c-15e2-4845-a286-4774e0515799-三笔特殊账单逾期与罚息核查修正。


## 2026-11-16 用户排除第三笔特殊账单修复

用户明确BFX2505081709160010已有历史处理，保留现状、不再修复。已从当前正向/回退SQL移除该笔全部写入，撤销75.57元补计减免方案，旧合并版本归档弃用。当前仅保留BFX2409191504350003展期12改11的单表UPDATE草案；生产未执行。


## 2026-11-16 账务监测FunctionCall化实施完成

REQ-67108299 实施完成：账务监测执行统一走 FunctionCallService（func_call_sql 模板 + 审计），新增 SAPS_BILL_CHAIN 规则包（叉车/智能柜 14 条规则、29 条 OPS_ACC_* 种子），迁移清空 10 条旧配置写入叉车/智能柜 2 条并开启每日对账。db-prod 双 schema 58 条渲染 SQL 全部实跑通过；发现 statement_pay_detail 无 is_reverse 列已修正；ol_saps 交叉规则发现 3 笔订单缴费单结算与账单结算各差 1000 元（WARN 级，待业务确认是否异步时差）。账务相关 31 项单测通过。部署后需触发一次手动对账并核对 sys_job_config 的 OPS_ACCOUNTING_RECONCILE。


## 2026-11-16 巡检CLI格式校正60秒取消定位与修复（REQ-98a19dce）

用户报消费分期巡检失败：FORMATTING 阶段 workspace/generateText 报 -32603 Model request was cancelled。库里统计 11-14~11-16 四个业务线 9 次失败全部 60–62s、成功 14 次 20–52s，整齐 60 秒线且与 WorkHub 配置（1800s）无关。逆向 zcode.cjs 0.17.1：generateWorkspaceTextImpl 在调用方不传 abortSignal 时挂 AbortSignal.timeout(60000)（常量 Goi=6e4，无配置口，取消不可重试）；RPC 层 params 带 operationId 才走可显式取消通道（无超时）。本地按 WorkHub 同样私有目录/环境复现对照：不带 operationId 60.8s 被取消（CLI 日志 durationMs=60001 reason=cancelled），带 operationId 75.6s 完整成功；curl 直连先行通网关非流式 180s 返回 200，排除服务端。修复：ZCodeTaskConversation 的 wh-format-result 请求加 operationId（wh-format-result-{inputId}），时限回归平台预算。过程细节见需求目录 docs/业务线/BL000012-工作台/需求迭代/REQ-98a19dce-f751-4bde-a19e-7e6b2c841e62-ZCode终稿格式校正60秒取消修复/。复现脚本 /tmp/fmt-repro/repro.mjs（临时，重启即失）。

## 2026-11-17 业务线归档迁移到 agents-playbook

按用户要求将非 WorkHub 自身的业务线归档迁出 docs：BL000001~BL000011 与支付中台（共 12 个目录）以 git mv 迁到仓库根 `agents-playbook/`，归档指引与已确认评估索引随迁为 `agents-playbook/AGENTS.md`、`agents-playbook/index.md`；`docs/业务线/` 仅保留 `BL000012-工作台/`。代码同步按业务线编码分流：`RepositoryDocumentPaths.wikiArchiveRoot`（BL000012 → docs/业务线，其余 → 仓库根 agents-playbook）、`ProjectKnowledgeBaseService` 归档与索引写入改用 location.wikiRoot（wikiRelativePath 改为仓库根相对）、`DevelopmentPlanFolderService`、`McpRuntimeService.businessLineWikiRoot`、`McpResourceService` 的 requirementWikiPathPattern 与 wikiPublishRule 文案。原始材料仍在 `docs/requirement-sources/`，过程记录仍在 `docs/notes.md`。现行文档内部链接批量改写 157 个文件（`docs/业务线/{迁移目录}` → `agents-playbook/{迁移目录}`，含绝对路径形式）；`历史版本/` 快照与历史日志原文按原字节保留。测试：受影响 9 个测试类 127 用例全绿；全量 1215 用例仅 BusinessLogSearchServiceTest 1 例环境性错误（ELK 范围配置缺失，与本次无关）；顺手修正 KnowledgeArchiveWorkflowTest 一处 HEAD 上已失效的旧断言（4412e13 改映射未同步测试）。迁移前非 WorkHub 材料的旧位置 `docs/业务线/` 可按 git 历史定位。

## 2026-11-17 业务线归档迁入独立仓库 agents-playbook

修正早前方案：agents-playbook 是工作区独立 git 仓库（/Users/aslight/IDEAWorkspace/agents-playbook，远端 engineering/agents-playbook.git），非本仓库内目录。WorkHub 仓库内暂存的 agents-playbook/ 迁移全部撤销，12 个业务线目录按双轨并入该仓库：未重叠的 BL000003/000007/000008/000009/000010 整体移入其 docs/业务线/；重叠 6 条（BL000001/2/4/5/6/11，该仓库已有 AGENTS/架构设计/产品要素/系统实现/需求 结构）并入同名目录——现状.md、需求迭代/ 等移入，迭代日志.md 以「## 2026-11-17 工作台归档迁入」分节原文追加（对方表格格式不动）；支付中台并入其 docs/支付中台/；已确认评估索引迁为其 docs/业务线/index.md。本仓库 docs/业务线/ 仅剩 BL000012-工作台/ 与重写的 AGENTS.md（双归档根指引），文档以 staged 删除记录迁出。代码同步：RepositoryDocumentPaths 新增 playbookPath 配置（默认取本仓库同级 agents-playbook 仓库），非 BL000012 业务线的研发评估自动归档与索引写入 agents-playbook 仓库 docs/业务线/{编码}-{名称}/需求迭代/；MCP knowledgeCatalog 新增 projectPlaybookPath，businessLineWikiRoot、requirementWikiPathPattern、wikiPublishRule 同步。迁移文件仅修正指向两仓库的路径，历史版本/ 快照按原字节保留；agents-playbook 仓库内新文件保持未跟踪、迭代日志追加不覆盖其未提交改动，由其自行评审提交。受影响 9 个测试类 127 用例全绿；两仓库互链与跨仓库代码引用验证 0 缺失。

## 2026-11-18 账务监测场景化优化（REQ-d227e09d）

按用户要求下线扫表对账：删除 AccountingReconcileExecutor/AccountingRuleCatalog/AccountingReconcileJob 与 29 条 OPS_ACC_* 种子（库内模板 V20261118_210000 停用留档，run/result/evidence 历史保留），AccountingExecutionEvidenceService 摘除 capture 后保留为共享读取设施（Agent 巡检/系统预警在用，勿删整类）。监测改为场景维度：ops_account_monitor_config 复用原表 ALTER（避免新表自增 ID 与历史 run 悬空 monitor_config_id 撞号、Agent 读链路 join 不动），配置=场景类型×预期/核对时间×应发起笔数来源（func_call_sql 编码或固定值）×实际/成功 SQL 编码×容差×告警开关；AccountingScenarioCheckExecutor 三段式核对（按时执行/应发起 vs 实际/实际 vs 成功）计数走 FunctionCallService（逐条审计），差异按容差判级、alert_enabled 时推 WeCom。WorkhubJob 新增 defaultEnabled()，OPS_ACCOUNTING_SCENARIO_CHECK（每 10 分钟扫描到期配置）种子与配置行双重默认停用，旧 OPS_ACCOUNTING_RECONCILE 行删除。代扣场景占位模板 3 条 enabled=0（OPS-SCN-WITHHOLD），口径待业务确认后页面启用；前端三文件场景化重构。账务/调度 64 用例全绿、前端 vue-tsc+build 通过；BusinessLogSearchServiceTest（ELK 范围）与 TeamAliasRecoveryStaticTest（缺回滚文档）为存量失败与本需求无关。注意：迁移含 MySQL 多操作 ALTER（DROP KEY/ADD UNIQUE KEY），H2 不执行该文件，部署需真实 MySQL；执行器传参约定 :checkDate/:startDateTime/:endExclusiveDateTime。


## 2026-11-20 14:13｜202611190001 TEST 强制回购黑盒实测

按用户明确授权，对测试 amp-saps 10.10.116.32:8235 的 /api/mandatory/buyback 发起 6 次实际 POST（三项目各两次），均返回 code=900000、success=false 与项目“暂不允许强制回购”。测试窗口新增回购对账单及所选单据回购明细为 0。批量回购、补跑、自动强制回购、代偿未实测；日志补证受限（TEST 无 ELK 范围、服务器无 python3）。详情见 [黑盒测试记录](../agents-playbook/docs/业务线/BL000004-消费分期/需求/202611190001-即科医美、商品业务（通汇诚泰）回购功能调整1119/20261120-黑盒测试记录.md)。

## 2026-11-20 14:28｜202611190001 补测发现数组回购绕过

用户指出覆盖不全后，TEST XXL-JOB 任务 9 限定三项目各一笔申请单，实际生成代偿单 id 6784/6785/6786，日志 6912/6915/6916 均成功；JK-D 同参重跑日志 6917 未重复出单。随后数组入参 `applicationNos=[JK026072714343944]` 实际返回成功并生成回购单 FHGA26112014280800011（id 6787，25 条明细），12 期账单变为待回购。根因：CapitalProviderController 把 null 单值先加入列表，导致数组未进入排除校验。首轮单值拦截结论不能覆盖数组入口；整项验收不通过。已保留现场及请求/任务/数据库证据，未修改配置/代码、未支付审批或清理数据。日批、指定项目补跑、自动回购开启、规则阈值及对照项目等仍未完整覆盖，详见同一黑盒测试记录和 tasks.md。
