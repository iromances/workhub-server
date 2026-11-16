# xxt-pay 与 intf-paychannel 代码审查及生产预警归类

> 整理日期：2026-10-19｜版本：v1.0｜状态：审查与分析结果，修复建议尚未实施。
>
> 审查对象为两个仓库在本次核查时的远程 `master`。预警范围为生产环境、共享支付系统、固定最近 7 天窗口。本文区分代码缺陷、页面观测与待核实事项，不将代码风险直接认定为已发生的生产事故。

## 1. 主要结论

确认 3 项代码问题，其中 2 项 P1 涉及交易状态误判，1 项 P2 涉及正常零流水被记为系统错误。两个 P1 的关键控制流已通过隔离执行复现；尚未核实生产部署版本及受影响订单。

| 编号 | 优先级 | 问题 | 证据与当前结论 |
| --- | --- | --- | --- |
| R1 | P1 | 代付、内转的系统异常可能被判为交易失败终态 | `RuntimeException` 统一转为 `FAILED`；缺失状态、空返回保护分支等已复现，终态保护会阻止后续成功结果修正 |
| R2 | P1 | 扫码超时关单将部分未知查单结果转换为失败 | `GATEWAY_NULL`、`GATEWAY_DATA_NULL` 被转译为 `PROCESSING` 后仍触发过期关单，已复现 |
| R3 | P2 | 交行账户明细正常零记录被返回为系统错误 | 静态代码确认；页面有 55 条相同错误文案，但尚未核实其中正常零记录的数量 |

预警页面在固定筛选条件下共保留 **489 条 xxt-pay ERROR 事件**，归为 7 类、22 种文案：账户流水相关 235 条，签约相关 236 条，通用请求异常日志 18 条。该数量是页面事件数，不能解释为失败交易数、客户人数或完整的 7 天原始日志量。

`intf-paychannel` 的代码审查已完成；本次页面服务筛选未取得该服务的独立预警数据，因此数量标为**待核实**，不能记为 0。

## 2. 范围与版本

### 2.1 用户确认的范围

| 项目 | 本次口径 |
| --- | --- |
| 代码审查 | 远程 `master` 现状，结合预警重点审查；不是仅审查最近一次提交或某个分支差异 |
| 系统范围 | `xxt-pay`、`intf-paychannel`，跨业务线共享支付系统 |
| 环境 | 生产 |
| 预警时间窗 | 2026-10-12 10:23:21 至 2026-10-19 10:23:21，北京时间 |
| 重点链路 | 代付、内转、支付结果转换、查单与关单、账户流水查询、签约错误及日志链路 |
| 实际执行 | 只读代码审查、预警页面统计、隔离控制流验证 |

未执行业务代码修改、生产数据写入、真实支付调用、应用启动、部署、告警删除或告警规则调整。本次审查聚焦上述链路，不构成全仓库所有功能的穷尽审计。

### 2.2 固定代码基线

| 仓库 | 本地真实工作区 | 核查时的远程 master 提交 | 提交时间（北京时间） |
| --- | --- | --- | --- |
| xxt-pay | [xxt-pay](/Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay) | `53a609eb394de5ca8003b133082d3776262aba6c` | 2026-10-13 13:58:10 |
| intf-paychannel | [intf-paychannel](/Users/aslight/IDEAWorkspace/xxt-pay/intf-paychannel) | `05dadd77979372898d83848f9a18b44ee61ef17f` | 2026-10-13 14:10:37 |

已核实远程仓库身份并获取 `origin/master`；审查时两个工作区的 HEAD 代码与对应 `origin/master` 一致。原有用户文件未改动。

日志切面同时参考公共依赖 [lease-public](/Users/aslight/IDEAWorkspace/lease-saas/lease-public)，本地 HEAD 为 `3b92f6dc834a04ac710513eb801fd4be1025eb92`。公共依赖属于辅助只读证据，其生产实际依赖版本仍需核实。

