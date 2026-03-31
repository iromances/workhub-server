package cn.aslight.workhub.domain.sprint.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
