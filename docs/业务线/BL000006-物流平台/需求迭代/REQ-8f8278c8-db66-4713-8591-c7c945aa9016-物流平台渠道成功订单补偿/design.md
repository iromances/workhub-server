# Design：物流平台渠道成功订单补偿脚本

需求编码：REQ-8f8278c8-db66-4713-8591-c7c945aa9016；业务线：BL000006-物流平台；Proposal V1；状态：待生产执行审核。
事实核对：2026-11-06 17:13:38 +08:00，PROD。payment origin/master ee34164c6651ec2e0d9832c4d4e388647e6d549b；intf-bank 6b5a422edc9790655663693e2c801389828e0717；saps 4f4a228aba8190c99ca78d4e55e9d66f1e80d244。

## 现状与补偿任务缺口

任务186 inTimeWheelPadding每分钟正常运行。ELK 17:15的实际窗口17:08:20—17:10:00，仅查新近创建订单；TradeSyncScheduleBizServiceImpl:68明确跳过WX_MINI_PROGRAM，即使指定订单号也跳过。43笔均此模式，ELK逐笔找到成功回调但未检索到带订单号的主动查单日志。
任务187只通知终态3/4/5且notice_flag为0/2、grey_flag=1、notice_count<=4、完成超过5分钟的订单，这批支付单仍status=2。任务179的getNeedPushPcs要求payment_result_code IS NOT NULL，这批为NULL，故漏选。任务178超过两天将未终态扫码/小程序单直接置超时关闭，不承担成功查单补偿。
鲁K65555回调链路已确认notifyPCS创建线程时发生unable to create new native thread；日志批量核对至少5笔同类堆栈，未声称43笔全部同因。

## 脚本与数据映射

按明确主键及全部写入相关原值核验，每条UPDATE只写一张表。渠道状态4、ch_serial_no=wechat_pay、channel_trans_no取通联trxid、完成时间取成功通知paytime。保留已核实渠道实付本金及费用字段。业务单real_amount取本金，real_fee_amount=渠道real_th_fee_amount（客户承担总手续费），real_th_fee_amount=渠道real_fee_amount（渠道手续费），real_earn_fee_amount取渠道收益；这是UpdatePayOrderBizServiceImpl非拆单分支的字段映射。通联手续费合计29.00，客户手续费41.28，收益12.28。
notice_flag、notice_count保持0，不伪造通知成功。版本均递增，设置明确modify_by与本次时间。intf回调记录、SAPS、账单和账户只读，不新增日志伪造调用结果。

## 事务、并发与回滚

五组受控证据各43条在同一事务锁定，另核对每张业务单只有一个渠道单。前置比对失败则UPDATE门禁关闭；任一UPDATE不是1行，后续关闭并整批ROLLBACK。默认@wh_commit=0，只演练回滚；授权并复核后改1，仅86行全部成功才提交。全程同一专用会话，遇SQL错误停止，断连由InnoDB回滚。
提交使订单立即满足任务187补通知条件。任务187最近17:10一轮17:12:21完成且返回200，不能据此承诺下游业务必成功。SAPS处理可能触发外部账户动账，提交前可回滚；提交后只有下游完全未消费且通知计数未变化才可用03脚本，已处理须按实际账务另定冲正，不能反改状态冒充撤销动账。

## 验证与交付

01核对执行前后订单与任务；02变更；03受限回滚；evidence.json保留原值。SQL仅离线验证，不连接生产写入。生产最终核验SAPS订单及交易、账户与账单/充值结果，通知标记成功不足以独立作为全部业务成功证据。