**未取得生产部署 SHA。** 本文代码结论适用于以上基线，不能据此认定这些提交已部署生产。文内代码链接指向本地文件，行号对应本次核查版本，后续代码变化可能使行号移动。

## 3. 代码审查发现

### R1｜P1：代付系统异常可能被判为交易失败终态

**核心位置：**[RemitResultProcessor.java:60][src-remit-processor]，`handleGatewayCall()` 的 `RuntimeException` 分支。

当前代码将 Feign 异常保留为 `PROCESSING`，但将全部其他 `RuntimeException` 认定为业务明确失败并生成 `FAILED`。系统异常、数据解析异常和业务明确拒绝没有得到可靠区分。

#### 触发路径

1. 代付或内转策略调用 `intf-paychannel`。当 HTTP 请求成功但 `Result` 内部为系统错误时，策略通过 `new RuntimeException(result.getMessage())` 抛出异常，随后被统一转换为 `FAILED`。
2. 渠道响应缺失 `status` 时，结果转换中的状态读取触发运行时异常，同样落入 `FAILED`。
3. 结果为空时，策略试图构造一个 503 `FeignException`，但 `Response.builder().build()` 没有设置 `request`，先抛出 `IllegalStateException`。实际进入的是运行时异常失败分支，而不是预期的 Feign 补偿分支。

以上逻辑出现在 [AccountRemitApplyStrategy.java:83][src-account-strategy]、[MerchantRemitApplyStrategy.java:84][src-merchant-strategy]、[InnerTransferRemitApplyStrategy.java:81][src-inner-strategy]。

另一个具体风险入口是渠道调用后的请求日志落库：

- [InnerTransferBizImpl.java:68][src-inner-biz] 在获得渠道结果后先执行 `saveApplyLog()`，随后才构造返回结果；日志保存异常没有隔离。
- [RemitApplyBizImpl.java:80][src-remit-biz] 的普通代付链路也存在渠道返回后日志落库的处理。
- [intf-paychannel 的 ApptGlobalExceptionHandler.java:24][src-intf-handler] 会将相应异常包装为 HTTP 200 下的错误 `Result`，上游再抛出 `RuntimeException`。

因此，渠道已经受理、甚至已成功，但本地日志保存失败时，系统仍可能把交易记为失败。该路径是代码推导出的风险，尚未通过真实银行调用或生产订单核实。

#### 影响

[RemitResultHandleBizImpl.java:93][src-remit-handle] 对已有终态直接返回。若交易先被错误标为 `FAILED`，后续成功通知或查询结果无法通过该处理入口修正，可能造成渠道与本地交易状态不一致。

该异常分类逻辑属于 master 存量，追溯提交为 `3e520d3e1ab9fe3f0484b6b3daef01af106d8ba4`（2026-04-24）；新增内转链路沿用了这套处理。不能仅归因于最近一次提交。

#### 建议

- 只有明确、可识别的渠道拒绝才能转换为失败终态；系统异常、响应缺失和结果未知应保留处理中，并进入查单或人工核对流程。
- 使用明确的异常或结果类型区分业务拒绝和技术故障，避免按 `RuntimeException` 大类判定交易失败。
- 修复空返回保护分支中的 Feign 响应构造；为状态缺失单独定义未知结果处理。
- 隔离请求日志持久化异常，避免辅助日志故障覆盖已取得的渠道交易结果。
- 核对存量失败订单与渠道结果后，再设计受控修正流程，不直接移除所有终态保护。

**证据等级（初次审查时点）：**静态链路确认；3 个问题场景及 1 个 Feign 对照场景已隔离执行。当时未确认生产受影响订单或错账。

**2026-10-19 后续生产复核补充：**现存 3504 笔代付中已确认 6 笔系统异常被写成 `BUSINESS_FAIL / FAILED`，均为 2026-06-23 智能柜 `ol-order` 的 **同步申请响应**，合计 28,610.00 元。同日下午再次提交后六笔均有渠道最终成功证据；尚未证实资金损失。详见 [[wiki/公司项目/研发需求/共享支付系统/20261019-代码审查与预警归类/上线以来代付交易全量复核-20261019|全历史生产复核报告]]。

