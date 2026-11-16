package cn.aslight.workhub.model.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

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
     * 支持环境编码列表，如 DEV、FAT、TEST、PROD。
     */
    @NotEmpty(message = "支持环境不能为空")
    private List<String> supportedEnvironments;
    /**
     * 是否启用。
     */
    private Boolean enabled = Boolean.TRUE;
    /**
     * 多环境访问地址。编辑时为 null 表示保留存量，空数组表示清空。
     */
    private List<BusinessLineAccessConfigRequest> accessConfigs;

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

    public List<String> getSupportedEnvironments() {
        return supportedEnvironments;
    }

    public void setSupportedEnvironments(List<String> supportedEnvironments) {
        this.supportedEnvironments = supportedEnvironments;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<BusinessLineAccessConfigRequest> getAccessConfigs() {
        return accessConfigs;
    }

    public void setAccessConfigs(List<BusinessLineAccessConfigRequest> accessConfigs) {
        this.accessConfigs = accessConfigs;
    }
}
