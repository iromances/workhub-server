package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.tool.McpToolRegistry;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.service.intake.GitlabRepositoryService;
import cn.aslight.workhub.service.intake.IntakeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * HTTP-hosted MCP runtime backed by the live WorkHub resource catalog.
 */
@Service
public class McpRuntimeService {

    private final McpResourceService mcpResourceService;
    private final IntakeService intakeService;
    private final GitlabRepositoryService gitlabRepositoryService;
    private final ObjectMapper objectMapper;

    public McpRuntimeService(McpResourceService mcpResourceService,
                             IntakeService intakeService,
                             GitlabRepositoryService gitlabRepositoryService) {
        this.mcpResourceService = mcpResourceService;
        this.intakeService = intakeService;
        this.gitlabRepositoryService = gitlabRepositoryService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String handle(String rawRequest) {
        try {
            return objectMapper.writeValueAsString(handle(objectMapper.readTree(rawRequest)));
        } catch (IOException ex) {
            throw new IllegalArgumentException("MCP 请求不是合法 JSON", ex);
        }
    }

    public JsonNode handle(JsonNode request) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", request == null || !request.has("id") ? NullNode.getInstance() : request.get("id"));
        try {
            if (request == null || !request.has("id")) {
                throw new IllegalArgumentException("MCP 请求缺少 id");
            }
            String method = request.path("method").asText();
            JsonNode params = request.path("params");
            response.set("result", switch (method) {
                case "initialize" -> initializeResult();
                case "tools/list" -> toolsListResult();
                case "tools/call" -> toolsCallResult(params);
                default -> throw new IllegalArgumentException("不支持的 MCP 方法：" + method);
            });
        } catch (Exception ex) {
            response.set("error", errorNode(ex));
        }
        return response;
    }

    private ObjectNode initializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        ObjectNode capabilities = objectMapper.createObjectNode();
        capabilities.set("tools", objectMapper.createObjectNode());
        result.set("capabilities", capabilities);
        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "workhub-http-mcp-runtime");
        serverInfo.put("version", "0.1.0");
        result.set("serverInfo", serverInfo);
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", registry().listTools());
        return result;
    }

    private ObjectNode toolsCallResult(JsonNode params) {
        String name = requireText(params, "name");
        JsonNode arguments = params.path("arguments");
        Object toolResult = registry().call(name, arguments);
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode text = objectMapper.createObjectNode();
        text.put("type", "text");
        text.put("text", renderText(toolResult));
        content.add(text);
        result.set("content", content);
        return result;
    }

    private McpToolRegistry registry() {
        McpResourceCatalog catalog = objectMapper.convertValue(mcpResourceService.catalog(), McpResourceCatalog.class);
        McpToolRegistry registry = McpToolRegistry.firstVersion(catalog, objectMapper);
        registerIntakeTools(registry);
        registerGitlabTools(registry);
        return registry;
    }

    private void registerIntakeTools(McpToolRegistry registry) {
        registry.register("get_intake_detail", "Read one requirement-management intake detail by intakeId.",
                objectSchema(List.of("intakeId")),
                args -> intakeService.detail(requireLong(args, "intakeId"), null, false));
        registry.register("search_intakes", "Search requirement-management intakes by approvalCode and/or requirementName.",
                objectSchema(List.of(), "approvalCode", "requirementName", "limit"),
                this::searchIntakes);
    }

    private void registerGitlabTools(McpToolRegistry registry) {
        registry.register("sync_gitlab_group_repositories", "Clone or refresh all accessible repositories for a business line GitLab group into the local controlled cache.",
                objectSchema(List.of("businessLine")),
                this::syncGitlabGroupRepositories);
    }

    private Map<String, Object> searchIntakes(JsonNode args) {
        String approvalCode = optionalText(args, "approvalCode");
        String requirementName = optionalText(args, "requirementName");
        if (approvalCode == null && requirementName == null) {
            throw new IllegalArgumentException("审批编号或需求名称至少填写一个");
        }
        int limit = optionalInt(args, "limit", 10, 1, 50);
        List<IntakeSummaryResponse> matches = intakeService.list(null, requirementName, approvalCode, null, null, null, null)
                .stream()
                .limit(limit)
                .toList();
        return Map.of(
                "count", matches.size(),
                "limit", limit,
                "items", matches
        );
    }

    private Map<String, Object> syncGitlabGroupRepositories(JsonNode args) {
        GitlabRepositoryService.GitlabRepositoryBundle bundle =
                gitlabRepositoryService.resolveAndFetchGroup(requireText(args, "businessLine"));
        return Map.of(
                "gitlabGroupName", bundle.gitlabGroupName(),
                "localRoot", bundle.localRoot().toString(),
                "repositoryCount", bundle.repositories().size(),
                "repositories", bundle.repositories().stream()
                        .map(repository -> Map.of(
                                "repositoryUrl", repository.repositoryUrl(),
                                "localPath", repository.localPath().toString()
                        ))
                        .toList()
        );
    }

    private String renderText(Object value) {
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalArgumentException("MCP 工具结果无法序列化", ex);
        }
    }

    private ObjectNode errorNode(Exception ex) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", -32603);
        error.put("message", ex.getMessage());
        error.set("data", objectMapper.valueToTree(Map.of("exception", ex.getClass().getName())));
        return error;
    }

    private String requireText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw new IllegalArgumentException("MCP 参数缺少字段：" + field);
        }
        return value;
    }

    private Long requireLong(JsonNode node, String field) {
        String value = requireText(node, field);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("MCP 参数必须是数字：" + field, ex);
        }
    }

    private String optionalText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int optionalInt(JsonNode node, String field, int defaultValue, int min, int max) {
        String value = optionalText(node, field);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException("MCP 参数超出范围：" + field + "，允许范围 " + min + "-" + max);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("MCP 参数必须是数字：" + field, ex);
        }
    }

    private ObjectNode objectSchema(List<String> requiredFields, String... optionalFields) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
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
}
