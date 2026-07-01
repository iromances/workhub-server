package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeClarificationAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeWorkItemRelationMapper;
import cn.aslight.workhub.dao.project.ProjectInvolvedSystemMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisChatRequest;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisConfirmResponse;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraftUpdateRequest;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisEntity;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisOwnerUpdateRequest;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisResponse;
import cn.aslight.workhub.model.intake.DevelopmentWorkItemDraft;
import cn.aslight.workhub.model.intake.IntakeClarificationAnalysisEntity;
import cn.aslight.workhub.model.intake.IntakeClarificationItem;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeWorkItemRelationEntity;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemEntity;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.model.workitem.WorkItemCreateRequest;
import cn.aslight.workhub.model.workitem.WorkItemDetailResponse;
import cn.aslight.workhub.service.workitem.WorkItemService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 研发需求代码影响分析与工作项草稿服务。
 */
@Service
public class DevelopmentAnalysisService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String MESSAGE_PENDING = "任务评估已提交，系统正在后台拉取 GitLab 最新代码并调用 AI 分析。";
    private static final String MESSAGE_RUNNING = "任务评估正在执行，请稍后刷新查看结果。";
    private static final Pattern NORMALIZED_HOUR_PATTERN = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)h$");
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ISO_LOCAL_DATE
    );

    private final IntakeMapper intakeMapper;
    private final IntakeHistoryMapper intakeHistoryMapper;
    private final DevelopmentAnalysisMapper developmentAnalysisMapper;
    private final IntakeWorkItemRelationMapper intakeWorkItemRelationMapper;
    private final IntakeClarificationAnalysisMapper intakeClarificationAnalysisMapper;
    private final ProjectMapper projectMapper;
    private final ProjectInvolvedSystemMapper involvedSystemMapper;
    private final UserMapper userMapper;
    private final GitlabRepositoryService gitlabRepositoryService;
    private final ProjectKnowledgeBaseService projectKnowledgeBaseService;
    private final CodexCliDevelopmentAnalysisGenerator analysisGenerator;
    private final WorkItemService workItemService;
    private final Executor workhubTaskExecutor;
    private final ObjectMapper objectMapper;

    @Autowired
    public DevelopmentAnalysisService(IntakeMapper intakeMapper,
                                      IntakeHistoryMapper intakeHistoryMapper,
                                      DevelopmentAnalysisMapper developmentAnalysisMapper,
                                      IntakeWorkItemRelationMapper intakeWorkItemRelationMapper,
                                      IntakeClarificationAnalysisMapper intakeClarificationAnalysisMapper,
                                      ProjectMapper projectMapper,
                                      ProjectInvolvedSystemMapper involvedSystemMapper,
                                      UserMapper userMapper,
                                      GitlabRepositoryService gitlabRepositoryService,
                                      ProjectKnowledgeBaseService projectKnowledgeBaseService,
                                      CodexCliDevelopmentAnalysisGenerator analysisGenerator,
                                      WorkItemService workItemService,
                                      @Qualifier("workhubTaskExecutor") Executor workhubTaskExecutor,
                                      ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.intakeHistoryMapper = intakeHistoryMapper;
        this.developmentAnalysisMapper = developmentAnalysisMapper;
        this.intakeWorkItemRelationMapper = intakeWorkItemRelationMapper;
        this.intakeClarificationAnalysisMapper = intakeClarificationAnalysisMapper;
        this.projectMapper = projectMapper;
        this.involvedSystemMapper = involvedSystemMapper;
        this.userMapper = userMapper;
        this.gitlabRepositoryService = gitlabRepositoryService;
        this.projectKnowledgeBaseService = projectKnowledgeBaseService;
        this.analysisGenerator = analysisGenerator;
        this.workItemService = workItemService;
        this.workhubTaskExecutor = workhubTaskExecutor;
        this.objectMapper = objectMapper;
    }

    DevelopmentAnalysisService(IntakeMapper intakeMapper,
                               IntakeHistoryMapper intakeHistoryMapper,
                               DevelopmentAnalysisMapper developmentAnalysisMapper,
                               ProjectMapper projectMapper,
                               ProjectInvolvedSystemMapper involvedSystemMapper,
                               UserMapper userMapper,
                               GitlabRepositoryService gitlabRepositoryService,
                               ProjectKnowledgeBaseService projectKnowledgeBaseService,
                               CodexCliDevelopmentAnalysisGenerator analysisGenerator,
                               WorkItemService workItemService,
                               @Qualifier("workhubTaskExecutor") Executor workhubTaskExecutor,
                               ObjectMapper objectMapper) {
        this(
                intakeMapper,
                intakeHistoryMapper,
                developmentAnalysisMapper,
                new NoopIntakeWorkItemRelationMapper(),
                new NoopIntakeClarificationAnalysisMapper(),
                projectMapper,
                involvedSystemMapper,
                userMapper,
                gitlabRepositoryService,
                projectKnowledgeBaseService,
                analysisGenerator,
                workItemService,
                workhubTaskExecutor,
                objectMapper
        );
    }

    private static class NoopIntakeWorkItemRelationMapper implements IntakeWorkItemRelationMapper {
        @Override
        public int upsert(IntakeWorkItemRelationEntity entity) {
            return 0;
        }

        @Override
        public List<cn.aslight.workhub.model.intake.IntakeRelatedWorkItemResponse> findByIntakeId(Long intakeId) {
            return List.of();
        }

        @Override
        public IntakeWorkItemRelationEntity findLatestByWorkItemId(Long workItemId) {
            return null;
        }
    }

    private static class NoopIntakeClarificationAnalysisMapper implements IntakeClarificationAnalysisMapper {
        @Override
        public IntakeClarificationAnalysisEntity findById(Long id) {
            return null;
        }

        @Override
        public IntakeClarificationAnalysisEntity findLatestByIntakeId(Long intakeId) {
            return null;
        }

        @Override
        public int insert(IntakeClarificationAnalysisEntity entity) {
            return 0;
        }

        @Override
        public int update(IntakeClarificationAnalysisEntity entity) {
            return 0;
        }

        @Override
        public int updateExecutionState(IntakeClarificationAnalysisEntity entity) {
            return 0;
        }
    }

    public DevelopmentAnalysisResponse detail(Long intakeId) {
        DevelopmentAnalysisEntity entity = developmentAnalysisMapper.findLatestByIntakeId(intakeId);
        if (entity != null) {
            return toResponse(entity);
        }
        IntakeRecordEntity intake = requireIntake(intakeId);
        IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
        if (structuredData == null || !"研发需求".equals(structuredData.requirementType())) {
            return null;
        }
        ProjectDetailResponse project = resolveProjectOrNull(structuredData);
        List<String> developerPool = resolveDevelopers(
                        project == null ? structuredData.projectHint() : project.businessLine(),
                        project == null ? intake.getDevelopmentOwnerUserName() : project.ownerUserName()
                ).stream()
                .map(UserOptionResponse::userName)
                .distinct()
                .toList();
        DevelopmentAnalysisDraft draft = new DevelopmentAnalysisDraft(
                STATUS_DRAFT,
                project == null ? structuredData.projectHint() : project.businessLine(),
                project == null ? null : project.id(),
                project == null ? null : project.name(),
                null,
                null,
                null,
                null,
                firstNonBlank(structuredData.requirementSummary(), structuredData.requirementDigest()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                developerPool,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "RESERVED",
                "禅道同步接口已预留，当前不会调用禅道。",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "manual",
                null
        );
        LocalDateTime now = LocalDateTime.now();
        return new DevelopmentAnalysisResponse(0L, intakeId, STATUS_DRAFT, "人工录入研发任务草稿", draft, now, now);
    }

    public DevelopmentAnalysisResponse analyze(Long intakeId, String operatorUserName) {
        return analyze(intakeId, null, operatorUserName);
    }

    public DevelopmentAnalysisResponse analyze(Long intakeId, String businessLineOverride, String operatorUserName) {
        IntakeRecordEntity intake = requireIntake(intakeId);
        IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
        requireDevelopmentRequirement(structuredData);
        requirePendingEvaluation(intake, structuredData, "仅待评估需求允许生成任务评估");
        structuredData = withBusinessLineOverride(structuredData, businessLineOverride);
        IntakeStructuredData nextStructuredData = structuredData;
        String nextStructuredDataJson = writeStructuredDataJson(nextStructuredData);
        intakeMapper.updateStructuredData(intakeId, nextStructuredDataJson);

        DevelopmentAnalysisEntity existing = developmentAnalysisMapper.findLatestByIntakeId(intakeId);
        if (existing != null && isExecutionInProgress(existing.getAnalysisStatus())) {
            return toResponse(existing);
        }

        DevelopmentAnalysisEntity entity = upsertExecutionState(
                existing,
                intakeId,
                trimToNull(nextStructuredData.projectHint()),
                STATUS_PENDING,
                MESSAGE_PENDING,
                null,
                operatorUserName
        );
        Runnable task = () -> executeAnalysis(entity.getId(), intakeId, operatorUserName);
        workhubTaskExecutor.execute(task);
        return toResponse(entity);
    }

    @Transactional
    public DevelopmentAnalysisResponse chat(Long intakeId, DevelopmentAnalysisChatRequest request, String operatorUserName) {
        DevelopmentAnalysisEntity entity = requireAnalysis(intakeId);
        DevelopmentAnalysisDraft currentDraft = readDraft(entity.getDraftJson());
        if (currentDraft == null) {
            throw new IllegalArgumentException("当前任务评估尚未生成可调整的草稿");
        }
        ProjectDetailResponse project = resolveProjectForDraft(currentDraft);
        List<UserOptionResponse> developers = resolveDevelopers(project.businessLine(), project.ownerUserName());
        GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle = gitlabRepositoryService.resolveAndFetchGroup(project);
        DevelopmentAnalysisDraft adjusted = analysisGenerator.adjust(currentDraft, request.getMessage().trim(), project, repositoryBundle, developers);
        entity.setDraftJson(writeDraftJson(adjusted));
        entity.setAnalysisStatus(STATUS_DRAFT);
        entity.setAnalysisMessage(null);
        entity.setUpdatedBy(operatorUserName);
        developmentAnalysisMapper.update(entity);
        recordHistory(intakeId, "AI 调整研发拆解", request.getMessage().trim(), operatorUserName);
        return toResponse(developmentAnalysisMapper.findLatestByIntakeId(intakeId));
    }

    @Transactional
    public DevelopmentAnalysisConfirmResponse confirm(Long intakeId, String operatorUserName) {
        IntakeRecordEntity intake = requireIntake(intakeId);
        IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
        requirePendingEvaluation(intake, structuredData, "仅待评估需求允许确认研发评估");
        DevelopmentAnalysisEntity entity = requireAnalysis(intakeId);
        if (STATUS_CONFIRMED.equals(entity.getAnalysisStatus())) {
            throw new IllegalArgumentException("当前任务评估已确认，不能重复确认");
        }
        if (!STATUS_DRAFT.equals(entity.getAnalysisStatus())) {
            throw new IllegalArgumentException("当前任务评估尚未生成可确认的草稿");
        }
        DevelopmentAnalysisDraft draft = readDraft(entity.getDraftJson());
        if (draft == null) {
            throw new IllegalArgumentException("当前任务评估尚未生成可确认的草稿");
        }
        validateConfirmableDraft(draft);
        if (draft.businessLine() != null) {
            validateDraftSystemTags(draft, draft.businessLine(), draft);
        }
        List<Long> workItemIds = new ArrayList<>();
        if (draft.projectId() != null && draft.workItems() != null && !draft.workItems().isEmpty()) {
            for (int i = 0; i < draft.workItems().size(); i++) {
                DevelopmentWorkItemDraft item = draft.workItems().get(i);
                WorkItemDetailResponse created = workItemService.create(toWorkItemCreateRequest(draft, item), operatorUserName);
                workItemIds.add(created.id());
                linkWorkItem(intakeId, created.id(), i);
            }
        }
        intakeMapper.updateManagementFields(
                intakeId,
                writeStructuredDataJson(structuredData),
                intake.getDevelopmentOwnerUserName(),
                IntakeDemandStatusRules.PENDING_SCHEDULING
        );
        developmentAnalysisMapper.updateStatus(
                entity.getId(),
                STATUS_CONFIRMED,
                null,
                "RESERVED",
                "禅道同步接口已预留，当前未调用禅道。",
                operatorUserName
        );
        String historySummary = workItemIds.isEmpty()
                ? "已确认研发评估，未创建正式工作项。"
                : "已创建正式工作项：" + workItemIds;
        recordHistory(intakeId, "确认研发评估", historySummary, operatorUserName);
        return new DevelopmentAnalysisConfirmResponse(intakeId, workItemIds, "RESERVED", historySummary);
    }

    private void validateConfirmableDraft(DevelopmentAnalysisDraft draft) {
        requireConfirmEffort(draft.totalEstimatedEffort(), "确认研发评估前必须填写总评估工时", "总评估工时格式不正确，请输入如 8h 或 1d");
        requireConfirmEffort(draft.testingEstimatedEffort(), "确认研发评估前必须填写测试工时", "测试工时格式不正确，请输入如 4h 或 0.5d");
        validateOptionalWorkItemEfforts(draft.workItems());
    }

    private void requireConfirmEffort(String value, String blankMessage, String formatMessage) {
        String normalized = EffortUnitNormalizer.normalizeEffort(value);
        if (normalized == null) {
            throw new IllegalArgumentException(blankMessage);
        }
        if (parseNormalizedHours(normalized) == null) {
            throw new IllegalArgumentException(formatMessage);
        }
    }

    @Transactional
    public DevelopmentAnalysisResponse updateOwners(Long intakeId,
                                                    DevelopmentAnalysisOwnerUpdateRequest request,
                                                    String operatorUserName) {
        DevelopmentAnalysisEntity entity = requireAnalysis(intakeId);
        DevelopmentAnalysisDraft draft = readDraft(entity.getDraftJson());
        if (draft == null) {
            throw new IllegalArgumentException("当前任务评估尚未生成可修改的草稿");
        }
        if (draft.workItems() == null || draft.workItems().isEmpty()) {
            throw new IllegalArgumentException("当前没有可修改的工作项草稿");
        }

        Map<Integer, String> ownerByIndex = new LinkedHashMap<>();
        for (DevelopmentAnalysisOwnerUpdateRequest.WorkItemOwner item : request.workItems()) {
            if (item.index() == null || item.index() < 0 || item.index() >= draft.workItems().size()) {
                throw new IllegalArgumentException("任务序号不合法");
            }
            ownerByIndex.put(item.index(), trimToNull(item.ownerUserName()));
        }

        List<String> developerPool = new ArrayList<>(draft.developerPool() == null ? List.of() : draft.developerPool());
        List<DevelopmentWorkItemDraft> workItems = new ArrayList<>();
        for (int i = 0; i < draft.workItems().size(); i++) {
            DevelopmentWorkItemDraft item = draft.workItems().get(i);
            String owner = ownerByIndex.containsKey(i) ? ownerByIndex.get(i) : item.ownerUserName();
            addDeveloperIfMissing(developerPool, owner);
            persistDeveloperCandidate(owner);
            workItems.add(new DevelopmentWorkItemDraft(
                    item.title(),
                    item.description(),
                    trimToNull(item.requirementChangePoint()),
                    normalizeTaskType(item.taskType()),
                    item.targetResources() == null ? List.of() : item.targetResources(),
                    item.changePoints() == null ? List.of() : item.changePoints(),
                    item.systemTags() == null ? List.of() : item.systemTags(),
                    item.evidenceRefs() == null ? List.of() : item.evidenceRefs(),
                    normalizeConfidence(item.confidence()),
                    item.moduleName(),
                    item.relatedFiles() == null ? List.of() : item.relatedFiles(),
                    item.estimatedEffort(),
                    owner,
                    normalizePriority(item.priority()),
                    normalizeDate(item.plannedStartDate(), "预计开始日期"),
                    normalizeDate(item.plannedEndDate(), "截止日期"),
                    item.dependency(),
                    item.risk()
            ));
        }

        DevelopmentAnalysisDraft updated = new DevelopmentAnalysisDraft(
                resolveEditableAnalysisStatus(entity),
                draft.businessLine(),
                draft.projectId(),
                draft.projectName(),
                draft.repositoryUrl(),
                draft.requirementRawPath(),
                draft.requirementWikiPath(),
                draft.requirementWikiUrl(),
                draft.summary(),
                mergeRequirementChangePoints(draft.requirementChangePoints(), workItems),
                draft.impactedModules() == null ? List.of() : draft.impactedModules(),
                draft.risks() == null ? List.of() : draft.risks(),
                draft.questions() == null ? List.of() : draft.questions(),
                developerPool,
                workItems,
                draft.totalEstimatedEffort(),
                draft.developmentEstimatedEffort(),
                draft.testingEstimatedEffort(),
                draft.plannedDueDate(),
                draft.plannedTestingStartDate(),
                draft.plannedTestingEndDate(),
                draft.plannedReleaseDate(),
                draft.zentaoSyncStatus(),
                draft.zentaoSyncMessage(),
                draft.generatedAt(),
                draft.generator(),
                draft.plannedDevelopmentStartDate()
        );
        entity.setDraftJson(writeDraftJson(updated));
        entity.setAnalysisStatus(updated.status());
        entity.setAnalysisMessage(null);
        entity.setUpdatedBy(operatorUserName);
        developmentAnalysisMapper.update(entity);
        recordHistory(intakeId, "调整研发负责人", "已更新研发拆解草稿中的任务负责人。", operatorUserName);
        return toResponse(developmentAnalysisMapper.findLatestByIntakeId(intakeId));
    }

    @Transactional
    public DevelopmentAnalysisResponse updateDraft(Long intakeId,
                                                   DevelopmentAnalysisDraftUpdateRequest request,
                                                   String operatorUserName) {
        IntakeRecordEntity intake = requireIntake(intakeId);
        IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
        requireDevelopmentRequirement(structuredData);
        requireEditableDevelopmentTasks(intake, structuredData);
        DevelopmentAnalysisEntity entity = developmentAnalysisMapper.findLatestByIntakeId(intakeId);
        String analysisStatus = resolveEditableAnalysisStatus(entity);
        DevelopmentAnalysisDraft draft = readDraft(entity == null ? null : entity.getDraftJson());
        List<DevelopmentAnalysisDraftUpdateRequest.WorkItemDraft> requestItems =
                request.workItems() == null ? List.of() : request.workItems();
        String businessLine = firstNonBlank(
                request.businessLine(),
                firstNonBlank(draft == null ? null : draft.businessLine(), structuredData.projectHint())
        );
        ProjectDetailResponse project = requestItems.isEmpty() ? null : findBusinessLineProject(businessLine);
        DevelopmentAnalysisDraft existingDraft = draft;

        List<String> developerPool = new ArrayList<>(draft == null || draft.developerPool() == null ? List.of() : draft.developerPool());
        List<DevelopmentWorkItemDraft> workItems = new ArrayList<>();
        for (DevelopmentAnalysisDraftUpdateRequest.WorkItemDraft item : requestItems) {
            if (item == null) {
                throw new IllegalArgumentException("工作项草稿不能为空");
            }
            String title = trimToNull(item.title());
            String description = trimToNull(item.description());
            if (title == null && description == null) {
                throw new IllegalArgumentException("任务项不能为空");
            }
            String plannedStartDate = normalizeDate(item.plannedStartDate(), "预计开始日期");
            String plannedEndDate = normalizeDate(item.plannedEndDate(), "截止日期");
            validateDraftDateRange(plannedStartDate, plannedEndDate);
            String owner = trimToNull(item.ownerUserName());
            addDeveloperIfMissing(developerPool, owner);
            persistDeveloperCandidate(owner);
            List<String> systemTags = sanitizeList(item.systemTags());
            if (systemTags.isEmpty()) {
                throw new IllegalArgumentException("研发任务必须选择涉及系统");
            }
            workItems.add(new DevelopmentWorkItemDraft(
                    firstNonBlank(title, description),
                    description,
                    trimToNull(item.requirementChangePoint()),
                    normalizeTaskType(item.taskType()),
                    sanitizeList(item.targetResources()),
                    sanitizeList(item.changePoints()),
                    systemTags,
                    sanitizeList(item.evidenceRefs()),
                    normalizeConfidence(item.confidence()),
                    trimToNull(item.moduleName()),
                    sanitizeList(item.relatedFiles()),
                    EffortUnitNormalizer.normalizeEffort(item.estimatedEffort()),
                    owner,
                    normalizePriority(item.priority()),
                    plannedStartDate,
                    plannedEndDate,
                    trimToNull(item.dependency()),
                    trimToNull(item.risk())
            ));
        }
        validateWorkItemSystemTags(workItems, businessLineKeyForValidation(project, businessLine), existingDraft);

        validateOptionalWorkItemEfforts(workItems);
        String developmentEstimatedEffort = sumEstimatedEffort(workItems, null);
        String testingEstimatedEffort = EffortUnitNormalizer.normalizeEffort(request.testingEstimatedEffort());
        if (testingEstimatedEffort != null && parseNormalizedHours(testingEstimatedEffort) == null) {
            throw new IllegalArgumentException("测试工时格式不正确，请输入如 4h 或 0.5d");
        }
        String totalEstimatedEffort = sumPlanEffort(developmentEstimatedEffort, testingEstimatedEffort);
        if (totalEstimatedEffort == null) {
            totalEstimatedEffort = EffortUnitNormalizer.normalizeEffort(request.totalEstimatedEffort());
        }
        if (totalEstimatedEffort == null) {
            totalEstimatedEffort = sumEstimatedEffort(workItems, draft == null ? null : draft.totalEstimatedEffort());
        }
        String updatedBusinessLine = project == null ? businessLine : project.businessLine();
        Long updatedProjectId = project == null ? retainedProjectId(draft, updatedBusinessLine) : project.id();
        String updatedProjectName = project == null ? retainedProjectName(draft, updatedBusinessLine) : project.name();
        DevelopmentAnalysisDraft updated = new DevelopmentAnalysisDraft(
                analysisStatus,
                updatedBusinessLine,
                updatedProjectId,
                updatedProjectName,
                draft == null ? null : draft.repositoryUrl(),
                draft == null ? null : draft.requirementRawPath(),
                draft == null ? null : draft.requirementWikiPath(),
                draft == null ? null : draft.requirementWikiUrl(),
                draft == null ? firstNonBlank(structuredData.requirementSummary(), structuredData.requirementDigest()) : draft.summary(),
                mergeRequirementChangePoints(draft == null ? List.of() : draft.requirementChangePoints(), workItems),
                draft == null || draft.impactedModules() == null ? List.of() : draft.impactedModules(),
                draft == null || draft.risks() == null ? List.of() : draft.risks(),
                draft == null || draft.questions() == null ? List.of() : draft.questions(),
                developerPool,
                workItems,
                totalEstimatedEffort,
                developmentEstimatedEffort,
                testingEstimatedEffort,
                null,
                null,
                null,
                null,
                draft == null ? "RESERVED" : draft.zentaoSyncStatus(),
                draft == null ? "禅道同步接口已预留，当前不会调用禅道。" : draft.zentaoSyncMessage(),
                draft == null ? LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : draft.generatedAt(),
                draft == null ? "manual" : draft.generator(),
                null
        );
        if (entity == null) {
            entity = new DevelopmentAnalysisEntity();
            entity.setIntakeId(intakeId);
            entity.setCreatedBy(operatorUserName);
            entity.setZentaoSyncStatus("RESERVED");
            entity.setZentaoSyncMessage("禅道同步接口已预留，当前不会调用禅道。");
        }
        entity.setProjectId(updated.projectId());
        entity.setBusinessLine(updated.businessLine());
        entity.setRepositoryUrl(updated.repositoryUrl());
        entity.setDraftJson(writeDraftJson(updated));
        entity.setAnalysisStatus(updated.status());
        entity.setAnalysisMessage(null);
        entity.setZentaoSyncStatus(updated.zentaoSyncStatus());
        entity.setZentaoSyncMessage(updated.zentaoSyncMessage());
        entity.setUpdatedBy(operatorUserName);
        if (entity.getId() == null) {
            developmentAnalysisMapper.insert(entity);
        } else {
            developmentAnalysisMapper.update(entity);
        }
        recordHistory(intakeId, "调整研发任务", "已人工更新研发任务，共 " + workItems.size() + " 项。", operatorUserName);
        return toResponse(developmentAnalysisMapper.findLatestByIntakeId(intakeId));
    }

    public DevelopmentAnalysisConfirmResponse syncZentao(Long intakeId, String operatorUserName) {
        DevelopmentAnalysisEntity entity = requireAnalysis(intakeId);
        developmentAnalysisMapper.updateStatus(
                entity.getId(),
                entity.getAnalysisStatus(),
                entity.getAnalysisMessage(),
                "RESERVED",
                "禅道同步接口已预留，等待配置真实禅道 API 后启用。",
                operatorUserName
        );
        return new DevelopmentAnalysisConfirmResponse(intakeId, List.of(), "RESERVED", "禅道同步接口已预留，当前未执行外部同步。");
    }

    void executeAnalysis(Long analysisId, Long intakeId, String operatorUserName) {
        DevelopmentAnalysisEntity running = requireAnalysisEntity(analysisId);
        running.setAnalysisStatus(STATUS_RUNNING);
        running.setAnalysisMessage(MESSAGE_RUNNING);
        running.setUpdatedBy(operatorUserName);
        developmentAnalysisMapper.updateExecutionState(running);
        try {
            IntakeRecordEntity intake = requireIntake(intakeId);
            IntakeStructuredData structuredData = readStructuredData(intake.getStructuredDataJson());
            requireDevelopmentRequirement(structuredData);
            ProjectDetailResponse project = resolveProjectForStructuredData(structuredData);
            List<UserOptionResponse> developers = resolveDevelopers(project.businessLine(), project.ownerUserName());
            GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle = gitlabRepositoryService.resolveAndFetchGroup(project);
            ProjectKnowledgeBaseService.RequirementKnowledgeNote requirementNote =
                    projectKnowledgeBaseService.upsertRequirementIterationNote(intake, structuredData, project);
            String knowledgeBaseContext = appendClarificationContext(
                    projectKnowledgeBaseService.buildContext(structuredData, project),
                    readClarificationItems(intakeId)
            );
            DevelopmentAnalysisDraft draft = attachRequirementKnowledgeNote(
                    analysisGenerator.generate(intake, structuredData, project, repositoryBundle, developers, requirementNote.asPromptContext(), knowledgeBaseContext),
                    requirementNote
            );
            intakeMapper.updateManagementFields(
                    intakeId,
                    writeStructuredDataJson(structuredData),
                    intake.getDevelopmentOwnerUserName(),
                    IntakeDemandStatusRules.PENDING_EVALUATION
            );
            DevelopmentAnalysisEntity completed = requireAnalysisEntity(analysisId);
            completed.setProjectId(project.id());
            completed.setBusinessLine(project.businessLine());
            completed.setRepositoryUrl(repositoryBundle.repositorySummary());
            completed.setAnalysisStatus(STATUS_DRAFT);
            completed.setAnalysisMessage(null);
            completed.setDraftJson(writeDraftJson(draft));
            completed.setUpdatedBy(operatorUserName);
            developmentAnalysisMapper.update(completed);
            String noteSummary = requirementNote.wikiRelativePath() == null
                    ? ""
                    : "需求材料已沉淀到知识库：" + requirementNote.wikiRelativePath() + "。";
            recordHistory(intakeId, "生成研发拆解草稿", noteSummary + "AI 已基于需求 Markdown 和 GitLab 代码生成研发工作项草稿，共 " + draft.workItems().size() + " 项。", operatorUserName);
        } catch (Exception ex) {
            DevelopmentAnalysisEntity failed = requireAnalysisEntity(analysisId);
            failed.setAnalysisStatus(STATUS_FAILED);
            failed.setAnalysisMessage(summarizeException(ex));
            failed.setDraftJson(null);
            failed.setUpdatedBy(operatorUserName);
            developmentAnalysisMapper.updateExecutionState(failed);
            recordHistory(intakeId, "任务评估失败", failed.getAnalysisMessage(), operatorUserName);
        }
    }

    private DevelopmentAnalysisDraft attachRequirementKnowledgeNote(DevelopmentAnalysisDraft draft,
                                                                    ProjectKnowledgeBaseService.RequirementKnowledgeNote note) {
        if (draft == null || note == null) {
            return draft;
        }
        return new DevelopmentAnalysisDraft(
                draft.status(),
                draft.businessLine(),
                draft.projectId(),
                draft.projectName(),
                draft.repositoryUrl(),
                note.rawRelativePath(),
                note.wikiRelativePath(),
                note.wikiObsidianUrl(),
                draft.summary(),
                draft.requirementChangePoints(),
                draft.impactedModules(),
                draft.risks(),
                draft.questions(),
                draft.developerPool(),
                draft.workItems(),
                draft.totalEstimatedEffort(),
                draft.developmentEstimatedEffort(),
                draft.testingEstimatedEffort(),
                draft.plannedDueDate(),
                draft.plannedTestingStartDate(),
                draft.plannedTestingEndDate(),
                draft.plannedReleaseDate(),
                draft.zentaoSyncStatus(),
                draft.zentaoSyncMessage(),
                draft.generatedAt(),
                draft.generator(),
                draft.plannedDevelopmentStartDate()
        );
    }

    private WorkItemCreateRequest toWorkItemCreateRequest(DevelopmentAnalysisDraft draft, DevelopmentWorkItemDraft item) {
        WorkItemCreateRequest request = new WorkItemCreateRequest();
        request.setProjectId(draft.projectId());
        request.setType("任务");
        request.setTitle(item.title());
        request.setDescription(buildWorkItemDescription(item));
        request.setSourceType("需求管理");
        request.setSourceChannel("AI研发拆解");
        request.setPriority(firstNonBlank(normalizePriority(item.priority()), "3"));
        request.setOwnerUserName(firstNonBlank(item.ownerUserName(), draft.developerPool().isEmpty() ? "admin" : draft.developerPool().getFirst()));
        request.setFollowerUserName(request.getOwnerUserName());
        request.setAcceptanceCriteria("按需求拆解完成开发、自测并通过代码评审。");
        request.setPlannedStartAt(parseDraftDate(item.plannedStartDate()));
        request.setPlannedEndAt(parseDraftDate(item.plannedEndDate()));
        return request;
    }

    private void linkWorkItem(Long intakeId, Long workItemId, int draftIndex) {
        IntakeWorkItemRelationEntity relation = new IntakeWorkItemRelationEntity();
        relation.setIntakeId(intakeId);
        relation.setWorkItemId(workItemId);
        relation.setDraftIndex(draftIndex);
        relation.setRelationType("DEVELOPMENT_TASK");
        intakeWorkItemRelationMapper.upsert(relation);
    }

    private String buildWorkItemDescription(DevelopmentWorkItemDraft item) {
        List<String> lines = new ArrayList<>();
        lines.add(firstNonBlank(item.description(), item.title()));
        if (item.taskType() != null) {
            lines.add("任务类型：" + item.taskType());
        }
        if (item.systemTags() != null && !item.systemTags().isEmpty()) {
            lines.add("系统标签：" + String.join("、", item.systemTags()));
        }
        if (item.requirementChangePoint() != null) {
            lines.add("对应需求变化点：" + item.requirementChangePoint());
        }
        if (item.targetResources() != null && !item.targetResources().isEmpty()) {
            lines.add("改动对象：" + String.join("、", item.targetResources()));
        }
        if (item.evidenceRefs() != null && !item.evidenceRefs().isEmpty()) {
            lines.add("判断依据：" + String.join("、", item.evidenceRefs()));
        }
        if (item.confidence() != null) {
            lines.add("置信度：" + item.confidence());
        }
        if (item.changePoints() != null && !item.changePoints().isEmpty()) {
            lines.add("改地点：");
            for (int i = 0; i < item.changePoints().size(); i++) {
                lines.add((i + 1) + ". " + item.changePoints().get(i));
            }
        }
        if (item.relatedFiles() != null && !item.relatedFiles().isEmpty()) {
            lines.add("涉及文件：" + String.join("、", item.relatedFiles()));
        }
        if (item.estimatedEffort() != null) {
            lines.add("预估工时：" + item.estimatedEffort());
        }
        if (item.priority() != null) {
            lines.add("优先级：" + item.priority());
        }
        if (item.plannedStartDate() != null) {
            lines.add("预计开始日期：" + item.plannedStartDate());
        }
        if (item.plannedEndDate() != null) {
            lines.add("截止日期：" + item.plannedEndDate());
        }
        if (item.dependency() != null) {
            lines.add("依赖：" + item.dependency());
        }
        if (item.risk() != null) {
            lines.add("风险：" + item.risk());
        }
        return String.join("\n", lines);
    }

    private DevelopmentAnalysisEntity upsertAnalysis(Long intakeId,
                                                     ProjectDetailResponse project,
                                                     String repositoryUrl,
                                                     DevelopmentAnalysisDraft draft,
                                                     String status,
                                                     String operatorUserName) {
        DevelopmentAnalysisEntity entity = developmentAnalysisMapper.findLatestByIntakeId(intakeId);
        if (entity == null) {
            entity = new DevelopmentAnalysisEntity();
            entity.setIntakeId(intakeId);
            entity.setCreatedBy(operatorUserName);
        }
        entity.setProjectId(project.id());
        entity.setBusinessLine(project.businessLine());
        entity.setRepositoryUrl(repositoryUrl);
        entity.setAnalysisStatus(status);
        entity.setAnalysisMessage(null);
        entity.setDraftJson(writeDraftJson(draft));
        entity.setZentaoSyncStatus("RESERVED");
        entity.setZentaoSyncMessage("禅道同步接口已预留，当前不会调用禅道。");
        entity.setUpdatedBy(operatorUserName);
        if (entity.getId() == null) {
            developmentAnalysisMapper.insert(entity);
        } else {
            developmentAnalysisMapper.update(entity);
        }
        return developmentAnalysisMapper.findLatestByIntakeId(intakeId);
    }

    private DevelopmentAnalysisEntity upsertExecutionState(DevelopmentAnalysisEntity existing,
                                                           Long intakeId,
                                                           String businessLine,
                                                           String status,
                                                           String message,
                                                           String draftJson,
                                                           String operatorUserName) {
        DevelopmentAnalysisEntity entity = existing;
        if (entity == null) {
            entity = new DevelopmentAnalysisEntity();
            entity.setIntakeId(intakeId);
            entity.setCreatedBy(operatorUserName);
            entity.setZentaoSyncStatus("RESERVED");
            entity.setZentaoSyncMessage("禅道同步接口已预留，当前不会调用禅道。");
        }
        entity.setProjectId(null);
        entity.setBusinessLine(businessLine);
        entity.setRepositoryUrl(null);
        entity.setAnalysisStatus(status);
        entity.setAnalysisMessage(message);
        entity.setDraftJson(draftJson);
        entity.setUpdatedBy(operatorUserName);
        if (entity.getId() == null) {
            developmentAnalysisMapper.insert(entity);
        } else {
            developmentAnalysisMapper.update(entity);
        }
        return developmentAnalysisMapper.findLatestByIntakeId(intakeId);
    }

    private IntakeRecordEntity requireIntake(Long intakeId) {
        IntakeRecordEntity entity = intakeMapper.findById(intakeId);
        if (entity == null) {
            throw new IllegalArgumentException("需求不存在");
        }
        return entity;
    }

    private DevelopmentAnalysisEntity requireAnalysis(Long intakeId) {
        DevelopmentAnalysisEntity entity = developmentAnalysisMapper.findLatestByIntakeId(intakeId);
        if (entity == null) {
            throw new IllegalArgumentException("请先生成研发拆解草稿");
        }
        return entity;
    }

    private DevelopmentAnalysisEntity requireAnalysisEntity(Long analysisId) {
        DevelopmentAnalysisEntity entity = developmentAnalysisMapper.findById(analysisId);
        if (entity == null) {
            throw new IllegalArgumentException("研发拆解草稿不存在");
        }
        return entity;
    }

    private void requireDevelopmentRequirement(IntakeStructuredData structuredData) {
        if (structuredData == null || !"研发需求".equals(structuredData.requirementType())) {
            throw new IllegalArgumentException("只有研发需求支持代码影响分析");
        }
    }

    private void requirePendingEvaluation(IntakeRecordEntity intake, IntakeStructuredData structuredData, String message) {
        String currentStatus = IntakeDemandStatusRules.resolve(intake, structuredData);
        if (!IntakeDemandStatusRules.PENDING_EVALUATION.equals(currentStatus)) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireEditableDevelopmentTasks(IntakeRecordEntity intake, IntakeStructuredData structuredData) {
        String currentStatus = IntakeDemandStatusRules.resolve(intake, structuredData);
        if (currentStatus == null) {
            throw new IllegalArgumentException("需求尚未进入业务生命周期，不能调整研发任务");
        }
    }

    private String resolveEditableAnalysisStatus(DevelopmentAnalysisEntity entity) {
        if (entity == null || trimToNull(entity.getAnalysisStatus()) == null) {
            return STATUS_DRAFT;
        }
        String analysisStatus = entity.getAnalysisStatus().trim();
        if (STATUS_RUNNING.equals(analysisStatus)) {
            throw new IllegalArgumentException("AI 任务评估运行中，不能同时调整研发任务");
        }
        if (STATUS_CONFIRMED.equals(analysisStatus)) {
            return STATUS_CONFIRMED;
        }
        return STATUS_DRAFT;
    }

    private ProjectDetailResponse resolveProjectForStructuredData(IntakeStructuredData structuredData) {
        ProjectDetailResponse project = resolveProjectOrNull(structuredData);
        if (project != null) {
            return project;
        }
        return syntheticProject(resolveBusinessLine(structuredData));
    }

    private ProjectDetailResponse resolveProjectOrNull(IntakeStructuredData structuredData) {
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, structuredData.businessLineCode());
        addCandidate(candidates, structuredData.businessLine());
        addCandidate(candidates, structuredData.department());
        addCandidate(candidates, structuredData.projectHint());
        for (String candidate : candidates) {
            String group = trimToNull(candidate);
            if (group == null) {
                continue;
            }
            ProjectDetailResponse project = projectMapper.findFirstDetailByBusinessLine(group);
            if (project != null) {
                return project;
            }
        }
        return null;
    }

    private void addCandidate(List<String> candidates, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            candidates.add(normalized);
        }
    }

    private ProjectDetailResponse resolveProjectForDraft(DevelopmentAnalysisDraft draft) {
        ProjectDetailResponse project = projectMapper.findFirstDetailByBusinessLine(draft.businessLine());
        if (project == null) {
            return syntheticProject(draft.businessLine());
        }
        return project;
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
        return firstNonBlank(
                structuredData == null ? null : structuredData.businessLineCode(),
                firstNonBlank(
                        structuredData == null ? null : structuredData.businessLine(),
                        firstNonBlank(
                                structuredData == null ? null : structuredData.department(),
                                structuredData == null ? null : structuredData.projectHint()
                        )
                )
        );
    }

    private ProjectDetailResponse requireBusinessLineProject(String businessLine) {
        String normalizedBusinessLine = trimToNull(businessLine);
        if (normalizedBusinessLine == null) {
            throw new IllegalArgumentException("请先选择业务线");
        }
        ProjectDetailResponse project = findBusinessLineProject(normalizedBusinessLine);
        if (project == null) {
            throw new IllegalArgumentException("未找到与需求业务线匹配的项目，请先在项目管理中维护业务线");
        }
        return project;
    }

    private ProjectDetailResponse findBusinessLineProject(String businessLine) {
        String normalizedBusinessLine = trimToNull(businessLine);
        if (normalizedBusinessLine == null) {
            return null;
        }
        return projectMapper.findFirstDetailByBusinessLine(normalizedBusinessLine);
    }

    private Long retainedProjectId(DevelopmentAnalysisDraft draft, String businessLine) {
        if (draft == null || !Objects.equals(trimToNull(draft.businessLine()), trimToNull(businessLine))) {
            return null;
        }
        return draft.projectId();
    }

    private String retainedProjectName(DevelopmentAnalysisDraft draft, String businessLine) {
        if (draft == null || !Objects.equals(trimToNull(draft.businessLine()), trimToNull(businessLine))) {
            return null;
        }
        return draft.projectName();
    }

    private String businessLineKeyForValidation(ProjectDetailResponse project, String fallbackBusinessLine) {
        if (project == null) {
            return fallbackBusinessLine;
        }
        return firstNonBlank(project.businessLineCode(), firstNonBlank(fallbackBusinessLine, project.businessLine()));
    }

    private IntakeStructuredData withBusinessLineOverride(IntakeStructuredData baseline, String businessLineOverride) {
        String businessLine = trimToNull(businessLineOverride);
        if (businessLine == null) {
            return baseline;
        }
        return new IntakeStructuredData(
                baseline.category(),
                baseline.approvalTitle(),
                baseline.proposerName(),
                baseline.developmentOwnerUserName(),
                baseline.approvalCode(),
                baseline.submittedTime(),
                baseline.requirementType(),
                baseline.developmentBranchName(),
                baseline.zentaoUrl(),
                baseline.requirementDigest(),
                baseline.requirementName(),
                baseline.requirementSummary(),
                baseline.department(),
                baseline.businessLine(),
                baseline.remark(),
                baseline.plannedDueDate(),
                baseline.plannedDevelopmentStartDate(),
                baseline.plannedTestingStartDate(),
                baseline.plannedReleaseDate(),
                baseline.developmentStartedDate(),
                baseline.actualEffort(),
                baseline.testingStartedDate(),
                baseline.actualCompletedTime(),
                baseline.acceptanceTime(),
                baseline.releasedTime(),
                baseline.closedTime(),
                baseline.closeReason(),
                businessLine,
                baseline.fields() == null ? List.of() : baseline.fields(),
                baseline.attachmentSummaries() == null ? List.of() : baseline.attachmentSummaries(),
                baseline.sqlDraft()
        );
    }

    private List<UserOptionResponse> resolveDevelopers(String businessLine, String projectOwner) {
        Map<String, UserOptionResponse> merged = new LinkedHashMap<>();
        List<UserOptionResponse> activeUsers = userMapper.findActiveUsers();
        if (activeUsers != null) {
            for (UserOptionResponse item : activeUsers) {
                merged.putIfAbsent(item.userName(), new UserOptionResponse(item.userName(), item.displayName(), null));
            }
        }
        if (merged.isEmpty()) {
            merged.put("admin", new UserOptionResponse("admin", "admin", null));
        }
        return new ArrayList<>(merged.values());
    }

    private DevelopmentAnalysisResponse toResponse(DevelopmentAnalysisEntity entity) {
        DevelopmentAnalysisDraft draft = enrichDeveloperPool(readDraft(entity.getDraftJson()));
        return new DevelopmentAnalysisResponse(
                entity.getId(),
                entity.getIntakeId(),
                entity.getAnalysisStatus(),
                entity.getAnalysisMessage(),
                draft,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DevelopmentAnalysisDraft enrichDeveloperPool(DevelopmentAnalysisDraft draft) {
        if (draft == null) {
            return null;
        }
        List<String> developerPool = new ArrayList<>(draft.developerPool() == null ? List.of() : draft.developerPool());
        if (draft.workItems() != null) {
            for (DevelopmentWorkItemDraft item : draft.workItems()) {
                addDeveloperIfMissing(developerPool, item == null ? null : item.ownerUserName());
            }
        }
        String projectOwner = null;
        if (trimToNull(draft.businessLine()) != null) {
            ProjectDetailResponse project = projectMapper.findFirstDetailByBusinessLine(draft.businessLine());
            projectOwner = project == null ? null : project.ownerUserName();
        }
        for (UserOptionResponse developer : resolveDevelopers(draft.businessLine(), projectOwner)) {
            addDeveloperIfMissing(developerPool, developer.userName());
        }
        return new DevelopmentAnalysisDraft(
                draft.status(),
                draft.businessLine(),
                draft.projectId(),
                draft.projectName(),
                draft.repositoryUrl(),
                draft.requirementRawPath(),
                draft.requirementWikiPath(),
                draft.requirementWikiUrl(),
                draft.summary(),
                draft.requirementChangePoints(),
                draft.impactedModules(),
                draft.risks(),
                draft.questions(),
                developerPool,
                draft.workItems(),
                draft.totalEstimatedEffort(),
                draft.developmentEstimatedEffort(),
                draft.testingEstimatedEffort(),
                draft.plannedDueDate(),
                draft.plannedTestingStartDate(),
                draft.plannedTestingEndDate(),
                draft.plannedReleaseDate(),
                draft.zentaoSyncStatus(),
                draft.zentaoSyncMessage(),
                draft.generatedAt(),
                draft.generator(),
                draft.plannedDevelopmentStartDate()
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

    private List<IntakeClarificationItem> readClarificationItems(Long intakeId) {
        IntakeClarificationAnalysisEntity entity = intakeClarificationAnalysisMapper.findLatestByIntakeId(intakeId);
        if (entity == null || entity.getItemsJson() == null || entity.getItemsJson().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(entity.getItemsJson(), new TypeReference<List<IntakeClarificationItem>>() {
            });
        } catch (JacksonException ex) {
            throw new IllegalStateException("需求澄清项解析失败", ex);
        }
    }

    private String appendClarificationContext(String knowledgeBaseContext, List<IntakeClarificationItem> clarificationItems) {
        if (clarificationItems == null || clarificationItems.isEmpty()) {
            return knowledgeBaseContext;
        }
        StringBuilder builder = new StringBuilder(valueOrEmpty(knowledgeBaseContext));
        builder.append("\n\n需求澄清确认结果：\n");
        for (IntakeClarificationItem item : clarificationItems) {
            if (item == null) {
                continue;
            }
            builder.append("- ")
                    .append("QUESTION".equals(item.itemType()) ? "待确认项" : "风险项")
                    .append("：")
                    .append(defaultText(item.title()));
            if (trimToNull(item.responseText()) != null) {
                builder.append("；人工回复/确认：").append(item.responseText().trim());
            } else {
                builder.append("；尚未回复/确认");
            }
            if (trimToNull(item.evidence()) != null) {
                builder.append("；依据：").append(item.evidence().trim());
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String defaultText(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "-" : normalized;
    }

    private DevelopmentAnalysisDraft readDraft(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DevelopmentAnalysisDraft.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("研发拆解草稿解析失败", ex);
        }
    }

    private String writeStructuredDataJson(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(EffortUnitNormalizer.normalizeStructuredData(structuredData));
        } catch (JacksonException ex) {
            throw new IllegalStateException("结构化需求写入失败", ex);
        }
    }

    private String writeDraftJson(DevelopmentAnalysisDraft draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (JacksonException ex) {
            throw new IllegalStateException("研发拆解草稿写入失败", ex);
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

    private String firstNonBlank(String first, String second) {
        String normalized = trimToNull(first);
        return normalized == null ? trimToNull(second) : normalized;
    }

    private void addDeveloperIfMissing(List<String> developerPool, String userName) {
        String normalized = trimToNull(userName);
        if (normalized == null || developerPool.contains(normalized)) {
            return;
        }
        developerPool.add(normalized);
    }

    private void persistDeveloperCandidate(String userName) {
        String normalized = trimToNull(userName);
        if (normalized == null) {
            return;
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("负责人不能超过 64 个字符");
        }
        userMapper.upsertActiveUser(normalized, normalized);
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>();
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null && !sanitized.contains(normalized)) {
                sanitized.add(normalized);
            }
        }
        return sanitized;
    }

    private void validateDraftSystemTags(DevelopmentAnalysisDraft draft,
                                         String businessLine,
                                         DevelopmentAnalysisDraft existingDraft) {
        if (draft == null || draft.workItems() == null || draft.workItems().isEmpty()) {
            return;
        }
        validateWorkItemSystemTags(draft.workItems(), businessLine, existingDraft);
    }

    private void validateWorkItemSystemTags(List<DevelopmentWorkItemDraft> workItems,
                                            String businessLine,
                                            DevelopmentAnalysisDraft existingDraft) {
        if (workItems == null || workItems.isEmpty()) {
            return;
        }
        String normalizedBusinessLine = trimToNull(businessLine);
        if (normalizedBusinessLine == null) {
            throw new IllegalArgumentException("请先选择业务线");
        }
        List<String> allowedSystems = involvedSystemMapper.findSelectableByBusinessLine(normalizedBusinessLine).stream()
                .map(ProjectInvolvedSystemEntity::getSystemName)
                .toList();
        List<String> existingSystems = collectExistingSystemTags(existingDraft);
        for (int i = 0; i < workItems.size(); i++) {
            DevelopmentWorkItemDraft item = workItems.get(i);
            List<String> systemTags = item == null ? List.of() : sanitizeList(item.systemTags());
            if (systemTags.isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个研发任务必须选择涉及系统");
            }
            for (String systemTag : systemTags) {
                if (!allowedSystems.contains(systemTag) && !existingSystems.contains(systemTag)) {
                    throw new IllegalArgumentException("涉及系统不在当前业务线或中台系统清单中：" + systemTag);
                }
            }
        }
    }

    private List<String> collectExistingSystemTags(DevelopmentAnalysisDraft draft) {
        if (draft == null || draft.workItems() == null || draft.workItems().isEmpty()) {
            return List.of();
        }
        List<String> existing = new ArrayList<>();
        for (DevelopmentWorkItemDraft item : draft.workItems()) {
            if (item == null || item.systemTags() == null) {
                continue;
            }
            for (String systemTag : item.systemTags()) {
                String normalized = trimToNull(systemTag);
                if (normalized != null && !existing.contains(normalized)) {
                    existing.add(normalized);
                }
            }
        }
        return existing;
    }

    private List<String> mergeRequirementChangePoints(List<String> existing, List<DevelopmentWorkItemDraft> workItems) {
        List<String> merged = new ArrayList<>(sanitizeList(existing));
        if (workItems == null || workItems.isEmpty()) {
            return merged;
        }
        for (DevelopmentWorkItemDraft item : workItems) {
            String normalized = item == null ? null : trimToNull(item.requirementChangePoint());
            if (normalized != null && !merged.contains(normalized)) {
                merged.add(normalized);
            }
        }
        return merged;
    }

    private String sumEstimatedEffort(List<DevelopmentWorkItemDraft> workItems, String fallback) {
        if (workItems == null || workItems.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        boolean hasEffort = false;
        for (DevelopmentWorkItemDraft item : workItems) {
            String effort = EffortUnitNormalizer.normalizeEffort(item.estimatedEffort());
            if (effort == null) {
                continue;
            }
            Matcher matcher = NORMALIZED_HOUR_PATTERN.matcher(effort);
            if (!matcher.matches()) {
                return fallback;
            }
            total = total.add(new BigDecimal(matcher.group(1)));
            hasEffort = true;
        }
        if (!hasEffort) {
            return null;
        }
        return total.stripTrailingZeros().toPlainString() + "h";
    }

    private void validateOptionalWorkItemEfforts(List<DevelopmentWorkItemDraft> workItems) {
        if (workItems == null || workItems.isEmpty()) {
            return;
        }
        for (DevelopmentWorkItemDraft item : workItems) {
            if (item == null || item.estimatedEffort() == null) {
                continue;
            }
            if (parseNormalizedHours(item.estimatedEffort()) == null) {
                throw new IllegalArgumentException("任务工时格式不正确，请输入如 8h 或 1d");
            }
        }
    }

    private String sumPlanEffort(String developmentEstimatedEffort, String testingEstimatedEffort) {
        BigDecimal developmentHours = parseNormalizedHours(developmentEstimatedEffort);
        BigDecimal testingHours = parseNormalizedHours(testingEstimatedEffort);
        if (developmentHours == null && testingHours == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        if (developmentHours != null) {
            total = total.add(developmentHours);
        }
        if (testingHours != null) {
            total = total.add(testingHours);
        }
        return total.stripTrailingZeros().toPlainString() + "h";
    }

    private BigDecimal parseNormalizedHours(String effort) {
        String normalized = EffortUnitNormalizer.normalizeEffort(effort);
        if (normalized == null) {
            return null;
        }
        Matcher matcher = NORMALIZED_HOUR_PATTERN.matcher(normalized);
        return matcher.matches() ? new BigDecimal(matcher.group(1)) : null;
    }

    private String normalizePriority(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (!List.of("1", "2", "3", "4").contains(normalized)) {
            throw new IllegalArgumentException("优先级只能是 1、2、3、4，数字越小优先级越高");
        }
        return normalized;
    }

    private String normalizeTaskType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "CODE_CHANGE";
        }
        String upper = normalized.toUpperCase();
        if (!List.of("CODE_CHANGE", "CONFIG_CHANGE", "DATA_CHANGE", "SQL_SCRIPT", "OPS_ACTION", "VERIFY", "UNKNOWN").contains(upper)) {
            throw new IllegalArgumentException("任务类型不合法");
        }
        return upper;
    }

    private String normalizeConfidence(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "MEDIUM";
        }
        String upper = normalized.toUpperCase();
        if (!List.of("HIGH", "MEDIUM", "LOW").contains(upper)) {
            throw new IllegalArgumentException("置信度只能是 HIGH、MEDIUM、LOW");
        }
        return upper;
    }

    private String normalizeDate(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, formatter).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            } catch (DateTimeParseException ignored) {
                // Try next supported format.
            }
        }
        throw new IllegalArgumentException(fieldName + "格式必须是 yyyy/MM/dd");
    }

    private LocalDateTime parseDraftDate(String value) {
        String normalized = normalizeDate(value, "计划日期");
        return normalized == null
                ? null
                : LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy/MM/dd")).atStartOfDay();
    }

    private void validateDraftDateRange(String plannedStartDate, String plannedEndDate) {
        if (plannedStartDate == null || plannedEndDate == null) {
            return;
        }
        LocalDate start = LocalDate.parse(plannedStartDate, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        LocalDate end = LocalDate.parse(plannedEndDate, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("截止日期不能早于预计开始日期");
        }
    }

    private void validateOrderedDate(String earlier, String later, String message) {
        if (earlier == null || later == null) {
            return;
        }
        LocalDate earlierDate = LocalDate.parse(earlier, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        LocalDate laterDate = LocalDate.parse(later, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        if (laterDate.isBefore(earlierDate)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isExecutionInProgress(String status) {
        return STATUS_PENDING.equals(status) || STATUS_RUNNING.equals(status);
    }

    private String summarizeException(Exception ex) {
        String message = trimToNull(ex.getMessage());
        return message == null ? "任务评估失败，请查看后端日志" : message;
    }
}
