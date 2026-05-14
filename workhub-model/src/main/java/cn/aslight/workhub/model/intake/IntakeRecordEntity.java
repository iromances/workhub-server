package cn.aslight.workhub.model.intake;

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
