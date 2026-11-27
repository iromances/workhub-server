package cn.aslight.workhub.service.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.aslight.workhub.dao.project.BusinessLineAccessConfigMapper;
import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.tool.McpToolRegistry;
import cn.aslight.workhub.model.attachment.AttachmentEntity;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.model.project.BusinessLineAccessConfigEntity;
import cn.aslight.workhub.model.ops.BusinessLogSearchRequest;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.attachment.AttachmentTextExtractionService;
import cn.aslight.workhub.service.intake.GitlabRepositoryService;
import cn.aslight.workhub.service.intake.IntakeService;
import cn.aslight.workhub.service.intake.RepositoryDocumentPaths;
import cn.aslight.workhub.service.ops.BusinessLogSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HTTP-hosted MCP runtime backed by the live WorkHub resource catalog.
 */
@Service
public class McpRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(McpRuntimeService.class);

    private static final String LATEST_PROTOCOL_VERSION = "2025-11-25";
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of(
            "2026-07-28",
            LATEST_PROTOCOL_VERSION,
            "2025-06-18",
            "2025-03-26"
    );
    private static final String SERVER_INSTRUCTIONS = """
            WorkHub 提供受控的需求、业务线、本地代码工作区、数据库和服务器只读诊断工具。业务代码统一位于 /Users/aslight/IDEAWorkspace/{gitlabGroupName}；涉及业务线排查或测试时，必须先确认业务线、环境和分支；生产日志排查首选 search_business_logs 实时查询受控 ELK 范围，服务器日志作为补充，尤其在生产服务器需要堡垒机时；数据库只允许只读 SQL，查询结果和日志结果会在服务端脱敏；目录和文本文件仅通过 list_server_directory/read_server_file 在统一 allowedLogPaths 范围内只读访问，默认 /mnt，空白名单关闭，其他目录必须先经用户明确授权后配置，AI 不得自行扩展；不得执行所读取脚本；不得尝试绕过资源白名单、时间、行数与字节限制、路径限制或安全策略。
            """.strip();

    private final McpResourceService mcpResourceService;
    private final IntakeService intakeService;
    private final GitlabRepositoryService gitlabRepositoryService;
    private final BusinessLineAccessConfigMapper businessLineAccessConfigMapper;
    private final BusinessLogSearchService businessLogSearchService;
    private final AttachmentService attachmentService;
    private final AttachmentTextExtractionService attachmentTextExtractionService;
    private final McpCryptoService mcpCryptoService;
    private final ObjectMapper objectMapper;
    private RepositoryFileService repositoryFileService;

    public McpRuntimeService(McpResourceService mcpResourceService,
                             IntakeService intakeService,
                             GitlabRepositoryService gitlabRepositoryService) {
        this(mcpResourceService, intakeService, gitlabRepositoryService, null, null, null, null, null);
    }

    public McpRuntimeService(McpResourceService mcpResourceService,
                             IntakeService intakeService,
                             GitlabRepositoryService gitlabRepositoryService,
                             BusinessLineAccessConfigMapper businessLineAccessConfigMapper) {
        this(mcpResourceService, intakeService, gitlabRepositoryService,
                businessLineAccessConfigMapper, null, null, null, null);
    }

    public McpRuntimeService(McpResourceService mcpResourceService,
                             IntakeService intakeService,
                             GitlabRepositoryService gitlabRepositoryService,
                             BusinessLineAccessConfigMapper businessLineAccessConfigMapper,
                             BusinessLogSearchService businessLogSearchService) {
        this(mcpResourceService, intakeService, gitlabRepositoryService,
                businessLineAccessConfigMapper, businessLogSearchService, null, null, null);
    }

    @Autowired
    public McpRuntimeService(McpResourceService mcpResourceService,
                             IntakeService intakeService,
                             GitlabRepositoryService gitlabRepositoryService,
                             BusinessLineAccessConfigMapper businessLineAccessConfigMapper,
                             BusinessLogSearchService businessLogSearchService,
                             AttachmentService attachmentService,
                             AttachmentTextExtractionService attachmentTextExtractionService,
                             McpCryptoService mcpCryptoService) {
        this.mcpResourceService = mcpResourceService;
        this.intakeService = intakeService;
        this.gitlabRepositoryService = gitlabRepositoryService;
        this.businessLineAccessConfigMapper = businessLineAccessConfigMapper;
        this.businessLogSearchService = businessLogSearchService;
        this.attachmentService = attachmentService;
        this.attachmentTextExtractionService = attachmentTextExtractionService;
        this.mcpCryptoService = mcpCryptoService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setRepositoryFileService(RepositoryFileService repositoryFileService) {
        this.repositoryFileService = repositoryFileService;
    }

    private cn.aslight.workhub.service.system.IntegrationQueryService integrationQueryService;

    /** 系统集成只读查询工具由查询服务装配；未装配时 tools/list 不出现相关工具。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setIntegrationQueryService(cn.aslight.workhub.service.system.IntegrationQueryService integrationQueryService) {
        this.integrationQueryService = integrationQueryService;
    }

    private cn.aslight.workhub.integration.wecom.WecomRobotMessageSender wecomRobotMessageSender;

    /** 数字员工企微机器人发送工具由发送组件装配；未装配时 tools/list 不出现该工具。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setWecomRobotMessageSender(cn.aslight.workhub.integration.wecom.WecomRobotMessageSender wecomRobotMessageSender) {
        this.wecomRobotMessageSender = wecomRobotMessageSender;
    }

    public String handle(String rawRequest) {
        try {
            return objectMapper.writeValueAsString(handle(objectMapper.readTree(rawRequest)));
        } catch (IOException ex) {
            throw new IllegalArgumentException("MCP 请求不是合法 JSON", ex);
        }
    }

    /**
     * 处理 Streamable HTTP MCP 消息。请求返回 JSON-RPC 对象，通知或响应返回 202 空响应。
     */
    public HttpResponse handleHttp(String rawRequest, String protocolVersionHeader) {
        JsonNode message;
        try {
            message = objectMapper.readTree(rawRequest);
        } catch (IOException ex) {
            return jsonErrorResponse(400, null, -32700, "MCP 请求不是合法 JSON");
        }
        if (message == null || !message.isObject() || !"2.0".equals(message.path("jsonrpc").asText())) {
            return jsonErrorResponse(400, message == null ? null : message.get("id"), -32600, "无效的 JSON-RPC 请求");
        }
        if (protocolVersionHeader != null
                && !protocolVersionHeader.isBlank()
                && !SUPPORTED_PROTOCOL_VERSIONS.contains(protocolVersionHeader.trim())) {
            return jsonErrorResponse(400, message.get("id"), -32602,
                    "不支持的 MCP 协议版本：" + protocolVersionHeader.trim());
        }
        if (!message.has("id")) {
            if (message.hasNonNull("method") || message.has("result") || message.has("error")) {
                return new HttpResponse(202, null);
            }
            return jsonErrorResponse(400, null, -32600, "无效的 JSON-RPC 通知或响应");
        }
        return new HttpResponse(200, writeJson(handle(message)));
    }

    public JsonNode handle(JsonNode request) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", request == null || !request.has("id") ? NullNode.getInstance() : request.get("id"));
        try {
            if (request == null || !request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())) {
                throw new IllegalArgumentException("无效的 JSON-RPC 请求");
            }
            if (!request.has("id")) {
                throw new IllegalArgumentException("MCP 请求缺少 id");
            }
            String method = request.path("method").asText();
            JsonNode params = request.path("params");
            response.set("result", switch (method) {
                case "initialize" -> initializeResult(params);
                case "tools/list" -> toolsListResult();
                case "tools/call" -> toolsCallResult(params);
                default -> throw new UnsupportedOperationException("不支持的 MCP 方法：" + method);
            });
        } catch (Exception ex) {
            response.set("error", errorNode(ex));
        }
        return response;
    }

    private ObjectNode initializeResult(JsonNode params) {
        String requestedVersion = requireText(params, "protocolVersion");
        if (!SUPPORTED_PROTOCOL_VERSIONS.contains(requestedVersion)) {
            throw new IllegalArgumentException("不支持的 MCP 协议版本：" + requestedVersion);
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", requestedVersion);
        ObjectNode capabilities = objectMapper.createObjectNode();
        capabilities.set("tools", objectMapper.createObjectNode());
        result.set("capabilities", capabilities);
        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "workhub-http-mcp-runtime");
        serverInfo.put("version", "0.1.0");
        result.set("serverInfo", serverInfo);
        result.put("instructions", SERVER_INSTRUCTIONS);
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.set("tools", registry().listTools());
        return result;
    }

    /** 数字员工 CLI 运行时按此清单放开平台 MCP 工具，AI 依据职责描述自行选择。 */
    public List<String> toolNames() {
        List<String> names = new ArrayList<>();
        registry().listTools().forEach(node -> names.add(node.path("name").asText()));
        return List.copyOf(names);
    }

    private ObjectNode toolsCallResult(JsonNode params) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode text = objectMapper.createObjectNode();
        text.put("type", "text");
        try {
            String name = requireText(params, "name");
            JsonNode arguments = params.path("arguments");
            long startedAt = System.currentTimeMillis();
            Object outcome = registry().call(name, arguments);
            log.info("MCP tool call completed. tool={}, argsSize={}, durationMs={}",
                    name, arguments == null ? 0 : arguments.size(), System.currentTimeMillis() - startedAt);
            text.put("text", renderText(outcome));
        } catch (Exception ex) {
            text.put("text", ex.getMessage() == null ? "MCP 工具执行失败" : ex.getMessage());
            result.put("isError", true);
        }
        content.add(text);
        result.set("content", content);
        return result;
    }

    private McpToolRegistry registry() {
        McpResourceCatalog catalog = objectMapper.convertValue(mcpResourceService.catalog(), McpResourceCatalog.class);
        McpToolRegistry registry = McpToolRegistry.firstVersion(catalog, objectMapper);
        registry.register("get_business_line_context", "Get GitLab, access endpoints, system, knowledge, database and server context for a business line.",
                objectSchema(List.of("businessLine")),
                args -> businessLineContext(args, catalog));
        registerIntakeTools(registry, catalog);
        registerGitlabTools(registry);
        registerBusinessLogTools(registry, catalog);
        registerRepositoryFileTools(registry);
        registerIntegrationQueryTools(registry);
        registerWecomRobotTools(registry);
        return registry;
    }

    private Object repositoryCall(RepositoryFileService.IOCall call) {
        try {
            return call.run();
        } catch (java.io.IOException ex) {
            throw new IllegalArgumentException("仓库文件访问失败：" + ex.getMessage(), ex);
        }
    }

    /** 仓库只读文件工具：白名单关闭时不注册，tools/list 可见缺失。 */
    private void registerRepositoryFileTools(McpToolRegistry registry) {
        if (repositoryFileService == null || !repositoryFileService.enabled()) {
            return;
        }
        registry.register("list_repository_files", "List files and directories below a configured repository-read-roots root. Bounded depth and entries; never follows symbolic links. Other directories require explicit user authorization; never expand the allowlist automatically.",
                objectSchema(List.of("path"), "maxDepth", "maxEntries"),
                args -> repositoryCall(() -> repositoryFileService.list(
                        requireText(args, "path"),
                        args.hasNonNull("maxDepth") ? args.get("maxDepth").asInt() : null,
                        args.hasNonNull("maxEntries") ? args.get("maxEntries").asInt() : null)));
        registry.register("read_repository_text_file", "Read a bounded UTF-8 text file below a configured repository-read-roots root. Rejects symbolic links, special files and binary data; never executes the file. Other directories require explicit user authorization; never expand the allowlist automatically.",
                objectSchema(List.of("path"), "maxBytes"),
                args -> repositoryCall(() -> repositoryFileService.readTextFile(
                        requireText(args, "path"),
                        args.hasNonNull("maxBytes") ? args.get("maxBytes").asInt() : null)));
        registry.register("search_repository_text", "Search a fixed keyword (no regex) in UTF-8 text files below a configured repository-read-roots root. Bounded match count; skips binary and oversized files.",
                objectSchema(List.of("path", "keyword"), "maxMatches"),
                args -> repositoryCall(() -> repositoryFileService.searchText(
                        requireText(args, "path"),
                        requireText(args, "keyword"),
                        args.hasNonNull("maxMatches") ? args.get("maxMatches").asInt() : null)));
    }

    private void registerBusinessLogTools(McpToolRegistry registry, McpResourceCatalog catalog) {
        if (businessLogSearchService == null) {
            return;
        }
        registry.register("search_business_logs", "Preferred production log query. Search the controlled live ELK scope for one business line and environment. serviceNames and levels are comma-separated; no raw index or DSL is accepted.",
                objectSchema(List.of("businessLine", "environmentCode"),
                        "serviceNames", "levels", "from", "to", "phrase", "traceId", "requestId", "limit"),
                args -> searchBusinessLogs(args, catalog));
    }

    private Object searchBusinessLogs(JsonNode args, McpResourceCatalog catalog) {
        String businessLine = requireText(args, "businessLine");
        McpResourceCatalog.BusinessLine matched = findBusinessLine(catalog, businessLine);
        if (matched == null) {
            throw new IllegalArgumentException("业务线不存在：" + businessLine);
        }
        return businessLogSearchService.search(new BusinessLogSearchRequest(
                matched.code(),
                requireText(args, "environmentCode"),
                optionalCsv(args, "serviceNames"),
                optionalCsv(args, "levels"),
                optionalText(args, "from"),
                optionalText(args, "to"),
                optionalText(args, "phrase"),
                optionalText(args, "traceId"),
                optionalText(args, "requestId"),
                optionalInt(args, "limit", 50, 1, 100)
        ));
    }

    /** 数字员工唯一的企微出口：webhook 服务端配置，AI 只提供 markdown 内容。 */
    private void registerWecomRobotTools(McpToolRegistry registry) {
        if (wecomRobotMessageSender == null) {
            return;
        }
        registry.register("send_wecom_robot_markdown",
                "Send one markdown message to the configured WeCom group robot (system config wecom.agent.webhook, server-side only). "
                        + "content is the markdown body, UTF-8 bytes must be 1-4096; use for digital-employee patrol summaries, at most one summary per run.",
                objectSchema(List.of("content")),
                args -> wecomRobotMessageSender.send(requireText(args, "content")));
    }

    private void registerIntakeTools(McpToolRegistry registry, McpResourceCatalog catalog) {
        registry.register("get_intake_detail", "Read one requirement-management intake detail by intakeId.",
                objectSchema(List.of("intakeId")),
                args -> intakeService.detail(requireLong(args, "intakeId"), null, false));
        if (attachmentService != null && attachmentTextExtractionService != null) {
            registry.register("get_intake_attachment",
                    "Read one intake attachment's text content extracted in memory from the database (no local file cache); documents are server-side text-extracted with a size cap, images return metadata only.",
                    objectSchema(List.of("attachmentId"), "intakeId"),
                    this::intakeAttachmentText);
        }
        registry.register("search_intakes", "Search requirement-management intakes by approvalCode, requirementName and/or keyword in requirement digest/summary.",
                objectSchema(List.of(), "approvalCode", "requirementName", "keyword", "limit"),
                this::searchIntakes);
        registry.register("get_requirement_test_context", "Resolve a requirement by intakeId, approvalCode, requirementName or summary keyword, then return business-line, branch, access endpoints, SERVER/DB and GitLab/knowledge context for testing.",
                objectSchema(List.of(), "intakeId", "approvalCode", "requirementName", "keyword", "businessLine", "environmentCode", "systemName", "limit"),
                args -> requirementTestContext(args, catalog));
        registry.register("get_business_line_test_context", "Return access endpoints, SERVER/DB, GitLab, knowledge and involved-system context for testing a business line in one environment.",
                objectSchema(List.of("businessLine"), "environmentCode", "systemName"),
                args -> businessLineTestContext(args, catalog));
    }

    private void registerGitlabTools(McpToolRegistry registry) {
        registry.register("sync_gitlab_group_repositories", "Locate all accessible repositories in /Users/aslight/IDEAWorkspace/{gitlabGroupName}; clone missing repositories and safely fetch existing repositories without resetting local work.",
                objectSchema(List.of("businessLine")),
                this::syncGitlabGroupRepositories);
    }

    /** 系统集成只读查询工具（禅道/Jenkins）：地址与凭据取自系统集成配置，AI 不能提供；全部只读，不触发构建。 */
    private void registerIntegrationQueryTools(McpToolRegistry registry) {
        if (integrationQueryService == null) {
            return;
        }
        registry.register("search_zentao_bugs", "Search ZenTao bugs read-only. Optional productId/executionId scopes the query (falls back to the global list with field filtering when the scoped endpoint is missing); keyword matches title/keywords/id case-insensitively.",
                objectSchema(List.of(), "productId", "executionId", "keyword", "limit"),
                args -> integrationQueryService.searchZentaoBugs(
                        optionalIdLong(args, "productId"), optionalIdLong(args, "executionId"),
                        optionalText(args, "keyword"), optionalPositiveInt(args, "limit")));
        registry.register("get_zentao_bug", "Read one ZenTao bug detail by bugId.",
                objectSchema(List.of("bugId")),
                args -> integrationQueryService.getZentaoBug(requireText(args, "bugId")));
        registry.register("get_zentao_story", "Read one ZenTao story detail by storyId.",
                objectSchema(List.of("storyId")),
                args -> integrationQueryService.getZentaoStory(requireText(args, "storyId")));
        registry.register("get_zentao_task", "Read one ZenTao task detail by taskId.",
                objectSchema(List.of("taskId")),
                args -> integrationQueryService.getZentaoTask(requireText(args, "taskId")));
        registry.register("get_zentao_execution", "Read one ZenTao execution detail by executionId, including its project and product.",
                objectSchema(List.of("executionId")),
                args -> integrationQueryService.getZentaoExecution(requireText(args, "executionId")));
        registry.register("list_jenkins_jobs", "List Jenkins jobs at the instance root or below a folder path. Read-only; never triggers builds. Optional instanceId defaults to the first enabled instance configured in system integration.",
                objectSchema(List.of(), "instanceId", "folder", "limit"),
                args -> integrationQueryService.listJenkinsJobs(
                        optionalText(args, "instanceId"), optionalText(args, "folder"), optionalPositiveInt(args, "limit")));
        registry.register("get_jenkins_job", "Read one Jenkins job detail (buildable, health, last build) by jobPath like folder/job. Read-only; never triggers builds.",
                objectSchema(List.of("jobPath"), "instanceId"),
                args -> integrationQueryService.getJenkinsJob(optionalText(args, "instanceId"), requireText(args, "jobPath")));
        registry.register("get_jenkins_build", "Read one Jenkins build detail (result, parameters, duration) by jobPath and buildNumber. Read-only; never triggers builds.",
                objectSchema(List.of("jobPath", "buildNumber"), "instanceId"),
                args -> integrationQueryService.getJenkinsBuild(
                        optionalText(args, "instanceId"), requireText(args, "jobPath"), requireText(args, "buildNumber")));
        registry.register("get_jenkins_build_log", "Read a bounded console-log tail of one Jenkins build by jobPath and buildNumber. Read-only; never triggers builds.",
                objectSchema(List.of("jobPath", "buildNumber"), "instanceId", "lines", "maxBytes"),
                args -> integrationQueryService.getJenkinsBuildLog(
                        optionalText(args, "instanceId"), requireText(args, "jobPath"), requireText(args, "buildNumber"),
                        optionalPositiveInt(args, "lines"), optionalPositiveInt(args, "maxBytes")));
    }

    /** 可选正整数 ID 参数：缺省返回 null；非法、零或负数拒绝。 */
    private Long optionalIdLong(JsonNode node, String field) {
        Long value = optionalLong(node, field);
        if (value != null && value < 1) {
            throw new IllegalArgumentException("MCP 参数必须为正整数：" + field);
        }
        return value;
    }

    /** 可选数值参数（整数字符串）：缺省返回 null；非法、零或负数拒绝，超出上限由服务收敛。 */
    private Integer optionalPositiveInt(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException("MCP 参数必须为正整数：" + field);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("MCP 参数必须是数字：" + field);
        }
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

    private Map<String, Object> businessLineContext(JsonNode args, McpResourceCatalog catalog) {
        McpResourceCatalog.BusinessLine matched = findBusinessLine(catalog, requireText(args, "businessLine"));
        if (matched == null) {
            throw new IllegalArgumentException("业务线不存在：" + requireText(args, "businessLine"));
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("businessLine", businessLineSummary(matched));
        context.put("accessEndpoints", accessEndpointSummaries(matched.code(), null));
        context.put("gitlab", gitlabContext(catalog, matched));
        context.put("knowledge", knowledgeContext(catalog, matched));
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

    private List<IntakeSummaryResponse> searchIntakeCandidates(String approvalCode,
                                                               String requirementName,
                                                               String keyword,
                                                               int limit) {
        return intakeService.search(approvalCode, requirementName, keyword, limit);
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
        context.put("accessEndpoints", accessEndpointSummaries(matched.code(), environmentCode));
        context.put("gitlab", gitlabContext(catalog, matched));
        context.put("knowledge", knowledgeContext(catalog, matched));
        context.put("databaseTargets", catalog.databaseTargets().stream()
                .filter(target -> target.publicResource() || matchesBusinessLine(target.businessLineCodes(), matched))
                .filter(target -> matchesEnvironment(target.environmentCode(), environmentCode))
                .filter(target -> matchesSystems(target.systemNames(), requestedSystems))
                .map(this::databaseTargetSummary)
                .toList());
        context.put("serverTargets", catalog.serverTargets().stream()
                .filter(target -> target.publicResource() || matchesBusinessLine(target.businessLineCodes(), matched))
                .filter(target -> matchesEnvironment(target.environmentCode(), environmentCode))
                .filter(target -> matchesSystems(target.systemNames(), requestedSystems))
                .map(this::serverTargetSummary)
                .toList());
        return context;
    }

    private List<Map<String, Object>> accessEndpointSummaries(String businessLineCode, String environmentCode) {
        if (businessLineAccessConfigMapper == null || businessLineCode == null || businessLineCode.isBlank()) {
            return List.of();
        }
        List<BusinessLineAccessConfigEntity> configs =
                businessLineAccessConfigMapper.findByBusinessLineCodes(List.of(businessLineCode));
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }
        return configs.stream()
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .filter(config -> matchesEnvironment(config.getEnvironmentCode(), environmentCode))
                .map(this::accessEndpointSummary)
                .toList();
    }

    private Map<String, Object> accessEndpointSummary(BusinessLineAccessConfigEntity config) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("environmentCode", config.getEnvironmentCode());
        summary.put("endpointType", config.getEndpointType());
        summary.put("endpointName", config.getEndpointName());
        summary.put("endpointUrl", config.getEndpointUrl());
        summary.put("pathPrefix", config.getPathPrefix());
        summary.put("effectiveUrl", resolveEffectiveUrl(config.getEndpointUrl(), config.getPathPrefix()));
        if (config.getLoginUsername() != null) {
            summary.put("loginUsername", config.getLoginUsername());
        }
        if (config.getLoginPasswordEncrypted() != null && mcpCryptoService != null) {
            summary.put("loginPassword", mcpCryptoService.decrypt(config.getLoginPasswordEncrypted()));
        }
        return summary;
    }

    private String resolveEffectiveUrl(String endpointUrl, String pathPrefix) {
        if (endpointUrl == null || pathPrefix == null || pathPrefix.isBlank()) {
            return endpointUrl;
        }
        String baseUrl = endpointUrl.endsWith("/")
                ? endpointUrl.substring(0, endpointUrl.length() - 1)
                : endpointUrl;
        String normalizedPrefix = pathPrefix.startsWith("/") ? pathPrefix : "/" + pathPrefix;
        return baseUrl + normalizedPrefix;
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
            context.put("codeWorkspaceRoot", "/Users/aslight/IDEAWorkspace/" + businessLine.gitlabGroupName().trim());
        }
        return context;
    }

    private Map<String, Object> knowledgeContext(McpResourceCatalog catalog, McpResourceCatalog.BusinessLine businessLine) {
        Map<String, Object> context = new LinkedHashMap<>(catalog.knowledge());
        Object documentPath = context.get("projectDocumentPath");
        if (documentPath != null) {
            // WorkHub 平台（BL000012）归档在 docs/业务线，其余业务线归档在 agents-playbook 仓库 docs/业务线
            context.put("businessLineWikiRoot", businessLineWikiRoot(Path.of(documentPath.toString()), context, businessLine));
        }
        return context;
    }

    private String businessLineWikiRoot(Path documentsRoot, Map<String, Object> context, McpResourceCatalog.BusinessLine businessLine) {
        String segment = businessLineDirSegment(businessLine);
        if (RepositoryDocumentPaths.WORKHUB_BUSINESS_LINE_CODE.equals(firstNonBlank(businessLine.code(), null))) {
            return documentsRoot.resolve(RepositoryDocumentPaths.BUSINESS_LINES).resolve(segment).toString();
        }
        Object playbookPath = context.get("projectPlaybookPath");
        Path root = playbookPath != null ? Path.of(playbookPath.toString())
                : RepositoryDocumentPaths.defaultPlaybookRepository(documentsRoot)
                        .resolve("docs").resolve(RepositoryDocumentPaths.BUSINESS_LINES);
        return root.resolve(segment).toString();
    }

    /** 业务线文档目录统一为 {业务线编码}-{业务线名称}；无编码或编码与名称相同时退回名称。 */
    private String businessLineDirSegment(McpResourceCatalog.BusinessLine businessLine) {
        String code = firstNonBlank(businessLine.code(), null);
        String name = firstNonBlank(businessLine.name(), code);
        return code == null || code.equals(name) ? name : code + "-" + name;
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
                "读取部署目录或启动脚本时，先确认 serverTargets.allowedLogPaths，再调用 list_server_directory/read_server_file；默认 /mnt，其他目录须用户明确授权，空白名单关闭，不执行文件。",
                "分支为空或部署版本不明时，先结合需求 developmentBranchName、服务日志和部署信息确认测试环境是否已发对应分支。"
        );
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
        int code = ex instanceof UnsupportedOperationException ? -32601
                : ex instanceof IllegalArgumentException ? -32602 : -32603;
        error.put("code", code);
        error.put("message", ex.getMessage() == null ? "MCP 请求处理失败" : ex.getMessage());
        error.set("data", objectMapper.valueToTree(Map.of("exception", ex.getClass().getName())));
        return error;
    }

    private HttpResponse jsonErrorResponse(int status, JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? NullNode.getInstance() : id);
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        response.set("error", error);
        return new HttpResponse(status, writeJson(response));
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("MCP 响应无法序列化", ex);
        }
    }

    public record HttpResponse(int status, String body) {
    }

    private String requireText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw new IllegalArgumentException("MCP 参数缺少字段：" + field);
        }
        return value;
    }

    /** 数据库附件的空中读取：内存取内容并提取文本，不写本地缓存。 */
    private Map<String, Object> intakeAttachmentText(JsonNode args) {
        long attachmentId = requireLong(args, "attachmentId");
        Long intakeId = optionalLong(args, "intakeId");
        AttachmentService.AttachmentResource resource = attachmentService.loadAsResource(attachmentId);
        AttachmentEntity entity = resource.entity();
        boolean intakeMaterial = entity.getBizType() != null && entity.getBizType().startsWith("INTAKE_");
        if (intakeId != null && (!intakeMaterial || !intakeId.equals(entity.getBizId()))) {
            throw new IllegalArgumentException("附件不属于当前需求");
        }
        byte[] content;
        try (InputStream input = resource.resource().getInputStream()) {
            content = input.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("附件内容读取失败：" + entity.getFileName(), ex);
        }
        var context = new AttachmentService.AttachmentFileContext(entity.getId(), entity.getBizType(), entity.getFileName(),
                entity.getStoragePath(), entity.getContentType(), content, entity.getFileSize(), entity.getFileSha256());
        AttachmentTextExtractionService.InMemoryAttachmentText extracted =
                attachmentTextExtractionService.extractTextInMemory(context);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("attachmentId", entity.getId());
        result.put("intakeId", entity.getBizId());
        result.put("bizType", entity.getBizType());
        result.put("fileName", entity.getFileName());
        result.put("contentType", entity.getContentType());
        result.put("fileSize", entity.getFileSize());
        result.put("fileSha256", entity.getFileSha256());
        result.put("fileType", extracted.fileType());
        result.put("image", extracted.image());
        if (extracted.text() != null) {
            result.put("text", extracted.text());
            result.put("truncated", extracted.truncated());
        }
        if (extracted.note() != null) {
            result.put("note", extracted.note());
        }
        return result;
    }

    private Long requireLong(JsonNode node, String field) {
        String value = requireText(node, field);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("MCP 参数必须是数字：" + field, ex);
        }
    }

    private Long optionalLong(JsonNode args, String name) {
        String value = optionalText(args, name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("MCP 参数必须是数字：" + name, ex);
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

    private List<String> optionalCsv(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
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
