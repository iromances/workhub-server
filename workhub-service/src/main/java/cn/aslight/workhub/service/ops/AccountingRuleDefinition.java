package cn.aslight.workhub.service.ops;

import java.time.LocalDateTime;
import java.util.Set;

public record AccountingRuleDefinition(String ruleCode,
                                       String ruleName,
                                       String category,
                                       String granularity,
                                       String severity,
                                       String description,
                                       Set<String> requiredTables,
                                       SqlBuilder sqlBuilder) {

    public AccountingRuleSql buildSql(String schemaName, LocalDateTime startTime, LocalDateTime endTime) {
        return sqlBuilder.build(schemaName, startTime, endTime);
    }

    @FunctionalInterface
    public interface SqlBuilder {
        AccountingRuleSql build(String schemaName, LocalDateTime startTime, LocalDateTime endTime);
    }
}
