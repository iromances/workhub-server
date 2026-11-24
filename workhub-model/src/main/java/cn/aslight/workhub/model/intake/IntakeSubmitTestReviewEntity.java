package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;

/**
 * 提测 AI 审核记录实体。
 */
public class IntakeSubmitTestReviewEntity {

    private Long id;
    private Long intakeId;
    private String reviewStatus;
    private String reviewMessage;
    private String findingsJson;
    private String conclusion;
    private String summary;
    private String humanConfirmedBy;
    private LocalDateTime humanConfirmedAt;
    private String zentaoTestTaskNo;
    private String zentaoTestTaskUrl;
    /** 提测确认选择的测试环境编码（沿用 jenkins_environment_code 列，无论是否触发构建均记录）。 */
    private String jenkinsEnvironmentCode;
    private Long jenkinsBuildNumber;
    private String jenkinsBuildUrl;
    private String jenkinsBuildStatus;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIntakeId() {
        return intakeId;
    }

    public void setIntakeId(Long intakeId) {
        this.intakeId = intakeId;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewMessage() {
        return reviewMessage;
    }

    public void setReviewMessage(String reviewMessage) {
        this.reviewMessage = reviewMessage;
    }

    public String getFindingsJson() {
        return findingsJson;
    }

    public void setFindingsJson(String findingsJson) {
        this.findingsJson = findingsJson;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getHumanConfirmedBy() {
        return humanConfirmedBy;
    }

    public void setHumanConfirmedBy(String humanConfirmedBy) {
        this.humanConfirmedBy = humanConfirmedBy;
    }

    public LocalDateTime getHumanConfirmedAt() {
        return humanConfirmedAt;
    }

    public void setHumanConfirmedAt(LocalDateTime humanConfirmedAt) {
        this.humanConfirmedAt = humanConfirmedAt;
    }

    public String getZentaoTestTaskNo() {
        return zentaoTestTaskNo;
    }

    public void setZentaoTestTaskNo(String zentaoTestTaskNo) {
        this.zentaoTestTaskNo = zentaoTestTaskNo;
    }

    public String getZentaoTestTaskUrl() {
        return zentaoTestTaskUrl;
    }

    public void setZentaoTestTaskUrl(String zentaoTestTaskUrl) {
        this.zentaoTestTaskUrl = zentaoTestTaskUrl;
    }

    public String getJenkinsEnvironmentCode() {
        return jenkinsEnvironmentCode;
    }

    public void setJenkinsEnvironmentCode(String jenkinsEnvironmentCode) {
        this.jenkinsEnvironmentCode = jenkinsEnvironmentCode;
    }

    public Long getJenkinsBuildNumber() {
        return jenkinsBuildNumber;
    }

    public void setJenkinsBuildNumber(Long jenkinsBuildNumber) {
        this.jenkinsBuildNumber = jenkinsBuildNumber;
    }

    public String getJenkinsBuildUrl() {
        return jenkinsBuildUrl;
    }

    public void setJenkinsBuildUrl(String jenkinsBuildUrl) {
        this.jenkinsBuildUrl = jenkinsBuildUrl;
    }

    public String getJenkinsBuildStatus() {
        return jenkinsBuildStatus;
    }

    public void setJenkinsBuildStatus(String jenkinsBuildStatus) {
        this.jenkinsBuildStatus = jenkinsBuildStatus;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
