package cn.aslight.workhub.model.ops;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 一次真实只读明细查询的来源证明，随 Agent Observation 固定；不含连接凭据或 SQL 正文。 */
public record XxlJobQueryEvidence(int schemaVersion, String executionId, Definition definition, String definitionHash,
        String targetBindingHash, List<String> queryHashes, Instant startedAt, Instant finishedAt, String recordTimeZone,
        long matchingCount, long activityCount, LocalDateTime latestActivityAt, boolean completePage) {
    public record Definition(Long monitorId, String monitorType, String monitorKey, String businessLineCode,
            String environmentCode, String databaseName, String configuredExecutors, boolean enabled,
            LocalDate fromDate, LocalDate toDate, String author, String executorFilter, String logStatus, int page, int pageSize) { }
}
