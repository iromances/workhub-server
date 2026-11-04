package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.service.agent.AgentInspectionService.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
public class AgentInspectionTools {
    private final AgentInspectionService service;
    private final AgentAccessService access;
    private final ObjectMapper json;
    public AgentInspectionTools(AgentInspectionService service, AgentAccessService access, ObjectMapper json) {
        this.service = service; this.access = access; this.json = json;
    }

    @Bean
    AgentTool agentInspectionNextTool() {
        var properties = scopeProperties(); properties.put("retryItemId", number(1));
        return tool("inspection.next", "继续本任务持久化巡检，来源 SYSTEM_ALERT/ACCOUNTING_RUN/BUG。每次取得一条待分析快照；先 ack 再取下一条。重复读取不会跳过未确认记录。BUG 不传环境。retryItemId 显式重读待补充记录。", false,
                properties, List.of("source", "businessLineCode"), (context, args) -> service.next(context, source(args), args.required("businessLineCode"),
                        args.optional("environmentCode"), args.optionalId("retryItemId")));
    }
    @Bean
    AgentTool agentInspectionAckTool() {
        return tool("inspection.ack", "确认已分析一条巡检快照：NO_FINDING/FINDING/NEEDS_INPUT，须说明理由。FINDING 先 case.record 并给 caseId；账务规则差异另提供对应 detail 的 findingObservationId/findingPointer。确认不代表修复。", false,
                Map.of("itemId", number(1), "outcome", Map.of("type", "string", "enum", List.of("NO_FINDING", "FINDING", "NEEDS_INPUT")),
                        "reason", Map.of("type", "string", "maxLength", 2000), "caseId", text(), "findingObservationId", text(), "findingPointer", text()),
                List.of("itemId", "outcome", "reason"), (context, args) -> service.acknowledge(context, args.id("itemId"), args.required("outcome"),
                        args.content("reason", 2000), args.optional("caseId"), args.optional("findingObservationId"), args.optional("findingPointer")));
    }
    @Bean
    AgentTool agentInspectionPendingTool() {
        var properties = scopeProperties(); properties.put("afterItemId", number(0));
        return tool("inspection.pending", "读取本任务同一来源范围待补充的分析及续页位置；有新线索后以 inspection.next 的 retryItemId 重新领取。", true,
                properties, List.of("source", "businessLineCode"), (context, args) -> service.pending(context, source(args), args.required("businessLineCode"),
                        args.optional("environmentCode"), args.position("afterItemId")));
    }
    private AgentTool tool(String code, String description, boolean readOnly, Map<String, Object> properties, List<String> required,
                           BiFunction<AgentToolContext, AgentToolArguments, JsonNode> action) {
        return new AgentTool() {
            public String code() { return code; }
            public String description() { return description; }
            public JsonNode inputSchema() { return json.valueToTree(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false)); }
            public Set<String> permissions() { return Arrays.stream(Source.values()).flatMap(source -> source.permissions().stream()).collect(Collectors.toUnmodifiableSet()); }
            public boolean readOnly() { return readOnly; }
            public Object execute(AgentToolContext context, JsonNode arguments) {
                try {
                    access.requireExecution(context, this);
                    return action.apply(context, new AgentToolArguments(arguments, properties.keySet()));
                } catch (IllegalArgumentException | AccessDeniedException ex) { throw new AgentTool.Rejected(ex.getMessage()); }
            }
        };
    }
    private Source source(AgentToolArguments args) {
        try { return Source.valueOf(args.required("source")); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("巡检来源必须为 SYSTEM_ALERT、ACCOUNTING_RUN 或 BUG"); }
    }
    private Map<String, Object> scopeProperties() {
        return new HashMap<>(Map.of("source", Map.of("type", "string", "enum", Arrays.stream(Source.values()).map(Enum::name).toList()),
                "businessLineCode", text(), "environmentCode", text()));
    }
    private Map<String, Object> text() { return Map.of("type", "string", "maxLength", 500); }
    private Map<String, Object> number(int minimum) { return Map.of("type", "integer", "minimum", minimum); }
}
