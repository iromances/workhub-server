# Tasks：国胜项目-调整回购规则（测试环境黑盒测试）

> 依据本需求[研发评估](研发评估.md)执行黑盒测试。研发方案为**配置修改**（无代码发版）：从 `needRepurchaseProjects` 中移除中恒项目实现 11 月暂停，12-01 加回恢复。本文件记录链路证据、测试用例与结论。

## 1. 元信息

- 需求编码：202611030001（需求 ID 272）
- 需求目录：`docs/业务线/BL000009-嘉泰租赁资产保理/需求迭代/中恒信合乘用车保理项目/2026/202611030001-国胜项目-调整回购规则/`
- 涉及仓库与功能域：`ca-assets/assets-saps`，资方账单回购处理链路（配置驱动，无代码变更）
- 业务线/项目：BL000009 嘉泰租赁资产保理 / 中恒信合乘用车保理项目 PRJ-ZH-001
- 目标环境：TEST（DB 10.10.116.35:3306 库 assets_saps；执行器 10.10.116.35:10008；xxl-job job 63）
- 代码基线：origin/master @ c8372e995（2026-11-06，配置方案无代码分支）
- 方案确认：研发 shihao（2026-11-10，配置修改：needRepurchaseProjects 删中恒）
- 测试执行：配置变更与 job 触发由研发执行，结果由只读查询核对

## 2. 需求实现链路（代码与配置证据，2026-11-10 核查）

### 2.1 触发链路

```
xxl-job repurchaseHandleSchedule（FunderBillCompensationRepurchaseSchedule.java:51）
  → CompensationRepurchaseBiz.repurchaseHandle(projectCode, financeApplicationCode, appointGraceDate)
    → 项目清单：needRepurchaseProjects（business_config, DEF/StatementConfig，逗号分隔）
       · 不传 projectCode 时按清单遍历；显式传 projectCode 时【绕过清单】直接执行
    → 每项目两分支：
       a) 连续三期代偿回购：中恒已被代码常量 NO_TOTAL_THREE_REPURCHASE_PROJECTS="PRJ-ZH-001"
          硬编码排除（CompensationRepurchaseBiz.java:71，原有逻辑，与本次需求无关）
       b) 客户账单逾期回购：项目专属配置 needRepurchaseCustomerBillOverdueDays != null 时
          → FinancingApplicationMapper.needRepurchaseFinancingApplications
          SQL：customer_bill（未结清、未取消）DATEDIFF(#{appointGraceDate}, last_pay_date) > #{days}
          → 命中的 application_no 经 financing_mapping → 融资单 → repurchaseHandleBiz.repurchaseHandle
    → 回购动作：repayment_plan.bill_status → 601(BUY_BACK) + 生成回购对账单
```

### 2.2 配置现状（TEST，只读核查）

| 配置 | 值 | 说明 |
| --- | --- | --- |
| needRepurchaseProjects（DEF/StatementConfig, id=1732） | `PRJ-KE-001,PRJ-HC-001` | **中恒 PRJ-ZH-001 已删（暂停配置已生效）** |
| needRepurchaseCustomerBillOverdueDays（PRJ-ZH-001 专属, id=4398） | `70` | 中恒 70 天触发配置保留 |
| needCompensationDays（PRJ-ZH-001 专属, id=2161） | `3` | 代偿天数，与回购清单无关 |
| job 63 参数（当前停止） | `{"projectCode":"PRJ-ZH-001","financeApplicationCode":"FA2506051452080001","appointGraceDate":"2027-02-08"}` | **显式带 projectCode，会绕过清单**，最近手动执行 2026-10-29 |

### 2.3 关键行为结论（由代码推得，待黑盒验证）

1. 11 月暂停 = needRepurchaseProjects 不含 PRJ-ZH-001 + 定时/手动触发**不带 projectCode**。已配置 ✓
2. 12-01 恢复 = 把 PRJ-ZH-001 加回清单。恢复后首次执行将把全部已达标存量融资单一次性回购（天然满足"纳入 11 月已达标资产"）
3. **绕过风险**：显式传 projectCode=PRJ-ZH-001 可在暂停窗口强制执行回购。TEST 已有 24 笔融资单处于回购状态（601），系历史显式传参手动执行所致（job 63 现存参数即此形态）
4. **口径差**：需求"连续达到 70 个自然日" vs 实现 `DATEDIFF > 70`（超过 70，即第 71 天才命中）。第 70 天当天不触发
5. appointGraceDate 参数控制逾期计算基准日，可用于黑盒精确控制边界，无需改动业务数据

