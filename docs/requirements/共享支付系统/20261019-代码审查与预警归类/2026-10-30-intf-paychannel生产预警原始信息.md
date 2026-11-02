# intf-paychannel 生产预警原始信息摘录

> 整理日期：2026-10-30；本轮日志复核截至约 14:05（Asia/Shanghai）。
> 范围：生产环境、全部业务线共用的 intf-paychannel；受控 ELK 范围为工作台 BL000012 / prod。
> 配套分析：[分类、根因与处理建议](2026-10-30-intf-paychannel生产预警分析.md)。

## 1. 口径与阅读方式

- 本文按前次分析保留的 179 条历史 ERROR 分类整理**代表性原始样本**，不是 179 条日志的逐条全量导出。原统计区间为 2026-10-16 09:33:00 至 2026-10-30 10:00:01。
- 已排除用户确认恢复并要求删除的渠道故障。本次不将后续单独排查的 xxt-pay 当日流水查询、签约验证码问题混入原统计。
- 下文“原统计条数”沿用前次菜单快照，不是本轮 ELK 检索数量；同一请求跨层 ERROR 可能被统计多次。
- 日志消息保留原文；堆栈仅保留异常首行、前若干调用帧和 Caused by 原文，标注为节选。时间统一转换为北京时间，事件 ID 保持原值。
- 账户型 merchantNo、请求 URL 的 v 参数做额外脱敏；工具已掩码的内容保留掩码。未保存凭据、请求头、验证码或完整交易报文。
- 每条样本附 sourceEventId，可结合时间、服务及错误文本复查。具体根因判断与待核实项见配套分析，不将分析结论冒充原始日志。
- 本轮只读获取日志并整理文档，未修改生产配置、业务数据或代码。

## 2. 分类索引

| 编号 | 类别 | 原统计条数 | 本文样本日志数 |
| --- | --- | ---: | ---: |
| 1 | 宝付对账：IP 与报备不匹配 | 28 | 3 |
| 2 | 交行对账：未找到适配器 | 33 | 3 |
| 3 | 新生对账：渠道配置加载异常 | 11 | 1 |
| 4 | 京东对账：商户 IP 未授权 | 22 | 2 |
| 5 | 京东对账：商户未授权 | 22 | 2 |
| 6 | 京东对账：请求文件无效 | 22 | 2 |
| 7 | 京东对账：文件无明细 | 22 | 2 |
| 8 | 首易信对账：三类文件均不存在 | 14 | 1 |
| 9 | 新生支付：Connection reset | 3 | 3 |
| 10 | 请求方法不支持 | 1 | 1 |
| 11 | 请求 JSON 未闭合 | 1 | 1 |
| 合计 | | 179 | 21 |

## 3. 原始错误及堆栈

### 1. 宝付对账：IP 与报备不匹配

原统计条数：28。代付商户 1283248、1283250；支付商户 1273947。以下分别保留一个失败样本。

#### 样本 1.1

- 发生时间：`2026-10-29 10:00:01.004 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`1c2377acf920d072e743fa14ce1d420a40f709711acc79ba32d07751ce64f933`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=BAOFUPAY, merchantNo=1273947, type=PAYMENT, date=2026-10-28
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 宝付对账单下载失败: IP与报备的Ip不匹配
	at com.intf.paychannel.adapter.baofu.BaofuStatementChannelAdapter.downloadFromChannel(BaofuStatementChannelAdapter.java:156)
	at com.intf.paychannel.adapter.baofu.BaofuStatementChannelAdapter.downloadStatement(BaofuStatementChannelAdapter.java:81)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
	at com.intf.paychannel.business.service.statement.impl.StatementBizImpl.lambda$trySubmitAsyncPull$0(StatementBizImpl.java:169)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
...（后续调用帧省略）
```

#### 样本 1.2

- 发生时间：`2026-10-30 10:00:00.740 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`2e8be392b48a54477c75c4b23f7b787e393c985e99d48c7c004b6b479eff8373`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=BAOFUPAY, merchantNo=1283248, type=REMIT, date=2026-10-29
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 宝付对账单下载失败: IP与报备的Ip不匹配
	at com.intf.paychannel.adapter.baofu.BaofuStatementChannelAdapter.downloadFromChannel(BaofuStatementChannelAdapter.java:156)
	at com.intf.paychannel.adapter.baofu.BaofuStatementChannelAdapter.downloadStatement(BaofuStatementChannelAdapter.java:81)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
	at com.intf.paychannel.business.service.statement.impl.StatementBizImpl.lambda$trySubmitAsyncPull$0(StatementBizImpl.java:169)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
...（后续调用帧省略）
```

