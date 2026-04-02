package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.attachment.AttachmentTextExtractionService;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
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
    private final AttachmentService attachmentService;
    private final AttachmentTextExtractionService attachmentTextExtractionService;
    private final IntakeStructuredDataExtractor intakeStructuredDataExtractor;
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
        this.intakeMapper = intakeMapper;
        this.attachmentService = attachmentService;
        this.attachmentTextExtractionService = attachmentTextExtractionService;
        this.intakeStructuredDataExtractor = intakeStructuredDataExtractor;
        this.codexCliStructuredExtractor = codexCliStructuredExtractor;
        this.workhubTaskExecutor = workhubTaskExecutor;
        this.objectMapper = objectMapper;
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
                IntakeDemandStatusRules.resolve(existing, readStructuredData(existing.getStructuredDataJson())),
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

            IntakeStructuredData baseline = readStructuredData(entity.getStructuredDataJson());
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

            boolean shouldMarkFailed = codexResult.failureSummary() != null && !hasMeaningfulStructuredData(merged);
            String nextStatus = shouldMarkFailed ? IntakeEnrichmentStatus.FAILED : IntakeEnrichmentStatus.SUCCEEDED;
            String warningSummary = summarizeWarnings(warnings);
            String nextDemandStatus = IntakeDemandStatusRules.resolveAfterEnrichment(entity.getDemandStatus(), nextStatus, merged);

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
                    IntakeDemandStatusRules.resolveAfterEnrichment(failedEntity.getDemandStatus(), IntakeEnrichmentStatus.FAILED, readStructuredData(failedEntity.getStructuredDataJson())),
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
                    List.of(),
                    safeSummaries
            );
        }
        return new IntakeStructuredData(
                structuredData.category(),
                structuredData.approvalTitle(),
                structuredData.proposerName(),
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
                structuredData.remark(),
                structuredData.estimatedEffort(),
                structuredData.plannedDueDate(),
                structuredData.developmentStartedDate(),
                structuredData.actualEffort(),
                structuredData.testingStartedDate(),
                structuredData.actualCompletedTime(),
                structuredData.acceptanceTime(),
                structuredData.releasedTime(),
                structuredData.projectHint(),
                safeFields(structuredData.fields()),
                safeSummaries
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
                firstNonBlank(enriched.remark(), baseline.remark()),
                firstNonBlank(enriched.estimatedEffort(), baseline.estimatedEffort()),
                firstNonBlank(enriched.plannedDueDate(), baseline.plannedDueDate()),
                firstNonBlank(enriched.developmentStartedDate(), baseline.developmentStartedDate()),
                firstNonBlank(enriched.actualEffort(), baseline.actualEffort()),
                firstNonBlank(enriched.testingStartedDate(), baseline.testingStartedDate()),
                firstNonBlank(enriched.actualCompletedTime(), baseline.actualCompletedTime()),
                firstNonBlank(enriched.acceptanceTime(), baseline.acceptanceTime()),
                firstNonBlank(enriched.releasedTime(), baseline.releasedTime()),
                firstNonBlank(enriched.projectHint(), baseline.projectHint()),
                mergeFields(baseline.fields(), enriched.fields()),
                mergeAttachmentSummaries(baseline.attachmentSummaries(), enriched.attachmentSummaries())
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
            return objectMapper.readValue(structuredDataJson, IntakeStructuredData.class);
        } catch (JacksonException ex) {
            throw new IllegalStateException("结构化需求解析失败", ex);
        }
    }

    private String writeStructuredDataJson(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(structuredData);
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

    private boolean hasMeaningfulStructuredData(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return false;
        }
        return firstNonBlank(
                structuredData.approvalTitle(),
                firstNonBlank(
                        structuredData.proposerName(),
                        firstNonBlank(
                                structuredData.approvalCode(),
                                firstNonBlank(
                                        structuredData.submittedTime(),
                                        firstNonBlank(
                                                structuredData.requirementType(),
                                                firstNonBlank(
                                                        structuredData.requirementDigest(),
                                                        firstNonBlank(
                                                structuredData.requirementName(),
                                                firstNonBlank(
                                                        structuredData.requirementSummary(),
                                                        firstNonBlank(
                                                                structuredData.department(),
                                                                firstNonBlank(
                                                                        structuredData.businessLine(),
                                                                        firstNonBlank(structuredData.remark(), structuredData.estimatedEffort())
                                                                )
                                                        )
                                                )
                                                ))
                                        )
                                )
                        )
                )
        ) != null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
