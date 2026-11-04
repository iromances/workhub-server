package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentCodeMapper;
import cn.aslight.workhub.model.agent.AgentCodeWorkspaceEntity;
import cn.aslight.workhub.model.agent.AgentRepositoryChange;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.*;

/** API 与原生 CLI 共享的仓库读写服务；不会进入源工作树或执行模型提供的命令。 */
@Service
public class AgentCodeWorkspaceService {
    private final AgentCodePolicy policies;
    private final AgentCodeMapper mapper;
    private final AgentCodeStore store;
    private final AgentRunStore runs;
    private final AgentAccessService access;
    private final AgentCodeGit git;
    private final ObjectMapper json;
    private final cn.aslight.workhub.mcp.security.ServerFileContentMasker codeMasker = new cn.aslight.workhub.mcp.security.ServerFileContentMasker();

    public AgentCodeWorkspaceService(AgentCodePolicy policies, AgentCodeMapper mapper, AgentCodeStore store,
                                     AgentRunStore runs, AgentAccessService access, AgentCodeGit git, ObjectMapper json) {
        this.policies = policies; this.mapper = mapper; this.store = store; this.runs = runs;
        this.access = access; this.git = git; this.json = json;
    }

    public Object repositories(AgentToolContext context) {
        active(context);
        var policy = policies.current();
        var result = new ArrayList<Object>();
        for (var repo : policy.policy().repositories()) {
            if (!repo.enabled() || !context.specification().scopes().contains(repo.scope())) continue;
            access.requireScope(context, repo.scope());
            var view = new LinkedHashMap<String, Object>();
            view.put("repositoryId", repo.id()); view.put("name", repo.name()); view.put("scope", repo.scope());
            view.put("baseBranch", repo.baseBranch()); view.put("readablePaths", repo.readablePaths()); view.put("writablePaths", repo.writablePaths());
            try { view.put("baseSha", git.baseSha(Path.of(repo.sourcePath()), repo.baseBranch())); view.put("state", "READY"); }
            catch (IllegalArgumentException ex) { view.put("state", "UNAVAILABLE"); view.put("reason", ex.getMessage()); }
            result.add(view);
        }
        return Map.of("repositories", result, "policyHash", policy.hash(), "coverage", result.isEmpty() ? "NO_AUTHORIZED_REPOSITORY" : "CONFIGURED");
    }

    public Object open(AgentToolContext context, String repositoryId, String baseSha) {
        active(context);
        AgentCodeGit.sha(baseSha);
        var policy = policies.current();
        var repo = policy.require(repositoryId);
        access.requireScope(context, repo.scope());
        var workspace = store.reserve(context, policy, repo, baseSha, host());
        Path directory = git.directory(policy, workspace.id());
        return git.locked(directory, () -> {
            active(context);
            git.prepare(directory, Path.of(repo.sourcePath()), repo.baseBranch(), baseSha);
            active(context);
            var ready = store.ready(context, workspace.id());
            git.retain(directory, ready.candidateSha(), ready.version());
            return view(ready);
        });
    }

    public Object status(AgentToolContext context) {
        active(context);
        var policy = policies.current();
        var result = new ArrayList<Object>();
        for (var workspace : mapper.list(context.runId())) {
            var repo = policy.require(workspace.repositoryId());
            access.requireScope(context, repo.scope());
            var view = new LinkedHashMap<>(view(workspace));
            view.put("policyCurrent", workspace.policyHash().equals(policy.hash()));
            view.put("executorAvailable", workspace.hostId().equals(host()));
            result.add(view);
        }
        return Map.of("workspaces", result, "tested", false, "delivered", false);
    }

    public Object list(AgentToolContext context, String id, String prefix, int offset) {
        var resolved = resolve(context, id);
        if (prefix != null && !prefix.isBlank()) AgentCodePolicy.path(prefix);
        var all = visible(resolved).stream().filter(entry -> prefix == null || prefix.isBlank() || entry.path().startsWith(prefix)).toList();
        return Map.of("workspace", view(resolved.workspace()), "total", all.size(), "offset", offset,
                "files", all.stream().skip(offset).limit(100).map(entry -> Map.of("path", entry.path(), "blob", entry.blob(), "mode", entry.mode())).toList());
    }

