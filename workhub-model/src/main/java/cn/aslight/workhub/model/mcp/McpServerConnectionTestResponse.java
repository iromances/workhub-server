package cn.aslight.workhub.model.mcp;

import java.time.LocalDateTime;

public record McpServerConnectionTestResponse(boolean success,
                                              String connectionMode,
                                              String failureStage,
                                              long durationMs,
                                              String message,
                                              LocalDateTime testedAt) {
}
