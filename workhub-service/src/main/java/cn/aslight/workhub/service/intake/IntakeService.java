package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.attachment.AttachmentResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.DevelopmentWorkItemDraft;
import cn.aslight.workhub.model.intake.IntakeAIDraft;
import cn.aslight.workhub.model.intake.IntakeBusinessLineUpdateRequest;
import cn.aslight.workhub.model.intake.IntakeCreateRequest;
import cn.aslight.workhub.model.intake.IntakeDashboardResponse;
import cn.aslight.workhub.model.intake.IntakeDevelopmentBranchRequest;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeHistoryResponse;
import cn.aslight.workhub.model.intake.IntakePauseRequest;
import cn.aslight.workhub.model.intake.IntakeRelatedWorkItemResponse;
import cn.aslight.workhub.model.intake.IntakeTodoEntity;
import cn.aslight.workhub.model.intake.IntakeTodoResponse;
import cn.aslight.workhub.model.intake.IntakeWorkItemRelationEntity;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSqlDraft;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.model.intake.IntakeUploadRequest;
import cn.aslight.workhub.model.intake.WecomCallbackRequest;
import cn.aslight.workhub.model.intake.IntakeZentaoLinkRequest;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeStructuredFieldMapper;
import cn.aslight.workhub.dao.intake.IntakeTodoMapper;
import cn.aslight.workhub.dao.intake.IntakeWorkItemRelationMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.intake.IntakeStructuredFieldEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * 待整理箱业务服务。
 */
@Service
public class IntakeService {

    private static final Logger log = LoggerFactory.getLogger(IntakeService.class);

    private static final List<Map.Entry<String, String>> BRANCH_SUBJECT_TRANSLATIONS = List.of(
            Map.entry("里易二轮车", "liyi_ebike"),
            Map.entry("二轮车", "ebike"),
            Map.entry("沃橙", "wocheng"),
            Map.entry("嘉泰保理", "jiatai_factoring"),
            Map.entry("嘉泰", "jiatai"),
            Map.entry("易宝", "yeepay"),
            Map.entry("苏宁支付", "suning_pay")
    );

    private static final List<Map.Entry<String, String>> BRANCH_KEYWORD_TRANSLATIONS = List.of(
            Map.entry("分账", "split_account"),
            Map.entry("核验", "verify"),
            Map.entry("绑卡", "bind_card"),
            Map.entry("换电", "battery_swap"),
            Map.entry("银行卡", "bank_card"),
            Map.entry("接口", "api"),
            Map.entry("对接", "integration"),
            Map.entry("支付", "payment"),
            Map.entry("扣款", "withhold"),
            Map.entry("还款", "repayment"),
            Map.entry("放款", "loan"),
            Map.entry("取数", "data_export"),
            Map.entry("导出", "data_export"),
            Map.entry("数据", "data"),
            Map.entry("报表", "report"),
            Map.entry("审批", "approval"),
            Map.entry("商户", "merchant"),
            Map.entry("客户", "customer"),
            Map.entry("产品", "product"),
            Map.entry("校验", "validate"),
            Map.entry("规则", "rule"),
            Map.entry("配置", "config"),
            Map.entry("优化", "optimize"),
            Map.entry("改造", "refactor"),
            Map.entry("修复", "fix"),
            Map.entry("新增", "add")
    );
    private static final List<String> DEMAND_STATUS_SORT_ORDER = List.of(
            IntakeDemandStatusRules.RECORDED,
            IntakeDemandStatusRules.CLARIFYING,
            IntakeDemandStatusRules.PENDING_PROCESSING,
            IntakeDemandStatusRules.PROCESSING,
            IntakeDemandStatusRules.PENDING_EVALUATION,
            IntakeDemandStatusRules.PENDING_SCHEDULING,
            IntakeDemandStatusRules.PENDING_DESIGN,
            IntakeDemandStatusRules.IN_DEVELOPMENT,
            IntakeDemandStatusRules.TESTING,
            IntakeDemandStatusRules.PENDING_ACCEPTANCE,
            IntakeDemandStatusRules.PENDING_RELEASE,
            IntakeDemandStatusRules.PAUSED,
            IntakeDemandStatusRules.COMPLETED,
            IntakeDemandStatusRules.TERMINATED
    );
    private static final List<DashboardDemandGroup> DASHBOARD_DEMAND_GROUPS = List.of(
            new DashboardDemandGroup(
                    "not-started",
                    "未启动的需求",
                    List.of(
                            IntakeDemandStatusRules.RECORDED,
                            IntakeDemandStatusRules.CLARIFYING,
                            IntakeDemandStatusRules.PENDING_EVALUATION,
                            IntakeDemandStatusRules.PENDING_SCHEDULING,
                            IntakeDemandStatusRules.PENDING_DESIGN
                    ),
                    "已进入需求台账，但还未开始研发或执行",
                    "metric-card--idle",
                    "stage-tag--idle"
            ),
            new DashboardDemandGroup(
                    "developing",
                    "开发中的需求",
                    List.of(IntakeDemandStatusRules.IN_DEVELOPMENT),
                    "已指定研发人员或执行人，正在推进实现",
                    "metric-card--developing",
                    "stage-tag--developing"
            ),
            new DashboardDemandGroup(
                    "testing",
                    "测试中的需求",
                    List.of(IntakeDemandStatusRules.TESTING),
                    "已进入测试验证",
                    "metric-card--testing",
                    "stage-tag--testing"
            ),
            new DashboardDemandGroup(
                    "pending-release",
                    "待上线的需求",
                    List.of(IntakeDemandStatusRules.PENDING_ACCEPTANCE, IntakeDemandStatusRules.PENDING_RELEASE),
                    "等待业务验收或上线发布闭环",
                    "metric-card--release",
                    "stage-tag--release"
            )
    );
    private static final Set<String> UNRELEASED_EXCLUDED_STATUSES = Set.of(
            IntakeDemandStatusRules.COMPLETED,
            IntakeDemandStatusRules.TERMINATED,
            IntakeDemandStatusRules.PAUSED
    );

    private final IntakeMapper intakeMapper;
    private final IntakeHistoryMapper intakeHistoryMapper;
    private final IntakeWorkItemRelationMapper intakeWorkItemRelationMapper;
    private final IntakeTodoMapper intakeTodoMapper;
    private final IntakeStructuredFieldMapper intakeStructuredFieldMapper;
    private final BusinessLineMapper businessLineMapper;
    private final AttachmentService attachmentService;
    private final IntakeStructuredDataExtractor intakeStructuredDataExtractor;
    private final IntakeStructuredFieldNormalizer intakeStructuredFieldNormalizer;
    private final IntakeEnrichmentService intakeEnrichmentService;
    private final CodexCliSqlDraftGenerator codexCliSqlDraftGenerator;
    private final IntakeClarificationAnalysisService intakeClarificationAnalysisService;
    private final ObjectMapper objectMapper;

    @Autowired
    public IntakeService(IntakeMapper intakeMapper,
                         IntakeHistoryMapper intakeHistoryMapper,
                         IntakeWorkItemRelationMapper intakeWorkItemRelationMapper,
                         IntakeTodoMapper intakeTodoMapper,
                         IntakeStructuredFieldMapper intakeStructuredFieldMapper,
                         BusinessLineMapper businessLineMapper,
                         AttachmentService attachmentService,
                         IntakeStructuredDataExtractor intakeStructuredDataExtractor,
                         IntakeStructuredFieldNormalizer intakeStructuredFieldNormalizer,
                         IntakeEnrichmentService intakeEnrichmentService,
                         CodexCliSqlDraftGenerator codexCliSqlDraftGenerator,
                         IntakeClarificationAnalysisService intakeClarificationAnalysisService,
                         ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.intakeHistoryMapper = intakeHistoryMapper;
        this.intakeWorkItemRelationMapper = intakeWorkItemRelationMapper;
        this.intakeTodoMapper = intakeTodoMapper;
        this.intakeStructuredFieldMapper = intakeStructuredFieldMapper;
        this.businessLineMapper = businessLineMapper;
        this.attachmentService = attachmentService;
        this.intakeStructuredDataExtractor = intakeStructuredDataExtractor;
        this.intakeStructuredFieldNormalizer = intakeStructuredFieldNormalizer;
        this.intakeEnrichmentService = intakeEnrichmentService;
        this.codexCliSqlDraftGenerator = codexCliSqlDraftGenerator;
        this.intakeClarificationAnalysisService = intakeClarificationAnalysisService;
        this.objectMapper = objectMapper;
    }

    IntakeService(IntakeMapper intakeMapper,
                  IntakeHistoryMapper intakeHistoryMapper,
                  AttachmentService attachmentService,
                  IntakeStructuredDataExtractor intakeStructuredDataExtractor,
                  IntakeEnrichmentService intakeEnrichmentService,
                  CodexCliSqlDraftGenerator codexCliSqlDraftGenerator,
                  ObjectMapper objectMapper) {
        this(
                intakeMapper,
                intakeHistoryMapper,
                new NoopIntakeWorkItemRelationMapper(),
                new NoopIntakeTodoMapper(),
                new NoopIntakeStructuredFieldMapper(),
                null,
                attachmentService,
                intakeStructuredDataExtractor,
                new IntakeStructuredFieldNormalizer(),
                intakeEnrichmentService,
                codexCliSqlDraftGenerator,
                null,
                objectMapper
        );
    }

    protected IntakeService(IntakeMapper intakeMapper,
                            IntakeHistoryMapper intakeHistoryMapper,
                            IntakeWorkItemRelationMapper intakeWorkItemRelationMapper,
                            BusinessLineMapper businessLineMapper,
                            AttachmentService attachmentService,
                            IntakeStructuredDataExtractor intakeStructuredDataExtractor,
                            IntakeEnrichmentService intakeEnrichmentService,
                            CodexCliSqlDraftGenerator codexCliSqlDraftGenerator,
                            IntakeClarificationAnalysisService intakeClarificationAnalysisService,
                            ObjectMapper objectMapper) {
        this(
                intakeMapper,
                intakeHistoryMapper,
                intakeWorkItemRelationMapper,
                new NoopIntakeTodoMapper(),
                new NoopIntakeStructuredFieldMapper(),
                businessLineMapper,
                attachmentService,
                intakeStructuredDataExtractor,
                new IntakeStructuredFieldNormalizer(),
                intakeEnrichmentService,
                codexCliSqlDraftGenerator,
                intakeClarificationAnalysisService,
                objectMapper
        );
    }

    IntakeService(IntakeMapper intakeMapper,
                  IntakeHistoryMapper intakeHistoryMapper,
                  IntakeWorkItemRelationMapper intakeWorkItemRelationMapper,
                  AttachmentService attachmentService,
                  IntakeStructuredDataExtractor intakeStructuredDataExtractor,
                  IntakeEnrichmentService intakeEnrichmentService,
                  CodexCliSqlDraftGenerator codexCliSqlDraftGenerator,
                  IntakeClarificationAnalysisService intakeClarificationAnalysisService,
                  ObjectMapper objectMapper) {
        this(
                intakeMapper,
                intakeHistoryMapper,
                intakeWorkItemRelationMapper,
                new NoopIntakeTodoMapper(),
                new NoopIntakeStructuredFieldMapper(),
                null,
                attachmentService,
                intakeStructuredDataExtractor,
                new IntakeStructuredFieldNormalizer(),
                intakeEnrichmentService,
                codexCliSqlDraftGenerator,
                intakeClarificationAnalysisService,
                objectMapper
        );
    }

    private static class NoopIntakeWorkItemRelationMapper implements IntakeWorkItemRelationMapper {
        @Override
        public int upsert(IntakeWorkItemRelationEntity entity) {
            return 0;
        }

        @Override
        public List<IntakeRelatedWorkItemResponse> findByIntakeId(Long intakeId) {
            return List.of();
        }

        @Override
        public IntakeWorkItemRelationEntity findLatestByWorkItemId(Long workItemId) {
            return null;
        }
    }

    private static class NoopIntakeTodoMapper implements IntakeTodoMapper {
        @Override
        public List<IntakeTodoEntity> findByIntakeId(Long intakeId) {
            return List.of();
        }

        @Override
        public IntakeTodoEntity findById(Long id) {
            return null;
        }

        @Override
        public int insert(IntakeTodoEntity entity) {
            return 0;
        }

        @Override
        public int updateEditableFields(IntakeTodoEntity entity) {
            return 0;
        }

        @Override
        public int updateStatus(IntakeTodoEntity entity) {
            return 0;
        }

        @Override
        public int deleteById(Long id) {
            return 0;
        }
    }

