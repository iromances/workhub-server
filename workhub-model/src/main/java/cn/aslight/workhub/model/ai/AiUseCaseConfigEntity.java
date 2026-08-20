package cn.aslight.workhub.model.ai;

import java.time.LocalDateTime;

/**
 * AI 任务配置实体。
 */
public class AiUseCaseConfigEntity {

    private Long id;
    private String useCaseCode;
    private String useCaseName;
    private String domain;
    private String description;
    private Long providerConfigId;
    private String model;
    private String reasoningLevel;
    private String speedMode;
    private Integer timeoutSeconds;
    private Boolean jsonSchemaEnabled;
    private String schemaClasspath;
    private String promptTemplate;
    private String promptVariablesDesc;
    private Integer promptVersion;
    private String promptChecksum;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime modifyTime;
    private String modifyBy;
    private LocalDateTime deleteTime;
    private String deleteBy;
    private Boolean deleted;
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUseCaseCode() {
        return useCaseCode;
    }

    public void setUseCaseCode(String useCaseCode) {
        this.useCaseCode = useCaseCode;
    }

    public String getUseCaseName() {
        return useCaseName;
    }

    public void setUseCaseName(String useCaseName) {
        this.useCaseName = useCaseName;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getProviderConfigId() {
        return providerConfigId;
    }

    public void setProviderConfigId(Long providerConfigId) {
        this.providerConfigId = providerConfigId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getReasoningLevel() {
        return reasoningLevel;
    }

    public void setReasoningLevel(String reasoningLevel) {
        this.reasoningLevel = reasoningLevel;
    }

    public String getSpeedMode() {
        return speedMode;
    }

    public void setSpeedMode(String speedMode) {
        this.speedMode = speedMode;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Boolean getJsonSchemaEnabled() {
        return jsonSchemaEnabled;
    }

    public void setJsonSchemaEnabled(Boolean jsonSchemaEnabled) {
        this.jsonSchemaEnabled = jsonSchemaEnabled;
    }

    public String getSchemaClasspath() {
        return schemaClasspath;
    }

    public void setSchemaClasspath(String schemaClasspath) {
        this.schemaClasspath = schemaClasspath;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getPromptVariablesDesc() {
        return promptVariablesDesc;
    }

    public void setPromptVariablesDesc(String promptVariablesDesc) {
        this.promptVariablesDesc = promptVariablesDesc;
    }

    public Integer getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(Integer promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getPromptChecksum() {
        return promptChecksum;
    }

    public void setPromptChecksum(String promptChecksum) {
        this.promptChecksum = promptChecksum;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { this.createTime = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { this.createBy = value; }
    public LocalDateTime getModifyTime() { return modifyTime; }
    public void setModifyTime(LocalDateTime value) { this.modifyTime = value; }
    public String getModifyBy() { return modifyBy; }
    public void setModifyBy(String value) { this.modifyBy = value; }
    public LocalDateTime getDeleteTime() { return deleteTime; }
    public void setDeleteTime(LocalDateTime value) { this.deleteTime = value; }
    public String getDeleteBy() { return deleteBy; }
    public void setDeleteBy(String value) { this.deleteBy = value; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean value) { this.deleted = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
}
