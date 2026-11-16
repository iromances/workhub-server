package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;

public class SysJobConfigEntity {

    private Long id;
    private String jobCode;
    private String jobName;
    private String scheduleType;
    private String scheduleValue;
    private String defaultScheduleValue;
    private Boolean enabled;
    private Boolean allowManualTrigger;
    private String paramDesc;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobCode() {
        return jobCode;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }

    public String getScheduleValue() {
        return scheduleValue;
    }

    public void setScheduleValue(String scheduleValue) {
        this.scheduleValue = scheduleValue;
    }

    public String getDefaultScheduleValue() {
        return defaultScheduleValue;
    }

    public void setDefaultScheduleValue(String defaultScheduleValue) {
        this.defaultScheduleValue = defaultScheduleValue;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getAllowManualTrigger() {
        return allowManualTrigger;
    }

    public void setAllowManualTrigger(Boolean allowManualTrigger) {
        this.allowManualTrigger = allowManualTrigger;
    }

    public String getParamDesc() {
        return paramDesc;
    }

    public void setParamDesc(String paramDesc) {
        this.paramDesc = paramDesc;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
