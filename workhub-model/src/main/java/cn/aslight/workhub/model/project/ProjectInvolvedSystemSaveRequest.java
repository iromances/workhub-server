package cn.aslight.workhub.model.project;

import jakarta.validation.constraints.NotBlank;

/**
 * 研发涉及系统清单保存请求。
 */
public class ProjectInvolvedSystemSaveRequest {

    @NotBlank(message = "系统范围不能为空")
    private String systemScope;
    private String businessLine;
    @NotBlank(message = "系统名称不能为空")
    private String systemName;
    private String description;
    private Boolean enabled = Boolean.TRUE;
    private Integer sortOrder = 0;

    public String getSystemScope() {
        return systemScope;
    }

    public void setSystemScope(String systemScope) {
        this.systemScope = systemScope;
    }

    public String getBusinessLine() {
        return businessLine;
    }

    public void setBusinessLine(String businessLine) {
        this.businessLine = businessLine;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
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
}
