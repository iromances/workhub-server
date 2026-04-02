package cn.aslight.workhub.model.sprint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * SprintSave 请求模型。
 */
public class SprintSaveRequest {

    @NotNull(message = "不能为空")
    private Long projectId;

    @NotBlank(message = "不能为空")
    private String name;

    @NotBlank(message = "不能为空")
    private String status;

    private LocalDate startDate;
    private LocalDate endDate;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
