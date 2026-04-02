package cn.aslight.workhub.model.attachment;

import java.time.LocalDateTime;

/**
 * 附件实体。
 */
public class AttachmentEntity {

    /**
     * 附件主键 ID。
     */
    private Long id;
    /**
     * 业务类型，例如 intake、work-item。
     */
    private String bizType;
    /**
     * 业务主键 ID。
     */
    private Long bizId;
    /**
     * 原始文件名。
     */
    private String fileName;
    /**
     * 本地存储路径。
     */
    private String storagePath;
    /**
     * 文件内容类型。
     */
    private String contentType;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
