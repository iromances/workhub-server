package cn.aslight.workhub.model.system;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统用户状态请求。
 */
public class SysUserStatusRequest {

    @NotBlank(message = "不能为空")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
