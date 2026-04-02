package cn.aslight.workhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workhub.ai")
/**
 * Ai 配置属性。
 */
public class AiProperties {

    private String provider = "heuristic";
    private final Openrouter openrouter = new Openrouter();
    private final Minimax minimax = new Minimax();
    private final CodexCli codexCli = new CodexCli();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Openrouter getOpenrouter() {
        return openrouter;
    }

    public Minimax getMinimax() {
        return minimax;
    }

    public CodexCli getCodexCli() {
        return codexCli;
    }

    public static class Openrouter {

        private boolean enabled;
        private String apiKey;
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String model = "openai/gpt-4o-mini";
        private String referer = "http://127.0.0.1:8080";
        private String title = "WorkHub Server";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getReferer() {
            return referer;
        }

        public void setReferer(String referer) {
            this.referer = referer;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    public static class Minimax {

        private boolean enabled;
        private String apiKey;
        private String baseUrl = "https://api.minimax.io/v1";
        private String model = "MiniMax-M2.5";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class CodexCli {

        private boolean enabled;
        private String command = "codex";
        private String model = "gpt-5.3-codex";
        private String reasoningEffort = "low";
        private int timeoutSeconds = 0;
        private boolean disablePlugins = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public void setReasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isDisablePlugins() {
            return disablePlugins;
        }

        public void setDisablePlugins(boolean disablePlugins) {
            this.disablePlugins = disablePlugins;
        }
    }
}
