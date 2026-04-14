package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.attachment.AttachmentResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.model.intake.IntakeAIDraft;
import cn.aslight.workhub.model.intake.IntakeCreateRequest;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeHistoryResponse;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.model.intake.IntakeUploadRequest;
import cn.aslight.workhub.model.intake.WecomCallbackRequest;
import cn.aslight.workhub.model.intake.IntakeZentaoLinkRequest;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 待整理箱业务服务。
 */
@Service
public class IntakeService {

    private final IntakeMapper intakeMapper;
    private final IntakeHistoryMapper intakeHistoryMapper;
    private final AttachmentService attachmentService;
    private final IntakeStructuredDataExtractor intakeStructuredDataExtractor;
    private final IntakeEnrichmentService intakeEnrichmentService;
    private final ObjectMapper objectMapper;

    public IntakeService(IntakeMapper intakeMapper,
                         IntakeHistoryMapper intakeHistoryMapper,
                         AttachmentService attachmentService,
                         IntakeStructuredDataExtractor intakeStructuredDataExtractor,
                         IntakeEnrichmentService intakeEnrichmentService,
                         ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.intakeHistoryMapper = intakeHistoryMapper;
        this.attachmentService = attachmentService;
        this.intakeStructuredDataExtractor = intakeStructuredDataExtractor;
        this.intakeEnrichmentService = intakeEnrichmentService;
        this.objectMapper = objectMapper;
    }

    public List<IntakeSummaryResponse> list(String status,
                                            String sourceType,
                                            String keyword,
                                            String demandStatus,
                                            String enrichmentStatus) {
        String normalizedKeyword = trimToNull(keyword);
        String normalizedDemandStatus = trimToNull(demandStatus);
        return intakeMapper.findAll(trimToNull(status),
                        trimToNull(sourceType),
                        trimToNull(keyword),
                        trimToNull(enrichmentStatus)).stream()
                .map(this::toSummaryResponse)
                .filter(item -> matchesKeyword(item, normalizedKeyword))
                .filter(item -> normalizedDemandStatus == null || normalizedDemandStatus.equals(item.demandStatus()))
                .toList();
    }

    public IntakeDetailResponse detail(Long id) {
        return detail(id, null, false);
    }

    public IntakeDetailResponse detail(Long id, String operatorUserName, boolean recordView) {
        IntakeRecordEntity entity = requireExisting(id);
        return toDetailResponse(entity);
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
        intakeCreateRequest.setSourceChannel(defaultChannel(request.getSourceChannel(), "需求截图录入"));
        intakeCreateRequest.setReceivedAt(request.getReceivedAt());
        intakeCreateRequest.setRawContent(buildUploadRawContent(request.getRawContent(), screenshots, attachments));

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
        IntakeStructuredData currentStructuredData = readStructuredData(entity.getStructuredDataJson());
        String currentDemandStatus = IntakeDemandStatusRules.resolve(entity, currentStructuredData);
        if (currentDemandStatus == null) {
            throw new IllegalArgumentException("需求尚未识别成功，暂不能推进业务阶段");
        }
        String action = IntakeDemandStatusRules.normalizeAction(request.getAction());
        String nextDemandStatus = IntakeDemandStatusRules.resolveNextStatus(
                currentDemandStatus,
                action,
                currentStructuredData == null ? null : currentStructuredData.requirementType()
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
        intakeMapper.updateManagementFields(id, writeStructuredDataJson(nextStructuredData), nextDemandStatus);
        if (historyDetail != null) {
            recordHistory(id, "UPDATE", resolveStageActionSummary(action), historyDetail, operatorUserName);
        }
        return detail(id, null, false);
    }

    @Transactional
    public IntakeDetailResponse updateZentaoLink(Long id,
                                                 IntakeZentaoLinkRequest request,
                                                 String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        IntakeStructuredData currentStructuredData = readStructuredData(entity.getStructuredDataJson());
        IntakeStructuredData nextStructuredData = mergeZentaoLink(currentStructuredData, request);
        intakeMapper.updateStructuredData(id, writeStructuredDataJson(nextStructuredData));
        String historyDetail = buildZentaoLinkHistory(currentStructuredData, nextStructuredData);
        if (historyDetail != null) {
            recordHistory(id, "UPDATE", "关联禅道地址", historyDetail, operatorUserName);
        }
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
        entity.setStructuredDataJson(writeStructuredDataJson(intakeStructuredDataExtractor.extract(entity.getRawContent())));
        entity.setAiDraftJson(null);
        entity.setIntakeStatus(IntakeStatusRules.PENDING);
        entity.setDemandStatus(null);
        entity.setEnrichmentStatus(null);
        entity.setEnrichmentErrorSummary(null);
        entity.setEnrichmentUpdatedAt(null);
        entity.setConvertedWorkItemId(null);
        intakeMapper.insert(entity);
        return detail(entity.getId());
    }

    private IntakeRecordEntity requireExisting(Long id) {
        IntakeRecordEntity entity = intakeMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("待整理记录不存在");
        }
        return entity;
    }

