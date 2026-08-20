package cn.aslight.workhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workhub.ai")
/**
 * Ai 配置属性。
 */
public class AiProperties {

    private String masterKey;
    private final CodexCli codexCli = new CodexCli();

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public CodexCli getCodexCli() {
        return codexCli;
    }

    public static class CodexCli {

        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

    }
}
