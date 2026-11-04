package cn.aslight.workhub.service.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/** 正常返回与持久结果恢复使用同一执行端协议，不把来源结果误当作外层回执。 */
final class AgentToolReplies {
    private AgentToolReplies() { }

    static Map<String, Object> apiSuccess(AgentToolData data, Object value) {
        return Map.of("succeeded", true, "value", data.sanitize(value));
    }

    static Map<String, Object> nativeSuccess(ObjectMapper json, AgentToolData data, Object value, List<JsonNode> userUpdates) {
        return Map.of("isError", false, "content", List.of(Map.of("type", "text", "text",
                json.writeValueAsString(Map.of("result", data.sanitize(value), "userUpdates", userUpdates)))));
    }
}
