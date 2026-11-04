package cn.aslight.workhub.service.agent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Configuration(proxyBeanMethods = false)
public class AgentDeliveryTools {
    private final AgentDeliveryService service;
    private final AgentAccessService access;
    private final ObjectMapper json;
    public AgentDeliveryTools(AgentDeliveryService service, AgentAccessService access, ObjectMapper json) { this.service = service; this.access = access; this.json = json; }
    @Bean AgentTool agentPublishChangeTool() { return tool(false); }
    @Bean AgentTool agentDeliveryStatusTool() { return tool(true); }
    private AgentTool tool(boolean readOnly) {
        var properties = new LinkedHashMap<String, Object>(); properties.put("changeSetId", Map.of("type", "string", "maxLength", 36));
        if (!readOnly) properties.put("repositoryId", Map.of("type", "string", "maxLength", 64));
        var schema = json.valueToTree(Map.of("type", "object", "properties", properties, "required", properties.keySet(), "additionalProperties", false));
        return new AgentTool() {
            public String code() { return readOnly ? "change.delivery" : AgentDeliveryStore.TOOL; }
            public String description() { return readOnly ? "读取本清单各仓库远端交付的已保存状态；时间为上次实际核验，不代表已实测或已批准发布。"
                    : "按管理员绑定逐仓库推送本版候选分支并创建草稿MR，回读准确提交；同清单/仓库幂等。未知写入只查询已有分支/MR，未找到也不自动重发。无变更仓库只核对基线。不会合并或发布，需逐个完成清单所有仓库；实际测试仍单独要求。"; }
            public JsonNode inputSchema() { return schema; }
            public Set<String> permissions() { return readOnly ? Set.of("agent:code:use") : AgentDeliveryStore.PERMISSIONS; }
            public boolean readOnly() { return readOnly; }
            public Object execute(AgentToolContext context, JsonNode arguments) {
                try {
                    access.requireExecution(context, this);
                    var args = new AgentToolArguments(arguments, properties.keySet());
                    return readOnly ? service.list(context, args.required("changeSetId")) : service.publish(context, args.required("changeSetId"), args.required("repositoryId"));
                } catch (IllegalArgumentException | AccessDeniedException | AgentCodePolicy.Unavailable ex) { throw new Rejected(ex.getMessage()); }
            }
        };
    }
}
