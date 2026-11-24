package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;

public final class AgentReportRows {
    private AgentReportRows() {}
    public record Report(String id, String runId, String taskId, String agentId, int schemaVersion, String reportType, String title, String conclusion, String assessment, String scopeJson, String limitationsJson, String originalEventId, String payloadHash, LocalDateTime createdAt) {}
    public record Check(String id, String reportId, String key, int ordinal, String category, String title, String outcome, String result) {}
    public record Finding(String id, String reportId, String key, int ordinal, String title, String severity, String verificationStatus, String disposition, String category, String trigger, String analysis, String impact, String recommendation, String signature, String domain, String issueId) {}
    public record Evidence(String id, String reportId, String key, int ordinal, String targetType, String targetKey, String type, String source, String locator, String observedAt, String environment, String verificationMethod, String result, String limitations) {}
    public record Action(String id, String reportId, String key, int ordinal, String findingKey, String priority, String description, String acceptanceCriteria, String prerequisite) {}
}
