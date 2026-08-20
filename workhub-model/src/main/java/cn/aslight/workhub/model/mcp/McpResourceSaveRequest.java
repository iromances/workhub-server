package cn.aslight.workhub.model.mcp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class McpResourceSaveRequest {

    @NotBlank
    private String resourceType;

    private String targetKey;

    private Boolean publicResource;

    private List<String> featureTags;

    private String businessLineCode;

    private List<String> businessLineCodes;

    @NotBlank
    private String environmentCode;

    private String name;

    private String systemName;

    private List<String> systemNames;

    @NotBlank
    private String host;

    @NotNull
    private Integer port;

    private String databaseSchema;
    private String username;
    private String secretRef;
    private String password;
    private String sshPassword;
    private Boolean sshBastionEnabled;
    private Long bastionId;
    private String sshBastionHost;
    private Integer sshBastionPort;
    private String sshBastionUser;
    private String sshBastionPassword;
    private String sshIdentityFile;
    private List<String> allowedServices;
    private List<String> allowedLogPaths;
    private List<McpResourceProfile> profiles;
    private Boolean enabled;
    private String remark;

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

    public List<String> getFeatureTags() {
        return featureTags;
    }

    public void setFeatureTags(List<String> featureTags) {
        this.featureTags = featureTags;
    }

    public String getBusinessLineCode() {
        return businessLineCode;
    }

    public void setBusinessLineCode(String businessLineCode) {
        this.businessLineCode = businessLineCode;
    }

    public List<String> getBusinessLineCodes() {
        return businessLineCodes;
    }

    public void setBusinessLineCodes(List<String> businessLineCodes) {
        this.businessLineCodes = businessLineCodes;
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

    public List<String> getSystemNames() {
        return systemNames;
    }

    public void setSystemNames(List<String> systemNames) {
        this.systemNames = systemNames;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public void setSshPassword(String sshPassword) {
        this.sshPassword = sshPassword;
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

    public String getSshBastionPassword() {
        return sshBastionPassword;
    }

    public void setSshBastionPassword(String sshBastionPassword) {
        this.sshBastionPassword = sshBastionPassword;
    }

    public String getSshIdentityFile() {
        return sshIdentityFile;
    }

    public void setSshIdentityFile(String sshIdentityFile) {
        this.sshIdentityFile = sshIdentityFile;
    }

    public List<String> getAllowedServices() {
        return allowedServices;
    }

    public void setAllowedServices(List<String> allowedServices) {
        this.allowedServices = allowedServices;
    }

    public List<String> getAllowedLogPaths() {
        return allowedLogPaths;
    }

    public void setAllowedLogPaths(List<String> allowedLogPaths) {
        this.allowedLogPaths = allowedLogPaths;
    }

    public List<McpResourceProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<McpResourceProfile> profiles) {
        this.profiles = profiles;
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
}