## 3. 测试用例与结果

> 前置：触发操作在 xxl-job 控制台执行（job 63）；每步后由只读 SQL 核对。执行记录由操作人回填。

### 用例 A：11 月暂停验证（2026-11-10 已执行，通过）

- 操作：通过调度中心 REST 接口以空参数触发（`POST /jobinfo/trigger, id=63`）。分两步：先空字符串参数，再 `{}` 参数。
- 执行记录（xxl_job_log）：
  - 20:36:06 空字符串参数 → handle_code=500，`NullPointerException at FunderBillCompensationRepurchaseSchedule.repurchaseExecute(java:55)`：`JSONObject.parseObject("")` 返回 null 后直接调用（**缺陷 1：入口缺判空**，对比 customerBillRepurchaseSchedule 有 `isNotBlank` 保护）。
  - 20:36:43 `{}` 参数 → 正常执行清单遍历，20:37:00 因 `ApiException: 生成权利转让协议失败`（`PlatformRepurchaseResourceGen.generateAfter(java:121)`）异常结束（**观察 3：TEST 协议生成失败导致全量任务中断**，见第 4 节风险）。
- 核对结果：
  - [x] 中恒 PRJ-ZH-001 零回购动作：repayment_plan 601 计数与触发前基线完全一致（601=392 笔/24 融资单、501=69 笔/22 融资单）。
  - [x] 清单机制工作正常：清单内卡尔 PRJ-KE-001 有 5 笔满足条件的融资单被正常回购，生成对账单 REP261110200001~REP261110200005（资方 HZF000053，总额约 13.3 万，创建时间 20:36:50~59，全部归属 PRJ-KE-001）。
- 判定：**通过**——needRepurchaseProjects 移除中恒后，任务仅处理清单内项目，11 月暂停机制生效。
- 附注：5 笔卡尔回购是任务按清单的预期行为（TEST 存量数据本满足条件），非测试误伤；已如实记录。

### 用例 B：恢复验证（单笔可控 + 恢复后回滚）

- 操作：
  1. needRepurchaseProjects 临时加回 PRJ-ZH-001（`PRJ-KE-001,PRJ-HC-001,PRJ-ZH-001`）。
  2. job 63 参数 `{"financeApplicationCode":"FA2603311542410001"}`（不带 projectCode），触发。
  3. 核对后**立即回滚配置**（再删 PRJ-ZH-001）。
- 预期：FA2603311542410001（AP260331154221000001，中恒最"新鲜"的达标融资单）资方账单未结清部分转 601、生成回购对账单；其余项目与融资单不受影响（financeApplicationCode 参数限定单笔）。
- 核对 SQL：repayment_plan 该融资单 bill_status 分布；statement_resource/statement 回购对账单生成。
- 结果：待执行

### 用例 C：70 天口径边界（ appointGraceDate 控制）

- 操作（依赖用例 B 配置临时恢复的窗口，可合并执行）：
  1. 取 AP260331154221000001 中逾期 71 天（按当天算）账单的 last_pay_date（记为 D）。
  2. appointGraceDate=`D+70` 触发（单笔参数同 B）→ 预期 DATEDIFF=70 不满足 `>70`，不回购。
  3. appointGraceDate=`D+71` 触发 → 预期回购（与用例 B 合并验证时即一步到位）。
- 预期：证实"超过 70 天触发、第 70 天当天不触发"的实现口径。
- 结果：待执行

### 用例 D：绕过风险确认（历史证据 + 操作规范）

- 证据：TEST 24 笔融资单已回购（601），与 job 63 现存显式传参的历史执行吻合；无需重复触发。
- 结论：暂停有效性依赖操作规范——**11 月暂停窗口内禁止带 projectCode=PRJ-ZH-001 触发**；生产 xxl-job 任务配置需复核无此类参数。
- 结果：已核实（代码 + 配置 + 数据三方证据）

## 4. 风险与落地清单

