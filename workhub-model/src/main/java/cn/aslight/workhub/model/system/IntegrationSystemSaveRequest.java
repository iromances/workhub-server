package cn.aslight.workhub.model.system;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 系统集成台账保存请求；更新时 systemCode 忽略（不可修改）。
 */
public class IntegrationSystemSaveRequest {

    @NotBlank(message = "系统编码不能为空")
    private String systemCode;

    @NotBlank(message = "系统名称不能为空")
    private String systemName;

    @NotBlank(message = "集成方式不能为空")
    private String integrationMethod;

    @NotBlank(message = "认证方式不能为空")
    private String authType;

    private List<String> configGroups;
    private Boolean enabled = Boolean.TRUE;
    private Integer sortOrder = 0;
    private String remark;

    @Valid
    private List<IntegrationCapabilityItem> capabilities;

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getIntegrationMethod() {
        return integrationMethod;
    }

    public void setIntegrationMethod(String integrationMethod) {
        this.integrationMethod = integrationMethod;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public List<String> getConfigGroups() {
        return configGroups;
    }

    public void setConfigGroups(List<String> configGroups) {
        this.configGroups = configGroups;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<IntegrationCapabilityItem> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<IntegrationCapabilityItem> capabilities) {
        this.capabilities = capabilities;
    }
}
