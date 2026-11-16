# 消费分期中间件升级实施方案

目标版本：Nacos 2.5.4 · XXL-JOB 2.5.0

版本 v2.3 · 更新日期 2026年10月22日 · 代码核查基线 2026年10月19日 · 生产页面核查 2026年10月22日

## 一 目标

以消费分期为实施对象，完成中间件服务端、业务客户端、任务入口和配套安全配置升级。其他业务线以本方案改造项及验收记录为模板准备。

**本次采用同一维护窗口一次性切换：迁移 Nacos 和 XXL-JOB 调度中心，统一发布已完成客户端升级与执行器适配的应用包。联合验证通过后恢复流量和任务；保留升级前的应用包、配置、旧中间件及数据库，并演练整体回退。**

| 对象 | 现状依据 | 目标与本次处理 |
| --- | --- | --- |
| Nacos 服务端 | 1.3.2，单节点，MySQL；用户确认且控制台版本、节点信息已核对 | 新建独立 2.5.4 集群；采用新集群迁移路线 |
| Nacos Java 客户端 | 源码父 POM 链推导为 1.2.1 | 14 个 Java 后端统一验证并升级至 2.5.4 |
| XXL-JOB 调度中心 | 2.2.0，单节点，MySQL；用户确认且页面版本已核对 | 部署独立 2.5.0 调度中心及独立数据库 |
| xxl-job-core | 源码管理版本 2.2.0 | 升级至 2.5.0；同步改造 86 个业务入口及 1 个公共入口 |
| 业务应用框架 | Boot 2.2.9.RELEASE / Oracle JDK 8u361（截图） | 本次先保持；兼容或安全验收失败则阻断上线并另行升级 |
| 安全整改 | T38、T41 及认证、传输、审计相关问题 | 漏洞按原扫描项复测；加密、认证、留存分别提交验证证据 |

### 验收目标

全部在用服务可以注册、发现、加载和刷新配置；全部在用任务保持原 handler 名称、参数、调度策略和业务结果；支付、代扣、清结算及补偿无重复处理；失败可被识别并告警；完成回退演练及原漏洞复测。

版本边界：本方案属于升级实施准备，未完成新版本应用构建、联调或生产变更。2026年10月22日用户明确确认消费分期生产为 Nacos 1.3.2、XXL-JOB 2.2.0，Safari 中两个生产控制台的版本标识与之相符；两套中间件均为消费分期专用，无其他业务线共用。

## 二 系统现状

代码基线为 2026年10月19日同步的 asset-plateform 各仓库 origin/master。17 个仓库中有 14 个 Java 后端、2 个前端和 1 个文档仓库。用户已确认生产依赖与 master 最新代码一致，本方案据此采用已核查的源码依赖作为生产依赖基线，不再将“生产依赖是否与 master 一致”列为待确认项。本次未重新同步仓库；既有代码清单对应 2026年10月19日提交。实施时固定当次 master 的提交 SHA，并对该基线之后的改动做增量检查，避免用浮动分支名代替发布版本。

