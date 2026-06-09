package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 更新需求待办处理状态请求。
 */
public class IntakeTodoStatusRequest {

    @NotBlank(message = "待办状态不能为空")
    private String status;

    private String processResult;
    private LocalDateTime completedAt;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProcessResult() {
        return processResult;
    }

    public void setProcessResult(String processResult) {
        this.processResult = processResult;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
