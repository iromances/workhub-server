package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;
import java.util.List;

public record SystemAlertRuleResponse(Long id,
                                      String ruleName,
                                      String action,
                                      String matchScope,
                                      String matchMode,
                                      List<String> keywords,
                                      Integer priority,
                                      Boolean enabled,
                                      String remark,
                                      LocalDateTime createdAt,
                                      LocalDateTime updatedAt) {
}
