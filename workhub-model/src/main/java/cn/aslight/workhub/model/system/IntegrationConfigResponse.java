package cn.aslight.workhub.model.system;

import java.util.List;

/**
 * 系统集成连接配置响应：仅返回地址与账号，凭据以“已配置”布尔表达，不回显明文。
 */
public class IntegrationConfigResponse {

    private String systemCode;
    private ZentaoConfig zentao;
    private JenkinsConfig jenkins;

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public ZentaoConfig getZentao() {
        return zentao;
    }

    public void setZentao(ZentaoConfig zentao) {
        this.zentao = zentao;
    }

    public JenkinsConfig getJenkins() {
        return jenkins;
    }

    public void setJenkins(JenkinsConfig jenkins) {
        this.jenkins = jenkins;
    }

    /** 禅道连接配置视图。 */
    public static class ZentaoConfig {

        private String baseUrl;
        private String account;
        private Boolean passwordConfigured;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getAccount() {
            return account;
        }

        public void setAccount(String account) {
            this.account = account;
        }

        public Boolean getPasswordConfigured() {
            return passwordConfigured;
        }

        public void setPasswordConfigured(Boolean passwordConfigured) {
            this.passwordConfigured = passwordConfigured;
        }
    }

    /** Jenkins 实例配置视图（instances 段；Job 绑定不在此维护）。 */
    public static class JenkinsConfig {

        private List<JenkinsInstance> instances;

        public List<JenkinsInstance> getInstances() {
            return instances;
        }

        public void setInstances(List<JenkinsInstance> instances) {
            this.instances = instances;
        }
    }

    /** Jenkins 单实例视图；Token 只返回是否已配置。 */
    public static class JenkinsInstance {

        private String id;
        private String name;
        private String baseUrl;
        private String userName;
        private Boolean enabled;
        private Boolean tokenConfigured;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Boolean getTokenConfigured() {
            return tokenConfigured;
        }

        public void setTokenConfigured(Boolean tokenConfigured) {
            this.tokenConfigured = tokenConfigured;
        }
    }
}
