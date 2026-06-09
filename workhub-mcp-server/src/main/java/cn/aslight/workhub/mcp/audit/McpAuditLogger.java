package cn.aslight.workhub.mcp.audit;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

/**
 * Append-only JSONL audit logger for MCP tool calls.
 */
public class McpAuditLogger {

    private final ObjectMapper objectMapper;
    private final Path auditLogPath;

    public McpAuditLogger(ObjectMapper objectMapper, Path auditLogPath) {
        this.objectMapper = objectMapper;
        this.auditLogPath = auditLogPath;
    }

    public static McpAuditLogger defaultLogger(ObjectMapper objectMapper) {
        String path = System.getenv("WORKHUB_MCP_AUDIT_LOG");
        return new McpAuditLogger(objectMapper, Path.of(path == null || path.isBlank() ? "logs/mcp-audit.jsonl" : path));
    }

    public void record(Map<String, Object> fields) {
        try {
            if (auditLogPath.getParent() != null) {
                Files.createDirectories(auditLogPath.getParent());
            }
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("timestamp", Instant.now().toString());
            payload.putAll(fields);
            Files.writeString(
                    auditLogPath,
                    objectMapper.writeValueAsString(payload) + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ex) {
            System.err.println("Failed to write MCP audit log: " + ex.getMessage());
        }
    }
}
