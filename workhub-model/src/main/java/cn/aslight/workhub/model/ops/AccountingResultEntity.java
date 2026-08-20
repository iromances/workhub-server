package cn.aslight.workhub.model.ops;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountingResultEntity(Long id,
                                     Long runId,
                                     String ruleCode,
                                     String ruleName,
                                     String category,
                                     String granularity,
                                     String severity,
                                     String status,
                                     Integer anomalyCount,
                                     BigDecimal differenceAmount,
                                     String sampleJson,
                                     String message,
                                     Long durationMs,
                                     LocalDateTime createdAt) {
}
