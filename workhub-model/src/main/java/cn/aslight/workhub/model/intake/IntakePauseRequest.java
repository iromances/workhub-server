package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 需求暂停请求模型。
 */
public class IntakePauseRequest {

    @NotBlank(message = "暂停原因不能为空")
    private String reason;

    @NotNull(message = "暂停日期不能为空")
    private LocalDate pauseDate;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDate getPauseDate() {
        return pauseDate;
    }

    public void setPauseDate(LocalDate pauseDate) {
        this.pauseDate = pauseDate;
    }
}
