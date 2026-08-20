package cn.aslight.workhub.model.mcp;

import java.time.LocalDateTime;

public class McpResourceEntity {

    private Long id;
    private String resourceType;
    private String targetKey;
    private Boolean publicResource;
    private String featureTagsJson;
    private String businessLineCode;
    private String environmentCode;
    private String name;
    private String systemName;
    private String host;
    private Integer port;
    private String databaseSchema;
    private String username;
    private String secretRef;
    private String passwordEncrypted;
    private String sshPasswordEncrypted;
    private Boolean sshBastionEnabled;
    private Long bastionId;
    private String sshBastionHost;
    private Integer sshBastionPort;
    private String sshBastionUser;
    private String sshBastionPasswordEncrypted;
    private String sshIdentityFile;
    private String allowedServicesJson;
    private String allowedLogPathsJson;
    private String profilesJson;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }

    public Boolean getPublicResource() {
        return publicResource;
    }

    public void setPublicResource(Boolean publicResource) {
        this.publicResource = publicResource;
    }

    public String getFeatureTagsJson() {
        return featureTagsJson;
    }

    public void setFeatureTagsJson(String featureTagsJson) {
        this.featureTagsJson = featureTagsJson;
    }

    public String getBusinessLineCode() {
        return businessLineCode;
    }

    public void setBusinessLineCode(String businessLineCode) {
        this.businessLineCode = businessLineCode;
    }

    public String getEnvironmentCode() {
        return environmentCode;
    }

    public void setEnvironmentCode(String environmentCode) {
        this.environmentCode = environmentCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabaseSchema() {
        return databaseSchema;
    }

    public void setDatabaseSchema(String databaseSchema) {
        this.databaseSchema = databaseSchema;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSecretRef() {
        return secretRef;
    }

    public void setSecretRef(String secretRef) {
        this.secretRef = secretRef;
    }

    public String getPasswordEncrypted() {
        return passwordEncrypted;
    }

    public void setPasswordEncrypted(String passwordEncrypted) {
        this.passwordEncrypted = passwordEncrypted;
    }

    public String getSshPasswordEncrypted() {
        return sshPasswordEncrypted;
    }

    public void setSshPasswordEncrypted(String sshPasswordEncrypted) {
        this.sshPasswordEncrypted = sshPasswordEncrypted;
    }

    public Boolean getSshBastionEnabled() {
        return sshBastionEnabled;
    }

    public void setSshBastionEnabled(Boolean sshBastionEnabled) {
        this.sshBastionEnabled = sshBastionEnabled;
    }

    public Long getBastionId() {
        return bastionId;
    }

    public void setBastionId(Long bastionId) {
        this.bastionId = bastionId;
    }

    public String getSshBastionHost() {
        return sshBastionHost;
    }

    public void setSshBastionHost(String sshBastionHost) {
        this.sshBastionHost = sshBastionHost;
    }

    public Integer getSshBastionPort() {
        return sshBastionPort;
    }

    public void setSshBastionPort(Integer sshBastionPort) {
        this.sshBastionPort = sshBastionPort;
    }

    public String getSshBastionUser() {
        return sshBastionUser;
    }

    public void setSshBastionUser(String sshBastionUser) {
        this.sshBastionUser = sshBastionUser;
    }

    public String getSshBastionPasswordEncrypted() {
        return sshBastionPasswordEncrypted;
    }

    public void setSshBastionPasswordEncrypted(String sshBastionPasswordEncrypted) {
        this.sshBastionPasswordEncrypted = sshBastionPasswordEncrypted;
    }

    public String getSshIdentityFile() {
        return sshIdentityFile;
    }

    public void setSshIdentityFile(String sshIdentityFile) {
        this.sshIdentityFile = sshIdentityFile;
    }

    public String getAllowedServicesJson() {
        return allowedServicesJson;
    }

    public void setAllowedServicesJson(String allowedServicesJson) {
        this.allowedServicesJson = allowedServicesJson;
    }

    public String getAllowedLogPathsJson() {
        return allowedLogPathsJson;
    }

    public void setAllowedLogPathsJson(String allowedLogPathsJson) {
        this.allowedLogPathsJson = allowedLogPathsJson;
    }

    public String getProfilesJson() {
        return profilesJson;
    }

    public void setProfilesJson(String profilesJson) {
        this.profilesJson = profilesJson;
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
