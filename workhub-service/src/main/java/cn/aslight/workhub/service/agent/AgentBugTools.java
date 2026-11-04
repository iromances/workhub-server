package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMonitorReadMapper;
import cn.aslight.workhub.service.autonomy.AutonomyEvidence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** 读取已登记缺陷作为调查线索；不猜测运行环境或改动人工工作项。 */
@Configuration(proxyBeanMethods = false)
public class AgentBugTools {
    private final AgentMonitorReadMapper mapper;
    private final AgentAccessService access;
    private final AgentToolData data;
    private final ObjectMapper json;

    public AgentBugTools(AgentMonitorReadMapper mapper, AgentAccessService access, AgentToolData data, ObjectMapper json) {
        this.mapper = mapper; this.access = access; this.data = data; this.json = json;
    }

    @Bean
    AgentTool agentBugListTool() {
        return tool("workhub.bugs.list", "读取指定业务线已登记的缺陷工作项，默认全部状态，可用 status 筛选及 beforeWorkItemId 续页。记录没有环境字段，需继续定位。",
                Map.of("businessLineCode", text(), "status", text(), "beforeWorkItemId", number(0, Long.MAX_VALUE), "pageSize", number(1, 20)),
                List.of("businessLineCode"), (context, args) -> {
                    String line = args.required("businessLineCode");
                    long before = args.position("beforeWorkItemId");
                    int size = args.bounded("pageSize", 10, 1, 20);
                    access.requireBusinessLine(context, line);
                    var records = mapper.bugs(line, args.optional("status"), before, size + 1);
                    boolean more = records.size() > size;
                    var page = List.copyOf(records.subList(0, Math.min(records.size(), size)));
                    return response(line, Map.of("items", page, "hasMore", more, "nextBeforeWorkItemId", more ? page.getLast().id() : 0L));
                });
    }

    @Bean
    AgentTool agentBugDetailTool() {
        return tool("workhub.bugs.detail", "读取业务线内缺陷的实际描述与验收要求；须结合授权日志/监测确认环境，不能以工作项状态证明修复或上线成功。",
                Map.of("businessLineCode", text(), "workItemId", number(1, Long.MAX_VALUE)), List.of("businessLineCode", "workItemId"), (context, args) -> {
                    String line = args.required("businessLineCode");
                    long id = args.id("workItemId");
                    access.requireBusinessLine(context, line);
                    var record = mapper.bug(line, id);
                    if (record == null) throw new IllegalArgumentException("该业务线中不存在所指定的缺陷工作项");
                    var result = new LinkedHashMap<>(response(line, record));
                    result.put("workItemVersion", AutonomyEvidence.digest(json.writeValueAsString(record)));
                    return result;
                });
    }

    private AgentTool tool(String code, String description, Map<String, Object> properties, List<String> required,
                           BiFunction<AgentToolContext, AgentToolArguments, Object> action) {
        return new AgentTool() {
            public String code() { return code; }
            public String description() { return description; }
            public JsonNode inputSchema() { return json.valueToTree(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false)); }
            public Set<String> permissions() { return Set.of("work-item:view"); }
            public boolean readOnly() { return true; }
            public Object execute(AgentToolContext context, JsonNode arguments) {
                access.requireExecution(context, this);
                return data.sanitize(action.apply(context, new AgentToolArguments(arguments, properties.keySet())));
            }
        };
    }

    private Map<String, Object> response(String line, Object value) {
        return Map.of("sourceType", "BUG", "businessLineCode", line, "environmentState", "UNRESOLVED",
                "observedAt", Instant.now().toString(), "data", value,
                "evidenceUsage", "已登记工作项线索，尚非已定位环境的巡检证据。先结合实际日志/监测或人工补充定位，不自动关闭工作项或宣称发布成功。");
    }
    private Map<String, Object> text() { return Map.of("type", "string", "maxLength", 500); }
    private Map<String, Object> number(long min, long max) { return Map.of("type", "integer", "minimum", min, "maximum", max); }
}
