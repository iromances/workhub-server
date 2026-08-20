package cn.aslight.workhub.model.mcp;

import java.time.LocalDateTime;

public record McpDatabaseConnectionTestResponse(boolean success,
                                                String connectionMode,
                                                String failureStage,
                                                String bastionName,
                                                long durationMs,
                                                String message,
                                                LocalDateTime testedAt) {
}
