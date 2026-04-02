package cn.aslight.workhub.model.release;

import java.time.LocalDateTime;

/**
 * 版本实体。
 */
public class ReleaseEntity {

    /**
     * 版本主键 ID。
     */
    private Long id;
    /**
     * 所属项目 ID。
     */
    private Long projectId;
    /**
     * 版本名称。
     */
    private String releaseName;
    /**
     * 版本号。
     */
    private String releaseVersion;
    /**
     * 版本状态。
     */
    private String releaseStatus;
    /**
     * 计划发布时间。
     */
    private LocalDateTime plannedAt;
    /**
     * 实际发布时间。
     */
    private LocalDateTime releasedAt;

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

    public String getReleaseName() {
        return releaseName;
    }

    public void setReleaseName(String releaseName) {
        this.releaseName = releaseName;
    }

    public String getReleaseVersion() {
        return releaseVersion;
    }

    public void setReleaseVersion(String releaseVersion) {
        this.releaseVersion = releaseVersion;
    }

    public String getReleaseStatus() {
        return releaseStatus;
    }

    public void setReleaseStatus(String releaseStatus) {
        this.releaseStatus = releaseStatus;
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