    public Object read(AgentToolContext context, String id, String path) {
        return read(context, id, path, 0, 16000);
    }

    public Object read(AgentToolContext context, String id, String path, int offset, int limit) {
        if (offset < 0 || limit < 100 || limit > 16000) throw new IllegalArgumentException("代码读取分页范围非法");
        var resolved = resolve(context, id);
        requirePath(resolved, path, false);
        var entry = entries(resolved).stream().filter(item -> item.path().equals(path)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
        String raw = git.read(resolved.directory(), entry);
        // 先对完整文本脱敏再分页，避免凭据被拆到两页后绕过识别。超长文本的脱敏输出不走通用 JSON 截断。
        String safe = codeMasker.mask(raw);
        if (offset > safe.length() || offset > 0 && offset < safe.length() && Character.isLowSurrogate(safe.charAt(offset))) throw new IllegalArgumentException("代码读取偏移无效");
        int end = Math.min(safe.length(), offset + limit);
        if (end > offset && end < safe.length() && Character.isHighSurrogate(safe.charAt(end - 1))) end--;
        return Map.of("workspaceId", id, "version", resolved.workspace().version(), "path", path, "blob", entry.blob(),
                "content", safe.substring(offset, end), "contentMasked", !raw.equals(safe), "nextOffset", end, "totalChars", safe.length(), "truncated", end < safe.length());
    }

    public Object search(AgentToolContext context, String id, String text, int offset) {
        if (text == null || text.isEmpty() || text.length() > 200) throw new IllegalArgumentException("检索文本应为1到200字");
        var resolved = resolve(context, id);
        var files = visible(resolved);
        var matches = new ArrayList<Object>();
        var skipped = new ArrayList<String>();
        int scanned = 0;
        for (var entry : files.stream().skip(offset).limit(100).toList()) {
            active(context);
            scanned++;
            String content;
            try { content = git.read(resolved.directory(), entry); }
            catch (IllegalArgumentException ex) { skipped.add(entry.path()); continue; }
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length && matches.size() < 100; i++) {
                if (lines[i].contains(text)) matches.add(Map.of("path", entry.path(), "line", i + 1, "text", lines[i].substring(0, Math.min(lines[i].length(), 1000))));
            }
            if (matches.size() >= 100) break;
        }
        return Map.of("workspaceId", id, "version", resolved.workspace().version(), "matches", matches, "skippedFiles", skipped,
                "scannedFiles", scanned, "nextOffset", offset + scanned, "hasMore", offset + scanned < files.size(), "truncated", matches.size() >= 100);
    }

    public Object write(AgentToolContext context, String id, long version, String path, String expectedBlob,
                         String content, String requestKey, boolean delete) {
        return mutate(context, id, version, path, expectedBlob, new Edit(delete ? "DELETE" : "WRITE", content, null, null), requestKey);
    }

    public Object replace(AgentToolContext context, String id, long version, String path, String expectedBlob,
                           String oldText, String newText, String requestKey) {
        if (oldText == null || oldText.isEmpty() || oldText.length() > 16000 || newText == null || newText.length() > 16000) throw new IllegalArgumentException("局部替换需要1到16000字的唯一原文和不超过16000字的新文本");
        return mutate(context, id, version, path, expectedBlob, new Edit("REPLACE", null, oldText, newText), requestKey);
    }

