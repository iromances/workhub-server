package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.CodexModelCatalogResponse;
import cn.aslight.workhub.model.ai.ZCodeModelCatalogRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.*;

/** 与 Codex 共用目录响应形状；ZCode 每次读取当前配置，不缓存认证材料。 */
@Service
public class ZCodeModelCatalogService {
    private final ZCodeCliClient client;
    private final ObjectMapper json;
    private final Semaphore permits = new Semaphore(4);
    private final long timeoutMillis;

    @Autowired
    public ZCodeModelCatalogService(ZCodeCliClient client, ObjectMapper json) {
        this(client, json, 25_000);
    }

    ZCodeModelCatalogService(ZCodeCliClient client, ObjectMapper json, long timeoutMillis) {
        this.client = client;
        this.json = json;
        this.timeoutMillis = timeoutMillis;
    }

    public CodexModelCatalogResponse query(ZCodeModelCatalogRequest request) {
        if (!permits.tryAcquire()) throw new IllegalStateException("ZCode 模型查询繁忙，请稍后重试");
        Path directory = null;
        Process process = null;
        Thread reader = null;
        var descendants = new LinkedHashMap<Long, ProcessHandle>();
        try {
            var provider = request.provider();
            client.preflight(provider);
            directory = Files.createTempDirectory("workhub-zcode-catalog-");
            process = client.prepareBuilder(provider, directory, false).start();
            var conversation = new ZCodeModelCatalogConversation(json, directory.resolve("work"));
            Process observed = process;
            reader = Thread.ofVirtual().start(() -> {
                try {
                    conversation.start(observed.getOutputStream());
                    ZCodeCliClient.readProtocol(observed.getInputStream(), conversation, ignored -> { });
                    conversation.result.completeExceptionally(new IllegalStateException("ZCode 查询进程提前结束"));
                } catch (Exception ex) {
                    conversation.result.completeExceptionally(new IllegalStateException("ZCode 模型目录响应无效，请检查 CLI 版本和配置"));
                }
            });
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (!conversation.result.isDone()) {
                process.descendants().forEach(child -> descendants.put(child.pid(), child));
                if (System.nanoTime() >= deadline) throw new IllegalStateException("查询 ZCode 模型超时，请重试");
                Thread.sleep(25);
            }
            return new CodexModelCatalogResponse(conversation.result.get(), Instant.now(), false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("查询 ZCode 模型已中断");
        } catch (IOException ex) {
            throw new IllegalStateException("无法启动 ZCode 模型查询，请检查程序和配置路径");
        } catch (ExecutionException ex) {
            throw new IllegalStateException("ZCode 模型目录响应无效，请检查 CLI 版本和配置");
        } finally {
            boolean interrupted = Thread.interrupted();
            boolean stopped = process == null || ZCodeCliClient.stopTree(process, descendants);
            if (reader != null) reader.interrupt();
            if (stopped && directory != null) ZCodeCliClient.delete(directory);
            permits.release();
            if (interrupted) Thread.currentThread().interrupt();
            if (!stopped) throw new IllegalStateException("ZCode 查询进程未能回收");
        }
    }
}
