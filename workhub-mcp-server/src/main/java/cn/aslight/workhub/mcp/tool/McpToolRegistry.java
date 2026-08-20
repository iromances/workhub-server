package cn.aslight.workhub.mcp.tool;

import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.db.DatabaseDiagnosticService;
import cn.aslight.workhub.mcp.security.CommandPolicyGuard;
import cn.aslight.workhub.mcp.security.SecretResolver;
import cn.aslight.workhub.mcp.security.SqlPolicyGuard;
import cn.aslight.workhub.mcp.server.ServerDiagnosticService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * First-version MCP tool registry.
 */
public class McpToolRegistry {

    private final ObjectMapper objectMapper;
    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    private McpToolRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static McpToolRegistry firstVersion(McpResourceCatalog catalog, ObjectMapper objectMapper) {
        McpToolRegistry registry = new McpToolRegistry(objectMapper);
        McpAuditLogger auditLogger = McpAuditLogger.defaultLogger(objectMapper);
        DatabaseDiagnosticService databaseService = new DatabaseDiagnosticService(
                catalog,
                new SqlPolicyGuard(),
                new SecretResolver(),
                auditLogger
        );
        ServerDiagnosticService serverService = new ServerDiagnosticService(
                catalog,
                new CommandPolicyGuard(),
                auditLogger
        );
        registry.register("list_mcp_targets", "List business lines, environments, database targets and server targets.",
                registry.emptySchema(), ignored -> registry.targetSummary(catalog));
        registry.register("get_business_line_context", "Get GitLab, system, knowledge, database and server context for a business line.",
                registry.objectSchema("businessLine"),
                args -> registry.businessLineContext(catalog, registry.requireText(args, "businessLine")));
        registry.register("get_knowledge_context", "Get project knowledge-base location and path conventions for a business line.",
                registry.objectSchema("businessLine"),
                args -> registry.knowledgeContext(catalog, registry.requireText(args, "businessLine")));
        registry.register("run_readonly_query", "Run a guarded read-only SQL query.",
                registry.objectSchema("targetKey", "profileKey", "sql"),
                args -> databaseService.runReadonlyQuery(
                        registry.requireText(args, "targetKey"),
                        registry.requireText(args, "profileKey"),
                        registry.requireText(args, "sql")
                ));
        registry.register("get_service_status", "Read status for a whitelisted service on a configured server target.",
                registry.objectSchema("targetKey", "profileKey", "service"),
                args -> serverService.getServiceStatus(
                        registry.requireText(args, "targetKey"),
                        registry.requireText(args, "profileKey"),
                        registry.requireText(args, "service")
                ));
        registry.register("read_service_logs", "Read recent logs from a whitelisted service or a log file below an allowed directory.",
                registry.objectSchema(List.of("targetKey", "profileKey"), "service", "logPath", "lines"),
                args -> serverService.readServiceLogs(
                        registry.requireText(args, "targetKey"),
                        registry.requireText(args, "profileKey"),
                        registry.optionalText(args, "service"),
                        registry.optionalText(args, "logPath"),
                        registry.optionalText(args, "lines")
                ));
        registry.register("search_service_logs", "Search one allowed plain-text or ZIP log file with a fixed string and a bounded match count.",
                registry.objectSchema(List.of("targetKey", "profileKey", "logPath", "keyword"), "maxMatches"),
                args -> serverService.searchServiceLogs(
                        registry.requireText(args, "targetKey"),
                        registry.requireText(args, "profileKey"),
                        registry.requireText(args, "logPath"),
                        registry.requireText(args, "keyword"),
                        registry.optionalText(args, "maxMatches")
                ));
        return registry;
    }

    public ArrayNode listTools() {
        ArrayNode nodes = objectMapper.createArrayNode();
        tools.values().forEach(tool -> {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("name", tool.name());
            node.put("description", tool.description());
            node.set("inputSchema", tool.inputSchema());
            nodes.add(node);
        });
        return nodes;
    }

