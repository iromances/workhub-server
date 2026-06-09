package cn.aslight.workhub.model.mcp;

public record McpResourceProfile(String key,
                                 Integer maxRows,
                                 Integer queryTimeoutSeconds,
                                 Integer maxResultBytes,
                                 Integer maxOutputLines,
                                 Integer timeoutSeconds) {
}
