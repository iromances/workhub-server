package cn.aslight.workhub.model.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改密码请求。
 */
public class ChangePasswordRequest {

    @NotBlank(message = "不能为空")
    private String oldPassword;

    @NotBlank(message = "不能为空")
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
