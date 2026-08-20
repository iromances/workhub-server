package cn.aslight.workhub.model.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 当前用户资料修改请求。
 */
public class UpdateProfileRequest {

    @NotBlank(message = "不能为空")
    private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
