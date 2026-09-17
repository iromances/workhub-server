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
     * 历史本地存储路径。数据库型附件为空。
     */
    private String storagePath;
    /**
     * 文件内容类型。
     */
    private String contentType;
    /**
     * 数据库存储的文件原始内容。历史附件可能为空并回退本地路径。
     */
    private byte[] fileContent;
    /**
     * 文件原始字节数。
     */
    private Long fileSize;
    /**
     * 文件内容 SHA-256 摘要。
     */
    private String fileSha256;
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

    public byte[] getFileContent() {
        return fileContent;
    }

    public void setFileContent(byte[] fileContent) {
        this.fileContent = fileContent;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public void setFileSha256(String fileSha256) {
        this.fileSha256 = fileSha256;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
