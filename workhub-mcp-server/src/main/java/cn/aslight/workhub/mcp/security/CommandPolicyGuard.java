package cn.aslight.workhub.mcp.security;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget;
import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget.ServerProfile;

import java.util.List;
import java.util.Map;

/**
 * Builds read-only diagnostic commands from a closed whitelist.
 */
public class CommandPolicyGuard {

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
            default -> throw new IllegalArgumentException("不支持的诊断命令：" + commandKey);
        };
    }

    private String requireAllowedService(ServerTarget target, Map<String, String> args) {
        String service = requireValue(args.get("service"), "服务名不能为空");
        if (!target.allowedServices().isEmpty() && !target.allowedServices().contains(service)) {
            throw new IllegalArgumentException("服务不在白名单中：" + service);
        }
        return service;
    }

    private String requireAllowedLogPath(ServerTarget target, Map<String, String> args) {
        String logPath = requireValue(args.get("logPath"), "日志路径不能为空");
        if (!target.allowedLogPaths().isEmpty() && !target.allowedLogPaths().contains(logPath)) {
            throw new IllegalArgumentException("日志路径不在白名单中：" + logPath);
        }
        return logPath;
    }

    private String normalizeLines(String rawLines, int maxLines) {
        int fallback = maxLines <= 0 ? 200 : maxLines;
        int lines;
        if (rawLines == null || rawLines.isBlank()) {
            lines = fallback;
        } else {
            try {
                lines = Integer.parseInt(rawLines);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("日志行数必须是数字");
            }
        }
        if (lines <= 0) {
            throw new IllegalArgumentException("日志行数必须大于 0");
        }
        return String.valueOf(Math.min(lines, fallback));
    }

    private String requireValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
