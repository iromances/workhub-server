package cn.aslight.workhub.model.sprint;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * SprintDetail 响应模型。
 */
public record SprintDetailResponse(Long id,
                                   Long projectId,
                                   String projectName,
                                   String name,
                                   String status,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
}
