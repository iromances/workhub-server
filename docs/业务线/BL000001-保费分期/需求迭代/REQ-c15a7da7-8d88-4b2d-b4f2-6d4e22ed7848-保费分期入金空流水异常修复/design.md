# Design：保费分期入金空流水异常修复

- 需求编码：`REQ-c15a7da7-8d88-4b2d-b4f2-6d4e22ed7848`
- 需求名称：保费分期入金空流水异常修复
- 唯一需求目录：`wiki/公司项目/研发需求/业务线/BL000001-保费分期/REQ-c15a7da7-8d88-4b2d-b4f2-6d4e22ed7848-保费分期入金空流水异常修复/`
- 业务线：保费分期 `BL000001`；GitLab 组：`scf`
- 当前交付仓库：`scf-saps`
- 研发分支：`fix/pend-entry-empty-details-20261021`
- 版本：v4，2026-10-21；用户已确认保留当前抛异常实现

## 基线与实现

SAPS 对照 origin/master `f4985f3af89e51935b01bac9d8d336e96a2b8b85`。该提交不等同于生产部署制品。

调用链：XXL-JOB 46 → SAPS PendEntryBizImpl → Payment `/api/bcm/queryDayDetail` → 银行当日明细。

保留当前源码：返回 result=null 时通过 ExcepitionUtil 抛“支付交易明细查询响应为空”；成功但 data=null 时抛“支付交易明细查询成功但返回数据为空”。沿用 SapsException COMMON_ERROR（900000），不进行持久化。data 存在时继续走原 CollectionUtil 集合判断，null/空明细正常结束。

正常流水仍走 checkPendAndSave/savePend，失败响应仍使用原 Wechat 告警。金额、银行流水幂等、调度频率、结算和数据库结构不变。Payment 本次变更已撤回。

## 验证与交付

使用 Mock Payment、SequenceService、PendTransactionService、WechatMessageService，不启动 Spring，不调用真实银行或中间件。使用已安装 JDK 8/Maven 离线执行 7 项测试，测试前后校验源码 SHA-256 一致。

当前 Payment 的零流水 data=null 会被 SAPS 当作业务异常，任务仍失败；本版本交付明确错误原因，不消除此失败状态。无需 Payment 配套发布。回滚 SAPS 后该场景恢复原空指针风险；没有数据迁移。

仅本地分支工作区修改，未提交、未推送、未部署。v1/v2/v3 的过程方案与验证保留历史目录；当前以 v4 为准。
