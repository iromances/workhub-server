package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeClarificationAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.model.intake.IntakeClarificationAnalysisEntity;
import cn.aslight.workhub.model.intake.IntakeClarificationAnalysisResponse;
import cn.aslight.workhub.model.intake.IntakeClarificationItem;
import cn.aslight.workhub.model.intake.IntakeClarificationReplyRequest;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 研发需求澄清分析与逐项回复服务。
 */
@Service
public class IntakeClarificationAnalysisService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String MESSAGE_PENDING = "需求澄清分析已提交，系统正在后台拉取 GitLab 最新代码并调用 AI 分析。";
    private static final String MESSAGE_RUNNING = "需求澄清分析正在执行，请稍后刷新查看结果。";
    private static final Duration EXECUTION_STALE_TIMEOUT = Duration.ofMinutes(30);

    private final IntakeMapper intakeMapper;
    private final IntakeHistoryMapper intakeHistoryMapper;
    private final IntakeClarificationAnalysisMapper clarificationAnalysisMapper;
    private final ProjectMapper projectMapper;
    private final GitlabRepositoryService gitlabRepositoryService;
    private final ProjectKnowledgeBaseService projectKnowledgeBaseService;
    private final CodexCliClarificationAnalysisGenerator clarificationGenerator;
    private final DevelopmentAnalysisService developmentAnalysisService;
    private final Executor workhubTaskExecutor;
    private final ObjectMapper objectMapper;

    public IntakeClarificationAnalysisService(IntakeMapper intakeMapper,
                                              IntakeHistoryMapper intakeHistoryMapper,
                                              IntakeClarificationAnalysisMapper clarificationAnalysisMapper,
                                              ProjectMapper projectMapper,
                                              GitlabRepositoryService gitlabRepositoryService,
                                              ProjectKnowledgeBaseService projectKnowledgeBaseService,
                                              CodexCliClarificationAnalysisGenerator clarificationGenerator,
                                              DevelopmentAnalysisService developmentAnalysisService,
                                              @Qualifier("workhubTaskExecutor") Executor workhubTaskExecutor,
                                              ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.intakeHistoryMapper = intakeHistoryMapper;
        this.clarificationAnalysisMapper = clarificationAnalysisMapper;
        this.projectMapper = projectMapper;
        this.gitlabRepositoryService = gitlabRepositoryService;
        this.projectKnowledgeBaseService = projectKnowledgeBaseService;
        this.clarificationGenerator = clarificationGenerator;
        this.developmentAnalysisService = developmentAnalysisService;
        this.workhubTaskExecutor = workhubTaskExecutor;
        this.objectMapper = objectMapper;
    }

    public IntakeClarificationAnalysisResponse detail(Long intakeId) {
        IntakeClarificationAnalysisEntity entity = clarificationAnalysisMapper.findLatestByIntakeId(intakeId);
        return entity == null ? null : toResponse(entity);
    }

    public IntakeClarificationAnalysisResponse analyze(Long intakeId, String operatorUserName) {
        IntakeRecordEntity intake = requireIntake(intakeId);
        IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
        requireDevelopmentRequirement(structuredData);
        String currentStatus = IntakeDemandStatusRules.resolve(intake, structuredData);
        if (!IntakeDemandStatusRules.CLARIFYING.equals(currentStatus)) {
            throw new IllegalArgumentException("仅待澄清研发需求允许生成澄清分析");
        }
        IntakeClarificationAnalysisEntity existing = clarificationAnalysisMapper.findLatestByIntakeId(intakeId);
        if (existing != null && isExecutionInProgress(existing.getAnalysisStatus()) && !isStaleExecution(existing)) {
            return toResponse(existing);
        }
        IntakeClarificationAnalysisEntity entity = upsertExecutionState(
                existing,
                intakeId,
                resolveBusinessLine(structuredData),
                STATUS_PENDING,
                MESSAGE_PENDING,
                null,
                operatorUserName
        );
        workhubTaskExecutor.execute(() -> executeAnalysis(entity.getId(), intakeId, operatorUserName));
        return toResponse(entity);
    }

    @Transactional
    public IntakeClarificationAnalysisResponse reply(Long intakeId,
                                                     IntakeClarificationReplyRequest request,
                                                     String operatorUserName) {
        IntakeClarificationAnalysisEntity entity = requireAnalysis(intakeId);
        if (isExecutionInProgress(entity.getAnalysisStatus())) {
            throw new IllegalArgumentException("需求澄清分析还在执行中，请稍后再回复");
        }
        List<IntakeClarificationItem> items = readItems(entity.getItemsJson());
        String itemType = normalizeItemType(request.itemType());
        int itemIndex = request.itemIndex();
        String responseText = trimToNull(request.responseText());
        if ("QUESTION".equals(itemType) && responseText == null) {
            throw new IllegalArgumentException("待确认项必须填写回复内容");
        }
        List<IntakeClarificationItem> updatedItems = new ArrayList<>();
        boolean matched = false;
        String respondedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        for (IntakeClarificationItem item : items) {
            if (item != null && itemIndex == safeIndex(item.index()) && itemType.equals(normalizeItemType(item.itemType()))) {
                matched = true;
                updatedItems.add(new IntakeClarificationItem(
                        item.index(),
                        item.itemType(),
                        item.title(),
                        item.description(),
                        item.evidence(),
                        "QUESTION".equals(itemType) ? "RESPONDED" : "ACKNOWLEDGED",
                        "QUESTION".equals(itemType) ? responseText : firstNonBlank(responseText, "已确认风险"),
                        firstNonBlank(operatorUserName, "system"),
                        respondedAt
                ));
            } else {
                updatedItems.add(item);
            }
        }
        if (!matched) {
            throw new IllegalArgumentException("澄清项不存在");
        }
        entity.setItemsJson(writeItemsJson(updatedItems));
        entity.setAnalysisStatus(resolveDraftStatus(updatedItems));
        entity.setAnalysisMessage(null);
        entity.setUpdatedBy(operatorUserName);
        clarificationAnalysisMapper.update(entity);
        recordHistory(intakeId,
                "回复需求澄清项",
                ("QUESTION".equals(itemType) ? "待确认项" : "风险项") + " #" + (itemIndex + 1) + " 已处理。",
                operatorUserName);
        return toResponse(clarificationAnalysisMapper.findLatestByIntakeId(intakeId));
    }

    public void triggerDevelopmentAnalysis(Long intakeId, String operatorUserName) {
        developmentAnalysisService.analyze(intakeId, operatorUserName);
    }

    void executeAnalysis(Long analysisId, Long intakeId, String operatorUserName) {
        IntakeClarificationAnalysisEntity running = requireAnalysisEntity(analysisId);
        running.setAnalysisStatus(STATUS_RUNNING);
        running.setAnalysisMessage(MESSAGE_RUNNING);
        running.setUpdatedBy(operatorUserName);
        clarificationAnalysisMapper.updateExecutionState(running);
        try {
            IntakeRecordEntity intake = requireIntake(intakeId);
            IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
            requireDevelopmentRequirement(structuredData);
            ProjectDetailResponse project = resolveProjectForGroup(structuredData);
            GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle = gitlabRepositoryService.resolveAndFetchGroup(project);
            ProjectKnowledgeBaseService.RequirementKnowledgeNote requirementNote =
                    projectKnowledgeBaseService.upsertRequirementIterationNote(intake, structuredData, project);
            String knowledgeBaseContext = projectKnowledgeBaseService.buildContext(structuredData, project);
            List<IntakeClarificationItem> items = clarificationGenerator.generate(
                    intake,
                    structuredData,
                    project,
                    repositoryBundle,
                    requirementNote.asPromptContext(),
                    knowledgeBaseContext
            );
            IntakeClarificationAnalysisEntity completed = requireAnalysisEntity(analysisId);
            completed.setBusinessLine(project.businessLine());
            completed.setAnalysisStatus(resolveDraftStatus(items));
            completed.setAnalysisMessage(items.isEmpty() ? "AI 未发现需要人工澄清的事项，可直接确认澄清完成。" : null);
            completed.setItemsJson(writeItemsJson(items));
            completed.setUpdatedBy(operatorUserName);
            clarificationAnalysisMapper.update(completed);
            recordHistory(intakeId,
                    "生成需求澄清项",
                    "AI 已基于需求材料和 GitLab 代码生成澄清项，共 " + items.size() + " 项。",
                    operatorUserName);
        } catch (Exception ex) {
            IntakeClarificationAnalysisEntity failed = requireAnalysisEntity(analysisId);
            failed.setAnalysisStatus(STATUS_FAILED);
            failed.setAnalysisMessage(summarizeException(ex));
            failed.setUpdatedBy(operatorUserName);
            clarificationAnalysisMapper.updateExecutionState(failed);
            recordHistory(intakeId, "需求澄清分析失败", failed.getAnalysisMessage(), operatorUserName);
        }
    }

    private IntakeClarificationAnalysisEntity upsertExecutionState(IntakeClarificationAnalysisEntity existing,
                                                                   Long intakeId,
                                                                   String businessLine,
                                                                   String status,
                                                                   String message,
                                                                   String itemsJson,
                                                                   String operatorUserName) {
        IntakeClarificationAnalysisEntity entity = existing;
        if (entity == null) {
            entity = new IntakeClarificationAnalysisEntity();
            entity.setIntakeId(intakeId);
            entity.setCreatedBy(operatorUserName);
        }
        entity.setBusinessLine(businessLine);
        entity.setAnalysisStatus(status);
        entity.setAnalysisMessage(message);
        entity.setItemsJson(itemsJson);
        entity.setUpdatedBy(operatorUserName);
        if (entity.getId() == null) {
            clarificationAnalysisMapper.insert(entity);
        } else {
            clarificationAnalysisMapper.update(entity);
        }
        return clarificationAnalysisMapper.findLatestByIntakeId(intakeId);
    }

    private ProjectDetailResponse resolveProjectForGroup(IntakeStructuredData structuredData) {
        String businessLine = resolveBusinessLine(structuredData);
        ProjectDetailResponse project = projectMapper.findFirstDetailByBusinessLine(businessLine);
        if (project != null) {
            return project;
        }
        return syntheticProject(businessLine);
    }

    private ProjectDetailResponse syntheticProject(String businessLine) {
        String group = trimToNull(businessLine);
        if (group == null) {
            throw new IllegalArgumentException("请先选择业务线");
        }
        LocalDateTime now = LocalDateTime.now();
        return new ProjectDetailResponse(null, null, group, "BUSINESS_LINE", group, null, "进行中", null, now, now);
    }

    private String resolveBusinessLine(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return null;
        }
        return firstNonBlank(
                structuredData.businessLineCode(),
                firstNonBlank(
                        structuredData.businessLine(),
                        firstNonBlank(structuredData.department(), structuredData.projectHint())
                )
        );
    }

    private IntakeClarificationAnalysisEntity requireAnalysis(Long intakeId) {
        IntakeClarificationAnalysisEntity entity = clarificationAnalysisMapper.findLatestByIntakeId(intakeId);
        if (entity == null) {
            throw new IllegalArgumentException("请先生成需求澄清分析");
        }
        return entity;
    }

    private IntakeClarificationAnalysisEntity requireAnalysisEntity(Long analysisId) {
        IntakeClarificationAnalysisEntity entity = clarificationAnalysisMapper.findById(analysisId);
        if (entity == null) {
            throw new IllegalArgumentException("需求澄清分析不存在");
        }
        return entity;
    }

    private IntakeRecordEntity requireIntake(Long intakeId) {
        IntakeRecordEntity entity = intakeMapper.findById(intakeId);
        if (entity == null) {
            throw new IllegalArgumentException("需求不存在");
        }
        return entity;
    }

    private void requireDevelopmentRequirement(IntakeStructuredData structuredData) {
        if (structuredData == null || !"研发需求".equals(structuredData.requirementType())) {
            throw new IllegalArgumentException("只有研发需求支持需求澄清分析");
        }
    }

    private boolean isResolved(IntakeClarificationItem item) {
        String itemType = normalizeItemType(item.itemType());
        String status = trimToNull(item.status());
        return ("QUESTION".equals(itemType) && "RESPONDED".equals(status))
                || ("RISK".equals(itemType) && "ACKNOWLEDGED".equals(status));
    }

    private String resolveDraftStatus(List<IntakeClarificationItem> items) {
        return items == null || items.isEmpty() || items.stream().allMatch(this::isResolved) ? STATUS_CONFIRMED : STATUS_DRAFT;
    }

    private boolean isExecutionInProgress(String status) {
        return STATUS_PENDING.equals(status) || STATUS_RUNNING.equals(status);
    }

    private boolean isStaleExecution(IntakeClarificationAnalysisEntity entity) {
        if (entity == null || !isExecutionInProgress(entity.getAnalysisStatus())) {
            return false;
        }
        LocalDateTime updatedAt = entity.getUpdatedAt();
        if (updatedAt == null) {
            return true;
        }
        return updatedAt.plus(EXECUTION_STALE_TIMEOUT).isBefore(LocalDateTime.now());
    }

    private IntakeClarificationAnalysisResponse toResponse(IntakeClarificationAnalysisEntity entity) {
        return new IntakeClarificationAnalysisResponse(
                entity.getId(),
                entity.getIntakeId(),
                entity.getAnalysisStatus(),
                entity.getAnalysisMessage(),
                readItems(entity.getItemsJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private IntakeStructuredData readStructuredData(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, IntakeStructuredData.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("结构化需求解析失败", ex);
        }
    }

    private List<IntakeClarificationItem> readItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<IntakeClarificationItem>>() {
            });
        } catch (JacksonException ex) {
            throw new IllegalStateException("需求澄清项解析失败", ex);
        }
    }

    private String writeItemsJson(List<IntakeClarificationItem> items) {
        try {
            return objectMapper.writeValueAsString(items == null ? List.of() : items);
        } catch (JacksonException ex) {
            throw new IllegalStateException("需求澄清项写入失败", ex);
        }
    }

    private void recordHistory(Long intakeId, String summary, String detail, String operatorUserName) {
        IntakeHistoryEntity entity = new IntakeHistoryEntity();
        entity.setIntakeId(intakeId);
        entity.setActionType("UPDATE");
        entity.setActionSummary(summary);
        entity.setDetailText(detail);
        entity.setOperatorUserName(firstNonBlank(operatorUserName, "system"));
        intakeHistoryMapper.insert(entity);
    }

    private String normalizeItemType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("澄清项类型不能为空");
        }
        String upper = normalized.toUpperCase();
        if (!List.of("QUESTION", "RISK").contains(upper)) {
            throw new IllegalArgumentException("澄清项类型不合法");
        }
        return upper;
    }

    private int safeIndex(Integer index) {
        return index == null ? -1 : index;
    }

    private String firstNonBlank(String first, String second) {
        String normalized = trimToNull(first);
        return normalized == null ? trimToNull(second) : normalized;
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
}
