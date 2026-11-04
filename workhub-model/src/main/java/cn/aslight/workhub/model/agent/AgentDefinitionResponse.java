package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentDefinitionResponse(String id, AgentConfiguration configuration, boolean enabled,
                                      String ownerName, long version, LocalDateTime createdAt,
                                      LocalDateTime updatedAt) {
}
