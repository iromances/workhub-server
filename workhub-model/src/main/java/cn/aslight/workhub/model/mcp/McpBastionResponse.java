package cn.aslight.workhub.model.mcp;

import java.time.LocalDateTime;

public record McpBastionResponse(Long id,
                                 String name,
                                 String host,
                                 Integer port,
                                 String username,
                                 Boolean passwordConfigured,
                                 String identityFile,
                                 Boolean enabled,
                                 String remark,
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
}
