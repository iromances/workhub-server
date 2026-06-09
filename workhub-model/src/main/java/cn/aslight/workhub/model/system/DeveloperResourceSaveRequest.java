package cn.aslight.workhub.model.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 研发人员资源保存请求。
 */
public class DeveloperResourceSaveRequest {

    @NotBlank(message = "研发人员用户名不能为空")
    @Size(max = 64, message = "研发人员用户名不能超过 64 个字符")
    private String userName;

    @NotBlank(message = "研发人员名称不能为空")
    @Size(max = 128, message = "研发人员名称不能超过 128 个字符")
    private String displayName;

    private Boolean enabled;

    @Size(max = 255, message = "备注不能超过 255 个字符")
    private String remark;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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
