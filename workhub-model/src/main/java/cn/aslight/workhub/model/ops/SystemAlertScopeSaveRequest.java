package cn.aslight.workhub.model.ops;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class SystemAlertScopeSaveRequest {

    @NotBlank(message = "业务线不能为空")
    private String businessLineCode;

    @NotBlank(message = "环境不能为空")
    private String environmentCode;

    @NotBlank(message = "关注范围不能为空")
    private String watchMode;

    @Valid
    @Size(max = 100, message = "关注子系统最多配置100个")
    private List<SystemAlertScopeServiceRequest> services = new ArrayList<>();

    @NotEmpty(message = "至少配置一个日志索引模式")
    @Size(max = 20, message = "日志索引模式最多配置20个")
    private List<@NotBlank(message = "日志索引模式不能为空")
                 @Size(max = 255, message = "日志索引模式长度不能超过255") String> indexPatterns;

    private Boolean enabled;

    @Size(max = 255, message = "备注长度不能超过255")
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

    public String getWatchMode() {
        return watchMode;
    }

    public void setWatchMode(String watchMode) {
        this.watchMode = watchMode;
    }

    public List<SystemAlertScopeServiceRequest> getServices() {
        return services;
    }

    public void setServices(List<SystemAlertScopeServiceRequest> services) {
        this.services = services == null ? new ArrayList<>() : services;
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
