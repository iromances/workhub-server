package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.tool.McpToolRegistry;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        registerIntakeTools(registry, catalog);
        registerGitlabTools(registry);
        return registry;
    }

    private void registerIntakeTools(McpToolRegistry registry, McpResourceCatalog catalog) {
        registry.register("get_intake_detail", "Read one requirement-management intake detail by intakeId.",
                objectSchema(List.of("intakeId")),
                args -> intakeService.detail(requireLong(args, "intakeId"), null, false));
        registry.register("search_intakes", "Search requirement-management intakes by approvalCode, requirementName and/or keyword in requirement digest/summary.",
                objectSchema(List.of(), "approvalCode", "requirementName", "keyword", "limit"),
                this::searchIntakes);
        registry.register("get_requirement_test_context", "Resolve a requirement by intakeId, approvalCode, requirementName or summary keyword, then return business-line, branch, SERVER/DB and GitLab/knowledge context for testing.",
                objectSchema(List.of(), "intakeId", "approvalCode", "requirementName", "keyword", "businessLine", "environmentCode", "systemName", "limit"),
                args -> requirementTestContext(args, catalog));
        registry.register("get_business_line_test_context", "Return SERVER/DB, GitLab, knowledge and involved-system context for testing a business line in one environment.",
                objectSchema(List.of("businessLine"), "environmentCode", "systemName"),
                args -> businessLineTestContext(args, catalog));
    }

    private void registerGitlabTools(McpToolRegistry registry) {
        registry.register("sync_gitlab_group_repositories", "Clone or refresh all accessible repositories for a business line GitLab group into the local controlled cache.",
                objectSchema(List.of("businessLine")),
                this::syncGitlabGroupRepositories);
    }

    private Map<String, Object> searchIntakes(JsonNode args) {
        String approvalCode = optionalText(args, "approvalCode");
        String requirementName = optionalText(args, "requirementName");
        String keyword = optionalText(args, "keyword");
        if (approvalCode == null && requirementName == null && keyword == null) {
            throw new IllegalArgumentException("审批编号、需求名称或摘要关键词至少填写一个");
        }
        int limit = optionalInt(args, "limit", 10, 1, 50);
        List<IntakeSummaryResponse> matches = searchIntakeCandidates(approvalCode, requirementName, keyword, limit);
        return Map.of(
                "count", matches.size(),
                "limit", limit,
                "items", matches
        );
    }

    private Map<String, Object> requirementTestContext(JsonNode args, McpResourceCatalog catalog) {
        String intakeId = optionalText(args, "intakeId");
        String approvalCode = optionalText(args, "approvalCode");
        String requirementName = optionalText(args, "requirementName");
        String keyword = optionalText(args, "keyword");
        if (intakeId == null && approvalCode == null && requirementName == null && keyword == null) {
            throw new IllegalArgumentException("intakeId、审批编号、需求名称或摘要关键词至少填写一个");
        }

        IntakeDetailResponse detail;
        if (intakeId != null) {
            detail = intakeService.detail(requireLong(args, "intakeId"), null, false);
        } else {
            int limit = optionalInt(args, "limit", 20, 1, 50);
            List<IntakeSummaryResponse> matches = searchIntakeCandidates(approvalCode, requirementName, keyword, limit);
            IntakeSummaryResponse selected = selectSingleRequirement(matches, approvalCode);
            if (selected == null) {
                Map<String, Object> unresolved = new LinkedHashMap<>();
                unresolved.put("resolutionStatus", matches.isEmpty() ? "NOT_FOUND" : "AMBIGUOUS");
                unresolved.put("criteria", requirementSearchCriteria(approvalCode, requirementName, keyword));
                unresolved.put("count", matches.size());
                unresolved.put("items", matches);
                return unresolved;
            }
            detail = intakeService.detail(selected.id(), null, false);
        }
        if (detail == null) {
            throw new IllegalArgumentException("需求详情不存在");
        }

        IntakeStructuredData structuredData = detail.structuredData();
        String businessLine = firstNonBlank(
                optionalText(args, "businessLine"),
                structuredData == null ? null : structuredData.businessLineCode(),
                structuredData == null ? null : structuredData.businessLine()
        );
        String environmentCode = optionalText(args, "environmentCode");
        String systemName = optionalText(args, "systemName");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("resolutionStatus", "RESOLVED");
        context.put("requirement", requirementSummary(detail));
        context.put("testContext", buildBusinessLineTestContext(catalog, businessLine, environmentCode, requestedSystems(detail, systemName)));
        context.put("testingGuidance", testingGuidance());
        return context;
    }

    private Map<String, Object> businessLineTestContext(JsonNode args, McpResourceCatalog catalog) {
        String businessLine = requireText(args, "businessLine");
        String environmentCode = optionalText(args, "environmentCode");
        String systemName = optionalText(args, "systemName");
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("resolutionStatus", "RESOLVED");
        context.put("testContext", buildBusinessLineTestContext(catalog, businessLine, environmentCode, requestedSystems(null, systemName)));
        context.put("testingGuidance", testingGuidance());
        return context;
    }

    private List<IntakeSummaryResponse> searchIntakeCandidates(String approvalCode,
                                                               String requirementName,
                                                               String keyword,
                                                               int limit) {
        return intakeService.list(null, requirementName, approvalCode, null, null, null, null, null, null)
                .stream()
                .filter(item -> keyword == null || containsKeyword(item, keyword))
                .limit(limit)
                .toList();
    }

    private IntakeSummaryResponse selectSingleRequirement(List<IntakeSummaryResponse> matches, String approvalCode) {
        if (matches.size() == 1) {
            return matches.getFirst();
        }
        String normalizedApprovalCode = normalizeText(approvalCode);
        if (normalizedApprovalCode.isEmpty()) {
            return null;
        }
        List<IntakeSummaryResponse> exactMatches = matches.stream()
                .filter(item -> normalizedApprovalCode.equals(normalizeText(item.approvalCode())))
                .toList();
        return exactMatches.size() == 1 ? exactMatches.getFirst() : null;
    }

    private Map<String, Object> requirementSummary(IntakeDetailResponse detail) {
        IntakeStructuredData structuredData = detail.structuredData();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("intakeId", detail.id());
        summary.put("approvalCode", structuredData == null ? null : structuredData.approvalCode());
        summary.put("requirementName", structuredData == null ? null : structuredData.requirementName());
        summary.put("requirementDigest", structuredData == null ? null : structuredData.requirementDigest());
        summary.put("requirementSummary", structuredData == null ? null : structuredData.requirementSummary());
        summary.put("businessLineCode", structuredData == null ? null : structuredData.businessLineCode());
        summary.put("businessLine", structuredData == null ? null : structuredData.businessLine());
        summary.put("developmentBranchName", structuredData == null ? null : structuredData.developmentBranchName());
        summary.put("zentaoUrl", structuredData == null ? null : structuredData.zentaoUrl());
        summary.put("demandStatus", detail.demandStatus());
        summary.put("involvedSystems", detail.involvedSystems());
        summary.put("relatedWorkItems", detail.relatedWorkItems());
        return summary;
    }

    private Map<String, Object> buildBusinessLineTestContext(McpResourceCatalog catalog,
                                                             String businessLine,
                                                             String environmentCode,
                                                             List<String> requestedSystems) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("requestedBusinessLine", businessLine);
        context.put("environmentCode", environmentCode);
        context.put("requestedSystems", requestedSystems);

        McpResourceCatalog.BusinessLine matched = findBusinessLine(catalog, businessLine);
        context.put("businessLineLookupStatus", matched == null ? "NOT_FOUND" : "FOUND");
        if (matched == null) {
            context.put("businessLine", null);
            context.put("databaseTargets", List.of());
            context.put("serverTargets", List.of());
            return context;
        }

        context.put("businessLine", businessLineSummary(matched));
        context.put("environment", environmentSummary(catalog, environmentCode));
        context.put("gitlab", gitlabContext(catalog, matched));
        context.put("knowledge", knowledgeContext(catalog, matched));
        context.put("databaseTargets", catalog.databaseTargets().stream()
                .filter(target -> matchesBusinessLine(target.businessLineCodes(), matched))
                .filter(target -> matchesEnvironment(target.environmentCode(), environmentCode))
                .map(this::databaseTargetSummary)
                .toList());
        context.put("serverTargets", catalog.serverTargets().stream()
                .filter(target -> matchesBusinessLine(target.businessLineCodes(), matched))
                .filter(target -> matchesEnvironment(target.environmentCode(), environmentCode))
                .filter(target -> matchesSystems(target.systemNames(), requestedSystems))
                .map(this::serverTargetSummary)
                .toList());
        return context;
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

    private Map<String, Object> environmentSummary(McpResourceCatalog catalog, String environmentCode) {
        if (environmentCode == null) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("lookupStatus", "NOT_SPECIFIED");
            summary.put("availableEnvironments", catalog.environments());
            return summary;
        }
        return catalog.environments().stream()
                .filter(environment -> normalizeText(environmentCode).equals(normalizeText(environment.code())))
                .findFirst()
                .<Map<String, Object>>map(environment -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("lookupStatus", "FOUND");
                    summary.put("code", environment.code());
                    summary.put("name", environment.name());
                    summary.put("production", environment.production());
                    summary.put("enabled", environment.enabled());
                    return summary;
                })
                .orElseGet(() -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("lookupStatus", "NOT_FOUND");
                    summary.put("code", environmentCode);
                    summary.put("availableEnvironments", catalog.environments());
                    return summary;
                });
    }

    private Map<String, Object> gitlabContext(McpResourceCatalog catalog, McpResourceCatalog.BusinessLine businessLine) {
        Map<String, Object> context = new LinkedHashMap<>(catalog.gitlab());
        context.put("gitlabGroupName", businessLine.gitlabGroupName());
        if (businessLine.gitlabGroupName() != null && !businessLine.gitlabGroupName().isBlank()) {
            context.put("codeCacheRoot", "data/git-cache/" + businessLine.gitlabGroupName().trim());
        }
        return context;
    }

    private Map<String, Object> knowledgeContext(McpResourceCatalog catalog, McpResourceCatalog.BusinessLine businessLine) {
        Map<String, Object> context = new LinkedHashMap<>(catalog.knowledge());
        Object vaultPath = context.get("projectVaultPath");
        if (vaultPath != null) {
            context.put("businessLineWikiRoot", vaultPath + "/wiki/projects/" + firstNonBlank(businessLine.code(), businessLine.name()));
            context.put("businessLineRawRoot", vaultPath + "/raw/requirements/" + firstNonBlank(businessLine.code(), businessLine.name()));
        }
        return context;
    }

    private Map<String, Object> databaseTargetSummary(McpResourceCatalog.DatabaseTarget target) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("key", target.key());
        summary.put("businessLineCode", target.businessLineCode());
        summary.put("businessLineCodes", target.businessLineCodes());
        summary.put("environmentCode", target.environmentCode());
        summary.put("name", target.name());
        summary.put("schema", target.schema());
        summary.put("profiles", target.profiles().stream().map(McpResourceCatalog.DatabaseTarget.DatabaseProfile::key).toList());
        return summary;
    }

    private Map<String, Object> serverTargetSummary(McpResourceCatalog.ServerTarget target) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("key", target.key());
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

    private Map<String, Object> requirementSearchCriteria(String approvalCode, String requirementName, String keyword) {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("approvalCode", approvalCode);
        criteria.put("requirementName", requirementName);
        criteria.put("keyword", keyword);
        return criteria;
    }

    private List<String> requestedSystems(IntakeDetailResponse detail, String systemName) {
        List<String> systems = new ArrayList<>();
        if (detail != null && detail.involvedSystems() != null) {
            detail.involvedSystems().forEach(value -> addUnique(systems, value));
        }
        addUnique(systems, systemName);
        return systems;
    }

    private List<String> testingGuidance() {
        return List.of(
                "需求测试优先以需求关联业务线解析 SERVER/DB 资源；环境未指定时必须先确认环境后再请求服务或查库。",
                "数据库仅使用 run_readonly_query 和 readonly profile 查询；不得写库或修改测试数据。",
                "不通过网关时，先用 serverTargets 的 targetKey/profile/allowedServices/allowedLogPaths 确认服务状态和日志入口，再直接请求对应服务。",
                "分支为空或部署版本不明时，先结合需求 developmentBranchName、服务日志和部署信息确认测试环境是否已发对应分支。"
        );
    }

    private boolean containsKeyword(IntakeSummaryResponse item, String keyword) {
        String normalized = normalizeText(keyword);
        return normalizeText(item.approvalCode()).contains(normalized)
                || normalizeText(item.requirementName()).contains(normalized)
                || normalizeText(item.requirementDigest()).contains(normalized)
                || normalizeText(item.requirementSummary()).contains(normalized)
                || normalizeText(item.remark()).contains(normalized);
    }

    private McpResourceCatalog.BusinessLine findBusinessLine(McpResourceCatalog catalog, String businessLine) {
        String normalized = normalizeText(businessLine);
        if (normalized.isEmpty()) {
            return null;
        }
        return catalog.businessLines().stream()
                .filter(item -> normalized.equals(normalizeText(item.code()))
                        || normalized.equals(normalizeText(item.name()))
                        || normalized.equals(normalizeText(item.gitlabGroupName())))
                .findFirst()
                .orElse(null);
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

    private boolean matchesEnvironment(String targetEnvironmentCode, String requestedEnvironmentCode) {
        return requestedEnvironmentCode == null
                || normalizeText(requestedEnvironmentCode).equals(normalizeText(targetEnvironmentCode));
    }

    private boolean matchesSystems(List<String> targetSystems, List<String> requestedSystems) {
        if (requestedSystems == null || requestedSystems.isEmpty() || targetSystems == null || targetSystems.isEmpty()) {
            return true;
        }
        return targetSystems.stream()
                .map(this::normalizeText)
                .anyMatch(target -> requestedSystems.stream()
                        .map(this::normalizeText)
                        .anyMatch(target::equals));
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void addUnique(List<String> values, String value) {
        String normalized = firstNonBlank(value);
        if (normalized != null && !values.contains(normalized)) {
            values.add(normalized);
        }
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
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