#### 样本 1.3

- 发生时间：`2026-10-30 10:00:00.762 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`a433a1e1ee97843086191c2f02ee714aa323037318bf361d8938a098c0089f02`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=BAOFUPAY, merchantNo=1283250, type=REMIT, date=2026-10-29
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 宝付对账单下载失败: IP与报备的Ip不匹配
	at com.intf.paychannel.adapter.baofu.BaofuStatementChannelAdapter.downloadFromChannel(BaofuStatementChannelAdapter.java:156)
	at com.intf.paychannel.adapter.baofu.BaofuStatementChannelAdapter.downloadStatement(BaofuStatementChannelAdapter.java:81)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
	at com.intf.paychannel.business.service.statement.impl.StatementBizImpl.lambda$trySubmitAsyncPull$0(StatementBizImpl.java:169)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
...（后续调用帧省略）
```

### 2. 交行对账：未找到适配器

原统计条数：33。商户 0008232869 的两条日志时间不同，均保留；另一个 merchantNo 与账户号相同，已脱敏。

#### 样本 2.1

- 发生时间：`2026-10-30 10:00:00.783 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`fadec0c1ba940633c0e7a88de8027493becb7b19a773eceb080c871d349825e2`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=BCM, merchantNo=0008232869, type=REMIT, date=2026-10-29
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 未找到渠道对账单适配器:BCM
	at com.intf.paychannel.business.adapter.ChannelAdapterRouter.requireAdapter(ChannelAdapterRouter.java:232)
	at com.intf.paychannel.business.adapter.ChannelAdapterRouter.routeStatement(ChannelAdapterRouter.java:217)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:72)
	at com.intf.paychannel.business.service.statement.impl.StatementBizImpl.lambda$trySubmitAsyncPull$0(StatementBizImpl.java:169)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
...（后续调用帧省略）
```

#### 样本 2.2

- 发生时间：`2026-10-30 10:00:00.861 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`ca45298b3a9b8389151e30a95897fd40e1aeb79c2439f8f86b3ace22f3db2952`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=BCM, merchantNo=0008232869, type=REMIT, date=2026-10-29
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 未找到渠道对账单适配器:BCM
	at com.intf.paychannel.business.adapter.ChannelAdapterRouter.requireAdapter(ChannelAdapterRouter.java:232)
	at com.intf.paychannel.business.adapter.ChannelAdapterRouter.routeStatement(ChannelAdapterRouter.java:217)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:72)
	at com.intf.paychannel.business.service.statement.impl.StatementBizImpl.lambda$trySubmitAsyncPull$0(StatementBizImpl.java:169)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
...（后续调用帧省略）
```

#### 样本 2.3

- 发生时间：`2026-10-30 10:00:00.868 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`057c90e155c3045905d97b0a9070dc9be847e0ee08cf0c1ee93c0c876ec89590`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=BCM, merchantNo=***************6419, type=REMIT, date=2026-10-29
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 未找到渠道对账单适配器:BCM
	at com.intf.paychannel.business.adapter.ChannelAdapterRouter.requireAdapter(ChannelAdapterRouter.java:232)
	at com.intf.paychannel.business.adapter.ChannelAdapterRouter.routeStatement(ChannelAdapterRouter.java:217)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:72)
	at com.intf.paychannel.business.service.statement.impl.StatementBizImpl.lambda$trySubmitAsyncPull$0(StatementBizImpl.java:169)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
...（后续调用帧省略）
```

### 3. 新生对账：渠道配置加载异常

原统计条数：11。日志原文只说明配置加载失败；接口配置和 SFTP 缺失属于原分析文档中的数据库核验结论。

#### 样本 3.1

- 发生时间：`2026-10-30 10:00:00.821 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`7c3b555bef9467ee83863b66e13f9497a4305ac6f84e8ce254fc3d841336fc04`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=HNAPAY, merchantNo=11000012793, type=PAYMENT, date=2026-10-29
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 渠道配置信息加载异常
	at com.intf.paychannel.adapter.hnapay.service.statement.HnapayStatementService.getStatementApiConfig(HnapayStatementService.java:129)
	at com.intf.paychannel.adapter.hnapay.service.statement.HnapayStatementService.downloadStatement(HnapayStatementService.java:71)
	at com.intf.paychannel.adapter.hnapay.HnapayStatementChannelAdapter.downloadStatement(HnapayStatementChannelAdapter.java:56)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
	at com.intf.paychannel.business.service.statement.impl.StatementBizImpl.lambda$trySubmitAsyncPull$0(StatementBizImpl.java:169)
...（后续调用帧省略）
```

