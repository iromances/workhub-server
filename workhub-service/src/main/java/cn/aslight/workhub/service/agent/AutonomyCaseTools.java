package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentScope;
import cn.aslight.workhub.model.autonomy.AutonomyProposal;
import cn.aslight.workhub.service.autonomy.AutonomyCaseService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.BiFunction;

/** API 和 CLI 共享问题登记与协作能力；工具不能关闭问题或批准发布。 */
@Configuration(proxyBeanMethods = false)
public class AutonomyCaseTools {
    private final AutonomyCaseService service;
    private final AgentAccessService access;
    private final ObjectMapper json;
    public AutonomyCaseTools(AutonomyCaseService service, AgentAccessService access, ObjectMapper json) {
        this.service = service; this.access = access; this.json = json;
    }

    @Bean AgentTool autonomyCaseRecordTool() {
        return tool("case.record", "引用本次监测工具返回的 observationId 和具体 JSON Pointer 登记问题候选；来源/范围由平台取证。重复证据归入原问题。STALE_DATA 的阈值记作 AI 建议，不能冒充已配置规则。", false,
                Map.of("observationId", string(36), "pointer", string(256), "category", Map.of("type", "string", "enum", List.of(
                        "MONITORING_GAP", "EXECUTION_FAILURE", "STALE_DATA", "DATA_DISCREPANCY", "APPLICATION_ERROR", "PERFORMANCE")),
                        "title", string(200), "staleAfterSeconds", integer(60, 2_592_000)),
                List.of("observationId", "pointer", "category", "title"), (context, args) -> {
                    int threshold = args.bounded("staleAfterSeconds", 0, 60, 2_592_000);
                    return service.record(context, args.required("observationId"), args.required("pointer"), args.required("category"),
                            args.required("title"), threshold == 0 ? null : (long) threshold);
                });
    }
    @Bean AgentTool autonomyCaseListTool() {
        return tool("case.list", "查询授权业务线和环境已有问题，避免重复分析或修复；只展示仍有来源权限的问题。", true,
                Map.of("businessLineCode", string(32), "environmentCode", string(64), "page", integer(1, 10000), "pageSize", integer(1, 50)),
                List.of("businessLineCode", "environmentCode"), (context, args) -> service.list(context,
                        new AgentScope(args.required("businessLineCode"), args.required("environmentCode")), args.bounded("page", 1, 1, 10000), args.bounded("pageSize", 20, 1, 50)));
    }
    @Bean AgentTool autonomyCaseDetailTool() {
        return tool("case.detail", "SUMMARY 读取问题处理版本、证据/方案总数及关联运行；EVIDENCE 或 PROPOSALS 分别分页读取完整证据或 AI 方案，输出截断时减小 pageSize 到1。方案不是验证或发布结果。", true,
                Map.of("caseId", string(36), "section", Map.of("type", "string", "enum", List.of("SUMMARY", "EVIDENCE", "PROPOSALS")),
                        "evidencePage", integer(1, 10000), "proposalPage", integer(1, 10000), "pageSize", integer(1, 10)), List.of("caseId"),
                (context, args) -> {
                    String section = args.optional("section");
                    if (section == null) section = "SUMMARY";
                    if (!Set.of("SUMMARY", "EVIDENCE", "PROPOSALS").contains(section)) throw new IllegalArgumentException("问题详情分区无效");
                    var result = service.detail(context, args.required("caseId"), args.bounded("evidencePage", 1, 1, 10000),
                            args.bounded("proposalPage", 1, 1, 10000), args.bounded("pageSize", 1, 1, 10));
                    return switch (section) {
                        case "EVIDENCE" -> Map.of("caseId", result.caseInfo().id(), "signals", result.signals());
                        case "PROPOSALS" -> Map.of("caseId", result.caseInfo().id(), "proposals", result.proposals(), "source", "AI_ANALYSIS");
                        default -> Map.of("caseInfo", result.caseInfo(), "evidenceTotal", result.signals().total(), "proposalTotal", result.proposals().total(),
                                "relatedRuns", result.relatedRuns(), "relatedRunTotal", result.relatedRunTotal());
                    };
                });
    }
    @Bean AgentTool autonomyCaseClaimTool() {
        return tool("case.claim", "以问题当前 version 领取唯一分析/修复运行；claimed=false 表示已有活动运行，应附证据并查看进度，避免另起修复。未知外部执行不会被租约过期释放。", false,
                Map.of("caseId", string(36), "version", integer(0, Long.MAX_VALUE)), List.of("caseId", "version"),
                (context, args) -> service.claim(context, args.required("caseId"), args.version("version")));
    }
    @Bean AgentTool autonomyCaseProposeTool() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("caseId", string(36)); properties.put("version", integer(0, Long.MAX_VALUE)); properties.put("requestKey", string(64));
        properties.put("facts", string(4000)); properties.put("hypothesis", string(4000)); properties.put("impact", string(2000));
        properties.put("plan", string(6000)); properties.put("acceptance", string(3000)); properties.put("rollback", string(2000)); properties.put("unknowns", string(2000));
        properties.put("changeType", Map.of("type", "string", "enum", List.of("CODE", "CONFIGURATION", "DATA", "INVESTIGATION")));
        return tool("case.propose", "领取后以当前处理版本保存不可变 AI 分析：事实、假设、影响、方案、验收、回退、未知项和变更类型。更改内容需新 requestKey；保存不会批准发布或关闭问题。", false,
                properties, List.copyOf(properties.keySet()), (context, args) -> service.propose(context, args.required("caseId"), args.version("version"), args.required("requestKey"),
                        new AutonomyProposal(args.content("facts", 4000), args.content("hypothesis", 4000), args.content("impact", 2000), args.content("plan", 6000),
                                args.content("acceptance", 3000), args.content("rollback", 2000), args.content("unknowns", 2000), args.required("changeType"))));
    }

    @Bean AgentTool autonomyCaseLinkBugTool() {
        return tool("case.link_bug", "领取问题后，引用缺陷 detail 的 workItemVersion（或 inspection.next 的 sourceVersion）及本次真实监测证据关联缺陷。需 work-item:view；保存的是 AI 判断，环境沿用证据限制。新缺陷版本使旧方案失效；同一当前版本复用原关联。", false,
                Map.of("caseId", string(36), "version", integer(0, Long.MAX_VALUE), "requestKey", string(64),
                        "workItemId", integer(1, Long.MAX_VALUE), "workItemVersion", string(64), "observationId", string(36),
                        "pointer", string(256), "reason", string(2000), "staleAfterSeconds", integer(60, 2_592_000)),
                List.of("caseId", "version", "requestKey", "workItemId", "workItemVersion", "observationId", "pointer", "reason"), (context, args) -> {
                    int threshold = args.bounded("staleAfterSeconds", 0, 60, 2_592_000);
                    return service.linkBug(context, args.required("caseId"), args.version("version"), args.required("requestKey"),
                            args.id("workItemId"), args.required("workItemVersion"), args.required("observationId"), args.required("pointer"),
                            args.content("reason", 2000), threshold == 0 ? null : (long) threshold);
                });
    }
    @Bean AgentTool autonomyCaseBugsTool() {
        return tool("case.bugs", "分页读取该问题的缺陷关联历史及 CURRENT/CHANGED/UNAVAILABLE 原文状态；latest 标明每个缺陷最新关联。关联为 AI 判断，需 work-item:view。输出截断时将 pageSize 减至1。", true,
                Map.of("caseId", string(36), "page", integer(1, 10000), "pageSize", integer(1, 5)), List.of("caseId"),
                (context, args) -> service.bugs(context, args.required("caseId"), args.bounded("page", 1, 1, 10000), args.bounded("pageSize", 1, 1, 5)));
    }

    private AgentTool tool(String code, String description, boolean readOnly, Map<String, Object> properties, List<String> required,
                            BiFunction<AgentToolContext, AgentToolArguments, Object> action) {
        var schema = json.valueToTree(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false));
        return new AgentTool() {
            public String code() { return code; }
            public String description() { return description; }
            public JsonNode inputSchema() { return schema; }
            public Set<String> permissions() { return readOnly ? Set.of("autonomy:case:view", "autonomy:case:operate") : Set.of("autonomy:case:operate"); }
            public boolean readOnly() { return readOnly; }
            public Object execute(AgentToolContext context, JsonNode arguments) {
                try {
                    access.requireExecution(context, this);
                    return action.apply(context, new AgentToolArguments(arguments, properties.keySet()));
                } catch (IllegalArgumentException | AccessDeniedException ex) { throw new Rejected(ex.getMessage()); }
            }
        };
    }
    private Map<String, Object> string(int max) { return Map.of("type", "string", "maxLength", max); }
    private Map<String, Object> integer(long min, long max) { return Map.of("type", "integer", "minimum", min, "maximum", max); }
}