| 基础项 | 已核实的源码现状 | 影响 |
| --- | --- | --- |
| 公共父工程 | thctay-dependencies 0.0.1-SNAPSHOT | 13 个后端直接继承；risk-backend 经 bpm-framework 间接继承 |
| Spring 组合 | Boot 2.2.9.RELEASE<br>Cloud Hoxton.SR7<br>Alibaba 2.2.1.RELEASE | Spring Framework 按 Boot 默认管理为 5.2.8.RELEASE；不是新版本联调结论 [1](https://raw.githubusercontent.com/alibaba/spring-cloud-alibaba/2.2.1.RELEASE/spring-cloud-alibaba-dependencies/pom.xml)[2](https://raw.githubusercontent.com/spring-projects/spring-boot/v2.2.9.RELEASE/spring-boot-project/spring-boot-dependencies/pom.xml) |
| Nacos 依赖 | Alibaba BOM 管理 nacos-client 1.2.1 | 配置中心和注册中心均需改；不要只改其中一项 [1](https://raw.githubusercontent.com/alibaba/spring-cloud-alibaba/2.2.1.RELEASE/spring-cloud-alibaba-dependencies/pom.xml) |
| XXL 公共组件 | thctay-schedule 0.0.1-SNAPSHOT<br>xxl-job-core 2.2.0 | BaseJobHandler 使用旧接口；配置中 Token 允许为空、执行日志默认 30 天 |
| 额外硬编码 | amp-risk/risk-schedule/pom.xml<br>直接固定 xxl-job-core 2.2.0 | 必须删除局部版本覆盖，否则父 POM 升级不生效 |
| 公共任务 | thctay-mq/MessageSenderRetrySchedule | 另有 1 个旧入口；订单、支付、清结算、风控、公共接口有直接 MQ 依赖 |
| 框架例外 | amp-risk 存在旧日志版本属性；<br>两个 gateway 固定 Gateway 2.2.3.RELEASE | 依赖合并和网关回归需要单独检查，不能只验证支付服务 |

14 个后端在同一窗口完成 Nacos 客户端升级；其中包含 XXL 或公共 MQ 调度依赖的应用同时适配执行器。每个应用准备一个目标包，具体范围以第四节清单及打包依赖为准。

### 改造工作量

| 业务仓库 | 任务模块或目录 | 旧入口数 |
| --- | --- | --- |
| amp-capital | capital-schedule | 26 |
| amp-saps | saps-schedule | 30 |
| amp-payment | payment-schedule | 13 |
| amp-order | amp-order-service / service/xxljob | 8 |
| amp-ops-reporting | amp-ops-reporting-service / service/schedule | 6 |
| amp-risk | risk-schedule | 3 |
| 合计 | 以上均为有效 @XxlJob 注解入口 | 86 |
| 另计公共组件 | thctay-mq / MessageSenderRetrySchedule | 1 |

数量按代码入口统计，不代表生产任务记录数。2026年10月22日控制台逐组核查共 83 条任务记录，其中 68 条 RUNNING、15 条 STOP，见下方现场清单；一个 handler 可对应多条任务记录。

### 生产现场核查（2026年10月22日）

来源分为用户确认、用户提供的终端截图及 Safari 已打开的生产控制台。以下为当次观察值；上线前再按冻结时点核对差异。本次仅查看页面和列表，没有修改生产配置、任务定义或启停状态，没有触发任务。

| 项目 | 已确认现状 | 来源与边界 |
| --- | --- | --- |
| 生产依赖基线 | 与 master 最新代码一致 | 用户确认；既有依赖清单来自 2026年10月19日源码核查，本次未重新拉取 master |
| JDK | Oracle Java 1.8.0_361，Runtime build 1.8.0_361-b09，64-Bit Server VM 25.361-b09 | 用户提供 ecs-amp-public-0001 的 java -version 截图；未逐个进程检查 JAVA_HOME |
| Nacos | 1.3.2，单节点，MySQL；节点状态 UP | 用户确认；页面节点为 172.17.0.120:8848 |
| XXL-JOB | 2.2.0，单节点，MySQL | 用户确认；控制台入口 172.17.0.120:38080/xxl-job-admin，页脚为 2.2.0 |
| 使用范围 | 两套中间件均为消费分期专用，无其他业务线共用 | 用户确认；跨系统业务接口仍按原联调范围回归 |
| MySQL 版本及实际表结构 | 尚未取得具体版本和表结构 | 控制台列表不展示；由 DBA 提供版本和中间件库 DDL，不需要数据库密码 |

#### Nacos 配置及注册服务

- 命名空间 `prod` 的 ID 为 `8941c382-6c67-4de8-a6fe-6ae6b8d9003b`，共 16 条配置，均为 `DEFAULT_GROUP`。迁移保留原 ID、dataId、group，不能仅创建同名空间。
- 16 个 dataId：`amp-intfcommon.yml`、`amp-order.yml`、`amp-auth`、`amp-gateway`、`amp-risk.yml`、`amp-intfrisk.yml`、`amp-capital.yml`、`amp-opgateway`、`thctay-contract.yml`、`amp-saps`、`amp-payment`、`amp-risk-scene-backend.yml`、`amp-risk-backend.yml`、`amp-ops-reporting`、`amp-wolease`、`amp-intf-risk-jt.yml`。有无 `.yml` 后缀均按原值保留。
- `public` 另有 1 条配置：dataId=`nacos.cfg.dataIdfoo`、group=`foo`。未读取其配置正文，名称不能证明其无用，迁移前确认是否保留。
- prod 服务列表在“隐藏空服务”开启时可见 13 个服务、29 个实例，健康实例数均等于实例数；每个服务的逻辑集群数为 1、保护阈值触发状态为 false。这里的服务实例数不代表 Nacos 服务端节点数。

| prod 服务名 | 实例数／健康实例数 |
| --- | --- |
| amp-risk-backend | 1／1 |
| amp-order | 4／4 |
| amp-capital | 4／4 |
| amp-intf-risk-jt | 1／1 |
| amp-opgateway | 1／1 |
| amp-intfcommon | 3／3 |
| amp-risk | 2／2 |
| amp-auth | 1／1 |
| amp-saps | 3／3 |
| amp-gateway | 4／4 |
| amp-payment | 3／3 |
| amp-ops-reporting | 1／1 |
| amp-intfrisk | 1／1 |
| 合计 | 29／29 |

`amp-wolease` 未出现在上述有实例的服务列表中；`thctay-contract.yml`、`amp-risk-scene-backend.yml` 有配置，但未见同名在线服务。这些观察不能直接证明系统已下线，需负责人确认使用情况后调整实际发布范围。

#### XXL-JOB 执行器和生产任务

8 个执行器分组均为自动注册。在线地址数与任务数量是两类指标；RUNNING 表示调度启用状态，不表示当前正在执行，也不表示最近执行成功。

| 执行器 AppName | 在线地址数 | 任务记录数 | RUNNING | STOP | STOP 任务 ID |
| --- | --- | --- | --- | --- | --- |
| amp-capital | 4 | 25 | 22 | 3 | 81、47、16 |
| amp-ops-reporting | 1 | 6 | 6 | 0 | — |
| amp-order | 2 | 8 | 7 | 1 | 66 |
| amp-payment | 3 | 17 | 14 | 3 | 54、18、17 |
| amp-risk | 1 | 2 | 1 | 1 | 86 |
| amp-saps | 1 | 24 | 18 | 6 | 49、42、41、40、25、8 |
| amp-wolease | 0 | 0 | 0 | 0 | — |
| xxl-job-executor-sample | 0 | 1 | 0 | 1 | 1 |
| 合计 | 13 | 83 | 68 | 15 | 15 条 |

迁移时必须落实以下差异：

1. `payCheckSchedule` 在支付分组对应多个任务 ID 和不同 Cron，不能按 handler 去重合并；每条任务的参数也必须分别迁移。
2. 清结算任务 13 描述为“还款计划逾期天数计算（子任务8）”，任务 8 描述为“回购对账单生成（任务13触发）”且为 STOP。该关系目前仅由任务描述提示，须核对数据库 `child_jobid`；不能因 STOP 状态删除任务 8，也不能擅自把它改成定时启用。
3. 部分服务的 Nacos 实例数与 XXL 在线地址数不同，例如订单 4／2、风控 2／1、清结算 3／1。两类实例用途不同，先核对执行器部署配置，不能直接按 Nacos 实例数量补建执行器。
4. 任务清单存在凌晨日切、每月 1 日账单、09:31—09:37 对账及 10:15／14:15／20:15 代扣批次等时间点。维护窗口由业务负责人结合完整任务链确定；暂停期间的补跑规则不能仅凭 Cron 推断。
5. 本次已读取每组列表中的任务 ID、描述、handler、Cron、启停状态并汇总；尚未形成包含任务参数、路由、阻塞、重试、超时及子任务字段的完整迁移快照。该快照在实施前由受控导出生成，不要求人工重新填写已读取的版本和数量。

#### 尚需补充及实施前产物

- 需确认：MySQL 具体版本、中间件库实际 DDL／定制变更；wolease、合同及风控场景配置是否在用；public 配置是否保留；T38、T41 漏洞扫描明细。
- 排期时确定：维护时段、最长停机时间、回退决策人、各资金类任务补跑与去重规则；目标三节点 Nacos 所需资源是否落实。当前单节点是已确认现状，三节点是本方案目标部署设计。
- 实施前生成：完整配置迁移快照、完整任务及执行器数据库快照、备份恢复验证、目标应用包与依赖树、网络和认证验证记录。这些是执行工作产物，不是要求用户重复确认现状。

## 三 版本切换带来的变化

本次升级的主要技术变化是 Nacos 客户端通信机制、XXL-JOB 任务调用接口及调度数据库结构变化。下表的当前客户端版本采用用户确认的 master 依赖基线；生产服务端已确认为 Nacos 1.3.2、XXL-JOB 2.2.0。

### 技术变化与改造影响

| 对象 | 升级带来的变化 | 消费分期需要落实的改造及验证 |
| --- | --- | --- |
| Nacos 客户端通信 | 源码依赖从 1.2.1 升到 2.5.4，注册、订阅和配置通信采用 2.x 的 gRPC 长连接机制。[15](https://nacos.io/docs/v2.5/upgrading/200-compatibility/) | 14 个后端更新依赖；除默认 8848 外，放通客户端到服务端的 9848，并调整负载均衡和防火墙。测试冷启动、断线重连、配置刷新、服务发现及网关路由。 |
| Nacos 服务端和数据结构 | 目标为 2.5.4，需使用对应数据库结构；2.5 系列对配置灰度及灰度历史记录做了调整。2.x 客户端不能连接旧 1.x 服务端。[7](https://nacos.io/docs/v2.5/manual/admin/upgrading/)[15](https://nacos.io/docs/v2.5/upgrading/200-compatibility/) | 本方案新建独立集群和新库，迁移配置、namespace ID、权限及所需元数据；旧历史记录另行归档。独立新集群属于本次部署方案，需要运维实施。 |
| XXL 任务调用接口 | 执行器依赖从 2.2.0 升到 2.5.0。旧方式通过方法参数接收任务参数并返回 ReturnT；新入口采用无参方法，通过 XxlJobHelper 获取参数、记录日志和上报结果。[6](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)[16](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java) | 适配 86 个业务入口和 1 个公共入口，修改 thctay-schedule、thctay-mq；保留 handler 名称，核对参数、失败上报、异常、重试和业务结果。 |
| XXL 调度数据库 | 相较官方 2.2.0，目标结构包含 schedule_type、schedule_conf、misfire_strategy 等字段，并调整注册唯一索引及日志索引。[10](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/doc/db/tables_xxl_job.sql)[11](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/doc/db/tables_xxl_job.sql) | 将原 job_cron 等数据迁入目标结构，保留任务 ID、执行器组、参数及子任务关系；明确窗口期间任务的补跑规则，避免漏跑或重复执行。生产版本已确认为 2.2.0，仍须核对实际表结构是否有定制差异。 |
| Spring 与第三方依赖 | XXL 2.5.0 调度中心使用自身的 Boot 2.7.18，并更新 Netty、Gson、SLF4J、Groovy 等依赖；xxl-job-core 的 Spring 依赖为 provided。[3](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/pom.xml)[4](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/pom.xml) | 业务应用 Java 8、Boot 2.2.9、Cloud Hoxton.SR7、Alibaba 2.2.1 本轮暂保留，不代表该组合已通过兼容验证。检查 Maven 最终解析和打包结果，完成网关、Redis/Redisson、日志和任务回归；失败则阻断上线。 |

### 任务参数与失败处理的变化

例如旧任务接收一个订单号，处理失败后返回 `ReturnT.FAIL`。如果仅升级 jar 而保留原调用入口，2.5.0 的方法调用实现会为旧方法的参数位置传入 `null`，且不再使用方法返回值决定任务结果。因此可能出现指定订单参数丢失，或业务失败未按预期上报的问题。[6](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)[16](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)

改造时保留原 `process(String)` 作为内部业务方法，新增 `public void` 无参入口，通过 `XxlJobHelper.getJobParam()` 取参，再将原失败返回映射为 `XxlJobHelper.handleFail()`；异常按原语义继续抛出。具体代码见改造项 5。测试至少覆盖指定订单、空参数、非法参数、业务失败和抛异常五种情形。

### 升级收益与验收边界

- **Nacos 修复与通信能力**：接入 2.x 长连接能力；2.5.4 包含数据库查询结果处理加固、主动健康检查目标地址校验修复，并支持 Java 8。实际吞吐、资源占用及恢复时间以本系统测试为准。[13](https://github.com/alibaba/nacos/releases/tag/2.5.4)[15](https://nacos.io/docs/v2.5/upgrading/200-compatibility/)
- **XXL 调度可靠性**：2.5.0 修复部分调度结果丢失、任务停止边界问题，优化执行器注册及日志索引，并支持自定义通信超时；仍需对消费分期任务执行和资金幂等进行验证。[14](https://github.com/xuxueli/xxl-job/releases/tag/2.5.0)
- **配套安全整改**：账号和权限、随机 Token、HTTPS/TLS 或受控隧道、MFA、口令策略及日志留存需要分别实施和验证，升级不会自动完成。T38、T41 必须按原漏洞名称、扫描对象和复测标准销项，版本号变化不能替代复测。
- **业务验收目标**：业务操作流程、任务 handler、参数含义、调度规则及账务结果保持原有约定；重点核对支付、代扣、清结算、补偿、日切和对账，不因框架变化产生重复业务处理。

### 一次性切换对实施的影响

1. 每个应用只准备一个包含本次全部适配的目标包，14 个后端和纳入同一调用域的关联系统绑定同一发布清单。
2. 同一维护窗口暂停入口流量、迁移任务和清单内 MQ 消费，等待在途处理结束，完成最终数据同步并统一切换两套中间件及应用。
3. Nacos 注册和配置、XXL 触发和回调、关键业务同时验收通过后，统一确认恢复生产；任一关键项失败按改造项 14 整体回退。
4. 回退恢复升级前完整应用包、配置、旧 Nacos 和旧 XXL 中心及调度库。已执行任务按执行对照表核对，应用回退不能撤销已发生的支付或外部通知。

上述独立部署、统一切换、整体回退及配套安全配置属于本次实施安排；新版提供的能力仍须通过实际配置、代码适配和验收才能生效。

## 四 各系统需要修改的位置

下表路径相对于对应仓库根目录。所有 Java 后端都要更新最终 nacos-client，并同步核对 config 与 discovery 的地址、命名空间和凭据；标有任务数量的仓库另做 XXL 入口适配。

| 仓库 | Nacos 启动配置位置 | XXL 改动 |
| --- | --- | --- |
| amp-auth | auth-controller/src/main/resources/config/bootstrap.yml | 无本仓库入口 |
| amp-capital | capital-start/src/main/resources/bootstrap.yml | 26 |
| amp-gateway | src/main/resources/bootstrap.yml | 无本仓库入口 |
| amp-op-gateway | src/main/resources/bootstrap-*.yml<br>以生产实际激活的 profile 为准 | 无本仓库入口 |
| amp-intf-common | intfcommon-start/src/main/resources/bootstrap.yml | 公共 MQ 任务 |
| amp-intf-risk | intfrisk-start/src/main/resources/bootstrap.yml | 无本仓库入口 |
| amp-intf-risk-jt | intfrisk-start/src/main/resources/bootstrap.yml | 无本仓库入口 |
| amp-ops-reporting | amp-ops-reporting-start/src/main/resources/bootstrap.yml | 6 |
| amp-order | amp-order-start/src/main/resources/bootstrap.yml | 8 |
| amp-payment | payment-controller/src/main/resources/bootstrap.yml | 13 |
| amp-risk | risk-start/src/main/resources/bootstrap.yml | 3 |
| amp-risk-backend | src/main/resources/bootstrap.yml | 无本仓库入口 |
| amp-saps | saps-controller/src/main/resources/bootstrap.yml | 30 |
| amp-wolease | amp-wolease-start/src/main/resources/bootstrap.yml | 引用 schedule |

14 个仓库的根 pom.xml 均在依赖改造范围；risk-backend 的父链为 bpm-framework → thctay-dependencies，需要一并发布父工程。amp-admin、amp-unionpay-h5 为前端，amp-doc 为文档，本次不改 Java 依赖。

跨系统联调范围还包括 WorkHub 登记的 intf-paychannel、thctay-contract、thctay-eas、xxt-pay、xxt-pay-gateway。用户已确认本套中间件不与其他业务线共用，因此不再将“其他业务线共用中间件”作为已知范围；这些关联系统仅按实际接口依赖参与联调。合同配置在本套 prod 中存在，其使用情况按第二节待确认项落实。这些系统不计入上面的 14 个代码核查对象。

## 五 具体可执行改造项

### 改造项 1 固定生产版本和任务台账

责任部门：业务研发、运维、DBA、安全

修改位置：生产发布记录、应用包清单、Nacos 配置清单、XXL 任务导出

1. 采用已确认基线：生产依赖与 master 最新代码一致，JDK 截图为 8u361，Nacos 服务端 1.3.2，XXL-JOB 服务端 2.2.0。实施时固定当次 master 提交 SHA，核对 2026年10月19日之后的增量，填写各应用发布版本、负责人及 namespace/group、XXL appname；目标包的 BOOT-INF/lib 检查作为构建验收，不再作为用户补充现状的前置条件。

2. 两套中间件均单节点、MySQL、消费分期专用，直接采用第二节已确认记录。DBA 补充 MySQL 具体版本及实际 DDL。实施前导出 Nacos 全部 namespace ID、dataId、group、类型和内容摘要；导出 XXL 全部任务 ID、handler、Cron、参数、启停、重试、阻塞、路由及子任务关系。按本次 17 条配置、83 条任务及 8 个执行器分组核对变化，切换时以最终冻结快照为准。

3. 安全部门提供 T38、T41 对应的漏洞名称或 CVE、扫描对象、端口及复测标准。给每条生产任务标注可自动补跑、需人工确认或不得重跑。

**完成条件：形成唯一版本台账、配置清单、任务停启清单和漏洞清单；完整迁移快照、实际 DDL 和恢复验证未就绪前，不执行生产迁移或切换。**

### 改造项 2 统一发布版本并升级公共依赖

责任部门：公共组件维护人、各系统研发

修改位置：thctay-public 的 dependencies、schedule、mq；bpm-framework；14 个后端 pom.xml

1. 建立唯一发布版本清单，每个应用准备一个包含本次全部改造的目标包，统一纳入 Nacos 客户端升级及适用的 XXL 执行器、任务适配。记录 Maven 版本、Git SHA、包校验和及配置版本；保留升级前完整基线，不覆盖旧 SNAPSHOT。

2. 在新 thctay-dependencies 的 dependencyManagement 显式管理 com.alibaba.nacos:nacos-client:2.5.4 和 com.xuxueli:xxl-job-core:2.5.0。仅设置同名 property 不作为完成依据。

3. 发布适配后的 thctay-schedule、thctay-mq；更新 capital、payment、ops-reporting 根 POM，risk-schedule、wolease-service 的 schedule 固定版本；更新公共 mq 对 schedule 的引用。

4. 删除 risk-schedule 中 xxl-job-core 的 2.2.0 局部版本；同步更新 bpm-framework 父依赖及 risk-backend 的父版本。其他公共模块保持原版本，避免整套 SNAPSHOT 被动漂移。

**完成条件：所有目标包的 nacos-client 均为 2.5.4；包含 XXL 的应用仅有 xxl-job-core 2.5.0，并加载对应新版公共组件；包与配置归入同一发布版本清单。**

## 六 框架与公共组件改造

### 改造项 3 固定 Spring 边界并处理依赖冲突

责任部门：公共组件维护人、网关和风控研发

修改位置：thctay-dependencies/pom.xml；amp-risk/pom.xml；两个 gateway 的 pom.xml

1. 本轮业务应用暂保留 Boot 2.2.9、Cloud Hoxton.SR7、Alibaba 2.2.1。XXL 调度中心使用其 2.5.0 发行版自己的 Boot 2.7.18；不因此替换所有业务 Spring jar。[3](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/pom.xml)[4](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/pom.xml)

2. 检查目标包中 Netty、Gson、SLF4J、Logback、Groovy 的实际解析结果。以 XXL 2.5.0 声明的 Netty 4.1.116.Final、Gson 2.11.0 作为集成起点；通过当前 BOM 的显式依赖管理落实，不让旧 Boot 静默回落。网关、Redis/Redisson 一并回归。

3. 日志框架暂保持现有 Boot 支持的同代组合；不直接把 SLF4J 换成 2.x 配 Logback 1.2。核实并清除 risk 的遗留版本覆盖，将最终组合写入版本清单。所有新旧依赖仍需扫描，不能把上游声明版本当作安全认证。

**完成条件：编译、启动、日志及业务回归均通过；若出现无法解决的兼容错误或框架自身阻断漏洞，停止本次上线，另排 Boot / Cloud / Alibaba 整体升级。[5](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)**

### 改造项 4 改造公共调度组件和消息重投任务

责任部门：公共组件研发

修改位置：thctay-schedule 下 BaseJobHandler.java、XxlJobConfig.java；thctay-mq 下 MessageSenderRetrySchedule.java

1. BaseJobHandler 按 2.5.0 的 IJobHandler 改为 void execute()：从 XxlJobHelper.getJobParam() 取参，调用原 process(String)，将非成功结果映射为 handleFail；异常保留堆栈并继续抛出。

2. XxlJobLogger 改为 XxlJobHelper.log。XxlJobConfig 增加 Token 非空、appname 非空、管理地址非空的启动检查；生产环境缺参直接启动失败。保留已有端口和实例地址绑定逻辑。

3. MessageSenderRetrySchedule 使用与业务任务相同的无参入口方式。消息重投既要验证任务成功回调，也要验证消息幂等和失败重试，不把这一个公共入口遗漏在 86 个业务入口之外。

**完成条件：公共包在 Java 8 下构建成功；正确 Token 可通信、空或错误 Token 被拒绝；消息重投只产生预期的一次业务效果。**

## 七 业务任务代码如何改

### 改造项 5 为 86 个业务任务适配新的调用接口

责任部门：capital、saps、payment、order、ops-reporting、risk 研发

修改位置：第二节所列 6 个任务模块，完整 handler 清单见附录

1. 统一将 @XxlJob 放在 public void 无参方法上，handler 字符串原样保留。可以保留原 ReturnT&lt;String&gt; process(String) 作为内部业务方法，由新入口取参、调用并映射结果，避免同时重写业务逻辑。

2. order 的 ApplicationPreliminaryAuthSubmitJob、ApplicationRefactoringJob、PreliminaryRequestRecordJob、SyncApplyBankCardDataJob 及 ops-reporting 的 DeviceFinanceRecordFixedJob 共 5 个类直接继承 IJobHandler；改为普通 Spring Bean 并增加新入口，删除旧接口的 @Override。

3. order 的 AbstractXXLJob 与 3 个子类可以继续保留内部 process(String) 契约，把 @XxlJob 移到新入口。已有事务、代理和异常处理在服务层验证，不能因增加内部调用而绕过原有事务边界。

**完成条件：全部 86+1 个入口为无参 void；原 handler 无改名、无重复注册；指定参数、失败返回和抛异常三类结果均与预期一致。[6](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)**

### 以支付签章任务为例的适配方式

修改 payment-schedule/src/main/java/com/amp/payment/schedule/AgreementSignFileSchedule.java。原 process(String) 去掉 @XxlJob，保留其业务逻辑；新增如下入口。XxlJobHelper 需要新增 import。

```java
@XxlJob("agreementSignFileSchedule")
public void runJob() throws Exception {
    String args = XxlJobHelper.getJobParam();
    ReturnT<String> result = process(args);
    if (result == null || result.getCode() != ReturnT.SUCCESS_CODE) {
        XxlJobHelper.handleFail(result == null
            ? "Job returned null" : result.getMsg());
        return;
    }
    XxlJobHelper.handleSuccess();
}
```

必须验证：传入测试申请单号时只处理该单；原有空参数批处理语义保持；错误 JSON 可被标记为失败。2.5.0 的反射调用为旧方法的各参数位置传入 null，并忽略返回值，仅改依赖可能造成参数丢失或失败被记为成功。[6](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)

## 八 Nacos 服务端和客户端改造

### 改造项 6 建设独立 Nacos 2.5.4 集群并迁移配置

责任部门：运维、DBA、业务研发

修改位置：新集群 conf/application.properties、cluster.conf、新数据库、配置导出文件

1. 按三节点集群准备 2.5.4、独立数据源及运维入口，用目标版本 mysql-schema.sql 初始化空的新库。新旧集群不共享数据库；本方案不在旧 1.x 节点上直接替换为 2.5.4。[7](https://nacos.io/docs/v2.5/manual/admin/upgrading/)[8](https://nacos.io/docs/v2.5/manual/admin/deployment/deployment-overview/)

2. 导出并在新集群重建原 namespace ID，导入全部 dataId/group/type/content；确认共享配置、扩展配置和 profile 对应项。自定义角色、权限、服务元数据及持久化实例分别迁移，不能仅依赖配置 ZIP。

3. 启用 nacos.core.auth.enabled=true；设置 nacos.core.auth.plugin.nacos.token.secret.key、nacos.core.auth.server.identity.key/value 的自定义安全值，集群节点一致。为应用创建受限账号，设置管理员强口令；不沿用默认密钥或开启 UserAgent 白名单。[9](https://nacos.io/docs/v2.5/manual/admin/auth/)

4. 对配置逐项比较内容摘要和命名空间归属；业务服务切换后重新注册临时实例。存量配置历史单独归档可检索，不承诺配置导入会自动迁移历史记录。

**完成条件：3 节点健康；原配置清单逐项一致；匿名读取或修改被拒绝；停 1 个节点后应用仍能读配置和发现服务。**

| 通信方向 | 默认端口 | 具体动作 |
| --- | --- | --- |
| 业务客户端 → Nacos | 8848、9848 | 分别放通 HTTP 主端口与 gRPC TCP 通道；入口映射保持 +1000 偏移 |
| Nacos 节点之间 | 8848、9849、7848 | 按集群通信放通；9849、7848 不向业务网段及公网开放 |
| 运维终端 → 管理入口 | HTTPS 入口 | 限制到指定管理网段；服务端实际端口以部署配置为准 |

### 改造项 7 修改 14 个后端的 Nacos 启动配置

责任部门：各系统研发、运维

修改位置：第四节 bootstrap 路径及生产部署参数

1. 在统一目标包中落实 nacos-client 2.5.4。将 spring.cloud.nacos.config 与 discovery 下的 server-addr、username、password 分别注入；密码通过部署密钥配置，不写入 Git 或文档。

2. namespace 使用原 ID；group、file-extension、prefix、shared-configs / extension-configs 及 refresh 语义保持。应用启动前必须能得到地址和凭据，不能仅把这些值放在尚未连接成功的 Nacos 内。

3. 在测试环境验证旧 Alibaba Starter 与新 SDK 的启动、配置刷新、实例上下线、Feign/Ribbon 调用和网关路由。清理测试客户端快照后再做冷启动，避免本地缓存掩盖连接失败。

**完成条件：14 个后端逐一提交统一目标包与联合测试记录；服务端为 2.5.4 后再接入 2.x 客户端，客户端 2.5.4 不指向旧 1.x 集群。**

生产旧版本已确认为 1.3.2，本方案使用上述独立新集群路线。需要原地滚动升级时，必须另补官方 2.0/2.1 过渡路线和演练，不能把本方案改成直接跨版本替包。[7](https://nacos.io/docs/v2.5/manual/admin/upgrading/)

## 九 XXL 调度中心和数据库改造

### 改造项 8 迁移任务数据并保留任务身份

责任部门：DBA、调度平台研发

修改位置：XXL 新数据库；版本化的迁移 SQL、校验 SQL、任务停启清单

1. 从经核验的生产库备份恢复出独立新库；在新库上执行增量迁移。保留任务 ID、group ID、handler、Cron、参数、子任务 ID、重试及阻塞策略。禁止把包含初始化账号和示例任务的全量建表脚本直接执行到旧库或已恢复的新库。

2. 新库全部任务保持停止。保留原启停状态到迁移清单，清理新库中的旧自动注册地址；手动地址重新登记为目标执行器地址。新调度中心不得在正式切换前触发旧执行器。

3. 以下差异按官方 2.2.0 → 2.5.0 schema 准备；DBA 先对实际 SHOW CREATE TABLE 核对，再形成可重复演练的脚本。生产版本已确认为 2.2.0；实际 DDL 如有定制差异，应纳入迁移脚本。[10](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/doc/db/tables_xxl_job.sql)[11](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/doc/db/tables_xxl_job.sql)

**完成条件：新旧任务记录逐项校验，ID 及子任务关系保持；新库没有处于运行状态的迁移任务；恢复、迁移和数据校验演练通过。**

| 表 | 必须落实的迁移 |
| --- | --- |
| xxl_job_info | 增加 schedule_type、schedule_conf、misfire_strategy；原定时任务将 job_cron 复制到 schedule_conf 并设置 CRON；原 job_cron 核对后归档，并删除或改为可空，避免新建任务插入失败；停机期间补跑按清单执行。 |
| xxl_job_info | glue_remark 新结构为非空，先处理旧 NULL；保存并核对任务参数、超时、失败重试、阻塞策略和子任务关系。 |
| xxl_job_group | address_list 调整为 text；增加 update_time；区分自动和手动注册清理旧地址。 |
| xxl_job_registry | 检查重复键后按目标结构建立 registry_group/key/value 唯一索引；新库注册地址重新生成。 |
| xxl_job_log_report 与 log | 补充 report.update_time 和目标版本日志索引；按数据量评估索引工期，不在生产临时尝试。 |

### 改造项 9 部署 2.5.0 调度中心并切换执行器

责任部门：运维、调度平台研发、各业务研发

修改位置：xxl-job-admin 配置；XxlJobConfig 读取的部署参数

1. 先验证新数据库与发行版自带 Connector/J 9.1.0 的兼容性、时区及字符集。新中心连接新库，配置环境专用随机 xxl.job.accessToken，替换示例 default_token。目标包执行器使用完全相同 Token 和新 admin.addresses，不同时填写新旧中心地址。

2. 保留执行器 appname；核对 executor.address / ip / port 的可达性和端口占用。调度中心到执行器、执行器到中心的注册和回调两个方向都测试。全部执行器随 Nacos 和业务应用在同一维护窗口切换。

3. 保持迁移任务停止，先运行经批准的测试任务验证注册、触发、回调、日志、失败告警及子任务；恢复任务时严格采用改造项 1 中的停启清单。

**完成条件：新中心只发现目标执行器；非法 Token 被拒绝；任务调用、回调、日志读取全部成功；未引入新旧中心同时调度同一任务的状态。**

## 十 安全配置和审计改造

### 改造项 10 补齐认证权限和传输保护

责任部门：安全、运维、平台研发

修改位置：Nacos 与 XXL 管理入口、应用账号、网络策略、传输通道

1. 管理入口接入公司统一身份认证或受控管理入口，落实个人账号、MFA、口令复杂度与到期、失败锁定和会话超时；阻断绕过入口直达管理功能。建议按本次测评口径执行 5 次失败锁定 30 分钟、空闲 30 分钟退出，并由安全部门确认。

2. Nacos 应用账号按 namespace 和资源授权；XXL 普通运维账号按执行器授权，管理员单独保管。严格限制可编辑 GLUE 或执行脚本的账号；缺少所需审计员等角色时，由统一管理平台补足并提交操作证据。

3. 浏览器管理入口启用 HTTPS TLS 1.2+；业务 SDK 的 HTTP/gRPC、调度中心与执行器双向链路、数据库连接逐条实施 TLS 或受控 IPsec 隧道。XXL Token 和 Nacos 鉴权只解决身份校验，不替代传输加密。

4. 运维提交每条链路的源、目的、协议、证书或隧道配置及验证结果；若 TLS 在代理终止，后段跨主机链路也要保护。存在 SM 算法要求时，由安全部门确认密码产品和实施口径后验收。

**完成条件：无共享管理员或默认弱密钥；错误身份和越权访问被拒绝；所有测评涉及的跨主机敏感传输链路均有加密证据。**

### 改造项 11 将日志保存和审计落实到配置

责任部门：运维、DBA、安全、应用研发

修改位置：调度中心日志、执行器日志、Nacos 操作日志、集中日志平台

1. 建议将 admin 的 xxl.job.logretentiondays 和执行器的 xxl.job.executor.logretentiondays 统一配置为 200 天，并完成磁盘/数据库容量测算；同步调整公共 XxlJobConfig 的 30 天默认值。也可采用短期本地加集中归档，但必须证明完整记录覆盖至少 6 个自然月。[12](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-admin/src/main/resources/application.properties)

2. 集中采集任务执行日志、调度记录、登录和管理操作记录；将 Nacos 配置变更及管理员操作审计纳入采集。任务运行日志不能代替管理员审计日志。

3. 定义日志只读审计权限、删除审批、备份及恢复验证；选一条历史记录从集中平台检索并恢复。对任务参数和配置内容中的凭据及个人信息脱敏。

**完成条件：能按用户、任务或配置变更追溯；保存周期覆盖至少 6 个月；清理策略、备份和恢复均有证据。**

| 原问题 | 本方案对应项 | 销项依据 |
| --- | --- | --- |
| T38、T41 漏洞 | 1、2、4、5、6、8、9、12 | 同一漏洞检查项复测通过；仅显示新版本号不够 |
| T11、T14、T18、T21、T25 | 6、9、10 | 口令、锁定、超时、MFA、账号和最小权限实测 |
| T16、T50、T56 | 7、9、10 | 浏览器及 SDK / RPC 双向传输保护实测 |
| T34、T35 | 11 | 集中审计、保存周期、检索和恢复证据 |
| T47、T52、T58 等 | 独立整改任务 | 可信验证、存储完整性和加密不因本次升级自动完成 |

## 十一 构建和测试验收

### 改造项 12 按模块构建并核验发布包

责任部门：各业务研发、测试

修改位置：各仓库本次升级分支、统一版本清单和 CI 流水线

1. 在隔离测试配置下先生成 effective-pom 和依赖树，再运行对应 Maven 模块的 test / package。涉及数据库、MQ 或支付接口的集成测试使用获准的测试环境，外部资金渠道使用桩或测试账户。

2. 静态检查统一目标包中全部 @XxlJob 为无参 void；旧 XxlJobLogger、旧 IJobHandler 方法覆盖已清理；依赖树和最终 BOOT-INF/lib 中无遗留 nacos-client 1.x、xxl-job-core 2.2.0。

3. 版本证据覆盖 14 个后端；MQ 公共任务的实际注册范围按运行配置核对。将构建 SHA、包校验和、依赖树、扫描报告和测试报告绑定到同一版本清单。

**完成条件：每个待发布应用均有可定位的构建、依赖和验收记录；下表全部适用用例通过，失败用例不得以“服务能启动”替代。**

供执行人员使用的命令示例，当前文档编制过程未执行升级构建：

```bash
mvn help:effective-pom -Doutput=target/effective-pom.xml
mvn dependency:tree -Dverbose -DoutputFile=target/dependency-tree.txt
mvn -pl capital-start -am verify
mvn -pl payment-controller -am verify
mvn -pl saps-controller -am verify
mvn -pl amp-order-start -am verify
mvn -pl amp-ops-reporting-start -am verify
mvn -pl risk-start -am verify
```

以上模块命令分别在各自仓库根目录执行；其余后端按第四节启动模块构建，单模块工程在根目录 verify。不得跳过本次适配新增的测试。

| 测试项 | 执行方式和通过标准 |
| --- | --- |
| Nacos 冷启动和配置 | 清测试缓存后启动；配置值正确；修改测试配置可刷新；错误凭据或越权读取被拒绝 |
| 发现和网络故障 | 节点下线、重启、gRPC 连接中断后恢复；Feign、网关调用成功，无持续注册失败 |
| 任务参数 | 每个在用 handler 覆盖空参、有效参数、非法参数；指定单号只影响该单 |
| 失败反馈 | 人为制造业务失败返回和异常；中心必须记录失败并触发既定告警 |
| 支付和账务幂等 | 同单重复触发、失败重试、回调延迟或重复；无重复扣款、放款、账单和通知 |
| 调度策略 | Cron、路由、分片、阻塞、超时、子任务按生产清单逐项核对；未使用能力标不适用 |
| Spring 与三方依赖 | 两个网关、风控、支付、清结算均启动并执行业务；无 LinkageError、日志绑定错误 |
| 安全和日志 | 按原漏洞、接口、账号及权限复测；日志采集、保存、恢复有效 |

## 十二 一次性切换与整体回退

### 改造项 13 在同一维护窗口完成生产切换

责任部门：发布负责人、运维、DBA、业务研发、测试

修改位置：统一发布窗口、应用包及配置清单、任务停启清单

1. 窗口前完成新集群、新中心、目标包及整体回退演练；同一调用域全部系统就绪后进入窗口。冻结 Nacos 配置和 XXL 任务定义，入口停接新请求，停用全部迁移任务，按清单暂停 MQ 消费，等待在途请求和任务结束。

2. 完成 Nacos 最终配置导出、XXL 库备份及摘要校验，将最终数据同步至新集群和新调度库，执行迁移并复核。新旧中心的迁移任务均保持停用。统一发布 14 个后端及纳入窗口的关联应用，配置指向新 Nacos 和新 XXL 中心。

3. 流量、MQ 消费和生产任务保持暂停，联合验证服务注册、配置加载刷新、关键交易及执行器注册、触发、回调。全部通过后由发布负责人统一确认，按依赖顺序恢复流量、MQ 和任务；任一关键项失败即执行整体回退。

4. 旧中心的迁移任务持续停用；新旧中心不得重复调度。窗口内完成恢复验证，切换后建议至少观察 24 小时且覆盖完整关键业务周期，包括日切和对账。错过窗口的任务按登记规则补跑，资金任务先核对账务。

**完成条件：窗口内联合验收通过并恢复业务；观察期确认注册数、关键交易、补偿、日切和对账正常，无重复调度或处理，由业务负责人确认完成。**

### 改造项 14 演练并执行整体回退

责任部门：发布负责人、运维、DBA、研发、业务值守

修改位置：升级前完整版本清单、旧配置、旧集群、旧调度库、任务执行对照表

1. 上线前演练完整回退，测量恢复时间并记录最晚回退时刻。服务注册缺失、配置错误、关键交易失败、任务误处理或重复处理触发停止恢复业务并整体回退；涉及资金重复时立即停用相关任务并核对账务。

2. 回退先关闭新侧流量，暂停 MQ 消费和新中心任务，等待在途处理结束。冻结新侧配置修改，记录切换后已执行的任务及业务结果，核对需要保留的配置差异。

3. 按升级前清单恢复全部旧应用包及 Nacos、XXL 配置，接回旧集群和旧调度中心、旧调度库。验证旧服务注册、配置和执行器回调后恢复业务；按执行对照表恢复任务，已执行任务不得自动重放。不能仅把新客户端地址改回旧中间件。

4. 旧中间件、数据库和版本包保留到验收及回退期结束。回退应用或中间件不能撤销已经发生的支付、账单或外部通知，业务数据按现有核对和补偿流程处理。

**完成条件：至少完成一次 Nacos、XXL 及全部应用的联合回退演练；恢复时限满足窗口，验证旧服务与旧执行器恢复，完成在途任务和已执行任务核对。**

其他业务线复用方式：替换业务线编码、服务列表、当前依赖、任务入口和共享资源清单；保留 14 个改造项编号逐项认领。不得将本案例的 14 个服务、86 个任务或 Spring 版本直接套用到其他业务线。

## 附录一 任务入口改造清单

以下为 origin/master 去除注释后的有效 @XxlJob 入口。每条均按改造项 5 增加无参适配入口，handler 名称原样保留。源码文件名用于在对应模块内定位，统计不包含生产数据库任务实例。

### amp-capital  共 26 个入口

模块：capital-schedule；仓库提交：742871aefc30

| 源码类 | handler 名称 |
| --- | --- |
| AssetTransferContractSyncSchedule | assetTransferContractSyncJob |
| FunderTransferQuerySchedule | funderTransferQueryJob |
| LoanAttachmentSignSchedule | loanAttachmentSignJob |
| LoanBalanceStatisticsSchedule | loanBalanceStatisticsJob |
| LoanInitPaymentAgainSchedule | loanInitPaymentAgainJob |
| LoanInitPaymentAgainSchedule | loanInProgressJob |
| LoanOrderNotifyPayResultSchedule | loanOrderNotifyPayResultJob |
| LoanOrderSignedAttachmentSyncSchedule | loanOrderSignedAttachmentSyncSchedule |
| LoanPaymentQueryStatusSchedule | loanPaymentQueryStatusJob |
| LoanPaymentQueryStatusSchedule | loanExceptionNoticeJob |
| LoanPaymentSchedule | loanPaymentJob |
| LoanRiskSchedule | loanRiskJob |
| LoanStatisticsSchedule | loanStatisticsJob |
| OfflinePaymentAggregateSchedule | offlinePaymentAggregateJob |
| OfflinePaymentAggregateSchedule | offlinePaymentPushJob |
| OfflinePaymentAggregateSchedule | offlinePaymentSuccessCountCheckJob |
| RefactoringAssetBillDetailSchedule | refactoringAssetBillDetailJob |
| RefactoringLoanSchedule | refactoringLoanJob |
| RefactoringLoanSchedule | refactoringLoanSyncJob |
| RefactoringLoanSchedule | queryRefactoringLoanStatusJob |
| RefactoringLoanSchedule | queryRefactoringLoanStatusExceptionJob |
| RefactoringLogisticsSchedule | refactoringLogisticsUpdateJob |
| RefactoringPushAssetTransferSchedule | refactoringPushAssetTransferJob |
| RepaymentPlanLockSchedule | repaymentPlanLockJob |
| RepaymentPlanPushSchedule | pushRepaymentPlanSchedule |
| SupplementContractSyncSchedule | supplementContractSyncJob |

### amp-saps  共 30 个入口

模块：saps-schedule；仓库提交：47c88d6eb1fb

| 源码类 | handler 名称 |
| --- | --- |
| ActiveSettlementNotifyCompensateSchedule | activeSettlementResultNotifySchedule |
| AgreementPaySchedule | agreementPaySchedule |
| AgreementPayWatchSchedule | agreementPayWatchSchedule |
| CustomerOrderSchedule | queryOrderAndProcessSchedule |
| FunderBillSyncSchedule | funderBillSyncSchedule |
| OrderReconciliationSchedule | orderReconciliationSchedule |
| ProjectLoanAnalyseSchedule | projectLoanAnalyseSchedule |
| RepaymentPlanOverdueSchedule | repaymentPlanOverdueSchedule |
| BillAutoSettlementSchedule | billAutoSettlementSchedule |
| PaymentQuerySchedule | paymentQuerySchedule |
| AggPayStatementGenerateSchedule | aggPayStatementGenerateSchedule |
| BaoliServiceFeeGenerateSchedule | baoliServiceFeeGenerateSchedule |
| BuyBackStatementGenerateSchedule | buyBackStatementGenerateSchedule |
| ConsulationFeeStatementGenerateSchedule | consulationFeeStatementGenerateSchedule |
| DsdfPaySchedule | dsdfPaySchedule |
| DsdfRemitQuerySchedule | dsdfRemitQuerySchedule |
| FunderFeeGenerateSchedule | statementGenerateSchedule |
| FunderOverdueFeeStatementGenerateSchedule | funderOverdueFeeStatementGenerateSchedule |
| FunderStatementNoticeSchedule | funderStatementNoticeSchedule |
| FunderStatementSchedule | funderStatementSchedule |
| MandatoryBuyBackAnalyzeSchedule | mandatoryBuyBackAnalyzeSchedule |
| OfflineStatementGenerateSchedule | offlineStatementGenerateSchedule |
| PaymentFeeStatementGenerateSchedule | paymentFeeStatementGenerateSchedule |
| PlateformStatementGenerateSchedule | plateformStatementGenerateSchedule |
| RefundStatementGenerateSchedule | refundStatementGenerateSchedule |
| RefundStatementOverdueSchedule | refundStatementOverdueSchedule |
| RefundTicketSchedule | refundTicketSchedule |
| StatementNoticeSchedule | statementNoticeSchedule |
| StatementOverdueSchedule | statementOverdueSchedule |
| TradeInStatementGenerateSchedule | tradeInStatementGenerateSchedule |

### amp-payment  共 13 个入口

模块：payment-schedule；仓库提交：0c83df979d2d

| 源码类 | handler 名称 |
| --- | --- |
| AgreementSignFileSchedule | agreementSignFileSchedule |
| ChannelWarningSchedule | channelWarningSchedule |
| DownloadDigitalBankSlipSchedule | downloadDigitalBankSlipSchedule |
| OrderBatchSchedule | batchStartAction |
| PayCheckSchedule | payCheckSchedule |
| PayCheckSchedule | payCheckMergeSchedule |
| PayCheckSchedule | checkSummaryNotifySchedule |
| PayCheckSchedule | payPushCheck |
| PayResultNotifyCompensateSchedule | channelQuerySchedule |
| PayResultNotifyCompensateSchedule | payResultNotifySchedule |
| PushStatement2SapsSchedule | pushStatement2SapsSchedule |
| RemitNotifySchedule | remitNotifySchedule |
| RemitStatusSchedule | remitStatusSchedule |

### amp-order  共 8 个入口

模块：amp-order-service；仓库提交：f696b160af80

| 源码类 | handler 名称 |
| --- | --- |
| ApplicationAutoSubmitAuditJob | ApplicationAutoSubmitAuditJob |
| ApplicationPreliminaryAuthSubmitJob | ApplicationPreliminaryAuthSubmitJob |
| ApplicationRefactoringJob | ApplicationRefactoringJob |
| AssetHandlerApplicationQueryAuditResultJob | AssetHandlerApplicationQueryAuditResultJob |
| MonitorJob | MonitorJob |
| PreliminaryRequestRecordJob | PreliminaryRequestRecordJob |
| SupplementAssetHandlerRetryJob | supplementAssetHandlerRetryJob |
| SyncApplyBankCardDataJob | SyncApplyBankCardDataJob |

### amp-ops-reporting  共 6 个入口

模块：amp-ops-reporting-service；仓库提交：a6a7c8d9b835

| 源码类 | handler 名称 |
| --- | --- |
| ConsumerFinanceRecordFixedJob | FinanceReportJob |
| CustomerSupportWeekReportJob | CustomerSupportReportJob |
| DeviceFinanceRecordFixedJob | DeviceFinanceRecordFixedJob |
| ETCReportJob | ETCReportJob |
| ETCReportWeekRefreshJob | etcReportWeekRefreshJob |
| LogisticsReportJob | LogisticsReportJob |

### amp-risk  共 3 个入口

模块：risk-schedule；仓库提交：545bdaee6621

| 源码类 | handler 名称 |
| --- | --- |
| BatchBpmEngineJob | batchStrategy |
| BatchBpmEngineJob | singleInvokeEngine |
| BatchBpmEngineJob | batchEngineNotify |

### thctay-public  共 1 个入口

模块：thctay-mq；仓库提交：f0bfbdcea977

| 源码类 | handler 名称 |
| --- | --- |
| MessageSenderRetrySchedule | messageSenderRetrySchedule |

## 附录二 核查基线和技术依据

业务仓库根目录：/Users/aslight/IDEAWorkspace/asset-plateform。下表均为 2026年10月19日核查的 origin/master 提交；公共组件和 BPM 为本地远端引用基线。用户已确认生产依赖与 master 最新代码一致；本表保留原核查提交作为证据，实施时按改造项 1 固定发布 SHA 并核对后续增量。

| 仓库 | 提交 | 仓库 | 提交 |
| --- | --- | --- | --- |
| amp-admin | b69dc1e6544f | amp-auth | 72721bb79528 |
| amp-capital | 742871aefc30 | amp-doc | 4a148a418301 |
| amp-gateway | 218a0a5643b5 | amp-intf-common | ec1e4df4867b |
| amp-intf-risk | b54773cd9e25 | amp-intf-risk-jt | f1a87f39cb03 |
| amp-op-gateway | cff6a6fd09d9 | amp-ops-reporting | a6a7c8d9b835 |
| amp-order | f696b160af80 | amp-payment | 0c83df979d2d |
| amp-risk | 545bdaee6621 | amp-risk-backend | 74f1df26d418 |
| amp-saps | 47c88d6eb1fb | amp-unionpay-h5 | 34bb33b997ec |
| amp-wolease | 13c029e58e4d | thctay-public | f0bfbdcea977 |
| bpm-framework | 43e5efa9fbde |  |  |

公共源码位置：/Users/aslight/IDEAWorkspace/thctay-public；BPM 父工程位置：/Users/aslight/IDEAWorkspace/xxt-plateform/bpm-framework。源文件路径和本方案所列提交共同构成本次分析依据。

- [1. Spring Cloud Alibaba 2.2.1 BOM](https://raw.githubusercontent.com/alibaba/spring-cloud-alibaba/2.2.1.RELEASE/spring-cloud-alibaba-dependencies/pom.xml)

- [2. Spring Boot 2.2.9 BOM](https://raw.githubusercontent.com/spring-projects/spring-boot/v2.2.9.RELEASE/spring-boot-project/spring-boot-dependencies/pom.xml)

- [3. XXL-JOB 2.5.0 版本依赖](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/pom.xml)

- [4. xxl-job-core 2.5.0 依赖与 provided Spring](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/pom.xml)

- [5. Spring Boot 2.7 社区维护结束说明](https://spring.io/blog/2023/11/23/spring-boot-2-7-18-available-now/)

- [6. XXL-JOB 2.5.0 MethodJobHandler](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)

- [7. Nacos 2.5 升级手册](https://nacos.io/docs/v2.5/manual/admin/upgrading/)

- [8. Nacos 部署与端口说明](https://nacos.io/docs/v2.5/manual/admin/deployment/deployment-overview/)

- [9. Nacos 权限校验说明](https://nacos.io/docs/v2.5/manual/admin/auth/)

- [10. XXL-JOB 2.2.0 数据库结构](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/doc/db/tables_xxl_job.sql)

- [11. XXL-JOB 2.5.0 数据库结构](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/doc/db/tables_xxl_job.sql)

- [12. XXL-JOB 2.5.0 管理中心配置](https://raw.githubusercontent.com/xuxueli/xxl-job/2.5.0/xxl-job-admin/src/main/resources/application.properties)

- [13. Nacos 2.5.4 发布记录](https://github.com/alibaba/nacos/releases/tag/2.5.4)

- [14. XXL-JOB 2.5.0 发布记录](https://github.com/xuxueli/xxl-job/releases/tag/2.5.0)

- [15. Nacos 2.x 客户端通信与兼容性说明](https://nacos.io/docs/v2.5/upgrading/200-compatibility/)

- [16. XXL-JOB 2.2.0 MethodJobHandler](https://raw.githubusercontent.com/xuxueli/xxl-job/2.2.0/xxl-job-core/src/main/java/com/xxl/job/core/handler/impl/MethodJobHandler.java)

框架维护说明：Boot 2.7.18 也已结束社区维护；因此即使后续升到该版本，也不能直接作为长期安全整改终点。Java 8 的保留期限、维护支持和后续框架升级需另列生命周期计划。本次保留旧业务框架只是一项有测试和安全门槛的实施选择。

## 修订记录

| 版本 | 日期 | 变更内容 | 历史文件 |
| --- | --- | --- | --- |
| v2.3 | 2026-10-22 | 补充用户确认的生产版本、JDK、单节点 MySQL 和独占范围；增加 Safari 核查的配置、服务、执行器及任务数量，明确剩余信息与实施产物。 | 当前文档 |
| v2.2 | 2026-10-20 | 改为 Markdown 主文档；新增版本切换变化、任务参数和失败处理影响、升级收益及实施影响，保留一次性切换安排。 | [v2.2 Markdown](历史版本/v2.2-20261022修订前/消费分期Nacos和XXLJOB升级实施方案_20261019.md) |
| v2.1 | 2026-10-19 | 调整为同一维护窗口统一发布、联合验收和整体回退。 | [v2.1 Word](历史版本/v2.1-20261019/消费分期Nacos和XXLJOB升级实施方案_20261019.docx) |
| v2.0 | 2026-10-19 | 整理消费分期系统现状、14 项改造、任务清单及技术依据。 | [v2.0 Word](历史版本/v2.0-20261019/消费分期Nacos和XXLJOB升级实施方案_20261019.docx) |
