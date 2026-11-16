# Tasks：参考 amp-saps 调整 amp-order

需求编码：REQ-428dadce-1c72-4d7d-9eb7-d4233554e6c7  
需求名称：参考 amp-saps 调整 amp-order  
业务线：BL000004／消费分期；GitLab 组：asset-plateform  
唯一需求目录：wiki/公司项目/研发需求/业务线/BL000004-消费分期/20261019-中间件升级准备  
涉及仓库：amp-saps（只读参考）、amp-order（实施）  
规格版本：v1.0（2026-10-23，用户已确认；本地实现及离线验证完成）  
当前研发分支：`feature/nacos-xxljob-upgrade_20260123`（2026-10-23 按用户要求完成本地及远程分支更名）


目标：本地开发、test 配置；目标分支 codex/amp-order-nacos-upgrade。实施：Codex；确认人：用户（2026-10-23）。

## 任务及真实状态

| 任务 | 交付物 | 前置 | 验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 核实 IDEA、业务线、基线和两仓库差异 | 无 | 证据完整 | 已完成，只读 |
| T-02 | 完整规格统一确认 | T-01 | 范围与旧文档差异明确 | 用户已确认，2026-10-23 |
| T-03 | 分支与两文件最小修改 | T-02 | AC-01～AC-03 | 已完成，工作树未提交 |
| T-04 | XML/YAML 检查、离线依赖树与编译 | T-03 | AC-01～AC-04 | 已完成，本地检查通过 |
| T-05 | 更新本记录及业务线迭代日志、交付文件链接 | T-04 | 实际结果和缺口清楚 | 已完成 |

按 T-01 至 T-05 顺序执行。允许修改范围为 amp-order 根 pom.xml、amp-order-start/src/main/resources/bootstrap.yml 及对应规格/进展文档。参考 amp-saps 不修改；配置凭据不得输出至记录。

## 验证结果

- [x] XML 解析及 Nacos 客户端直接依赖唯一性。
- [x] YAML 解析；test 新值与源配置一致，dev-amp 删除，其他 profile 和服务属性保留。
- [x] Git 空白错误检查及两文件差异审阅。
- [x] Maven 离线依赖树和 compile；如环境或缓存阻塞，记录原始失败原因，不擅自下载安装。
- [x] 交付说明开发、验证与部署状态；没有运行时验证即不得写为已完成联调。

## 统一确认记录

范围：Proposal v1.0、Design v1.0 全文。用户于 2026-10-23 回复“确认，按这个 Nacos 升级范围执行”，明确确认新增 nacos-client 2.5.4、默认 test、移除 dev-amp、测试地址与账号密码沿用 amp-saps，以及新分支离线验证。结论：已确认，可以实施。旧迁移文档的不新增凭据口径在本轮 test 范围内以此确认及 amp-saps 参考提交为准。

## 实际证据

2026-10-23：IDEA 显示 amp-saps／feature/nacos-alibaba-upgrade。Git 记录 amp-saps 4ca0cd7b1db72503ccc8b4d163c63e139ca0a181（“nacos升级”）；amp-order master f696b160af8081f9a2090aca35ced64c2d5005af，两工作树均干净。WorkHub 映射 BL000004／消费分期／asset-plateform。以上为实施前基线记录。


### 2026-10-23 实施与验证

