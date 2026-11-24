package cn.aslight.workhub.service.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** A single contract for generation, validation and persistence. No model calls during parsing. */
public final class AgentReportContract {
    private static final ObjectMapper JSON = new ObjectMapper();
    public static final String PRESENTATION_GUIDE = "报告使用简明中文，不使用冗长比喻。report.conclusion 先用一句话给出总体结论，"
            + "再将重点问题、待核实事项分别换行列出；不重复检查明细、样例、术语长解释或证据全文。"
            + "完整事实分别写入 checks/findings/evidence/actions，不截断或省略风险和不确定性。"
            + "每种情况、每个例子独立换行，以清晰序号组织；结论、分析、影响、建议和验收条件均遵守此规则。"
            + "全量分析且没有统一起止范围时，scope.window.from/to 可为 null，basis 明确写出 FULL 全量分析、不限制时间范围，"
            + "并分行列出各来源实际覆盖范围；不能把执行时刻或局部抽样窗口当作整体数据范围。";
    public static final String SCHEMA;
    private static final JsonNode CONTRACT;
    private static final Pattern FENCE = Pattern.compile("(?m)^\\s*```(?:json)?[ \\t]*\\R([\\s\\S]*?)^\\s*```[ \\t]*$");
    static {
        try (var input = AgentReportContract.class.getResourceAsStream("/agent/report-v2.schema.json")) {
            if (input == null) throw new IllegalStateException("报告契约资源缺失");
            SCHEMA = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            CONTRACT = JSON.readTree(SCHEMA);
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private AgentReportContract() {}

    public static JsonNode extract(String raw) {
        try { var result = read(raw.strip()); if (result == null) throw invalid("$", "空结果"); return result; }
        catch (RuntimeException notWholeJson) {
            var matcher = FENCE.matcher(raw);
            String candidate = null;
            int count = 0;
            while (matcher.find()) { candidate = matcher.group(1); count++; }
            if (count != 1 || raw.lines().filter(line -> line.strip().startsWith("```")).count() != 2)
                throw invalid("$", "必须返回单个完整 JSON；无法唯一提取报告");
            try { return read(candidate); }
            catch (RuntimeException badJson) { throw invalid("$", "JSON 不完整或语法错误"); }
        }
    }
    private static JsonNode read(String text) {
        return JSON.reader().with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
    }
    public static boolean isV2(JsonNode result) { return result.has("schemaVersion") || result.has("report"); }
    public static String summary(JsonNode result) {
        return isV2(result) ? result.path("report").path("conclusion").asText() : result.path("summary").asText();
    }
    public static void validate(JsonNode result) {
        if (!isV2(result)) {
            if (!result.isObject() || result.properties().stream().anyMatch(e -> !Set.of("type", "summary", "deliveries", "problems").contains(e.getKey()))
                    || (result.has("problems") && !result.path("problems").isArray())
                    || (result.has("deliveries") && !result.path("deliveries").isArray())
                    || !Set.of("RESULT", "NEEDS_INPUT").contains(result.path("type").asText())
                    || !result.path("summary").isString() || result.path("summary").asText().isBlank())
                throw invalid("$", "旧版结果缺少有效 type/summary");
            return;
        }
        validateNode(result, CONTRACT, "$");
        var report = result.path("report");
        var checks = keys(report.path("checks"), "checks");
        var findings = keys(report.path("findings"), "findings");
        keys(report.path("evidence"), "evidence"); keys(report.path("actions"), "actions");
        for (var e : report.path("evidence")) {
            String type = e.path("targetType").asText();
            if ("REPORT".equals(type) ? !e.path("targetKey").isNull()
                    : !("CHECK".equals(type) ? checks : findings).contains(e.path("targetKey").asText()))
                throw invalid("$.report.evidence." + e.path("key").asText(), "证据引用不属于本报告");
        }
        for (var a : report.path("actions")) if (!a.path("findingKey").isNull() && !findings.contains(a.path("findingKey").asText()))
            throw invalid("$.report.actions." + a.path("key").asText(), "问题引用不存在");
        if ("NORMAL".equals(report.path("assessment").asText())) {
            if (report.path("checks").isEmpty() || !report.path("limitations").isEmpty())
                throw invalid("$.report.assessment", "缺少检查或存在未验证事项，不能认定正常");
            for (var c : report.path("checks")) if (!Set.of("PASS", "NOT_APPLICABLE").contains(c.path("outcome").asText()))
                throw invalid("$.report.assessment", "检查未通过或未验证，不能认定正常");
            for (var f : report.path("findings")) if (!"RESOLVED".equals(f.path("disposition").asText()))
                throw invalid("$.report.assessment", "存在未解决或未核实问题，不能认定正常");
        }
    }
    public static JsonNode problems(JsonNode result) {
        if (!isV2(result)) return result.path("problems");
        if (!"INSPECTION".equals(result.path("report").path("reportType").asText())) return tools.jackson.databind.node.MissingNode.getInstance();
        var problems = JSON.createArrayNode();
        for (var f : result.path("report").path("findings")) {
            if (!Set.of("SYSTEM_ALERT", "BUSINESS_MONITOR", "ACCOUNTING").contains(f.path("domain").asText())
                    || f.path("signature").asText().isBlank() || "RESOLVED".equals(f.path("disposition").asText())
                    || !"VERIFIED".equals(f.path("verificationStatus").asText())
                    || !Set.of("P0", "P1", "P2").contains(f.path("severity").asText())) continue;
            var p = JSON.createObjectNode();
            for (String name : List.of("domain", "signature", "title", "severity")) p.set(name, f.path(name));
            var evidence = new StringBuilder();
            for (var e : result.path("report").path("evidence"))
                if ("FINDING".equals(e.path("targetType").asText()) && f.path("key").equals(e.path("targetKey")))
                    evidence.append(e.path("source").asText()).append("：").append(e.path("result").asText()).append("\n");
            p.put("evidence", evidence.toString());
            p.set("analysis", JSON.createObjectNode().put("repro", f.path("trigger").asText())
                    .put("rootCause", f.path("analysis").asText()).put("impactVerification", f.path("impact").asText())
                    .put("fixDirection", f.path("recommendation").asText()));
            problems.add(p);
        }
        return problems;
    }
    private static Set<String> keys(JsonNode items, String field) {
        var keys = new HashSet<String>();
        for (var item : items) if (!keys.add(item.path("key").asText())) throw invalid("$.report." + field, "key 重复");
        return keys;
    }
    private static void validateNode(JsonNode value, JsonNode schema, String path) {
        var types = schema.path("type");
        boolean matched = types.isArray() ? java.util.stream.StreamSupport.stream(types.spliterator(), false).anyMatch(t -> type(value, t.asText())) : type(value, types.asText());
        if (!matched) throw invalid(path, "类型错误");
        if (schema.has("enum")) {
            boolean found = false;
            for (var item : schema.path("enum")) found |= value.equals(item);
            if (!found) throw invalid(path, "枚举值非法");
        }
        if (value.isString()) {
            if (schema.has("minLength") && value.asText().isBlank()) throw invalid(path, "不能为空");
            if (schema.has("maxLength") && value.asText().length() > schema.path("maxLength").asInt()) throw invalid(path, "标识长度超限");
            if (schema.has("pattern") && !value.asText().matches(schema.path("pattern").asText())) throw invalid(path, "格式错误");
        }
        if (value.isObject()) {
            for (var required : schema.path("required")) if (!value.has(required.asText())) throw invalid(path + "." + required.asText(), "缺少字段");
            for (var field : value.properties()) {
                var contract = schema.path("properties").path(field.getKey());
                if (contract.isMissingNode()) throw invalid(path + "." + field.getKey(), "未知字段");
                validateNode(field.getValue(), contract, path + "." + field.getKey());
            }
        }
        if (value.isArray()) for (int i = 0; i < value.size(); i++) validateNode(value.get(i), schema.path("items"), path + "[" + i + "]");
    }
    private static boolean type(JsonNode v, String type) {
        return switch (type) {
            case "object" -> v.isObject(); case "array" -> v.isArray(); case "string" -> v.isString();
            case "integer" -> v.isIntegralNumber(); case "null" -> v.isNull(); default -> false;
        };
    }
    private static IllegalArgumentException invalid(String path, String reason) {
        return new IllegalArgumentException("报告结构校验失败 " + path + "：" + reason + "；原始答复保留，可单独恢复");
    }
}
