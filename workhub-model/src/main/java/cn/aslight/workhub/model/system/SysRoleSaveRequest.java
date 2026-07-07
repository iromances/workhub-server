package cn.aslight.workhub.model.system;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统角色保存请求。
 */
public class SysRoleSaveRequest {

    @NotBlank(message = "不能为空")
    private String roleCode;

    @NotBlank(message = "不能为空")
    private String roleName;

    private Boolean enabled = Boolean.TRUE;
    private String remark;

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
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
