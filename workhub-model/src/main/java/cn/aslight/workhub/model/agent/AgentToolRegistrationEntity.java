package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** pm_agent_tool 行；工具目录以代码 AgentToolCatalog 为权威，本表仅存人读展示文案。 */
public record AgentToolRegistrationEntity(String code, String name, String description, long version,
                                          String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
