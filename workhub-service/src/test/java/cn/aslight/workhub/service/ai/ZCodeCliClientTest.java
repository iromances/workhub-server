package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ZCodeCliClientTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final ZCodeCliClient client = new ZCodeCliClient(json);

    private AiProviderConfigEntity provider() throws Exception {
        var provider = new AiProviderConfigEntity(); provider.setCliType("ZCODE"); provider.setChannelType("CLI");
        provider.setCliCommand("/usr/bin/true"); provider.setDefaultModel("custom/glm-4.6"); provider.setDefaultReasoningLevel("enabled"); provider.setDefaultSpeedMode("STANDARD");
        Path config = directory.resolve("source.json");
        Files.writeString(config, "{\"schemaVersion\":1,\"config\":{\"providerConfigRules\":{},\"apiKey\":\"test-secret\"}}");
        provider.setCliBuiltinConfigPath(config.toString()); provider.setCliPersonalConfigPath(config.toString());
        return provider;
    }
    @Test void launchIsPrivateAndDoesNotImportHostMcpSkillsOrShellSecrets() throws Exception {
        var provider = provider(); Path run = Files.createDirectory(directory.resolve("run"));
        var launch = client.prepare(provider, provider.getDefaultModel(), "enabled", run, run.resolve("result.json"), "task", List.of(), null, null);
        var command = launch.builder().command(); var environment = launch.builder().environment();
        assertEquals(List.of("/usr/bin/true", "app-server", "--stdio", "--cwd", run.resolve("work").toString()), command);
        assertFalse(String.join(" ", command).contains("test-secret"));
        assertEquals(run.resolve("profile").toString(), environment.get("HOME"));
        assertEquals(run.resolve("profile").toString(), environment.get("ZCODE_DATA_BASE_DIR"));
        assertFalse(environment.containsKey("NODE_OPTIONS"));
        assertFalse(json.readTree(Files.readString(run.resolve("profile/.zcode/cli/config.json"))).path("plugins").path("enabled").asBoolean());
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(run.resolve("provider.json")));
        assertEquals(Files.readString(directory.resolve("source.json")), Files.readString(run.resolve("provider.json")));
    }
    @Test void shellExpressionsAndUnknownCliTypesCannotSelectCodex() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ZCodeCliClient.executable("node; echo secret"));
        assertThrows(IllegalArgumentException.class, () -> AiCliType.resolve(null, "unrelated-cli"));
        assertEquals("ZCODE", AiCliType.resolve(null, "/opt/ZCode/zcode.cjs"));
        var provider = provider(); assertTrue(AiCliType.is(provider, "ZCODE")); assertFalse(AiCliType.is(provider, "CODEX"));
    }
    @Test void authenticationAndDeviceStateArePrivateCopiesAndSourceIsUnchanged() throws Exception {
        var provider = provider(); Path run = Files.createDirectory(directory.resolve("run"));
        Path source = directory.resolve("credentials.json");
        Files.writeString(source, "{\"oauth:bigmodel:access_token\":\"fixture-only\"}");
        Files.writeString(directory.resolve("telemetry-state.json"), "{\"deviceMid\":\"fixture-device\"}");
        var launch = client.prepare(provider, provider.getDefaultModel(), "enabled", run, run.resolve("result.json"), "task", List.of(), null, null);
        Path credentials = run.resolve("profile/.zcode/v2/credentials.json");
        assertEquals(Files.readString(source), Files.readString(credentials));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(credentials));
        assertEquals(run.resolve("profile").toString(), launch.builder().environment().get("ZCODE_DATA_BASE_DIR"));
        Files.writeString(credentials, "{\"refreshed\":\"fixture-only\"}");
        assertFalse(Files.readString(source).contains("refreshed"));
        assertEquals("fixture-device", json.readTree(Files.readString(run.resolve("profile/.zcode/v2/telemetry-state.json"))).path("deviceMid").asText());
    }

    @Test void malformedCredentialStateDoesNotExposeItsContents() throws Exception {
        var provider = provider(); Path run = Files.createDirectory(directory.resolve("run"));
        Files.writeString(directory.resolve("credentials.json"), "{a-secret-value-not-to-log");
        var error = assertThrows(IllegalArgumentException.class, () -> client.prepare(provider, provider.getDefaultModel(), "enabled", run, run.resolve("result.json"), "task", List.of(), null, null));
        assertFalse(error.getMessage().contains("a-secret-value")); assertNull(error.getCause());
    }
    @Test void modelAndReasoningAreExplicitAndSpeedDoesNotClaimUnsupportedModes() throws Exception {
        var provider = provider(); AiCliType.validate(provider);
        assertThrows(IllegalArgumentException.class, () -> AiCliType.selection("glm-4.6", "enabled"));
        assertThrows(IllegalArgumentException.class, () -> AiCliType.selection("provider/glm-4.6$high", "enabled"));
        assertThrows(IllegalArgumentException.class, () -> AiCliType.selection("provider/glm-4.6", ""));
        provider.setDefaultSpeedMode("FAST"); assertThrows(IllegalArgumentException.class, () -> AiCliType.validate(provider));
    }
    @Test void sourceSymlinkIsRejectedBeforeCredentialsCanBeCopied() throws Exception {
        var provider = provider(); Path link = directory.resolve("link.json"); Files.createSymbolicLink(link, directory.resolve("source.json"));
        provider.setCliPersonalConfigPath(link.toString()); Path run = Files.createDirectory(directory.resolve("run"));
        assertThrows(IllegalArgumentException.class, () -> client.prepare(provider, provider.getDefaultModel(), "enabled", run, run.resolve("result.json"), "task", List.of(), null, null));
        assertFalse(Files.exists(run.resolve("provider.json")));
    }
    @Test void protocolReaderRejectsUnboundedAndIncompleteMessages() {
        CliTaskConversation conversation = new CliTaskConversation() {
            public void start(java.io.OutputStream input) { }
            public String accept(tools.jackson.databind.JsonNode event) { return null; }
        };
        assertThrows(java.io.IOException.class, () -> ZCodeCliClient.readProtocol(new java.io.ByteArrayInputStream("{}".getBytes()), conversation, ignored -> { }));
        assertThrows(java.io.IOException.class, () -> ZCodeCliClient.readProtocol(new java.io.ByteArrayInputStream("x".repeat(524289).getBytes()), conversation, ignored -> { }));
    }
}
