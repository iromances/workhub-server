package cn.aslight.workhub.model.system;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统用户保存请求。
 */
public class SysUserSaveRequest {

    @NotBlank(message = "不能为空")
    private String userName;

    @NotBlank(message = "不能为空")
    private String displayName;

    private String email;
    private String mobile;
    private String wecomUserid;
    private String avatarUrl;
    private String status;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getWecomUserid() {
        return wecomUserid;
    }

    public void setWecomUserid(String wecomUserid) {
        this.wecomUserid = wecomUserid;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
