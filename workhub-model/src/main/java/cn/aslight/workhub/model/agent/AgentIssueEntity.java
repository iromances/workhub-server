package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

/** 巡检问题即工单：跨轮聚合（task+signature 唯一），状态机见 AgentIssueService。 */
public record AgentIssueEntity(
        String id, String taskId, String agentId, String businessLineCode, String environmentCode,
        String domain, String signature, String title, String severity, String status,
        String analysisJson, String evidenceJson, String fixJson, String reviewJson, String deployJson,
        String firstRunId, String lastRunId, LocalDateTime firstSeenAt, LocalDateTime lastSeenAt,
        int occurrenceCount, int unseenRounds, int reopenCount, long version,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