    private static class NoopIntakeStructuredFieldMapper implements IntakeStructuredFieldMapper {
        @Override
        public List<IntakeStructuredFieldEntity> findByIntakeId(Long intakeId) {
            return List.of();
        }

        @Override
        public int deleteByIntakeId(Long intakeId) {
            return 0;
        }

        @Override
        public int upsertBatch(List<IntakeStructuredFieldEntity> fields) {
            return 0;
        }
    }

    public List<IntakeSummaryResponse> list(String status,
                                            String requirementName,
                                            String approvalCode,
                                            String proposerName,
                                            String businessLine,
                                            String requirementType,
                                            String demandStatus,
                                            String releasedStartDate,
                                            String releasedEndDate) {
        String normalizedRequirementName = trimToNull(requirementName);
        String normalizedApprovalCode = trimToNull(approvalCode);
        String normalizedProposerName = trimToNull(proposerName);
        String normalizedBusinessLine = trimToNull(businessLine);
        String normalizedRequirementType = trimToNull(requirementType);
        String normalizedDemandStatus = trimToNull(demandStatus);
        LocalDate normalizedReleasedStartDate = parseFilterDate(releasedStartDate, "上线开始日期");
        LocalDate normalizedReleasedEndDate = parseFilterDate(releasedEndDate, "上线结束日期");
        return intakeMapper.findAll(trimToNull(status)).stream()
                .map(this::toSummaryResponse)
                .filter(item -> matchesRequirementName(item, normalizedRequirementName))
                .filter(item -> matchesApprovalCode(item, normalizedApprovalCode))
                .filter(item -> matchesProposerName(item, normalizedProposerName))
                .filter(item -> matchesBusinessLine(item, normalizedBusinessLine))
                .filter(item -> normalizedRequirementType == null || normalizedRequirementType.equals(item.requirementType()))
                .filter(item -> normalizedDemandStatus == null || normalizedDemandStatus.equals(item.demandStatus()))
                .filter(item -> matchesReleasedDate(item, normalizedReleasedStartDate, normalizedReleasedEndDate))
                .sorted(Comparator.comparingInt(this::enrichmentSortOrder)
                        .thenComparingInt(this::demandStatusSortOrder)
                        .thenComparing(IntakeSummaryResponse::receivedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(IntakeSummaryResponse::id, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public IntakeDashboardResponse dashboard(String demandType) {
        DashboardDemandType normalizedDemandType = DashboardDemandType.from(demandType);
        List<IntakeSummaryResponse> records = list(null, null, null, null, null, null, null, null, null);
        List<IntakeSummaryResponse> businessLineRecords = records.stream()
                .filter(item -> normalizedDemandType.matches(item.requirementType()))
                .toList();
        return new IntakeDashboardResponse(
                normalizedDemandType.name(),
                normalizedDemandType.label,
                businessLineRecords.size(),
                buildDashboardStageCards(records),
                buildDashboardBusinessLineStats(businessLineRecords),
                buildDashboardHighlightedDemands(records),
                buildProgressReport(records)
        );
    }

    private List<IntakeDashboardResponse.StageCard> buildDashboardStageCards(List<IntakeSummaryResponse> records) {
        return DASHBOARD_DEMAND_GROUPS.stream()
                .map(group -> new IntakeDashboardResponse.StageCard(
                        group.key,
                        group.label,
                        group.statuses,
                        group.note,
                        group.className,
                        group.tagClass,
                        (int) records.stream().filter(item -> group.matches(item.demandStatus())).count()
                ))
                .toList();
    }

    private List<IntakeDashboardResponse.BusinessLineStats> buildDashboardBusinessLineStats(List<IntakeSummaryResponse> records) {
        Map<String, MutableBusinessLineStats> statsByBusinessLine = new HashMap<>();
        Map<String, String> businessLineNameByCode = new HashMap<>();
        Map<String, BusinessLineEntity> businessLineByName = new HashMap<>();
        int totalDemandCount = records.size();
        for (IntakeSummaryResponse record : records) {
            DashboardBusinessLine businessLine = dashboardBusinessLine(record, businessLineNameByCode, businessLineByName);
            MutableBusinessLineStats stats = statsByBusinessLine.computeIfAbsent(businessLine.key(), ignored -> new MutableBusinessLineStats(
                    businessLine.code(),
                    businessLine.name()
            ));
            stats.refreshBusinessLineCode(businessLine.code());
            stats.refreshBusinessLineName(businessLine.name());
            stats.demandCount++;
            if (DASHBOARD_DEMAND_GROUPS.get(0).matches(record.demandStatus())) {
                stats.notStartedCount++;
                stats.totalCount++;
            } else if (DASHBOARD_DEMAND_GROUPS.get(1).matches(record.demandStatus())) {
                stats.developingCount++;
                stats.totalCount++;
            } else if (DASHBOARD_DEMAND_GROUPS.get(2).matches(record.demandStatus())) {
                stats.testingCount++;
                stats.totalCount++;
            } else if (DASHBOARD_DEMAND_GROUPS.get(3).matches(record.demandStatus())) {
                stats.pendingReleaseCount++;
                stats.totalCount++;
            }
        }
        return statsByBusinessLine.values().stream()
                .map(stats -> stats.toResponse(totalDemandCount))
                .sorted(Comparator
                        .comparingInt(IntakeDashboardResponse.BusinessLineStats::demandCount).reversed()
                        .thenComparing(Comparator.comparingInt(IntakeDashboardResponse.BusinessLineStats::totalCount).reversed())
                        .thenComparing(IntakeDashboardResponse.BusinessLineStats::businessLine, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<IntakeDashboardResponse.DashboardDemand> buildDashboardHighlightedDemands(List<IntakeSummaryResponse> records) {
        List<IntakeDashboardResponse.DashboardDemand> demands = new ArrayList<>();
        for (DashboardDemandGroup group : DASHBOARD_DEMAND_GROUPS) {
            records.stream()
                    .filter(item -> group.matches(item.demandStatus()))
                    .sorted((left, right) -> Long.compare(sortableDemandTime(right), sortableDemandTime(left)))
                    .map(item -> toDashboardDemand(item, group.label, group.tagClass))
                    .forEach(demands::add);
        }
        return demands.stream().limit(12).toList();
    }

    private IntakeDashboardResponse.ProgressReport buildProgressReport(List<IntakeSummaryResponse> records) {
        LocalDateTime generatedAt = LocalDateTime.now();
        List<IntakeDashboardResponse.ProgressReportSummary> summary = DASHBOARD_DEMAND_GROUPS.stream()
                .map(group -> new IntakeDashboardResponse.ProgressReportSummary(
                        group.key,
                        group.label.replace("的需求", ""),
                        (int) records.stream().filter(item -> group.matches(item.demandStatus())).count(),
                        group.statuses
                ))
                .toList();
        List<IntakeDashboardResponse.ProgressReportReleasedDemand> releasedDemands = records.stream()
                .filter(item -> isWeeklyReleasedDemand(item, generatedAt.toLocalDate()))
                .sorted((left, right) -> Long.compare(sortableReleaseTime(right), sortableReleaseTime(left)))
                .map(this::toProgressReportReleasedDemand)
                .toList();
        List<IntakeDashboardResponse.ProgressReportDemand> pendingDemands = records.stream()
                .filter(this::isUnreleasedDemand)
                .sorted((left, right) -> Long.compare(sortableDemandTime(right), sortableDemandTime(left)))
                .map(this::toProgressReportDemand)
                .toList();
        return new IntakeDashboardResponse.ProgressReport(
                "项目总体进度报告",
                formatDashboardDateTime(generatedAt),
                "待上线包含待验收和待上线",
                summary,
                releasedDemands,
                pendingDemands
        );
    }

    public IntakeDetailResponse detail(Long id) {
        return detail(id, null, false);
    }

    public IntakeDetailResponse detail(Long id, String operatorUserName, boolean recordView) {
        IntakeRecordEntity entity = requireExisting(id);
        if (recordView) {
            recordHistory(id, "VIEW", "查看需求详情", "查看需求详情。", operatorUserName);
        }
        return toDetailResponse(entity);
    }

    @Transactional
    public IntakeDetailResponse pauseDemand(Long id,
                                            IntakePauseRequest request,
                                            String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        IntakeStructuredData structuredData = toStructuredData(entity);
        String currentDemandStatus = IntakeDemandStatusRules.resolve(entity, structuredData);
        if (currentDemandStatus == null) {
            throw new IllegalArgumentException("需求尚未识别成功，暂不能暂停");
        }
        if (IntakeDemandStatusRules.PAUSED.equals(currentDemandStatus)) {
            throw new IllegalArgumentException("需求已经处于暂停状态");
        }
        if (IntakeDemandStatusRules.isTerminalStatus(currentDemandStatus)) {
            throw new IllegalArgumentException("终态需求不允许暂停");
        }
        String reason = requireValue(request == null ? null : request.getReason(), "暂停原因不能为空");
        LocalDate pauseDate = request == null ? null : request.getPauseDate();
        if (pauseDate == null) {
            throw new IllegalArgumentException("暂停日期不能为空");
        }

        intakeMapper.pauseDemand(id, currentDemandStatus, reason, pauseDate);
        recordHistory(id,
                "UPDATE",
                "暂停需求",
                buildPauseDemandHistory(currentDemandStatus, reason, pauseDate),
                operatorUserName);
        return detail(id, null, false);
    }

    @Transactional
    public IntakeDetailResponse resumeDemand(Long id, String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        IntakeStructuredData structuredData = toStructuredData(entity);
        String currentDemandStatus = IntakeDemandStatusRules.resolve(entity, structuredData);
        if (!IntakeDemandStatusRules.PAUSED.equals(currentDemandStatus)) {
            throw new IllegalArgumentException("仅暂停中的需求允许恢复");
        }
        String previousDemandStatus = trimToNull(entity.getPausePreviousDemandStatus());
        if (previousDemandStatus == null) {
            throw new IllegalArgumentException("暂停前状态缺失，不能恢复");
        }
        intakeMapper.restorePausedDemand(id, previousDemandStatus);
        recordHistory(id,
                "UPDATE",
                "恢复需求",
                buildResumeDemandHistory(previousDemandStatus),
                operatorUserName);
        return detail(id, null, false);
    }

    @Transactional
    public void delete(Long id, String operatorUserName) {
        requireExisting(id);
        intakeMapper.markDeleted(id, LocalDateTime.now(), trimToNull(operatorUserName));
    }

    /**
     * 重新触发识别失败需求的结构化识别。
     *
     * <p>该动作只重新执行 intake enrichment，结果仍然只回写待整理记录的结构化字段和识别状态，
     * 不会绕过人工确认创建正式工作项。</p>
     *
     * @param id 需求 ID
     * @param operatorUserName 操作人用户名
     * @return 更新后的需求详情
     */
    @Transactional
    public IntakeDetailResponse retryEnrichment(Long id, String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        String enrichmentStatus = IntakeDemandStatusRules.resolveEnrichmentStatus(entity);
        if (!IntakeEnrichmentStatus.FAILED.equals(enrichmentStatus)) {
            throw new IllegalArgumentException("当前需求不是识别失败状态，不能重新识别");
        }
        recordHistory(id,
                "UPDATE",
                "重试需求识别",
                "重新触发截图和附件结构化识别。当前识别状态：" + defaultHistoryValue(enrichmentStatus)
                        + "；上次失败原因：" + defaultHistoryValue(entity.getEnrichmentErrorSummary()),
                operatorUserName);
        intakeEnrichmentService.scheduleUploadedEnrichment(id, entity.getSourceChannel(), entity.getRawContent());
        return detail(id, null, false);
    }

    /**
     * 补充上传需求截图和需求附件。
     *
     * @param id 需求 ID
     * @param screenshots 需求截图
     * @param attachments 需求附件
     * @param operatorUserName 操作人用户名
     * @return 更新后的需求详情
     */
    @Transactional
    public IntakeDetailResponse appendAttachments(Long id,
                                                  List<MultipartFile> screenshots,
                                                  List<MultipartFile> attachments,
                                                  String operatorUserName) {
        requireExisting(id);
        if (!hasAnyFile(screenshots) && !hasAnyFile(attachments)) {
            throw new IllegalArgumentException("请至少上传一个需求截图或需求附件");
        }
        List<String> uploadedNames = uploadedMaterialNames(screenshots, attachments);
        attachmentService.appendIntakeFiles(id, screenshots, attachments);
        recordHistory(id,
                "UPDATE",
                "更新需求附件",
                "新增材料：" + String.join("、", uploadedNames),
                operatorUserName);
        return detail(id, null, false);
    }

    /**
     * 删除需求截图或需求附件。
     *
     * @param id 需求 ID
     * @param attachmentId 附件 ID
     * @param operatorUserName 操作人用户名
     * @return 更新后的需求详情
     */
    @Transactional
    public IntakeDetailResponse deleteAttachment(Long id, Long attachmentId, String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        AttachmentResponse deleted = attachmentService.deleteIntakeMaterial(id, attachmentId);
        IntakeStructuredData currentStructuredData = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(id));
        IntakeStructuredData nextStructuredData = removeAttachmentSummary(currentStructuredData, deleted.fileName());
        if (nextStructuredData != currentStructuredData) {
            replaceStructuredFields(id, nextStructuredData);
        }
        recordHistory(id,
                "UPDATE",
                "删除需求附件",
                "删除材料：" + deleted.fileName(),
                operatorUserName);
        return detail(id, null, false);
    }

    /**
     * 替换需求截图或需求附件。
     *
     * @param id 需求 ID
     * @param attachmentId 附件 ID
     * @param file 新文件
     * @param operatorUserName 操作人用户名
     * @return 更新后的需求详情
     */
    @Transactional
    public IntakeDetailResponse replaceAttachment(Long id, Long attachmentId, MultipartFile file, String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        AttachmentService.AttachmentReplaceResult replaced = attachmentService.replaceIntakeMaterial(id, attachmentId, file);
        IntakeStructuredData currentStructuredData = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(id));
        IntakeStructuredData nextStructuredData = removeAttachmentSummary(currentStructuredData, replaced.before().fileName());
        if (nextStructuredData != currentStructuredData) {
            replaceStructuredFields(id, nextStructuredData);
        }
        recordHistory(id,
                "UPDATE",
                "替换需求附件",
                "替换材料：" + replaced.before().fileName() + " -> " + replaced.after().fileName(),
                operatorUserName);
        return detail(id, null, false);
    }

    /**
     * 通过需求截图和需求附件录入待整理需求。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>校验原始说明、需求截图、需求附件至少有一项存在。</li>
     *   <li>将前端上传表单归一化成待整理箱内部使用的创建请求。</li>
     *   <li>先创建待整理记录并写入基础结构化字段，保证台账主记录优先落库。</li>
     *   <li>保存需求截图和需求附件，形成可追溯的附件记录。</li>
     *   <li>异步触发 enrichment，从截图和附件中继续抽取结构化信息并回写。</li>
     * </ol>
     *
     * @param request     上传表单
     * @param screenshots 需求截图文件列表
     * @param attachments 需求附件文件列表
     * @return 最新的待整理需求详情
     */
    @Transactional
    public IntakeDetailResponse createUploaded(IntakeUploadRequest request,
                                               List<MultipartFile> screenshots,
                                               List<MultipartFile> attachments) {
        if (!hasAnyContent(request, screenshots, attachments)) {
            throw new IllegalArgumentException("原始内容、截图和附件不能同时为空");
        }

        // 统一整理前端上传参数，复用现有待整理创建逻辑，避免上传入口单独维护一套字段规则。
        IntakeCreateRequest intakeCreateRequest = new IntakeCreateRequest();
        intakeCreateRequest.setSenderName(request.getSenderName());
        intakeCreateRequest.setDevelopmentOwnerUserName(request.getDevelopmentOwnerUserName());
        intakeCreateRequest.setSourceChannel(defaultChannel(request.getSourceChannel(), "需求截图录入"));
        intakeCreateRequest.setReceivedAt(request.getReceivedAt());
        BusinessLineEntity businessLine = resolveEnabledBusinessLine(request.getBusinessLineCode(), request.getBusinessLine(), false);
        intakeCreateRequest.setRawContent(buildUploadRawContent(
                request.getRawContent(),
                businessLine == null ? request.getBusinessLine() : businessLine.getBusinessLineName(),
                businessLine == null ? request.getBusinessLineCode() : businessLine.getBusinessLineCode(),
                screenshots,
                attachments
        ));

        // 先落待整理主记录，确保即使后续附件处理或异步增强失败，原始需求也不会丢失。
        IntakeDetailResponse detail = createIntakeRecord("需求截图附件录入",
                intakeCreateRequest.getSourceChannel(),
                null,
                intakeCreateRequest);

        // 再保存需求截图和需求附件，便于后续下载、审计和内容增强。
        attachmentService.saveIntakeFiles(detail.id(), screenshots, attachments);

        // 附件内容识别走异步增强，不阻塞上传接口响应。
        intakeEnrichmentService.scheduleUploadedEnrichment(detail.id(),
                intakeCreateRequest.getSourceChannel(),
                intakeCreateRequest.getRawContent());
        return detail(detail.id(), null, false);
    }

    @Transactional
    public IntakeDetailResponse receiveWecomCallback(WecomCallbackRequest request) {
        IntakeRecordEntity existing = intakeMapper.findByExternalMessageId(request.getExternalMessageId().trim());
        if (existing != null) {
                return toDetailResponse(existing);
        }
        IntakeCreateRequest intakeCreateRequest = new IntakeCreateRequest();
        intakeCreateRequest.setSenderName(request.getSenderName());
        intakeCreateRequest.setSourceChannel(defaultChannel(request.getSourceChannel(), "企业微信"));
        intakeCreateRequest.setReceivedAt(request.getReceivedAt());
        intakeCreateRequest.setRawContent(request.getRawContent());
        try {
            return createIntakeRecord("企业微信回调",
                    intakeCreateRequest.getSourceChannel(),
                    request.getExternalMessageId().trim(),
                    intakeCreateRequest);
        } catch (DuplicateKeyException ex) {
            IntakeRecordEntity duplicated = intakeMapper.findByExternalMessageId(request.getExternalMessageId().trim());
            if (duplicated != null) {
                return toDetailResponse(duplicated);
            }
            throw ex;
        }
    }

    @Transactional
    public IntakeDetailResponse advanceStage(Long id,
                                             IntakeStageActionRequest request,
                                             String operatorUserName) {
        return advanceStage(id, request, null, operatorUserName);
    }

    @Transactional
    public IntakeDetailResponse advanceStage(Long id,
                                             IntakeStageActionRequest request,
                                             List<MultipartFile> dataFiles,
                                             String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        IntakeStructuredData currentStructuredData = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(id));
        String currentDemandStatus = IntakeDemandStatusRules.resolve(entity, currentStructuredData);
        if (currentDemandStatus == null) {
            throw new IllegalArgumentException("需求尚未识别成功，暂不能推进业务阶段");
        }
        String action = IntakeDemandStatusRules.normalizeAction(request.getAction());
        String nextDemandStatus = IntakeDemandStatusRules.resolveNextStatus(
                currentDemandStatus,
                currentStructuredData == null ? null : currentStructuredData.requirementType(),
                action
        );
        IntakeStructuredData nextStructuredData = applyStageAction(currentStructuredData, request, action);

        List<String> uploadedFileNames = saveStageActionFiles(id, action, dataFiles);
        String historyDetail = buildStageActionHistory(
                currentStructuredData,
                nextStructuredData,
                currentDemandStatus,
                nextDemandStatus,
                uploadedFileNames
        );
        intakeStructuredFieldNormalizer.applyStructuredData(entity, nextStructuredData);
        intakeMapper.updateFormalFields(entity);
        intakeMapper.updateManagementFields(
                id,
                writeStructuredDataJson(nextStructuredData),
                entity.getDevelopmentOwnerUserName(),
                nextDemandStatus
        );
        replaceStructuredFields(id, nextStructuredData);
        if (historyDetail != null) {
            recordHistory(id, "UPDATE", resolveStageActionSummary(action), historyDetail, operatorUserName);
        }
        schedulePostStageAutomation(id, action, nextStructuredData, request, operatorUserName);
        return detail(id, null, false);
    }

    @Transactional
    public IntakeDetailResponse updateZentaoLink(Long id,
                                                 IntakeZentaoLinkRequest request,
                                                 String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        IntakeStructuredData currentStructuredData = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(id));
        IntakeStructuredData nextStructuredData = mergeZentaoLink(currentStructuredData, request);
        intakeStructuredFieldNormalizer.applyStructuredData(entity, nextStructuredData);
        intakeMapper.updateFormalFields(entity);
        intakeMapper.updateStructuredData(id, writeStructuredDataJson(nextStructuredData));
        replaceStructuredFields(id, nextStructuredData);
        String historyDetail = buildZentaoLinkHistory(currentStructuredData, nextStructuredData);
        if (historyDetail != null) {
            recordHistory(id, "UPDATE", "关联禅道地址", historyDetail, operatorUserName);
        }
        return detail(id, null, false);
    }

    @Transactional
    public IntakeDetailResponse updateDevelopmentBranch(Long id,
                                                        IntakeDevelopmentBranchRequest request,
                                                        String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        IntakeStructuredData currentStructuredData = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(id));
        IntakeStructuredData nextStructuredData = mergeDevelopmentBranch(currentStructuredData, request);
        intakeStructuredFieldNormalizer.applyStructuredData(entity, nextStructuredData);
        intakeMapper.updateFormalFields(entity);
        intakeMapper.updateStructuredData(id, writeStructuredDataJson(nextStructuredData));
        replaceStructuredFields(id, nextStructuredData);
        String historyDetail = buildDevelopmentBranchHistory(currentStructuredData, nextStructuredData);
        if (historyDetail != null) {
            recordHistory(id, "UPDATE", "更新研发分支", historyDetail, operatorUserName);
        }
        return detail(id, null, false);
    }

    @Transactional
    public IntakeDetailResponse updateBusinessLine(Long id,
                                                   IntakeBusinessLineUpdateRequest request,
                                                   String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        BusinessLineEntity businessLineEntity = resolveEnabledBusinessLine(
                request == null ? null : request.getBusinessLineCode(),
                request == null ? null : request.getBusinessLine(),
                true
        );
        String businessLine = businessLineEntity.getBusinessLineName();
        String businessLineCode = businessLineEntity.getBusinessLineCode();

        IntakeStructuredData currentStructuredData = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(id));
        IntakeStructuredData nextStructuredData = withBusinessLine(currentStructuredData, businessLine, businessLineCode);
        String previousBusinessLine = currentStructuredData == null ? null : trimToNull(currentStructuredData.businessLine());
        String previousBusinessLineCode = currentStructuredData == null ? null : trimToNull(currentStructuredData.businessLineCode());
        String previousProjectHint = currentStructuredData == null ? null : trimToNull(currentStructuredData.projectHint());
        if (java.util.Objects.equals(previousBusinessLine, businessLine)
                && java.util.Objects.equals(previousBusinessLineCode, businessLineCode)
                && java.util.Objects.equals(previousProjectHint, businessLine)) {
            return detail(id, null, false);
        }

        intakeStructuredFieldNormalizer.applyStructuredData(entity, nextStructuredData);
        intakeMapper.updateFormalFields(entity);
        intakeMapper.updateStructuredData(id, writeStructuredDataJson(nextStructuredData));
        replaceStructuredFields(id, nextStructuredData);
        recordHistory(id,
                "UPDATE",
                "修改业务线",
                buildBusinessLineHistory(previousBusinessLine, businessLine, previousProjectHint),
                operatorUserName);
        return detail(id, null, false);
    }

    /**
     * 为数据提取/运维类需求生成 SQL 草稿。
     *
     * <p>该动作只调用 Codex CLI 生成文本草稿，不连接任何数据库、不执行 SQL。
     * 生成结果写入结构化数据并记录修改历史，供人工确认后复制执行。</p>
     *
     * @param id               需求 ID
     * @param operatorUserName 操作人用户名
     * @return 更新后的需求详情
     */
    @Transactional
    public IntakeDetailResponse generateSqlDraft(Long id, String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        IntakeStructuredData currentStructuredData = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(id));
        if (currentStructuredData == null) {
            throw new IllegalArgumentException("当前需求尚未生成结构化信息，不能生成 SQL 草稿");
        }
        if (!"数据提取/运维".equals(trimToNull(currentStructuredData.requirementType()))) {
            throw new IllegalArgumentException("只有数据提取/运维类需求支持生成 SQL 草稿");
        }

        List<AttachmentService.AttachmentFileContext> attachments = attachmentService.listIntakeFileContexts(id);
        CodexCliSqlDraftGenerator.SqlDraftGenerationResult result =
                codexCliSqlDraftGenerator.generate(entity, currentStructuredData, attachments);
        if (!result.succeeded()) {
            throw new IllegalArgumentException(result.failureSummary());
        }

        IntakeStructuredData nextStructuredData = withSqlDraft(currentStructuredData, result.draft());
        intakeMapper.updateStructuredData(id, writeStructuredDataJson(toSqlDraftPayload(result.draft())));
        recordHistory(id, "UPDATE", "生成 SQL 草稿", buildSqlDraftHistory(result.draft()), operatorUserName);
        return detail(id, null, false);
    }

