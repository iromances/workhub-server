package cn.aslight.workhub.model.project;

import java.time.LocalDateTime;

/**
 * 项目组实体。
 */
public class ProjectGroupEntity {

    /**
     * 项目组主键 ID。
     */
    private Long id;
    /**
     * 项目组名称。
     */
    private String groupName;
    /**
     * GitLab 组名或命名空间。
     */
    private String gitlabGroupName;
    /**
     * 项目组说明。
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

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
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