0. **🚨 P1 生产缺口（2026-11-10 发现并已处置）**：生产 `business_config.needRepurchaseProjects`（id=404）原值为 `PRJ-ZH-001,PRJ-KE-001,PRJ-HC-001`（中恒未删），暂停窗口内（11-01~11-10）生产中恒已发生 12 笔回购对账单（11-05 18:22 手动 1 笔 + 11-06~11-09 定时 11 笔，合计约 140.1 万元，明细见下）。**处置：2026-11-10 晚研发已将 id=404 值改为 `PRJ-KE-001,PRJ-HC-001`（config_status=1，已复查确认）**；配置实时读取（getConfig 无缓存），自下一次定时任务（11-11 01:20）起中恒被跳过。待办：a) 11-11 01:20 后核对当日 repurchase 对账单无 PRJ-ZH-001（闭环证据）；b) 已发生的 12 笔交业务评估（回购时点早于合同约定的 12-01）。

   生产 11 月中恒已回购明细（12 笔）：

   | 对账单 | 融资单 | 金额 | 时间 | 触发 |
   | --- | --- | --- | --- | --- |
   | REP261105180001 | FA2509161653560001 | 79,580.16 | 11-05 18:22 | 手动 |
   | REP261106010001 | FA2506271431450001 | 386,183.76 | 11-06 01:20 | 定时 |
   | REP261106010002 | FA2412271022350001 | 15,306.27 | 11-06 01:20 | 定时 |
   | REP261107010001 | FA2508281017060001 | 242,340.86 | 11-07 01:20 | 定时 |
   | REP261107010002 | FA2412281117180001 | 32,121.72 | 11-07 01:20 | 定时 |
   | REP261107010003 | FA2505281638130001 | 73,928.67 | 11-07 01:20 | 定时 |
   | REP261107010004 | FA2507281504290001 | 74,941.16 | 11-07 01:20 | 定时 |
   | REP261107010005 | FA2504281016430001 | 78,343.63 | 11-07 01:20 | 定时 |
   | REP261108010001 | FA2505291636060001 | 65,364.92 | 11-08 01:20 | 定时 |
   | REP261108010002 | FA2507291007340001 | 190,504.65 | 11-08 01:20 | 定时 |
   | REP261109010001 | FA2504301535550001 | 117,515.54 | 11-09 01:20 | 定时 |
   | REP261109010002 | FA2507301638200001 | 45,525.06 | 11-09 01:20 | 定时 |

1. **口径确认（重要）**：实现为"超过 70 天"（`DATEDIFF > 70`，第 71 天触发），需求文字为"达到 70 个自然日"。若合同口径要求第 70 天即收购，需将配置调为 69——配置改动、无需发版，须业务确认。
2. **缺陷 1（建议修复或规避）**：`repurchaseHandleSchedule` 空字符串参数触发 NPE（FunderBillCompensationRepurchaseSchedule.java:55，`JSONObject.parseObject("")` 返回 null 未判空）。生产参数为合法 JSON 不受影响；TEST 手动触发需用 `{}`。
3. **隐患 2（操作规范）**：显式传 `projectCode` 会绕过暂停清单（TEST 已有 24 笔历史 601 佐证）。暂停窗口内任何手动触发不得携带 projectCode。
4. **观察 3**：TEST 全量执行在卡尔项目"生成权利转让协议失败"处中断（PlatformRepurchaseResourceGen.generateAfter）。中恒恢复后同样走该环节——12-01 恢复执行前建议先验证协议生成服务可用，并按 financeApplicationCode 分批执行。
5. **12-01 恢复操作**：把 PRJ-ZH-001 加回生产 needRepurchaseProjects（job 参数保持现形态）。恢复后定时任务将自动纳入 11 月已达标存量，量级需知会业务与资方。
6. **生产 job 状态待核实**：生产 xxl_job_info 查询受限（列名差异），job 是否 RUNNING 由生产调度中心核实；从每日 01:20 定时对账单看**生产 job 处于运行中**。

## 5. 结论记录

- 用例 A（暂停）：**通过（2026-11-10 20:36）**——中恒零回购动作，数据与基线一致；清单内卡尔正常回购 5 笔（对账单 REP261110200001~005）。
- 用例 B（恢复单笔）：待执行（涉及临时恢复清单+单笔真实回购，待确认）
- 用例 C（70 天口径）：待执行（依赖用例 B 的配置窗口）
- 用例 D（绕过风险）：已核实——代码分支 + 历史回购数据（TEST 24 笔 601）+ job 63 存量危险参数三证吻合
- 新发现：缺陷 1（空参数 NPE）；观察 3（协议生成失败中断全量任务）
- 口径差结论：待业务确认（超 70 触发 vs 达到 70 触发）
- 剩余风险：口径差待定；恢复后首次执行量级待评估；生产配置值与 job 参数待核实；TEST 卡尔协议生成失败原因待查（影响恢复后中恒全量执行）
