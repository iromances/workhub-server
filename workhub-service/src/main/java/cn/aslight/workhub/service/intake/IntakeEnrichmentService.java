package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.attachment.AttachmentTextExtractionService;
import cn.aslight.workhub.dao.intake.IntakeStructuredFieldMapper;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredFieldEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 待整理箱异步增强服务。
 */
@Service
public class IntakeEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(IntakeEnrichmentService.class);

    private final IntakeMapper intakeMapper;
    private final IntakeStructuredFieldMapper intakeStructuredFieldMapper;
    private final AttachmentService attachmentService;
    private final AttachmentTextExtractionService attachmentTextExtractionService;
    private final IntakeStructuredDataExtractor intakeStructuredDataExtractor;
    private final IntakeStructuredFieldNormalizer intakeStructuredFieldNormalizer;
    private final CodexCliStructuredExtractor codexCliStructuredExtractor;
    private final Executor workhubTaskExecutor;
    private final ObjectMapper objectMapper;

    public IntakeEnrichmentService(IntakeMapper intakeMapper,
                                   AttachmentService attachmentService,
                                   AttachmentTextExtractionService attachmentTextExtractionService,
                                   IntakeStructuredDataExtractor intakeStructuredDataExtractor,
                                   CodexCliStructuredExtractor codexCliStructuredExtractor,
                                   @Qualifier("workhubTaskExecutor") Executor workhubTaskExecutor,
                                   ObjectMapper objectMapper) {
        this(
                intakeMapper,
                new NoopIntakeStructuredFieldMapper(),
                attachmentService,
                attachmentTextExtractionService,
                intakeStructuredDataExtractor,
                new IntakeStructuredFieldNormalizer(),
                codexCliStructuredExtractor,
                workhubTaskExecutor,
                objectMapper
        );
    }

    @Autowired
    public IntakeEnrichmentService(IntakeMapper intakeMapper,
                                   IntakeStructuredFieldMapper intakeStructuredFieldMapper,
                                   AttachmentService attachmentService,
                                   AttachmentTextExtractionService attachmentTextExtractionService,
                                   IntakeStructuredDataExtractor intakeStructuredDataExtractor,
                                   IntakeStructuredFieldNormalizer intakeStructuredFieldNormalizer,
                                   CodexCliStructuredExtractor codexCliStructuredExtractor,
                                   @Qualifier("workhubTaskExecutor") Executor workhubTaskExecutor,
                                   ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.intakeStructuredFieldMapper = intakeStructuredFieldMapper;
        this.attachmentService = attachmentService;
        this.attachmentTextExtractionService = attachmentTextExtractionService;
        this.intakeStructuredDataExtractor = intakeStructuredDataExtractor;
        this.intakeStructuredFieldNormalizer = intakeStructuredFieldNormalizer;
        this.codexCliStructuredExtractor = codexCliStructuredExtractor;
        this.workhubTaskExecutor = workhubTaskExecutor;
        this.objectMapper = objectMapper;
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

    public void scheduleUploadedEnrichment(Long intakeId, String sourceChannel, String rawContent) {
        log.info("Scheduling intake enrichment. intakeId={}, sourceChannel={}, rawContentPreview={}",
                intakeId,
                sourceChannel,
                rawContent == null ? null : truncate(rawContent, 200));
        IntakeRecordEntity existing = requireExisting(intakeId);
        intakeMapper.updateEnrichmentState(
                intakeId,
                IntakeEnrichmentStatus.PENDING,
                IntakeDemandStatusRules.resolveWhenRunning(existing.getDemandStatus()),
                null,
                LocalDateTime.now()
        );
        Runnable task = () -> enrichUploadedIntake(intakeId, sourceChannel, rawContent);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    workhubTaskExecutor.execute(task);
                }
            });
            return;
        }
        workhubTaskExecutor.execute(task);
    }

    void enrichUploadedIntake(Long intakeId, String sourceChannel, String rawContent) {
        log.info("Intake enrichment started. intakeId={}, sourceChannel={}", intakeId, sourceChannel);
        IntakeRecordEntity runningEntity = requireExisting(intakeId);
        intakeMapper.updateEnrichmentState(
                intakeId,
                IntakeEnrichmentStatus.RUNNING,
                IntakeDemandStatusRules.resolveWhenRunning(runningEntity.getDemandStatus()),
                null,
                LocalDateTime.now()
        );
        try {
            IntakeRecordEntity entity = requireExisting(intakeId);
            List<AttachmentService.AttachmentFileContext> attachments = attachmentService.listIntakeFileContexts(intakeId);
            log.info("Loaded intake attachments for enrichment. intakeId={}, attachmentCount={}", intakeId, attachments.size());
            AttachmentTextExtractionService.AttachmentExtractionBatch extractionBatch =
                    attachmentTextExtractionService.extractSummaries(attachments);
            log.info("Attachment text extraction finished. intakeId={}, summaryCount={}, warnings={}",
                    intakeId,
                    extractionBatch.summaries().size(),
                    extractionBatch.warnings());

            IntakeStructuredData baseline = toStructuredData(entity, intakeStructuredFieldMapper.findByIntakeId(intakeId));
            IntakeStructuredData attachmentStructuredData = intakeStructuredDataExtractor.extract(
                    buildAttachmentSummarySource(extractionBatch.summaries())
            );
            IntakeStructuredData merged = mergeStructuredData(
                    baseline,
                    withAttachmentSummaries(attachmentStructuredData, extractionBatch.summaries())
            );

            List<String> warnings = new ArrayList<>(extractionBatch.warnings());
            CodexCliStructuredExtractor.CodexCliExtractionResult codexResult = codexCliStructuredExtractor.extract(
                    sourceChannel,
                    rawContent,
                    attachments,
                    extractionBatch.summaries()
            );
            if (codexResult.structuredData() != null) {
                merged = mergeStructuredData(merged, codexResult.structuredData());
            }
            if (codexResult.failureSummary() != null) {
                warnings.add(codexResult.failureSummary());
            }
            log.info("Codex CLI enrichment finished. intakeId={}, codexAttempted={}, codexFailureSummary={}, mergedFieldCount={}, attachmentSummaryCount={}",
                    intakeId,
                    codexResult.attempted(),
                    codexResult.failureSummary(),
                    merged == null || merged.fields() == null ? 0 : merged.fields().size(),
                    merged == null || merged.attachmentSummaries() == null ? 0 : merged.attachmentSummaries().size());

            boolean shouldMarkFailed = codexResult.failureSummary() != null;
            String nextStatus = shouldMarkFailed ? IntakeEnrichmentStatus.FAILED : IntakeEnrichmentStatus.SUCCEEDED;
            String warningSummary = summarizeWarnings(warnings);
            merged = normalizeDevelopmentBranchName(merged);
            String nextDemandStatus = IntakeDemandStatusRules.resolveAfterEnrichment(entity.getDemandStatus(), nextStatus, merged);

            intakeStructuredFieldNormalizer.applyStructuredData(entity, merged);
            intakeMapper.updateFormalFields(entity);
            replaceStructuredFields(intakeId, merged);
            intakeMapper.updateStructuredDataAndEnrichment(
                    intakeId,
                    writeStructuredDataJson(merged),
                    nextDemandStatus,
                    nextStatus,
                    warningSummary,
                    LocalDateTime.now()
            );
            log.info("Intake enrichment completed. intakeId={}, nextStatus={}, warnings={}", intakeId, nextStatus, warnings);
        } catch (Exception ex) {
            log.warn("Intake enrichment failed. intakeId={}", intakeId, ex);
            IntakeRecordEntity failedEntity = requireExisting(intakeId);
            intakeMapper.updateEnrichmentState(
                    intakeId,
                    IntakeEnrichmentStatus.FAILED,
                    IntakeDemandStatusRules.resolveAfterEnrichment(
                            failedEntity.getDemandStatus(),
                            IntakeEnrichmentStatus.FAILED,
                            toStructuredData(failedEntity, intakeStructuredFieldMapper.findByIntakeId(intakeId))
                    ),
                    summarizeException(ex),
                    LocalDateTime.now()
            );
        }
    }

    private IntakeStructuredData withAttachmentSummaries(IntakeStructuredData structuredData,
                                                         List<IntakeAttachmentSummary> attachmentSummaries) {
        List<IntakeAttachmentSummary> safeSummaries = attachmentSummaries == null ? List.of() : attachmentSummaries;
        if (structuredData == null) {
            return new IntakeStructuredData(
                    safeSummaries.isEmpty() ? null : "待整理项",
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
                    safeSummaries,
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
                structuredData.scheduledAcceptanceDate(),
                structuredData.actualTestingEffort(),
                structuredData.actualTestingCompletedDate(),
                structuredData.acceptanceTime(),
                structuredData.releasedTime(),
                structuredData.closedTime(),
                structuredData.closeReason(),
                structuredData.projectHint(),
                safeFields(structuredData.fields()),
                safeSummaries,
                structuredData.sqlDraft()
        );
    }

    private IntakeStructuredData mergeStructuredData(IntakeStructuredData baseline, IntakeStructuredData enriched) {
        if (baseline == null) {
            return enriched;
        }
        if (enriched == null) {
            return baseline;
        }
        return new IntakeStructuredData(
                firstNonBlank(enriched.category(), baseline.category()),
                firstNonBlank(enriched.approvalTitle(), baseline.approvalTitle()),
                firstNonBlank(enriched.proposerName(), baseline.proposerName()),
                firstNonBlank(enriched.developmentOwnerUserName(), baseline.developmentOwnerUserName()),
                firstNonBlank(enriched.approvalCode(), baseline.approvalCode()),
                firstNonBlank(enriched.submittedTime(), baseline.submittedTime()),
                firstNonBlank(enriched.requirementType(), baseline.requirementType()),
                firstNonBlank(enriched.developmentBranchName(), baseline.developmentBranchName()),
                firstNonBlank(enriched.zentaoUrl(), baseline.zentaoUrl()),
                firstNonBlank(enriched.requirementDigest(), baseline.requirementDigest()),
                firstNonBlank(enriched.requirementName(), baseline.requirementName()),
                firstNonBlank(enriched.requirementSummary(), baseline.requirementSummary()),
                firstNonBlank(enriched.department(), baseline.department()),
                firstNonBlank(enriched.businessLine(), baseline.businessLine()),
                firstNonBlank(enriched.businessLineCode(), baseline.businessLineCode()),
                firstNonBlank(enriched.remark(), baseline.remark()),
                firstNonBlank(enriched.plannedDueDate(), baseline.plannedDueDate()),
                firstNonBlank(enriched.plannedDevelopmentStartDate(), baseline.plannedDevelopmentStartDate()),
                firstNonBlank(enriched.plannedTestingStartDate(), baseline.plannedTestingStartDate()),
                firstNonBlank(enriched.plannedReleaseDate(), baseline.plannedReleaseDate()),
                firstNonBlank(enriched.developmentStartedDate(), baseline.developmentStartedDate()),
                firstNonBlank(enriched.actualEffort(), baseline.actualEffort()),
                firstNonBlank(enriched.testingStartedDate(), baseline.testingStartedDate()),
                firstNonBlank(enriched.actualCompletedTime(), baseline.actualCompletedTime()),
                firstNonBlank(enriched.scheduledAcceptanceDate(), baseline.scheduledAcceptanceDate()),
                firstNonBlank(enriched.actualTestingEffort(), baseline.actualTestingEffort()),
                firstNonBlank(enriched.actualTestingCompletedDate(), baseline.actualTestingCompletedDate()),
                firstNonBlank(enriched.acceptanceTime(), baseline.acceptanceTime()),
                baseline.releasedTime(),
                firstNonBlank(enriched.closedTime(), baseline.closedTime()),
                firstNonBlank(enriched.closeReason(), baseline.closeReason()),
                firstNonBlank(enriched.projectHint(), baseline.projectHint()),
                mergeFields(baseline.fields(), enriched.fields()),
                mergeAttachmentSummaries(baseline.attachmentSummaries(), enriched.attachmentSummaries()),
                enriched.sqlDraft() == null ? baseline.sqlDraft() : enriched.sqlDraft()
        );
    }

    private IntakeStructuredData normalizeDevelopmentBranchName(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return null;
        }
        String developmentBranchName = DevelopmentBranchNameGenerator.normalize(
                structuredData.developmentBranchName(),
                structuredData.requirementType(),
                structuredData.approvalCode(),
                structuredData.submittedTime(),
                structuredData.requirementName(),
                structuredData.requirementDigest(),
                structuredData.requirementSummary()
        );
        if (java.util.Objects.equals(developmentBranchName, structuredData.developmentBranchName())) {
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
                developmentBranchName,
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
                structuredData.scheduledAcceptanceDate(),
                structuredData.actualTestingEffort(),
                structuredData.actualTestingCompletedDate(),
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

    private List<IntakeStructuredField> mergeFields(List<IntakeStructuredField> baseline, List<IntakeStructuredField> enriched) {
        List<IntakeStructuredField> safeEnriched = safeFields(enriched);
        return safeEnriched.isEmpty() ? safeFields(baseline) : safeEnriched;
    }

    private List<IntakeStructuredField> safeFields(List<IntakeStructuredField> fields) {
        return fields == null ? List.of() : fields;
    }

    private List<IntakeAttachmentSummary> mergeAttachmentSummaries(List<IntakeAttachmentSummary> baseline,
                                                                   List<IntakeAttachmentSummary> enriched) {
        List<IntakeAttachmentSummary> safeEnriched = enriched == null ? List.of() : enriched;
        return safeEnriched.isEmpty() ? (baseline == null ? List.of() : baseline) : safeEnriched;
    }

    private void replaceStructuredFields(Long intakeId, IntakeStructuredData structuredData) {
        intakeStructuredFieldMapper.deleteByIntakeId(intakeId);
        List<IntakeStructuredFieldEntity> fields = intakeStructuredFieldNormalizer.toFieldEntities(intakeId, structuredData);
        if (!fields.isEmpty()) {
            intakeStructuredFieldMapper.upsertBatch(fields);
        }
    }

    private IntakeStructuredData toStructuredData(IntakeRecordEntity entity, List<IntakeStructuredFieldEntity> fieldEntities) {
        IntakeStructuredData legacyPayload = readStructuredData(entity.getStructuredDataJson());
        if (!hasStructuredFormalFields(entity)) {
            return legacyPayload;
        }
        return new IntakeStructuredData(
                legacyPayload == null ? null : legacyPayload.category(),
                firstNonBlank(entity.getApprovalTitle(), legacyPayload == null ? null : legacyPayload.approvalTitle()),
                firstNonBlank(entity.getProposerName(), legacyPayload == null ? null : legacyPayload.proposerName()),
                firstNonBlank(entity.getDevelopmentOwnerUserName(), legacyPayload == null ? null : legacyPayload.developmentOwnerUserName()),
                firstNonBlank(entity.getApprovalCode(), legacyPayload == null ? null : legacyPayload.approvalCode()),
                firstNonBlank(formatDateTime(entity.getSubmittedAt()), legacyPayload == null ? null : legacyPayload.submittedTime()),
                firstNonBlank(entity.getRequirementType(), legacyPayload == null ? null : legacyPayload.requirementType()),
                firstNonBlank(entity.getDevelopmentBranchName(), legacyPayload == null ? null : legacyPayload.developmentBranchName()),
                firstNonBlank(entity.getZentaoUrl(), legacyPayload == null ? null : legacyPayload.zentaoUrl()),
                firstNonBlank(legacyPayload == null ? null : legacyPayload.requirementDigest(), entity.getRequirementDigest()),
                firstNonBlank(legacyPayload == null ? null : legacyPayload.requirementName(), entity.getRequirementName()),
                firstNonBlank(legacyPayload == null ? null : legacyPayload.requirementSummary(), entity.getRequirementSummary()),
                firstNonBlank(entity.getDepartment(), legacyPayload == null ? null : legacyPayload.department()),
                firstNonBlank(entity.getBusinessLine(), legacyPayload == null ? null : legacyPayload.businessLine()),
                firstNonBlank(entity.getBusinessLineCode(), legacyPayload == null ? null : legacyPayload.businessLineCode()),
                firstNonBlank(entity.getRemark(), legacyPayload == null ? null : legacyPayload.remark()),
                firstNonBlank(formatDate(entity.getPlannedDueDate()), legacyPayload == null ? null : legacyPayload.plannedDueDate()),
                firstNonBlank(formatDate(entity.getPlannedDevelopmentStartDate()), legacyPayload == null ? null : legacyPayload.plannedDevelopmentStartDate()),
                firstNonBlank(formatDate(entity.getPlannedTestingStartDate()), legacyPayload == null ? null : legacyPayload.plannedTestingStartDate()),
                firstNonBlank(formatDate(entity.getPlannedReleaseDate()), legacyPayload == null ? null : legacyPayload.plannedReleaseDate()),
                firstNonBlank(formatDate(entity.getDevelopmentStartedDate()), legacyPayload == null ? null : legacyPayload.developmentStartedDate()),
                firstNonBlank(entity.getActualEffort(), legacyPayload == null ? null : legacyPayload.actualEffort()),
                firstNonBlank(formatDate(entity.getTestingStartedDate()), legacyPayload == null ? null : legacyPayload.testingStartedDate()),
                firstNonBlank(formatDate(entity.getActualCompletedDate()), legacyPayload == null ? null : legacyPayload.actualCompletedTime()),
                firstNonBlank(formatDate(entity.getScheduledAcceptanceDate()), legacyPayload == null ? null : legacyPayload.scheduledAcceptanceDate()),
                firstNonBlank(entity.getActualTestingEffort(), legacyPayload == null ? null : legacyPayload.actualTestingEffort()),
                firstNonBlank(formatDate(entity.getActualTestingCompletedDate()), legacyPayload == null ? null : legacyPayload.actualTestingCompletedDate()),
                firstNonBlank(formatDate(entity.getAcceptanceDate()), legacyPayload == null ? null : legacyPayload.acceptanceTime()),
                firstNonBlank(formatDate(entity.getReleasedDate()), legacyPayload == null ? null : legacyPayload.releasedTime()),
                firstNonBlank(formatDate(entity.getClosedDate()), legacyPayload == null ? null : legacyPayload.closedTime()),
                firstNonBlank(entity.getCloseReason(), legacyPayload == null ? null : legacyPayload.closeReason()),
                firstNonBlank(entity.getProjectHint(), legacyPayload == null ? null : legacyPayload.projectHint()),
                mergeStructuredFields(fieldEntities, legacyPayload),
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

    private List<IntakeStructuredField> toStructuredFields(List<IntakeStructuredFieldEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(entity -> new IntakeStructuredField(entity.getFieldLabel(), entity.getFieldValue()))
                .toList();
    }

    private List<IntakeStructuredField> mergeStructuredFields(List<IntakeStructuredFieldEntity> entities,
                                                              IntakeStructuredData legacyPayload) {
        List<IntakeStructuredField> formalFields = toStructuredFields(entities);
        List<IntakeStructuredField> legacyFields = legacyPayload == null || legacyPayload.fields() == null
                ? List.of()
                : legacyPayload.fields();
        if (!legacyFields.isEmpty()) {
            return legacyFields;
        }
        return formalFields;
    }

    private String formatDate(LocalDate value) {
        return value == null ? null : value.toString().replace('-', '/');
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
    }

    private IntakeRecordEntity requireExisting(Long intakeId) {
        IntakeRecordEntity entity = intakeMapper.findById(intakeId);
        if (entity == null) {
            throw new IllegalArgumentException("待整理记录不存在");
        }
        return entity;
    }

    private IntakeStructuredData readStructuredData(String structuredDataJson) {
        if (structuredDataJson == null || structuredDataJson.isBlank()) {
            return null;
        }
        try {
            return EffortUnitNormalizer.normalizeStructuredData(objectMapper.readValue(structuredDataJson, IntakeStructuredData.class));
        } catch (JacksonException ex) {
            throw new IllegalStateException("结构化需求解析失败", ex);
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

    private String buildAttachmentSummarySource(List<IntakeAttachmentSummary> attachmentSummaries) {
        if (attachmentSummaries == null || attachmentSummaries.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (IntakeAttachmentSummary summary : attachmentSummaries) {
            if (summary == null || isBlank(summary.summaryText())) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("附件文件：").append(summary.fileName()).append('\n')
                    .append("文件类型：").append(summary.fileType()).append('\n')
                    .append(summary.summaryText());
        }
        return builder.toString();
    }

    private String summarizeWarnings(List<String> warnings) {
        List<String> filtered = warnings.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }
        String joined = String.join("；", filtered);
        return joined.length() <= 255 ? joined : joined.substring(0, 255);
    }

    private String summarizeException(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 255 ? message : message.substring(0, 255);
    }

    private String firstNonBlank(String preferred, String fallback) {
        String preferredValue = trimToNull(preferred);
        return preferredValue != null ? preferredValue : trimToNull(fallback);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