    private IntakeDetailResponse toDetailResponse(IntakeRecordEntity entity) {
        List<AttachmentResponse> attachments = attachmentService.listIntakeAttachments(entity.getId());
        IntakeStructuredData structuredData = readStructuredData(entity.getStructuredDataJson());
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
                entity.getReceivedAt(),
                IntakeDemandStatusRules.resolve(entity, structuredData),
                entity.getRawContent(),
                structuredData,
                histories,
                entity.getIntakeStatus(),
                entity.getEnrichmentStatus(),
                entity.getEnrichmentErrorSummary(),
                entity.getEnrichmentUpdatedAt(),
                readDraft(entity.getAiDraftJson()),
                attachments,
                entity.getConvertedWorkItemId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private IntakeSummaryResponse toSummaryResponse(IntakeRecordEntity entity) {
        IntakeStructuredData structuredData = tryReadStructuredData(entity.getStructuredDataJson());
        if (structuredData == null) {
            structuredData = intakeStructuredDataExtractor.extract(entity.getRawContent());
        }
        return new IntakeSummaryResponse(
                entity.getId(),
                entity.getSourceType(),
                entity.getSourceChannel(),
                entity.getSenderName(),
                entity.getReceivedAt(),
                IntakeDemandStatusRules.resolve(entity, structuredData),
                structuredData == null ? null : structuredData.proposerName(),
                structuredData == null ? null : structuredData.approvalCode(),
                structuredData == null ? null : structuredData.submittedTime(),
                structuredData == null ? null : structuredData.requirementType(),
                structuredData == null ? null : structuredData.requirementDigest(),
                structuredData == null ? null : structuredData.department(),
                structuredData == null ? null : structuredData.requirementName(),
                structuredData == null ? null : structuredData.requirementSummary(),
                structuredData == null ? null : structuredData.businessLine(),
                structuredData == null ? null : structuredData.remark(),
                structuredData == null ? null : structuredData.estimatedEffort(),
                structuredData == null ? null : structuredData.plannedDueDate(),
                structuredData == null ? null : structuredData.actualEffort(),
                structuredData == null ? null : structuredData.actualCompletedTime(),
                structuredData == null ? null : structuredData.acceptanceTime(),
                structuredData == null ? null : structuredData.releasedTime(),
                entity.getEnrichmentStatus(),
                entity.getIntakeStatus(),
                entity.getConvertedWorkItemId()
        );
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
                                         List<MultipartFile> screenshots,
                                         List<MultipartFile> attachments) {
        StringBuilder builder = new StringBuilder();
        String content = trimToNull(rawContent);
        if (content != null) {
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

    private IntakeAIDraft readDraft(String aiDraftJson) {
        if (aiDraftJson == null || aiDraftJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(aiDraftJson, IntakeAIDraft.class);
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
            return objectMapper.writeValueAsString(draft);
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
        String requirementType = normalizeRequirementType(structuredData.requirementType());
        String proposerName = normalizeProposerName(structuredData.proposerName(), structuredData.approvalTitle());
        String developmentOwnerUserName = trimToNull(structuredData.developmentOwnerUserName());
        String developmentBranchName = normalizeDevelopmentBranchName(
                structuredData.developmentBranchName(),
                requirementType,
                structuredData.approvalCode(),
                structuredData.submittedTime()
        );
        String zentaoUrl = trimToNull(structuredData.zentaoUrl());
        if (java.util.Objects.equals(requirementType, structuredData.requirementType())
                && java.util.Objects.equals(proposerName, structuredData.proposerName())
                && java.util.Objects.equals(developmentOwnerUserName, structuredData.developmentOwnerUserName())
                && java.util.Objects.equals(developmentBranchName, structuredData.developmentBranchName())
                && java.util.Objects.equals(zentaoUrl, structuredData.zentaoUrl())) {
            return structuredData;
        }
        return new IntakeStructuredData(
                structuredData.category(),
                structuredData.approvalTitle(),
                proposerName,
                developmentOwnerUserName,
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
                structuredData.fields(),
                structuredData.attachmentSummaries()
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
                                                  String submittedTime) {
        String normalized = trimToNull(developmentBranchName);
        if (normalized != null) {
            return normalized;
        }
        if (!"研发需求".equals(trimToNull(requirementType))) {
            return null;
        }
        String suffix = sanitizeBranchToken(approvalCode);
        if (suffix == null) {
            suffix = sanitizeBranchToken(submittedTime);
        }
        if (suffix == null) {
            return "feature/req-generated";
        }
        return "feature/req-" + suffix;
    }

    private String sanitizeBranchToken(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String sanitized = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        sanitized = sanitized.replaceAll("^-+", "").replaceAll("-+$", "");
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
                    List.of(),
                    List.of()
            );
        }
        String estimatedEffort = baseline.estimatedEffort();
        String plannedDueDate = baseline.plannedDueDate();
        String developmentOwnerUserName = baseline.developmentOwnerUserName();
        String developmentStartedDate = baseline.developmentStartedDate();
        String actualEffort = baseline.actualEffort();
        String testingStartedDate = baseline.testingStartedDate();
        String actualCompletedTime = baseline.actualCompletedTime();
        String acceptanceTime = baseline.acceptanceTime();
        String releasedTime = baseline.releasedTime();

        switch (action) {
            case IntakeDemandStatusRules.ACTION_EVALUATE_EFFORT -> {
                estimatedEffort = requireValue(request.getEstimatedEffort(), "预估工时不能为空");
                plannedDueDate = requireValue(request.getPlannedDueDate(), "预估完成时间不能为空");
            }
            case IntakeDemandStatusRules.ACTION_START_DEVELOPMENT -> {
                developmentOwnerUserName = requireValue(request.getDevelopmentOwnerUserName(), "研发人员不能为空");
                developmentStartedDate = resolveActionDate(request.getOccurredAt());
            }
            case IntakeDemandStatusRules.ACTION_SUBMIT_TESTING -> {
                actualEffort = requireValue(request.getActualEffort(), "实际工时不能为空");
                actualCompletedTime = firstNonBlank(request.getActualCompletedTime(), resolveActionDate(request.getOccurredAt()));
            }
            case IntakeDemandStatusRules.ACTION_START_TESTING ->
                    testingStartedDate = resolveActionDate(request.getOccurredAt());
            case IntakeDemandStatusRules.ACTION_SUBMIT_ACCEPTANCE -> {
                // 提交验收只推进阶段，不额外覆盖字段。
            }
            case IntakeDemandStatusRules.ACTION_CONFIRM_ACCEPTANCE ->
                    acceptanceTime = firstNonBlank(request.getAcceptanceTime(), resolveActionDate(request.getOccurredAt()));
            case IntakeDemandStatusRules.ACTION_CONFIRM_RELEASE ->
                    releasedTime = resolveActionDate(request.getOccurredAt());
            case IntakeDemandStatusRules.ACTION_COMPLETE_DELIVERY -> {
                actualEffort = firstNonBlank(request.getActualEffort(), actualEffort);
                actualCompletedTime = firstNonBlank(request.getActualCompletedTime(), resolveActionDate(request.getOccurredAt()));
                releasedTime = resolveActionDate(request.getOccurredAt());
            }
            default -> throw new IllegalArgumentException("不支持的阶段动作");
        }

        return new IntakeStructuredData(
                baseline.category(),
                baseline.approvalTitle(),
                baseline.proposerName(),
                developmentOwnerUserName,
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
                estimatedEffort,
                plannedDueDate,
                developmentStartedDate,
                actualEffort,
                testingStartedDate,
                actualCompletedTime,
                acceptanceTime,
                releasedTime,
                baseline.projectHint(),
                baseline.fields() == null ? List.of() : baseline.fields(),
                baseline.attachmentSummaries() == null ? List.of() : baseline.attachmentSummaries()
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
                baseline.remark(),
                baseline.estimatedEffort(),
                baseline.plannedDueDate(),
                baseline.developmentStartedDate(),
                baseline.actualEffort(),
                baseline.testingStartedDate(),
                baseline.actualCompletedTime(),
                baseline.acceptanceTime(),
                baseline.releasedTime(),
                baseline.projectHint(),
                baseline.fields() == null ? List.of() : baseline.fields(),
                baseline.attachmentSummaries() == null ? List.of() : baseline.attachmentSummaries()
        );
    }

    private String resolveActionDate(String preferredDate) {
        return firstNonBlank(preferredDate, LocalDate.now().toString().replace('-', '/'));
    }

    private String requireValue(String value, String errorMessage) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String buildStageActionHistory(IntakeStructuredData before,
                                           IntakeStructuredData after,
                                           String beforeDemandStatus,
                                           String afterDemandStatus,
                                           List<String> uploadedFileNames) {
        List<String> changes = new java.util.ArrayList<>();
        appendChange(changes, "需求状态", beforeDemandStatus, afterDemandStatus);
        appendChange(changes, "预估工时", before == null ? null : before.estimatedEffort(), after == null ? null : after.estimatedEffort());
        appendChange(changes, "预估完成时间", before == null ? null : before.plannedDueDate(), after == null ? null : after.plannedDueDate());
        appendChange(changes, "研发人员", before == null ? null : before.developmentOwnerUserName(), after == null ? null : after.developmentOwnerUserName());
        appendChange(changes, "研发开始日期", before == null ? null : before.developmentStartedDate(), after == null ? null : after.developmentStartedDate());
        appendChange(changes, "实际工时", before == null ? null : before.actualEffort(), after == null ? null : after.actualEffort());
        appendChange(changes, "测试开始日期", before == null ? null : before.testingStartedDate(), after == null ? null : after.testingStartedDate());
        appendChange(changes, "实际完成时间", before == null ? null : before.actualCompletedTime(), after == null ? null : after.actualCompletedTime());
        appendChange(changes, "验收时间", before == null ? null : before.acceptanceTime(), after == null ? null : after.acceptanceTime());
        appendChange(changes, "上线时间", before == null ? null : before.releasedTime(), after == null ? null : after.releasedTime());
        appendUploadedFiles(changes, uploadedFileNames);
        if (changes.isEmpty()) {
            return null;
        }
        return String.join("\n", changes);
    }

    private String buildZentaoLinkHistory(IntakeStructuredData before, IntakeStructuredData after) {
        List<String> changes = new java.util.ArrayList<>();
        appendChange(changes, "禅道地址", before == null ? null : before.zentaoUrl(), after == null ? null : after.zentaoUrl());
        if (changes.isEmpty()) {
            return null;
        }
        return String.join("\n", changes);
    }

    private String resolveStageActionSummary(String action) {
        return switch (action) {
            case IntakeDemandStatusRules.ACTION_EVALUATE_EFFORT -> "评估工时";
            case IntakeDemandStatusRules.ACTION_START_DEVELOPMENT -> "开始研发";
            case IntakeDemandStatusRules.ACTION_SUBMIT_TESTING -> "提交测试";
            case IntakeDemandStatusRules.ACTION_START_TESTING -> "开始测试";
            case IntakeDemandStatusRules.ACTION_SUBMIT_ACCEPTANCE -> "提交验收";
            case IntakeDemandStatusRules.ACTION_CONFIRM_ACCEPTANCE -> "确认验收";
            case IntakeDemandStatusRules.ACTION_CONFIRM_RELEASE -> "确认上线";
            case IntakeDemandStatusRules.ACTION_COMPLETE_DELIVERY -> "确认完成";
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
        if (!IntakeDemandStatusRules.ACTION_COMPLETE_DELIVERY.equals(action) || dataFiles == null || dataFiles.isEmpty()) {
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

    private boolean matchesKeyword(IntakeSummaryResponse item, String keyword) {
        if (keyword == null) {
            return true;
        }
        return contains(item.senderName(), keyword)
                || contains(item.approvalCode(), keyword)
                || contains(item.proposerName(), keyword)
                || contains(item.requirementType(), keyword)
                || contains(item.requirementDigest(), keyword)
                || contains(item.requirementName(), keyword)
                || contains(item.requirementSummary(), keyword)
                || contains(item.department(), keyword)
                || contains(item.businessLine(), keyword)
                || contains(item.remark(), keyword)
                || contains(item.sourceChannel(), keyword)
                || contains(item.sourceType(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.contains(keyword);
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
}
