package cn.aslight.workhub.model.sprint;

import java.time.LocalDate;

/**
 * SprintSummary 响应模型。
 */
public record SprintSummaryResponse(Long id,
                                    Long projectId,
                                    String projectName,
                                    String name,
                                    String status,
                                    LocalDate startDate,
                                    LocalDate endDate) {
}
