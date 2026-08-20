package cn.aslight.workhub.model.mcp;

import java.time.LocalDateTime;

public record McpBastionConnectionTestResponse(boolean success,
                                               long durationMs,
                                               String message,
                                               LocalDateTime testedAt) {
}