    private Object mutate(AgentToolContext context, String id, long version, String path, String expectedBlob, Edit edit, String requestKey) {
        boolean delete = "DELETE".equals(edit.type());
        boolean replace = "REPLACE".equals(edit.type());
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("代码操作必须提供唯一请求标识");
        if (version < 0) throw new IllegalArgumentException("工作区版本非法");
        if (!delete && !replace && (edit.content() == null || edit.content().indexOf('\0') >= 0)) throw new IllegalArgumentException("代码写入内容非法");
        var initial = resolve(context, id);
        String requestHash = AgentCodePolicy.hash(json.writeValueAsString(new MutationIntent(version, path, expectedBlob, edit)));
        return git.locked(initial.directory(), () -> {
            var resolved = resolve(context, id);
            requirePath(resolved, path, true);
            var previous = mapper.operation(id, requestKey);
            if (previous != null) return store.previous(previous, requestHash);
            var workspace = resolved.workspace();
            if (workspace.version() != version) throw new IllegalArgumentException("工作区版本已变化，请重新读取后修改");
            var tree = entries(resolved);
            var entry = tree.stream().filter(item -> item.path().equals(path)).findFirst().orElse(null);
            if (entry != null && !entry.regular()) throw new IllegalArgumentException("不能修改软链接或子模块");
            for (var other : tree) {
                if (path.startsWith(other.path() + "/") || other.path().startsWith(path + "/")) throw new IllegalArgumentException("不能穿越文件、软链接或覆盖目录");
            }
            String actualBlob = entry == null ? "MISSING" : entry.blob();
            if (!Objects.equals(expectedBlob, actualBlob)) throw new IllegalArgumentException("文件内容已变化，请按最新 blob 修改");
            if (delete && entry == null) throw new IllegalArgumentException("不能删除不存在的文件");
            String content = edit.content();
            if (replace) {
                if (entry == null) throw new IllegalArgumentException("局部替换的文件不存在");
                String original = git.read(resolved.directory(), entry);
                int first = original.indexOf(edit.oldText());
                if (first < 0 || first != original.lastIndexOf(edit.oldText())) throw new IllegalArgumentException("原文必须恰好出现一次；请重新读取目标片段");
                if (edit.newText().indexOf('\0') >= 0) throw new IllegalArgumentException("不能写入二进制内容");
                content = original.substring(0, first) + edit.newText() + original.substring(first + edit.oldText().length());
            } else if (!delete && entry != null) {
                String original = git.read(resolved.directory(), entry);
                if (!original.equals(codeMasker.mask(original))) {
                    throw new IllegalArgumentException("原文件含脱敏内容，不能整文件覆盖；请使用唯一原文的局部替换以保留未暴露内容");
                }
            }
            active(context);
            String candidate = git.revise(resolved.directory(), workspace.candidateSha(), workspace.baseSha(), path,
                    content, entry == null ? "100644" : entry.mode(), "WorkHub Agent task " + context.taskId() + " revision " + (version + 1),
                    workspace.createdAt().toEpochSecond(ZoneOffset.UTC) + version + 1);
            active(context);
            String blob = delete ? "MISSING" : git.entries(resolved.directory(), candidate).stream().filter(item -> item.path().equals(path)).findFirst().orElseThrow().blob();
            // ref 仅保留不可变候选对象；SQL 版本是工作区权威状态，CAS 失败时不能采用此候选。
            git.retain(resolved.directory(), candidate, version + 1);
            return store.revise(context, workspace, requestKey, requestHash, candidate, path, blob);
        });
    }

    public Object diff(AgentToolContext context, String id) {
        var resolved = resolve(context, id);
        var paths = changedPaths(resolved);
        String diff = git.diff(resolved.directory(), resolved.workspace().baseSha(), resolved.workspace().candidateSha(), paths);
        boolean truncated = diff.length() > 32_000;
        return Map.of("workspace", view(resolved.workspace()), "changedPaths", paths, "diff", diff.substring(0, Math.min(32_000, diff.length())), "truncated", truncated);
    }

    /** 候选清单的来源只能是实际对象库；注册清单时另以整组 SQL 版本核对。 */
    public AgentRepositoryChange snapshot(AgentToolContext context, String id) {
        var resolved = resolve(context, id);
        var workspace = resolved.workspace();
        if (!git.baseSha(Path.of(resolved.repo().sourcePath()), workspace.baseBranch()).equals(workspace.baseSha())) {
            throw new IllegalArgumentException("源分支基线已变化，请以新基线重新分派并验证，不能直接使用旧候选交付");
        }
        var paths = changedPaths(resolved);
        String raw = git.diff(resolved.directory(), workspace.baseSha(), workspace.candidateSha(), paths);
        String safe = codeMasker.mask(raw);
        active(context);
        return new AgentRepositoryChange(workspace.repositoryId(), resolved.repo().name(), resolved.repo().scope(), id, workspace.version(),
                workspace.policyHash(), workspace.baseBranch(), workspace.baseSha(), workspace.sourceBranch(), workspace.candidateSha(),
                paths, AgentCodePolicy.hash(raw), safe.substring(0, Math.min(16_000, safe.length())), safe.length() > 16_000);
    }

