package cn.aslight.workhub.domain.sprint.dto;

import java.time.LocalDate;

public record SprintSummaryResponse(Long id,
                                    Long projectId,
                                    String projectName,
                                    String name,
                                    String status,
                                    LocalDate startDate,
                                    LocalDate endDate) {
}