### 4. 京东对账：商户 IP 未授权

原统计条数：22。渠道原始返回摘要与异步入口异常各一条，属于同一拉取失败的两层日志。

#### 样本 4.1

- 发生时间：`2026-10-30 10:00:01.164 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`3168023659a1873a4c275a17bc8b22bdee31d110038be65b8236e400ae49fdbf`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
京东对账单下载失败: {"httpCode":200,"returnCode":"00112","returnMessage":"商户IP未授权","bodySnippet":""}
```

#### 样本 4.2

- 发生时间：`2026-10-30 10:00:01.165 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`cbda8909ea68cf8f9dcac762c331cbc3f19a6f8da7acf68d0b845474118464e8`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=JDPAY, merchantNo=360080004099090170, type=PAYMENT, date=2026-10-29
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 京东对账单下载失败: 00112 商户IP未授权
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.postForm(JdStatementService.java:260)
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.downloadFromChannel(JdStatementService.java:170)
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.downloadStatement(JdStatementService.java:91)
	at com.intf.paychannel.adapter.jd.JdStatementChannelAdapter.downloadStatement(JdStatementChannelAdapter.java:51)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
...（后续调用帧省略）
```

### 5. 京东对账：商户未授权

原统计条数：22。渠道原始返回摘要与异步入口异常各一条。

#### 样本 5.1

- 发生时间：`2026-10-30 10:00:01.217 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`035d99463acf4ce6c777e05f9e2bcb7a9b72a7e31ef671f196255048ce20a281`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
京东对账单下载失败: {"httpCode":200,"returnCode":"00111","returnMessage":"商户未授权","bodySnippet":""}
```

#### 样本 5.2

- 发生时间：`2026-10-30 10:00:01.218 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`58fdc7f7765b875c84b2fd0ea397ead808228ecfb0478ccbc597888c43199955`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=JDPAY, merchantNo=360080004099945902, type=PAYMENT, date=2026-10-29
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 京东对账单下载失败: 00111 商户未授权
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.postForm(JdStatementService.java:260)
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.downloadFromChannel(JdStatementService.java:170)
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.downloadStatement(JdStatementService.java:91)
	at com.intf.paychannel.adapter.jd.JdStatementChannelAdapter.downloadStatement(JdStatementChannelAdapter.java:51)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
...（后续调用帧省略）
```

### 6. 京东对账：请求文件无效

原统计条数：22。保留渠道返回的文件路径，便于渠道核对产品、文件名及账单日期。

#### 样本 6.1

- 发生时间：`2026-10-30 10:00:01.198 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`43564e4161022aaf86c4048dc19f52111a90e4cda5accff326ebfaecf43c860f`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
京东对账单下载失败: {"httpCode":200,"returnCode":"00124","returnMessage":"请求文件无效-->/opt/nfs_workspace/exportdata/0001/0039/185688856/20261029serviceOrderCreate_185688856002.zip","bodySnippet":""}
```

#### 样本 6.2

- 发生时间：`2026-10-30 10:00:01.199 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`2cfeb25271d231e1bdc4bf58cec315efa8d728e8a4c4563d5bd7250cdbf1a545`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=JDPAY, merchantNo=360080004100058885, type=PAYMENT, date=2026-10-29
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 京东对账单下载失败: 00124 请求文件无效-->/opt/nfs_workspace/exportdata/0001/0039/185688856/20261029serviceOrderCreate_185688856002.zip
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.postForm(JdStatementService.java:260)
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.downloadFromChannel(JdStatementService.java:170)
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.downloadStatement(JdStatementService.java:91)
	at com.intf.paychannel.adapter.jd.JdStatementChannelAdapter.downloadStatement(JdStatementChannelAdapter.java:51)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
...（后续调用帧省略）
```

### 7. 京东对账：文件无明细

原统计条数：22。两个商户各一个样本。注意日志发生于 09:33，而请求的账单日期是当天。

#### 样本 7.1

