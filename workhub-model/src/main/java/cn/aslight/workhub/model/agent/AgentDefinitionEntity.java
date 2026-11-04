package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public record AgentDefinitionEntity(String id, String name, String configurationJson,
                                    boolean enabled, String ownerName, long version,
                                    LocalDateTime createdAt, LocalDateTime updatedAt) {
}
