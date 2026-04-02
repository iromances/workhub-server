package cn.aslight.workhub.model.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Login 请求模型。
 */
public class LoginRequest {

    @NotBlank(message = "不能为空")
    private String username;

    @NotBlank(message = "不能为空")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
