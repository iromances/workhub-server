package cn.aslight.workhub.service.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 本地代码仓库只读文件服务。仅在 workhub.mcp.repository-read-roots 白名单内提供
 * 列目录、读文本、固定串搜索三种只读能力；空白名单即整体关闭，AI 不得自行扩展。
 */
@Component
public class RepositoryFileService {

    private static final int DEFAULT_MAX_DEPTH = 6;
    private static final int LIMIT_MAX_DEPTH = 12;
    private static final int DEFAULT_MAX_ENTRIES = 200;
    private static final int LIMIT_MAX_ENTRIES = 2000;
    private static final int DEFAULT_MAX_BYTES = 256 * 1024;
    private static final int LIMIT_MAX_BYTES = 1024 * 1024;
    private static final int DEFAULT_MAX_MATCHES = 30;
    private static final int LIMIT_MAX_MATCHES = 100;
    private static final long SEARCH_FILE_LIMIT = 2L * 1024 * 1024;

    private final List<Path> readRoots;

    public RepositoryFileService(@Value("${workhub.mcp.repository-read-roots:}") String roots) {
        this.readRoots = parseRoots(roots);
    }

    @FunctionalInterface
    public interface IOCall {
        Object run() throws IOException;
    }

    public boolean enabled() {
        return !readRoots.isEmpty();
    }

    public Map<String, Object> list(String rawPath, Integer maxDepth, Integer maxEntries) throws IOException {
        Path directory = requireAllowedDirectory(rawPath);
        int depth = bounded(nonNegativeOrDefault(maxDepth, DEFAULT_MAX_DEPTH), LIMIT_MAX_DEPTH);
        int entries = bounded(nonNegativeOrDefault(maxEntries, DEFAULT_MAX_ENTRIES), LIMIT_MAX_ENTRIES);
        List<Map<String, Object>> items = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(directory, depth)) {
            stream.skip(1).sorted(Comparator.comparing(Path::toString)).limit(entries + 1L).forEach(path -> {
                if (items.size() >= entries) return;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("path", path.toString());
                item.put("directory", Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS));
                items.add(item);
            });
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("root", directory.toString());
        result.put("truncated", items.size() >= entries);
        result.put("entries", items);
        return result;
    }

    public Map<String, Object> readTextFile(String rawPath, Integer maxBytes) throws IOException {
        Path file = requireAllowedRegularFile(rawPath);
        long limit = bounded(nonNegativeOrDefault(maxBytes, DEFAULT_MAX_BYTES), LIMIT_MAX_BYTES);
        long size = Files.size(file);
        if (size > limit) throw new IllegalArgumentException("文件超过读取上限 " + limit + " 字节: " + file);
        byte[] bytes = Files.readAllBytes(file);
        if (isBinary(bytes)) throw new IllegalArgumentException("文件疑似二进制，仅支持 UTF-8 文本: " + file);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", file.toString());
        result.put("bytes", size);
        result.put("content", new String(bytes, StandardCharsets.UTF_8));
        return result;
    }

    public Map<String, Object> searchText(String rawPath, String keyword, Integer maxMatches) throws IOException {
        Path directory = requireAllowedDirectory(rawPath);
        if (keyword == null || keyword.isBlank()) throw new IllegalArgumentException("keyword 不能为空");
        int limit = bounded(nonNegativeOrDefault(maxMatches, DEFAULT_MAX_MATCHES), LIMIT_MAX_MATCHES);
        List<Map<String, Object>> matches = new ArrayList<>();
        boolean truncated;
        try (Stream<Path> stream = Files.walk(directory, LIMIT_MAX_DEPTH)) {
            truncated = !stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !isBinaryFile(path))
                    .filter(path -> fileSizeAtMost(path, SEARCH_FILE_LIMIT))
                    .anyMatch(path -> collectMatches(path, keyword, limit, matches) == CollectResult.LIMIT_REACHED);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("root", directory.toString());
        result.put("keyword", keyword);
        result.put("truncated", truncated || matches.size() >= limit);
        result.put("matches", matches);
        return result;
    }

    private enum CollectResult { CONTINUED, LIMIT_REACHED }

    private CollectResult collectMatches(Path file, String keyword, int limit, List<Map<String, Object>> matches) {
        if (matches.size() >= limit) return CollectResult.LIMIT_REACHED;
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return CollectResult.CONTINUED;
        }
        int index = 0;
        while ((index = text.indexOf(keyword, index)) >= 0) {
            int line = 1 + (int) text.chars().limit(index).filter(ch -> ch == '\n').count();
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("path", file.toString());
            match.put("line", line);
            matches.add(match);
            if (matches.size() >= limit) return CollectResult.LIMIT_REACHED;
            index += keyword.length();
        }
        return CollectResult.CONTINUED;
    }

    private Path requireAllowedDirectory(String rawPath) throws IOException {
        Path path = requireAllowed(rawPath);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("路径不是目录: " + path);
        return path;
    }

    private Path requireAllowedRegularFile(String rawPath) throws IOException {
        Path path = requireAllowed(rawPath);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("路径不是普通文件: " + path);
        return path;
    }

    private Path requireAllowed(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) throw new IllegalArgumentException("path 不能为空");
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (rawPath.indexOf('\0') >= 0) throw new IllegalArgumentException("路径非法");
        for (Path root : readRoots) {
            if (path.startsWith(root) && !path.equals(root)) return path;
        }
        throw new IllegalArgumentException("路径不在 repository-read-roots 白名单内: " + path);
    }

    private static boolean isBinaryFile(Path path) {
        try {
            return isBinary(Files.readAllBytes(path));
        } catch (IOException ex) {
            return true;
        }
    }

    private static boolean isBinary(byte[] bytes) {
        int probe = Math.min(bytes.length, 8192);
        for (int i = 0; i < probe; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    private static boolean fileSizeAtMost(Path path, long limit) {
        try {
            return Files.size(path) <= limit;
        } catch (IOException ex) {
            return false;
        }
    }

    private static List<Path> parseRoots(String roots) {
        if (roots == null || roots.isBlank()) return List.of();
        List<Path> parsed = new ArrayList<>();
        for (String part : roots.split("[,;]")) {
            if (part.isBlank()) continue;
            parsed.add(Path.of(part.trim()).toAbsolutePath().normalize());
        }
        return List.copyOf(parsed);
    }

    private static int nonNegativeOrDefault(Integer value, int fallback) {
        return value == null || value < 0 ? fallback : value;
    }

    private static int bounded(int value, int limit) {
        return Math.min(value, limit);
    }
}
