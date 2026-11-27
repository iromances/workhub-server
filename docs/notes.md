# WorkHub 需求分析与排查笔记

## 2026-11-17 开发完成提测被拦截

需求 202611050003 自动开发后进入开发完成，提测服务 startReview/confirmAndSubmit 仍调用仅接受开发中的旧校验，而统一 IntakeDemandStatusRules 已支持开发完成。按用户明确授权修复两入口，统一复用 SUBMIT_TESTING 规则，补齐双状态及其他阶段无副作用回归，30 项全部通过。确认无在途数字员工运行后于 16:59 重启本机服务并核实启动成功。浏览器验证需要登录，未实际提交提测、建测试单或部署；服务成功路径与外部调用使用桩验证。规格归属 REQ-a71851db。

## 2026-11-17 数字员工完整 CLI 与需求 202611050003 交付

按用户确认恢复数字员工 Codex/ZCode 原生能力并追加 WorkHub MCP，修复空授权范围、设计完成触发和同任务重试状态同步。实际自动开发期间发现预算累计耗尽，以及原生 Git 已推送但旧清单不能验收；保留历史运行，补充平台独立 GitLab 分支提交核验。经独立浏览器续跑，15:29 运行 COMPLETED，15:30 需求自动同步开发完成。scs/scs-saps 研发分支 `feature/repay_plan_query_202611050003` 的远端提交为 `e7c66a14289955b26f6e263fefa361e73d1f52fa`，2 项自测通过；TEST 联调和部署未执行。第三次重启前临时暂停新派发并等待在途任务结束，重启后已恢复原 5000ms 调度。详见 [平台验证](业务线/BL000012-工作台/需求迭代/REQ-bf94e06e-8737-47dc-a7ba-68dba9556f9e-平台内置Agent自主迭代一期/验证/20261117-原生CLI完整能力/验证说明.md)。

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

按用户明确授权，对测试 amp-saps 10.10.116.32:8235 的 /api/mandatory/buyback 发起 6 次实际 POST（三项目各两次），均返回 code=900000、success=false 与项目“暂不允许强制回购”。测试窗口新增回购对账单及所选单据回购明细为 0。批量回购、补跑、自动强制回购、代偿未实测；日志补证受限（TEST 无 ELK 范围、服务器无 python3）。详情见 [黑盒测试记录](/Users/aslight/IDEAWorkspace/agents-playbook/docs/业务线/BL000004-消费分期/需求/202611190001-即科医美、商品业务（通汇诚泰）回购功能调整1119/20261120-黑盒测试记录.md)。

## 2026-11-20 14:28｜202611190001 补测发现数组回购绕过

用户指出覆盖不全后，TEST XXL-JOB 任务 9 限定三项目各一笔申请单，实际生成代偿单 id 6784/6785/6786，日志 6912/6915/6916 均成功；JK-D 同参重跑日志 6917 未重复出单。随后数组入参 `applicationNos=[JK026072714343944]` 实际返回成功并生成回购单 FHGA26112014280800011（id 6787，25 条明细），12 期账单变为待回购。根因：CapitalProviderController 把 null 单值先加入列表，导致数组未进入排除校验。首轮单值拦截结论不能覆盖数组入口；整项验收不通过。已保留现场及请求/任务/数据库证据，未修改配置/代码、未支付审批或清理数据。日批、指定项目补跑、自动回购开启、规则阈值及对照项目等仍未完整覆盖，详见同一黑盒测试记录和 tasks.md。

## 2026-11-24 清理仓库内 agents-playbook，归档指向独立仓库

