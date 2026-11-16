package cn.aslight.workhub.model.system;

import java.util.List;

/**
 * 系统集成连接配置保存请求：仅包含对应系统段（zentao 或 jenkins）；
 * password/apiToken 留空表示保留既有凭据，不覆盖。
 */
public class IntegrationConfigSaveRequest {

    private ZentaoSave zentao;
    private JenkinsSave jenkins;

    public ZentaoSave getZentao() {
        return zentao;
    }

    public void setZentao(ZentaoSave zentao) {
        this.zentao = zentao;
    }

    public JenkinsSave getJenkins() {
        return jenkins;
    }

    public void setJenkins(JenkinsSave jenkins) {
        this.jenkins = jenkins;
    }

    /** 禅道连接配置保存段。 */
    public static class ZentaoSave {

        private String baseUrl;
        private String account;
        private String password;

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

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /** Jenkins 实例保存段：全量覆盖 instances，Job 绑定由后端保留。 */
    public static class JenkinsSave {

        private List<JenkinsInstanceSave> instances;

        public List<JenkinsInstanceSave> getInstances() {
            return instances;
        }

        public void setInstances(List<JenkinsInstanceSave> instances) {
            this.instances = instances;
        }
    }

    /** Jenkins 单实例保存段；apiToken 留空=保留既有凭据。 */
    public static class JenkinsInstanceSave {

        private String id;
        private String name;
        private String baseUrl;
        private String userName;
        private String apiToken;
        private Boolean enabled = Boolean.TRUE;

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

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