- 分支：`codex/amp-order-nacos-upgrade`；基线保持 `f696b160af8081f9a2090aca35ced64c2d5005af`，改动未提交。
- 改动：仅根 `pom.xml` 和 `amp-order-start/src/main/resources/bootstrap.yml`；新增直接依赖 `com.alibaba.nacos:nacos-client:2.5.4`，默认 profile 改为 test，删除 dev-amp，test 配置/注册两个地址及鉴权项逐项沿用 amp-saps 参考提交。
- XML 结构验证通过：除新增唯一的 nacos-client 2.5.4 直接依赖外，POM 结构与基线一致。
- 使用本地 SnakeYAML 1.25 和 JDK 8 解析验证全部 YAML 文档；默认 test、删除 dev-amp、test 目标字段与参考配置一致，其余配置结构与基线完全相同，应用名称仍为 amp-order。凭据未输出到文档或日志。
- `git diff --check` 通过。
- 本地 Java SE 8 `1.8.0_481`、Maven `3.9.14`，执行 `mvn -o -B -Dstyle.color=never -DskipTests compile`：退出码 0，BUILD SUCCESS，聚合工程及 7 个业务模块全部成功。
- 执行 `mvn -o -B -Dstyle.color=never -N dependency:tree -Dincludes=com.alibaba.nacos:nacos-client`：退出码 0，BUILD SUCCESS，解析结果 `com.alibaba.nacos:nacos-client:jar:2.5.4:compile`。
- 构建仍报告既有重复依赖声明、Druid 1.2.6 POM 中 tools/jconsole 的 systemPath 问题和 Hibernate Validator relocation 提示；这些依赖声明不在本次变更中，构建未被阻断。本轮未扩大范围修复。
- 临时完整构建日志：`/var/folders/br/ndyhl3gs0rvb6z12m_x8rqf00000gn/T/amp-order-nacos-c2dc0z0j/compile.log` 与 `dependency-tree.log`。
- 未运行 Spring 应用或集成测试；Nacos 配置拉取、注册、鉴权及 gRPC 连通性尚待指定环境联调。未安装或下载任何软件/依赖，未提交、推送或部署。
- 已核对业务线系统设计：当前仅描述跨业务线业务规则，本轮没有变更业务契约或部署现状，故不改系统设计。实际进展记入业务线 `迭代日志.md`；原生产迁移文档维持其原适用范围。


### 2026-10-23 分支名称更新（本轮最新状态）

用户要求 amp-order、amp-saps、thctay-public 的本地及远程升级分支统一为 `feature/nacos-xxljob-upgrade_20260123`，按用户原样指定的日期后缀执行。

| 仓库及工作区 | 原本地分支 | 远程处理 | 核验提交 |
| --- | --- | --- | --- |
| asset-plateform/amp-order | codex/amp-order-nacos-upgrade | 原分支未推送，创建目标远程分支 | f696b160af8081f9a2090aca35ced64c2d5005af |
| asset-plateform/amp-saps | feature/nacos-alibaba-upgrade | 原子创建目标远程分支并删除旧远程分支 | 4ca0cd7b1db72503ccc8b4d163c63e139ca0a181 |
| IDEAWorkspace/thctay-public | feature/nacos-xxljob-upgrade | 原分支未推送，创建目标远程分支 | 3bb5e766c850ef43bde4bdb58de0eb211aaaabc9 |

三仓库的本地分支与 origin 同名分支提交一致，均已设置同名上游，旧本地分支与对应旧远程分支均不存在。通过变更前后的工作树 diff、暂存区 diff 及状态摘要校验，全部未提交改动保持原样。amp-order 和 thctay-public 的未提交代码仍仅在本地，本次没有创建提交。`xxt-plateform/thctay-public` 的另一份修复工作区未操作。

前述旧分支名称和“未推送”描述保留为历史验证记录，当前分支及远程状态以上述最新核验为准。


## 2026-11-04 补充任务 v2.0：public v2 同步

- 需求编码：`REQ-428dadce-1c72-4d7d-9eb7-d4233554e6c7`；对应本目录 Proposal/Design 的 2026-11-04 补充章节。
- 范围确认依据：用户本轮明确要求“public有个v2版本，帮我更新到v1版本还有lease-public那边”；仅移植既有 `71494aa` 提交的 Netty 管理增量。没有将新的实现方案或环境发布写为已获确认。
- 目标分支：两仓无 `-v2` 后缀的原升级分支；代码工作区为 IDEAWorkspace 根目录两仓。
- 计划：核实来源与基线 → 同步两份 POM → 离线验证 → 记录实际结果。生产配置读取不在范围内。

