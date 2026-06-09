package cn.aslight.workhub.model.project;

import jakarta.validation.constraints.NotBlank;

/**
 * 业务线保存请求。
 */
public class BusinessLineSaveRequest {

    /**
     * 业务线名称。
     */
    @NotBlank(message = "不能为空")
    private String businessLineName;
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
    private Boolean enabled = Boolean.TRUE;

    public String getBusinessLineName() {
        return businessLineName;
    }

    public void setBusinessLineName(String businessLineName) {
        this.businessLineName = businessLineName;
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
