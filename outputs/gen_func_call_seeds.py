#!/usr/bin/env python3
"""从 XxlJobMonitorCollector/MqMonitorCollector 的在用 SQL 文本块生成 FuncCallSqlSeeds.java。

按 Java text block 语义（最小缩进剥离、行尾空白剥离、闭合定界符独立成行则保留末尾换行）
提取运行期字符串，将 %n$s / %n$d 逐位替换为 :name 命名参数，MQ 分支按原逻辑 stripTrailing。
"""
import re, sys

BASE = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/workhub-service/src/main/java/cn/aslight/workhub/service/ops"
XXL = f"{BASE}/XxlJobMonitorCollector.java"
MQ = f"{BASE}/MqMonitorCollector.java"

def text_blocks(src):
    """返回 (start_line, text) 列表，text 为按 Java 语义处理后的字符串。"""
    lines = src.split("\n")
    out = []
    i = 0
    while i < len(lines):
        m = re.search(r'"""\s*$', lines[i])
        if m and not re.search(r'\."""', lines[i]):
            # opening delimiter; content starts next line
            j = i + 1
            content = []
            close_line = None
            while j < len(lines):
                cm = re.search(r'"""', lines[j])
                if cm:
                    close_line = j
                    break
                content.append(lines[j])
                j += 1
            if close_line is None:
                sys.exit(f"unterminated text block at line {i+1}")
            closing_indent = len(lines[close_line]) - len(lines[close_line].lstrip())
            # min indent over non-blank content lines and closing delimiter line
            indents = [len(l) - len(l.lstrip()) for l in content if l.strip()]
            indents.append(closing_indent)
            strip = min(indents)
            proc = [l[strip:].rstrip() if l.strip() else "" for l in content]
            # closing delimiter on own line -> trailing newline kept
            if lines[close_line].strip() == '"""':
                text = "\n".join(proc) + "\n"
            else:
                # closing on same line as content: content part is in proc already? no—
                # closing line has content before """ ; treat its content too
                pre = lines[close_line][:cm.start()]
                proc.append(pre[strip:].rstrip() if pre.strip() else "")
                text = "\n".join(proc)
            out.append((i, text))
            i = close_line
        i += 1
    return out

def method_blocks(path, method):
    src = open(path, encoding="utf-8").read()
    m = re.search(rf"private [\w.]+\s+{re.escape(method)}\(", src)
    if not m:
        sys.exit(f"method {method} not found in {path}")
    start = m.start()
    # method body ends at the next "\n    private " after start
    nxt = src.find("\n    private ", start)
    if nxt == -1:
        nxt = len(src)
    body = src[start:nxt]
    return text_blocks(body)

def convert(text, params, strip_trailing):
    for idx, name in enumerate(params, start=1):
        text = text.replace(f"%{idx}$s", ":" + name)
        text = text.replace(f"%{idx}$d", ":" + name)
    leftover = re.findall(r"%\d+\$[sd]", text)
    if leftover:
        sys.exit(f"unconverted placeholders {leftover} in {text[:80]}")
    return text.rstrip() if strip_trailing else text

