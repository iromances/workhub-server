# Design：参考 amp-saps 调整 amp-order

需求编码：REQ-428dadce-1c72-4d7d-9eb7-d4233554e6c7  
需求名称：参考 amp-saps 调整 amp-order  
业务线：BL000004／消费分期；GitLab 组：asset-plateform  
唯一需求目录：wiki/公司项目/研发需求/业务线/BL000004-消费分期/20261019-中间件升级准备  
涉及仓库：amp-saps（只读参考）、amp-order（实施）  
规格版本：v1.0（2026-10-23，用户已确认；本地实现及离线验证完成）  
当前研发分支：`feature/nacos-xxljob-upgrade_20260123`（2026-10-23 按用户要求完成本地及远程分支更名）


对应 [Proposal v1.0](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000004-消费分期/需求迭代/20261019-中间件升级准备/proposal.md>)，任务见 [Tasks v1.0](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000004-消费分期/需求迭代/20261019-中间件升级准备/tasks.md>)。

## 现状证据

- 2026-10-23 本地只读核对，业务线映射来自 WorkHub list_mcp_targets。
- 参考仓库：/Users/aslight/IDEAWorkspace/asset-plateform/amp-saps，feature/nacos-alibaba-upgrade，4ca0cd7b1db72503ccc8b4d163c63e139ca0a181。
- 目标仓库：/Users/aslight/IDEAWorkspace/asset-plateform/amp-order，master，f696b160af8081f9a2090aca35ced64c2d5005af。
- 两仓库 test namespace 相同，server-addr 不同；amp-order 没有 username/password，config 自有 group 须保留。
- 本地已有 Maven、Java、nacos-client 2.5.4 和 SnakeYAML 缓存。Java 8 离线编译及依赖解析均成功，详见 Tasks。

## 修改设计

### 根 pom.xml

在现有 dependencies 中增加直接依赖 com.alibaba.nacos:nacos-client:2.5.4，保持已有父工程、模块和其余依赖。采用与参考仓库相同的放置方式，不调整共享 thctay-dependencies。

### bootstrap.yml

在原文件上精确修改，避免整体复制 amp-saps 配置。将默认 active 改为 test，移除完整 dev-amp 文档段。只修改 test 文档中 config/discovery 的 server-addr 并各新增 username/password；四项鉴权取值及两个地址均从 amp-saps 对应节点读取，不人工猜测。保留 amp-order 的其他配置和 dev/pre/prod 文档原文。

## 契约与数据

不修改 REST/Feign 契约、业务数据或数据库结构。Nacos 配置加载与注册使用新的 test 端点，应用名仍为 amp-order。客户端失败行为沿用原框架，不引入重试或兜底代码。

## 验证与边界

1. 使用标准库解析 pom.xml，核对直接依赖唯一性和版本。
2. 使用已缓存 SnakeYAML 解析全部配置文档；按结构比较修改前后，断言预期变更及其余节点保留，凭据比较仅输出布尔结果。
3. git diff --check；离线 Maven dependency:tree 与 compile，优先 Java 8（若本地已安装），不得下载依赖。
4. 不启动 Spring 应用，不连接 Nacos、数据库、Redis、RabbitMQ 或远程服务；运行时配置加载、注册及 gRPC 连通性尚不在本次本地验证覆盖内。

## 交付与回滚

改动保留于新分支工作树，不自动提交、推送或部署。回滚本次两处文件差异即可恢复本地基线；若后续发布，客户端版本与 Nacos 地址应配套回退。验证后记录实际证据，并更新业务线迭代日志；现有系统设计若未涉及 Nacos 版本且无已部署现状变化，说明无需修改的原因。

## 确认记录

用户于 2026-10-23 确认 Proposal 所列完整范围，包括默认 test、删除 dev-amp，以及本轮 test 配置以最新 amp-saps 鉴权配置为准。


## 2026-11-04 补充设计 v2.0：同步既有 Netty 修复

本节承接 Proposal 的 AC-N1～AC-N4，来源为用户指定的已有 v2 分支，不改变历史 v1.0 方案。

### 核实基线

- thctay-public：`/Users/aslight/IDEAWorkspace/thctay-public`，原升级分支及 origin 均为 `d44d555c252b751af4d367fdea656fdb1fda41cb`，工作区干净。
- thctay-public v2：`71494aa9133c1a973c2cf0a48ecc11004e31a6e8`，相对原分支仅 `thctay-dependencies/pom.xml` 增加 10 行。
- lease-public：`/Users/aslight/IDEAWorkspace/lease-public`，升级分支及 origin 均为 `86e2721313671b452a85869c8188e71bee2b9dab`，工作区干净。
- 已刷新相关远程引用。Java 8、Maven 和 Netty 4.1.140.Final 所需关键缓存已存在。

### 实施和宿主影响

在两个公共 dependencies POM 的 properties 中加入 `netty.version=4.1.140.Final`，并在 dependencyManagement 第一项导入 `io.netty:netty-bom:${netty.version}`，优先于现有 Boot/Cloud BOM。thctay 侧保持与 v2 同字节；lease 侧仅移植同一增量。

