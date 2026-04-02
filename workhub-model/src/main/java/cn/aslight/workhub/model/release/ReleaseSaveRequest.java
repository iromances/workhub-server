package cn.aslight.workhub.model.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * ReleaseSave 请求模型。
 */
public class ReleaseSaveRequest {

    @NotNull(message = "不能为空")
    private Long projectId;

    @NotBlank(message = "不能为空")
    private String name;

    @NotBlank(message = "不能为空")
    private String version;

    @NotBlank(message = "不能为空")
    private String status;

    private LocalDateTime plannedAt;
    private LocalDateTime releasedAt;

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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPlannedAt() {
        return plannedAt;
    }

    public void setPlannedAt(LocalDateTime plannedAt) {
        this.plannedAt = plannedAt;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }
}
