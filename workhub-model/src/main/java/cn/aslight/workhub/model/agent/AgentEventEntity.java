package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** eventKey 对补充指令和执行回调提供幂等，ordinal 保留每次运行内的顺序。 */
public record AgentEventEntity(String id, String runId, String eventKey, long ordinal,
                               String kind, String payloadJson, String actor, LocalDateTime createdAt) {
}
