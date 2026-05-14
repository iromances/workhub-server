package cn.aslight.workhub.model.project;

import jakarta.validation.constraints.NotBlank;

/**
 * 项目组保存请求。
 */
public class ProjectGroupSaveRequest {

    /**
     * 项目组名称。
     */
    @NotBlank(message = "不能为空")
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
    private Boolean enabled = Boolean.TRUE;

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
}