- 发生时间：`2026-10-30 09:33:01.636 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`1b09fc5df299c514ed6c0496a400439673fe4f7db19cfc26646ce2d79e00a0c4`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=JDPAY, merchantNo=360080004090349187, type=PAYMENT, date=2026-10-30
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 京东新分账对账单文件无明细
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementCsvConvert.parseToRows(JdStatementCsvConvert.java:69)
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.downloadStatement(JdStatementService.java:93)
	at com.intf.paychannel.adapter.jd.JdStatementChannelAdapter.downloadStatement(JdStatementChannelAdapter.java:51)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
	at com.intf.paychannel.business.service.statement.impl.StatementBizImpl.lambda$trySubmitAsyncPull$0(StatementBizImpl.java:169)
...（后续调用帧省略）
```

#### 样本 7.2

- 发生时间：`2026-10-30 09:33:01.000 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`2c1db5498e65073d6c83c5c42644bd5d3844499ecb8215f9592b4a2d9afe34d9`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
对账单异步拉取执行失败: channel=JDPAY, merchantNo=360080004099096375, type=PAYMENT, date=2026-10-30
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 京东新分账对账单文件无明细
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementCsvConvert.parseToRows(JdStatementCsvConvert.java:69)
	at com.intf.paychannel.adapter.jd.service.statement.JdStatementService.downloadStatement(JdStatementService.java:93)
	at com.intf.paychannel.adapter.jd.JdStatementChannelAdapter.downloadStatement(JdStatementChannelAdapter.java:51)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
	at com.intf.paychannel.business.service.statement.impl.StatementBizImpl.lambda$trySubmitAsyncPull$0(StatementBizImpl.java:169)
...（后续调用帧省略）
```

### 8. 首易信对账：三类文件均不存在

原统计条数：14。保留一条下载适配器错误。14 条为原菜单快照统计；本节不声称重新完整提取了历史两层日志。

#### 样本 8.1

- 发生时间：`2026-10-29 10:00:01.708 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`43922904722da134d0e56ffa054a4ad77a662e409bfc2beb04764a4273d35f0b`
- traceId：`N/A`（日志记录值）

日志原文（脱敏）：

```text
首易信对账适配器下载异常: merchantNo=898649839, statementDate=2026-10-28, error=首易信对账单：支付、退款、分账文件均不存在
```

异常及关键调用帧（节选）：

```text
com.intf.paychannel.common.exception.BizException: 首易信对账单：支付、退款、分账文件均不存在
	at com.intf.paychannel.adapter.syxpay.service.statement.SyxpayStatementService.failure(SyxpayStatementService.java:162)
	at com.intf.paychannel.adapter.syxpay.service.statement.SyxpayStatementService.convert(SyxpayStatementService.java:120)
	at com.intf.paychannel.adapter.syxpay.service.statement.SyxpayStatementService.download(SyxpayStatementService.java:47)
	at com.intf.paychannel.adapter.syxpay.SyxpayStatementChannelAdapter.downloadStatement(SyxpayStatementChannelAdapter.java:43)
	at com.intf.paychannel.business.service.statement.impl.StatementPullRunner.pullAndPersist(StatementPullRunner.java:73)
...（后续调用帧省略）
```

### 9. 新生支付：Connection reset

原统计条数：3。三条日志关联同一订单 XC2610290705550001，不能当成三笔失败订单。

#### 样本 9.1

- 发生时间：`2026-10-29 07:05:55.285 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`e67ae683997b5fdcc6cb11c51ee024b6d2f85d0c1910a2a989240a56a425bebf`
- traceId：`Ignored_Trace`（日志记录值）

日志原文（脱敏）：

```text
新生渠道HTTP请求异常, url=https://gateway.hnapay.com/exp/payRequest2Step.do?v=[已脱敏]
```

异常及关键调用帧（节选）：

```text
java.net.SocketException: Connection reset
	at java.net.SocketInputStream.read(SocketInputStream.java:210)
	at java.net.SocketInputStream.read(SocketInputStream.java:141)
	at sun.security.ssl.SSLSocketInputRecord.read(SSLSocketInputRecord.java:475)
	at sun.security.ssl.SSLSocketInputRecord.readHeader(SSLSocketInputRecord.java:469)
	at sun.security.ssl.SSLSocketInputRecord.bytesInCompletePacket(SSLSocketInputRecord.java:69)
...（后续调用帧省略）
```

#### 样本 9.2

- 发生时间：`2026-10-29 07:05:55.285 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`943a349012bc55e46d54705f7f4c2207e99d6ac329322acf4002dacac35500a2`
- traceId：`Ignored_Trace`（日志记录值）

日志原文（脱敏）：

```text
新生协议支付下单业务异常, orderId=XC2610290705550001, error=接口调用异常
```

#### 样本 9.3

- 发生时间：`2026-10-29 07:05:55.290 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`aea978a4cfb17304ac782326a31f75d406a3546d3a90163da131a071db31d245`
- traceId：`Ignored_Trace`（日志记录值）