    public Object call(String name, JsonNode arguments) {
        McpTool tool = tools.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("未知 MCP 工具：" + name);
        }
        return tool.handler().apply(arguments == null ? objectMapper.createObjectNode() : arguments);
    }

    public void register(String name, String description, JsonNode inputSchema, Function<JsonNode, Object> handler) {
        tools.put(name, new McpTool(name, description, inputSchema, handler));
    }

    private ObjectNode emptySchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    private ObjectNode objectSchema(String... requiredFields) {
        return objectSchema(List.of(requiredFields));
    }

    private ObjectNode objectSchema(List<String> requiredFields, String... optionalFields) {
        ObjectNode schema = emptySchema();
        ObjectNode properties = objectMapper.createObjectNode();
        ArrayNode required = objectMapper.createArrayNode();
        for (String field : requiredFields) {
            ObjectNode property = objectMapper.createObjectNode();
            property.put("type", "string");
            properties.set(field, property);
            required.add(field);
        }
        for (String field : optionalFields) {
            ObjectNode property = objectMapper.createObjectNode();
            property.put("type", "string");
            properties.set(field, property);
        }
        schema.set("properties", properties);
        schema.set("required", required);
        return schema;
    }

    private Map<String, Object> targetSummary(McpResourceCatalog catalog) {
        return Map.of(
                "businessLines", catalog.businessLines(),
                "environments", catalog.environments(),
                "databaseTargets", catalog.databaseTargets().stream()
                        .map(this::databaseTargetSummary)
                        .toList(),
                "serverTargets", catalog.serverTargets().stream()
                        .map(this::serverTargetSummary)
                        .toList(),
                "knowledge", catalog.knowledge(),
                "gitlab", catalog.gitlab()
        );
    }

    private Map<String, Object> businessLineContext(McpResourceCatalog catalog, String businessLine) {
        McpResourceCatalog.BusinessLine matched = findBusinessLine(catalog, businessLine);
        String code = matched.code();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("businessLine", businessLineSummary(matched));
        context.put("gitlab", gitlabContext(catalog, matched));
        context.put("knowledge", knowledgeContext(catalog, code));
        context.put("databaseTargets", catalog.databaseTargets().stream()
                .filter(target -> target.publicResource() || matchesBusinessLine(target.businessLineCodes(), matched))
                .map(this::databaseTargetSummary)
                .toList());
        context.put("serverTargets", catalog.serverTargets().stream()
                .filter(target -> target.publicResource() || matchesBusinessLine(target.businessLineCodes(), matched))
                .map(this::serverTargetSummary)
                .toList());
        return context;
    }

    private Map<String, Object> knowledgeContext(McpResourceCatalog catalog, String businessLine) {
        Map<String, Object> context = new LinkedHashMap<>(catalog.knowledge());
        Object vaultPath = context.get("projectVaultPath");
        if (vaultPath != null) {
            context.put("businessLineWikiRoot", vaultPath + "/wiki/projects/" + businessLine);
            context.put("businessLineRawRoot", vaultPath + "/raw/requirements/" + businessLine);
        }
        return context;
    }

    private Map<String, Object> gitlabContext(McpResourceCatalog catalog, McpResourceCatalog.BusinessLine businessLine) {
        Map<String, Object> context = new LinkedHashMap<>(catalog.gitlab());
        context.put("gitlabGroupName", businessLine.gitlabGroupName());
        if (businessLine.gitlabGroupName() != null && !businessLine.gitlabGroupName().isBlank()) {
            context.put("codeCacheRoot", "data/git-cache/" + businessLine.gitlabGroupName().trim());
        }
        return context;
    }

    private McpResourceCatalog.BusinessLine findBusinessLine(McpResourceCatalog catalog, String businessLine) {
        String normalized = normalizeText(businessLine);
        return catalog.businessLines().stream()
                .filter(item -> normalized.equals(normalizeText(item.code()))
                        || normalized.equals(normalizeText(item.name()))
                        || normalized.equals(normalizeText(item.gitlabGroupName())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("业务线不存在：" + businessLine));
    }

    private boolean matchesBusinessLine(List<String> values, McpResourceCatalog.BusinessLine businessLine) {
        List<String> aliases = List.of(
                nullToEmpty(businessLine.code()),
                nullToEmpty(businessLine.name()),
                nullToEmpty(businessLine.gitlabGroupName())
        ).stream()
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .toList();
        if (aliases.isEmpty()) {
            return false;
        }
        return values.stream()
                .map(this::normalizeText)
                .filter(value -> !value.isBlank())
                .anyMatch(aliases::contains);
    }

    private Map<String, Object> businessLineSummary(McpResourceCatalog.BusinessLine businessLine) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("code", businessLine.code());
        summary.put("name", businessLine.name());
        summary.put("gitlabGroupName", businessLine.gitlabGroupName());
        summary.put("involvedSystems", businessLine.involvedSystems());
        summary.put("globalSystems", businessLine.globalSystems());
        summary.put("enabled", businessLine.enabled());
        return summary;
    }

    private Map<String, Object> databaseTargetSummary(McpResourceCatalog.DatabaseTarget target) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("key", target.key());
        summary.put("publicResource", target.publicResource());
        summary.put("featureTags", target.featureTags());
        summary.put("businessLineCode", target.businessLineCode());
        summary.put("businessLineCodes", target.businessLineCodes());
        summary.put("environmentCode", target.environmentCode());
        summary.put("name", target.name());
        summary.put("systemName", target.systemName());
        summary.put("systemNames", target.systemNames());
        summary.put("schema", target.schema());
        summary.put("profiles", target.profiles().stream().map(McpResourceCatalog.DatabaseTarget.DatabaseProfile::key).toList());
        return summary;
    }

    private Map<String, Object> serverTargetSummary(McpResourceCatalog.ServerTarget target) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("key", target.key());
        summary.put("publicResource", target.publicResource());
        summary.put("featureTags", target.featureTags());
        summary.put("businessLineCode", target.businessLineCode());
        summary.put("businessLineCodes", target.businessLineCodes());
        summary.put("environmentCode", target.environmentCode());
        summary.put("name", target.name());
        summary.put("systemName", target.systemName());
        summary.put("systemNames", target.systemNames());
        summary.put("allowedServices", target.allowedServices());
        summary.put("allowedLogPaths", target.allowedLogPaths());
        summary.put("profiles", target.profiles().stream().map(McpResourceCatalog.ServerTarget.ServerProfile::key).toList());
        return summary;
    }

    private String requireText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw new IllegalArgumentException("MCP 工具参数缺少字段：" + field);
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.path(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record McpTool(String name,
                           String description,
                           JsonNode inputSchema,
                           Function<JsonNode, Object> handler) {
    }
}