R1 要区分两个使用公共处理器的路径：

- **代付同步申请：**`AbsRemitApplyStrategy` 以 `source=APPLY` 调用 `RemitResultProcessor`，落单后将 FAILED 通过 `POST /pay/remit/singleRemit` 同步返回业务系统。本次六笔属于此路径。
- **后台渠道查单：**`RemitOrderQueryRemoteBiz` 以 `source=QUERY_SCHEDULE` 调用同一处理器；空结果、接口错误或空 data 会抛出 RuntimeException，因此同样存在错误转失败的路径。本次未确认由这一查单异常路径引发的生产实例。

业务方的 `POST /pay/remit/queryRemit` 当前直接读取本地订单状态，不在此接口内发起渠道查询。原报告 R2 的扫码超时关单是独立缺陷，不能将本次六笔代付生产证据归入 R2。

### R2｜P1：扫码超时关单漏排除空查单结果

**核心位置：**[UnionScanExpireCloseScheduleBizImpl.java:111][src-expire-close]，`processOrder()`。

当前关单逻辑在处理已成功、已失败的查单结果后，仅对 `channelRespCode = GATEWAY_EXCEPTION` 跳过本轮；其余 `UNPAID` 或 `PROCESSING` 结果会生成 `FAILED / EXPIRE_CLOSE`。

[AbstractGatewayResultTranslator.java:31][src-translator] 会把以下情况转换为有效的“处理中”结果对象：

| 原始查单结果 | 转换后的状态与代码 | 当前关单处理 |
| --- | --- | --- |
| 外层 `Result` 为空 | `PROCESSING / GATEWAY_NULL` | 生成失败终态 |
| 成功 `Result` 的 `data` 为空 | `PROCESSING / GATEWAY_DATA_NULL` | 生成失败终态 |
| Feign 调用异常 | `PROCESSING / GATEWAY_EXCEPTION` | 跳过本轮 |

前两类并不代表渠道明确确认交易未支付。因为已经转换为非空对象和非空状态，方法开头的空值判断也不会拦截。

#### 影响

未知结果被写为本地过期失败后，[PaymentOrderUpdateBizImpl.java:95][src-payment-update] 的终态判断会跳过后续更新，晚到的成功结果无法通过该入口纠正。

关单逻辑追溯至提交 `5ad01257d`（2026-10-09）。本次已复现两个空返回误关单场景，并以 Feign 异常验证正常跳过路径；尚未核实生产是否存在受影响扫码订单。

#### 建议

- 以“渠道明确确认可以关闭或已关闭”为进入失败终态的条件，明确区分未知结果和确定未支付。
- `GATEWAY_NULL`、`GATEWAY_DATA_NULL` 及其他未知结果继续查单，不依赖仅排除一个异常码的判断。
- 补充空外层响应、空数据、调用异常、明确未支付、成功结果以及晚到通知的回归验证。

**证据等级：**静态链路确认；2 个问题场景及 1 个异常对照场景已隔离执行。无已发生生产损失的证据。

### R3｜P2：账户明细正常零记录被返回为系统错误

**核心位置：**[BcmEventServer.java:118][src-bcm-event]，交行明细查询的空列表分支。

该查询先将 `Result` 初始化为系统错误。渠道成功码为 `0000` 且存在响应体后，代码解析明细；当明细列表为空时，设置“查询当日交易明细返回条数为空！”并直接返回，未将结果改为成功。

[BcmDirectResponseParser.java][src-bcm-parser] 的 `parseDetailRecords()` 在 `recordNum <= 0` 时返回空列表；字段缺失也可能返回空列表。因此，结构正常且 `recordNum = 0` 的“没有交易流水”结果，也会进入上述系统错误分支。

