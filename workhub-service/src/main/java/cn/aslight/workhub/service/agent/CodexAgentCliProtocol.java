package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.service.ai.CliTaskConversation;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 数字员工使用原生 CLI 完整能力，并追加 WorkHub MCP。 */
@Component
public class CodexAgentCliProtocol implements AgentCliProtocol {
    private final ObjectMapper json;
    public CodexAgentCliProtocol(ObjectMapper json) { this.json = json; }

    @Override
    public boolean supports(AiProviderConfigEntity provider) {
        if (provider.getCliType() != null) return "CODEX".equals(provider.getCliType());
        if (provider.getCliCommand() == null || provider.getCliCommand().isBlank()) return false;
        String name = Path.of(provider.getCliCommand()).getFileName().toString();
        return name.equals("codex") || name.startsWith("codex-");
    }

    @Override
    public void preflight(AiProviderConfigEntity provider) throws IOException, InterruptedException {
        String executable = executable(provider.getCliCommand());
        String help = inspect(List.of(executable, "exec", "--help"));
        for (String required : List.of("--sandbox", "--output-schema", "--output-last-message", "--json", "--ephemeral")) {
            if (!help.contains(required)) throw new IllegalArgumentException("当前 Codex CLI 不支持任务运行参数 " + required + "；未自动升级");
        }
    }

    public List<String> command(AiProviderConfigEntity provider, String model, Path directory, Path schema, Path output, String mcpUrl) {
        var args = new ArrayList<>(List.of(executable(provider.getCliCommand()), "exec",
                "--ephemeral", "--skip-git-repo-check", "--sandbox", "danger-full-access", "--json", "--color", "never",
                "-C", directory.toString(), "--model", model, "--output-schema", schema.toString(), "--output-last-message", output.toString()));
        config(args, "approval_policy", "never");
        config(args, "web_search", "live");
        if (mcpUrl != null) {
            // 平台标准 MCP 入口；AI 依据职责描述自行选择 WorkHub 能力，授权由 MCP 侧策略与审计约束。
            config(args, "mcp_servers.workhub_task.url", mcpUrl);
            config(args, "mcp_servers.workhub_task.bearer_token_env_var", "WORKHUB_AGENT_RUN_TOKEN");
            config(args, "mcp_servers.workhub_task.default_tools_approval_mode", "approve");
            args.addAll(List.of("-c", "mcp_servers.workhub_task.required=true", "-c", "mcp_servers.workhub_task.tool_timeout_sec=300"));
        }
        if (provider.getDefaultReasoningLevel() != null && !provider.getDefaultReasoningLevel().isBlank()) {
            config(args, "model_reasoning_effort", provider.getDefaultReasoningLevel());
        }
        args.add("-");
        return List.copyOf(args);
    }

    @Override
    public cn.aslight.workhub.service.ai.CliTaskLaunch prepare(AiProviderConfigEntity provider, String model, Path directory, Path schema, Path output,
                                         String mcpUrl, String token, String prompt) {
        var builder = new ProcessBuilder(command(provider, model, directory, schema, output, mcpUrl)).directory(directory.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        restrictEnvironment(builder.environment());
        if (token != null) builder.environment().put("WORKHUB_AGENT_RUN_TOKEN", token);
        return new cn.aslight.workhub.service.ai.CliTaskLaunch(builder, new CliTaskConversation() {
            public boolean unlimitedResultMessages() { return true; }
            public void start(java.io.OutputStream input) throws IOException {
                try (input) { input.write(prompt.getBytes(StandardCharsets.UTF_8)); }
            }
            public String accept(tools.jackson.databind.JsonNode event) {
                return "item.completed".equals(event.path("type").asText()) && "agent_message".equals(event.path("item").path("type").asText())
                        ? event.path("item").path("text").asText() : null;
            }
        });
    }

    static void restrictEnvironment(Map<String, String> environment) {
        // 只继承 CLI 认证/系统证书及现有沙箱约束；不把 WorkHub DB、Jenkins、Git 凭据传给模型进程。
        environment.keySet().removeIf(key -> !List.of("PATH", "HOME", "USER", "LANG", "LC_ALL", "TMPDIR", "CODEX_HOME",
                "SSL_CERT_FILE", "SSL_CERT_DIR", "NODE_EXTRA_CA_CERTS", "SSH_AUTH_SOCK").contains(key) && !key.startsWith("CODEX_SANDBOX"));
    }

    private void config(List<String> args, String key, String value) { args.addAll(List.of("-c", key + "=" + json.writeValueAsString(value))); }

    private static String executable(String command) {
        if (command == null || command.isBlank() || command.indexOf('\n') >= 0) throw new IllegalArgumentException("CLI 未配置可执行文件");
        Path path = Path.of(command);
        if (!path.isAbsolute()) {
            if (!command.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("CLI 命令只能是单个程序名或绝对路径，不接受 shell 表达式");
            String search = System.getenv("PATH");
            if (search != null) for (String folder : search.split(java.io.File.pathSeparator)) {
                if (folder.isBlank()) continue;
                Path candidate = Path.of(folder).resolve(command);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate.toAbsolutePath().toString();
            }
            throw new IllegalArgumentException("未找到已安装的 CLI 程序: " + command);
        }
        if (!Files.isRegularFile(path) || !Files.isExecutable(path)) throw new IllegalArgumentException("CLI 程序不存在或不可执行");
        return path.toString();
    }

    private static String inspect(List<String> command) throws IOException, InterruptedException {
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        restrictEnvironment(builder.environment());
        Process process = builder.start();
        // help/features 输出有界；并发排空，避免管道填满造成假超时。
        var bytes = new java.io.ByteArrayOutputStream();
        var reader = Thread.ofVirtual().start(() -> {
            try (var input = process.getInputStream()) { bytes.write(input.readNBytes(65537)); }
            catch (IOException ignored) { }
        });
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IllegalArgumentException("CLI 能力预检超时");
            reader.join(1000);
            if (process.exitValue() != 0 || reader.isAlive() || bytes.size() > 65536) throw new IllegalArgumentException("CLI 能力预检失败");
            return bytes.toString(StandardCharsets.UTF_8);
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