公共构件仍为已有升级坐标 `0.0.2-SNAPSHOT`。继承该 BOM 的宿主需重新构建才能获得依赖变更；未升级的 `0.0.1-SNAPSHOT` 构件不在本次修改范围。需要在后续环境回归中覆盖 Nacos 连接、XXL-JOB 执行器、网关及 Redis/Redisson；本次源码和离线验证不证明运行环境已修复。

| 决策 | 锚点类型 | 验证 |
| --- | --- | --- |
| 复用 v2 的现有版本和 BOM 优先级 | C，AC-N1～N3 | 源文件比较、有效 POM 和依赖树断言 |
| 仅修改公共 BOM，不重构业务或 MQ | A，交付检查 | 精确变更路径和前后差异检查 |
| 离线验证，不新增下载或本地安装 | A，交付检查 | Maven -o、compile / verify，不执行 install / deploy |

### 验证与回滚

使用 Java 8 执行两仓离线编译、受影响模块验证、有效 POM 和依赖树核对。完整命令、退出码及失败原因记录于 Tasks。验证不启动宿主应用或连接业务基础设施。

本轮只留下两个 POM 的工作区差异；回滚仅撤销各自新增的 10 行，不涉及数据库或运行配置。业务线系统现状未发生部署变化，无需改写已运行系统现状；实际开发进展记入业务线迭代日志。

## 2026-11-04 补充设计 v3.0 生产不停机迁移

对应 Proposal 的 AC-P1～AC-P5，详细步骤、放行条件及回退见 [生产不停机迁移方案](生产Nacos和XXLJOB不停机迁移方案_20261104.md)。用户已确认完整新版本可并行部署、任务可短暂延后但必须补齐。

设计顺序：准备独立新 Nacos/XXL-JOB 及新库 → 新应用依赖就绪并验证并行行为 → 在线流量灰度 → XXL-JOB 旧侧收敛与触发隔离 → 新侧接管和补跑 → 观察及退出旧环境。用户确认 MQ 共用，不设置独立 MQ 交接阶段；消费者随应用正常发布与退出，开启前核对依赖就绪、消息兼容和原有消费语义。旧应用、旧中间件和原参数保留供回退，生产业务数据继续共用。

| 设计 | 验收锚点 | 实施验证 |
| --- | --- | --- |
| 完整两套应用，按入口灰度，不将新旧中心混填一个客户端地址列表 | AC-P1 | 真实调用路径、连接归属、入口回切演练 |
| 新 Nacos 实际拉取验收，跨注册中心依赖单独处理 | AC-P2 | 配置键/摘要、SDK 结果、服务调用及健康/摘除验证 |
| 同一任务仅一个中心负责，收敛旧执行与回调后隔离触发 | AC-P3 | 预读/队列、重试、父子链、回调及有效隔离证据 |
| 按业务批次记录缺口，不依赖 misfire 自动补全 | AC-P4 | 补跑台账、幂等、账务及外部结果核对 |
| 共用 MQ，消费者随应用发布和正常关闭；在线与调度分别交接和回退 | AC-P5 | 消费模型、ACK/幂等、后台互斥、容量及新旧契约兼容性 |

事实边界：版本和任务数量来自桌面 v1.6 历史方案；两公共仓库本地升级分支提交已回读，所有业务应用的实际生产制品和联调结果尚需执行清单确认。本轮没有读取生产配置或运行状态，不据历史任务数量声明生产就绪。正式目录为本文件所在 WorkHub 需求目录，前述旧路径保持历史含义。

## 2026-11-05 补充设计 v4.0：生产迁移辅助脚本

对应 AC-S1～AC-S5，交付到本目录 `生产迁移脚本_20261105/`。使用已有 Python 3 标准库，无安装步骤。生产新入口来自用户附件；Nacos 源/目标库为 amp_nacos / amp_nacos_254，XXL-JOB 源/目标库为 amp_xxl_job / amp_xxl_job_250。初始 INSERT 迁移仍是独立步骤，不能在已有数据的新库重跑。

Nacos：只处理用户明确列出的新中心配置。预览在内存中定位两个指定属性，正文不输出；常规单文档 YAML 映射及 properties 支持原位置替换，歧义/重复/复杂结构拒绝。运行时输入 token。发布使用 CAS 摘要校验，保留配置元数据，逐项从客户端取配置接口回读；批量发布非事务，失败停止，已成功项保留并可重跑。旧配置和历史记录不做批量字符串替换。

XXL-JOB：对六个已核实 AppName 取完整分页列表，冻结状态与定义摘要；快照不保存执行参数/GLUE 正文。新侧任务保持同一 ID，摘要归一化旧 jobCron 与新 CRON/scheduleConf，逐任务检查定义、组、子任务范围和执行器注册地址。启用前必须完成旧中心触发隔离及在途/重试/回调收敛；脚本显式要求操作人声明该门槛已经完成，不能以“控制台停用”或“旧中心不可访问”自动替代。调用 /jobinfo/start，不调用 /trigger，不写 trigger_next_time。

默认预览；显式执行按子任务优先的拓扑顺序启用快照 ON 项，原 STOP 子任务继续 STOP，父子触发关系保留。保存每次请求前后的进度，单次失败中止；重跑在定义一致的前提下跳过已启用项，避免重算它们的下一次计划时间。启用动作是顺序执行，不保证跨任务原子性，不自动反向启旧；补跑与回退按生产方案单独进行。