上游 [xxt-pay 的 ChannelBalanceBizImpl.java:109][src-channel-balance] 收到非成功结果后抛出运行时异常，最终在 `/api/channel/account/detail/day` 形成系统异常日志。

#### 与预警的对应关系

- 页面中有 55 条“查询当日交易明细返回条数为空！”事件，文案和调用路径与代码一致。
- 没有取得这 55 条事件对应的银行原始响应，不能将它们全部认定为正常零流水。
- 另有 180 条“查询当日交易明细失败！”。代码在渠道码非 `0000` 或响应体缺失时使用这一笼统文案，仅凭汇总日志无法区分银行拒绝、报文问题或其他原因。

#### 建议

- 渠道成功、结构有效、记录数为 0 时返回成功和 `detailLists = []`。
- 把“正常无数据”“字段缺失或报文异常”“渠道拒绝”分别处理。
- 对真实渠道错误保留原始返回码及脱敏后的错误说明，便于定位 180 条失败事件的根因。
- 先修正接口语义，再调整告警；不直接屏蔽全部空明细或账户查询错误。

**证据等级：**静态代码确认，并与页面同文案对应；未执行银行联调，未确认 55 条事件中正常零记录的占比。

## 4. 预警来源、统计口径与纠正说明

### 4.1 本报告采用的数据源