    private IntakeDetailResponse createIntakeRecord(String sourceType,
                                                    String sourceChannel,
                                                    String externalMessageId,
                                                    IntakeCreateRequest request) {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setSourceType(sourceType);
        entity.setSourceChannel(sourceChannel);
        entity.setExternalMessageId(externalMessageId);
        entity.setSenderName(request.getSenderName().trim());
        entity.setReceivedAt(request.getReceivedAt() == null ? LocalDateTime.now() : request.getReceivedAt());
        entity.setRawContent(request.getRawContent().trim());
        entity.setDevelopmentOwnerUserName(trimToNull(request.getDevelopmentOwnerUserName()));
        IntakeStructuredData structuredData = normalizeStructuredData(intakeStructuredDataExtractor.extract(entity.getRawContent()));
        intakeStructuredFieldNormalizer.applyStructuredData(entity, structuredData);
        entity.setStructuredDataJson(null);
        entity.setAiDraftJson(null);
        entity.setIntakeStatus(IntakeStatusRules.PENDING);
        entity.setDemandStatus(null);
        entity.setEnrichmentStatus(null);
        entity.setEnrichmentErrorSummary(null);
        entity.setEnrichmentUpdatedAt(null);
        entity.setConvertedWorkItemId(null);
        entity.setDeleted(Boolean.FALSE);
        entity.setDeletedAt(null);
        entity.setDeletedBy(null);
        intakeMapper.insert(entity);
        replaceStructuredFields(entity.getId(), structuredData);
        return detail(entity.getId());
    }

