package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * IntakeUpload 请求模型。
 */
public class IntakeUploadRequest {

    @NotBlank(message = "不能为空")
    private String senderName;

    @NotBlank(message = "不能为空")
    private String developmentOwnerUserName;

    private String sourceChannel;

    private LocalDateTime receivedAt;

    private String rawContent;

    private String projectGroup;

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getDevelopmentOwnerUserName() {
        return developmentOwnerUserName;
    }

    public void setDevelopmentOwnerUserName(String developmentOwnerUserName) {
        this.developmentOwnerUserName = developmentOwnerUserName;
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

    public String getProjectGroup() {
        return projectGroup;
    }

    public void setProjectGroup(String projectGroup) {
        this.projectGroup = projectGroup;
    }
}
