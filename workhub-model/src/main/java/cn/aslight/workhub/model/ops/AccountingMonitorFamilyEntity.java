package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

/** REQ-7f26a063：配置下的监测规则组（ops_account_monitor_family，内置目录为种子）。 */
public record AccountingMonitorFamilyEntity(Long id,
                                            Long configId,
                                            String familyCode,
                                            String familyName,
                                            String description,
                                            Integer batchSize,
                                            String sourceType,
                                            Boolean enabled,
                                            Boolean deleted,
                                            Integer sortOrder,
                                            String createdBy,
                                            String updatedBy,
                                            LocalDateTime createdAt,
                                            LocalDateTime updatedAt) {
}
