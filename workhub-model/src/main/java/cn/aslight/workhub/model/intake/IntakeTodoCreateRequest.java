package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 新增需求待办请求。
 */
public class IntakeTodoCreateRequest {

    @NotBlank(message = "待办标题不能为空")
    private String title;

    private String content;
    private String assigneeUserName;
    private LocalDateTime plannedAt;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAssigneeUserName() {
        return assigneeUserName;
    }

    public void setAssigneeUserName(String assigneeUserName) {
        this.assigneeUserName = assigneeUserName;
    }

    public LocalDateTime getPlannedAt() {
        return plannedAt;
    }

    public void setPlannedAt(LocalDateTime plannedAt) {
        this.plannedAt = plannedAt;
    }
}
