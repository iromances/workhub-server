package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentScope;
import cn.aslight.workhub.service.system.SysConfigService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** 管理员通过既有系统配置明确仓库授权；模型不能提交宿主目录或覆盖执行策略。 */
@Service
public class AgentCodePolicy {
    private final SysConfigService configs;
    private final ObjectMapper json;

    public AgentCodePolicy(SysConfigService configs, ObjectMapper json) { this.configs = configs; this.json = json; }

    public Snapshot current() {
        String raw = configs.findPlainValue("agent.code", "policy");
        if (raw == null) throw new Unavailable("尚未配置 Agent 代码工作区和仓库绑定（agent.code/policy）");
        var policy = json.readValue(raw, Policy.class);
        absolute(policy.workspaceRoot());
        if (policy.repositories() == null || policy.repositories().isEmpty() || policy.repositories().size() > 200) {
            throw new IllegalArgumentException("代码策略需要 1 到 200 个明确的仓库绑定");
        }
        var ids = new HashSet<String>();
        for (var repo : policy.repositories()) {
            if (repo.id() == null || !repo.id().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}") || !ids.add(repo.id())) throw new IllegalArgumentException("代码仓库标识非法或重复");
            if (repo.name() == null || repo.name().isBlank() || repo.name().length() > 128) throw new IllegalArgumentException("仓库名称不能为空或超过128字");
            absolute(repo.sourcePath());
            if (repo.scope() == null) throw new IllegalArgumentException("仓库必须绑定业务线和环境");
            branch(repo.baseBranch());
            prefixes(repo.readablePaths());
            prefixes(repo.writablePaths());
            for (String path : repo.writablePaths()) {
                if (!within(path, repo.readablePaths())) throw new IllegalArgumentException("可写路径必须在可读范围内");
            }
        }
        return new Snapshot(policy, hash(json.writeValueAsString(policy)));
    }

    static Path absolute(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("仓库策略目录不能为空");
        Path path = Path.of(value);
        if (!path.isAbsolute() || !path.normalize().equals(path) || path.getNameCount() < 2) throw new IllegalArgumentException("仓库策略目录必须为明确的绝对路径");
        return path;
    }

    static void branch(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,199}") || value.contains("..")
                || value.contains("//") || value.endsWith("/") || value.endsWith(".") || value.endsWith(".lock")
                || Arrays.stream(value.split("/")).anyMatch(part -> part.startsWith("."))) throw new IllegalArgumentException("基线分支名称非法");
    }

    private static void prefixes(List<String> values) {
        if (values == null || values.size() > 100 || new HashSet<>(values).size() != values.size()) throw new IllegalArgumentException("代码路径范围缺失、重复或过多");
        for (String value : values) if (!".".equals(value)) path(value);
    }

    static String path(String value) {
        if (value == null || value.isBlank() || value.length() > 500 || value.startsWith("/") || value.startsWith("-")
                || value.contains("\\") || value.chars().anyMatch(c -> c < 32 || c == 127)) throw new IllegalArgumentException("代码路径非法");
        for (String part : value.split("/", -1)) {
            if (part.isBlank() || part.equals(".") || part.equals("..") || part.equalsIgnoreCase(".git")) throw new IllegalArgumentException("代码路径越界");
        }
        return value;
    }

    static boolean within(String path, List<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> ".".equals(prefix) || path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    static boolean sensitive(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        for (String part : lower.split("/")) {
            if (Set.of(".git", ".ssh", ".aws", ".gnupg", ".codex", ".npmrc", ".netrc", "credentials", "credentials.json", "settings.xml").contains(part)
                    || part.equals(".env") || part.startsWith(".env.") || part.endsWith(".pem") || part.endsWith(".key")
                    || part.endsWith(".p12") || part.endsWith(".pfx") || part.startsWith("id_rsa") || part.startsWith("id_ed25519")
                    || part.matches("application[-.]prod(uction)?[.].*")) return true;
        }
        return false;
    }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public record Policy(String workspaceRoot, List<Repository> repositories) { }
    public record Repository(String id, String name, AgentScope scope, String sourcePath, String baseBranch,
                              List<String> readablePaths, List<String> writablePaths, boolean enabled) { }
    public record Snapshot(Policy policy, String hash) {
        Repository require(String id) {
            return policy.repositories().stream().filter(repo -> repo.enabled() && repo.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("仓库不存在或未启用"));
        }
    }
    public static class Unavailable extends IllegalStateException { public Unavailable(String message) { super(message); } }
}