JOBS = [
    # (file, method, block_index, strip_trailing, sql_code, scenario, branch, name, desc, params, param_desc, module)
    (XXL, "summarySql", 0, False, "OPS_XXL_SUMMARY", "OPS-XXL-SUMMARY", 1,
     "XXL-JOB 全库汇总", "xxl_job_log_report 报表汇总（无执行器过滤）",
     ["databaseName", "startDate", "endDateExclusive"],
     "databaseName=反引号库名；startDate/endDateExclusive=trigger_day 窗口（含头不含尾，日期）", "OPS_XXL"),
    (XXL, "focusedSummarySql", 0, False, "OPS_XXL_FOCUSED_SUMMARY", "OPS-XXL-FOCUSED-SUMMARY", 1,
     "XXL-JOB 执行器聚焦汇总", "按执行器过滤的日志/任务汇总",
     ["databaseName", "executorFilter", "startDateTime", "endExclusiveDateTime"],
     "executorFilter=AND g.app_name IN (...) 片段；startDateTime/endExclusiveDateTime=yyyy-MM-dd 00:00:00", "OPS_XXL"),
    (XXL, "healthStatsSql", 0, False, "OPS_XXL_HEALTH_STATS", "OPS-XXL-HEALTH-STATS", 1,
     "XXL-JOB 五态健康统计", "巡检用全库健康统计（成功/运行/卡死/执行失败/静默）",
     ["silentGraceCutoff", "databaseName", "staleCutoff", "startDate", "endDateExclusive"],
     "silentGraceCutoff=静默宽限线；staleCutoff=卡死阈值线；窗口为 trigger_time 日期段", "OPS_XXL"),
    (XXL, "failedJobsCountSql", 0, False, "OPS_XXL_FAILED_COUNT", "OPS-XXL-FAILED-COUNT", 1,
     "XXL-JOB 失败明细计数", "失败/异常日志总数",
     ["databaseName", "startDateTime", "endExclusiveDateTime", "executorFilter", "detailFilter", "statusFilter"],
     "executorFilter/detailFilter/statusFilter=白名单片段，可为空串", "OPS_XXL"),
    (XXL, "failedJobsSql", 0, False, "OPS_XXL_FAILED_PAGE", "OPS-XXL-FAILED-PAGE", 1,
     "XXL-JOB 失败明细分页", "失败/异常日志分页明细",
     ["databaseName", "startDateTime", "endExclusiveDateTime", "executorFilter", "detailFilter", "statusFilter", "limit", "offset"],
     "limit/offset=分页数字；其余同失败明细计数", "OPS_XXL"),
    (XXL, "silentJobsSql", 0, False, "OPS_XXL_SILENT_JOBS", "OPS-XXL-SILENT-JOBS", 1,
     "XXL-JOB 静默任务明细", "启用中但调度停摆的任务（前 20）",
     ["databaseName", "silentGraceCutoff"],
     "silentGraceCutoff=静默宽限线时间字面量", "OPS_XXL"),
    (XXL, "logStatisticsSql", 0, False, "OPS_XXL_LOG_STATS", "OPS-XXL-LOG-STATS", 1,
     "XXL-JOB 日志页统计", "日志分页统计（单 SELECT 统计与明细共享读取视图）",
     ["matching", "databaseName", "startDate", "endDateExclusive", "executorFilter", "detailFilter"],
     "matching=1 或 CASE WHEN 异常谓词片段", "OPS_XXL"),
    (XXL, "executorsSql", 0, False, "OPS_XXL_EXECUTORS", "OPS-XXL-EXECUTORS", 1,
     "XXL-JOB 执行器清单", "xxl_job_group 执行器列表",
     ["databaseName"], "databaseName=反引号库名", "OPS_XXL"),
    (MQ, "statsSql", 0, True, "OPS_MQ_STATS_SENDER", "OPS-MQ-STATS", 1,
     "MQ 巡检统计·发送分支", "message_sender_log 窗口统计（发送侧）",
     ["databaseLabel", "databaseName", "windowFrom", "windowTo", "stuckLiteral", "exchangeFilter"],
     "databaseLabel=展示名原样字面量；databaseName=反引号库名；stuckLiteral=卡死阈值时间；exchangeFilter 可为空串", "OPS_MQ"),
    (MQ, "statsSql", 1, True, "OPS_MQ_STATS_RECEIVER", "OPS-MQ-STATS", 2,
     "MQ 巡检统计·接收分支", "message_receiver_log 窗口统计（接收侧）",
     ["databaseLabel", "windowFrom", "windowTo", "exchangeFilter", "queueFilter", "databaseName"],
     "exchangeFilter/queueFilter 可为空串", "OPS_MQ"),
    (MQ, "messageStatisticsSql", 0, True, "OPS_MQ_MESSAGE_STATS_SENDER", "OPS-MQ-MESSAGE-STATS", 1,
     "MQ 消息统计·发送分支", "发送侧活动数/匹配数统计",
     ["databaseName", "windowFrom", "windowTo", "senderPredicate", "exchangeFilter"],
     "senderPredicate=异常判定片段（含卡死时间）", "OPS_MQ"),
    (MQ, "messageStatisticsSql", 1, True, "OPS_MQ_MESSAGE_STATS_RECEIVER", "OPS-MQ-MESSAGE-STATS", 2,
     "MQ 消息统计·接收分支", "接收侧活动数/匹配数统计",
     ["databaseName", "windowFrom", "windowTo", "receiverPredicate", "exchangeFilter", "queueFilter"],
     "receiverPredicate=异常判定片段", "OPS_MQ"),
    (MQ, "messagePageSql", 0, True, "OPS_MQ_MESSAGE_PAGE_SENDER", "OPS-MQ-MESSAGE-PAGE", 1,
     "MQ 消息分页·发送分支", "发送侧异常消息分页明细",
     ["databaseLabel", "databaseName", "windowFrom", "windowTo", "senderPredicate", "exchangeFilter"],
     "同消息统计发送分支", "OPS_MQ"),
    (MQ, "messagePageSql", 1, True, "OPS_MQ_MESSAGE_PAGE_RECEIVER", "OPS-MQ-MESSAGE-PAGE", 2,
     "MQ 消息分页·接收分支", "接收侧异常消息分页明细",
     ["databaseLabel", "windowFrom", "windowTo", "receiverPredicate", "exchangeFilter", "queueFilter", "databaseName"],
     "同消息统计接收分支", "OPS_MQ"),
]

