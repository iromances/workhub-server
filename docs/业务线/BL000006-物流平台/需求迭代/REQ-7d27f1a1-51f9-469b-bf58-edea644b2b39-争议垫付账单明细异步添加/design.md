# 争议垫付账单明细异步添加设计

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-7d27f1a1-51f9-469b-bf58-edea644b2b39 |
| 业务线 | BL000006 物流平台 |
| 需求目录 | 本文所在目录 |
| 版本 | v0.3，2026-11-09 用户明确将处理结果放到合作伙伴账单 |
| 对应 Proposal | [v0.2](proposal.md) |
| 仓库 | thctay-saps 和 xxt-op-portal |
| 核对基线 | 2026-11-09，thctay-saps 远端 master `4f4a228aba8190c99ca78d4e55e9d66f1e80d244`，xxt-op-portal 远端 master `61da464886d4b9cc9d1fc8b27a3d4e4f1cdb17df`，生产部署提交未核实 |

## 现状

`POST /manage/partner/bill/querySettledDispute` 在 HTTP 请求中完成筛选并返回完整列表。`updatePartnerBill` 接收整单明细 ID，写入 `pb_source_customer_bill`，并更新账单汇总。历史回查先取整单 ID，再全量联表。生产关系表已有 `idx_pbid`，索引覆盖和回查执行计划待在验证环境核对。

## 任务与页面

- 发起接口校验账单状态、版本和筛选条件，提交用于防重的运行标记后立即交给专用执行线程。同一实例同时处理一笔，线程忙时即时返回可重试错误。
- 在 `partner_bill` 增加添加状态、完成时间和失败原因摘要。发起和回写用账单版本做条件更新，版本匹配才递增并取得写入资格。详情和发起只按实际添加状态判断，RUNNING 不随时间改变。标签和附件操作独立更新自身字段及账单修改时间，并保留账单版本。筛选参数由发起请求直接传入异步线程，候选查询超时为 900 秒，超时异常按失败处理。服务中断后遗留的 RUNNING 需人工核对并处理。
- 查询在写事务外执行。落库前核对当前运行标记和草稿版本，事务内写入 `pb_source_customer_bill`、更新汇总及完成标记。失败保留已保存明细。
- 页面提交后提示已发起，重新打开详情时按账单 ID 分页读取已保存明细。处理期间状态字段显示“处理中”，添加已结算、添加未结算、删除及清空四个明细按钮禁用，已有明细仍可分页查看。删除、清空、保存和正式提交仍校验后台运行标记与账单版本。
- 上线顺序为合作伙伴账单表结构变更、后端、前端。滚动更新时应先停用旧版 XXL-JOB 配置（如果此前已注册）。回退前等待在途操作结束。

## 后端接口

基路径为 `/manage/partner/bill/disputeDraft`，响应沿用结算服务的 `Result`。

| 方法 | 路径 | 参数与行为 |
| --- | --- | --- |
| POST | `/settledAdd?version={version}` | 请求体沿用已结算筛选的 `billNo`、`billDate`、单个 `expressWayIds`、可选记账日期范围、卡号和车牌。请求受理后直接返回。 |
| GET | `/details?billNo={billNo}&page=1&size=50` | 返回 `records`、`total`、`totalAmount`、`version`、`expressWayId`、`settleDate` 和 `processing`，每页最多 100 条。 |
| POST | `/unsettledAdd` | 添加已选未结算明细，传 `billNo`、`version`、`consumeIds`、`expressWayId`、`settleDate`。 |
| POST | `/remove`、`/clear` | 传 `billNo` 和 `version`。删除指定明细时另传 `consumeIds`。 |
| POST | `/save`、`/submit` | 传 `billNo`、`version` 和备注。保存空草稿时可另传 `expressWayId` 和 `settleDate`，提交读取整张草稿的已保存关联。 |

页面进入草稿详情时读取数据和版本。发起成功后关闭详情并提示稍后重新打开查看。

部署使用脚本 `dbscript/DDL/v12_partner_bill_dispute_add_state_261109.sql`，在 `partner_bill` 增加三个可空字段，已有账单无需回填。

## 验证

核对直接异步提交、线程忙时拒绝、重复发起、异常保留原草稿、不同实际状态下的详情显示、旧执行者回写隔离及详情按账单分页。记录发起请求、后台查询和分页查询的耗时。
