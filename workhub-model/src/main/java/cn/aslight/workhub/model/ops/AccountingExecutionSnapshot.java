package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;
import java.util.List;

/** 实际查询前固定的上下文。这里只保存目标和 SQL 的摘要，不保存口令或 SQL 原文。 */
public record AccountingExecutionSnapshot(int schemaVersion, Long runId, Configuration configuration,
        String configurationHash, List<Rule> rules, String rulesHash, LocalDateTime startTime, LocalDateTime endTime,
        String recordTimeZone, String targetBindingHash) {
    public record Configuration(Long configId, String businessLineCode, String environmentCode, String systemName,
            String displayName, String databaseTargetKey, String schemaName, String ruleProfile, Boolean enabled) { }
    public record Rule(String ruleCode, String ruleName, String category, String granularity, String severity,
            List<String> requiredTables, String definitionHash, String summarySqlHash, String sampleSqlHash) { }
}
