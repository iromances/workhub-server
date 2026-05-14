package cn.aslight.workhub.model.system;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统配置保存请求。
 */
public class SysConfigSaveRequest {

    @NotBlank(message = "不能为空")
    private String configGroup;

    @NotBlank(message = "不能为空")
    private String configKey;

    @NotBlank(message = "不能为空")
    private String configName;

    @NotBlank(message = "不能为空")
    private String valueType;

    private String value;
    private Boolean enabled = Boolean.TRUE;
    private String remark;

    public String getConfigGroup() {
        return configGroup;
    }

    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
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
