package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public class SystemAlertSubsystemSaveRequest {

    @NotBlank(message = "业务线不能为空")
    private String businessLineCode;

    @NotBlank(message = "环境不能为空")
    private String environmentCode;

    @NotBlank(message = "子系统名称不能为空")
    private String subsystemName;

    @NotBlank(message = "服务名不能为空")
    private String serviceName;

    @NotEmpty(message = "至少配置一个日志索引模式")
    @Size(max = 20, message = "日志索引模式最多配置20个")
    private List<@NotBlank(message = "日志索引模式不能为空")
                 @Size(max = 255, message = "日志索引模式长度不能超过255") String> indexPatterns;

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

    public List<String> getIndexPatterns() {
        return indexPatterns;
    }

    public void setIndexPatterns(List<String> indexPatterns) {
        this.indexPatterns = indexPatterns;
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