records = []
for (path, method, idx, strip_tail, code, scenario, branch, name, desc, params, param_desc, module) in JOBS:
    blocks = method_blocks(path, method)
    if idx >= len(blocks):
        sys.exit(f"block {idx} missing in {method}")
    text = blocks[idx][1]
    tpl = convert(text, params, strip_tail)
    records.append((code, scenario, branch, name, desc, params, param_desc, module, tpl))

# REQ-b4d7e6f2：discoverDatabases 原内联 SQL 已随执行收口改造移出方法体，模板在此内联登记。
records.append(("OPS_MQ_DISCOVER_DATABASES", "OPS-MQ-DISCOVER-DATABASES", 1, "MQ 库发现",
    "按 message_sender_log 表定位业务线下的 MQ 子应用库", [], "无参数", "OPS_MQ",
    "SELECT table_schema AS databaseName FROM information_schema.tables\nWHERE table_name = 'message_sender_log'"))

def java_textblock(tpl):
    lines = tpl.split("\n")
    if lines and lines[-1] == "":
        lines = lines[:-1]
        closing_own_line = True
    else:
        closing_own_line = False
    out = []
    for l in lines:
        out.append("            " + l if l else "")
    body = "\n".join(out)
    if closing_own_line:
        return '            """\n' + body + '\n            """'
    return '            """\n' + body + '"""'

parts = []
parts.append("package cn.aslight.workhub.service.ops;\n")
parts.append("""
import cn.aslight.workhub.model.ops.FuncCallSqlEntity;

import java.util.List;

/**
 * REQ-9f3c2a84：Function Call 场景 SQL 种子。模板文本取自改造前 XxlJobMonitorCollector /
 * MqMonitorCollector 的在用 SQL 文本块（%n$s/%n$d 逐位替换为 :name 命名参数，MQ 分支保持
 * stripTrailing 语义）；由 FuncCallSchemaInitializer 幂等回写，页面维护后以库内定义优先。
 * 本文件由生成脚本产出，请勿手改模板内容；新增场景请走页面登记或补充种子后重新校验等价性。
 */
public final class FuncCallSqlSeeds {

    private FuncCallSqlSeeds() {
    }

    public record Seed(String sqlCode, String scenarioCode, int branchOrder, String name, String description,
                       String sqlTemplate, String paramDesc, String sourceModule) {

        FuncCallSqlEntity toEntity() {
            FuncCallSqlEntity entity = new FuncCallSqlEntity();
            entity.setSqlCode(sqlCode());
            entity.setScenarioCode(scenarioCode());
            entity.setBranchOrder(branchOrder());
            entity.setName(name());
            entity.setDescription(description());
            entity.setSqlTemplate(sqlTemplate());
            entity.setParamDesc(paramDesc());
            entity.setSourceModule(sourceModule());
            entity.setEnabled(true);
            entity.setRemark("FuncCallSqlSeeds 初始种子");
            return entity;
        }
    }

    public static final List<Seed> SEEDS = List.of(
""")
for i, (code, scenario, branch, name, desc, params, param_desc, module, tpl) in enumerate(records):
    parts.append("            new Seed(\"%s\", \"%s\", %d, \"%s\", \"%s\",\n" % (code, scenario, branch, name, desc))
    parts.append(java_textblock(tpl) + ",\n")
    params_desc = "参数：" + ", ".join(params) + "；" + param_desc
    parts.append("                    \"%s\", \"%s\")%s\n" % (params_desc, module, "," if i < len(records) - 1 else ""))
parts.append("    );\n}\n")

out_path = "/Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/workhub-service/src/main/java/cn/aslight/workhub/service/ops/FuncCallSqlSeeds.java"
open(out_path, "w", encoding="utf-8").write("".join(parts))
print(f"wrote {out_path} with {len(records)} seeds")
for code, scenario, branch, name, desc, params, param_desc, module, tpl in records:
    print(f"  {code:35s} scenario={scenario:25s} branch={branch} len={len(tpl)}")
