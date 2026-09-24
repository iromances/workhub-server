package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.CodexModelCatalogResponse.Model;
import cn.aslight.workhub.model.ai.CodexModelCatalogResponse.ReasoningEffort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** 只通过 app-server 查询目录，不创建会话或执行模型任务。 */
@Component
public class CodexModelCatalogClient {
    private static final int MAX_OUTPUT_CHARS = 4 * 1024 * 1024;
    private final ObjectMapper mapper;
    private final long timeoutMillis;

    @Autowired
    public CodexModelCatalogClient(ObjectMapper mapper) {
        this(mapper, 10_000);
    }

    CodexModelCatalogClient(ObjectMapper mapper, long timeoutMillis) {
        this.mapper = mapper;
        this.timeoutMillis = timeoutMillis;
    }

    public List<Model> query(Path executable, Path directory) {
        Process process;
        try {
            process = startProcess(executable, directory);
        } catch (IOException ex) {
            throw new IllegalStateException("无法启动 Codex，请检查 CLI 命令和运行权限");
        }
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<List<Model>> response = executor.submit(() -> {
            try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
                 Writer writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
                return readCatalog(reader, writer);
            }
        });
        // 不缓存 stderr 内容，防止账号或本机配置信息进入接口响应及日志。
        Future<?> errors = executor.submit(() -> {
            try (InputStream stream = process.getErrorStream()) {
                stream.transferTo(OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // 查询结束关闭进程时，stderr 读取也随之结束。
            }
        });
        try {
            return response.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("查询 Codex 模型超时，请重试");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("查询 Codex 模型已中断");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IllegalStateException failure) {
                throw failure;
            }
            throw new IllegalStateException("Codex 模型目录响应无效，请检查 CLI 版本和登录状态");
        } finally {
            try {
                stopProcess(process);
            } finally {
                response.cancel(true);
                errors.cancel(true);
                executor.shutdownNow();
            }
        }
    }

    Process startProcess(Path executable, Path directory) throws IOException {
        return new ProcessBuilder(executable.toString(), "app-server", "--listen", "stdio://")
                .directory(directory.toFile()).start();
    }

    private void stopProcess(Process process) {
        List<ProcessHandle> children = process.descendants().toList();
        children.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Codex 查询进程未能正常回收");
            }
            CompletableFuture.allOf(children.stream().map(ProcessHandle::onExit)
                    .toArray(CompletableFuture[]::new)).get(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            interrupted = true;
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("Codex 查询子进程未能正常回收");
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    List<Model> readCatalog(Reader input, Writer output) throws IOException {
        CatalogReader reader = new CatalogReader(input);
        send(output, Map.of("id", 1, "method", "initialize", "params",
                Map.of("clientInfo", Map.of("name", "workhub_model_catalog", "version", "1.0"))));
        response(reader, 1);
        send(output, Map.of("method", "initialized"));
        List<Model> models = new ArrayList<>();
        Set<String> cursors = new HashSet<>();
        String cursor = null;
        int id = 2;
        do {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("limit", 100);
            params.put("includeHidden", false);
            if (cursor != null) params.put("cursor", cursor);
            send(output, Map.of("id", id, "method", "model/list", "params", params));
            JsonNode result = response(reader, id++);
            if (!result.path("data").isArray()) throw invalidCatalog();
            for (JsonNode item : result.path("data")) {
                if (item.path("hidden").asBoolean(false)) continue;
                String model = text(item, "model", 128);
                String defaultEffort = text(item, "defaultReasoningEffort", 32);
                List<ReasoningEffort> efforts = new ArrayList<>();
                if (!item.path("supportedReasoningEfforts").isArray()) throw invalidCatalog();
                for (JsonNode effort : item.path("supportedReasoningEfforts")) {
                    efforts.add(new ReasoningEffort(text(effort, "reasoningEffort", 32),
                            effort.path("description").asText("")));
                }
                if (efforts.stream().noneMatch(e -> e.reasoningEffort().equals(defaultEffort))) {
                    throw invalidCatalog();
                }
                models.add(new Model(model, text(item, "displayName", 256),
                        item.path("isDefault").asBoolean(false), defaultEffort, List.copyOf(efforts)));
            }
            JsonNode next = result.path("nextCursor");
            if (!next.isMissingNode() && !next.isNull() && !next.isTextual()) throw invalidCatalog();
            cursor = next.isTextual() ? next.asText() : null;
            if (cursor != null && (cursor.isBlank() || !cursors.add(cursor))) throw invalidCatalog();
        } while (cursor != null);
        if (models.isEmpty()) throw new IllegalStateException("Codex 未返回可用模型，请检查登录状态后重试");
        return List.copyOf(models);
    }

    private JsonNode response(CatalogReader reader, int id) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            JsonNode message;
            try {
                message = mapper.readTree(line);
            } catch (RuntimeException ex) {
                throw invalidCatalog();
            }
            if (message == null || !message.isObject()) throw invalidCatalog();
            if (!message.has("id") && message.path("method").isTextual()) continue;
            if (!message.path("id").isIntegralNumber() || message.path("id").asInt() != id) {
                throw invalidCatalog();
            }
            if (message.hasNonNull("error")) {
                throw new IllegalStateException("Codex 拒绝模型查询，请检查 CLI 版本和登录状态");
            }
            if (!message.path("result").isObject()) throw invalidCatalog();
            return message.path("result");
        }
        throw new IllegalStateException("Codex 查询进程提前结束，未返回完整模型目录");
    }

    private void send(Writer writer, Object message) throws IOException {
        writer.write(mapper.writeValueAsString(message));
        writer.write('\n');
        writer.flush();
    }

    private String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength) {
            throw invalidCatalog();
        }
        return value.asText();
    }

    private IllegalStateException invalidCatalog() {
        return new IllegalStateException("Codex 模型目录响应无效，请检查 CLI 版本");
    }

    /** 累计限制包含通知在内的整个响应，避免 readLine 分配无限长字符串。 */
    private static final class CatalogReader {
        private final Reader reader;
        private int count;

        private CatalogReader(Reader reader) {
            this.reader = new BufferedReader(reader);
        }

        private String readLine() throws IOException {
            StringBuilder line = new StringBuilder();
            int character;
            while ((character = reader.read()) != -1) {
                if (++count > MAX_OUTPUT_CHARS) throw new IllegalStateException("Codex 模型目录响应过大");
                if (character == '\n') return line.toString();
                line.append((char) character);
            }
            return line.isEmpty() ? null : line.toString();
        }
    }
}
