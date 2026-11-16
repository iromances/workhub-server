package cn.aslight.workhub.service.agent;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Git 对象操作不 checkout，不执行 hooks/过滤器，不接受 shell；用户的源仓库永远只读。 */
@Component
public class AgentCodeGit {
    static final int MAX_FILE_BYTES = 131_072;
    private static final int MAX_OUTPUT = 4 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(45);

    public String baseSha(Path source, String branch) {
        requireSource(source);
        AgentCodePolicy.branch(branch);
        return sha(run(source, Map.of(), null, "rev-parse", "--verify", "refs/heads/" + branch + "^{commit}").trim());
    }

    public Path directory(AgentCodePolicy.Snapshot policy, String id) {
        if (!id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("工作区标识非法");
        Path root = AgentCodePolicy.absolute(policy.policy().workspaceRoot());
        noSymlinks(root);
        for (var repo : policy.policy().repositories()) {
            Path source = AgentCodePolicy.absolute(repo.sourcePath());
            if (root.startsWith(source) || source.startsWith(root)) throw new IllegalArgumentException("Agent 工作区必须独立于源仓库");
        }
        for (Path parent = root; parent != null; parent = parent.getParent()) {
            if (Files.exists(parent.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Agent 工作区不能位于任何源码仓库内部");
        }
        try {
            Files.createDirectories(root);
            Path directory = root.resolve(id);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            noSymlinks(directory);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("工作区目录类型不符");
            return directory;
        } catch (IOException ex) { throw new IllegalStateException("无法创建受控代码工作区", ex); }
    }

    public <T> T locked(Path directory, Supplier<T> action) {
        Path path = directory.resolve("operation.lock");
        noSymlinks(path);
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            try (var lock = channel.tryLock()) {
                if (lock == null) throw new IllegalStateException("该代码工作区正在执行其他操作");
                return action.get();
            }
        } catch (java.nio.channels.OverlappingFileLockException ex) { throw new IllegalStateException("该代码工作区正在执行其他操作"); }
        catch (IOException ex) { throw new IllegalStateException("无法锁定受控代码工作区", ex); }
    }

    public void prepare(Path directory, Path source, String branch, String expectedSha) {
        requireSource(source);
        AgentCodePolicy.branch(branch);
        sha(expectedSha);
        Path repo = directory.resolve("objects.git");
        if (Files.exists(repo, LinkOption.NOFOLLOW_LINKS)) {
            requireBare(repo);
            if (!run(repo, Map.of(), null, "cat-file", "-t", expectedSha).trim().equals("commit")) throw new IllegalStateException("工作区缺少已登记基线");
            return;
        }
        if (!baseSha(source, branch).equals(expectedSha)) throw new IllegalArgumentException("源分支已变化，请重新读取基线 SHA 后分派任务");
        Path temporary = directory.resolve("prepare-" + UUID.randomUUID());
        boolean cleanupAllowed = true;
        try {
            // --no-local 避免共享对象和硬链接；depth=1 限制复制范围。源仅为已绑定本地目录。
            run(directory, Map.of(), null, "clone", "--bare", "--no-local", "--no-hardlinks", "--depth=1", "--single-branch",
                    "--no-tags", "--branch", branch, "--template=", "--", source.toString(), temporary.toString());
            requireBare(temporary);
            if (!run(temporary, Map.of(), null, "rev-parse", "HEAD").trim().equals(expectedSha)) throw new IllegalArgumentException("复制期间源分支发生变化，未采用其他基线");
            run(temporary, Map.of(), null, "remote", "remove", "origin");
            Files.move(temporary, repo, StandardCopyOption.ATOMIC_MOVE);
        } catch (UnknownGitProcess ex) { cleanupAllowed = false; throw ex; }
        catch (IOException ex) { throw new IllegalStateException("无法保存隔离 Git 对象库", ex); }
        finally { if (cleanupAllowed) removePrivateTree(temporary); }
    }

    public List<Entry> entries(Path directory, String commit) {
        Path repo = repository(directory);
        String listing = run(repo, Map.of(), null, "ls-tree", "-r", "-z", "--full-tree", sha(commit));
        var entries = new ArrayList<Entry>();
        for (String item : listing.split("\u0000")) {
            if (item.isEmpty()) continue;
            int tab = item.indexOf('\t');
            String[] meta = item.substring(0, tab).split(" ");
            entries.add(new Entry(meta[0], meta[1], meta[2], item.substring(tab + 1)));
        }
        return entries;
    }

    public String read(Path directory, Entry entry) {
        if (!entry.regular()) throw new IllegalArgumentException("不读取软链接、子模块或其他特殊文件");
        Path repo = repository(directory);
        long size = Long.parseLong(run(repo, Map.of(), null, "cat-file", "-s", sha(entry.blob())).trim());
        if (size > MAX_FILE_BYTES) throw new IllegalArgumentException("文件超过128KiB，请使用有界检索或拆分任务");
        String result = run(repo, Map.of(), null, "cat-file", "blob", entry.blob());
        if (result.indexOf('\0') >= 0) throw new IllegalArgumentException("不读取二进制文件");
        return result;
    }

    public String revise(Path directory, String previous, String baseSha, String path, String content,
                          String mode, String message, long timestamp) {
        Path repo = repository(directory);
        AgentCodePolicy.path(path);
        sha(previous); sha(baseSha);
        if (content != null && content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw new IllegalArgumentException("单次写入不能超过128KiB");
        Path index = directory.resolve("index-" + UUID.randomUUID());
        Map<String, String> environment = Map.of("GIT_INDEX_FILE", index.toString(), "GIT_AUTHOR_NAME", "Agent",
                "GIT_AUTHOR_EMAIL", "agent@noreply.invalid", "GIT_COMMITTER_NAME", "Agent", "GIT_COMMITTER_EMAIL", "agent@noreply.invalid",
                "GIT_AUTHOR_DATE", "@" + timestamp + " +0000", "GIT_COMMITTER_DATE", "@" + timestamp + " +0000");
        try {
            run(repo, environment, null, "read-tree", previous);
            if (content == null) run(repo, environment, "0 " + "0".repeat(previous.length()) + "\t" + path + "\0", "update-index", "-z", "--index-info");
            else {
                String blob = sha(run(repo, environment, content, "hash-object", "-w", "--stdin").trim());
                run(repo, environment, null, "update-index", "--add", "--cacheinfo", mode, blob, path);
            }
            String tree = sha(run(repo, environment, null, "write-tree").trim());
            return sha(run(repo, environment, message, "commit-tree", tree, "-p", baseSha).trim());
        } finally {
            try { Files.deleteIfExists(index); Files.deleteIfExists(Path.of(index + ".lock")); }
            catch (IOException ex) { throw new IllegalStateException("无法清理工作区临时索引", ex); }
        }
    }

    public String diff(Path directory, String base, String candidate, List<String> paths) {
        if (paths.isEmpty()) return "";
        var args = new ArrayList<>(List.of("diff", "--no-ext-diff", "--no-textconv", "--no-renames", "--no-color", "--unified=3", sha(base), sha(candidate), "--"));
        paths.forEach(AgentCodePolicy::path);
        args.addAll(paths);
        return run(repository(directory), Map.of(), null, args.toArray(String[]::new));
    }

    public void retain(Path directory, String sha, long version) {
        run(repository(directory), Map.of(), null, "update-ref", "refs/workhub/revisions/" + version, sha(sha));
    }

    /** 只创建新的交付分支；空期望值阻止覆盖已有远端引用，凭据不落文件或命令行。 */
    public void publish(Path directory, AgentGitLabClient.Target target, String branch, String candidate) {
        AgentCodePolicy.branch(branch); sha(candidate);
        if (!branch.matches("codex/agent-[a-f0-9-]{36}-r[1-9][0-9]{0,2}")) throw new IllegalArgumentException("只允许发布本平台生成的候选分支");
        String credentials = Base64.getEncoder().encodeToString(("oauth2:" + target.accessToken()).getBytes(StandardCharsets.UTF_8));
        var environment = Map.of("GIT_CONFIG_COUNT", "1", "GIT_CONFIG_KEY_0", "http." + target.repositoryUri() + ".extraHeader",
                "GIT_CONFIG_VALUE_0", "Authorization: Basic " + credentials);
        run(repository(directory), environment, null, "-c", "protocol.file.allow=never", "-c", "protocol.https.allow=always", "-c", "protocol.http.allow=always",
                "-c", "http.followRedirects=false", "-c", "http.sslVerify=true", "-c", "credential.helper=", "-c", "push.followTags=false",
                "push", "--porcelain", "--atomic", "--no-follow-tags", "--push-option=ci.skip", "--force-with-lease=refs/heads/" + branch + ":",
                "--", target.repositoryUri().toString(), candidate + ":refs/heads/" + branch);
    }

    private Path repository(Path directory) { Path repo = directory.resolve("objects.git"); requireBare(repo); return repo; }
    private void requireBare(Path path) {
        noSymlinks(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || !run(path, Map.of(), null, "rev-parse", "--is-bare-repository").trim().equals("true")) throw new IllegalStateException("隔离 Git 对象库不可用");
    }
    private void requireSource(Path source) {
        noSymlinks(source);
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("已绑定源仓库目录不存在");
        if (!run(source, Map.of(), null, "rev-parse", "--is-bare-repository").trim().equals("true")) {
            if (!Path.of(run(source, Map.of(), null, "rev-parse", "--show-toplevel").trim()).equals(source)) {
                throw new IllegalArgumentException("绑定的源目录必须是仓库根目录");
            }
        }
    }
    private static void noSymlinks(Path path) {
        for (Path part = path; part != null; part = part.getParent()) if (Files.isSymbolicLink(part)) throw new IllegalArgumentException("代码工作区路径不能经过软链接");
    }
    static String sha(String value) {
        if (value == null || !value.matches("[a-f0-9]{40}([a-f0-9]{24})?")) throw new IllegalArgumentException("必须使用完整提交或对象 SHA");
        return value;
    }

    private String run(Path directory, Map<String, String> extra, String input, String... args) {
        var command = new ArrayList<>(List.of("git", "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "core.pager=cat",
                "-c", "protocol.allow=never", "-c", "protocol.file.allow=always", "-c", "gc.auto=0", "-c", "maintenance.auto=false"));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().clear();
        builder.environment().putAll(Map.of("PATH", "/usr/bin:/bin:/usr/local/bin", "LANG", "C.UTF-8", "GIT_CONFIG_NOSYSTEM", "1",
                "GIT_CONFIG_GLOBAL", "/dev/null", "GIT_TERMINAL_PROMPT", "0", "GIT_NO_REPLACE_OBJECTS", "1", "GIT_OPTIONAL_LOCKS", "0"));
        builder.environment().putAll(extra);
        Process process = null;
        var children = new HashMap<Long, ProcessHandle>();
        var pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            process = builder.start();
            Process running = process;
            var stdout = pool.submit(() -> bounded(running.getInputStream(), MAX_OUTPUT));
            var stderr = pool.submit(() -> bounded(running.getErrorStream(), 16_384));
            var writer = pool.submit(() -> { try (var out = running.getOutputStream()) { if (input != null) out.write(input.getBytes(StandardCharsets.UTF_8)); } return null; });
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (running.isAlive()) {
                running.descendants().forEach(child -> children.put(child.pid(), child));
                if (System.nanoTime() > deadline) throw new IllegalStateException("Git 对象操作超时");
                running.waitFor(50, TimeUnit.MILLISECONDS);
            }
            writer.get(2, TimeUnit.SECONDS);
            byte[] output = stdout.get(2, TimeUnit.SECONDS);
            stderr.get(2, TimeUnit.SECONDS);
            if (running.exitValue() != 0) throw new IllegalArgumentException("Git 对象操作失败（" + args[0] + ", exit=" + running.exitValue() + "），请检查已绑定仓库及基线");
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output)).toString();
        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AgentRunStore.StopRequested("代码操作已中断"); }
        catch (IOException | ExecutionException | TimeoutException ex) { throw new IllegalStateException("受控 Git 操作未完成", ex); }
        finally {
            children.values().stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            if (process != null) {
                if (process.isAlive()) process.destroyForcibly();
                try { process.getInputStream().close(); process.getErrorStream().close(); process.getOutputStream().close(); }
                catch (IOException ignored) { /* 停止后的流关闭不改变已保存的执行结论。 */ }
            }
            pool.shutdownNow();
            boolean interrupted = Thread.interrupted();
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while ((process != null && process.isAlive() || children.values().stream().anyMatch(ProcessHandle::isAlive)) && System.nanoTime() < deadline) {
                    java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                    interrupted |= Thread.interrupted();
                }
                if (process != null && process.isAlive() || children.values().stream().anyMatch(ProcessHandle::isAlive)) {
                    throw new UnknownGitProcess("Git 进程树尚未确认停止，工作区保留待核实");
                }
            } finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
    }

    private static byte[] bounded(InputStream input, int limit) throws IOException {
        try (input; var buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            boolean exceeded = false;
            for (int size; (size = input.read(chunk)) >= 0;) {
                if (buffer.size() + size <= limit) buffer.write(chunk, 0, size); else exceeded = true;
            }
            if (exceeded) throw new IOException("Git 输出超过受控上限");
            return buffer.toByteArray();
        }
    }

    static void removePrivateTree(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ex) { throw new IllegalStateException("临时代码对象目录清理失败", ex); }
    }

    public record Entry(String mode, String type, String blob, String path) {
        boolean regular() { return "blob".equals(type) && ("100644".equals(mode) || "100755".equals(mode)); }
    }
    static final class UnknownGitProcess extends IllegalStateException { private UnknownGitProcess(String message) { super(message); } }
}
