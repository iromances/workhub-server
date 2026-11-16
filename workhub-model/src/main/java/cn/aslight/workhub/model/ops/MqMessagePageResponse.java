package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;
import java.util.List;

public record MqMessagePageResponse(Long id,
                                    String monitorKey,
                                    String businessLineCode,
                                    String environmentCode,
                                    String name,
                                    String status,
                                    String message,
                                    LocalDateTime checkedAt,
                                    int total,
                                    int page,
                                    int pageSize,
                                    List<MqMessageResponse> messages,
                                    MqQueryEvidence executionEvidence) {

    public MqMessagePageResponse(Long id, String monitorKey, String businessLineCode, String environmentCode, String name,
            String status, String message, LocalDateTime checkedAt, int total, int page, int pageSize,
            List<MqMessageResponse> messages) {
        this(id, monitorKey, businessLineCode, environmentCode, name, status, message, checkedAt, total, page, pageSize,
                messages, null);
    }
}
