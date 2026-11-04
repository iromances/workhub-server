package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.mcp.security.SensitiveDataMasker;
import cn.aslight.workhub.mcp.security.ServerFileContentMasker;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/** 在进入模型、运行记录或 CLI 前统一脱敏；过大结果明确标注截断。 */
@Component
public class AgentToolData {
    private static final int MAX_RESULT_CHARS = 48_000;
    private final ObjectMapper json;
    private final SensitiveDataMasker fields = new SensitiveDataMasker();
    private final ServerFileContentMasker text = new ServerFileContentMasker();

    public AgentToolData(ObjectMapper json) { this.json = json; }

    public JsonNode sanitize(Object value) {
        JsonNode masked = mask("", json.valueToTree(value), 0);
        String encoded = json.writeValueAsString(masked);
        if (encoded.length() <= MAX_RESULT_CHARS) return masked;
        ObjectNode result = json.createObjectNode();
        result.put("truncated", true);
        result.put("message", "结果超出单次工具输出上限，请缩小范围或分页；不能据此判定全量健康。");
        // 预留 JSON 再编码和证据引用空间，二次脱敏不能把 observationId 再截掉。
        result.put("preview", encoded.substring(0, 16_000));
        return result;
    }

    private JsonNode mask(String key, JsonNode node, int depth) {
        if (depth > 24) throw new IllegalArgumentException("工具数据嵌套过深");
        if ("******".equals(fields.mask(key, key, "field-probe"))) return StringNode.valueOf("******");
        if (node.isObject()) {
            ObjectNode result = json.createObjectNode();
            node.properties().forEach(entry -> result.set(entry.getKey(), mask(entry.getKey(), entry.getValue(), depth + 1)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = json.createArrayNode();
            node.forEach(item -> result.add(mask(key, item, depth + 1)));
            return result;
        }
        if (node.isNull()) return node;
        String original = node.asText();
        String fieldMasked = String.valueOf(fields.mask(key, key, original));
        if (!original.equals(fieldMasked)) return StringNode.valueOf(fieldMasked);
        if (!node.isString()) return node;
        // 平台摘要可能恰好含手机号形状的数字段；保持64位摘要协议，正文和敏感字段仍正常脱敏。
        if ((key.endsWith("Hash") || java.util.Set.of("sourceVersion", "workItemVersion", "latestEventId", "sourceEventId").contains(key))
                && original.matches("[a-f0-9]{64}")) return node;
        // sampleJson 等历史文本列也要按字段脱敏，避免嵌套样本的银行卡等字段漏过。
        if (key.toLowerCase(java.util.Locale.ROOT).endsWith("json") && (original.startsWith("{") || original.startsWith("["))) {
            try {
                return mask(key, json.readTree(original), depth + 1);
            } catch (tools.jackson.core.JacksonException ex) {
                return StringNode.valueOf(text.mask(original));
            }
        }
        return StringNode.valueOf(text.mask(original));
    }
}
