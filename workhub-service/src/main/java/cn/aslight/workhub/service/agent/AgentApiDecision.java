package cn.aslight.workhub.service.agent;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/** 仅用于模型 API 适配；原生 CLI 不使用本协议。计划字段是对用户可见的行动摘要。 */
public record AgentApiDecision(String type, String plan, String tool, JsonNode arguments, String summary) {
    public static AgentApiDecision parse(String output, ObjectMapper json) {
        if (output == null || output.length() > 24000) throw new IllegalArgumentException("行动输出为空或超过 24000 字符");
        String value = output.strip();
        if (value.startsWith("```json") && value.endsWith("```")) value = value.substring(7, value.length() - 3).strip();
        else if (value.startsWith("```") && value.endsWith("```")) value = value.substring(3, value.length() - 3).strip();
        JsonNode node = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
        if (!node.isObject()) throw new IllegalArgumentException("行动必须是 JSON 对象");
        Set<String> allowed = Set.of("type", "plan", "tool", "arguments", "summary");
        for (String name : node.propertyNames()) if (!allowed.contains(name)) throw new IllegalArgumentException("行动包含未知字段: " + name);
        String type = string(node, "type", 32, true);
        String plan = string(node, "plan", 2000, true);
        if ("TOOL_CALL".equals(type)) {
            String tool = string(node, "tool", 64, true);
            if (!node.path("arguments").isObject()) throw new IllegalArgumentException("工具参数必须是 JSON 对象");
            if (node.hasNonNull("summary")) throw new IllegalArgumentException("工具调用不能同时提交最终结果");
            return new AgentApiDecision(type, plan, tool, node.get("arguments"), null);
        }
        if (!Set.of("RESULT", "NEEDS_INPUT").contains(type)) throw new IllegalArgumentException("未知行动类型");
        if (node.hasNonNull("tool") || node.hasNonNull("arguments")) throw new IllegalArgumentException("最终结果不能夹带工具调用");
        return new AgentApiDecision(type, plan, null, null, string(node, "summary", 16000, true));
    }

    private static String string(JsonNode node, String name, int max, boolean required) {
        var value = node.get(name);
        if (value == null || !value.isString() || value.asText().isBlank() || value.asText().length() > max) {
            if (!required && value == null) return null;
            throw new IllegalArgumentException(name + " 必须为有效文本，长度不超过 " + max);
        }
        return value.asText();
    }
}
