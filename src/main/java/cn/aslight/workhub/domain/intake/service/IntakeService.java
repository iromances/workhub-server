package cn.aslight.workhub.domain.intake.service;

import cn.aslight.workhub.domain.attachment.dto.AttachmentResponse;
import cn.aslight.workhub.domain.attachment.service.AttachmentService;
import cn.aslight.workhub.domain.intake.dto.IntakeAIDraft;
import cn.aslight.workhub.domain.intake.dto.IntakeConvertRequest;
import cn.aslight.workhub.domain.intake.dto.IntakeConvertResponse;
import cn.aslight.workhub.domain.intake.dto.IntakeCreateRequest;
import cn.aslight.workhub.domain.intake.dto.IntakeDetailResponse;
import cn.aslight.workhub.domain.intake.dto.IntakeStructuredData;
import cn.aslight.workhub.domain.intake.dto.IntakeTaskBreakdownItem;
import cn.aslight.workhub.domain.intake.dto.IntakeSummaryResponse;
import cn.aslight.workhub.domain.intake.dto.IntakeUploadRequest;
import cn.aslight.workhub.domain.intake.dto.WecomCallbackRequest;
import cn.aslight.workhub.domain.intake.mapper.IntakeMapper;
import cn.aslight.workhub.domain.intake.model.IntakeRecordEntity;
import cn.aslight.workhub.domain.workitem.dto.WorkItemCreateRequest;
import cn.aslight.workhub.domain.workitem.dto.WorkItemDetailResponse;
import cn.aslight.workhub.domain.workitem.service.WorkItemFollowUpService;
import cn.aslight.workhub.domain.workitem.service.WorkItemService;
import cn.aslight.workhub.integration.ai.AiDraftGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IntakeService {

    private final IntakeMapper intakeMapper;
    private final WorkItemService workItemService;
    private final AiDraftGenerator aiDraftGenerator;
    private final AttachmentService attachmentService;
    private final WorkItemFollowUpService workItemFollowUpService;
    private final IntakeStructuredDataExtractor intakeStructuredDataExtractor;
    private final IntakeEnrichmentService intakeEnrichmentService;
    private final ObjectMapper objectMapper;

    public IntakeService(IntakeMapper intakeMapper,
                         WorkItemService workItemService,
                         AiDraftGenerator aiDraftGenerator,
                         AttachmentService attachmentService,
                         WorkItemFollowUpService workItemFollowUpService,
                         IntakeStructuredDataExtractor intakeStructuredDataExtractor,
                         IntakeEnrichmentService intakeEnrichmentService,
                         ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.workItemService = workItemService;
        this.aiDraftGenerator = aiDraftGenerator;
        this.attachmentService = attachmentService;
        this.workItemFollowUpService = workItemFollowUpService;
        this.intakeStructuredDataExtractor = intakeStructuredDataExtractor;
        this.intakeEnrichmentService = intakeEnrichmentService;
        this.objectMapper = objectMapper;
    }

    public List<IntakeSummaryResponse> list(String status, String sourceType, String keyword) {
        return intakeMapper.findAll(trimToNull(status), trimToNull(sourceType), trimToNull(keyword));
    }

    public IntakeDetailResponse detail(Long id) {
        return toDetailResponse(requireExisting(id));
    }

    @Transactional
    public IntakeDetailResponse createManual(IntakeCreateRequest request) {
        return createIntakeRecord("人工录入", defaultChannel(request.getSourceChannel(), "人工录入"), null, request);
    }

    @Transactional
    public IntakeDetailResponse createPasted(IntakeCreateRequest request) {
        return createIntakeRecord("人工粘贴", defaultChannel(request.getSourceChannel(), "文本粘贴"), null, request);
    }

    @Transactional
    public IntakeDetailResponse createUploaded(IntakeUploadRequest request,
                                               List<MultipartFile> screenshots,
                                               List<MultipartFile> attachments) {
        if (!hasAnyContent(request, screenshots, attachments)) {
            throw new IllegalArgumentException("原始内容、截图和附件不能同时为空");
        }
        IntakeCreateRequest intakeCreateRequest = new IntakeCreateRequest();
        intakeCreateRequest.setSenderName(request.getSenderName());
        intakeCreateRequest.setSourceChannel(defaultChannel(request.getSourceChannel(), "人工上传"));
        intakeCreateRequest.setReceivedAt(request.getReceivedAt());
        intakeCreateRequest.setRawContent(buildUploadRawContent(request.getRawContent(), screenshots, attachments));

        IntakeDetailResponse detail = createIntakeRecord("截图附件录入",
                intakeCreateRequest.getSourceChannel(),
                null,
                intakeCreateRequest);
        attachmentService.saveIntakeFiles(detail.id(), screenshots, attachments);
        intakeEnrichmentService.scheduleUploadedEnrichment(detail.id(),
                intakeCreateRequest.getSourceChannel(),
                intakeCreateRequest.getRawContent());
        return detail(detail.id());
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
    public IntakeDetailResponse generateAiDraft(Long id, String provider) {
        IntakeRecordEntity entity = requireExisting(id);
        if (entity.getConvertedWorkItemId() != null) {
            throw new IllegalArgumentException("待整理记录已转正式工作项");
        }

        IntakeAIDraft draft = aiDraftGenerator.generate(
                entity.getRawContent(),
                entity.getSourceType(),
                provider,
                readStructuredData(entity.getStructuredDataJson())
        );
        intakeMapper.updateAiDraft(id, writeDraftJson(draft), IntakeStatusRules.AI_DRAFTED);
        return detail(id);
    }

    @Transactional
    public IntakeConvertResponse convertToWorkItem(Long id,
                                                   IntakeConvertRequest request,
                                                   String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        if (entity.getConvertedWorkItemId() != null) {
            throw new IllegalArgumentException("待整理记录已转正式工作项");
        }

        IntakeAIDraft draft = readDraft(entity.getAiDraftJson());
        WorkItemCreateRequest workItemRequest = toWorkItemCreateRequest(entity, request, draft);
        WorkItemDetailResponse workItem = workItemService.create(workItemRequest, operatorUserName);
        writeTaskBreakdownNotes(workItem.id(), draft, operatorUserName);
        intakeMapper.markConverted(id, IntakeStatusRules.CONVERTED, workItem.id());

        return new IntakeConvertResponse(detail(id), workItem);
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
        return new IntakeDetailResponse(
                entity.getId(),
                entity.getSourceType(),
                entity.getSourceChannel(),
                entity.getExternalMessageId(),
                entity.getSenderName(),
                entity.getReceivedAt(),
                entity.getRawContent(),
                readStructuredData(entity.getStructuredDataJson()),
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
        appendFileSection(builder, "截图", screenshots);
        appendFileSection(builder, "附件", attachments);
        return builder.toString().trim();
    }

    private void appendFileSection(StringBuilder builder, String title, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
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

    private WorkItemCreateRequest toWorkItemCreateRequest(IntakeRecordEntity entity,
                                                          IntakeConvertRequest request,
                                                          IntakeAIDraft draft) {
        WorkItemCreateRequest workItemRequest = new WorkItemCreateRequest();
        workItemRequest.setProjectId(request.getProjectId());
        workItemRequest.setSprintId(request.getSprintId());
        workItemRequest.setReleaseId(request.getReleaseId());
        workItemRequest.setType(firstNonBlank(request.getType(), draft == null ? null : draft.typeSuggestion()));
        workItemRequest.setTitle(firstNonBlank(request.getTitle(), draft == null ? null : draft.titleSuggestion()));
        workItemRequest.setDescription(firstNonBlank(request.getDescription(),
                draft == null ? entity.getRawContent() : draft.descriptionSuggestion()));
        workItemRequest.setSourceType(entity.getSourceType());
        workItemRequest.setSourceChannel(entity.getSourceChannel());
        workItemRequest.setPriority(firstNonBlank(request.getPriority(), draft == null ? null : draft.prioritySuggestion()));
        workItemRequest.setUrgency(trimToNull(request.getUrgency()));
        workItemRequest.setOwnerUserName(request.getOwnerUserName());
        workItemRequest.setFollowerUserName(request.getFollowerUserName());
        workItemRequest.setProposerName(firstNonBlank(request.getProposerName(), entity.getSenderName()));
        workItemRequest.setAcceptanceCriteria(firstNonBlank(request.getAcceptanceCriteria(),
                draft == null ? null : draft.acceptanceCriteriaSuggestion()));
        workItemRequest.setPlannedStartAt(request.getPlannedStartAt());
        workItemRequest.setPlannedEndAt(request.getPlannedEndAt());
        return workItemRequest;
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
            return objectMapper.readValue(structuredDataJson, IntakeStructuredData.class);
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
            return objectMapper.writeValueAsString(structuredData);
        } catch (JacksonException ex) {
            throw new IllegalStateException("结构化需求写入失败", ex);
        }
    }

    private void writeTaskBreakdownNotes(Long workItemId, IntakeAIDraft draft, String operatorUserName) {
        if (draft == null || draft.taskBreakdownSuggestions() == null || draft.taskBreakdownSuggestions().isEmpty()) {
            return;
        }
        StringBuilder content = new StringBuilder("AI 拆解建议：");
        int index = 1;
        for (IntakeTaskBreakdownItem item : draft.taskBreakdownSuggestions()) {
            if (item == null || trimToNull(item.taskName()) == null) {
                continue;
            }
            content.append("\n")
                    .append(index++)
                    .append(". ")
                    .append(item.taskName().trim())
                    .append(" | 工时: ").append(defaultValue(item.estimatedEffort()))
                    .append(" | 开发负责人: ").append(defaultValue(item.ownerUserName()))
                    .append(" | 状态: ").append(defaultValue(item.status()));
            String notes = trimToNull(item.notes());
            if (notes != null) {
                content.append(" | 说明: ").append(notes);
            }
        }
        if (index > 1) {
            workItemFollowUpService.addSystemNote(workItemId, content.toString(), operatorUserName);
        }
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
