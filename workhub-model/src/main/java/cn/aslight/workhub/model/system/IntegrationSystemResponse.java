package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统集成台账列表响应。
 */
public class IntegrationSystemResponse {

    private Long id;
    private String systemCode;
    private String systemName;
    private String integrationMethod;
    private String authType;
    private List<String> configGroups;
    private Boolean enabled;
    private Integer sortOrder;
    private String remark;
    private Long capabilityCount;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Long getCapabilityCount() {
        return capabilityCount;
    }

    public void setCapabilityCount(Long capabilityCount) {
        this.capabilityCount = capabilityCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