    private List<String> changedPaths(Resolved resolved) {
        var current = entries(resolved);
        var base = git.entries(resolved.directory(), resolved.workspace().baseSha());
        var baseBlobs = new HashMap<String, String>();
        base.forEach(entry -> baseBlobs.put(entry.path(), entry.mode() + ":" + entry.blob()));
        var currentBlobs = new HashMap<String, String>();
        current.forEach(entry -> currentBlobs.put(entry.path(), entry.mode() + ":" + entry.blob()));
        var paths = new TreeSet<String>(); paths.addAll(baseBlobs.keySet()); paths.addAll(currentBlobs.keySet());
        paths.removeIf(path -> Objects.equals(baseBlobs.get(path), currentBlobs.get(path)));
        if (paths.size() > 100) throw new IllegalArgumentException("单个仓库变更超过100个文件，请拆分任务");
        paths.forEach(path -> requirePath(resolved, path, true));
        return List.copyOf(paths);
    }

    public Path deliveryDirectory(AgentToolContext context, AgentRepositoryChange expected) {
        var resolved = resolve(context, expected.workspaceId());
        if (!AgentChangeStore.matches(resolved.workspace(), expected)) throw new IllegalArgumentException("候选代码已变化，不能发布旧工作区版本");
        if (!git.baseSha(Path.of(resolved.repo().sourcePath()), resolved.repo().baseBranch()).equals(expected.baseSha())) {
            throw new IllegalArgumentException("源分支已变化，请基于新版本重新规划");
        }
        return resolved.directory();
    }

    private Resolved resolve(AgentToolContext context, String id) {
        active(context);
        var workspace = mapper.find(id);
        if (workspace == null || !workspace.runId().equals(context.runId())) throw new IllegalArgumentException("工作区不属于本次运行");
        var policy = policies.current();
        if (!policy.hash().equals(workspace.policyHash())) throw new IllegalArgumentException("仓库策略已变化，请重新分派；旧工作区未继续使用");
        if (!host().equals(workspace.hostId())) throw new AgentCodePolicy.Unavailable("工作区位于其他执行主机，需要原执行机恢复");
        if (!"READY".equals(workspace.status())) throw new AgentCodePolicy.Unavailable("工作区尚未准备完成，请重试打开同一仓库和基线");
        var repo = policy.require(workspace.repositoryId());
        access.requireScope(context, repo.scope());
        return new Resolved(workspace, repo, git.directory(policy, id));
    }

    private void requirePath(Resolved resolved, String path, boolean write) {
        AgentCodePolicy.path(path);
        if (AgentCodePolicy.sensitive(path) || !AgentCodePolicy.within(path, resolved.repo().readablePaths())
                || write && !AgentCodePolicy.within(path, resolved.repo().writablePaths())) throw new IllegalArgumentException("文件超出已授权路径或属于敏感配置");
    }
    private List<AgentCodeGit.Entry> entries(Resolved resolved) { return git.entries(resolved.directory(), resolved.workspace().candidateSha()); }
    private List<AgentCodeGit.Entry> visible(Resolved resolved) {
        return entries(resolved).stream().filter(AgentCodeGit.Entry::regular).filter(entry -> !AgentCodePolicy.sensitive(entry.path())
                && AgentCodePolicy.within(entry.path(), resolved.repo().readablePaths())).toList();
    }
    private void active(AgentToolContext context) { runs.requireActive(context.runId(), context.ownerToken()); }
    static Map<String, Object> view(AgentCodeWorkspaceEntity value) {
        return Map.of("workspaceId", value.id(), "repositoryId", value.repositoryId(), "baseBranch", value.baseBranch(),
                "baseSha", value.baseSha(), "candidateSha", value.candidateSha(), "sourceBranch", value.sourceBranch(),
                "status", value.status(), "version", value.version(), "tested", false, "delivered", false);
    }
    private static String host() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (UnknownHostException ex) { throw new AgentCodePolicy.Unavailable("无法确定代码执行主机身份"); }
    }
    private record Resolved(AgentCodeWorkspaceEntity workspace, AgentCodePolicy.Repository repo, Path directory) { }
    private record Edit(String type, String content, String oldText, String newText) { }
    private record MutationIntent(long version, String path, String expectedBlob, Edit edit) { }
}
