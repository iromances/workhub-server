package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CodexAgentCliProtocolTest {
    private final CodexAgentCliProtocol protocol = new CodexAgentCliProtocol(new ObjectMapper());

    @Test
    void commandEnablesNativeExecutionAndAddsWorkhubWithoutDisablingHostTools() {
        var provider = new AiProviderConfigEntity();
        provider.setCliCommand("/usr/bin/true");
        var command = protocol.command(provider, "selected-model", Path.of("/private/tmp/task"), Path.of("/private/tmp/schema.json"),
                Path.of("/private/tmp/result.json"), "http://127.0.0.1:12345/mcp");
        assertEquals("exec", command.get(1));
        assertTrue(command.contains("--output-schema"));
        assertEquals("/private/tmp/schema.json", command.get(command.indexOf("--output-schema") + 1));
        assertFalse(command.contains("--ignore-user-config"));
        assertFalse(command.contains("--ignore-rules"));
        assertFalse(command.contains("--disable"));
        assertFalse(command.contains("read-only"));
        assertTrue(command.contains("danger-full-access"));
        assertTrue(command.contains("web_search=\"live\""));
        assertFalse(command.stream().anyMatch(arg -> arg.contains("dangerously")));
        assertTrue(command.contains("mcp_servers.workhub_task.bearer_token_env_var=\"WORKHUB_AGENT_RUN_TOKEN\""));
        assertTrue(command.contains("selected-model"));
        assertTrue(command.contains("mcp_servers.workhub_task.default_tools_approval_mode=\"approve\""));
        assertTrue(command.contains("approval_policy=\"never\""));
        assertFalse(command.stream().anyMatch(arg -> arg.startsWith("skills.") || arg.startsWith("project_doc_max_bytes=")));
    }

    @Test
    void nativeToolsRemainAvailableWithoutMcp() {
        var provider = new AiProviderConfigEntity(); provider.setCliCommand("/usr/bin/true");
        var command = protocol.command(provider, "m", Path.of("/tmp"), Path.of("/tmp/s"), Path.of("/tmp/r"), null);
        assertTrue(command.contains("danger-full-access"));
        assertFalse(command.stream().anyMatch(arg -> arg.contains("mcp_servers")));
    }

    @Test
    void otherCliIsNotSentCodexFlagsAndShellExpressionsAreRejected() {
        var provider = new AiProviderConfigEntity();
        provider.setCliCommand("claude");
        assertFalse(protocol.supports(provider));
        provider.setCliCommand("codex");
        assertTrue(protocol.supports(provider));
        provider.setCliCommand("codex; echo unsafe");
        assertThrows(IllegalArgumentException.class, () -> protocol.command(provider, "m", Path.of("/tmp"), Path.of("/tmp/s"), Path.of("/tmp/r"), "http://127.0.0.1/mcp"));
    }

    @Test
    void childEnvironmentKeepsSandboxRestrictionsAndDropsEnterpriseCredentials() {
        var environment = new HashMap<>(Map.of("PATH", "/usr/bin", "HOME", "/task-user", "CODEX_HOME", "/task-auth",
                "CODEX_SANDBOX_NETWORK_DISABLED", "1", "DB_PASSWORD", "db-secret", "JENKINS_TOKEN", "jenkins-secret", "GITLAB_TOKEN", "git-secret"));
        CodexAgentCliProtocol.restrictEnvironment(environment);
        assertEquals("1", environment.get("CODEX_SANDBOX_NETWORK_DISABLED"));
        assertTrue(environment.containsKey("CODEX_HOME"));
        assertFalse(environment.containsValue("db-secret"));
        assertFalse(environment.containsValue("jenkins-secret"));
        assertFalse(environment.containsValue("git-secret"));
    }
}
