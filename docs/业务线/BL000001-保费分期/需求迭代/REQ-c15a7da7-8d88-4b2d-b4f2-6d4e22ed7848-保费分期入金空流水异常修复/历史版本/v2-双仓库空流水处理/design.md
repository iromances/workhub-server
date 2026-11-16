# Design：保费分期入金空流水异常修复

- 需求编码：`REQ-c15a7da7-8d88-4b2d-b4f2-6d4e22ed7848`
- 需求名称：保费分期入金空流水异常修复
- 唯一需求目录：`wiki/公司项目/研发需求/业务线/BL000001-保费分期/REQ-c15a7da7-8d88-4b2d-b4f2-6d4e22ed7848-保费分期入金空流水异常修复/`
- 所属业务线：保费分期，`BL000001`；GitLab 组：`scf`
- 涉及仓库：`scf-saps`、`scf-payment`
- 研发分支：两仓库均为 `fix/pend-entry-empty-details-20261021`
- 版本：v2，2026-10-21

## 现状与基线

- SAPS：`origin/master` = `f4985f3af89e51935b01bac9d8d336e96a2b8b85`。
- Payment：`origin/master` = `35468f0a24ea4d4b8fb856958dd75470d9b01658`。
- 生产日志和上述远程代码在 2026-10-21 核对；远程提交号不代表部署制品版本。
- 两工作区开始时干净。Payment 原分支为 `fix_log20260821`，本次从 master 建新分支，原分支提交保留。

## 实现与接口契约

调用链：XXL-JOB 46 → SAPS PendEntryBizImpl → `/api/bcm/queryDayDetail` → Payment BcmEventServer → 交行当日明细。

Payment 仅在已存在的零流水分支补齐 `resultDto.detailLists=[]` 和 `result.data=resultDto`，不新增查询或外部调用。SAPS 在调用返回后、读取 data 前检查 null 并直接 return；空明细继续由现有集合判断正常结束，不保存流水、不抛异常。接口路径、成功码和已有错误响应不变。

按用户 v2 要求，缺失响应或成功但 data 缺失也直接结束，兼容现网旧 Payment 的零流水返回。Payment 保留将明确零流水规范为非空 data、空 detailLists 的修复。现有失败响应仍走既有告警分支，本次不重构失败状态传播。

## 资金、幂等与验证

- 本次不新增挂账写入路径；空列表不调用 savePend，正常入金沿用 checkPendAndSave/savePend。
- 金额 BigDecimal、借贷方向、银行流水去重、自动结算及外部通知规则不变；不进行数据补偿。
- Payment 采用只绑定 127.0.0.1 的临时 HTTP 响应桩和模拟 RedisIdWorker；SAPS 模拟 Payment、SequenceService、PendTransactionService、WechatMessageService。测试不启动 Spring、不接真实中间件或银行，数据全部为合成值。
- 使用已安装 JDK 8 与 Maven，离线执行指定测试；依赖不足时记录真实阻塞并使用已有依赖进行最小隔离验证，不下载或安装。

## 发布与回滚

本轮交付本地分支代码和测试，不执行发布。v2 两侧互相兼容，不再要求固定先后顺序：SAPS 兼容旧 Payment 的 null data，Payment 的空列表也兼容旧 SAPS。SAPS 可单独先发布解决现有空指针。无 DDL/DML，无数据迁移。回滚按对应代码版本回退；仅 Payment 回退而保留 v2 SAPS 时仍可正常跳过零流水；两侧均回退后原空指针风险重新存在。

## 一致性

AC-01/02 由 Payment 契约修复与测试承接；AC-03/04 由 SAPS 边界和持久化回归承接；AC-05 由 Tasks 的 Git 与验证证据承接。更新保费分期系统现状中的修复说明，并明确未部署，迭代日志记录实际阶段。