    private IntakeRecordEntity requireExisting(Long id) {
        IntakeRecordEntity entity = intakeMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("待整理记录不存在");
        }
        return entity;
    }

    private void replaceStructuredFields(Long intakeId, IntakeStructuredData structuredData) {
        intakeStructuredFieldMapper.deleteByIntakeId(intakeId);
        List<IntakeStructuredFieldEntity> fields = intakeStructuredFieldNormalizer.toFieldEntities(intakeId, structuredData);
        if (!fields.isEmpty()) {
            intakeStructuredFieldMapper.upsertBatch(fields);
        }
    }

    private IntakeDetailResponse toDetailResponse(IntakeRecordEntity entity) {
        List<AttachmentResponse> attachments = attachmentService.listIntakeAttachments(entity.getId());
        IntakeStructuredData structuredData = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(entity.getId()));
        List<IntakeHistoryResponse> histories = intakeHistoryMapper.findRecentByIntakeId(entity.getId(), 20).stream()
                .filter(history -> "UPDATE".equals(history.getActionType()))
                .map(this::toHistoryResponse)
                .toList();
        return new IntakeDetailResponse(
                entity.getId(),
                entity.getSourceType(),
                entity.getSourceChannel(),
                entity.getExternalMessageId(),
                entity.getSenderName(),
                entity.getDevelopmentOwnerUserName(),
                entity.getReceivedAt(),
                IntakeDemandStatusRules.resolve(entity, structuredData),
                entity.getPausePreviousDemandStatus(),
                entity.getPauseReason(),
                entity.getPauseDate(),
                entity.getRawContent(),
                structuredData,
                summarizeInvolvedSystems(entity.getLatestDevelopmentDraftJson()),
                intakeWorkItemRelationMapper.findByIntakeId(entity.getId()),
                histories,
                entity.getIntakeStatus(),
                IntakeDemandStatusRules.resolveEnrichmentStatus(entity),
                entity.getEnrichmentErrorSummary(),
                entity.getEnrichmentUpdatedAt(),
                readDraft(entity.getAiDraftJson()),
                attachments,
                intakeTodoMapper.findByIntakeId(entity.getId()).stream()
                        .map(this::toTodoResponse)
                        .toList(),
                entity.getConvertedWorkItemId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private IntakeStructuredData toStructuredData(IntakeRecordEntity entity) {
        return toStructuredData(entity, List.of());
    }

    private IntakeStructuredData toStructuredData(IntakeRecordEntity entity, List<IntakeStructuredFieldEntity> fieldEntities) {
        IntakeStructuredData legacyPayload = tryReadStructuredData(entity.getStructuredDataJson());
        if (!hasStructuredFormalFields(entity)) {
            if (legacyPayload != null) {
                return legacyPayload;
            }
            return normalizeStructuredData(intakeStructuredDataExtractor.extract(entity.getRawContent()));
        }
        return new IntakeStructuredData(
                legacyPayload == null ? null : legacyPayload.category(),
                entity.getApprovalTitle(),
                entity.getProposerName(),
                entity.getDevelopmentOwnerUserName(),
                entity.getApprovalCode(),
                formatDateTime(entity.getSubmittedAt()),
                entity.getRequirementType(),
                entity.getDevelopmentBranchName(),
                entity.getZentaoUrl(),
                entity.getRequirementDigest(),
                entity.getRequirementName(),
                entity.getRequirementSummary(),
                entity.getDepartment(),
                entity.getBusinessLine(),
                entity.getBusinessLineCode(),
                entity.getRemark(),
                formatDate(entity.getPlannedDueDate()),
                formatDate(entity.getPlannedDevelopmentStartDate()),
                formatDate(entity.getPlannedTestingStartDate()),
                formatDate(entity.getPlannedReleaseDate()),
                formatDate(entity.getDevelopmentStartedDate()),
                entity.getActualEffort(),
                formatDate(entity.getTestingStartedDate()),
                formatDate(entity.getActualCompletedDate()),
                formatDate(entity.getScheduledAcceptanceDate()),
                entity.getActualTestingEffort(),
                formatDate(entity.getActualTestingCompletedDate()),
                formatDate(entity.getAcceptanceDate()),
                formatDate(entity.getReleasedDate()),
                formatDate(entity.getClosedDate()),
                entity.getCloseReason(),
                entity.getProjectHint(),
                toStructuredFields(fieldEntities),
                legacyPayload == null || legacyPayload.attachmentSummaries() == null ? List.of() : legacyPayload.attachmentSummaries(),
                legacyPayload == null ? null : legacyPayload.sqlDraft()
        );
    }

    private boolean hasStructuredFormalFields(IntakeRecordEntity entity) {
        return trimToNull(entity.getApprovalCode()) != null
                || trimToNull(entity.getApprovalTitle()) != null
                || trimToNull(entity.getProposerName()) != null
                || entity.getSubmittedAt() != null
                || trimToNull(entity.getRequirementType()) != null
                || trimToNull(entity.getRequirementName()) != null
                || trimToNull(entity.getRequirementSummary()) != null
                || trimToNull(entity.getRequirementDigest()) != null
                || trimToNull(entity.getDepartment()) != null
                || trimToNull(entity.getBusinessLine()) != null
                || trimToNull(entity.getBusinessLineCode()) != null
                || trimToNull(entity.getProjectHint()) != null
                || trimToNull(entity.getDevelopmentBranchName()) != null
                || trimToNull(entity.getZentaoUrl()) != null
                || trimToNull(entity.getRemark()) != null
                || entity.getPlannedDueDate() != null
                || entity.getPlannedDevelopmentStartDate() != null
                || entity.getPlannedTestingStartDate() != null
                || entity.getPlannedReleaseDate() != null
                || entity.getDevelopmentStartedDate() != null
                || entity.getTestingStartedDate() != null
                || entity.getActualCompletedDate() != null
                || entity.getScheduledAcceptanceDate() != null
                || entity.getActualTestingCompletedDate() != null
                || entity.getAcceptanceDate() != null
                || entity.getReleasedDate() != null
                || entity.getClosedDate() != null
                || trimToNull(entity.getCloseReason()) != null
                || trimToNull(entity.getEstimatedEffort()) != null
                || trimToNull(entity.getActualEffort()) != null
                || trimToNull(entity.getActualTestingEffort()) != null;
    }

    private IntakeStructuredData toSqlDraftPayload(IntakeSqlDraft sqlDraft) {
        return new IntakeStructuredData(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                sqlDraft
        );
    }

    private List<IntakeStructuredField> toStructuredFields(List<IntakeStructuredFieldEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(entity -> new IntakeStructuredField(entity.getFieldLabel(), entity.getFieldValue()))
                .toList();
    }

    private String formatDate(LocalDate value) {
        return value == null ? null : value.toString().replace('-', '/');
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
    }

    private IntakeTodoResponse toTodoResponse(IntakeTodoEntity entity) {
        return new IntakeTodoResponse(
                entity.getId(),
                entity.getIntakeId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getTodoStatus(),
                entity.getAssigneeUserName(),
                entity.getPlannedAt(),
                entity.getCompletedAt(),
                entity.getProcessResult(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private IntakeSummaryResponse toSummaryResponse(IntakeRecordEntity entity) {
        IntakeStructuredData structuredData = toStructuredData(entity);
        String totalEstimatedEffort = normalizeJsonText(entity.getTotalEstimatedEffort());
        String developmentEstimatedEffort = normalizeJsonText(entity.getDevelopmentEstimatedEffort());
        String testingEstimatedEffort = normalizeJsonText(entity.getTestingEstimatedEffort());
        String legacyEstimatedEffort = legacyEstimatedEffort(entity.getStructuredDataJson());
        boolean operationsRequirement = structuredData != null && isOperationsRequirement(structuredData.requirementType());
        if (operationsRequirement) {
            String operationsEffort = firstNonBlank(entity.getEstimatedEffort(),
                    firstNonBlank(legacyEstimatedEffort, structuredData.actualEffort()));
            totalEstimatedEffort = firstNonBlank(totalEstimatedEffort, operationsEffort);
            developmentEstimatedEffort = firstNonBlank(developmentEstimatedEffort, operationsEffort);
        }
        String testingStartedDate = structuredData == null ? null : structuredData.testingStartedDate();
        if (operationsRequirement) {
            testingStartedDate = firstNonBlank(testingStartedDate, structuredData.actualCompletedTime());
        }
        return new IntakeSummaryResponse(
                entity.getId(),
                entity.getSourceType(),
                entity.getSourceChannel(),
                entity.getSenderName(),
                entity.getDevelopmentOwnerUserName(),
                entity.getReceivedAt(),
                IntakeDemandStatusRules.resolve(entity, structuredData),
                structuredData == null ? null : structuredData.proposerName(),
                structuredData == null ? null : structuredData.approvalCode(),
                structuredData == null ? null : structuredData.submittedTime(),
                structuredData == null ? null : structuredData.requirementType(),
                structuredData == null ? null : structuredData.developmentBranchName(),
                structuredData == null ? null : structuredData.zentaoUrl(),
                structuredData == null ? null : structuredData.requirementDigest(),
                structuredData == null ? null : structuredData.department(),
                structuredData == null ? null : structuredData.requirementName(),
                structuredData == null ? null : structuredData.requirementSummary(),
                structuredData == null ? null : structuredData.businessLine(),
                structuredData == null ? null : structuredData.businessLineCode(),
                structuredData == null ? null : structuredData.remark(),
                totalEstimatedEffort,
                developmentEstimatedEffort,
                testingEstimatedEffort,
                structuredData == null ? null : structuredData.plannedDueDate(),
                structuredData == null ? null : structuredData.plannedDevelopmentStartDate(),
                structuredData == null ? null : structuredData.plannedTestingStartDate(),
                structuredData == null ? null : structuredData.plannedReleaseDate(),
                structuredData == null ? null : structuredData.developmentStartedDate(),
                structuredData == null ? null : structuredData.actualEffort(),
                testingStartedDate,
                structuredData == null ? null : structuredData.actualCompletedTime(),
                structuredData == null ? null : structuredData.scheduledAcceptanceDate(),
                structuredData == null ? null : structuredData.actualTestingEffort(),
                structuredData == null ? null : structuredData.actualTestingCompletedDate(),
                structuredData == null ? null : structuredData.acceptanceTime(),
                structuredData == null ? null : structuredData.releasedTime(),
                structuredData == null ? null : structuredData.projectHint(),
                summarizeInvolvedSystems(entity.getLatestDevelopmentDraftJson()),
                IntakeDemandStatusRules.resolveEnrichmentStatus(entity),
                entity.getIntakeStatus(),
                entity.getConvertedWorkItemId(),
                entity.getActiveTodoCount() == null ? 0L : entity.getActiveTodoCount()
        );
    }

    private List<String> summarizeInvolvedSystems(String draftJson) {
        DevelopmentAnalysisDraft draft = readDevelopmentAnalysisDraft(draftJson);
        return summarizeInvolvedSystems(draft);
    }

    private List<String> summarizeInvolvedSystems(DevelopmentAnalysisDraft draft) {
        if (draft == null || draft.workItems() == null || draft.workItems().isEmpty()) {
            return List.of();
        }
        List<String> systems = new ArrayList<>();
        for (DevelopmentWorkItemDraft item : draft.workItems()) {
            if (item == null || item.systemTags() == null) {
                continue;
            }
            for (String tag : item.systemTags()) {
                String normalized = trimToNull(tag);
                if (normalized != null && !systems.contains(normalized)) {
                    systems.add(normalized);
                }
            }
        }
        return systems;
    }

    private DevelopmentAnalysisDraft readDevelopmentAnalysisDraft(String draftJson) {
        if (draftJson == null || draftJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(draftJson, DevelopmentAnalysisDraft.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("研发拆解草稿解析失败", ex);
        }
    }

    private IntakeStructuredData tryReadStructuredData(String structuredDataJson) {
        try {
            return readStructuredData(structuredDataJson);
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private boolean hasAnyContent(IntakeUploadRequest request,
                                  List<MultipartFile> screenshots,
                                  List<MultipartFile> attachments) {
        return trimToNull(request.getRawContent()) != null
                || hasAnyFile(screenshots)
                || hasAnyFile(attachments);
    }

    private boolean hasAnyFile(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String buildUploadRawContent(String rawContent,
                                         String businessLine,
                                         String businessLineCode,
                                         List<MultipartFile> screenshots,
                                         List<MultipartFile> attachments) {
        StringBuilder builder = new StringBuilder();
        String normalizedBusinessLine = trimToNull(businessLine);
        if (normalizedBusinessLine != null) {
            builder.append("业务线：").append(normalizedBusinessLine);
        }
        String normalizedBusinessLineCode = trimToNull(businessLineCode);
        if (normalizedBusinessLineCode != null) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("业务线编码：").append(normalizedBusinessLineCode);
        }
        String content = trimToNull(rawContent);
        if (content != null) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append(content.trim());
        }
        // 将文件名拼入原始文本，保证即使异步增强尚未完成，也能在待整理详情中看到上传上下文。
        appendFileSection(builder, "需求截图", screenshots);
        appendFileSection(builder, "需求附件", attachments);
        return builder.toString().trim();
    }

    private void appendFileSection(StringBuilder builder, String title, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        // 这里只记录文件名摘要，不直接处理二进制内容；真正的正文抽取和截图识别在异步 enrichment 中完成。
        List<String> fileNames = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(MultipartFile::getOriginalFilename)
                .filter(this::hasText)
                .map(String::trim)
                .toList();
        if (fileNames.isEmpty()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(title).append("：\n");
        for (String fileName : fileNames) {
            builder.append("- ").append(fileName).append('\n');
        }
    }

    private List<String> uploadedMaterialNames(List<MultipartFile> screenshots, List<MultipartFile> attachments) {
        List<String> names = new ArrayList<>();
        appendUploadedMaterialNames(names, "需求截图", screenshots);
        appendUploadedMaterialNames(names, "需求附件", attachments);
        return names;
    }

    private void appendUploadedMaterialNames(List<String> names, String category, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String fileName = trimToNull(file.getOriginalFilename());
            if (fileName != null) {
                names.add(category + "：" + fileName);
            }
        }
    }

    private IntakeStructuredData removeAttachmentSummary(IntakeStructuredData structuredData, String fileName) {
        if (structuredData == null || structuredData.attachmentSummaries() == null || structuredData.attachmentSummaries().isEmpty()) {
            return structuredData;
        }
        List<cn.aslight.workhub.model.intake.IntakeAttachmentSummary> summaries = structuredData.attachmentSummaries().stream()
                .filter(summary -> !java.util.Objects.equals(summary.fileName(), fileName))
                .toList();
        if (summaries.size() == structuredData.attachmentSummaries().size()) {
            return structuredData;
        }
        return new IntakeStructuredData(
                structuredData.category(),
                structuredData.approvalTitle(),
                structuredData.proposerName(),
                structuredData.developmentOwnerUserName(),
                structuredData.approvalCode(),
                structuredData.submittedTime(),
                structuredData.requirementType(),
                structuredData.developmentBranchName(),
                structuredData.zentaoUrl(),
                structuredData.requirementDigest(),
                structuredData.requirementName(),
                structuredData.requirementSummary(),
                structuredData.department(),
                structuredData.businessLine(),
                structuredData.businessLineCode(),
                structuredData.remark(),
                structuredData.plannedDueDate(),
                structuredData.plannedDevelopmentStartDate(),
                structuredData.plannedTestingStartDate(),
                structuredData.plannedReleaseDate(),
                structuredData.developmentStartedDate(),
                structuredData.actualEffort(),
                structuredData.testingStartedDate(),
                structuredData.actualCompletedTime(),
                structuredData.acceptanceTime(),
                structuredData.releasedTime(),
                structuredData.closedTime(),
                structuredData.closeReason(),
                structuredData.projectHint(),
                structuredData.fields(),
                summaries,
                structuredData.sqlDraft()
        );
    }

    private IntakeStructuredData withBusinessLine(IntakeStructuredData structuredData, String businessLine) {
        return withBusinessLine(structuredData, businessLine, null);
    }

    private IntakeStructuredData withBusinessLine(IntakeStructuredData structuredData,
                                                  String businessLine,
                                                  String businessLineCode) {
        if (structuredData == null) {
            return new IntakeStructuredData(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    businessLine,
                    businessLineCode,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    businessLine,
                    List.of(),
                    List.of(),
                    null
            );
        }
        return new IntakeStructuredData(
                structuredData.category(),
                structuredData.approvalTitle(),
                structuredData.proposerName(),
                structuredData.developmentOwnerUserName(),
                structuredData.approvalCode(),
                structuredData.submittedTime(),
                structuredData.requirementType(),
                structuredData.developmentBranchName(),
                structuredData.zentaoUrl(),
                structuredData.requirementDigest(),
                structuredData.requirementName(),
                structuredData.requirementSummary(),
                structuredData.department(),
                businessLine,
                businessLineCode,
                structuredData.remark(),
                structuredData.plannedDueDate(),
                structuredData.plannedDevelopmentStartDate(),
                structuredData.plannedTestingStartDate(),
                structuredData.plannedReleaseDate(),
                structuredData.developmentStartedDate(),
                structuredData.actualEffort(),
                structuredData.testingStartedDate(),
                structuredData.actualCompletedTime(),
                structuredData.scheduledAcceptanceDate(),
                structuredData.actualTestingEffort(),
                structuredData.actualTestingCompletedDate(),
                structuredData.acceptanceTime(),
                structuredData.releasedTime(),
                structuredData.closedTime(),
                structuredData.closeReason(),
                businessLine,
                structuredData.fields(),
                structuredData.attachmentSummaries(),
                structuredData.sqlDraft()
        );
    }

    private BusinessLineEntity resolveEnabledBusinessLine(String businessLineCode,
                                                          String businessLine,
                                                          boolean required) {
        String normalizedCode = trimToNull(businessLineCode);
        String normalizedName = trimToNull(businessLine);
        if (normalizedCode == null && normalizedName == null) {
            if (required) {
                throw new IllegalArgumentException("业务线不能为空");
            }
            return null;
        }
        if (businessLineMapper == null) {
            BusinessLineEntity fallback = new BusinessLineEntity();
            fallback.setBusinessLineCode(normalizedCode);
            fallback.setBusinessLineName(normalizedName);
            fallback.setEnabled(true);
            return fallback;
        }
        BusinessLineEntity entity = null;
        if (normalizedCode != null) {
            entity = businessLineMapper.findByCode(normalizedCode);
        }
        if (entity == null && normalizedName != null) {
            entity = businessLineMapper.findByName(normalizedName);
        }
        String label = normalizedCode == null ? normalizedName : normalizedCode;
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalArgumentException("业务线不存在或未启用：" + label);
        }
        return entity;
    }

    private String buildBusinessLineHistory(String previousBusinessLine,
                                            String businessLine,
                                            String previousProjectHint) {
        StringBuilder builder = new StringBuilder();
        builder.append("业务线：")
                .append(defaultHistoryValue(previousBusinessLine))
                .append(" -> ")
                .append(defaultHistoryValue(businessLine));
        if (!java.util.Objects.equals(previousProjectHint, businessLine)) {
            builder.append("\n项目提示：")
                    .append(defaultHistoryValue(previousProjectHint))
                    .append(" -> ")
                    .append(defaultHistoryValue(businessLine));
        }
        return builder.toString();
    }

    private IntakeAIDraft readDraft(String aiDraftJson) {
        if (aiDraftJson == null || aiDraftJson.isBlank()) {
            return null;
        }
        try {
            return EffortUnitNormalizer.normalizeDraft(objectMapper.readValue(aiDraftJson, IntakeAIDraft.class));
        } catch (JacksonException ex) {
            throw new IllegalStateException("AI 草稿解析失败", ex);
        }
    }

    private IntakeStructuredData readStructuredData(String structuredDataJson) {
        if (structuredDataJson == null || structuredDataJson.isBlank()) {
            return null;
        }
        try {
            return normalizeStructuredData(objectMapper.readValue(structuredDataJson, IntakeStructuredData.class));
        } catch (JacksonException ex) {
            throw new IllegalStateException("结构化需求解析失败", ex);
        }
    }

    private String writeDraftJson(IntakeAIDraft draft) {
        try {
            return objectMapper.writeValueAsString(EffortUnitNormalizer.normalizeDraft(draft));
        } catch (JacksonException ex) {
            throw new IllegalStateException("AI 草稿写入失败", ex);
        }
    }

    private String writeStructuredDataJson(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalizeStructuredData(structuredData));
        } catch (JacksonException ex) {
            throw new IllegalStateException("结构化需求写入失败", ex);
        }
    }

    private IntakeStructuredData normalizeStructuredData(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return null;
        }
        IntakeStructuredData effortNormalized = EffortUnitNormalizer.normalizeStructuredData(structuredData);
        structuredData = effortNormalized;
        String requirementType = normalizeRequirementType(structuredData.requirementType());
        String proposerName = normalizeProposerName(structuredData.proposerName(), structuredData.approvalTitle());
        String developmentBranchName = normalizeDevelopmentBranchName(
                structuredData.developmentBranchName(),
                requirementType,
                structuredData.approvalCode(),
                structuredData.submittedTime(),
                structuredData.requirementName(),
                structuredData.requirementDigest(),
                structuredData.requirementSummary()
        );
        String zentaoUrl = trimToNull(structuredData.zentaoUrl());
        if (java.util.Objects.equals(requirementType, structuredData.requirementType())
                && java.util.Objects.equals(proposerName, structuredData.proposerName())
                && structuredData.developmentOwnerUserName() == null
                && java.util.Objects.equals(developmentBranchName, structuredData.developmentBranchName())
                && java.util.Objects.equals(zentaoUrl, structuredData.zentaoUrl())) {
            return structuredData;
        }
        return new IntakeStructuredData(
                structuredData.category(),
                structuredData.approvalTitle(),
                proposerName,
                null,
                structuredData.approvalCode(),
                structuredData.submittedTime(),
                requirementType,
                developmentBranchName,
                zentaoUrl,
                structuredData.requirementDigest(),
                structuredData.requirementName(),
                structuredData.requirementSummary(),
                structuredData.department(),
                structuredData.businessLine(),
                structuredData.businessLineCode(),
                structuredData.remark(),
                structuredData.plannedDueDate(),
                structuredData.plannedDevelopmentStartDate(),
                structuredData.plannedTestingStartDate(),
                structuredData.plannedReleaseDate(),
                structuredData.developmentStartedDate(),
                structuredData.actualEffort(),
                structuredData.testingStartedDate(),
                structuredData.actualCompletedTime(),
                structuredData.acceptanceTime(),
                structuredData.releasedTime(),
                structuredData.closedTime(),
                structuredData.closeReason(),
                structuredData.projectHint(),
                structuredData.fields(),
                structuredData.attachmentSummaries(),
                structuredData.sqlDraft()
        );
    }

    private String normalizeProposerName(String proposerName, String approvalTitle) {
        String normalized = trimToNull(proposerName);
        if (normalized != null) {
            return normalized;
        }
        String title = trimToNull(approvalTitle);
        if (title == null) {
            return null;
        }
        int separator = title.indexOf('的');
        if (separator <= 0) {
            return null;
        }
        return trimToNull(title.substring(0, separator));
    }

    private String normalizeRequirementType(String requirementType) {
        String normalized = trimToNull(requirementType);
        if (normalized == null) {
            return null;
        }
        if ("研发".equals(normalized)) {
            return "研发需求";
        }
        return normalized;
    }

    private String normalizeDevelopmentBranchName(String developmentBranchName,
                                                  String requirementType,
                                                  String approvalCode,
                                                  String submittedTime,
                                                  String requirementName,
                                                  String requirementDigest,
                                                  String requirementSummary) {
        return DevelopmentBranchNameGenerator.normalize(
                developmentBranchName,
                requirementType,
                approvalCode,
                submittedTime,
                requirementName,
                requirementDigest,
                requirementSummary
        );
    }

    private String buildEnglishBranchSubject(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : BRANCH_SUBJECT_TRANSLATIONS) {
            if (normalized.contains(entry.getKey())) {
                return firstBranchWords(toBranchWords(entry.getValue()), 3);
            }
        }
        return null;
    }

    private String buildEnglishBranchSummary(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        LinkedHashSet<String> words = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : BRANCH_KEYWORD_TRANSLATIONS) {
            if (normalized.contains(entry.getKey())) {
                appendBranchWords(words, entry.getValue());
                if (words.size() >= 3) {
                    return firstBranchWords(words, 3);
                }
            }
        }
        String asciiToken = sanitizeAsciiBranchToken(normalized);
        if (asciiToken != null) {
            for (String word : asciiToken.split("_")) {
                appendBranchWords(words, word);
                if (words.size() >= 3) {
                    return firstBranchWords(words, 3);
                }
            }
        }
        if (words.isEmpty()) {
            return null;
        }
        return firstBranchWords(words, 3);
    }

    private void appendBranchWords(LinkedHashSet<String> words, String token) {
        String normalized = trimToNull(token);
        if (normalized == null) {
            return;
        }
        for (String word : normalized.split("_")) {
            String safeWord = sanitizeAsciiBranchToken(word);
            if (safeWord != null) {
                words.add(safeWord);
            }
        }
    }

    private LinkedHashSet<String> toBranchWords(String token) {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        appendBranchWords(words, token);
        return words;
    }

    private String firstBranchWords(LinkedHashSet<String> words, int limit) {
        return words.stream().limit(limit).collect(java.util.stream.Collectors.joining("_"));
    }

    private String resolveBranchDateToken(String submittedTime, String approvalCode) {
        String fromSubmittedTime = formatBranchDate(trimToNull(submittedTime));
        if (fromSubmittedTime != null) {
            return fromSubmittedTime;
        }
        String fromApprovalCode = firstRegexGroup(trimToNull(approvalCode), "(20\\d{6})");
        return fromApprovalCode == null ? null : fromApprovalCode;
    }

    private String formatBranchDate(String value) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{4})\\D*(\\d{1,2})\\D*(\\d{1,2})")
                .matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1) + padDatePart(matcher.group(2)) + padDatePart(matcher.group(3));
    }

    private String firstRegexGroup(String value, String regex) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String padDatePart(String value) {
        return value.length() == 1 ? "0" + value : value;
    }

    private String sanitizeBranchToken(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String sanitized = normalized.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHan}\\p{IsAlphabetic}\\p{IsDigit}]+", "-");
        sanitized = sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
        return sanitized.isEmpty() ? null : sanitized;
    }

    private String sanitizeAsciiBranchToken(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String sanitized = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        sanitized = sanitized.replaceAll("^_+", "").replaceAll("_+$", "");
        return sanitized.isEmpty() ? null : sanitized;
    }

    private IntakeStructuredData applyStageAction(IntakeStructuredData currentStructuredData,
                                                  IntakeStageActionRequest request,
                                                  String action) {
        IntakeStructuredData baseline = currentStructuredData == null
                ? intakeStructuredDataExtractor.extract("")
                : currentStructuredData;
        if (baseline == null) {
            baseline = new IntakeStructuredData(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null
            );
        }
        String plannedDueDate = baseline.plannedDueDate();
        String plannedDevelopmentStartDate = baseline.plannedDevelopmentStartDate();
        String plannedTestingStartDate = baseline.plannedTestingStartDate();
        String plannedReleaseDate = baseline.plannedReleaseDate();
        String developmentStartedDate = baseline.developmentStartedDate();
        String actualEffort = baseline.actualEffort();
        String testingStartedDate = baseline.testingStartedDate();
        String actualCompletedTime = baseline.actualCompletedTime();
        String scheduledAcceptanceDate = baseline.scheduledAcceptanceDate();
        String actualTestingEffort = baseline.actualTestingEffort();
        String actualTestingCompletedDate = baseline.actualTestingCompletedDate();
        String acceptanceTime = baseline.acceptanceTime();
        String releasedTime = baseline.releasedTime();
        String closedTime = baseline.closedTime();
        String closeReason = baseline.closeReason();

        switch (action) {
            case IntakeDemandStatusRules.ACTION_START_CLARIFICATION,
                    IntakeDemandStatusRules.ACTION_CONFIRM_RECORDED,
                    IntakeDemandStatusRules.ACTION_CONFIRM_CLARIFICATION -> {
            }
            case IntakeDemandStatusRules.ACTION_START_PROCESSING -> {
                if (isOperationsRequirement(baseline.requirementType())) {
                    developmentStartedDate = resolveActionDate(request.getOccurredAt());
                }
            }
            case IntakeDemandStatusRules.ACTION_CONFIRM_SCHEDULING -> {
                plannedDevelopmentStartDate = normalizeBusinessDate(request.getPlannedDevelopmentStartDate(), "预估开发日期");
                plannedTestingStartDate = normalizeBusinessDate(request.getPlannedTestingStartDate(), "预估提测日期");
                plannedReleaseDate = normalizeBusinessDate(request.getPlannedReleaseDate(), "预估上线日期");
                validateOrderedBusinessDate(plannedDevelopmentStartDate, plannedTestingStartDate, "预估提测日期不能早于预估开发日期");
                validateOrderedBusinessDate(plannedTestingStartDate, plannedReleaseDate, "预估上线日期不能早于预估提测日期");
            }
            case IntakeDemandStatusRules.ACTION_COMPLETE_EVALUATION ->
                    throw new IllegalArgumentException("待评估需求必须通过研发任务评估确认推进到待排期");
            case IntakeDemandStatusRules.ACTION_CONFIRM_DESIGN ->
                    developmentStartedDate = resolveActionDate(request.getOccurredAt());
            case IntakeDemandStatusRules.ACTION_SUBMIT_TESTING -> {
                actualEffort = requireEffortValue(request.getActualEffort(), "实际开发工时不能为空");
                actualCompletedTime = requireBusinessDate(request.getActualCompletedTime(), "实际开发完成日期不能为空", "实际开发完成日期");
                testingStartedDate = requireBusinessDate(request.getOccurredAt(), "实际提测日期不能为空", "实际提测日期");
            }
            case IntakeDemandStatusRules.ACTION_SUBMIT_ACCEPTANCE -> {
                actualEffort = normalizeEffortValue(request.getActualEffort());
                actualCompletedTime = firstNonBlank(request.getActualCompletedTime(), resolveActionDate(request.getOccurredAt()));
                if (isOperationsRequirement(baseline.requirementType())) {
                    testingStartedDate = firstNonBlank(testingStartedDate, actualCompletedTime);
                    releasedTime = actualCompletedTime;
                }
            }
            case IntakeDemandStatusRules.ACTION_PASS_TESTING -> {
                scheduledAcceptanceDate = requireBusinessDate(request.getScheduledAcceptanceDate(), "预约验收日期不能为空", "预约验收日期");
                actualTestingEffort = requireEffortValue(request.getActualTestingEffort(), "实际测试工时不能为空");
                actualTestingCompletedDate = requireBusinessDate(request.getActualTestingCompletedDate(), "实际测试完成日期不能为空", "实际测试完成日期");
            }
            case IntakeDemandStatusRules.ACTION_CONFIRM_RELEASE ->
                    releasedTime = requireBusinessDate(request.getOccurredAt(), "上线日期不能为空", "上线日期");
            case IntakeDemandStatusRules.ACTION_CONFIRM_ACCEPTANCE ->
                    acceptanceTime = requireBusinessDate(request.getAcceptanceTime(), "实际验收日期不能为空", "实际验收日期");
            case IntakeDemandStatusRules.ACTION_CLOSE_REQUIREMENT -> {
                closeReason = requireValue(request.getCloseReason(), "关闭原因不能为空");
                closedTime = resolveActionDate(request.getOccurredAt());
            }
            default -> throw new IllegalArgumentException("不支持的阶段动作");
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
                baseline.businessLineCode(),
                baseline.remark(),
                plannedDueDate,
                plannedDevelopmentStartDate,
                plannedTestingStartDate,
                plannedReleaseDate,
                developmentStartedDate,
                actualEffort,
                testingStartedDate,
                actualCompletedTime,
                scheduledAcceptanceDate,
                actualTestingEffort,
                actualTestingCompletedDate,
                acceptanceTime,
                releasedTime,
                closedTime,
                closeReason,
                baseline.projectHint(),
                baseline.fields() == null ? List.of() : baseline.fields(),
                baseline.attachmentSummaries() == null ? List.of() : baseline.attachmentSummaries(),
                baseline.sqlDraft()
        );
    }

    private IntakeStructuredData mergeZentaoLink(IntakeStructuredData currentStructuredData,
                                                 IntakeZentaoLinkRequest request) {
        IntakeStructuredData baseline = normalizeStructuredData(currentStructuredData);
        if (baseline == null) {
            throw new IllegalArgumentException("当前需求尚未生成结构化信息，不能关联禅道地址");
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
                trimToNull(request.getZentaoUrl()),
                baseline.requirementDigest(),
                baseline.requirementName(),
                baseline.requirementSummary(),
                baseline.department(),
                baseline.businessLine(),
                baseline.businessLineCode(),
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
                baseline.projectHint(),
                baseline.fields() == null ? List.of() : baseline.fields(),
                baseline.attachmentSummaries() == null ? List.of() : baseline.attachmentSummaries(),
                baseline.sqlDraft()
        );
    }

    private IntakeStructuredData mergeDevelopmentBranch(IntakeStructuredData currentStructuredData,
                                                        IntakeDevelopmentBranchRequest request) {
        IntakeStructuredData baseline = normalizeStructuredData(currentStructuredData);
        if (baseline == null) {
            throw new IllegalArgumentException("当前需求尚未生成结构化信息，不能更新研发分支");
        }
        String normalizedBranchName = normalizeDevelopmentBranchName(
                request.getDevelopmentBranchName(),
                baseline.requirementType(),
                baseline.approvalCode(),
                baseline.submittedTime(),
                baseline.requirementName(),
                baseline.requirementDigest(),
                baseline.requirementSummary()
        );
        return new IntakeStructuredData(
                baseline.category(),
                baseline.approvalTitle(),
                baseline.proposerName(),
                baseline.developmentOwnerUserName(),
                baseline.approvalCode(),
                baseline.submittedTime(),
                baseline.requirementType(),
                normalizedBranchName,
                baseline.zentaoUrl(),
                baseline.requirementDigest(),
                baseline.requirementName(),
                baseline.requirementSummary(),
                baseline.department(),
                baseline.businessLine(),
                baseline.businessLineCode(),
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
                baseline.projectHint(),
                baseline.fields() == null ? List.of() : baseline.fields(),
                baseline.attachmentSummaries() == null ? List.of() : baseline.attachmentSummaries(),
                baseline.sqlDraft()
        );
    }

    private IntakeStructuredData withSqlDraft(IntakeStructuredData baseline, IntakeSqlDraft sqlDraft) {
        IntakeStructuredData normalized = normalizeStructuredData(baseline);
        if (normalized == null) {
            throw new IllegalArgumentException("当前需求尚未生成结构化信息，不能生成 SQL 草稿");
        }
        return new IntakeStructuredData(
                normalized.category(),
                normalized.approvalTitle(),
                normalized.proposerName(),
                normalized.developmentOwnerUserName(),
                normalized.approvalCode(),
                normalized.submittedTime(),
                normalized.requirementType(),
                normalized.developmentBranchName(),
                normalized.zentaoUrl(),
                normalized.requirementDigest(),
                normalized.requirementName(),
                normalized.requirementSummary(),
                normalized.department(),
                normalized.businessLine(),
                normalized.businessLineCode(),
                normalized.remark(),
                normalized.plannedDueDate(),
                normalized.plannedDevelopmentStartDate(),
                normalized.plannedTestingStartDate(),
                normalized.plannedReleaseDate(),
                normalized.developmentStartedDate(),
                normalized.actualEffort(),
                normalized.testingStartedDate(),
                normalized.actualCompletedTime(),
                normalized.acceptanceTime(),
                normalized.releasedTime(),
                normalized.closedTime(),
                normalized.closeReason(),
                normalized.projectHint(),
                normalized.fields() == null ? List.of() : normalized.fields(),
                normalized.attachmentSummaries() == null ? List.of() : normalized.attachmentSummaries(),
                sqlDraft
        );
    }

    private String resolveActionDate(String preferredDate) {
        return firstNonBlank(preferredDate, LocalDate.now().toString().replace('-', '/'));
    }

    private String requireBusinessDate(String value, String blankMessage, String fieldName) {
        String normalized = requireValue(value, blankMessage);
        LocalDate parsed = parseBusinessDate(normalized);
        if (parsed == null) {
            throw new IllegalArgumentException(fieldName + "格式不正确，请使用 yyyy/MM/dd");
        }
        return parsed.toString().replace('-', '/');
    }

    private String normalizeBusinessDate(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        LocalDate parsed = parseBusinessDate(normalized);
        if (parsed == null) {
            throw new IllegalArgumentException(fieldName + "格式不正确，请使用 yyyy/MM/dd");
        }
        return parsed.toString().replace('-', '/');
    }

    private void validateOrderedBusinessDate(String earlier, String later, String message) {
        LocalDate earlierDate = parseBusinessDate(earlier);
        LocalDate laterDate = parseBusinessDate(later);
        if (earlierDate != null && laterDate != null && laterDate.isBefore(earlierDate)) {
            throw new IllegalArgumentException(message);
        }
    }

    private String requireValue(String value, String errorMessage) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String requireEffortValue(String value, String errorMessage) {
        String normalized = EffortUnitNormalizer.normalizeEffort(requireValue(value, errorMessage));
        if (!isNormalizedEffort(normalized)) {
            throw new IllegalArgumentException(resolveEffortFormatMessage(errorMessage));
        }
        return normalized;
    }

    private String normalizeEffortValue(String value) {
        String rawValue = trimToNull(value);
        if (rawValue == null) {
            return null;
        }
        String normalized = EffortUnitNormalizer.normalizeEffort(rawValue);
        if (!isNormalizedEffort(normalized)) {
            throw new IllegalArgumentException("实际工时格式不正确，请输入如 8h 或 1d");
        }
        return normalized;
    }

    private boolean isNormalizedEffort(String value) {
        return value != null && java.util.regex.Pattern
                .compile("^([0-9]+(?:\\.[0-9]+)?)h$")
                .matcher(value)
                .matches();
    }

    private String resolveEffortFormatMessage(String blankMessage) {
        return blankMessage.startsWith("实际")
                ? "实际工时格式不正确，请输入如 8h 或 1d"
                : "预估工时格式不正确，请输入如 8h 或 1d";
    }

    private String buildStageActionHistory(IntakeStructuredData before,
                                           IntakeStructuredData after,
                                           String beforeDemandStatus,
                                           String afterDemandStatus,
                                           List<String> uploadedFileNames) {
        List<String> changes = new java.util.ArrayList<>();
        appendChange(changes, "需求状态", beforeDemandStatus, afterDemandStatus);
        appendChange(changes, "预估完成时间", before == null ? null : before.plannedDueDate(), after == null ? null : after.plannedDueDate());
        appendChange(changes, "预估开发日期", before == null ? null : before.plannedDevelopmentStartDate(), after == null ? null : after.plannedDevelopmentStartDate());
        appendChange(changes, "预估提测日期", before == null ? null : before.plannedTestingStartDate(), after == null ? null : after.plannedTestingStartDate());
        appendChange(changes, "预估上线日期", before == null ? null : before.plannedReleaseDate(), after == null ? null : after.plannedReleaseDate());
        appendChange(changes, "研发开始日期", before == null ? null : before.developmentStartedDate(), after == null ? null : after.developmentStartedDate());
        appendChange(changes, "实际开发工时", before == null ? null : before.actualEffort(), after == null ? null : after.actualEffort());
        appendChange(changes, "实际提测日期", before == null ? null : before.testingStartedDate(), after == null ? null : after.testingStartedDate());
        appendChange(changes, "实际开发完成日期", before == null ? null : before.actualCompletedTime(), after == null ? null : after.actualCompletedTime());
        appendChange(changes, "预约验收日期", before == null ? null : before.scheduledAcceptanceDate(), after == null ? null : after.scheduledAcceptanceDate());
        appendChange(changes, "实际测试工时", before == null ? null : before.actualTestingEffort(), after == null ? null : after.actualTestingEffort());
        appendChange(changes, "实际测试完成日期", before == null ? null : before.actualTestingCompletedDate(), after == null ? null : after.actualTestingCompletedDate());
        appendChange(changes, "实际验收日期", before == null ? null : before.acceptanceTime(), after == null ? null : after.acceptanceTime());
        appendChange(changes, "实际上线日期", before == null ? null : before.releasedTime(), after == null ? null : after.releasedTime());
        appendChange(changes, "关闭时间", before == null ? null : before.closedTime(), after == null ? null : after.closedTime());
        appendChange(changes, "关闭原因", before == null ? null : before.closeReason(), after == null ? null : after.closeReason());
        appendUploadedFiles(changes, uploadedFileNames);
        if (changes.isEmpty()) {
            return null;
        }
        return String.join("\n", changes);
    }

    private String buildPauseDemandHistory(String beforeDemandStatus,
                                           String pauseReason,
                                           LocalDate pauseDate) {
        List<String> changes = new java.util.ArrayList<>();
        appendChange(changes, "需求状态", beforeDemandStatus, IntakeDemandStatusRules.PAUSED);
        appendChange(changes, "暂停日期", null, pauseDate == null ? null : pauseDate.toString());
        appendChange(changes, "暂停原因", null, pauseReason);
        return String.join("\n", changes);
    }

    private String buildResumeDemandHistory(String previousDemandStatus) {
        List<String> changes = new java.util.ArrayList<>();
        appendChange(changes, "需求状态", IntakeDemandStatusRules.PAUSED, previousDemandStatus);
        changes.add("暂停前状态：" + defaultHistoryValue(previousDemandStatus));
        return String.join("\n", changes);
    }

    private boolean isOperationsRequirement(String requirementType) {
        return "数据提取/运维".equals(trimToNull(requirementType));
    }

    private boolean isDevelopmentRequirement(IntakeStructuredData structuredData) {
        return structuredData != null && "研发需求".equals(trimToNull(structuredData.requirementType()));
    }

    private void schedulePostStageAutomation(Long intakeId,
                                             String action,
                                             IntakeStructuredData nextStructuredData,
                                             IntakeStageActionRequest request,
                                             String operatorUserName) {
        if (intakeClarificationAnalysisService == null || !isDevelopmentRequirement(nextStructuredData)) {
            return;
        }
        if (IntakeDemandStatusRules.ACTION_START_CLARIFICATION.equals(action)) {
            if (request != null && Boolean.FALSE.equals(request.getAiClarificationEnabled())) {
                return;
            }
            runAfterCommit(() -> intakeClarificationAnalysisService.analyze(intakeId, operatorUserName));
        } else if (IntakeDemandStatusRules.ACTION_CONFIRM_CLARIFICATION.equals(action)) {
            runAfterCommit(() -> intakeClarificationAnalysisService.triggerDevelopmentAnalysis(intakeId, operatorUserName));
        }
    }

    private void runAfterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runAutomationSafely(runnable);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runAutomationSafely(runnable);
            }
        });
    }

    private void runAutomationSafely(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            log.warn("Post stage automation failed", ex);
        }
    }

    private String buildZentaoLinkHistory(IntakeStructuredData before, IntakeStructuredData after) {
        List<String> changes = new java.util.ArrayList<>();
        appendChange(changes, "禅道地址", before == null ? null : before.zentaoUrl(), after == null ? null : after.zentaoUrl());
        if (changes.isEmpty()) {
            return null;
        }
        return String.join("\n", changes);
    }

    private String buildDevelopmentBranchHistory(IntakeStructuredData before, IntakeStructuredData after) {
        List<String> changes = new java.util.ArrayList<>();
        appendChange(changes, "研发分支", before == null ? null : before.developmentBranchName(), after == null ? null : after.developmentBranchName());
        if (changes.isEmpty()) {
            return null;
        }
        return String.join("\n", changes);
    }

    private String buildSqlDraftHistory(IntakeSqlDraft sqlDraft) {
        if (sqlDraft == null) {
            return "SQL 草稿生成失败";
        }
        List<String> details = new java.util.ArrayList<>();
        details.add("SQL 方言：" + defaultHistoryValue(sqlDraft.dialect()));
        details.add("用途说明：" + defaultHistoryValue(sqlDraft.explanation()));
        details.add("待确认问题：" + (sqlDraft.questions() == null || sqlDraft.questions().isEmpty()
                ? "-"
                : String.join("；", sqlDraft.questions())));
        details.add("SQL 草稿：\n" + defaultHistoryValue(sqlDraft.sql()));
        return String.join("\n", details);
    }

    private String resolveStageActionSummary(String action) {
        return switch (action) {
            case IntakeDemandStatusRules.ACTION_START_CLARIFICATION -> "开始澄清";
            case IntakeDemandStatusRules.ACTION_CONFIRM_RECORDED -> "确认收录";
            case IntakeDemandStatusRules.ACTION_CONFIRM_CLARIFICATION -> "澄清完成";
            case IntakeDemandStatusRules.ACTION_START_PROCESSING -> "开始处理";
            case IntakeDemandStatusRules.ACTION_SUBMIT_ACCEPTANCE -> "提交验收";
            case IntakeDemandStatusRules.ACTION_COMPLETE_EVALUATION -> "评估完成";
            case IntakeDemandStatusRules.ACTION_CONFIRM_SCHEDULING -> "排期确认";
            case IntakeDemandStatusRules.ACTION_CONFIRM_DESIGN -> "设计完成";
            case IntakeDemandStatusRules.ACTION_SUBMIT_TESTING -> "提交测试";
            case IntakeDemandStatusRules.ACTION_PASS_TESTING -> "测试通过";
            case IntakeDemandStatusRules.ACTION_CONFIRM_RELEASE -> "确认上线";
            case IntakeDemandStatusRules.ACTION_CONFIRM_ACCEPTANCE -> "验收通过";
            case IntakeDemandStatusRules.ACTION_CLOSE_REQUIREMENT -> "关闭需求";
            default -> "推进需求阶段";
        };
    }

    private void appendChange(List<String> changes, String label, String before, String after) {
        String beforeValue = defaultHistoryValue(before);
        String afterValue = defaultHistoryValue(after);
        if (!beforeValue.equals(afterValue)) {
            changes.add(label + "：" + beforeValue + " -> " + afterValue);
        }
    }

    private void appendUploadedFiles(List<String> changes, List<String> uploadedFileNames) {
        if (uploadedFileNames == null || uploadedFileNames.isEmpty()) {
            return;
        }
        changes.add("上传数据文件：" + String.join("、", uploadedFileNames));
    }

    private List<String> saveStageActionFiles(Long intakeId, String action, List<MultipartFile> dataFiles) {
        if (dataFiles == null || dataFiles.isEmpty()) {
            return List.of();
        }
        List<String> uploadedFileNames = new ArrayList<>();
        for (MultipartFile dataFile : dataFiles) {
            if (dataFile == null || dataFile.isEmpty()) {
                continue;
            }
            String fileName = trimToNull(dataFile.getOriginalFilename());
            if (fileName != null) {
                uploadedFileNames.add(fileName);
            }
        }
        if (!uploadedFileNames.isEmpty()) {
            attachmentService.saveIntakeDeliveryFiles(intakeId, dataFiles);
        }
        return uploadedFileNames;
    }

    private String defaultHistoryValue(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "-" : normalized;
    }

    private void recordHistory(Long intakeId,
                               String actionType,
                               String actionSummary,
                               String detailText,
                               String operatorUserName) {
        IntakeHistoryEntity entity = new IntakeHistoryEntity();
        entity.setIntakeId(intakeId);
        entity.setActionType(actionType);
        entity.setActionSummary(actionSummary);
        entity.setDetailText(detailText);
        entity.setOperatorUserName(trimToNull(operatorUserName) == null ? "system" : operatorUserName.trim());
        intakeHistoryMapper.insert(entity);
    }

    private IntakeHistoryResponse toHistoryResponse(IntakeHistoryEntity entity) {
        return new IntakeHistoryResponse(
                entity.getId(),
                entity.getActionType(),
                entity.getActionSummary(),
                entity.getDetailText(),
                entity.getOperatorUserName(),
                entity.getCreatedAt()
        );
    }

    private String defaultChannel(String channel, String fallback) {
        return trimToNull(channel) == null ? fallback : channel.trim();
    }

    private String firstNonBlank(String preferred, String fallback) {
        String preferredValue = trimToNull(preferred);
        if (preferredValue != null) {
            return preferredValue;
        }
        return trimToNull(fallback);
    }

    private DashboardBusinessLine dashboardBusinessLine(IntakeSummaryResponse record,
                                                        Map<String, String> businessLineNameByCode,
                                                        Map<String, BusinessLineEntity> businessLineByName) {
        String businessLineCode = trimToNull(record.businessLineCode());
        if (businessLineCode != null) {
            return new DashboardBusinessLine(
                    "code:" + businessLineCode,
                    businessLineCode,
                    dashboardBusinessLineName(record, businessLineNameByCode)
            );
        }
        String businessLine = trimToNull(record.businessLine());
        if (businessLine != null) {
            BusinessLineEntity entity = resolveBusinessLineByName(businessLine, businessLineByName);
            String resolvedCode = entity == null ? null : trimToNull(entity.getBusinessLineCode());
            String resolvedName = entity == null ? null : trimToNull(entity.getBusinessLineName());
            if (resolvedCode != null) {
                return new DashboardBusinessLine(
                        "code:" + resolvedCode,
                        resolvedCode,
                        firstNonBlank(resolvedName, businessLine)
                );
            }
            return new DashboardBusinessLine("name:" + businessLine, null, businessLine);
        }
        return new DashboardBusinessLine("empty", null, "未填写业务线");
    }

    private String dashboardBusinessLineName(IntakeSummaryResponse record, Map<String, String> businessLineNameByCode) {
        String businessLineCode = trimToNull(record.businessLineCode());
        if (businessLineCode != null) {
            String catalogName = businessLineNameByCode.computeIfAbsent(businessLineCode, this::resolveBusinessLineNameByCode);
            if (!businessLineCode.equals(catalogName)) {
                return catalogName;
            }
            return firstNonBlank(record.businessLine(), catalogName);
        }
        return firstNonBlank(record.businessLine(), "未填写业务线");
    }

    private BusinessLineEntity resolveBusinessLineByName(String businessLine, Map<String, BusinessLineEntity> businessLineByName) {
        if (businessLineMapper == null) {
            return null;
        }
        String normalizedBusinessLine = trimToNull(businessLine);
        if (normalizedBusinessLine == null) {
            return null;
        }
        if (businessLineByName.containsKey(normalizedBusinessLine)) {
            return businessLineByName.get(normalizedBusinessLine);
        }
        BusinessLineEntity entity = businessLineMapper.findByName(normalizedBusinessLine);
        businessLineByName.put(normalizedBusinessLine, entity);
        return entity;
    }

    private String resolveBusinessLineNameByCode(String businessLineCode) {
        if (businessLineMapper != null) {
            BusinessLineEntity entity = businessLineMapper.findByCode(businessLineCode);
            if (entity != null && trimToNull(entity.getBusinessLineName()) != null) {
                return entity.getBusinessLineName().trim();
            }
        }
        return businessLineCode;
    }

    private IntakeDashboardResponse.DashboardDemand toDashboardDemand(IntakeSummaryResponse record,
                                                                      String groupLabel,
                                                                      String groupTagClass) {
        return new IntakeDashboardResponse.DashboardDemand(
                record.id(),
                dashboardDemandTitle(record),
                firstNonBlank(record.businessLine(), "未填写业务线"),
                record.businessLineCode(),
                firstNonBlank(record.demandStatus(), "-"),
                firstNonBlank(record.requirementType(), "-"),
                firstNonBlank(record.approvalCode(), "-"),
                firstNonBlank(firstNonBlank(record.proposerName(), record.senderName()), "-"),
                formatDashboardDateTime(record.submittedTime(), record.receivedAt()),
                firstNonBlank(firstNonBlank(record.remark(), record.requirementSummary()), "等待继续推进需求阶段"),
                groupLabel,
                groupTagClass
        );
    }

    private IntakeDashboardResponse.ProgressReportDemand toProgressReportDemand(IntakeSummaryResponse record) {
        return new IntakeDashboardResponse.ProgressReportDemand(
                record.id(),
                dashboardDemandTitle(record),
                firstNonBlank(record.businessLine(), "未填写业务线"),
                firstNonBlank(record.demandStatus(), "-"),
                firstNonBlank(record.approvalCode(), "-"),
                firstNonBlank(firstNonBlank(record.proposerName(), record.senderName()), "-"),
                formatDashboardDateTime(record.submittedTime(), record.receivedAt())
        );
    }

    private IntakeDashboardResponse.ProgressReportReleasedDemand toProgressReportReleasedDemand(IntakeSummaryResponse record) {
        return new IntakeDashboardResponse.ProgressReportReleasedDemand(
                record.id(),
                dashboardDemandTitle(record),
                firstNonBlank(record.businessLine(), "未填写业务线"),
                firstNonBlank(record.demandStatus(), "-"),
                firstNonBlank(record.approvalCode(), "-"),
                firstNonBlank(firstNonBlank(record.proposerName(), record.senderName()), "-"),
                formatDashboardDateTime(record.submittedTime(), record.receivedAt()),
                formatDashboardDate(record.releasedTime())
        );
    }

    private String dashboardDemandTitle(IntakeSummaryResponse record) {
        return firstNonBlank(firstNonBlank(record.requirementName(), record.requirementDigest()), firstNonBlank(record.approvalCode(), "未命名需求"));
    }

    private boolean isUnreleasedDemand(IntakeSummaryResponse record) {
        String demandStatus = trimToNull(record.demandStatus());
        return demandStatus != null && !UNRELEASED_EXCLUDED_STATUSES.contains(demandStatus);
    }

    private boolean isWeeklyReleasedDemand(IntakeSummaryResponse record, LocalDate dateInWeek) {
        LocalDate releasedDate = parseDashboardDate(record.releasedTime());
        if (releasedDate == null) {
            return false;
        }
        int day = dateInWeek.getDayOfWeek().getValue();
        LocalDate start = dateInWeek.minusDays(day - 1L);
        LocalDate end = start.plusDays(6);
        return !releasedDate.isBefore(start) && !releasedDate.isAfter(end);
    }

    private long sortableDemandTime(IntakeSummaryResponse record) {
        LocalDateTime parsed = parseDashboardDateTime(firstNonBlank(record.submittedTime(), formatDashboardDateTime(record.receivedAt())));
        return parsed == null ? 0 : parsed.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long sortableReleaseTime(IntakeSummaryResponse record) {
        LocalDate releasedDate = parseDashboardDate(record.releasedTime());
        return releasedDate == null ? 0 : releasedDate.toEpochDay();
    }

    private LocalDate parseDashboardDate(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "无".equals(normalized)) {
            return null;
        }
        String datePart = normalized.replace('T', ' ').split(" ")[0].replace('/', '-');
        try {
            return LocalDate.parse(datePart);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDateTime parseDashboardDateTime(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "无".equals(normalized)) {
            return null;
        }
        String candidate = normalized.replace('T', ' ').replace('/', '-');
        try {
            return LocalDateTime.parse(candidate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(candidate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (RuntimeException ignoredAgain) {
                LocalDate date = parseDashboardDate(candidate);
                return date == null ? null : date.atStartOfDay();
            }
        }
    }

    private String formatDashboardDateTime(String submittedTime, LocalDateTime receivedAt) {
        String submitted = trimToNull(submittedTime);
        return submitted == null ? formatDashboardDateTime(receivedAt) : submitted.replace('T', ' ');
    }

    private String formatDashboardDateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatDashboardDate(LocalDate value) {
        return value == null ? "-" : value.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String formatDashboardDate(String value) {
        LocalDate date = parseDashboardDate(value);
        return date == null ? "-" : formatDashboardDate(date);
    }

    private String normalizeJsonText(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "null".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String legacyEstimatedEffort(String structuredDataJson) {
        String value = structuredJsonText(structuredDataJson, "estimatedEffort");
        if (value == null) {
            return null;
        }
        return normalizeJsonText(EffortUnitNormalizer.normalizeEffort(value));
    }

    private String structuredJsonText(String structuredDataJson, String fieldName) {
        String normalizedJson = trimToNull(structuredDataJson);
        if (normalizedJson == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(normalizedJson);
            return normalizeJsonText(node.path(fieldName).asText(null));
        } catch (JacksonException ex) {
            return null;
        }
    }

    private boolean matchesRequirementName(IntakeSummaryResponse item, String requirementName) {
        if (requirementName == null) {
            return true;
        }
        return contains(item.requirementName(), requirementName)
                || contains(item.requirementDigest(), requirementName);
    }

    private int demandStatusSortOrder(IntakeSummaryResponse item) {
        String demandStatus = item == null ? null : trimToNull(item.demandStatus());
        if (demandStatus == null) {
            return DEMAND_STATUS_SORT_ORDER.size();
        }
        int order = DEMAND_STATUS_SORT_ORDER.indexOf(demandStatus);
        return order >= 0 ? order : DEMAND_STATUS_SORT_ORDER.size();
    }

    private int enrichmentSortOrder(IntakeSummaryResponse item) {
        String enrichmentStatus = item == null ? null : trimToNull(item.enrichmentStatus());
        return IntakeEnrichmentStatus.RUNNING.equals(enrichmentStatus) ? 0 : 1;
    }

    private boolean matchesApprovalCode(IntakeSummaryResponse item, String approvalCode) {
        return approvalCode == null || contains(item.approvalCode(), approvalCode);
    }

    private boolean matchesProposerName(IntakeSummaryResponse item, String proposerName) {
        return proposerName == null || contains(item.proposerName(), proposerName);
    }

    private boolean matchesBusinessLine(IntakeSummaryResponse item, String businessLine) {
        return businessLine == null
                || businessLine.equals(item.businessLineCode())
                || businessLine.equals(item.businessLine());
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
    }

    private boolean matchesReleasedDate(IntakeSummaryResponse item, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return true;
        }
        LocalDate releasedDate = parseBusinessDate(item.releasedTime());
        if (releasedDate == null) {
            return false;
        }
        if (startDate != null && releasedDate.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !releasedDate.isAfter(endDate);
    }

    private LocalDate parseFilterDate(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        LocalDate parsed = parseBusinessDate(normalized);
        if (parsed == null) {
            throw new IllegalArgumentException(fieldName + "格式不正确，请使用 yyyy/MM/dd");
        }
        return parsed;
    }

    private LocalDate parseBusinessDate(String value) {
        String normalized = trimToNull(value);
        if (normalized == null || "无".equals(normalized) || "-".equals(normalized)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})")
                .matcher(normalized.replace('T', ' '));
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String defaultValue(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "-" : normalized;
    }

    private record DashboardDemandGroup(String key,
                                        String label,
                                        List<String> statuses,
                                        String note,
                                        String className,
                                        String tagClass) {
        private boolean matches(String demandStatus) {
            return statuses.contains(demandStatus);
        }
    }

    private record DashboardBusinessLine(String key, String code, String name) {
    }

    private enum DashboardDemandType {
        ALL("所有"),
        DEVELOPMENT("研发需求"),
        OPERATIONS("数据运维需求");

        private final String label;

        DashboardDemandType(String label) {
            this.label = label;
        }

        private boolean matches(String requirementType) {
            return switch (this) {
                case DEVELOPMENT -> "研发需求".equals(requirementType);
                case OPERATIONS -> "数据提取/运维".equals(requirementType);
                case ALL -> true;
            };
        }

        private static DashboardDemandType from(String value) {
            String normalized = value == null ? "" : value.trim();
            for (DashboardDemandType demandType : values()) {
                if (demandType.name().equalsIgnoreCase(normalized)) {
                    return demandType;
                }
            }
            return ALL;
        }
    }

    private static class MutableBusinessLineStats {
        private String businessLineCode;
        private String businessLine;
        private int notStartedCount;
        private int developingCount;
        private int testingCount;
        private int pendingReleaseCount;
        private int totalCount;
        private int demandCount;

        private MutableBusinessLineStats(String businessLineCode, String businessLine) {
            this.businessLineCode = businessLineCode;
            this.businessLine = businessLine;
        }

        private void refreshBusinessLineCode(String candidate) {
            if (businessLineCode == null && candidate != null) {
                businessLineCode = candidate;
            }
        }

        private void refreshBusinessLineName(String candidate) {
            if (businessLine == null || "未填写业务线".equals(businessLine) || Objects.equals(businessLine, businessLineCode)) {
                businessLine = candidate;
            }
        }

        private IntakeDashboardResponse.BusinessLineStats toResponse(int totalDemandCount) {
            return new IntakeDashboardResponse.BusinessLineStats(
                    businessLineCode,
                    businessLine,
                    notStartedCount,
                    developingCount,
                    testingCount,
                    pendingReleaseCount,
                    totalCount,
                    demandCount,
                    totalDemandCount == 0 ? 0 : (double) demandCount / totalDemandCount
            );
        }
    }
}
