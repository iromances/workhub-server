package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

/** REQ-7f26a063：规则组下的检查项（ops_account_monitor_check）。 */
public record AccountingMonitorCheckEntity(Long id,
                                           Long familyId,
                                           String checkId,
                                           String checkName,
                                           String severity,
                                           String note,
                                           Boolean enabled,
                                           Integer sortOrder,
                                           String createdBy,
                                           String updatedBy,
                                           LocalDateTime createdAt,
                                           LocalDateTime updatedAt) {
}