| 任务 | 对应验收 | 状态 |
| --- | --- | --- |
| T-N1 核实唯一增量、仓库副本及远程 SHA | AC-N1、AC-N2 | 已完成 |
| T-N2 同步 thctay 与 lease 的 Netty BOM 管理 | AC-N1、AC-N2 | 已完成，两个 POM 各增加 10 行，工作区未提交 |
| T-N3 有效模型、依赖树、Java 8 离线构建和差异核对 | AC-N3、AC-N4 | 已完成，编译及受影响模块依赖解析通过 |
| T-N4 更新验证记录及迭代日志 | 全部 | 已完成 |

### 实际验证结果

- thctay-dependencies 的工作区文件与来源提交 `71494aa9133c1a973c2cf0a48ecc11004e31a6e8` 对应文件逐字节一致；lease-dependencies 仅增加同一属性和 BOM 导入，移除新增的 10 行后与修改前 HEAD 完全一致。
- 两仓均保持原升级分支；分别基于 `d44d555` 和 `86e2721`，只留下各自 dependencies POM 的未提交修改。没有修改 v2 来源分支，也没有操作组目录下的 MQ 修复副本。
- 使用已有 Java SE 8 `1.8.0_481` 和 Maven `3.9.14`。两仓执行 `mvn -o -B -Dstyle.color=never clean compile` 均退出 0；thctay 15 个 reactor 节点、lease 25 个节点全部成功。
- 对两份 dependencies POM 执行离线 `help:effective-pom`，并逐坐标（含 classifier）核对新 Netty BOM 管理的 44 个 Java 构件，版本全部为 `4.1.140.Final`。
- 分别执行 `mvn -o -B -Dstyle.color=never -pl <prefix>-dependencies,<prefix>-schedule,<prefix>-mq -am dependency:tree -Dincludes=io.netty:* -Dverbose`；两侧升级版 schedule 与 MQ 实际解析的 8 个 Netty 构件均为 `4.1.140.Final`。未运行集成测试或启动应用；受影响模块未发现测试源码。
- 第一次模型断言把所有历史管理条目和 `-am` 带入的旧模块都视为此次升级对象，因而失败。按来源提交的实际范围拆分后，确认旧父 BOM 遗留的 `netty-example` 和两个旧 `linux-aarch64` classifier 仍为 `4.1.51.Final`，但未进入本轮 schedule/MQ 依赖树；`lease-cache:0.0.1-SNAPSHOT` 自身仍使用旧 Netty，这是旧坐标保留范围。没有据此擅自扩大 v2 的代码增量。
- 本次不能表述为所有宿主及所有历史 classifier 都已升级；宿主打包时仍需验证最终依赖和原生传输/TLS 路径。部署及 Nacos/gRPC、XXL-JOB、网关、Redis/Redisson 运行回归未执行。
- 两仓 `git diff --check` 通过。既有重复依赖及旧依赖 POM 警告没有阻断构建，未扩大范围修改。
- 未下载或安装依赖，未执行 `install` / `deploy`，未提交、推送或部署，未读取生产配置。
- 临时完整日志：`/tmp/public-netty-sync-20261104/` 下的 `thctay-compile.log`、`lease-compile.log`、两侧 `*-effective.log`、`*-dependencies.log`；结构化核验结果见本目录 `public-netty-sync-20261104-validation.json`。
- 基线影响：无业务契约、数据库、资金算法、MQ 消息语义或已部署系统现状变更；仅研发分支公共依赖管理变化，因此更新本规格及业务线迭代日志，不改已运行系统现状或 lease-public 的受保护基线文档。

### 2026-11-04 后续交付：用户授权提交并推送

用户明确要求：“把这两个仓库的改动提交并推送到对应的升级分支”。提交前重新刷新两个远程引用，确认没有远程新增提交、没有已有暂存内容，且两仓仅包含已经验证的 Netty 增量。各自仅暂存 dependencies POM，正常推送，没有强制更新。

| 仓库 | 升级分支 | 提交与远程回读 SHA | 最终状态 |
| --- | --- | --- | --- |
| thctay-public | release/nacos-2.5.4-xxljob-2.5.0_20261027 | 4252fe537d3c64700b715ebf7d2287b52d5a10c8 | 本地与远程一致，工作区干净 |
| lease-public | release/nacos-2.5.4-xxljob-2.5.0_20261027 | cf290f0fec83cffe47456d0841e1045b146d35a9 | 本地与远程一致，工作区干净 |

