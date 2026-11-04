package cn.aslight.workhub.service.agent;

import tools.jackson.databind.JsonNode;
import java.util.Set;

/** 外部执行端的工具参数均视作不可信输入。 */
final class AgentToolArguments {
    private final JsonNode value;

    AgentToolArguments(JsonNode value, Set<String> allowed) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("工具参数必须为对象");
        for (var property : value.properties()) {
            if (!allowed.contains(property.getKey())) throw new IllegalArgumentException("未知工具参数: " + property.getKey());
        }
        this.value = value;
    }

    String required(String name) {
        String text = optional(name);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("工具参数不能为空: " + name);
        return text;
    }

    String optional(String name) {
        JsonNode node = value.get(name);
        if (node == null || node.isNull()) return null;
        if (!node.isString() || node.asText().length() > 500) throw new IllegalArgumentException("工具参数必须为不超过500字的字符串: " + name);
        return node.asText().trim();
    }

    String content(String name, int max) {
        JsonNode node = value.get(name);
        if (node == null || !node.isString() || node.asText().length() > max) throw new IllegalArgumentException("文本参数缺失或超过上限: " + name);
        return node.asText();
    }

    long version(String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() < 0) throw new IllegalArgumentException("版本参数必须为非负整数: " + name);
        return node.asLong();
    }

    JsonNode object(String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isObject()) throw new IllegalArgumentException("对象参数缺失或格式错误: " + name);
        return node;
    }

    JsonNode array(String name, int max) {
        JsonNode node = value.get(name);
        if (node == null || !node.isArray() || node.size() > max) throw new IllegalArgumentException("列表参数缺失或超过上限: " + name);
        return node;
    }

    long id(String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() < 1) {
            throw new IllegalArgumentException("工具参数必须为正整数: " + name);
        }
        return node.asLong();
    }

    Long optionalId(String name) { return has(name) ? id(name) : null; }

    long position(String name) { return has(name) ? version(name) : 0; }

    boolean bool(String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isBoolean()) throw new IllegalArgumentException("布尔参数缺失或格式错误: " + name);
        return node.asBoolean();
    }

    boolean has(String name) { return value.hasNonNull(name); }

    int bounded(String name, int defaultValue, int min, int max) {
        JsonNode node = value.get(name);
        if (node == null || node.isNull()) return defaultValue;
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() < min || node.asInt() > max) {
            throw new IllegalArgumentException("工具参数超出范围: " + name);
        }
        return node.asInt();
    }
}
