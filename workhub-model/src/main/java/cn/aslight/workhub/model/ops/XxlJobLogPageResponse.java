package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;
import java.util.List;

public record XxlJobLogPageResponse(Long id,
                                    String monitorKey,
                                    String businessLineCode,
                                    String environmentCode,
                                    String name,
                                    String xxlJobDatabaseName,
                                    String status,
                                    String message,
                                    LocalDateTime checkedAt,
                                    int total,
                                    int page,
                                    int pageSize,
                                    List<XxlJobFailedJobResponse> logs) {

    public XxlJobLogPageResponse(Long id,
                                 String monitorKey,
                                 String businessLineCode,
                                 String environmentCode,
                                 String name,
                                 String xxlJobDatabaseName,
                                 int total,
                                 int page,
                                 int pageSize,
                                 List<XxlJobFailedJobResponse> logs) {
        this(id,
                monitorKey,
                businessLineCode,
                environmentCode,
                name,
                xxlJobDatabaseName,
                "UP",
                "",
                LocalDateTime.now(),
                total,
                page,
                pageSize,
                logs);
    }
}
