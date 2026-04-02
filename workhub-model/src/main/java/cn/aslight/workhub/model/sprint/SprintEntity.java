package cn.aslight.workhub.model.sprint;

import java.time.LocalDate;

/**
 * 迭代实体。
 */
public class SprintEntity {

    /**
     * 迭代主键 ID。
     */
    private Long id;
    /**
     * 所属项目 ID。
     */
    private Long projectId;
    /**
     * 迭代名称。
     */
    private String sprintName;
    /**
     * 迭代状态。
     */
    private String sprintStatus;
    /**
     * 计划开始日期。
     */
    private LocalDate startDate;
    /**
     * 计划结束日期。
     */
    private LocalDate endDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getSprintName() {
        return sprintName;
    }

    public void setSprintName(String sprintName) {
        this.sprintName = sprintName;
    }

    public String getSprintStatus() {
        return sprintStatus;
    }

    public void setSprintStatus(String sprintStatus) {
        this.sprintStatus = sprintStatus;
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
