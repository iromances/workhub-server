package cn.aslight.workhub.model.system;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统集成接入能力保存项。
 */
public class IntegrationCapabilityItem {

    @NotBlank(message = "能力编码不能为空")
    private String capabilityCode;

    @NotBlank(message = "能力名称不能为空")
    private String capabilityName;

    @NotBlank(message = "能力类型不能为空")
    private String capabilityType;

    @NotBlank(message = "实现状态不能为空")
    private String implStatus;

    private String description;
    private Boolean enabled = Boolean.TRUE;
    private Integer sortOrder = 0;
    private String remark;

    public String getCapabilityCode() {
        return capabilityCode;
    }

    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }

    public String getCapabilityName() {
        return capabilityName;
    }

    public void setCapabilityName(String capabilityName) {
        this.capabilityName = capabilityName;
    }

    public String getCapabilityType() {
        return capabilityType;
    }

    public void setCapabilityType(String capabilityType) {
        this.capabilityType = capabilityType;
    }

    public String getImplStatus() {
        return implStatus;
    }

    public void setImplStatus(String implStatus) {
        this.implStatus = implStatus;
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
}
