package cn.aslight.workhub.model.project;

import java.time.LocalDateTime;

/**
 * 业务线实体。
 */
public class BusinessLineEntity {

    /**
     * 业务线主键 ID。
     */
    private Long id;
    /**
     * 业务线名称。
     */
    private String businessLineName;
    /**
     * 业务线稳定编码。
     */
    private String businessLineCode;
    /**
     * GitLab 组名或命名空间。
     */
    private String gitlabGroupName;
    /**
     * 业务线说明。
     */
    private String description;
    /**
     * 是否启用。
     */
    private Boolean enabled;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBusinessLineName() {
        return businessLineName;
    }

    public void setBusinessLineName(String businessLineName) {
        this.businessLineName = businessLineName;
    }

    public String getBusinessLineCode() {
        return businessLineCode;
    }

    public void setBusinessLineCode(String businessLineCode) {
        this.businessLineCode = businessLineCode;
    }

    public String getGitlabGroupName() {
        return gitlabGroupName;
    }

    public void setGitlabGroupName(String gitlabGroupName) {
        this.gitlabGroupName = gitlabGroupName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
