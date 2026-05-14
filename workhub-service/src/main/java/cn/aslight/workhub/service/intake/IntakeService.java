package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.attachment.AttachmentResponse;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.model.intake.IntakeAIDraft;
import cn.aslight.workhub.model.intake.IntakeCreateRequest;
import cn.aslight.workhub.model.intake.IntakeDevelopmentBranchRequest;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeHistoryResponse;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSqlDraft;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/**
 * 待整理箱业务服务。
 */
@Service
public class IntakeService {

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
            IntakeDemandStatusRules.PENDING_EVALUATION,
            IntakeDemandStatusRules.PENDING_SCHEDULING,
            IntakeDemandStatusRules.PENDING_DESIGN,
            IntakeDemandStatusRules.IN_DEVELOPMENT,
            IntakeDemandStatusRules.TESTING,
            IntakeDemandStatusRules.PENDING_RELEASE,
            IntakeDemandStatusRules.PENDING_ACCEPTANCE,
            IntakeDemandStatusRules.COMPLETED,
            IntakeDemandStatusRules.TERMINATED
    );

    private final IntakeMapper intakeMapper;
    private final IntakeHistoryMapper intakeHistoryMapper;
    private final AttachmentService attachmentService;
    private final IntakeStructuredDataExtractor intakeStructuredDataExtractor;
    private final IntakeEnrichmentService intakeEnrichmentService;
    private final CodexCliSqlDraftGenerator codexCliSqlDraftGenerator;
    private final ObjectMapper objectMapper;

    public IntakeService(IntakeMapper intakeMapper,
                         IntakeHistoryMapper intakeHistoryMapper,
                         AttachmentService attachmentService,
                         IntakeStructuredDataExtractor intakeStructuredDataExtractor,
                         IntakeEnrichmentService intakeEnrichmentService,
                         CodexCliSqlDraftGenerator codexCliSqlDraftGenerator,
                         ObjectMapper objectMapper) {
        this.intakeMapper = intakeMapper;
        this.intakeHistoryMapper = intakeHistoryMapper;
        this.attachmentService = attachmentService;
        this.intakeStructuredDataExtractor = intakeStructuredDataExtractor;
        this.intakeEnrichmentService = intakeEnrichmentService;
        this.codexCliSqlDraftGenerator = codexCliSqlDraftGenerator;
        this.objectMapper = objectMapper;
    }

    public List<IntakeSummaryResponse> list(String status,
                                            String requirementName,
                                            String approvalCode,
                                            String proposerName,
                                            String demandStatus,
                                            String releasedStartDate,
                                            String releasedEndDate) {
        String normalizedRequirementName = trimToNull(requirementName);
        String normalizedApprovalCode = trimToNull(approvalCode);
        String normalizedProposerName = trimToNull(proposerName);
        String normalizedDemandStatus = trimToNull(demandStatus);
        LocalDate normalizedReleasedStartDate = parseFilterDate(releasedStartDate, "上线开始日期");
        LocalDate normalizedReleasedEndDate = parseFilterDate(releasedEndDate, "上线结束日期");
        return intakeMapper.findAll(trimToNull(status)).stream()
                .map(this::toSummaryResponse)
                .filter(item -> matchesRequirementName(item, normalizedRequirementName))
                .filter(item -> matchesApprovalCode(item, normalizedApprovalCode))
                .filter(item -> matchesProposerName(item, normalizedProposerName))
                .filter(item -> normalizedDemandStatus == null || normalizedDemandStatus.equals(item.demandStatus()))
                .filter(item -> matchesReleasedDate(item, normalizedReleasedStartDate, normalizedReleasedEndDate))
                .sorted(Comparator.comparingInt(this::demandStatusSortOrder)
                        .thenComparing(IntakeSummaryResponse::receivedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(IntakeSummaryResponse::id, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public IntakeDetailResponse detail(Long id) {
        return detail(id, null, false);
    }

    public IntakeDetailResponse detail(Long id, String operatorUserName, boolean recordView) {
        IntakeRecordEntity entity = requireExisting(id);
        return toDetailResponse(entity);
    }

    @Transactional
    public void delete(Long id, String operatorUserName) {
        requireExisting(id);
        intakeMapper.markDeleted(id, LocalDateTime.now(), trimToNull(operatorUserName));
    }

    /**
     * 重新触发识别中或失败需求的结构化识别。
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
        if (!IntakeEnrichmentStatus.RUNNING.equals(enrichmentStatus)
                && !IntakeEnrichmentStatus.FAILED.equals(enrichmentStatus)) {
            throw new IllegalArgumentException("当前需求不是识别中或识别失败状态，不能重新识别");
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
        IntakeStructuredData currentStructuredData = readStructuredData(entity.getStructuredDataJson());
        IntakeStructuredData nextStructuredData = removeAttachmentSummary(currentStructuredData, deleted.fileName());
        if (nextStructuredData != currentStructuredData) {
            intakeMapper.updateStructuredData(id, writeStructuredDataJson(nextStructuredData));
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
        IntakeStructuredData currentStructuredData = readStructuredData(entity.getStructuredDataJson());
        IntakeStructuredData nextStructuredData = removeAttachmentSummary(currentStructuredData, replaced.before().fileName());
        if (nextStructuredData != currentStructuredData) {
            intakeMapper.updateStructuredData(id, writeStructuredDataJson(nextStructuredData));
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
        intakeCreateRequest.setRawContent(buildUploadRawContent(request.getRawContent(), request.getProjectGroup(), screenshots, attachments));

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
        intakeMapper.updateManagementFields(
                id,
                writeStructuredDataJson(nextStructuredData),
                entity.getDevelopmentOwnerUserName(),
                nextDemandStatus
        );
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

    @Transactional
    public IntakeDetailResponse updateDevelopmentBranch(Long id,
                                                        IntakeDevelopmentBranchRequest request,
                                                        String operatorUserName) {
        IntakeRecordEntity entity = requireExisting(id);
        IntakeStructuredData currentStructuredData = readStructuredData(entity.getStructuredDataJson());
        IntakeStructuredData nextStructuredData = mergeDevelopmentBranch(currentStructuredData, request);
        intakeMapper.updateStructuredData(id, writeStructuredDataJson(nextStructuredData));
        String historyDetail = buildDevelopmentBranchHistory(currentStructuredData, nextStructuredData);
        if (historyDetail != null) {
            recordHistory(id, "UPDATE", "更新研发分支", historyDetail, operatorUserName);
        }
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
        IntakeStructuredData currentStructuredData = readStructuredData(entity.getStructuredDataJson());
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
        intakeMapper.updateStructuredData(id, writeStructuredDataJson(nextStructuredData));
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
        entity.setStructuredDataJson(writeStructuredDataJson(intakeStructuredDataExtractor.extract(entity.getRawContent())));
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
                entity.getDevelopmentOwnerUserName(),
                entity.getReceivedAt(),
                IntakeDemandStatusRules.resolve(entity, structuredData),
                entity.getRawContent(),
                structuredData,
                histories,
                entity.getIntakeStatus(),
                IntakeDemandStatusRules.resolveEnrichmentStatus(entity),
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
                entity.getDevelopmentOwnerUserName(),
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
                structuredData == null ? null : structuredData.projectHint(),
                IntakeDemandStatusRules.resolveEnrichmentStatus(entity),
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
                                         String projectGroup,
                                         List<MultipartFile> screenshots,
                                         List<MultipartFile> attachments) {
        StringBuilder builder = new StringBuilder();
        String normalizedProjectGroup = trimToNull(projectGroup);
        if (normalizedProjectGroup != null) {
            builder.append("项目组：").append(normalizedProjectGroup);
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
                structuredData.remark(),
                structuredData.estimatedEffort(),
                structuredData.plannedDueDate(),
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
                structuredData.remark(),
                structuredData.estimatedEffort(),
                structuredData.plannedDueDate(),
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
                    null,
                    List.of(),
                    List.of(),
                    null
            );
        }
        String estimatedEffort = baseline.estimatedEffort();
        String plannedDueDate = baseline.plannedDueDate();
        String developmentStartedDate = baseline.developmentStartedDate();
        String actualEffort = baseline.actualEffort();
        String testingStartedDate = baseline.testingStartedDate();
        String actualCompletedTime = baseline.actualCompletedTime();
        String acceptanceTime = baseline.acceptanceTime();
        String releasedTime = baseline.releasedTime();
        String closedTime = baseline.closedTime();
        String closeReason = baseline.closeReason();

        switch (action) {
            case IntakeDemandStatusRules.ACTION_START_CLARIFICATION, IntakeDemandStatusRules.ACTION_CONFIRM_CLARIFICATION,
                    IntakeDemandStatusRules.ACTION_CONFIRM_SCHEDULING -> {
            }
            case IntakeDemandStatusRules.ACTION_COMPLETE_EVALUATION -> {
                estimatedEffort = requireEffortValue(request.getEstimatedEffort(), "预估工时不能为空");
                plannedDueDate = requireValue(request.getPlannedDueDate(), "预估完成时间不能为空");
            }
            case IntakeDemandStatusRules.ACTION_CONFIRM_DESIGN ->
                    developmentStartedDate = resolveActionDate(request.getOccurredAt());
            case IntakeDemandStatusRules.ACTION_SUBMIT_TESTING -> {
                actualEffort = requireEffortValue(request.getActualEffort(), "实际工时不能为空");
                actualCompletedTime = firstNonBlank(request.getActualCompletedTime(), resolveActionDate(request.getOccurredAt()));
            }
            case IntakeDemandStatusRules.ACTION_PASS_TESTING ->
                    testingStartedDate = resolveActionDate(request.getOccurredAt());
            case IntakeDemandStatusRules.ACTION_CONFIRM_RELEASE ->
                    releasedTime = resolveActionDate(request.getOccurredAt());
            case IntakeDemandStatusRules.ACTION_CONFIRM_ACCEPTANCE ->
                    acceptanceTime = firstNonBlank(request.getAcceptanceTime(), resolveActionDate(request.getOccurredAt()));
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
                baseline.remark(),
                estimatedEffort,
                plannedDueDate,
                developmentStartedDate,
                actualEffort,
                testingStartedDate,
                actualCompletedTime,
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
                baseline.remark(),
                baseline.estimatedEffort(),
                baseline.plannedDueDate(),
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
        return new IntakeStructuredData(
                baseline.category(),
                baseline.approvalTitle(),
                baseline.proposerName(),
                baseline.developmentOwnerUserName(),
                baseline.approvalCode(),
                baseline.submittedTime(),
                baseline.requirementType(),
                trimToNull(request.getDevelopmentBranchName()),
                baseline.zentaoUrl(),
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
                normalized.remark(),
                normalized.estimatedEffort(),
                normalized.plannedDueDate(),
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

    private String requireValue(String value, String errorMessage) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String requireEffortValue(String value, String errorMessage) {
        return EffortUnitNormalizer.normalizeEffort(requireValue(value, errorMessage));
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
        appendChange(changes, "研发开始日期", before == null ? null : before.developmentStartedDate(), after == null ? null : after.developmentStartedDate());
        appendChange(changes, "实际工时", before == null ? null : before.actualEffort(), after == null ? null : after.actualEffort());
        appendChange(changes, "测试开始日期", before == null ? null : before.testingStartedDate(), after == null ? null : after.testingStartedDate());
        appendChange(changes, "实际完成时间", before == null ? null : before.actualCompletedTime(), after == null ? null : after.actualCompletedTime());
        appendChange(changes, "验收时间", before == null ? null : before.acceptanceTime(), after == null ? null : after.acceptanceTime());
        appendChange(changes, "上线时间", before == null ? null : before.releasedTime(), after == null ? null : after.releasedTime());
        appendChange(changes, "关闭时间", before == null ? null : before.closedTime(), after == null ? null : after.closedTime());
        appendChange(changes, "关闭原因", before == null ? null : before.closeReason(), after == null ? null : after.closeReason());
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
            case IntakeDemandStatusRules.ACTION_CONFIRM_CLARIFICATION -> "澄清完成";
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

    private boolean matchesApprovalCode(IntakeSummaryResponse item, String approvalCode) {
        return approvalCode == null || contains(item.approvalCode(), approvalCode);
    }

    private boolean matchesProposerName(IntakeSummaryResponse item, String proposerName) {
        return proposerName == null || contains(item.proposerName(), proposerName);
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
}
