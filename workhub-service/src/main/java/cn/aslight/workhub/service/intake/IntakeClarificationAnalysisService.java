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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 研发需求澄清分析与逐项回复服务。
 */
@Service
public class IntakeClarificationAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IntakeClarificationAnalysisService.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String MESSAGE_RUNNING = "需求澄清分析正在执行，请稍后刷新查看结果。";
    private static final String MESSAGE_SUPERSEDED = "超时未完成，已被新一次澄清分析取代。";
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
    private final TransactionTemplate analysisTransaction;

    public IntakeClarificationAnalysisService(IntakeMapper intakeMapper,
                                              IntakeHistoryMapper intakeHistoryMapper,
                                              IntakeClarificationAnalysisMapper clarificationAnalysisMapper,
                                              ProjectMapper projectMapper,
                                              GitlabRepositoryService gitlabRepositoryService,
                                              ProjectKnowledgeBaseService projectKnowledgeBaseService,
                                              CodexCliClarificationAnalysisGenerator clarificationGenerator,
                                              DevelopmentAnalysisService developmentAnalysisService,
                                              @Qualifier("workhubTaskExecutor") Executor workhubTaskExecutor,
                                              ObjectMapper objectMapper,
                                              PlatformTransactionManager transactionManager) {
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
        this.analysisTransaction = new TransactionTemplate(transactionManager);
        this.analysisTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public IntakeClarificationAnalysisResponse detail(Long intakeId) {
        IntakeClarificationAnalysisEntity entity = clarificationAnalysisMapper.findLatestByIntakeId(intakeId);
        return entity == null ? null : toResponse(entity);
    }

    public List<IntakeClarificationAnalysisResponse> history(Long intakeId) {
        return clarificationAnalysisMapper.listByIntakeId(intakeId).stream()
                .map(this::toResponse)
                .toList();
    }

    public IntakeClarificationAnalysisResponse analyze(Long intakeId, String operatorUserName) {
        PreparedAnalysis prepared = analysisTransaction.execute(status -> prepareAnalysis(intakeId, operatorUserName));
        var entity = prepared.entity();
        if (!prepared.created()) return toResponse(entity);
        try {
            workhubTaskExecutor.execute(() -> executeAnalysis(entity.getId(), intakeId, operatorUserName));
        } catch (RejectedExecutionException ex) {
            String message = "澄清分析任务提交失败，系统繁忙，请稍后重试。";
            analysisTransaction.executeWithoutResult(status -> {
                markAnalysisFailed(entity.getId(), message, operatorUserName);
                recordHistory(intakeId, "需求澄清分析失败", message + "原因：" + summarizeException(ex), operatorUserName);
            });
            throw new IllegalStateException(message, ex);
        }
        return toResponse(entity);
    }

    private record PreparedAnalysis(IntakeClarificationAnalysisEntity entity, boolean created) { }

    private PreparedAnalysis prepareAnalysis(Long intakeId, String operatorUserName) {
        IntakeRecordEntity intake = requireIntake(intakeId);
        IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
        requireDevelopmentRequirement(structuredData);
        String currentStatus = IntakeDemandStatusRules.resolve(intake, structuredData);
        if (!IntakeDemandStatusRules.CLARIFYING.equals(currentStatus)) {
            throw new IllegalArgumentException("仅待澄清研发需求允许生成澄清分析");
        }
        IntakeClarificationAnalysisEntity existing = clarificationAnalysisMapper.findLatestByIntakeId(intakeId);
        if (existing != null && isExecutionInProgress(existing.getAnalysisStatus()) && !isStaleExecution(existing)) {
            return new PreparedAnalysis(existing, false);
        }
        if (existing != null && existing.getItemsJson() != null) {
            projectKnowledgeBaseService.appendAnalysisNote(intakeId, existing.getId(), "重评前保留澄清记录",
                    readItems(existing.getItemsJson()), null, operatorUserName);
        }
        clarificationAnalysisMapper.failInFlightByIntakeId(intakeId, MESSAGE_SUPERSEDED, operatorUserName);
        IntakeClarificationAnalysisEntity entity = insertExecutionState(
                intakeId,
                resolveBusinessLine(structuredData),
                operatorUserName
        );
        return new PreparedAnalysis(entity, true);
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
        scheduleClarificationArchive(intakeId, entity.getId(), updatedItems, null, "回复需求澄清项", operatorUserName);
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
        long startedAt = System.currentTimeMillis();
        log.info("需求澄清分析开始执行 analysisId={}, intakeId={}, operator={}", analysisId, intakeId, operatorUserName);
        try {
            runAnalysis(analysisId, intakeId, operatorUserName);
        } catch (Throwable ex) {
            log.error("需求澄清分析异常终止 analysisId={}, intakeId={}, 耗时={}ms",
                    analysisId, intakeId, System.currentTimeMillis() - startedAt, ex);
            try {
                markAnalysisFailed(analysisId, "分析异常终止：" + summarizeThrowable(ex), operatorUserName);
                recordHistory(intakeId, "需求澄清分析失败",
                        "分析异常终止，已兜底置为失败：" + summarizeThrowable(ex), operatorUserName);
            } catch (Throwable fallbackEx) {
                log.error("需求澄清分析失败状态写入失败 analysisId={}, intakeId={}", analysisId, intakeId, fallbackEx);
            }
            return;
        }
        log.info("需求澄清分析执行结束 analysisId={}, intakeId={}, 耗时={}ms",
                analysisId, intakeId, System.currentTimeMillis() - startedAt);
    }

    private void runAnalysis(Long analysisId, Long intakeId, String operatorUserName) {
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
            var recovery = new cn.aslight.workhub.service.ai.AiResultRecovery(
                    fingerprint -> clarificationAnalysisMapper.findRecoverableOutput(intakeId, fingerprint),
                    (fingerprint, output) -> {
                        if (clarificationAnalysisMapper.saveRawOutput(analysisId, fingerprint, output) != 1)
                            throw new IllegalStateException("分析状态已变化，原始结果未保存，停止整理");
                    }, () -> {
                        if (clarificationAnalysisMapper.completeRawFormatting(analysisId) != 1)
                            throw new IllegalStateException("分析状态已变化，不能保存已过期的整理结果");
                    });
            List<IntakeClarificationItem> items = clarificationGenerator.generate(
                    intake,
                    structuredData,
                    project,
                    repositoryBundle,
                    requirementNote.asPromptContext(),
                    knowledgeBaseContext, recovery
            );
            IntakeClarificationAnalysisEntity completed = requireAnalysisEntity(analysisId);
            completed.setBusinessLineCode(project.businessLineCode());
            completed.setBusinessLine(null);
            completed.setAnalysisStatus(resolveDraftStatus(items));
            completed.setAnalysisMessage(items.isEmpty() ? "AI 未发现需要人工澄清的事项，可直接确认澄清完成。" : null);
            completed.setItemsJson(writeItemsJson(items));
            completed.setUpdatedBy(operatorUserName);
            clarificationAnalysisMapper.update(completed);
            scheduleClarificationArchive(intakeId, analysisId, items, requirementNote.rawRelativePath(),
                    "生成需求澄清项", operatorUserName);
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

    private void scheduleClarificationArchive(Long intakeId, Long analysisId, List<IntakeClarificationItem> items,
                                              String sourcePath, String phase, String operator) {
        projectKnowledgeBaseService.archiveAfterCommit(
                () -> {
                    projectKnowledgeBaseService.appendAnalysisNote(intakeId, analysisId, phase, items, sourcePath, operator);
                    recordHistory(intakeId, "澄清归档成功", "已追加 notes，保留历次分析。", operator);
                },
                ex -> recordHistory(intakeId, "澄清归档失败", "业务结果已保存，可独立重试归档：" + ex.getMessage(), operator));
    }

    private IntakeClarificationAnalysisEntity insertExecutionState(Long intakeId,
                                                                   String businessLine,
                                                                   String operatorUserName) {
        IntakeClarificationAnalysisEntity entity = new IntakeClarificationAnalysisEntity();
        entity.setIntakeId(intakeId);
        entity.setBusinessLineCode(resolveBusinessLineCode(businessLine));
        entity.setBusinessLine(null);
        entity.setAnalysisStatus(STATUS_PENDING);
        entity.setAnalysisMessage(null);
        entity.setItemsJson(null);
        entity.setCreatedBy(operatorUserName);
        entity.setUpdatedBy(operatorUserName);
        clarificationAnalysisMapper.insert(entity);
        return clarificationAnalysisMapper.findLatestByIntakeId(intakeId);
    }

    private void markAnalysisFailed(Long analysisId, String message, String operatorUserName) {
        IntakeClarificationAnalysisEntity failed = requireAnalysisEntity(analysisId);
        failed.setAnalysisStatus(STATUS_FAILED);
        failed.setAnalysisMessage(message);
        failed.setUpdatedBy(operatorUserName);
        clarificationAnalysisMapper.updateExecutionState(failed);
    }

    private ProjectDetailResponse resolveProjectForGroup(IntakeStructuredData structuredData) {
        String businessLine = resolveBusinessLine(structuredData);
        ProjectDetailResponse project = projectMapper.findFirstDetailByBusinessLine(businessLine);
        if (project != null) {
            return project;
        }
        return syntheticProject(businessLine);
    }

    private String resolveBusinessLineCode(String businessLine) {
        String normalized = trimToNull(businessLine);
        if (normalized == null) {
            return null;
        }
        ProjectDetailResponse project = projectMapper.findFirstDetailByBusinessLine(normalized);
        if (project != null && trimToNull(project.businessLineCode()) != null) {
            return project.businessLineCode();
        }
        return normalized.matches("BL[0-9]{6}") ? normalized : null;
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
                isRetryable(entity),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                readItems(entity.getItemsJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private boolean isRetryable(IntakeClarificationAnalysisEntity entity) {
        if (entity == null) {
            return true;
        }
        return !isExecutionInProgress(entity.getAnalysisStatus()) || isStaleExecution(entity);
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

    private String summarizeThrowable(Throwable ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getName() : message;
    }
}
