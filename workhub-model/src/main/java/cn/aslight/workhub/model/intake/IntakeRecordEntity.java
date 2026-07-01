package cn.aslight.workhub.model.intake;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待整理需求记录实体。
 */
public class IntakeRecordEntity {

    /**
     * 待整理记录主键 ID。
     */
    private Long id;
    /**
     * 来源类型，例如企业微信回调、需求截图附件录入。
     */
    private String sourceType;
    /**
     * 来源渠道名称。
     */
    private String sourceChannel;
    /**
     * 外部消息唯一标识。
     */
    private String externalMessageId;
    /**
     * 发送人或提交人。
     */
    private String senderName;
    /**
     * 接收或提交时间。
     */
    private LocalDateTime receivedAt;
    /**
     * 原始文本内容。
     */
    private String rawContent;
    /**
     * 研发负责人用户名。
     */
    private String developmentOwnerUserName;
    /**
     * 结构化需求 JSON。
     */
    private String structuredDataJson;
    /**
     * 历史兼容保留的 AI 草稿 JSON。
     */
    private String aiDraftJson;
    /**
     * 待整理状态。
     */
    private String intakeStatus;
    /**
     * 需求管理状态。
     */
    private String demandStatus;
    /**
     * 暂停前需求管理状态。
     */
    private String pausePreviousDemandStatus;
    /**
     * 暂停原因。
     */
    private String pauseReason;
    /**
     * 暂停日期。
     */
    private LocalDate pauseDate;
    /**
     * enrichment 状态。
     */
    private String enrichmentStatus;
    /**
     * enrichment 失败摘要。
     */
    private String enrichmentErrorSummary;
    /**
     * 最近一次 enrichment 更新时间。
     */
    private LocalDateTime enrichmentUpdatedAt;
    /**
     * 已转换的正式工作项 ID。
     */
    private Long convertedWorkItemId;
    /**
     * 审批编号。
     */
    private String approvalCode;
    /**
     * 审批标题。
     */
    private String approvalTitle;
    /**
     * 审批状态。
     */
    private String approvalStatus;
    /**
     * 提出人或申请人。
     */
    private String proposerName;
    /**
     * 提交时间。
     */
    private LocalDateTime submittedAt;
    /**
     * 需求类型。
     */
    private String requirementType;
    /**
     * 需求名称。
     */
    private String requirementName;
    /**
     * 需求说明。
     */
    private String requirementSummary;
    /**
     * 需求摘要。
     */
    private String requirementDigest;
    /**
     * 提出部门。
     */
    private String department;
    /**
     * 业务线名称。
     */
    private String businessLine;
    /**
     * 业务线稳定编码。
     */
    private String businessLineCode;
    /**
     * 项目组或项目提示。
     */
    private String projectHint;
    /**
     * 研发分支名。
     */
    private String developmentBranchName;
    /**
     * 禅道地址。
     */
    private String zentaoUrl;
    /**
     * 备注。
     */
    private String remark;
    /**
     * 预计完成日期。
     */
    private LocalDate plannedDueDate;
    /**
     * 计划开发开始日期。
     */
    private LocalDate plannedDevelopmentStartDate;
    /**
     * 计划测试开始日期。
     */
    private LocalDate plannedTestingStartDate;
    /**
     * 计划上线日期。
     */
    private LocalDate plannedReleaseDate;
    /**
     * 实际开发开始日期。
     */
    private LocalDate developmentStartedDate;
    /**
     * 实际测试开始日期。
     */
    private LocalDate testingStartedDate;
    /**
     * 实际开发完成日期。
     */
    private LocalDate actualCompletedDate;
    /**
     * 计划验收日期。
     */
    private LocalDate scheduledAcceptanceDate;
    /**
     * 实际测试完成日期。
     */
    private LocalDate actualTestingCompletedDate;
    /**
     * 验收日期。
     */
    private LocalDate acceptanceDate;
    /**
     * 实际上线日期。
     */
    private LocalDate releasedDate;
    /**
     * 关闭日期。
     */
    private LocalDate closedDate;
    /**
     * 关闭原因。
     */
    private String closeReason;
    /**
     * 预估工时。
     */
    private String estimatedEffort;
    /**
     * 实际开发工时。
     */
    private String actualEffort;
    /**
     * 实际测试工时。
     */
    private String actualTestingEffort;
    /**
     * 优先级。
     */
    private String priority;
    /**
     * 紧急程度。
     */
    private String urgency;
    /**
     * 最新任务评估草稿中的总预估工时。
     */
    private String totalEstimatedEffort;
    /**
     * 最新任务评估草稿中的开发预估工时。
     */
    private String developmentEstimatedEffort;
    /**
     * 最新任务评估草稿中的测试预估工时。
     */
    private String testingEstimatedEffort;
    /**
     * 最新任务评估草稿 JSON。
     */
    private String latestDevelopmentDraftJson;
    /**
     * 未完成待办数量。
     */
    private Long activeTodoCount;
    /**
     * 是否逻辑删除。
     */
    private Boolean deleted;
    /**
     * 逻辑删除时间。
     */
    private LocalDateTime deletedAt;
    /**
     * 逻辑删除人。
     */
    private String deletedBy;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 最后更新时间。
     */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public String getExternalMessageId() {
        return externalMessageId;
    }