日志原文（脱敏）：

```text
buildExp12FailedResponse: {"channel":22,"orderId":"XC2610290705550001","respCode":"100001","respMsg":"接口调用异常","status":3}
```

### 10. 请求方法不支持

原统计条数：1。当前样本未提供足够信息确认具体调用方和接口路径。

#### 样本 10.1

- 发生时间：`2026-10-26 09:49:36.393 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`bade10cc07ec4c28f80f40909d3547a77b44d0b5a51c7d5701ca8cb2f57277c0`
- traceId：`Ignored_Trace`（日志记录值）

日志原文（脱敏）：

```text
[20440]接口异常
```

异常及关键调用帧（节选）：

```text
org.springframework.web.HttpRequestMethodNotSupportedException: Request method 'GET' not supported
	at org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping.handleNoMatch(RequestMappingInfoHandlerMapping.java:213)
	at org.springframework.web.servlet.handler.AbstractHandlerMethodMapping.lookupHandlerMethod(AbstractHandlerMethodMapping.java:422)
	at org.springframework.web.servlet.handler.AbstractHandlerMethodMapping.getHandlerInternal(AbstractHandlerMethodMapping.java:367)
	at org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping.getHandlerInternal(RequestMappingInfoHandlerMapping.java:110)
	at org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping.getHandlerInternal(RequestMappingInfoHandlerMapping.java:59)
...（后续调用帧省略）
```

### 11. 请求 JSON 未闭合

原统计条数：1。token 已被受控日志工具脱敏；不推测其原值。当前样本未确认具体调用方和接口路径。

#### 样本 11.1

- 发生时间：`2026-10-27 10:45:33.393 +08:00`
- 服务 / 级别：`intf-paychannel / ERROR`
- sourceEventId：`4a46f7a3ee1a816c79925816144b7a8d86a822a7f5dd580b4c27a3aa28e8ce30`
- traceId：`Ignored_Trace`（日志记录值）

日志原文（脱敏）：

```text
[20440]接口异常
```

异常及关键调用帧（节选）：

```text
org.springframework.http.converter.HttpMessageNotReadableException: JSON parse error: not close json text, token : ******; nested exception is com.alibaba.fastjson.JSONException: not close json text, token : ******
	at com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter.readType(FastJsonHttpMessageConverter.java:271)
	at com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter.read(FastJsonHttpMessageConverter.java:237)
	at org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodArgumentResolver.readWithMessageConverters(AbstractMessageConverterMethodArgumentResolver.java:205)
	at org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor.readWithMessageConverters(RequestResponseBodyMethodProcessor.java:158)
	at org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor.resolveArgument(RequestResponseBodyMethodProcessor.java:131)
...（中间调用帧省略）
Caused by: com.alibaba.fastjson.JSONException: not close json text, token : ******
	at com.alibaba.fastjson.parser.DefaultJSONParser.close(DefaultJSONParser.java:1527)
...（后续调用帧省略）
```

## 4. 复查入口与检索窗口

入口：[系统预警](http://127.0.0.1:9529/ops/system-alerts)；或 WorkHub 受控日志工具 search_business_logs，固定 businessLine=工作台、environmentCode=prod、serviceNames=intf-paychannel、levels=ERROR。

| 用途 | 本轮检索时间（北京时间） | 关键词 |
| --- | --- | --- |
| 对账主要样本 | 2026-10-30 09:30:00—10:01:00 | 按渠道、商户及错误文本定位 |
| 宝付支付对账补充 | 2026-10-24 00:00:00—2026-10-30 10:01:00 | 1273947 |
| 首易信补充 | 2026-10-24 00:00:00—2026-10-30 10:01:00 | 首易信 |
| 新生连接异常 | 2026-10-29 07:05:50—07:06:05 | XC2610290705550001 / Connection reset |
| GET 不支持 | 2026-10-26 09:49:35—09:49:38 | Request method 'GET' not supported |
| JSON 未闭合 | 2026-10-27 10:45:32—10:45:35 | not close json text |

商户号检索命中情况受日志分词影响；复查不到时，使用上述窄时间窗口并结合异常文本核对。原始事件时间来自 ELK 的 occurredAt（UTC），本文已统一加 8 小时展示。

