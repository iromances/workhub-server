package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotBlank;

public class SystemAlertSubsystemSaveRequest {

    @NotBlank(message = "业务线不能为空")
    private String businessLineCode;

    @NotBlank(message = "环境不能为空")
    private String environmentCode;

    @NotBlank(message = "子系统名称不能为空")
    private String subsystemName;

    @NotBlank(message = "服务名不能为空")
    private String serviceName;

    private Boolean enabled;
    private String remark;

    public String getBusinessLineCode() {
        return businessLineCode;
    }

    public void setBusinessLineCode(String businessLineCode) {
        this.businessLineCode = businessLineCode;
    }

    public String getEnvironmentCode() {
        return environmentCode;
    }

    public void setEnvironmentCode(String environmentCode) {
        this.environmentCode = environmentCode;
    }

    public String getSubsystemName() {
        return subsystemName;
    }

    public void setSubsystemName(String subsystemName) {
        this.subsystemName = subsystemName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
