# WorkHub 需求分析与排查笔记

## 公司项目

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


## 2026-10-30 物流平台叉车智能柜中间件升级方案整理

- 根据用户两份消费分期参考文件，核对 WorkHub 业务线映射与三个 GitLab 组的远程 master，整理[三条业务线升级方案](requirements/业务线/BL000006-物流平台/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/README.md)，需求编码 REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba。
- 源码入口数：物流 256（共享合同/业财 21 单列）、叉车 71、智能柜 67，另两套公共 MQ 各 1；不等于线上启用任务数。
- 特别处理 lease MQ 参数、手工任务返回值、独立 BOM、中间父 POM 与共享注册域；当前 thctay public 远程升级源码日志默认 90 天，已注明与参考清单 60 天的差异。
- 本轮只整理文档和静态证据，未安装依赖、修改业务源码/配置、构建、触发任务或部署；实际环境和发布计划见各方案待落实项。
