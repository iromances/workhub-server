package cn.aslight.workhub.mcp.security;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget;
import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget.ServerProfile;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds read-only diagnostic commands from a closed whitelist.
 */
public class CommandPolicyGuard {

    private static final Pattern SAFE_LOG_PATH = Pattern.compile("/[\\p{L}\\p{N}._/@%+=:,-]+");
    private static final int MAX_SEARCH_KEYWORD_LENGTH = 256;
    private static final String ERE_META_CHARACTERS = ".\\^$*+?()[]{}|";

    public List<String> buildCommand(ServerTarget target,
                                     ServerProfile profile,
                                     String commandKey,
                                     Map<String, String> parameters) {
        Map<String, String> args = parameters == null ? Map.of() : parameters;
        return switch (requireValue(commandKey, "命令 key 不能为空")) {
            case "service_status" -> List.of("systemctl", "status", requireAllowedService(target, args), "--no-pager");
            case "service_logs" -> List.of("journalctl", "-u", requireAllowedService(target, args), "-n",
                    normalizeLines(args.get("lines"), profile.maxOutputLines()), "--no-pager");
            case "tail_log_file" -> List.of("tail", "-n", normalizeLines(args.get("lines"), profile.maxOutputLines()),
                    requireAllowedLogPath(target, args));
            case "search_log_file" -> buildSearchLogCommand(target, profile, args);
            default -> throw new IllegalArgumentException("不支持的诊断命令：" + commandKey);
        };
    }

    private List<String> buildSearchLogCommand(ServerTarget target,
                                                ServerProfile profile,
                                                Map<String, String> args) {
        String logPath = requireAllowedLogPath(target, args);
        String keyword = requireSearchKeyword(args.get("keyword"));
        String maxMatches = normalizePositiveLimit(
                args.get("maxMatches"),
                profile.maxOutputLines(),
                "最大命中数"
        );
        if (logPath.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            if (keyword.startsWith("-")) {
                throw new IllegalArgumentException("ZIP 日志检索关键词不能以 - 开头");
            }
            if (keyword.chars().anyMatch(character -> ERE_META_CHARACTERS.indexOf(character) >= 0)) {
                throw new IllegalArgumentException("ZIP 日志检索关键词不能包含正则元字符");
            }
            return List.of("zipgrep", "-n", "-m" + maxMatches, keyword, logPath);
        }
        return List.of("grep", "-F", "-n", "-m", maxMatches, "--", keyword, logPath);
    }

    private String requireAllowedService(ServerTarget target, Map<String, String> args) {
        String service = requireValue(args.get("service"), "服务名不能为空");
        if (!target.allowedServices().isEmpty() && !target.allowedServices().contains(service)) {
            throw new IllegalArgumentException("服务不在白名单中：" + service);
        }
        return service;
    }

    private String requireAllowedLogPath(ServerTarget target, Map<String, String> args) {
        String logPath = normalizeAbsoluteLogPath(
                requireValue(args.get("logPath"), "日志路径不能为空"),
                "日志路径必须是绝对路径"
        );
        if (!SAFE_LOG_PATH.matcher(logPath).matches()) {
            throw new IllegalArgumentException("日志路径包含不允许的字符：" + logPath);
        }
        if (!target.allowedLogPaths().isEmpty() && target.allowedLogPaths().stream().noneMatch(path -> {
            String allowedDirectory = normalizeAbsoluteLogPath(path, "日志目录白名单必须配置绝对路径");
            return Path.of(logPath).startsWith(Path.of(allowedDirectory));
        })) {
            throw new IllegalArgumentException("日志路径不在目录白名单中：" + logPath);
        }
        return logPath;
    }

    private String normalizeAbsoluteLogPath(String value, String message) {
        String normalizedValue = requireValue(value, message);
        try {
            Path path = Path.of(normalizedValue).normalize();
            if (!path.isAbsolute()) {
                throw new IllegalArgumentException(message + "：" + normalizedValue);
            }
            return path.toString();
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException(message + "：" + normalizedValue, ex);
        }
    }

    private String normalizeLines(String rawLines, int maxLines) {
        return normalizePositiveLimit(rawLines, maxLines, "日志行数");
    }

    private String normalizePositiveLimit(String rawValue, int configuredMaximum, String fieldName) {
        int fallback = configuredMaximum <= 0 ? 200 : configuredMaximum;
        int value;
        if (rawValue == null || rawValue.isBlank()) {
            value = fallback;
        } else {
            try {
                value = Integer.parseInt(rawValue);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(fieldName + "必须是数字");
            }
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于 0");
        }
        return String.valueOf(Math.min(value, fallback));
    }

    private String requireSearchKeyword(String rawKeyword) {
        String keyword = requireValue(rawKeyword, "检索关键词不能为空");
        if (keyword.length() > MAX_SEARCH_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("检索关键词长度不能超过 " + MAX_SEARCH_KEYWORD_LENGTH);
        }
        if (keyword.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new IllegalArgumentException("检索关键词不能包含控制字符");
        }
        return keyword;
    }

    private String requireValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