发现 2026-11-18 后非 BL000012 业务线的自动评估又落回本仓库根 `agents-playbook/`：根因是 `RepositoryDocumentPaths.playbookArchiveRoot` 未配置 playbookPath 时的默认值取 `仓库根/agents-playbook`（仓库内），而非约定的同级独立仓库；运行库 sys_config 仅配置 vaultPath，一直走该默认值。处理：仓库内 6 个需求归档（202611040003 保费分期、202611190001 消费分期含验证 JSON、2026110600003 嘉泰保理、202608110003 较新重确认版、叉车 202609070003/2026110600006 并入其唯一正式目录 BL000008-叉车）复制到 /Users/aslight/IDEAWorkspace/agents-playbook 对应目录并以 staged 删除迁出；其 docs/业务线/index.md 追加 5 条评估索引（202608110003 已有条目），新文件保持未跟踪由该仓库自行评审。清理期间运行中的旧版后端又自动归档 202611120004 新消费分期一条，同样迁出并追加索引；重启后端加载修复后的默认路径前，新确认的评估仍会写到仓库内旧位置，需再次手工迁移或尽快重启。代码修复默认值为仓库真实路径的同级 agents-playbook（新增 defaultPlaybookRepository，先 toRealPath 解析符号链接再取同级，McpRuntimeService.businessLineWikiRoot 回退同步复用）；AGENTS.md、docs/README.md、docs/业务线/AGENTS.md、docs/notes.md 中相对链接改指 /Users/aslight/IDEAWorkspace/agents-playbook。受影响测试更新断言后全绿（RepositoryDocumentPathsTest 4、McpRuntimeServiceTest 24、KnowledgeArchiveIntegrationTest 12、DevelopmentPlanFolderServiceTest 3、KnowledgeArchiveWorkflowTest 6、ProjectKnowledgeBaseServiceTest 4）；全量存量失败 BusinessLogSearchServiceTest（ELK 范围配置缺失）与 AgentWorkbenchServiceTest.apiPromptRequiresEli5SummaryReport 与本次无关。

## 2026-11-25 需求原始材料与评估输入版本归入需求目录

按用户确认调整 WorkHub 自动归档：评估与澄清前的输入版本改写到需求自己的目录 `原始材料/`（与 `研发评估.md` 同级，BL000012 在本仓库 docs/业务线，其余业务线在 agents-playbook），只保存需求原文和附件清单，附件文件留在数据库；`docs/requirement-sources/` 与 `docs/notes.md` 公司项目段退役。归档失败不再阻断 AI 评估，改记需求历史；未确认草稿不再写文件，重试归档只处理已确认评估；MCP 去掉 `businessLineRawRoot`。测试在源码副本上离线执行：运行中的调试实例直接使用各模块 target/classes，原地编译会让它混用新旧 class。定向 108 项通过，全量无新增失败。运行库只读核对发现 2026-09 及以前上传的 289 个附件没有 SHA-256，老需求的清单只能按附件 ID 引用。运行实例已于 16:10 重新加载并确认加载新代码；16:31 删除 `docs/requirement-sources/`（10 个未提交文件备份在 /tmp/workhub-requirement-sources-untracked-20261125）；16:35 对需求 286 跑第 5 轮真实评估，输入版本于 16:36 落在 agents-playbook 需求目录的 `原始材料/`，16:51 草稿生成，旧位置无写入。规格见 REQ-8be33332。

## 2026-11-25 清理仓库根目录 data 并把附件补入数据库

仓库根目录 `data/` 创建于 2026-08-21、最后写入 2026-09-01，不是新生成的：9 月之前需求附件存本地文件（`workhub.storage.local-path: data/uploads`），每录一条带附件的需求就写一次；9 月起附件改存数据库。核对：`data/` 下 28 个文件（需求 180～197）中 27 个库内已有内容且 SHA-256 逐个一致，只有附件 355（需求 197 的截图）库内无内容；库里另有 294 条 2026-04～08 的附件只存路径、文件已不存在，现在就打不开。用户确认后在内置浏览器登录页面内调用 `POST /api/attachments/355/replace?intakeId=197` 把原文件写回库（库内哈希一致、路径清空），18:01 删除 `data/`。替换接口会顺带把该需求材料导出到桌面「需求」目录并记一条“替换需求附件”历史。11-26 按用户确认把头像也改存数据库：以 `USER_AVATAR` 存入 `pm_attachment`，`GET /api/auth/avatars/{id}` 改为按头像附件 ID 读取，移除 `workhub.storage.local-path` 与 `StorageProperties`。新增 9 个头像用例，定向 109 项通过，全量离线回归无新增失败（3 个存量失败，其中启动模块的 `TeamAliasRecoveryStaticTest` 读取仓库中不存在的 `docs/WorkHub/团队重复人员合并/SDD/rollback.sql`，昨天的全量回归在服务模块失败后没有跑到它）；当天 11:03 重载实例后，用户在内置浏览器登录 admin，AI 在页面内生成图片调用头像接口完成两次上传：头像入库、替换后旧记录删除、匿名读取哈希一致、磁盘无文件；admin 的头像现在是蓝底“A”测试图。规格见 REQ-8e63d628。
