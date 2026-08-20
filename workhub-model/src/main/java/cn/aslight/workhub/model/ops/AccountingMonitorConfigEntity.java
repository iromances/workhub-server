package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public record AccountingMonitorConfigEntity(Long id,
                                            String businessLineCode,
                                            String businessLineName,
                                            String environmentCode,
                                            String systemName,
                                            String displayName,
                                            String databaseTargetKey,
                                            String schemaName,
                                            String ruleProfile,
                                            Boolean enabled,
                                            Boolean dailyEnabled,
                                            String remark,
                                            LocalDateTime createdAt,
                                            LocalDateTime updatedAt) {
}