两次提交均为 1 个文件、10 行新增，标题为“fix: 统一 Netty 至 4.1.140.Final”。提交内容与先前通过离线编译和依赖核验的内容完全一致，因此未重复运行相同构建。前述“未提交、未推送”保留为首次验证阶段的历史状态，当前交付状态以本节为准；未执行构件安装、发布或环境部署。

## 2026-11-04 补充任务 v3.0 生产不停机迁移方案

用户要求准备消费分期生产 Nacos/XXL-JOB 平滑过渡方案，并在本轮确认“允许短暂延后，补跑保证完整”“可以临时部署完整新版本”。amp-wolease 排除项和禁止未经允许读取配置文件的限制沿用。范围属于方案编写，不是生产执行授权。

| 任务 | 验收 | 状态 |
| --- | --- | --- |
| T-P1 核对桌面方案、既有迁移记录及适用范围 | AC-P1～AC-P5 | 已完成，生产历史快照与当前事实分开标记 |
| T-P2 核对 Nacos/XXL-JOB 官方机制 | AC-P2～AC-P4 | 已完成，兼容性、地址列表、dump、预读/重试/回调、子任务和 misfire 均有来源 |
| T-P3 起草生产不停机步骤、验收和回退 | AC-P1～AC-P5 | 已完成，见本目录生产迁移方案 |
| T-P4 填齐生产部署、依赖、任务清单及演练结果 | AC-P1～AC-P5 | 待实施前补齐；未读取生产配置，未查询实时运行数据 |
| T-P5 最终执行清单确认、生产发布与验收 | AC-P1～AC-P5 | 未执行；发布授权另行记录 |

交付：[生产 Nacos 和 XXL-JOB 不停机迁移方案](生产Nacos和XXLJOB不停机迁移方案_20261104.md)。方案采用完整两套应用并行，在线流量与调度分阶段交接；MQ 共用，消费者随应用正常发布和退出，不另设迁移阶段。在旧任务及回调收敛、触发隔离后启用新侧，以业务批次台账处理漏跑。无需注册的应用按既有职责验收，不强制新增注册。

本轮回读本地 public 升级分支，提交仍为 thctay-public 4252fe537d3c64700b715ebf7d2287b52d5a10c8 和 lease-public cf290f0fec83cffe47456d0841e1045b146d35a9，未重新刷新远程引用；不据此推断所有宿主已重建。当前会话未提供 WorkHub 运维 MCP，生产实时基线留待受控核验。

本轮只新增方案及补充既有规格/迭代记录，不安装工具、依赖或脚本，不修改应用/运行配置，不执行部署、任务触发、数据库写入或重启。系统运行现状没有变化，故不改写业务线现状。已有其他在途工作和暂存内容保持原样，本轮文档不提交或推送。

文档验证：新增方案和本轮补充段落的 11 个本地链接全部可解析，新增内容无行尾空白；四份已有文档的 git diff --check 通过。已回读范围、任务交接、补跑、回退与退出条件；未执行应用测试或生产演练，本次文档检查不替代运行验收。

2026-11-04 用户补充“MQ用的是同一个”：修正初稿中独立 MQ 交接阶段及强制统一暂停消费者的表述。保留共用 MQ 下的队列/绑定、消息兼容、ACK/幂等、依赖就绪和实例优雅退出检查；HTTP 灰度不控制 MQ 消费比例。公共重投若由 XXL-JOB 触发，仍按单中心调度规则执行。本次仅修订文档，未读取 MQ 配置或操作运行环境。

2026-11-04 用户确认入口使用“Nginx／HAProxy 等反向代理”：将在线切换位置明确为现有代理的网关后端池，补充准备、加权灰度、全量摘流和回切状态表。具体产品/版本和新旧网关地址尚未核实；Nginx 仅作为操作示例，不误记为现网已确定产品。更新方案的入口待定描述，未读取或修改现网配置、未执行 reload。WorkHub 工具此时已恢复可用，但本次仍为方案细化，未进行生产运行查询。

