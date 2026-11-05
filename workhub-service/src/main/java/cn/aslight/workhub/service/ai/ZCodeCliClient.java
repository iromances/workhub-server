package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 复用已安装 ZCode 和认证材料；不安装、升级、改写用户配置或套用 Codex 参数。 */
@Component
public class ZCodeCliClient {
    private final ObjectMapper json;
    public ZCodeCliClient(ObjectMapper json) { this.json = json; }

    public void preflight(AiProviderConfigEntity provider) throws IOException, InterruptedException {
        var command = new ArrayList<>(command(provider)); command.add("--help");
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        restrictEnvironment(builder.environment());
        Process process = builder.start(); var bytes = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var stream = process.getInputStream()) { bytes.write(stream.readNBytes(65537)); }
            catch (IOException ignored) { }
        });
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) throw new IllegalArgumentException("ZCode CLI 预检超时");
            reader.join(1000);
            if (reader.isAlive() || bytes.size() > 65536 || process.exitValue() != 0) throw new IllegalArgumentException("ZCode CLI 无法启动；CJS 分发需要已安装且支持 node:sqlite 的 Node 运行时");
            String help = bytes.toString(StandardCharsets.UTF_8);
            if (!help.matches("(?s)^zcode 0\\.17\\.[0-9]+(?:\\s.*)?") || !help.contains("app-server")) {
                throw new IllegalArgumentException("当前适配已核验 ZCode CLI 0.17 原生协议；所选版本未通过预检，未自动安装或升级");
            }
        } finally { stopTree(process, new LinkedHashMap<>()); }
    }

    public CliTaskLaunch prepare(AiProviderConfigEntity provider, String model, String reasoning, Path directory, Path output,
                          String prompt, List<String> toolNames, String mcpUrl, String token) throws IOException {
        return prepare(provider, model, reasoning, directory, output, prompt, toolNames, mcpUrl, token, null);
    }

    public CliTaskLaunch prepare(AiProviderConfigEntity provider, String model, String reasoning, Path directory, Path output,
                          String prompt, List<String> toolNames, String mcpUrl, String token, String finalResultSchema) throws IOException {
        if (!Set.of("Mac OS X", "Linux").contains(System.getProperty("os.name"))) throw new IllegalArgumentException("ZCode 任务隔离目前已适配 macOS/Linux");
        var selection = AiCliType.selection(model, reasoning);
        var command = new ArrayList<>(command(provider));
        Path taskProfile = privateDirectory(directory.resolve("profile"));
        Path configDirectory = privateDirectory(taskProfile.resolve(".zcode/cli"));
        Path storage = privateDirectory(directory.resolve("storage"));
        Path work = privateDirectory(directory.resolve("work"));
        // 独立目录作为项目边界，避免父目录的项目配置参与当前任务。
        privateDirectory(work.resolve(".git"));
        var config = Map.of("features", Map.of("mcp", mcpUrl != null, "memory", false, "subagent", false, "skill", false, "compact", false, "rewind", false),
                "memory", Map.of("use", false), "plugins", Map.of("enabled", false), "skills", Map.of("enabled", false, "includeInstructions", false),
                "hooks", Map.of("enabled", false), "mcp", Map.of("servers", Map.of()));
        privateFile(configDirectory.resolve("config.json"), json.writeValueAsBytes(config));
        Path builtin = resolveBuiltin(provider);
        Path personal = provider.getCliPersonalConfigPath() == null || provider.getCliPersonalConfigPath().isBlank()
                ? Path.of(System.getProperty("user.home"), ".zcode", "v2", "provider_config.json") : absolute(provider.getCliPersonalConfigPath());
        copyConfig(builtin, directory.resolve("builtin.json"), "ZCode 内置 Provider 定义");
        copyConfig(personal, directory.resolve("provider.json"), "ZCode 个人 Provider 配置");
        Path privateState = privateDirectory(taskProfile.resolve(".zcode/v2"));
        // 只复制选定配置旁的认证及设备状态；刷新与遥测写入只发生在本次私有目录。
        copyOptionalState(personal.getParent().resolve("credentials.json"), privateState.resolve("credentials.json"));
        copyOptionalState(personal.getParent().resolve("telemetry-state.json"), privateState.resolve("telemetry-state.json"));
        command.addAll(List.of("app-server", "--stdio", "--cwd", work.toString()));
        var builder = new ProcessBuilder(command).directory(work.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        restrictEnvironment(builder.environment());
        // 仅设置子进程的真实用户目录语义，不改 WorkHub 进程、用户 shell 或现有 ZCode 配置。
        builder.environment().put("HOME", taskProfile.toString());
        builder.environment().put("ZCODE_DATA_BASE_DIR", taskProfile.toString());
        builder.environment().put("ZCODE_BUILTIN_PROVIDER_CONFIG_FILE", directory.resolve("builtin.json").toString());
        builder.environment().put("ZCODE_PERSONAL_PROVIDER_CONFIG_FILE", directory.resolve("provider.json").toString());
        builder.environment().put("ZCODE_STORAGE_DIR", storage.toString());
        builder.environment().put("ZCODE_SESSION_DB_PATH", storage.resolve("session.db").toString());
        builder.environment().put("ZCODE_LOG_DIR", privateDirectory(directory.resolve("logs")).toString());
        builder.environment().put("ZCODE_MAX_TOOL_CONCURRENCY", "1");
        return new CliTaskLaunch(builder, new ZCodeTaskConversation(json, work, output, prompt, selection, toolNames, mcpUrl, token, finalResultSchema));
    }

    /** 既有文本整理/连接测试也走原生协议，但没有企业工具授权。 */
    public String execute(AiProviderConfigEntity provider, String model, String reasoning, String prompt, int seconds) throws IOException, InterruptedException {
        if (seconds < 1 || seconds > 14400 || prompt == null || prompt.length() > 180000) throw new IllegalArgumentException("ZCode 任务输入或时间预算无效");
        preflight(provider);
        Path directory = Files.createTempDirectory("workhub-zcode-");
        Process process = null; boolean stopped = false;
        var descendants = new LinkedHashMap<Long, ProcessHandle>();
        try {
            Path output = directory.resolve("result.json");
            var launch = prepare(provider, model, reasoning, directory, output, prompt, List.of(), null, null);
            process = launch.builder().start(); Process observed = process;
            var failure = new AtomicReference<Exception>();
            Thread writer = Thread.ofVirtual().start(() -> {
                try { launch.conversation().start(observed.getOutputStream()); }
                catch (Exception ex) { failure.compareAndSet(null, ex); }
            });
            Thread reader = Thread.ofVirtual().start(() -> {
                try { readProtocol(observed.getInputStream(), launch.conversation(), ignored -> { }); }
                catch (Exception ex) { failure.set(ex); }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (process.isAlive()) {
                process.descendants().forEach(child -> descendants.put(child.pid(), child));
                if (failure.get() != null) throw new IllegalStateException("ZCode 协议执行失败", failure.get());
                if (System.nanoTime() >= deadline) throw new IllegalStateException("ZCode CLI 调用超时");
                process.waitFor(100, TimeUnit.MILLISECONDS);
            }
            stopped = stopTree(process, descendants); reader.join(1000); writer.join(1000);
            if (!stopped || reader.isAlive() || writer.isAlive() || failure.get() != null || process.exitValue() != 0) throw new IllegalStateException("ZCode CLI 未返回有效终态", failure.get());
            if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS) || Files.size(output) > 24000) throw new IllegalStateException("ZCode CLI 缺少最终产物");
            return Files.readString(output);
        } finally {
            boolean interrupted = Thread.interrupted();
            if (process != null && !stopped) stopped = stopTree(process, descendants);
            if (process == null || stopped) delete(directory);
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    public static void readProtocol(InputStream input, CliTaskConversation conversation, java.util.function.Consumer<String> progress) throws IOException {
        var json = new ObjectMapper();
        try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] chars = new char[2048]; var line = new StringBuilder(); int total = 0, count;
            while ((count = reader.read(chars)) >= 0) {
                total += count;
                if (total > 4_000_000) throw new IOException("CLI 协议输出超过上限");
                for (int i = 0; i < count; i++) {
                    if (chars[i] == '\n') {
                        if (!line.isEmpty()) {
                            String text = conversation.accept(json.reader().with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(line.toString()));
                            if (text != null) progress.accept(text);
                        }
                        line.setLength(0);
                    } else if (line.length() < 524288) line.append(chars[i]);
                    else throw new IOException("CLI 协议单条消息超过上限");
                }
            }
            if (!line.isEmpty()) throw new IOException("CLI 协议消息不完整");
        }
    }

    private List<String> command(AiProviderConfigEntity provider) {
        String command = provider.getCliCommand();
        if (command != null && command.endsWith(".cjs")) {
            Path file = absolute(command);
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw new IllegalArgumentException("ZCode CLI 文件不存在或不可读");
            return List.of(executable(provider.getCliRuntimeCommand() == null || provider.getCliRuntimeCommand().isBlank() ? "node" : provider.getCliRuntimeCommand()), file.toString());
        }
        return List.of(executable(command));
    }
    private Path resolveBuiltin(AiProviderConfigEntity provider) {
        if (provider.getCliBuiltinConfigPath() != null && !provider.getCliBuiltinConfigPath().isBlank()) return absolute(provider.getCliBuiltinConfigPath());
        Path command = Path.of(provider.getCliCommand());
        if (command.isAbsolute() && command.getParent() != null && command.getParent().getFileName().toString().equals("glm")) {
            Path candidate = command.getParent().getParent().resolve("config/provider/zcode-builtin.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalArgumentException("请配置已安装 ZCode 分发包的内置 Provider 定义文件路径");
    }
    private void copyConfig(Path source, Path target, String label) throws IOException {
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.size(source) > 2_000_000) throw new IllegalArgumentException(label + "不存在、为链接或超过上限");
        byte[] raw = Files.readAllBytes(source);
        var config = readConfig(raw, label);
        if (!config.isObject() || config.path("schemaVersion").asInt() != 1 || !config.path("config").isObject()) throw new IllegalArgumentException(label + "格式不兼容");
        privateFile(target, raw);
    }
    private void copyOptionalState(Path source, Path target) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.size(source) > 1_000_000) throw new IllegalArgumentException("ZCode 认证或设备状态文件不是有效普通文件");
        byte[] raw = Files.readAllBytes(source);
        if (!readConfig(raw, "ZCode 认证或设备状态").isObject()) throw new IllegalArgumentException("ZCode 认证或设备状态格式不兼容");
        privateFile(target, raw);
    }
    private tools.jackson.databind.JsonNode readConfig(byte[] raw, String label) {
        try { return json.reader().with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(label + "不是有效 JSON，请在 ZCode 中修复配置"); }
    }
    private static Path absolute(String text) {
        if (text == null || text.isBlank() || text.indexOf('\n') >= 0 || text.indexOf('\0') >= 0) throw new IllegalArgumentException("ZCode 路径无效");
        Path path = Path.of(text); if (!path.isAbsolute()) throw new IllegalArgumentException("ZCode 配置及 CJS 文件需使用绝对路径");
        return path.normalize();
    }
    static String executable(String command) {
        if (command == null || command.isBlank() || command.indexOf('\n') >= 0) throw new IllegalArgumentException("CLI 程序未配置");
        Path path = Path.of(command);
        if (path.isAbsolute()) {
            if (!Files.isRegularFile(path) || !Files.isExecutable(path)) throw new IllegalArgumentException("CLI 程序不存在或不可执行");
            return path.toString();
        }
        if (!command.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("CLI 程序必须是单个名称或绝对路径，不接受 shell 表达式");
        for (String folder : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
            if (folder.isBlank()) continue;
            Path candidate = Path.of(folder).resolve(command);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate.toAbsolutePath().toString();
        }
        throw new IllegalArgumentException("未找到已安装的 CLI 程序");
    }
    static void restrictEnvironment(Map<String, String> environment) {
        environment.keySet().removeIf(key -> !Set.of("PATH", "HOME", "USER", "LANG", "LC_ALL", "TMPDIR", "SSL_CERT_FILE", "SSL_CERT_DIR", "NODE_EXTRA_CA_CERTS").contains(key)
                && !key.startsWith("CODEX_SANDBOX"));
    }
    private static Path privateDirectory(Path path) throws IOException { Files.createDirectories(path); Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------")); return path; }
    private static void privateFile(Path path, byte[] bytes) throws IOException {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(path, bytes);
    }
    public static boolean stopTree(Process process, Map<Long, ProcessHandle> descendants) {
        process.descendants().forEach(child -> descendants.put(child.pid(), child));
        descendants.values().forEach(child -> { if (child.isAlive()) child.destroy(); });
        if (process.isAlive()) process.destroy();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        do {
            descendants.values().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
            if (process.isAlive()) process.destroyForcibly();
            if (!process.isAlive() && descendants.values().stream().noneMatch(ProcessHandle::isAlive)) return true;
            try { Thread.sleep(20); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
        } while (System.nanoTime() < deadline);
        return false;
    }
    public static void delete(Path directory) {
        try (var paths = Files.walk(directory)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }
}
