# 报告契约 v2（2026-11-22 已确认）

顶层：`schemaVersion: 2`、`type: RESULT | NEEDS_INPUT`、`report`、可选 `deliveries`（沿用既有交付校验）。不接受额外顶层字段。

| report 字段 | 类型/含义 |
| --- | --- |
| reportType | INSPECTION / CODE_REVIEW / GENERAL |
| title | 非空标题 |
| conclusion | 非空总体结论，只说明结果、影响和关键下一步；详细材料归入明细，不设截断长度 |
| assessment | NORMAL / ISSUES_FOUND / INCONCLUSIVE；与运行状态独立 |
| scope | 固定对象：businessLines、environments、window（from/to/口径）、repositories（name/branch/commit/baseline）、included、excluded；未知值为 null 或明确未验证，非适用列表为空 |
| checks | 数组：key、category、title、outcome、result；outcome=PASS/FAIL/PARTIAL/NOT_VERIFIED/NOT_APPLICABLE |
| findings | 数组：key、title、severity（P0/P1/P2/UNASSESSED）、verificationStatus（VERIFIED/INFERRED/UNVERIFIED）、disposition（OPEN/PARTIAL/RESOLVED/UNKNOWN）、category、trigger、analysis、impact、recommendation；signature/domain 仅监测工单适用 |
| evidence | 数组：key、targetType、targetKey、type（CODE/QUERY/LOG/TEST/DOCUMENT/OTHER）、source、locator、observedAt、environment、verificationMethod、result、limitations |
| actions | 数组：key、findingKey（可空）、priority、description、acceptanceCriteria、prerequisite |
| limitations | 字符串数组：尚未核实、覆盖边界、环境差异，不能自动填成已完成 |

report 必填 title/reportType/conclusion/assessment/scope/checks/findings/evidence/actions/limitations；数组可以为空，但检查未执行时必须保留 NOT_VERIFIED 检查项或 limitations，不以空数组隐含“已检查且无异常”。
item key 本报告内稳定唯一；CHECK/FINDING 证据引用需存在。NORMAL 与已确认未解决问题冲突时拒绝并返回字段路径；未知或未验证范围不能当作通过。运行 RESULT 不代表 assessment=NORMAL；最终完成仍受既有验收守卫控制。

业务表达采用用户样例的层次：检查状态明确、发现与影响分开、证据有来源、建议有验收条件、未验证项显式保留。结构合法不等于事实真实，保留业务证据核验与适用范围。

模型只能生成内容；数据库 ID、run/task/agent 归属、创建时间、原文事件引用由后端提供。正文不得依赖未保存的临时本机文件作为唯一可读证据。超长正文保留，不强制把所有证据复制进 conclusion。

实现权威契约：[report-v2.schema.json](../../../../../workhub-service/src/main/resources/agent/report-v2.schema.json)。标识 key 最大 128 字符、工单 signature 最大 200 字符；正文无人工长度截断。REPORT 证据 targetKey 必须为 null，其他证据和行动引用同报告内 key。API 旧决策与已保存旧报告保留兼容。
