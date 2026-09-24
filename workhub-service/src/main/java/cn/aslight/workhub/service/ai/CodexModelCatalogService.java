package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.CodexModelCatalogRequest;
import cn.aslight.workhub.model.ai.CodexModelCatalogResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class CodexModelCatalogService {
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final int CACHE_LIMIT = 32;
    private final CodexModelCatalogClient client;
    private final Clock clock;
    private final Map<CatalogKey, CodexModelCatalogResponse> cache = new LinkedHashMap<>();
    private final Map<CatalogKey, CompletableFuture<CodexModelCatalogResponse>> inFlight = new HashMap<>();

    @Autowired
    public CodexModelCatalogService(CodexModelCatalogClient client) {
        this(client, Clock.systemUTC());
    }

    CodexModelCatalogService(CodexModelCatalogClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public CodexModelCatalogResponse query(CodexModelCatalogRequest request) {
        CatalogKey key = resolve(request);
        CompletableFuture<CodexModelCatalogResponse> pending;
        boolean owner;
        synchronized (cache) {
            CodexModelCatalogResponse saved = cache.get(key);
            if (!Boolean.TRUE.equals(request.forceRefresh()) && saved != null
                    && clock.instant().isBefore(saved.fetchedAt().plus(TTL))) {
                return new CodexModelCatalogResponse(saved.models(), saved.fetchedAt(), true);
            }
            pending = inFlight.get(key);
            owner = pending == null;
            if (owner) {
                if (inFlight.size() >= 4) throw new IllegalStateException("Codex 模型查询繁忙，请稍后重试");
                pending = new CompletableFuture<>();
                inFlight.put(key, pending);
                cache.remove(key);
            }
        }
        if (!owner) return await(pending);
        try {
            CodexModelCatalogResponse result = new CodexModelCatalogResponse(
                    client.query(key.executable(), key.directory()), clock.instant(), false);
            synchronized (cache) {
                if (cache.size() >= CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
                cache.put(key, result);
            }
            pending.complete(result);
            return result;
        } catch (RuntimeException ex) {
            pending.completeExceptionally(ex);
            throw ex;
        } finally {
            synchronized (cache) {
                inFlight.remove(key);
            }
        }
    }

    private CodexModelCatalogResponse await(CompletableFuture<CodexModelCatalogResponse> pending) {
        try {
            return pending.get(12, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("查询 Codex 模型已中断");
        } catch (TimeoutException ex) {
            throw new IllegalStateException("查询 Codex 模型超时，请重试");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException failure) throw failure;
            throw new IllegalStateException("查询 Codex 模型失败");
        }
    }

    private CatalogKey resolve(CodexModelCatalogRequest request) {
        if (request.cliCommand() == null || request.cliCommand().isBlank()) {
            throw new IllegalArgumentException("CLI 命令不能为空");
        }
        Path directory = request.cliWorkingDirectory() == null || request.cliWorkingDirectory().isBlank()
                ? Path.of(System.getProperty("user.dir")) : Path.of(request.cliWorkingDirectory().trim());
        if (!Files.isDirectory(directory)) throw new IllegalArgumentException("CLI 工作目录不存在");
        Path executable = Path.of(request.cliCommand().trim());
        String filename = executable.getFileName().toString();
        if (!filename.equals("codex") && !filename.equals("codex.exe")) {
            throw new IllegalArgumentException("模型查询仅支持 Codex 可执行文件，不支持 shell 命令或附加参数");
        }
        if (!executable.isAbsolute() && executable.getParent() == null) {
            executable = findOnPath(filename);
        } else if (!executable.isAbsolute()) {
            executable = directory.resolve(executable);
        }
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IllegalArgumentException("未找到可执行的 Codex，请检查 CLI 命令");
        }
        try {
            return new CatalogKey(executable.toRealPath(), directory.toRealPath());
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法访问 Codex 命令或工作目录");
        }
    }

    private Path findOnPath(String filename) {
        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (entry.isBlank()) continue;
                Path candidate = Path.of(entry).resolve(filename);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate;
            }
        }
        throw new IllegalArgumentException("后端运行环境 PATH 中未找到 Codex，请填写绝对路径");
    }

    private record CatalogKey(Path executable, Path directory) {
    }
}
