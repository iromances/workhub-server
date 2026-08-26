package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SystemAlertScopeServiceRequest {

    @NotBlank(message = "子系统名称不能为空")
    @Size(max = 128, message = "子系统名称长度不能超过128")
    private String subsystemName;

    @NotBlank(message = "服务名不能为空")
    @Size(max = 128, message = "服务名长度不能超过128")
    private String serviceName;

    private Boolean enabled;

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
}
