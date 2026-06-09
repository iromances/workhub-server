package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public record SystemAlertSubsystemSummaryResponse(String businessLineCode,
                                                  String environmentCode,
                                                  String subsystemName,
                                                  String serviceName,
                                                  Integer errorCount,
                                                  LocalDateTime latestOccurredAt) {
}