数据来自用户当前使用的 [WorkHub 系统预警页面](http://127.0.0.1:9529/ops/system-alerts)。本次固定筛选后逐页核对 25 页：前 24 页各 20 条，最后 1 页 9 条，合计 489 条。

| 筛选项 | 值 |
| --- | --- |
| 业务线 | 全部业务线，用于覆盖共享支付系统 |
| 环境 | 生产 |
| 服务 | 支付核心 / xxt-pay |
| 等级 | ERROR |
| 预警页签 | 系统异常，不含慢 SQL |
| 时间 | 2026-10-12 10:23:21 至 2026-10-19 10:23:21，北京时间 |
| 页面事件来源 | 全部标为 ELK |

“全部业务线”只用于共享系统筛选，本报告没有扩大为所有业务系统的预警统计。

### 4.2 先前“没有记录”结论已撤回

此前查询的 MCP 受控数据库没有这两个服务的记录，受控 ELK 查询范围也未包含它们。但这只反映当时所查询的数据源与覆盖范围，不能据此认定用户当前预警页面没有数据。

用户指出页面存在记录后，已改为核查当前页面并完成上述 489 条统计。MCP 数据源与当前页面存在不一致，其原因尚未核实。本文不沿用“共享支付系统没有预警”或“生产采集中断”的结论。

### 4.3 统计边界

- 489 是固定筛选下**页面当前保留的事件数**；未核实完整采集覆盖、保留策略、过滤或历史删除情况。
- 同一业务请求可能记录多条日志，因此事件数不是失败请求数、唯一故障数或受影响客户人数。
- 所有 489 条的 Trace ID 均为 `Ignored_Trace`，无法据此进行可靠的跨服务、逐请求串联。
- 页面服务下拉搜索 `intf-paychannel` 时未取得匹配项。其独立生产预警量待核实；通过 xxt-pay 的上游错误与源码只能建立部分链路对应关系。
- 本次保持原固定时间窗，不用整理文档时的新数据覆盖已核对的统计。

## 5. 预警分类结果

### 5.1 七类汇总

| 类别 | 条数 | 判断与处理方向 |
| --- | ---: | --- |
| 当日流水查询失败 | 180 | 保留技术告警，补原始渠道返回码、返回说明及对应调度上下文，再判断根因 |
| 当日流水返回空明细 | 55 | 优先处理 R3；核对报文后区分正常零记录与异常数据 |
| 银行、通道或账户类型不支持 | 182 | 核对产品与渠道能力、支持银行列表及请求参数；归为独立业务拒绝类别 |
| 手机号、持卡人信息及账户状态校验失败 | 31 | 以用户资料或账户校验失败归类，关注失败率突增及渠道集中性 |
| 验证码错误、过期及签约失效 | 13 | 区分输入错误、有效期和绑定状态，保留业务监控 |
| 发卡行拒绝及异常签约文案 | 10 | 保留渠道原码；其中退款文案出现在签约确认流程，需核对映射 |
| 通用“记录请求数据出现异常”日志 | 18 | 优先优化异常归属与重复记录；不能仅凭文案认定日志落库故障 |
| **合计** | **489** | **235 条流水相关 + 236 条签约相关 + 18 条通用请求异常** |

### 5.2 时间分布

| 日期（北京时间） | 当前页面命中事件数 |
| --- | ---: |
| 2026-10-15 | 73 |
| 2026-10-16 | 342 |
| 2026-10-19 | 74 |
| **合计** | **489** |

最早命中为 2026-10-15 19:33:05，最晚命中为 2026-10-19 10:21:13。其他日期没有当前页面命中，不等于这些日期没有业务错误。

### 5.3 集中特征与判断边界

**账户流水查询：**235 条均落在分钟值能被 5 整除的时间点，支持优先排查定时查询链路；这一时间特征不能单独证明具体任务身份，也不能直接判断为渠道持续故障。

**不支持银行：**173 条“暂不支持该银行”均指向 `ZJPAY`，涉及 64 个 `(tenantCode, customerCode)` 组合和 3 个 `appId`。组合数不等于自然人人数。应核对渠道能力及请求分布，现有证据不足以认定一定是配置错误。

**通用请求异常：**[ApiLoggerRecordAspect.java:71][src-api-aop] 的 `try/catch` 包住了 `joinPoint.proceed()`，业务方法抛出的异常也会被记录为“记录请求数据出现异常，异常信息:{}”，随后继续抛出。该文案不能证明异常发生在日志保存操作。

18 条通用请求异常在同秒均有独立业务错误日志，支持重复记录的可能性；但缺少有效 Trace ID，不能确认它们与业务日志逐请求一一对应。签约申请与确认在 [CardBindingBizServiceImpl.java:145][src-card-bind] 也以 ERROR 记录业务异常。

**异常文案：**3 条“退款信息与原交易信息不一致”出现在签约确认流程。需核对渠道返回码、接口上下文与错误码映射；目前列为待核实项，不认定为已确认的映射缺陷。

## 6. 建议处理顺序

| 顺序 | 工作项 | 完成判断 |
| --- | --- | --- |
| 1 | 核实生产部署版本并优先修复 R1、R2 | 未知支付结果不进入失败终态；隔离验证及相关回归通过；明确部署版本和实际影响范围 |
| 2 | 修复 R3，核查 235 条账户流水事件 | 正常零流水返回成功；异常响应有可定位的渠道码和原因；180 条失败事件形成细分结论 |
| 3 | 整理签约错误与渠道能力配置 | 182 条不支持类有明确产品或渠道解释；资料校验、验证码、发卡行拒绝分别统计 |
| 4 | 优化日志和告警分类 | 技术异常、业务拒绝、请求切面重复日志有明确归属；补齐可关联的 Trace ID 或业务流水标识 |
| 5 | 统一页面与 MCP 查询的数据源和服务映射 | 可复核相同环境、服务、时间窗的数据；核实 intf-paychannel 的独立告警覆盖 |

对于明确业务拒绝，可评估采用独立业务事件或 WARN 日志，并通过失败率、突增或渠道集中性监控。不能笼统过滤全部 `BizException`，也不能在未核实根因前批量删除预警。以上为建议，尚未实施。

## 7. 已执行的验证

### 7.1 隔离执行方式

使用本机已有 JDK 25、已有本地依赖缓存编译并执行相关代码，没有安装依赖或启动应用。

- 直接编译审查版本的 `RemitResultProcessor`、网关结果转译器、扫码关单处理等原始源码，共保留 20 个原始文件的 SHA-256 清单。
- `OrderChannelEntity`、`OrderChannelService`、`PaymentQueryRemoteBiz` 使用隔离替身；测试调用关键控制流，不连接数据库、网络或真实支付渠道。
- 调用链其他环节通过静态源码核查。此验证不是完整 Maven 构建、全量单元测试或生产集成验证。

### 7.2 七个场景的实际输出

```text
remit generic system exception => FAILED
remit missing status => FAILED
remit null-result guard => FAILED
remit actual Feign 503 control => PROCESSING
close null outer response [2,GATEWAY_NULL] => FAILED/EXPIRE_CLOSE
close null data response [2,GATEWAY_DATA_NULL] => FAILED/EXPIRE_CLOSE
close Feign exception control [2,GATEWAY_EXCEPTION] => SKIPPED
```

前述输出复现了 5 个问题场景，并验证了 2 个 Feign 正常对照场景。输出中的 `FAILED` 是现有缺陷行为，不代表修复已经完成或验证了正确业务结果。

测试环境出现 SLF4J 缺少绑定器并回退到 NOP logger 的提示，该提示属于隔离运行环境，未将其计入生产预警。

### 7.3 尚未完成的核实

- 生产部署 SHA、公共依赖实际版本，以及两个 P1 是否影响现有生产订单。
- 55 条空明细事件的银行原始报文及正常零记录占比。
- 180 条明细失败的原始渠道返回码与根因分布。
- `intf-paychannel` 独立服务的生产预警量、页面与 MCP 数据源不一致的原因。
- 通用请求异常与业务错误的逐请求关联；签约确认中退款错误文案的来源。

## 附录 A：22 种归一错误文案

按页面错误文案归类，未复制客户姓名、手机号、账号、完整业务请求或密钥等敏感内容。

| 序号 | 类别 | 错误文案 | 条数 |
| --- | --- | --- | ---: |
| 1 | 流水查询失败 | `系统异常, path=/api/channel/account/detail/day, msg=查询当日交易明细失败！` | 180 |
| 2 | 银行、通道或账户类型不支持 | `【签约申请】业务异常，error:300001,暂不支持该银行` | 173 |
| 3 | 流水空明细 | `系统异常, path=/api/channel/account/detail/day, msg=查询当日交易明细返回条数为空！` | 55 |
| 4 | 通用请求异常 | `记录请求数据出现异常，异常信息:{}` | 18 |
| 5 | 资料与账户校验 | `【签约申请】业务异常，error:验证失败（手机号有误）` | 15 |
| 6 | 资料与账户校验 | `【签约申请】业务异常，error:持卡人手机号不正确` | 7 |
| 7 | 验证码与签约失效 | `【签约确认】业务异常，error:动态验证码不符` | 6 |
| 8 | 银行、通道或账户类型不支持 | `【签约申请】业务异常，error:不支持此账户类型，请换卡重试` | 6 |
| 9 | 发卡行拒绝及异常文案 | `【签约申请】业务异常，error:PB001000,查发卡方` | 6 |
| 10 | 资料与账户校验 | `【签约申请】业务异常，error:预留手机状态异常，请到柜台核实` | 3 |
| 11 | 资料与账户校验 | `【签约申请】业务异常，error:持卡人姓名不正确` | 3 |
| 12 | 发卡行拒绝及异常文案 | `【签约确认】业务异常，error:退款信息与原交易信息不一致` | 3 |
| 13 | 银行、通道或账户类型不支持 | `【签约申请】业务异常，error:不支持该支付通道的交易` | 3 |
| 14 | 验证码与签约失效 | `【签约确认】业务异常，error:短信验证码不符` | 2 |
| 15 | 验证码与签约失效 | `【签约确认】业务异常，error:短信验证码错误` | 2 |
| 16 | 验证码与签约失效 | `【签约确认】业务异常，error:验证码错误，请重新输入` | 1 |
| 17 | 发卡行拒绝及异常文案 | `【签约确认】业务异常，error:除以上原因外的其他签约失败` | 1 |
| 18 | 验证码与签约失效 | `【签约确认】业务异常，error:绑定记录失效，请重新获取短信验证码！` | 1 |
| 19 | 资料与账户校验 | `【签约申请】业务异常，error:PB511013,手机号与在机构预留的协议支付手机号不符` | 1 |
| 20 | 资料与账户校验 | `【签约申请】业务异常，error:持卡人证件号不正确` | 1 |
| 21 | 资料与账户校验 | `【签约申请】业务异常，error:卡信息校验失败或开卡注册信息不完整` | 1 |
| 22 | 验证码与签约失效 | `【签约确认】业务异常，error:动态验证码已超过有效时间` | 1 |
| **合计** | | | **489** |

## 附录 B：来源与相关页面

- 预警来源：[WorkHub 系统预警](http://127.0.0.1:9529/ops/system-alerts)，筛选与保留边界见第 4 节。
- 代码来源：第 2.2 节固定提交的两个业务仓库及只读公共依赖；具体定位见正文代码链接。
- 验证来源：本次本地隔离运行，实际结果已完整写入第 7.2 节。
- 审查规范：[[schema/研发/WorkHub/代码审查方法]]。
- 运维定位规范：[[schema/研发/WorkHub/业务运维分析方法]]。
- 归档入口：[[index#公司项目研发需求|公司项目研发需求]]。

[src-remit-processor]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/remit/impl/RemitResultProcessor.java:60
[src-account-strategy]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/strategy/remit/impl/AccountRemitApplyStrategy.java:83
[src-merchant-strategy]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/strategy/remit/impl/MerchantRemitApplyStrategy.java:84
[src-inner-strategy]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/strategy/remit/impl/InnerTransferRemitApplyStrategy.java:81
[src-inner-biz]: /Users/aslight/IDEAWorkspace/xxt-pay/intf-paychannel/intf-paychannel-application/src/main/java/com/intf/paychannel/business/service/innerTransfer/impl/InnerTransferBizImpl.java:68
[src-remit-biz]: /Users/aslight/IDEAWorkspace/xxt-pay/intf-paychannel/intf-paychannel-application/src/main/java/com/intf/paychannel/business/service/remit/impl/RemitApplyBizImpl.java:80
[src-intf-handler]: /Users/aslight/IDEAWorkspace/xxt-pay/intf-paychannel/intf-paychannel-start/src/main/java/com/intf/paychannel/start/config/ApptGlobalExceptionHandler.java:24
[src-remit-handle]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/remit/impl/RemitResultHandleBizImpl.java:93
[src-expire-close]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/service/schedule/impl/UnionScanExpireCloseScheduleBizImpl.java:111
[src-translator]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/converter/AbstractGatewayResultTranslator.java:31
[src-payment-update]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/payment/impl/PaymentOrderUpdateBizImpl.java:95
[src-bcm-event]: /Users/aslight/IDEAWorkspace/xxt-pay/intf-paychannel/intf-paychannel-adapter/intf-paychannel-adapter-bcm/src/main/java/com/intf/paychannel/adapter/bcm/service/BcmEventServer.java:118
[src-bcm-parser]: /Users/aslight/IDEAWorkspace/xxt-pay/intf-paychannel/intf-paychannel-adapter/intf-paychannel-adapter-bcm/src/main/java/com/intf/paychannel/adapter/bcm/support/BcmDirectResponseParser.java
[src-channel-balance]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/remit/impl/ChannelBalanceBizImpl.java:109
[src-api-aop]: /Users/aslight/IDEAWorkspace/lease-saas/lease-public/lease-tracelog/trace-api-logger-plugin/src/main/java/com/lease/trace/aop/aspect/ApiLoggerRecordAspect.java:71
[src-card-bind]: /Users/aslight/IDEAWorkspace/xxt-pay/xxt-pay/xxt-pay-business/src/main/java/com/lease/xxt/pay/business/service/impl/CardBindingBizServiceImpl.java:145