## 2026-11-05 补充任务 v4.0：生产迁移辅助脚本

范围确认来源为用户本轮两项明确指令：替换 Nacos 内 XXL-JOB 参数、按旧任务一键启用；其后提供新生产迁移资料。沿用 v3.0 已确认的单中心调度交接原则，本轮实施仅生成本地脚本，不执行生产变更。

| 任务 | 验收 | 状态 |
| --- | --- | --- |
| T-S1 核实迁移脚本、官方接口及新中心信息 | AC-S1～S4 | 已完成；只读取用户附件和必要的任务统计/库名 |
| T-S2 Nacos 定向替换工具 | AC-S1、S5 | 脚本完成；DataId / Group 清单尚未提供，生产替换未执行 |
| T-S3 XXL-JOB 快照与批量启用工具 | AC-S2～S4 | 脚本完成；生产冻结快照及启用未执行 |
| T-S4 本地模拟及交付记录 | AC-S5 | 24 项本地模拟通过；真实中间件写入演练未执行 |

只读核查：WorkHub BL000004 / asset-plateform / PROD，资源 amp-db-prod、readonly；本轮无宿主代码变更，业务代码生产默认 master，未刷新或引用代码 SHA。2026-11-05 老库六组业务任务 82 条，其中启用 68、停止 14；新库同组 82 条全部停止。示例 1 条停止，amp-wolease 为 0 条。此结果仅为今日准备快照，切换前仍需再次保存。

首次跨两库 UNION 统计因字符排序规则冲突失败，改为分别查询完成；没有修改排序规则。尝试列出生产 Nacos 配置标识被自动审批拒绝，理由是超出 XXL-JOB 参数范围；没有继续枚举或读取正文。用户随后提供的资料包含入口、凭据和库名，但没有 DataId / Group 清单。凭据不复制到本目录。

### 实际交付与验证

- [生产迁移脚本与操作顺序](生产迁移脚本_20261105/README.md)：两份生产库名初始 SQL，Nacos 定向替换工具，XXL-JOB snapshot / check / start 工具，空的限定范围模板及本地模拟。
- 初始 SQL 沿用桌面字段映射，增加 InnoDB、目标非空检查、明确事务隔离及错误回滚；XXL-JOB 仍全部 STOP，旧自动地址不带入。已有数据的新库拒绝初始导入。SQL 没有连接实例执行，不能将静态检查描述为数据库验证通过。
- 本地 Python 3.9.6、标准库测试 24 项通过；两个 CLI 帮助与空清单失败门槛检查通过。未安装依赖，未登录真实 Nacos/XXL-JOB，未发布配置或发起停用、启用、手动执行。
- 覆盖：超过单页的列表读取、重复分页、任务缺失/漂移、原 STOP、旧地址混入、跨范围子任务/循环、停旧后的错误快照、摘要篡改、部分失败、响应丢失后回读重跑、同一任务不重复启用、Nacos 重复/复杂结构拒绝、其他内容保留、token 缺失新增、CAS 冲突、缓存延迟、命名空间/加密类型拒绝和部分发布保留。
- 只更新本需求产物、生产方案与业务线迭代日志；未改变已部署系统现状，未提交或推送。本轮用户在途修改和历史记录不作清理。
- 交付静态核验：Python AST、原 SQL INSERT 映射一致性（仅 Nacos 生产库名替换）、空清单在登录前拒绝、附件凭据未嵌入脚本，以及指定文档 `git diff --check` 均通过。结果与交付文件 SHA256 见 [validation.json](生产迁移脚本_20261105/validation.json)。
- 已将 9 个交付文件复制到桌面 `/Users/aslight/Desktop/nacos和xxljob升级/生产迁移脚本_20261105`，逐文件 SHA256 与正式归档一致。保留原有桌面脚本，没有覆盖或安装脚本，没有自动执行任何迁移动作。
