package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.DevelopmentWorkItemDraft;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Codex CLI 的研发代码影响分析生成器。
 */
@Component
public class CodexCliDevelopmentAnalysisGenerator {

    private static final String ANALYZE_USE_CASE_CODE = "intake.development.analyze";
    private static final String ADJUST_USE_CASE_CODE = "intake.development.adjust";
    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
                "properties": {
                "summary": { "type": ["string", "null"] },
                "requirementChangePoints": { "type": "array", "items": { "type": "string" } },
                "impactedModules": { "type": "array", "items": { "type": "string" } },
                "risks": { "type": "array", "items": { "type": "string" } },
                "questions": { "type": "array", "items": { "type": "string" } },
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
              "required": ["summary", "requirementChangePoints", "impactedModules", "risks", "questions", "workItems"],
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
                buildPrompt(entity, structuredData, project, repositoryBundle, developers, requirementMarkdownContext, knowledgeBaseContext, null),
                project,
                repositoryBundle,
                developers
        );
    }

    public DevelopmentAnalysisDraft adjust(DevelopmentAnalysisDraft currentDraft,
                                           String userMessage,
                                           ProjectDetailResponse project,
                                           GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                           List<UserOptionResponse> developers) {
        return execute(
                ADJUST_USE_CASE_CODE,
                buildAdjustmentPrompt(currentDraft, userMessage, developers, repositoryBundle),
                project,
                repositoryBundle,
                developers
        );
    }

    private DevelopmentAnalysisDraft execute(String useCaseCode,
                                             String prompt,
                                             ProjectDetailResponse project,
                                             GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                             List<UserOptionResponse> developers) {
        if (!aiGatewayClient.isConfigured(useCaseCode)) {
            throw new IllegalArgumentException("AI 场景未配置或已停用，不能执行代码影响分析");
        }
        AiGatewayResult result = aiGatewayClient.executeStructured(AiGatewayRequest.structured(
                useCaseCode,
                prompt,
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
                    safeList(raw.risks()),
                    safeList(raw.questions()),
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
                    null
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("代码影响分析结果解析失败：" + summarizeException(ex), ex);
        }
    }

    private String buildPrompt(IntakeRecordEntity entity,
                               IntakeStructuredData structuredData,
                               ProjectDetailResponse project,
                               GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                               List<UserOptionResponse> developers,
                               String requirementMarkdownContext,
                               String knowledgeBaseContext,
                               String extraInstruction) {
        return """
                你是 WorkHub 的需求评估与交付方案架构师。
                输出必须符合 JSON Schema，只生成评估草稿，不修改代码、不创建工作项。

                工作流：
                1. 先读需求 Markdown 和补充材料，提炼业务需求点 requirementChangePoints。
                2. 再查业务线 Git 代码，确认现有系统、页面、接口、配置、表、定时任务和可复用逻辑；纯文案/运营需求可不查代码，但要在 evidenceRefs 说明。
                3. 最后按可排期研发任务拆 workItems，不按 Controller/Service/DAO 这类代码层级拆。

                任务要求：
                - title 用 Jira/禅道风格，格式类似“系统名 + 动词 + 业务对象 + 能力/规则”。
                - systemTags 只能写真实涉及系统，优先从“可选改造系统”中选择。
                - changePoints 表示“改地点”，必须按有序列表顺序列出，覆盖配置/SQL、接口/服务、任务调度、页面/权限、导出/核销、幂等/验证等真实工作。
                - relatedFiles/evidenceRefs 写代码或材料证据；涉及系统改造却没有代码证据时，confidence 置 LOW，并在 risks/questions 说明。
                - questions 只写真正影响实现或验收的问题，不要把需求已明确的事实列为疑问。
                - estimatedEffort 用小时，例如 1h、4h、8h；ownerUserName 只能从候选研发人员中选，无法判断填 null。

                项目信息：
                - 项目：%s
                - 业务线：%s
                - 负责人：%s

                候选研发人员：
                %s

                可选改造系统：
                %s

                需求 Markdown 文件：
                %s

                需求信息（仅用于和需求 Markdown 交叉校验，不作为替代输入）：
                - 审批编号：%s
                - 需求名称：%s
                - 需求描述：%s
                - 业务线：%s
                - 备注：%s

                结构化识别字段：
                %s

                附件内容摘要：
                %s

                项目知识库参考：
                %s

                原始内容：
                %s

                %s
                """.formatted(
                project.name(),
                project.businessLine(),
                project.ownerUserName(),
                formatDevelopers(developers),
                formatAvailableSystemTags(repositoryBundle),
                value(requirementMarkdownContext),
                value(structuredData.approvalCode()),
                value(structuredData.requirementNameOrTitle()),
                value(structuredData.requirementSummary()),
                value(structuredData.businessLine()),
                value(structuredData.remark()),
                formatStructuredFields(structuredData.fields()),
                formatAttachmentSummaries(structuredData.attachmentSummaries()),
                value(knowledgeBaseContext),
                entity.getRawContent() == null ? "" : entity.getRawContent(),
                extraInstruction == null ? "" : extraInstruction
        );
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

    private String buildAdjustmentPrompt(DevelopmentAnalysisDraft currentDraft,
                                         String userMessage,
                                         List<UserOptionResponse> developers,
                                         GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle) {
        try {
            return """
                    你是 WorkHub 的研发拆解调整助手。
                    根据用户反馈调整当前研发评估草稿，并重新输出完整 JSON 草稿；不要修改代码、不要创建工作项。

                    调整规则：
                    - 先修正 requirementChangePoints，再调整 workItems。
                    - 任务按可排期交付项拆，不按 Controller/Service/DAO 等代码层拆。
                    - title 用 Jira/禅道风格；systemTags 写真实涉及系统；changePoints 表示“改地点”，必须按有序列表顺序列出。
                    - relatedFiles/evidenceRefs 保留或补充代码证据；删除无依据、过细或偏离需求的任务。
                    - questions 只保留真正影响实现或验收的问题，不重复追问需求已明确的事实。

                    候选研发人员：
                    %s

                    当前草稿：
                    %s

                    用户反馈：
                    %s
                    """.formatted(
                    formatDevelopers(developers),
                    objectMapper.writeValueAsString(currentDraft),
                    userMessage
            );
        } catch (Exception ex) {
            throw new IllegalStateException("研发拆解调整 prompt 生成失败", ex);
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
                                          List<String> risks,
                                          List<String> questions,
                                          List<DevelopmentWorkItemDraft> workItems) {
    }
}
