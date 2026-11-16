package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.Size;

/**
 * 提测确认请求：AI 审核与 Jenkins 构建均为可选项。
 *
 * <p>skipAiReview 为 true 时跳过 AI 审核前置校验（审核记录置 SKIPPED）；
 * triggerJenkins 为 false 时不触发 Jenkins 构建，environmentCode 与 jenkinsJobName 被忽略；
 * 未跳过审核且审核不通过时须 forceConfirm 强制提测并记录确认人。
 */
public class IntakeSubmitTestConfirmRequest {

    /** 本次发布的测试环境编码；triggerJenkins 缺省或为 true 时必填。 */
    @Size(max = 32, message = "测试环境编码长度不能超过 32")
    private String environmentCode;

    /** 测试负责人（禅道测试单 owner）；提测确认必填，须为禅道有效账号。 */
    @Size(max = 64, message = "测试负责人账号长度不能超过 64")
    private String testOwnerUserName;

    /** 跳过 AI 提测审核直接确认；缺省为 false，维持先审后提。 */
    private Boolean skipAiReview;

    /** 是否触发测试环境 Jenkins 构建；缺省为 true 兼容存量调用。 */
    private Boolean triggerJenkins;

    private boolean forceConfirm;

    /** 实际开发工时，随提测阶段动作写入。 */
    private String actualEffort;

    /** 实际开发完成日期，随提测阶段动作写入。 */
    private String actualCompletedTime;

    /** 实际提测日期，随提测阶段动作写入。 */
    private String occurredAt;

    /** 本次发布的 Jenkins Job 名（顶层）；未填时按环境前缀+首个任务系统拼接。 */
    private String jenkinsJobName;

    public String getEnvironmentCode() {
        return environmentCode;
    }

    public void setEnvironmentCode(String environmentCode) {
        this.environmentCode = environmentCode;
    }

    public String getTestOwnerUserName() {
        return testOwnerUserName;
    }

    public void setTestOwnerUserName(String testOwnerUserName) {
        this.testOwnerUserName = testOwnerUserName;
    }

    public Boolean getSkipAiReview() {
        return skipAiReview;
    }

    public void setSkipAiReview(Boolean skipAiReview) {
        this.skipAiReview = skipAiReview;
    }

    public Boolean getTriggerJenkins() {
        return triggerJenkins;
    }

    public void setTriggerJenkins(Boolean triggerJenkins) {
        this.triggerJenkins = triggerJenkins;
    }

    public boolean isForceConfirm() {
        return forceConfirm;
    }

    public void setForceConfirm(boolean forceConfirm) {
        this.forceConfirm = forceConfirm;
    }

    public String getActualEffort() {
        return actualEffort;
    }

    public void setActualEffort(String actualEffort) {
        this.actualEffort = actualEffort;
    }

    public String getActualCompletedTime() {
        return actualCompletedTime;
    }

    public void setActualCompletedTime(String actualCompletedTime) {
        this.actualCompletedTime = actualCompletedTime;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getJenkinsJobName() {
        return jenkinsJobName;
    }

    public void setJenkinsJobName(String jenkinsJobName) {
        this.jenkinsJobName = jenkinsJobName;
    }
}
