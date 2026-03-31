package cn.aslight.workhub.domain.intake.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class WecomCallbackRequest {

    @NotBlank(message = "不能为空")
    private String externalMessageId;

    @NotBlank(message = "不能为空")
    private String senderName;

    private String sourceChannel;

    private LocalDateTime receivedAt;

    @NotBlank(message = "不能为空")
    private String rawContent;

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

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
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
}
