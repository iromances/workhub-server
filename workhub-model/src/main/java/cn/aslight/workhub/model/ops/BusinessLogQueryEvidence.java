package cn.aslight.workhub.model.ops;

import java.time.Instant;
import java.util.List;

/** 同一 ELK PIT 中的匹配查询和指定服务活动；由平台生成，随 Observation 固定。 */
public record BusinessLogQueryEvidence(int schemaVersion, String executionId, Definition definition,
        String definitionHash, String targetBindingHash, String snapshotHash, Instant startedAt, Instant finishedAt,
        Instant from, Instant to, boolean complete, List<Activity> activities) {
    public record Definition(Long scopeId, String businessLineCode, String environmentCode, String watchMode,
            List<String> indexPatterns, List<String> serviceNames, List<String> levels,
            String phraseHash, String traceIdHash, String requestIdHash, int limit) { }
    public record Activity(String serviceName, long count, Instant latestAt, String latestEventId) { }
}
