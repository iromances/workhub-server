# Design：保费分期入金空流水异常修复

- 需求编码：`REQ-c15a7da7-8d88-4b2d-b4f2-6d4e22ed7848`
- 需求名称：保费分期入金空流水异常修复
- 唯一需求目录：`wiki/公司项目/研发需求/业务线/BL000001-保费分期/REQ-c15a7da7-8d88-4b2d-b4f2-6d4e22ed7848-保费分期入金空流水异常修复/`
- 业务线：保费分期 `BL000001`；GitLab 组：`scf`
- 当前交付范围：`scf-saps`，分支 `fix/pend-entry-empty-details-20261021`
- 版本：v3，2026-10-21；Payment 已撤回，SAPS 外部改动待确认

## 基线和预期设计

SAPS 对照 origin/master `f4985f3af89e51935b01bac9d8d336e96a2b8b85`；该提交不代表已部署制品版本。

XXL-JOB 46 → SAPS PendEntryBizImpl → Payment `/api/bcm/queryDayDetail` → 银行明细。预期仅在 SAPS 成功响应取得 data 后判断 null 并直接 return；明细列表为空沿用现有判断，不保存流水。Payment 保持原零流水返回，不需要配套发布。

正常流水继续走 checkPendAndSave/savePend，银行流水幂等与金额处理不改。失败响应沿用原告警。验证使用 Mock 外部服务和已有 JDK 8/Maven 离线执行，不安装依赖、不访问银行或生产数据库。

## 当前实现与交付边界

Payment 本次源码改动和新增测试已撤回。SAPS 生产文件在测试后出现外部修改，目前是空响应/data 抛异常，等待用户确认意图，不能将预期设计描述为当前已完成行为。当前保留 6 项测试，其中成功但缺失 data 用例与现源码不一致；暂不改写测试去迎合异常行为。

未提交、未推送、未部署。v1/v2 方案和证据保留在历史版本目录。完成修复仍需先解决外部修改冲突并重新验证最终源码。
