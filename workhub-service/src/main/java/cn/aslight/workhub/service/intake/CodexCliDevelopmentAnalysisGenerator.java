package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.DevelopmentWorkItemDraft;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeSddDocuments;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayRequest;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Codex CLI 的研发代码影响分析生成器。
 */
@Component
public class CodexCliDevelopmentAnalysisGenerator {

    private static final String ANALYZE_USE_CASE_CODE = "intake.development.analyze";
    private static final String ADJUST_USE_CASE_CODE = "intake.development.adjust";
    private static final String SDD_ANALYZE_USE_CASE_CODE = "intake.development.sdd-analyze";
    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
                "properties": {
                "summary": { "type": ["string", "null"] },
                "sddProposal": { "type": ["string", "null"] },
                "sddDesign": { "type": ["string", "null"] },
                "sddTasks": { "type": ["string", "null"] },
                "requirementChangePoints": { "type": "array", "items": { "type": "string" } },
                "impactedModules": { "type": "array", "items": { "type": "string" } },
                "workItems": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "title": { "type": "string" },
                      "description": { "type": ["string", "null"] },
                      "requirementChangePoint": { "type": ["string", "null"] },
                      "taskType": { "type": ["string", "null"] },
                      "targetResources": { "type": "array", "items": { "type": "string" } },
                      "changePoints": { "type": "array", "items": { "type": "string" } },
                      "systemTags": { "type": "array", "items": { "type": "string" } },
                      "evidenceRefs": { "type": "array", "items": { "type": "string" } },
                      "confidence": { "type": ["string", "null"] },
                      "moduleName": { "type": ["string", "null"] },
                      "relatedFiles": { "type": "array", "items": { "type": "string" } },
                      "estimatedEffort": { "type": ["string", "null"] },
                      "ownerUserName": { "type": ["string", "null"] },
                      "priority": { "type": ["string", "null"] },
                      "dependency": { "type": ["string", "null"] },
                      "risk": { "type": ["string", "null"] }
                    },
                    "required": ["title", "description", "requirementChangePoint", "taskType", "targetResources", "changePoints", "systemTags", "evidenceRefs", "confidence", "moduleName", "relatedFiles", "estimatedEffort", "ownerUserName", "priority", "dependency", "risk"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["summary", "requirementChangePoints", "impactedModules", "workItems"],
              "additionalProperties": false
            }
            """;

    private final AiGatewayClient aiGatewayClient;
    private final ChinaWorkdayCalendar chinaWorkdayCalendar;
    private final ObjectMapper objectMapper;

    public CodexCliDevelopmentAnalysisGenerator(AiGatewayClient aiGatewayClient,
                                                ChinaWorkdayCalendar chinaWorkdayCalendar,
                                                ObjectMapper objectMapper) {
        this.aiGatewayClient = aiGatewayClient;
        this.chinaWorkdayCalendar = chinaWorkdayCalendar;
        this.objectMapper = objectMapper;
    }

    public DevelopmentAnalysisDraft generate(IntakeRecordEntity entity,
                                             IntakeStructuredData structuredData,
                                             ProjectDetailResponse project,
                                             GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                             List<UserOptionResponse> developers,
                                             String requirementMarkdownContext,
                                             String knowledgeBaseContext) {
        return execute(
                ANALYZE_USE_CASE_CODE,
                buildVariables(entity, structuredData, project, repositoryBundle, developers, requirementMarkdownContext, knowledgeBaseContext),
                project,
                repositoryBundle,
                developers,
                false
        );
    }

    /**
     * SDD 模式评估：提示词附带知识库 SDD 流程规范，产出三件套草稿，任务负责人一律为空由人工指派。
     */
    public DevelopmentAnalysisDraft generateSdd(IntakeRecordEntity entity,
                                                IntakeStructuredData structuredData,
                                                ProjectDetailResponse project,
                                                GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                                List<UserOptionResponse> developers,
                                                String requirementMarkdownContext,
                                                String knowledgeBaseContext,
                                                String sddProcessSpec) {
        Map<String, Object> variables = buildVariables(entity, structuredData, project, repositoryBundle, developers, requirementMarkdownContext, knowledgeBaseContext);
        variables.put("sddProcessSpec", value(sddProcessSpec));
        return execute(
                SDD_ANALYZE_USE_CASE_CODE,
                variables,
                project,
                repositoryBundle,
                developers,
                true
        );
    }

    public DevelopmentAnalysisDraft adjust(DevelopmentAnalysisDraft currentDraft,
                                           String userMessage,
                                           ProjectDetailResponse project,
                                           GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                           List<UserOptionResponse> developers) {
        return execute(
                ADJUST_USE_CASE_CODE,
                buildAdjustmentVariables(currentDraft, userMessage, developers),
                project,
                repositoryBundle,
                developers,
                false
        );
    }

    private DevelopmentAnalysisDraft execute(String useCaseCode,
                                             Map<String, Object> variables,
                                             ProjectDetailResponse project,
                                             GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                             List<UserOptionResponse> developers,
                                             boolean sddMode) {
        if (!aiGatewayClient.isConfigured(useCaseCode)) {
            throw new IllegalArgumentException("AI 场景未配置或已停用，不能执行代码影响分析");
        }
        AiGatewayResult result = aiGatewayClient.executeStructured(AiGatewayRequest.withVariables(
                useCaseCode,
                variables,
                repositoryBundle.localRoot(),
                repositoryBundle.repositories().stream().map(item -> item.localPath().toString()).toList(),
                List.of(),
                OUTPUT_SCHEMA
        ));
        if (!result.succeeded()) {
            throw new IllegalArgumentException(result.failureSummary());
        }
        try {
            RawDevelopmentAnalysis raw = objectMapper.readValue(result.output(), RawDevelopmentAnalysis.class);
            List<DevelopmentWorkItemDraft> workItems = normalizeWorkItems(raw.workItems(), project, repositoryBundle);
            if (workItems.isEmpty()) {
                // 模型跳过代码调查直接交"过程叙述"时会被格式校正包装成空拆解，这里必须显式失败而不是保存空评估。
                throw new IllegalArgumentException("AI 未产出任务拆解（仅返回过程叙述或空结果），已拒绝保存空评估，请重跑研发任务评估");
            }
            if (sddMode) {
                workItems = workItems.stream()
                        .map(item -> new DevelopmentWorkItemDraft(
                                item.title(),
                                item.description(),
                                item.requirementChangePoint(),
                                item.taskType(),
                                item.targetResources(),
                                item.changePoints(),
                                item.systemTags(),
                                item.evidenceRefs(),
                                item.confidence(),
                                item.moduleName(),
                                item.relatedFiles(),
                                item.estimatedEffort(),
                                null,
                                item.priority(),
                                item.plannedStartDate(),
                                item.plannedEndDate(),
                                item.dependency(),
                                item.risk()
                        ))
                        .toList();
            }
            int totalEffortHours = sumEstimatedEffortHours(workItems);
            return new DevelopmentAnalysisDraft(
                    "DRAFT",
                    project.businessLine(),
                    project.id(),
                    project.name(),
                    repositoryBundle.repositorySummary(),
                    null,
                    null,
                    null,
                    trimToNull(raw.summary()),
                    safeList(raw.requirementChangePoints()),
                    safeList(raw.impactedModules()),
                    // 待确认项与风险统一由需求澄清阶段产出；评估阶段不再生成，字段仅保留兼容存量草稿。
                    List.of(),
                    List.of(),
                    developers.stream().map(UserOptionResponse::userName).distinct().toList(),
                    workItems,
                    totalEffortHours <= 0 ? null : totalEffortHours + "h",
                    totalEffortHours <= 0 ? null : totalEffortHours + "h",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "RESERVED",
                    "禅道同步接口已预留，当前不会调用禅道。",
                    LocalDateTime.now().toString(),
                    "Codex CLI",
                    null,
                    sddMode ? new IntakeSddDocuments(
                            trimToNull(raw.sddProposal()),
                            trimToNull(raw.sddDesign()),
                            trimToNull(raw.sddTasks())
                    ) : null
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("代码影响分析结果解析失败：" + summarizeException(ex), ex);
        }
    }

    Map<String, Object> buildVariables(IntakeRecordEntity entity,
                                       IntakeStructuredData structuredData,
                                       ProjectDetailResponse project,
                                       GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                       List<UserOptionResponse> developers,
                                       String requirementMarkdownContext,
                                       String knowledgeBaseContext) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("projectInfo", "- 项目：" + project.name()
                + "\n- 业务线：" + project.businessLine()
                + "\n- 负责人：" + project.ownerUserName());
        variables.put("developers", formatDevelopers(developers));
        variables.put("systemTags", formatAvailableSystemTags(repositoryBundle));
        variables.put("requirementMarkdown", value(requirementMarkdownContext));
        variables.put("intakeInfo", "- 审批编号：" + value(structuredData.approvalCode())
                + "\n- 需求名称：" + value(structuredData.requirementNameOrTitle())
                + "\n- 需求描述：" + value(structuredData.requirementSummary())
                + "\n- 业务线：" + value(structuredData.businessLine())
                + "\n- 备注：" + value(structuredData.remark()));
        variables.put("structuredFields", formatStructuredFields(structuredData.fields()));
        variables.put("attachmentSummaries", formatAttachmentSummaries(structuredData.attachmentSummaries()));
        variables.put("knowledgeBase", value(knowledgeBaseContext));
        variables.put("rawContent", entity.getRawContent() == null ? "" : entity.getRawContent());
        return variables;
    }

    private String formatStructuredFields(List<IntakeStructuredField> fields) {
        if (fields == null || fields.isEmpty()) {
            return "- 无";
        }
        StringBuilder builder = new StringBuilder();
        for (IntakeStructuredField field : fields) {
            if (field == null || trimToNull(field.label()) == null) {
                continue;
            }
            builder.append("- ")
                    .append(field.label())
                    .append("：")
                    .append(value(field.value()))
                    .append('\n');
        }
        return builder.isEmpty() ? "- 无" : builder.toString();
    }

    private String formatAttachmentSummaries(List<IntakeAttachmentSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "- 无";
        }
        StringBuilder builder = new StringBuilder();
        for (IntakeAttachmentSummary summary : summaries) {
            if (summary == null || trimToNull(summary.summaryText()) == null) {
                continue;
            }
            builder.append("- 来源文件：")
                    .append(value(summary.fileName()))
                    .append("（")
                    .append(value(summary.fileType()))
                    .append("）\n")
                    .append(summary.summaryText().trim())
                    .append("\n");
        }
        return builder.isEmpty() ? "- 无" : builder.toString();
    }

    Map<String, Object> buildAdjustmentVariables(DevelopmentAnalysisDraft currentDraft,
                                                 String userMessage,
                                                 List<UserOptionResponse> developers) {
        try {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("developers", formatDevelopers(developers));
            variables.put("currentDraft", objectMapper.writeValueAsString(currentDraft));
            variables.put("userMessage", userMessage);
            return variables;
        } catch (Exception ex) {
            throw new IllegalStateException("研发拆解调整 prompt 变量生成失败", ex);
        }
    }

    private String formatDevelopers(List<UserOptionResponse> developers) {
        if (developers == null || developers.isEmpty()) {
            return "- admin / WorkHub 管理员";
        }
        StringBuilder builder = new StringBuilder();
        for (UserOptionResponse developer : developers) {
            builder.append("- ")
                    .append(developer.userName())
                    .append(" / ")
                    .append(developer.displayName())
                    .append('\n');
        }
        return builder.toString();
    }

    private List<DevelopmentWorkItemDraft> normalizeWorkItems(List<DevelopmentWorkItemDraft> workItems,
                                                             ProjectDetailResponse project,
                                                             GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle) {
        if (workItems == null || workItems.isEmpty()) {
            return List.of();
        }
        return workItems.stream()
                .filter(item -> item != null && trimToNull(item.title()) != null)
                .map(item -> new DevelopmentWorkItemDraft(
                        item.title().trim(),
                        trimToNull(item.description()),
                        trimToNull(item.requirementChangePoint()),
                        normalizeTaskType(item.taskType()),
                        deriveTargetResources(item),
                        deriveChangePoints(item),
                        deriveSystemTags(item, project, repositoryBundle),
                        deriveEvidenceRefs(item),
                        normalizeConfidence(item.confidence()),
                        trimToNull(item.moduleName()),
                        safeList(item.relatedFiles()),
                        EffortUnitNormalizer.normalizeEffort(item.estimatedEffort()),
                        trimToNull(item.ownerUserName()),
                        normalizePriority(item.priority()),
                        null,
                        null,
                        trimToNull(item.dependency()),
                        trimToNull(item.risk())
                ))
                .toList();
    }

    private String normalizeTaskType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "CODE_CHANGE";
        }
        String upper = normalized.toUpperCase();
        return List.of("CODE_CHANGE", "CONFIG_CHANGE", "DATA_CHANGE", "SQL_SCRIPT", "OPS_ACTION", "VERIFY", "UNKNOWN").contains(upper)
                ? upper
                : "UNKNOWN";
    }

    private List<String> deriveTargetResources(DevelopmentWorkItemDraft item) {
        List<String> targetResources = safeList(item.targetResources());
        if (!targetResources.isEmpty()) {
            return targetResources;
        }
        List<String> relatedFiles = safeList(item.relatedFiles());
        if (!relatedFiles.isEmpty()) {
            return relatedFiles;
        }
        return List.of(trimToNull(item.moduleName()) == null ? item.title().trim() : item.moduleName().trim());
    }

    private List<String> deriveEvidenceRefs(DevelopmentWorkItemDraft item) {
        List<String> evidenceRefs = safeList(item.evidenceRefs());
        if (!evidenceRefs.isEmpty()) {
            return evidenceRefs;
        }
        List<String> relatedFiles = safeList(item.relatedFiles());
        if (!relatedFiles.isEmpty()) {
            return relatedFiles;
        }
        return List.of("需求原文");
    }

    private String normalizeConfidence(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "MEDIUM";
        }
        String upper = normalized.toUpperCase();
        return List.of("HIGH", "MEDIUM", "LOW").contains(upper) ? upper : "MEDIUM";
    }

    private List<String> deriveChangePoints(DevelopmentWorkItemDraft item) {
        List<String> changePoints = safeList(item.changePoints());
        if (!changePoints.isEmpty()) {
            return changePoints;
        }
        String description = trimToNull(item.description());
        if (description == null) {
            return List.of(item.title().trim());
        }
        List<String> derived = Pattern.compile("[。；;\\n]+")
                .splitAsStream(description)
                .map(this::trimToNull)
                .filter(value -> value != null)
                .toList();
        return derived.isEmpty() ? List.of(item.title().trim()) : derived;
    }

    private List<String> deriveSystemTags(DevelopmentWorkItemDraft item,
                                          ProjectDetailResponse project,
                                          GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle) {
        List<String> availableSystemTags = availableSystemTags(repositoryBundle);
        List<String> systemTags = safeList(item.systemTags());
        if (!systemTags.isEmpty()) {
            List<String> normalized = systemTags.stream()
                    .filter(tag -> isValidSystemTag(tag, project, item, availableSystemTags))
                    .toList();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        String fileSystemTag = inferSystemTagFromFiles(item.relatedFiles(), availableSystemTags);
        if (fileSystemTag != null) {
            return List.of(fileSystemTag);
        }
        if (!availableSystemTags.isEmpty()) {
            return List.of(availableSystemTags.getFirst());
        }
        return List.of("待确认微服务");
    }

    private String inferSystemTagFromFiles(List<String> relatedFiles, List<String> availableSystemTags) {
        for (String file : safeList(relatedFiles)) {
            String normalized = file.replace('\\', '/');
            for (String segment : normalized.split("/")) {
                String tag = trimToNull(segment);
                if (tag != null && availableSystemTags.stream().anyMatch(available -> equalsIgnoreCase(available, tag))) {
                    return tag;
                }
            }
        }
        return null;
    }

    private boolean isValidSystemTag(String tag,
                                     ProjectDetailResponse project,
                                     DevelopmentWorkItemDraft item,
                                     List<String> availableSystemTags) {
        String normalized = trimToNull(tag);
        if (normalized == null) {
            return false;
        }
        if (equalsIgnoreCase(normalized, project == null ? null : project.businessLine())
                || equalsIgnoreCase(normalized, project == null ? null : project.name())
                || equalsIgnoreCase(normalized, item.moduleName())) {
            return false;
        }
        if (!availableSystemTags.isEmpty()) {
            return availableSystemTags.stream().anyMatch(available -> equalsIgnoreCase(available, normalized));
        }
        return !Pattern.compile("^(Controller|Service|DAO|Mapper|前端|后端|接口|数据库)$", Pattern.CASE_INSENSITIVE)
                .matcher(normalized)
                .matches();
    }

    private String formatAvailableSystemTags(GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle) {
        List<String> tags = availableSystemTags(repositoryBundle);
        if (tags.isEmpty()) {
            return "- 无，请从仓库目录中识别微服务系统名";
        }
        return tags.stream().map(tag -> "- " + tag).reduce((left, right) -> left + "\n" + right).orElse("- 无");
    }

    private List<String> availableSystemTags(GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle) {
        if (repositoryBundle == null || repositoryBundle.repositories().isEmpty()) {
            return List.of();
        }
        return repositoryBundle.repositories().stream()
                .map(repository -> inferRepositoryName(repository.repositoryUrl()))
                .map(this::trimToNull)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private String inferRepositoryName(String repositoryUrl) {
        String normalized = trimToNull(repositoryUrl);
        if (normalized == null) {
            return null;
        }
        int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf(':'));
        String name = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        return trimToNull(name);
    }

    private boolean equalsIgnoreCase(String first, String second) {
        String normalizedFirst = trimToNull(first);
        String normalizedSecond = trimToNull(second);
        return normalizedFirst != null && normalizedSecond != null && normalizedFirst.equalsIgnoreCase(normalizedSecond);
    }

    private int sumEstimatedEffortHours(List<DevelopmentWorkItemDraft> workItems) {
        if (workItems == null || workItems.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (DevelopmentWorkItemDraft item : workItems) {
            total += parseEffortHours(item.estimatedEffort());
        }
        return total;
    }

    private int parseEffortHours(String effort) {
        String normalized = EffortUnitNormalizer.normalizeEffort(effort);
        if (normalized == null) {
            return 0;
        }
        Matcher matcher = Pattern.compile("(\\d+)h").matcher(normalized);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(this::trimToNull).filter(value -> value != null).distinct().toList();
    }

    private String normalizePriority(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "3";
        }
        return List.of("1", "2", "3", "4").contains(normalized) ? normalized : "3";
    }

    private String normalizeDate(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("yyyy/MM/dd"), DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                return LocalDate.parse(normalized, formatter).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            } catch (DateTimeParseException ignored) {
                // Try next supported date format.
            }
        }
        return null;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String summarizeException(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private record RawDevelopmentAnalysis(String summary,
                                          List<String> requirementChangePoints,
                                          List<String> impactedModules,
                                          List<DevelopmentWorkItemDraft> workItems,
                                          String sddProposal,
                                          String sddDesign,
                                          String sddTasks) {
    }
}
