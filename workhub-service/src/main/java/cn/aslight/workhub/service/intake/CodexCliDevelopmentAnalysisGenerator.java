package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.DevelopmentWorkItemDraft;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.system.UserOptionResponse;
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
                      "estimatedEffort": { "type": ["string", "null"] },
                      "ownerUserName": { "type": ["string", "null"] }
                    },
                    "required": ["title", "description", "estimatedEffort", "ownerUserName"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["summary", "requirementChangePoints", "impactedModules", "risks", "questions", "workItems"],
              "additionalProperties": false
            }
            """;

    private final CodexCliClient codexCliClient;
    private final ChinaWorkdayCalendar chinaWorkdayCalendar;
    private final ObjectMapper objectMapper;

    public CodexCliDevelopmentAnalysisGenerator(CodexCliClient codexCliClient,
                                                ChinaWorkdayCalendar chinaWorkdayCalendar,
                                                ObjectMapper objectMapper) {
        this.codexCliClient = codexCliClient;
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
        return execute(buildPrompt(entity, structuredData, project, repositoryBundle, developers, requirementMarkdownContext, knowledgeBaseContext, null), project, repositoryBundle, developers);
    }

    public DevelopmentAnalysisDraft adjust(DevelopmentAnalysisDraft currentDraft,
                                           String userMessage,
                                           ProjectDetailResponse project,
                                           GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                           List<UserOptionResponse> developers) {
        return execute(buildAdjustmentPrompt(currentDraft, userMessage, developers, repositoryBundle), project, repositoryBundle, developers);
    }

    private DevelopmentAnalysisDraft execute(String prompt,
                                             ProjectDetailResponse project,
                                             GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                             List<UserOptionResponse> developers) {
        if (!codexCliClient.isEnabled()) {
            throw new IllegalArgumentException("Codex CLI 未启用，不能执行代码影响分析");
        }
        CodexCliClient.CodexCliResult result = codexCliClient.execute(new CodexCliClient.CodexCliRequest(
                repositoryBundle.localRoot(),
                repositoryBundle.repositories().stream().map(item -> item.localPath().toString()).toList(),
                List.of(),
                OUTPUT_SCHEMA,
                prompt
        ));
        if (!result.succeeded()) {
            throw new IllegalArgumentException(result.failureSummary());
        }
        try {
            RawDevelopmentAnalysis raw = objectMapper.readValue(result.outputJson(), RawDevelopmentAnalysis.class);
            List<DevelopmentWorkItemDraft> workItems = normalizeWorkItems(raw.workItems(), project, repositoryBundle);
            int totalEffortHours = sumEstimatedEffortHours(workItems);
            return new DevelopmentAnalysisDraft(
                    "DRAFT",
                    project.group(),
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
                    chinaWorkdayCalendar.estimateFinishDate(LocalDate.now(), totalEffortHours),
                    null,
                    null,
                    "RESERVED",
                    "禅道同步接口已预留，当前不会调用禅道。",
                    LocalDateTime.now().toString(),
                    "Codex CLI"
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
                请按“先阅读需求材料 Markdown，再提炼需求点，再判断是否需要查阅项目组代码，最后拆解研发任务”的工作流，生成可交付任务评估草稿和工时估算。

                分析流程：
                1. 第一步只阅读“需求 Markdown 文件”。该文件只是把需求截图、附件、文档、结构化字段和原始内容归档到一起，不是需求点总结；这一步只建立对完整材料的理解，不读取代码，不输出任务项，不按 Controller/Service/DAO 或页面目录拆任务。
                2. 第二步基于“需求 Markdown 文件”提炼并输出需求点 requirementChangePoints；需求点必须来自材料本身，不把实现方式、系统名、页面名、接口名当需求点。
                3. 第三步由你自行判断是否需要查阅项目组 Git 仓库代码：如果需求已足够明确，可以直接拆解研发任务；如果需要确认现有系统边界、接口、配置、表或页面，再按需查阅代码。
                4. 第四步仅根据需求点输出研发任务 workItems；每个任务输出 title、description、estimatedEffort、ownerUserName。

                约束：
                1. 只生成分析草稿，不修改代码，不创建工作项。
                2. 必须先完成材料理解，再输出需求点 requirementChangePoints，再根据需求点列出研发任务。
                3. 研发任务只输出 title、description、estimatedEffort、ownerUserName，不输出需求变化点、任务类型、改动对象、改动点、判断依据、系统标签、置信度、优先级、预计开始日期、截止日期、风险。
                4. 预估工时必须是人类交付工时，配置、数据、代码、脚本、验证都可以估工时，统一用小时，例如 1h、4h、8h。
                5. 如果缺少配置中心 key、表结构、接口说明、数据口径或业务边界，请写入 questions，不要把证据不足的判断硬拆成代码任务。
                6. requirementChangePoints 必须从业务需求角度总结“需求点”，要求内敛、互斥、无重复：每一项只描述一个业务变化，不写实现方式，不按系统/页面/接口拆分，不把同一变化用近义句重复表达；不限制条数，但宁可少而准，不要为了凑数泛化拆碎。
                7. title 必须是一件独立可执行研发任务的摘要，例如“补充平台账户切换配置”“调整账单账户取数逻辑”“验证商户端收款展示”，不要写“实现接口”“修改 Service”这类代码层任务名。
                8. estimatedEffort 是单个任务的人类交付工时，统一用小时，例如 1h、4h、8h；不要估算机器运行时间、AI处理时间或等待时间。
                9. ownerUserName 只能从候选研发人员中选择；如果无法判断负责人，可以为 null。
                10. 如遇到业务口径、历史背景、系统边界不确定，可以参考“项目知识库参考”；但如果知识库与需求或代码冲突，以需求和代码为准，并把冲突或疑问写入 questions。

                项目信息：
                - 项目：%s
                - 项目组：%s
                - 负责人：%s

                候选研发人员：
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
                project.group(),
                project.ownerUserName(),
                formatDevelopers(developers),
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
                    请根据用户反馈重新理解需求变化，先形成待验证问题，再结合当前草稿和相关代码证据调整研发工作项草稿，并重新输出完整草稿。

                    约束：
                    1. 只调整工作项草稿，不修改代码，不创建工作项。
                    2. 先维护完整 requirementChangePoints，再由你自行判断是否需要查阅项目组代码；不要按代码目录或技术层级直接拆任务。
                    3. 根据需求点逐个拆解独立可执行研发任务，workItems 只输出 title、description、estimatedEffort、ownerUserName。
                    4. 如果用户反馈指出原草稿偏离实际评估，要优先复核需求，必要时按需查阅代码，删除无依据或不必要的研发任务。
                    5. 保留合理的 risks、questions。

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
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null,
                        List.of(),
                        EffortUnitNormalizer.normalizeEffort(item.estimatedEffort()),
                        trimToNull(item.ownerUserName()),
                        null,
                        null,
                        null,
                        null,
                        null
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
        if (equalsIgnoreCase(normalized, project == null ? null : project.group())
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
