package cn.aslight.workhub.model.agent;

import java.time.LocalDateTime;
import java.util.List;

/** 工单视图：列表/详情/运行内嵌共用。 */
public record AgentIssueResponse(
        String id, String taskId, String agentId, String businessLineCode, String environmentCode,
        String domain, String signature, String title, String severity, String status,
        Analysis analysis, Evidence evidence, Fix fix, Review review, Deploy deploy,
        String firstRunId, String lastRunId, LocalDateTime firstSeenAt, LocalDateTime lastSeenAt,
        int occurrenceCount, int unseenRounds, int reopenCount, long version,
        LocalDateTime createdAt, LocalDateTime updatedAt) {

    public record Analysis(String codeLocation, String repro, String rootCause, String impactVerification,
                           String fixDirection, List<RepoRef> repos) { }
    public record RepoRef(String system, String repository, String path) { }
    public record Evidence(String summary, List<String> references, String window) { }
    public record Fix(String branch, List<RepoRef> repos, String commit, String mrUrl, String diffSummary, String fixRunId) { }
    public record Review(String reviewer, String decision, String note, LocalDateTime reviewedAt) { }
    public record Deploy(String testStatus, String testReceipt, LocalDateTime testedAt,
                         String prodStatus, String prodReceipt, LocalDateTime prodAt) { }
}