    public void setExternalMessageId(String externalMessageId) {
        this.externalMessageId = externalMessageId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public String getDevelopmentOwnerUserName() {
        return developmentOwnerUserName;
    }

    public void setDevelopmentOwnerUserName(String developmentOwnerUserName) {
        this.developmentOwnerUserName = developmentOwnerUserName;
    }

    public String getStructuredDataJson() {
        return structuredDataJson;
    }

    public void setStructuredDataJson(String structuredDataJson) {
        this.structuredDataJson = structuredDataJson;
    }

    public String getAiDraftJson() {
        return aiDraftJson;
    }

    public void setAiDraftJson(String aiDraftJson) {
        this.aiDraftJson = aiDraftJson;
    }

    public String getIntakeStatus() {
        return intakeStatus;
    }

    public void setIntakeStatus(String intakeStatus) {
        this.intakeStatus = intakeStatus;
    }

    public String getDemandStatus() {
        return demandStatus;
    }

    public void setDemandStatus(String demandStatus) {
        this.demandStatus = demandStatus;
    }

    public String getPausePreviousDemandStatus() {
        return pausePreviousDemandStatus;
    }

    public void setPausePreviousDemandStatus(String pausePreviousDemandStatus) {
        this.pausePreviousDemandStatus = pausePreviousDemandStatus;
    }

    public String getPauseReason() {
        return pauseReason;
    }

    public void setPauseReason(String pauseReason) {
        this.pauseReason = pauseReason;
    }

    public LocalDate getPauseDate() {
        return pauseDate;
    }

    public void setPauseDate(LocalDate pauseDate) {
        this.pauseDate = pauseDate;
    }

    public String getEnrichmentStatus() {
        return enrichmentStatus;
    }

    public void setEnrichmentStatus(String enrichmentStatus) {
        this.enrichmentStatus = enrichmentStatus;
    }

    public String getEnrichmentErrorSummary() {
        return enrichmentErrorSummary;
    }

    public void setEnrichmentErrorSummary(String enrichmentErrorSummary) {
        this.enrichmentErrorSummary = enrichmentErrorSummary;
    }

    public LocalDateTime getEnrichmentUpdatedAt() {
        return enrichmentUpdatedAt;
    }

    public void setEnrichmentUpdatedAt(LocalDateTime enrichmentUpdatedAt) {
        this.enrichmentUpdatedAt = enrichmentUpdatedAt;
    }

    public Long getConvertedWorkItemId() {
        return convertedWorkItemId;
    }

    public void setConvertedWorkItemId(Long convertedWorkItemId) {
        this.convertedWorkItemId = convertedWorkItemId;
    }

    public String getApprovalCode() {
        return approvalCode;
    }

    public void setApprovalCode(String approvalCode) {
        this.approvalCode = approvalCode;
    }

    public String getApprovalTitle() {
        return approvalTitle;
    }

    public void setApprovalTitle(String approvalTitle) {
        this.approvalTitle = approvalTitle;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public String getProposerName() {
        return proposerName;
    }

    public void setProposerName(String proposerName) {
        this.proposerName = proposerName;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getRequirementType() {
        return requirementType;
    }

    public void setRequirementType(String requirementType) {
        this.requirementType = requirementType;
    }

    public String getRequirementName() {
        return requirementName;
    }

    public void setRequirementName(String requirementName) {
        this.requirementName = requirementName;
    }

    public String getRequirementSummary() {
        return requirementSummary;
    }

    public void setRequirementSummary(String requirementSummary) {
        this.requirementSummary = requirementSummary;
    }

    public String getRequirementDigest() {
        return requirementDigest;
    }

    public void setRequirementDigest(String requirementDigest) {
        this.requirementDigest = requirementDigest;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getBusinessLine() {
        return businessLine;
    }

    public void setBusinessLine(String businessLine) {
        this.businessLine = businessLine;
    }

    public String getBusinessLineCode() {
        return businessLineCode;
    }

    public void setBusinessLineCode(String businessLineCode) {
        this.businessLineCode = businessLineCode;
    }

    public String getProjectHint() {
        return projectHint;
    }

    public void setProjectHint(String projectHint) {
        this.projectHint = projectHint;
    }

    public String getDevelopmentBranchName() {
        return developmentBranchName;
    }

    public void setDevelopmentBranchName(String developmentBranchName) {
        this.developmentBranchName = developmentBranchName;
    }

    public String getZentaoUrl() {
        return zentaoUrl;
    }

    public void setZentaoUrl(String zentaoUrl) {
        this.zentaoUrl = zentaoUrl;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDate getPlannedDueDate() {
        return plannedDueDate;
    }

    public void setPlannedDueDate(LocalDate plannedDueDate) {
        this.plannedDueDate = plannedDueDate;
    }

    public LocalDate getPlannedDevelopmentStartDate() {
        return plannedDevelopmentStartDate;
    }

    public void setPlannedDevelopmentStartDate(LocalDate plannedDevelopmentStartDate) {
        this.plannedDevelopmentStartDate = plannedDevelopmentStartDate;
    }

    public LocalDate getPlannedTestingStartDate() {
        return plannedTestingStartDate;
    }

    public void setPlannedTestingStartDate(LocalDate plannedTestingStartDate) {
        this.plannedTestingStartDate = plannedTestingStartDate;
    }

    public LocalDate getPlannedReleaseDate() {
        return plannedReleaseDate;
    }

    public void setPlannedReleaseDate(LocalDate plannedReleaseDate) {
        this.plannedReleaseDate = plannedReleaseDate;
    }

    public LocalDate getDevelopmentStartedDate() {
        return developmentStartedDate;
    }

    public void setDevelopmentStartedDate(LocalDate developmentStartedDate) {
        this.developmentStartedDate = developmentStartedDate;
    }

    public LocalDate getTestingStartedDate() {
        return testingStartedDate;
    }

    public void setTestingStartedDate(LocalDate testingStartedDate) {
        this.testingStartedDate = testingStartedDate;
    }

    public LocalDate getActualCompletedDate() {
        return actualCompletedDate;
    }

    public void setActualCompletedDate(LocalDate actualCompletedDate) {
        this.actualCompletedDate = actualCompletedDate;
    }

    public LocalDate getScheduledAcceptanceDate() {
        return scheduledAcceptanceDate;
    }

    public void setScheduledAcceptanceDate(LocalDate scheduledAcceptanceDate) {
        this.scheduledAcceptanceDate = scheduledAcceptanceDate;
    }

    public LocalDate getActualTestingCompletedDate() {
        return actualTestingCompletedDate;
    }

    public void setActualTestingCompletedDate(LocalDate actualTestingCompletedDate) {
        this.actualTestingCompletedDate = actualTestingCompletedDate;
    }

    public LocalDate getAcceptanceDate() {
        return acceptanceDate;
    }

    public void setAcceptanceDate(LocalDate acceptanceDate) {
        this.acceptanceDate = acceptanceDate;
    }

    public LocalDate getReleasedDate() {
        return releasedDate;
    }

    public void setReleasedDate(LocalDate releasedDate) {
        this.releasedDate = releasedDate;
    }

    public LocalDate getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(LocalDate closedDate) {
        this.closedDate = closedDate;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    public String getEstimatedEffort() {
        return estimatedEffort;
    }

    public void setEstimatedEffort(String estimatedEffort) {
        this.estimatedEffort = estimatedEffort;
    }

    public String getActualEffort() {
        return actualEffort;
    }

    public void setActualEffort(String actualEffort) {
        this.actualEffort = actualEffort;
    }

    public String getActualTestingEffort() {
        return actualTestingEffort;
    }

    public void setActualTestingEffort(String actualTestingEffort) {
        this.actualTestingEffort = actualTestingEffort;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getTotalEstimatedEffort() {
        return totalEstimatedEffort;
    }

    public void setTotalEstimatedEffort(String totalEstimatedEffort) {
        this.totalEstimatedEffort = totalEstimatedEffort;
    }

    public String getDevelopmentEstimatedEffort() {
        return developmentEstimatedEffort;
    }

    public void setDevelopmentEstimatedEffort(String developmentEstimatedEffort) {
        this.developmentEstimatedEffort = developmentEstimatedEffort;
    }

    public String getTestingEstimatedEffort() {
        return testingEstimatedEffort;
    }

    public void setTestingEstimatedEffort(String testingEstimatedEffort) {
        this.testingEstimatedEffort = testingEstimatedEffort;
    }

    public String getLatestDevelopmentDraftJson() {
        return latestDevelopmentDraftJson;
    }

    public void setLatestDevelopmentDraftJson(String latestDevelopmentDraftJson) {
        this.latestDevelopmentDraftJson = latestDevelopmentDraftJson;
    }

    public Long getActiveTodoCount() {
        return activeTodoCount;
    }

    public void setActiveTodoCount(Long activeTodoCount) {
        this.activeTodoCount = activeTodoCount;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
