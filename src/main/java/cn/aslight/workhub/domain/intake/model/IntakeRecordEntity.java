package cn.aslight.workhub.domain.intake.model;

import java.time.LocalDateTime;

public class IntakeRecordEntity {

    private Long id;
    private String sourceType;
    private String sourceChannel;
    private String externalMessageId;
    private String senderName;
    private LocalDateTime receivedAt;
    private String rawContent;
    private String structuredDataJson;
    private String aiDraftJson;
    private String intakeStatus;
    private String enrichmentStatus;
    private String enrichmentErrorSummary;
    private LocalDateTime enrichmentUpdatedAt;
    private Long convertedWorkItemId;
    private LocalDateTime createdAt;
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
