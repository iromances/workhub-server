package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 需求阶段回退请求：回退到上一阶段并必填原因（写入历史记录）。
 */
public class IntakeStageRollbackRequest {

    /** 回退原因（必填，写入需求历史）。 */
    @NotBlank(message = "回退原因不能为空")
    @Size(max = 500, message = "回退原因不能超过 500 字")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
