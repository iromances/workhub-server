package cn.aslight.workhub.mcp.security;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget;
import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget.ServerProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 复用允许 AI 读取的目录白名单；固定读取程序不接收调用方代码或命令。 */
public final class ServerFilePolicy {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String READER = loadReader();

    private ServerFilePolicy() { }

    public static boolean isFileCommand(String commandKey) {
        return "list_server_directory".equals(commandKey) || "read_server_file".equals(commandKey);
    }

    public static List<String> buildCommand(ServerTarget target, ServerProfile profile,
                                            String commandKey, Map<String, String> parameters) {
        Map<String, Object> request = request(target, commandKey, parameters.get("path"));
        int outputLines = Math.min(profile.maxOutputLines() <= 0 ? 200 : profile.maxOutputLines(), 2000);
        if ("list_server_directory".equals(commandKey)) {
            request.put("maxDepth", limit(parameters.get("maxDepth"), 1, 3, "目录深度"));
            int entries = Math.min(outputLines, 1000);
            request.put("maxEntries", limit(parameters.get("maxEntries"), entries, entries, "目录条数"));
        } else if ("read_server_file".equals(commandKey)) {
            request.put("lines", limit(parameters.get("lines"), outputLines, outputLines, "读取行数"));
            request.put("maxBytes", limit(parameters.get("maxBytes"), 262144, 262144, "读取字节数"));
        } else {
            throw new IllegalArgumentException("不支持的文件读取操作");
        }
        return command(request);
    }

    public static boolean isPathLogCommand(String commandKey) {
        return "tail_log_file".equals(commandKey) || "search_log_file".equals(commandKey);
    }

    /** 原日志命令只用于确定固定操作和限制，实际读取通过已经验证的文件描述符。 */
    public static List<String> protectLogCommand(ServerTarget target, String commandKey, List<String> original) {
        Map<String, Object> request = request(target, commandKey, original.getLast());
        if ("tail_log_file".equals(commandKey)) {
            request.put("lines", Integer.parseInt(original.get(2)));
        } else {
            request.put("keyword", original.get(original.size() - 2));
            request.put("maxMatches", Integer.parseInt("zipgrep".equals(original.getFirst())
                    ? original.get(2).substring(2) : original.get(4)));
        }
        return command(request);
    }

    public static String requireAllowedPath(ServerTarget target, String value) {
        if (target.allowedLogPaths().isEmpty()) {
            throw new IllegalArgumentException("未配置允许 AI 读取的白名单目录，路径读取已关闭");
        }
        String path = normalizePath(value);
        if (target.allowedLogPaths().stream().map(ServerFilePolicy::normalizeRoot)
                .noneMatch(root -> Path.of(path).startsWith(root))) {
            throw new IllegalArgumentException("路径不在允许 AI 读取的白名单目录内，其他目录需用户明确授权：" + path);
        }
        return path;
    }

    private static Map<String, Object> request(ServerTarget target, String operation, String value) {
        String path = requireAllowedPath(target, value);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operation", operation);
        request.put("path", path);
        request.put("roots", target.allowedLogPaths().stream().map(ServerFilePolicy::normalizeRoot).toList());
        return request;
    }

    private static List<String> command(Map<String, Object> request) {
        try {
            return List.of("python3", "-I", "-S", "-c", READER, MAPPER.writeValueAsString(request));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("文件读取参数序列化失败", ex);
        }
    }

    public static String normalizeRoot(String value) {
        String path = normalizePath(value);
        if ("/".equals(path)) {
            throw new IllegalArgumentException("允许 AI 读取的白名单目录不能配置根目录");
        }
        return path;
    }

    private static String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        String path = value.trim();
        if (path.length() > 4096 || path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("文件路径过长或包含控制字符");
        }
        if (!path.startsWith("/") || List.of(path.split("/")).contains("..")) {
            throw new IllegalArgumentException("文件路径必须是绝对路径且不能包含上级目录引用");
        }
        try {
            return Path.of(path).normalize().toString();
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("文件路径无效", ex);
        }
    }

    private static int limit(String raw, int defaultValue, int maximum, String label) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                throw new IllegalArgumentException(label + "必须大于 0");
            }
            return Math.min(value, maximum);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + "必须是整数", ex);
        }
    }

    private static String loadReader() {
        try (var input = ServerFilePolicy.class.getResourceAsStream("/server-file-reader.py")) {
            if (input == null) {
                throw new IllegalStateException("文件读取程序资源缺失");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("文件读取程序资源加载失败", ex);
        }
    }
}
